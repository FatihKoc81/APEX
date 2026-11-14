package com.nexora.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Night = Color(0xFF080B14)
private val Panel = Color(0xFF141A29)
private val Cyan = Color(0xFF65E5FF)
private val Violet = Color(0xFF9A7BFF)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NexoraHome() }
    }
}

@Composable
fun NexoraHome() {
    var command by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("SİSTEM HAZIR") }
    var updateMessage by remember { mutableStateOf("Güncelleme denetimi henüz sunucuya bağlanmadı.") }
    MaterialTheme(colorScheme = darkColorScheme(
        background = Night, surface = Panel, primary = Cyan, secondary = Violet
    )) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF101A31), Night)))) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                contentPadding = PaddingValues(top = 34.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("NEXORA", fontSize = 32.sp, fontWeight = FontWeight.Black, color = Color.White, letterSpacing = 3.sp)
                            Text("MULTI-INTELLIGENCE COMMAND", fontSize = 10.sp, color = Cyan, letterSpacing = 2.sp)
                        }
                        Surface(color = Color(0x332DE5FF), shape = RoundedCornerShape(50)) {
                            Text("● ONLINE", Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = Cyan, fontSize = 10.sp)
                        }
                    }
                }
                item {
                    Surface(color = Panel.copy(alpha=.95f), shape = RoundedCornerShape(24.dp), tonalElevation = 4.dp) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("KOMUTA MERKEZİ", color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                            Text("Dört zihin. Tek hedef.", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                            OutlinedTextField(
                                value = command, onValueChange = { command = it },
                                modifier = Modifier.fillMaxWidth(), minLines = 3,
                                placeholder = { Text("NEXORA'ya bir görev ver...") },
                                shape = RoundedCornerShape(16.dp)
                            )
                            Button(onClick = { status = if(command.isBlank()) "Önce bir görev yaz." else "Sunucu bağlantısı yapılandırılmayı bekliyor." },
                                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                                Icon(Icons.Default.Bolt, null); Spacer(Modifier.width(8.dp)); Text("GÖREVİ BAŞLAT")
                            }
                            Text(status, color = Cyan, fontSize = 11.sp)
                        }
                    }
                }
                item {
                    Text("YAPAY ZEKÂ EKİBİ", color = Color.White, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ProviderCard("CHATGPT", "Planlama • Koordinasyon", "API bağlantısı bekleniyor", Cyan)
                        ProviderCard("GEMINI", "Analiz • Doğrulama", "API bağlantısı bekleniyor", Violet)
                        ProviderCard("GROK", "Alternatif çözüm • Araştırma", "API bağlantısı bekleniyor", Color(0xFFFFB86B))
                        ProviderCard("CLAUDE", "Eleştiri • Kalite kontrol", "API bağlantısı bekleniyor", Color(0xFFFF8F9B))
                    }
                }
                item {
                    Surface(color = Panel, shape = RoundedCornerShape(20.dp)) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("NEXORA UPDATE", color = Color.White, fontWeight = FontWeight.Bold)
                                Text(updateMessage, color = Color.LightGray, fontSize = 11.sp)
                            }
                            Button(onClick = { updateMessage = "Güncelleme servisi ve imzalı APK kanalı yapılandırılacak." }) {
                                Icon(Icons.Default.SystemUpdate, null); Spacer(Modifier.width(5.dp)); Text("GÜNCELLE")
                            }
                        }
                    }
                }
                item { Text("Görev motoru: kalıcı sunucu kuyruğu • checkpoint • kontrollü yeniden deneme", color = Color.Gray, fontSize = 10.sp) }
            }
        }
    }
}

@Composable
private fun ProviderCard(name: String, role: String, state: String, accent: Color) {
    Surface(color = Panel, shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(accent, RoundedCornerShape(50)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(role, color = Color.LightGray, fontSize = 10.sp)
            }
            Text(state, color = Color.Gray, fontSize = 9.sp)
        }
    }
}
