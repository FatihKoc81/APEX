package com.apexstudio.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

private val Bg = Color(0xFF080B14)
private val Panel = Color(0xFF151B2A)
private val Cyan = Color(0xFF62E8FF)
private val Muted = Color(0xFF9AA6BC)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("studio", MODE_PRIVATE)
        setContent {
            var token by remember { mutableStateOf(prefs.getString("token", "") ?: "") }
            var invite by remember { mutableStateOf("") }
            var topic by remember { mutableStateOf("") }
            var status by remember { mutableStateOf("Bağlantı bekleniyor") }
            var integrations by remember { mutableStateOf(listOf<Pair<String, Boolean>>()) }
            var jobs by remember { mutableStateOf(listOf<String>()) }
            var busy by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()
            val client = remember { OkHttpClient() }
            fun api(path: String, method: String = "GET", body: String? = null): String {
                val builder = Request.Builder().url(BuildConfig.API_BASE_URL.trimEnd('/') + path)
                if (token.isNotBlank()) builder.addHeader("Authorization", "Bearer $token")
                if (body != null) builder.method(method, body.toRequestBody("application/json".toMediaType()))
                else if (method != "GET") builder.method(method, ByteArray(0).toRequestBody())
                return client.newCall(builder.build()).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}: $text")
                    text
                }
            }
            fun refresh() {
                scope.launch {
                    busy = true
                    try {
                        val cfg = withContext(Dispatchers.IO) { JSONObject(api("/studio/config")) }
                        val arr = cfg.getJSONArray("integrations")
                        integrations = (0 until arr.length()).map { i ->
                            val x = arr.getJSONObject(i); x.getString("name") to x.getBoolean("configured")
                        }
                        val list = withContext(Dispatchers.IO) { JSONArray(api("/studio/jobs")) }
                        jobs = (0 until list.length()).map { i ->
                            val x = list.getJSONObject(i)
                            "${x.optString("status").uppercase()} • ${x.optString("topic")}"
                        }
                        status = "Sunucuya bağlı • durumlar güncel"
                    } catch (e: Exception) { status = e.message ?: "Bağlantı hatası" }
                    busy = false
                }
            }
            MaterialTheme(colorScheme = darkColorScheme(background = Bg, surface = Panel, primary = Cyan)) {
                Surface(Modifier.fillMaxSize(), color = Bg) {
                    Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.VideoLibrary, null, tint = Cyan, modifier = Modifier.size(34.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("APEX STUDIO", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                                Text("AI VIDEO FACTORY", color = Cyan, fontSize = 10.sp, letterSpacing = 2.sp)
                            }
                            Text("●", color = if (token.isNotBlank()) Color(0xFF42D6A0) else Color(0xFFFFB454))
                        }
                        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(22.dp)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("KOMUTA MERKEZİ", color = Cyan, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                Text("Fikirden videoya.\nOtomatik üretim.", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, lineHeight = 31.sp)
                                Text("Saatlik içerik • 60 saniye hedef • TikTok + YouTube", color = Muted, fontSize = 13.sp)
                                Text(status, color = Muted, fontSize = 11.sp)
                            }
                        }
                        if (token.isBlank()) {
                            Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Cihaz yetkilendirme", color = Color.White, fontWeight = FontWeight.Bold)
                                    OutlinedTextField(invite, { invite = it }, label = { Text("APEX davet kodu") }, singleLine = true)
                                    Button(onClick = {
                                        scope.launch {
                                            busy = true
                                            try {
                                                val req = JSONObject().put("inviteCode", invite).put("deviceName", "APEX STUDIO Android")
                                                val response = withContext(Dispatchers.IO) {
                                                    client.newCall(Request.Builder().url(BuildConfig.API_BASE_URL.trimEnd('/') + "/auth/enroll")
                                                        .post(req.toString().toRequestBody("application/json".toMediaType())).build())
                                                        .execute().use { it.body?.string().orEmpty() }
                                                }
                                                val data = JSONObject(response); token = data.getString("token")
                                                prefs.edit().putString("token", token).apply()
                                                status = "Cihaz yetkilendirildi"; refresh()
                                            } catch (e: Exception) { status = e.message ?: "Yetkilendirme başarısız" }
                                            busy = false
                                        }
                                    }, enabled = !busy && invite.isNotBlank()) { Text("Cihazı yetkilendir") }
                                }
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                integrations.take(4).forEach { (name, ok) ->
                                    AssistChip(onClick = {}, label = { Text(name + if (ok) " ✓" else " •") })
                                }
                            }
                            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("ÖZEL VİDEO ÜRET", color = Cyan, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    OutlinedTextField(topic, { topic = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Video talimatın") }, minLines = 2)
                                    Button(onClick = {
                                        scope.launch {
                                            busy = true
                                            try {
                                                val payload = JSONObject().put("topic", topic).put("durationSeconds", 60)
                                                withContext(Dispatchers.IO) { api("/studio/jobs", "POST", payload.toString()) }
                                                topic = ""; status = "Video görevi kuyruğa alındı"; refresh()
                                            } catch (e: Exception) { status = e.message ?: "Görev gönderilemedi" }
                                            busy = false
                                        }
                                    }, enabled = !busy && topic.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                                        Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Videoyu üret")
                                    }
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("SON GÖREVLER", color = Color.White, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.weight(1f))
                                TextButton(onClick = { refresh() }) { Text("Yenile") }
                            }
                            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(jobs) { job -> Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                                    Text(job, color = Color.White, modifier = Modifier.padding(12.dp), fontSize = 12.sp)
                                } }
                            }
                        }
                    }
                }
            }
        }
    }
}
