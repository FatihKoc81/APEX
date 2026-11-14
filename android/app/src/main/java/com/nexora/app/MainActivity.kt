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
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

private val Night = Color(0xFF080B14)
private val Panel = Color(0xFF141A29)
private val Cyan = Color(0xFF65E5FF)
private val Violet = Color(0xFF9A7BFF)
private val http = OkHttpClient()

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

    fun refreshProviders() {
        Thread {
            try {
                val req = Request.Builder().url(BuildConfig.API_BASE_URL.trimEnd('/') + "/providers").get().build()
                var createdTaskId = ""
                http.newCall(req).execute().use { response ->
                    val result = JSONObject(response.body?.string() ?: "{}")
                    if (!response.isSuccessful) throw Exception(result.optString("error", "HTTP ${response.code}"))
                    createdTaskId = result.getString("id")
                }
                val id = createdTaskId
                activity.runOnUiThread {
                    taskId = id
                    status = "GPT DEVİR ALDI • OTOMATİK DEVAM BAŞLADI"
                }
                // Poll outside the inline use lambda so Kotlin can exit the loop safely.
                var finished = false
                while (!finished) {
                    Thread.sleep(2500)
                    val poll = Request.Builder()
                        .url(BuildConfig.API_BASE_URL.trimEnd('/') + "/tasks/" + id)
                        .get().build()
                    http.newCall(poll).execute().use { taskResponse ->
                        if (!taskResponse.isSuccessful) throw Exception("Görev durumu alınamadı: HTTP ${taskResponse.code}")
                        val task = JSONObject(taskResponse.body?.string() ?: "{}")
                        val taskStatus = task.optString("status", "running")
                        val step = task.optInt("step", 0)
                        val message = when (taskStatus) {
                            "completed" -> "GÖREV TAMAMLANDI • ${step} TUR"
                            "blocked" -> "GÖREV ENGELDE • AYRINTI İÇİN GÖREV KAYDINA BAKIN"
                            "failed" -> "GÖREV HATASI • ${task.optString("error")}"
                            "paused" -> "GÜVENLİ DURAKLATMA • ${step} TUR KAYDEDİLDİ"
                            "queued" -> "GPT OTOMATİK DEVAM İÇİN SIRADA • ${step} TUR"
                            else -> "GPT DEVAM EDİYOR • ${step}. TUR • GEMINI DENETLİYOR"
                        }
                        activity.runOnUiThread { status = message }
                        finished = taskStatus in listOf("completed", "blocked", "failed", "paused", "cancelled")
                    }
                }
            } catch (e: Exception) {
                activity.runOnUiThread { status = "SUNUCUYA BAĞLANILAMADI • ADRESİ / API'Yİ KONTROL EDİN" }
            }
        }.start()
    }

    fun startTask() {
        if (command.isBlank()) { status = "ÖNCE BİR TALİMAT YAZIN"; return }
        Thread {
            try {
                val json = JSONObject().put("instruction", command).toString()
                val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
                val req = Request.Builder().url(BuildConfig.API_BASE_URL.trimEnd('/') + "/tasks").post(body).build()
                http.newCall(req).execute().use { response ->
                    val result = JSONObject(response.body?.string() ?: "{}")
                    if (!response.isSuccessful) throw Exception(result.optString("error", "HTTP ${response.code}"))
                    val id = result.getString("id")
                    activity.runOnUiThread {
                        taskId = id
                        status = "GPT DEVİR ALDI • OTOMATİK DEVAM BAŞLADI"
                    }
                    // Keep following the task automatically; do not wait for the user to press Continue.
                    while (true) {
                        Thread.sleep(2500)
                        val poll = Request.Builder()
                            .url(BuildConfig.API_BASE_URL.trimEnd('/') + "/tasks/" + id)
                            .get().build()
                        var finished = false
                        http.newCall(poll).execute().use { taskResponse ->
                            if (!taskResponse.isSuccessful) throw Exception("Görev durumu alınamadı: HTTP ${taskResponse.code}")
                            val task = JSONObject(taskResponse.body?.string() ?: "{}")
                            val taskStatus = task.optString("status", "running")
                            val step = task.optInt("step", 0)
                            val message = when (taskStatus) {
                                "completed" -> "GÖREV TAMAMLANDI • ${step} TUR"
                                "blocked" -> "GÖREV ENGELDE • AYRINTI İÇİN GÖREV KAYDINA BAKIN"
                                "failed" -> "GÖREV HATASI • ${task.optString("error")}"
                                "paused" -> "GÜVENLİ DURAKLATMA • ${step} TUR KAYDEDİLDİ"
                                "queued" -> "GPT OTOMATİK DEVAM İÇİN SIRADA • ${step} TUR"
                                else -> "GPT DEVAM EDİYOR • ${step}. TUR • GEMINI DENETLİYOR"
                            }
                            activity.runOnUiThread { status = message }
                            finished = taskStatus in listOf("completed", "blocked", "failed", "paused", "cancelled")
                        }
                        if (finished) break
                    }
                }
            } catch (e: Exception) {
                activity.runOnUiThread { status = "GÖREV GÖNDERİLEMEDİ: ${e.message ?: "Bağlantı hatası"}" }
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
                Surface(Modifier.fillMaxWidth(), color = Panel.copy(alpha = .99f), shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        OutlinedTextField(command, { command = it }, Modifier.fillMaxWidth(), minLines = 1, maxLines = 3, placeholder = { Text("APEX'ya bir görev ver...") }, shape = RoundedCornerShape(14.dp))
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
                            FilledTonalIconButton(onClick = {
                                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
                                    putExtra(RecognizerIntent.EXTRA_PROMPT, "APEX için talimatınızı söyleyin")
                                }
                                try { speechInput.launch(intent) } catch (_: Exception) { status = "SES TANIMA HİZMETİ BULUNAMADI" }
                            }, modifier = Modifier.size(42.dp)) { Icon(Icons.Default.Mic, "Sesli talimat", tint = Cyan) }
                            Spacer(Modifier.weight(1f))
                            Button(onClick = { startTask() }, modifier = Modifier.height(46.dp), shape = RoundedCornerShape(14.dp)) {
                                Icon(Icons.Default.Bolt, null); Spacer(Modifier.width(6.dp)); Text("GÖREVİ BAŞLAT", fontWeight = FontWeight.Bold, fontSize = 11.sp)
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
