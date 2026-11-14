package com.nexora.app

import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
    var addExpanded by remember { mutableStateOf(false) }
    var attachmentLabel by remember { mutableStateOf("") }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) attachmentLabel = "Fotoğraf: ${uri.lastPathSegment ?: "seçildi"}"
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) attachmentLabel = "Dosya: ${uri.lastPathSegment ?: "seçildi"}"
    }
    val cameraCapture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        attachmentLabel = if (bitmap != null) "Kamera görüntüsü eklendi" else "Kamera işlemi iptal edildi"
    }
    val speechInput = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!spoken.isNullOrBlank()) {
            command = listOf(command, spoken).filter { it.isNotBlank() }.joinToString(" ")
            status = "SESLİ TALİMAT METNE DÖNÜŞTÜRÜLDÜ"
        } else if (result.resultCode == android.app.Activity.RESULT_OK) {
            status = "SES ALGILANAMADI"
        }
    }

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
                                    status = "GÜNCELLEME DENETİMİ HENÜZ ETKİN DEĞİL"
                                }
                            )
                        }
                    }
                }

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
                        if (attachmentLabel.isNotBlank()) {
                            Text(attachmentLabel, color = Cyan, fontSize = 9.sp, maxLines = 1)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box {
                                FilledTonalIconButton(
                                    onClick = { addExpanded = true },
                                    modifier = Modifier.size(if (compact) 42.dp else 48.dp)
                                ) { Icon(Icons.Default.Add, contentDescription = "Ek ekle") }
                                DropdownMenu(expanded = addExpanded, onDismissRequest = { addExpanded = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Kamera") },
                                        onClick = { addExpanded = false; cameraCapture.launch(null) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Fotoğraflar") },
                                        onClick = { addExpanded = false; photoPicker.launch("image/*") }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Dosyalar") },
                                        onClick = { addExpanded = false; filePicker.launch(arrayOf("*/*")) }
                                    )
                                }
                            }
                            FilledTonalIconButton(
                                onClick = {
                                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
                                        putExtra(RecognizerIntent.EXTRA_PROMPT, "NEXORA için talimatınızı söyleyin")
                                    }
                                    try {
                                        speechInput.launch(intent)
                                    } catch (_: Exception) {
                                        status = "BU CİHAZDA SES TANIMA HİZMETİ BULUNAMADI"
                                    }
                                },
                                modifier = Modifier.size(if (compact) 42.dp else 48.dp)
                            ) { Icon(Icons.Default.Mic, contentDescription = "Sesli talimat", tint = Cyan) }
                            Spacer(Modifier.weight(1f))
                            Button(
                                onClick = {
                                    status = if (command.isBlank() && attachmentLabel.isBlank()) "ÖNCE BİR TALİMAT VEYA EK EKLEYİN"
                                    else "SUNUCU BAĞLANTISI YAPILANDIRILMAYI BEKLİYOR"
                                },
                                modifier = Modifier.weight(1.5f).height(if (compact) 46.dp else 52.dp),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Default.Bolt, null)
                                Spacer(Modifier.width(6.dp))
                                Text("GÖREVİ BAŞLAT", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }
                        Text(status, color = Cyan, fontSize = 9.sp, maxLines = 1)
                    }
                }

                Text("YAPAY ZEKÂ EKİBİ", color = Color.White,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.sp, fontSize = 14.sp)

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
                    color = Color.Gray, fontSize = 8.sp, letterSpacing = .5.sp, maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun ProviderTile(name: String, role: String, accent: Color, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxHeight(), color = Panel, shape = RoundedCornerShape(16.dp)) {
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
