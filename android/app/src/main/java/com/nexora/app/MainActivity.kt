package com.nexora.app

import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import java.util.Locale
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

private val Night = Color(0xFF080B14)
private val Panel = Color(0xFF141A29)
private val Cyan = Color(0xFF65E5FF)
private val Violet = Color(0xFF9A7BFF)
private val http = OkHttpClient.Builder()
    .connectTimeout(60, TimeUnit.SECONDS)
    .readTimeout(120, TimeUnit.SECONDS)
    .writeTimeout(60, TimeUnit.SECONDS)
    .callTimeout(150, TimeUnit.SECONDS)
    .build()

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        setContent { ApexHome() }
    }
    override fun onInit(result: Int) {
        if (result == TextToSpeech.SUCCESS) tts?.language = Locale("tr", "TR")
    }
    fun speak(text: String) {
        if (text.isNotBlank()) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "apex_reply")
    }
    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }
}

@Composable
fun ApexHome() {
    val activity = androidx.compose.ui.platform.LocalContext.current as ComponentActivity
    var command by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("SUNUCU BAĞLANTISI DENETLENİYOR") }
    var menuExpanded by remember { mutableStateOf(false) }
    var addExpanded by remember { mutableStateOf(false) }
    var attachmentLabel by remember { mutableStateOf("") }
    var providers by remember { mutableStateOf(mapOf("openai" to false, "openrouter" to false, "gemini" to false, "kimi" to false, "deepseek" to false)) }
    var taskId by remember { mutableStateOf("") }
    var chatMessages by remember { mutableStateOf(listOf<Pair<String, String>>()) }
    var voiceEnabled by remember { mutableStateOf(true) }

    fun refreshProviders() {
        Thread {
            try {
                val req = Request.Builder()
                    .url(BuildConfig.API_BASE_URL.trimEnd('/') + "/providers")
                    .get().build()
                val result = http.newCall(req).execute().use { response ->
                    if (!response.isSuccessful) throw Exception("Sağlayıcı durumu alınamadı: HTTP ${response.code}")
                    JSONArray(response.body?.string() ?: "[]")
                }
                val state = mutableMapOf("openai" to false, "openrouter" to false, "gemini" to false, "kimi" to false, "deepseek" to false)
                val missing = mutableListOf<String>()
                for (i in 0 until result.length()) {
                    val item = result.getJSONObject(i)
                    val provider = item.optString("provider")
                    val configured = item.optBoolean("configured")
                    if (provider in state.keys) state[provider] = configured
                    if (!configured) {
                        val expected = when(provider) { "openai" -> "OPENAI_API_KEY"; "openrouter" -> "OPENROUTER_API_KEY"; "gemini" -> "GEMINI_API_KEY"; else -> provider.uppercase() + "_MODEL (:free gerekli)" }
                        missing.add(expected)
                    }
                }
                activity.runOnUiThread {
                    providers = state
                    status = if (state["openai"] == true && state["openrouter"] == true && state["gemini"] == true)
                        "SUNUCU BAĞLI • OPENAI, OPENROUTER VE GEMINI HAZIR"
                    else "SUNUCU BAĞLI • EKSİK: " + missing.joinToString(", ")
                }
            } catch (e: Exception) {
                activity.runOnUiThread {
                    status = "SUNUCU BAĞLANTISI HATASI • ${e.message ?: "Bağlantıyı kontrol edin"}"
                }
            }
        }.start()
    }

    fun startTask() {
        if (command.isBlank()) { status = "ÖNCE BİR TALİMAT YAZIN"; return }
        val instruction = command.trim()
        Thread {
            try {
                val json = JSONObject().put("instruction", instruction).toString()
                val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
                val req = Request.Builder()
                    .url(BuildConfig.API_BASE_URL.trimEnd('/') + "/tasks")
                    .post(body).build()
                val id = http.newCall(req).execute().use { response ->
                    val result = JSONObject(response.body?.string() ?: "{}")
                    if (!response.isSuccessful) throw Exception(result.optString("error", "HTTP ${response.code}"))
                    result.getString("id")
                }
                activity.runOnUiThread {
                    taskId = id
                    chatMessages = chatMessages + ("user" to instruction)
                    command = ""
                    status = "APEX ekibi çalışıyor..."
                }
                while (true) {
                    Thread.sleep(2500)
                    val poll = Request.Builder()
                        .url(BuildConfig.API_BASE_URL.trimEnd('/') + "/tasks/" + id)
                        .get().build()
                    val task = http.newCall(poll).execute().use { response ->
                        if (!response.isSuccessful) throw Exception("Görev durumu alınamadı: HTTP ${response.code}")
                        JSONObject(response.body?.string() ?: "{}")
                    }
                    val taskStatus = task.optString("status", "running")
                    val transcript = task.optString("result", "")
                    val step = task.optInt("step", 0)
                    val message = when (taskStatus) {
                        "completed" -> "GÖREV TAMAMLANDI • ${step} TUR"
                        "blocked" -> "GÖREV ENGELDE • ${task.optString("error")}"
                        "failed" -> "GÖREV HATASI • ${task.optString("error")}"
                        "paused" -> "GÜVENLİ DURAKLATMA • ${step} TUR KAYDEDİLDİ"
                        "queued" -> "OPENROUTER OTOMATİK DEVAM İÇİN SIRADA • ${step} TUR"
                        "cancelled" -> "GÖREV İPTAL EDİLDİ"
                        else -> "OPENROUTER DEVAM EDİYOR • ${step}. TUR • GEMINI DENETLİYOR"
                    }
                    activity.runOnUiThread {
                        status = message
                        if (taskStatus in listOf("completed", "blocked", "failed")) {
                            val answer = transcript.ifBlank { message }
                            chatMessages = chatMessages + ("assistant" to answer)
                            if (voiceEnabled) (activity as? MainActivity)?.speak(answer.takeLast(3500))
                        }
                    }
                    if (taskStatus in listOf("completed", "blocked", "failed", "paused", "cancelled")) break
                }
            } catch (e: Exception) {
                activity.runOnUiThread {
                    status = "GÖREV GÖNDERİLEMEDİ • ${e.message ?: "Bağlantı hatası"}"
                }
            }
        }.start()
    }

    LaunchedEffect(Unit) { refreshProviders() }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) attachmentLabel = "Fotoğraf seçildi (henüz sunucuya aktarılmıyor)"
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) attachmentLabel = "Dosya seçildi (henüz sunucuya aktarılmıyor)"
    }
    val cameraCapture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        attachmentLabel = if (bitmap != null) "Kamera görüntüsü alındı (henüz sunucuya aktarılmıyor)" else "Kamera işlemi iptal edildi"
    }
    val speechInput = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!spoken.isNullOrBlank()) {
            command = listOf(command, spoken).filter { it.isNotBlank() }.joinToString(" ")
            status = "SESLİ TALİMAT METNE DÖNÜŞTÜRÜLDÜ"
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(background = Night, surface = Panel, primary = Cyan, secondary = Violet)) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF101A31), Night)))) {
            val compact = maxHeight < 700.dp
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = if (compact) 14.dp else 20.dp, vertical = if (compact) 8.dp else 12.dp),
                verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)
            ) {
                Row(Modifier.fillMaxWidth().height(if (compact) 54.dp else 64.dp), verticalAlignment = Alignment.CenterVertically) {
                    Image(painter = painterResource(R.drawable.apex_logo), contentDescription = "APEX", modifier = Modifier.size(if (compact) 48.dp else 58.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("APEX", fontSize = if (compact) 27.sp else 32.sp, fontWeight = FontWeight.Black, color = Color.White, letterSpacing = 3.sp)
                        Text("MULTI-INTELLIGENCE COMMAND", fontSize = 9.sp, color = Cyan, letterSpacing = 1.5.sp)
                    }
                    Surface(color = Color(0x332DE5FF), shape = RoundedCornerShape(50)) {
                        Text("● ONLINE", Modifier.padding(horizontal = 10.dp, vertical = 7.dp), color = Cyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Default.Menu, "Menü", tint = Color.White) }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(text = { Text("Güncellemeleri denetle") }, leadingIcon = { Icon(Icons.Default.SystemUpdate, null) }, onClick = {
                                menuExpanded = false
                                status = "GÜNCELLEME SERVİSİ HENÜZ ETKİN DEĞİL"
                            })
                            DropdownMenuItem(text = { Text("Bağlantıları yenile") }, onClick = { menuExpanded = false; refreshProviders() })
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("APEX EKİBİ", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(Modifier.weight(1f))
                    Text(status, color = Cyan, fontSize = 9.sp, maxLines = 1)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    ProviderPill("GPT", providers["openai"] == true)
                    ProviderPill("Gemini", providers["gemini"] == true)
                    ProviderPill("Router", providers["openrouter"] == true)
                    ProviderPill("Kimi", providers["kimi"] == true)
                    ProviderPill("DeepSeek", providers["deepseek"] == true)
                }
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    if (chatMessages.isEmpty()) item {
                        Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Image(painter = painterResource(R.drawable.apex_logo), contentDescription = "APEX", modifier = Modifier.size(68.dp))
                            Spacer(Modifier.height(18.dp))
                            Text("Merhaba, ben APEX.", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
                            Text("Nasıl yardımcı olabilirim?", color = Color.LightGray, fontSize = 13.sp)
                        }
                    }
                    items(chatMessages) { message ->
                        if (message.first == "user") {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                Surface(color = Color(0xFF303642), shape = RoundedCornerShape(20.dp), modifier = Modifier.widthIn(max = 310.dp)) {
                                    Text(message.second, Modifier.padding(15.dp), color = Color.White, fontSize = 15.sp, lineHeight = 22.sp)
                                }
                            }
                        } else {
                            Column(Modifier.fillMaxWidth().padding(end = 8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Image(painter = painterResource(R.drawable.apex_logo), contentDescription = "APEX", modifier = Modifier.size(26.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("APEX", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Spacer(Modifier.weight(1f))
                                    IconButton(onClick = { (activity as? MainActivity)?.speak(message.second) }) { Icon(Icons.Default.Mic, "Yanıtı sesli oku", tint = Cyan) }
                                }
                                Text(message.second, color = Color(0xFFE6E8ED), fontSize = 14.sp, lineHeight = 23.sp)
                            }
                        }
                    }
                }
                Surface(Modifier.fillMaxWidth(), color = Panel.copy(alpha = .99f), shape = RoundedCornerShape(30.dp)) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        if (attachmentLabel.isNotBlank()) Text(attachmentLabel, color = Cyan, fontSize = 10.sp, maxLines = 1)
                        Row(
                            Modifier.fillMaxWidth().background(Color(0xFF10141E), RoundedCornerShape(25.dp)).padding(horizontal = 12.dp, vertical = 15.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box {
                                FilledTonalIconButton(onClick = { addExpanded = true }, modifier = Modifier.size(42.dp)) { Icon(Icons.Default.Add, "Dosya veya fotoğraf ekle") }
                                DropdownMenu(expanded = addExpanded, onDismissRequest = { addExpanded = false }) {
                                    DropdownMenuItem(text = { Text("Kamera") }, onClick = { addExpanded = false; cameraCapture.launch(null) })
                                    DropdownMenuItem(text = { Text("Fotoğraflar") }, onClick = { addExpanded = false; photoPicker.launch("image/*") })
                                    DropdownMenuItem(text = { Text("Dosyalar") }, onClick = { addExpanded = false; filePicker.launch(arrayOf("*/*")) })
                                }
                            }
                            Box(Modifier.weight(1f)) {
                                if (command.isBlank()) Text("Mesaj yazın veya basılı tutup konuşun", color = Color(0xFFB7BAC5), fontSize = 14.sp)
                                BasicTextField(
                                    value = command,
                                    onValueChange = { command = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 15.sp),
                                    maxLines = 4
                                )
                            }
                            IconButton(onClick = {
                                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
                                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Konuşmaya başlayın")
                                }
                                try { speechInput.launch(intent) } catch (_: Exception) { status = "SES TANIMA HİZMETİ BULUNAMADI" }
                            }) { Icon(Icons.Default.Mic, "Konuşarak yaz", tint = Cyan) }
                            Surface(
                                color = Color.White,
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        "≋",
                                        color = Color(0xFF171923),
                                        fontSize = 27.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(bottom = 3.dp)
                                    )
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("＋", color = Color.LightGray, fontSize = 12.sp)
                            Spacer(Modifier.width(5.dp))
                            Text("Basılı tutup konuşun", color = Color(0xFF9EA3B1), fontSize = 11.sp)
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { voiceEnabled = !voiceEnabled }) {
                                Text(if (voiceEnabled) "Sesli yanıt açık" else "Sesli yanıt kapalı", color = if (voiceEnabled) Cyan else Color.Gray, fontSize = 10.sp)
                            }
                            Button(onClick = { startTask() }, modifier = Modifier.height(40.dp), shape = RoundedCornerShape(20.dp), enabled = command.isNotBlank()) {
                                Icon(Icons.Default.Bolt, null); Spacer(Modifier.width(5.dp)); Text("Gönder", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }
                    }
                }
                Text(if (taskId.isBlank()) "GÖREV MOTORU • SUNUCU KUYRUĞU" else "AKTİF GÖREV • $taskId", color = Color.Gray, fontSize = 8.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ProviderTile(name: String, role: String, configured: Boolean, accent: Color, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxHeight(), color = Panel, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(if (configured) Color(0xFF48D597) else accent, RoundedCornerShape(50)))
                Spacer(Modifier.width(7.dp))
                Text(name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Spacer(Modifier.height(5.dp))
            Text(role, color = Color.LightGray, fontSize = 9.sp, maxLines = 2)
            Spacer(Modifier.height(4.dp))
            Text(if (configured) "API HAZIR" else "API ANAHTARI EKSİK", color = if (configured) Color(0xFF48D597) else Color.Gray, fontSize = 8.sp, maxLines = 1)
        }
    }
}

@Composable
private fun ProviderPill(name: String, ready: Boolean) {
    Surface(color = if (ready) Color(0x223CCB91) else Color(0x222F3542), shape = RoundedCornerShape(50)) {
        Row(Modifier.padding(horizontal = 6.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(5.dp).background(if (ready) Color(0xFF48D597) else Color.Gray, RoundedCornerShape(50)))
            Spacer(Modifier.width(4.dp))
            Text(name, color = if (ready) Color(0xFFB8F3D8) else Color.LightGray, fontSize = 8.sp, maxLines = 1)
        }
    }
}
