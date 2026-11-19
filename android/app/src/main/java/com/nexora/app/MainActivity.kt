package com.nexora.app

import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.ui.text.input.PasswordVisualTransformation
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ApexHome() }
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
    var providers by remember { mutableStateOf(mapOf("openai" to false, "gemini" to false)) }
    var taskId by remember { mutableStateOf("") }
    val prefs = remember { activity.getSharedPreferences("apex_device_auth", android.content.Context.MODE_PRIVATE) }
    var deviceToken by remember { mutableStateOf(prefs.getString("device_token", "") ?: "") }
    var inviteCode by remember { mutableStateOf("") }
    var enrollmentOpen by remember { mutableStateOf(false) }
    var enrollmentBusy by remember { mutableStateOf(false) }

    fun enrollThisDevice() {
        if (inviteCode.isBlank()) { status = "DAVET KODU GEREKLİ"; return }
        enrollmentBusy = true
        Thread {
            try {
                val payload = JSONObject().put("inviteCode", inviteCode.trim()).put("deviceName", android.os.Build.MODEL ?: "Android cihaz").toString()
                val body = payload.toRequestBody("application/json; charset=utf-8".toMediaType())
                val req = Request.Builder().url(BuildConfig.API_BASE_URL.trimEnd('/') + "/auth/enroll").post(body).build()
                val result = http.newCall(req).execute().use { response ->
                    val raw = response.body?.string() ?: "{}"
                    if (!response.isSuccessful) throw Exception(JSONObject(raw).optString("error", "HTTP ${response.code}"))
                    JSONObject(raw)
                }
                val token = result.getString("token")
                prefs.edit().putString("device_token", token).putString("device_id", result.optString("id")).apply()
                activity.runOnUiThread {
                    deviceToken = token; inviteCode = ""; enrollmentOpen = false; enrollmentBusy = false
                    status = "BU CİHAZ APEX'E YETKİLENDİRİLDİ"; refreshProviders()
                }
            } catch (e: Exception) {
                activity.runOnUiThread { enrollmentBusy = false; status = "YETKİLENDİRME BAŞARISIZ • ${e.message}" }
            }
        }.start()
    }

    fun refreshProviders() {
        Thread {
            try {
                val req = Request.Builder()
                    .url(BuildConfig.API_BASE_URL.trimEnd('/') + "/providers")
                    .header("Authorization", "Bearer " + deviceToken).get().build()
                val result = http.newCall(req).execute().use { response ->
                    if (!response.isSuccessful) throw Exception("Sağlayıcı durumu alınamadı: HTTP ${response.code}")
                    JSONArray(response.body?.string() ?: "[]")
                }
                val state = mutableMapOf("openai" to false, "gemini" to false)
                val missing = mutableListOf<String>()
                for (i in 0 until result.length()) {
                    val item = result.getJSONObject(i)
                    val provider = item.optString("provider")
                    val configured = item.optBoolean("configured")
                    if (provider in state.keys) state[provider] = configured
                    if (!configured) {
                        val expected = if (provider == "openai") "OPENAI_API_KEY" else "GEMINI_API_KEY"
                        missing.add(expected)
                    }
                }
                activity.runOnUiThread {
                    providers = state
                    status = if (state["openai"] == true && state["gemini"] == true)
                        "SUNUCU BAĞLI • GPT VE GEMINI HAZIR"
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
                    .header("Authorization", "Bearer " + deviceToken).post(body).build()
                val id = http.newCall(req).execute().use { response ->
                    val result = JSONObject(response.body?.string() ?: "{}")
                    if (!response.isSuccessful) throw Exception(result.optString("error", "HTTP ${response.code}"))
                    result.getString("id")
                }
                activity.runOnUiThread {
                    taskId = id
                    status = "EMİR GPT'YE İLETİLDİ • OTOMATİK DEVAM BAŞLADI"
                }
                while (true) {
                    Thread.sleep(2500)
                    val poll = Request.Builder()
                        .url(BuildConfig.API_BASE_URL.trimEnd('/') + "/tasks/" + id)
                        .header("Authorization", "Bearer " + deviceToken).get().build()
                    val task = http.newCall(poll).execute().use { response ->
                        if (!response.isSuccessful) throw Exception("Görev durumu alınamadı: HTTP ${response.code}")
                        JSONObject(response.body?.string() ?: "{}")
                    }
                    val taskStatus = task.optString("status", "running")
                    val step = task.optInt("step", 0)
                    val message = when (taskStatus) {
                        "completed" -> "GÖREV TAMAMLANDI • ${step} TUR"
                        "blocked" -> "GÖREV ENGELDE • ${task.optString("error")}"
                        "failed" -> "GÖREV HATASI • ${task.optString("error")}"
                        "paused" -> "GÜVENLİ DURAKLATMA • ${step} TUR KAYDEDİLDİ"
                        "queued" -> "GPT OTOMATİK DEVAM İÇİN SIRADA • ${step} TUR"
                        "cancelled" -> "GÖREV İPTAL EDİLDİ"
                        else -> "GPT DEVAM EDİYOR • ${step}. TUR • GEMINI DENETLİYOR"
                    }
                    activity.runOnUiThread { status = message }
                    if (taskStatus in listOf("completed", "blocked", "failed", "paused", "cancelled")) break
                }
            } catch (e: Exception) {
                activity.runOnUiThread {
                    status = "GÖREV GÖNDERİLEMEDİ • ${e.message ?: "Bağlantı hatası"}"
                }
            }
        }.start()
    }

    LaunchedEffect(Unit) { if (deviceToken.isNotBlank()) refreshProviders() else status = "BU CİHAZ HENÜZ YETKİLENDİRİLMEDİ" }

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
        if (enrollmentOpen) {
            AlertDialog(
                onDismissRequest = { if (!enrollmentBusy) enrollmentOpen = false },
                title = { Text("Cihaz yetkilendirme") },
                text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Yönetici tarafından verilen davet kodunu girin. Kod yalnızca bu cihazı kaydetmek için kullanılır.")
                    OutlinedTextField(value = inviteCode, onValueChange = { inviteCode = it }, label = { Text("Davet kodu") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                } },
                confirmButton = { TextButton(onClick = { enrollThisDevice() }, enabled = !enrollmentBusy) { Text(if (enrollmentBusy) "BAĞLANIYOR..." else "YETKİLENDİR") } },
                dismissButton = { TextButton(onClick = { enrollmentOpen = false }, enabled = !enrollmentBusy) { Text("İPTAL") } }
            )
        }
        BoxWithConstraints(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF101A31), Night)))) {
            val compact = maxHeight < 700.dp
            Column(
                modifier = Modifier.fillMaxSize().padding(start = if (compact) 14.dp else 20.dp, end = if (compact) 14.dp else 20.dp, top = if (compact) 8.dp else 12.dp),
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
                            DropdownMenuItem(text = { Text("Bu cihazı yetkilendir") }, onClick = { menuExpanded = false; enrollmentOpen = true })
                            DropdownMenuItem(text = { Text("Bağlantıları yenile") }, onClick = { menuExpanded = false; if (deviceToken.isBlank()) enrollmentOpen = true else refreshProviders() })
                        }
                    }
                }

                Surface(Modifier.weight(0.72f).fillMaxWidth(), color = Panel.copy(alpha = .97f), shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.fillMaxSize().padding(if (compact) 14.dp else 18.dp), verticalArrangement = Arrangement.SpaceEvenly) {
                        Text("KOMUTA MERKEZİ", color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                        Text("İki zihin. Tek hedef.", color = Color.White, fontSize = if (compact) 20.sp else 23.sp, fontWeight = FontWeight.Bold)
                        Text("Görevlerini GPT ve Gemini birlikte ele alır.", color = Color.LightGray, fontSize = 13.sp)
                        Text(status, color = Cyan, fontSize = 9.sp, maxLines = 2)
                    }
                }

                Text("YAPAY ZEKÂ EKİBİ", color = Color.White, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, fontSize = 14.sp)
                Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ProviderTile("CHATGPT", "Planlama • Koordinasyon", providers["openai"] == true, Cyan, Modifier.weight(1f))
                        ProviderTile("GEMINI", "Analiz • Doğrulama", providers["gemini"] == true, Violet, Modifier.weight(1f))
                    }

                }
                Text(if (taskId.isBlank()) "GÖREV MOTORU • SUNUCU KUYRUĞU" else "AKTİF GÖREV • $taskId", color = Color.Gray, fontSize = 8.sp, maxLines = 1)
                Surface(
                    Modifier.fillMaxWidth(),
                    color = Panel.copy(alpha = .99f),
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 0.dp, bottomEnd = 0.dp)
                ) {
                    Column(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (attachmentLabel.isNotBlank()) Text(attachmentLabel, color = Cyan, fontSize = 9.sp, maxLines = 1)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box {
                                FilledTonalIconButton(onClick = { addExpanded = true }, modifier = Modifier.size(42.dp)) { Icon(Icons.Default.Add, "Ek ekle") }
                                DropdownMenu(expanded = addExpanded, onDismissRequest = { addExpanded = false }) {
                                    DropdownMenuItem(text = { Text("Kamera") }, onClick = { addExpanded = false; cameraCapture.launch(null) })
                                    DropdownMenuItem(text = { Text("Fotoğraflar") }, onClick = { addExpanded = false; photoPicker.launch("image/*") })
                                    DropdownMenuItem(text = { Text("Dosyalar") }, onClick = { addExpanded = false; filePicker.launch(arrayOf("*/*")) })
                                }
                            }
                            OutlinedTextField(
                                value = command,
                                onValueChange = { command = it },
                                modifier = Modifier.weight(1f),
                                minLines = 1,
                                maxLines = 4,
                                placeholder = { Text("Mesajınızı yazın...") },
                                shape = RoundedCornerShape(24.dp)
                            )
                            FilledIconButton(
                                onClick = { startTask() },
                                enabled = command.isNotBlank(),
                                modifier = Modifier.size(46.dp),
                                shape = RoundedCornerShape(50)
                            ) { Icon(Icons.Default.Send, contentDescription = "Gönder") }
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("APEX", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = {
                                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
                                    putExtra(RecognizerIntent.EXTRA_PROMPT, "APEX için talimatınızı söyleyin")
                                }
                                try { speechInput.launch(intent) } catch (_: Exception) { status = "SES TANIMA HİZMETİ BULUNAMADI" }
                            }, modifier = Modifier.size(34.dp)) { Icon(Icons.Default.Mic, "Sesli talimat", tint = Cyan) }
                        }
                    }
                }
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
