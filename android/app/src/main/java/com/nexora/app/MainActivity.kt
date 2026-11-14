package com.nexora.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.SystemUpdate
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
    var menuExpanded by remember { mutableStateOf(false) }
    var updateMessage by remember { mutableStateOf("Güncelleme denetimi henüz sunucuya bağlanmadı.") }

    MaterialTheme(colorScheme = darkColorScheme(
        background = Night, surface = Panel, primary = Cyan, secondary = Violet
    )) {
        BoxWithConstraints(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color(0xFF101A31), Night))
            )
        ) {
            val compact = maxHeight < 700.dp
            val outerPadding = if (compact) 14.dp else 20.dp
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = outerPadding, vertical = if (compact) 8.dp else 12.dp),
                verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)
            ) {
                // Header and top-right hamburger menu
                Row(
                    modifier = Modifier.fillMaxWidth().height(if (compact) 54.dp else 64.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("NEXORA", fontSize = if (compact) 27.sp else 32.sp,
                            fontWeight = FontWeight.Black, color = Color.White, letterSpacing = 3.sp)
                        Text("MULTI-INTELLIGENCE COMMAND", fontSize = 9.sp,
                            color = Cyan, letterSpacing = 1.5.sp)
                    }
                    Surface(color = Color(0x332DE5FF), shape = RoundedCornerShape(50)) {
                        Text("● ONLINE", Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            color = Cyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(8.dp))
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menü", tint = Color.White)
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("Güncellemeleri denetle") },
                                leadingIcon = { Icon(Icons.Default.SystemUpdate, null) },
                                onClick = {
                                    menuExpanded = false
                                    updateMessage = "Güncelleme servisi yapılandırılmayı bekliyor."
                                    status = "GÜNCELLEME DENETİMİ HENÜZ ETKİN DEĞİL"
                                }
                            )
                        }
                    }
                }

                // Main command panel takes a bounded share of the screen.
                Surface(
                    modifier = Modifier.weight(1.18f).fillMaxWidth(),
                    color = Panel.copy(alpha = .97f),
                    shape = RoundedCornerShape(22.dp),
                    tonalElevation = 3.dp
                ) {
                    Column(
                        Modifier.fillMaxSize().padding(if (compact) 14.dp else 18.dp),
                        verticalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Text("KOMUTA MERKEZİ", color = Cyan, fontSize = 11.sp,
                            fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                        Text("Dört zihin. Tek hedef.", color = Color.White,
                            fontSize = if (compact) 20.sp else 23.sp, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = command,
                            onValueChange = { command = it },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 3,
                            placeholder = { Text("NEXORA'ya bir görev ver...") },
                            shape = RoundedCornerShape(15.dp)
                        )
                        Button(
                            onClick = {
                                status = if (command.isBlank()) "ÖNCE BİR GÖREV YAZIN"
                                else "SUNUCU BAĞLANTISI YAPILANDIRILMAYI BEKLİYOR"
                            },
                            modifier = Modifier.fillMaxWidth().height(if (compact) 48.dp else 54.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.Bolt, null)
                            Spacer(Modifier.width(8.dp))
                            Text("GÖREVİ BAŞLAT", fontWeight = FontWeight.Bold)
                        }
                        Text(status, color = Cyan, fontSize = 10.sp, maxLines = 1)
                    }
                }

                Text("YAPAY ZEKÂ EKİBİ", color = Color.White,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.sp, fontSize = 14.sp)

                // Four agents in a fixed 2x2 grid; no vertical scrolling.
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ProviderTile("CHATGPT", "Planlama • Koordinasyon", Cyan, Modifier.weight(1f))
                        ProviderTile("GEMINI", "Analiz • Doğrulama", Violet, Modifier.weight(1f))
                    }
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ProviderTile("GROK", "Alternatif çözüm • Araştırma", Color(0xFFFFB86B), Modifier.weight(1f))
                        ProviderTile("CLAUDE", "Eleştiri • Kalite kontrol", Color(0xFFFF8F9B), Modifier.weight(1f))
                    }
                }

                Text(
                    "KALICI GÖREV MOTORU  •  CHECKPOINT  •  OTOMATİK DEVAM",
                    color = Color.Gray, fontSize = 8.sp, letterSpacing = .5.sp,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun ProviderTile(name: String, role: String, accent: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxHeight(),
        color = Panel,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(accent, RoundedCornerShape(50)))
                Spacer(Modifier.width(7.dp))
                Text(name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Spacer(Modifier.height(5.dp))
            Text(role, color = Color.LightGray, fontSize = 9.sp, maxLines = 2, lineHeight = 11.sp)
            Spacer(Modifier.height(4.dp))
            Text("BAĞLANTI BEKLİYOR", color = Color.Gray, fontSize = 8.sp, maxLines = 1)
        }
    }
}
