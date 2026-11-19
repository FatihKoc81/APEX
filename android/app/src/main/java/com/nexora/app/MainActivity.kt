package com.nexora.app

import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
                    status = "BU CİHAZ APEX'E YETKİLENDİRİLDİ"
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

    LaunchedEffect(deviceToken) { if (deviceToken.isNotBlank()) refreshProviders() else status = "BU CİHAZ HENÜZ YETKİLENDİRİLMEDİ" }

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
        BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
            val compact = maxHeight < 700.dp
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(54.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(painter = painterResource(R.drawable.apex_logo), contentDescription = "APEX", modifier = Modifier.size(40.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("APEX", fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Color.White, letterSpacing = 2.sp)
                        Text("MULTI-AGENT", fontSize = 8.sp, color = Cyan, letterSpacing = 1.8.sp)
                    }
                    Surface(color = Color(0xFF102B35), shape = RoundedCornerShape(50)) {
                        Text("● ONLINE", Modifier.padding(horizontal = 10.dp, vertical = 7.dp), color = Cyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)
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
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    color = Color(0xFF151923),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("APEX KOMUTA SİSTEMİ", color = Cyan, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                        Text("GPT planlar • Ajanlar üretir • Gemini denetler", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(7.dp).background(if (deviceToken.isNotBlank()) Color(0xFF43D6A0) else Color(0xFFFFB454), RoundedCornerShape(50)))
                            Spacer(Modifier.width(7.dp))
                            Text(status, color = Color(0xFFB8C1D4), fontSize = 9.sp, maxLines = 2)
                        }
                    }
                }
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(13.dp)
                ) {
                    if (taskId.isNotBlank()) {
                        Text("AKTİF GÖREV  •  ${taskId.take(8)}", color = Cyan, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    } else {
                        Spacer(Modifier.height(2.dp))
                        Text("Nasıl yardımcı olabilirim?", color = Color.White, fontSize = if (compact) 22.sp else 25.sp, fontWeight = FontWeight.SemiBold)
                        Text("Bir görev yaz. APEX ekibi üzerinde çalışmaya başlasın.", color = Color(0xFF9CA3AF), fontSize = 13.sp)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(7.dp).background(if (providers["openai"] == true) Color(0xFF43D6A0) else Color.Gray, RoundedCornerShape(50)))
                            Spacer(Modifier.width(6.dp))
                            Text("GPT", color = Color(0xFFBFC5D1), fontSize = 11.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(7.dp).background(if (providers["gemini"] == true) Color(0xFF43D6A0) else Color.Gray, RoundedCornerShape(50)))
                            Spacer(Modifier.width(6.dp))
                            Text("Gemini", color = Color(0xFFBFC5D1), fontSize = 11.sp)
                        }
                    }
                }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.Black,
                    shape = RoundedCornerShape(0.dp)
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                        color = Color(0xFF292A2D),
                        shape = RoundedCornerShape(30.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF55565A))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 7.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box {
                                IconButton(onClick = { addExpanded = true }, modifier = Modifier.size(40.dp)) {
                                    Icon(Icons.Default.Add, contentDescription = "Dosya ekle", tint = Color.White, modifier = Modifier.size(27.dp))
                                }
                                DropdownMenu(expanded = addExpanded, onDismissRequest = { addExpanded = false }) {
                                    DropdownMenuItem(text = { Text("Kamera") }, onClick = { addExpanded = false; cameraCapture.launch(null) })
                                    DropdownMenuItem(text = { Text("Fotoğraflar") }, onClick = { addExpanded = false; photoPicker.launch("image/*") })
                                    DropdownMenuItem(text = { Text("Dosyalar") }, onClick = { addExpanded = false; filePicker.launch(arrayOf("*/*")) })
                                }
                            }
                            Box(Modifier.weight(1f).padding(vertical = 9.dp), contentAlignment = Alignment.CenterStart) {
                                if (command.isEmpty()) Text("APEX'e mesaj yaz", color = Color(0xFFBFC0C5), fontSize = 15.sp)
                                BasicTextField(
                                    value = command,
                                    onValueChange = { command = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 15.sp),
                                    maxLines = 5
                                )
                            }
                            if (command.isBlank()) {
                                IconButton(onClick = {
                                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
                                        putExtra(RecognizerIntent.EXTRA_PROMPT, "APEX için talimatınızı söyleyin")
                                    }
                                    try { speechInput.launch(intent) } catch (_: Exception) { status = "SES TANIMA HİZMETİ BULUNAMADI" }
                                }, modifier = Modifier.size(40.dp)) {
                                    Icon(Icons.Default.Mic, "Sesli talimat", tint = Color.White, modifier = Modifier.size(24.dp))
                                }
                            } else {
                                FilledIconButton(
                                    onClick = { startTask(); command = "" },
                                    modifier = Modifier.size(38.dp),
                                    shape = RoundedCornerShape(50),
                                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White, contentColor = Color.Black)
                                ) { Icon(Icons.Default.Send, contentDescription = "Gönder") }
                            }
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
