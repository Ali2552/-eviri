package com.example.ui.translation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.preferences.EngineType
import com.example.ui.viewmodel.TranslationViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    viewModel: TranslationViewModel,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val apiKey by viewModel.apiKeyInput.collectAsStateWithLifecycle()
    val engineType by viewModel.engineType.collectAsStateWithLifecycle()
    val fontSize by viewModel.fontSize.collectAsStateWithLifecycle()
    val fontColor by viewModel.fontColor.collectAsStateWithLifecycle()
    val bgOpacity by viewModel.bgOpacity.collectAsStateWithLifecycle()
    val liveLevel by viewModel.liveAudioLevel.collectAsStateWithLifecycle()
    val isRunning by viewModel.isServiceRunning.collectAsStateWithLifecycle()

    var isKeyVisible by remember { mutableStateOf(false) }
    val animatedLevel by animateFloatAsState(targetValue = liveLevel, label = "audio_level_anim")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Çeviri ve Görünüm Ayarları",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 5. CANLI SES SEVİYESİ GÖSTERGESİ (VU METER)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("audio_level_card"),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Canlı Ses Seviyesi Göstergesi",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = if (isRunning) "%${(animatedLevel * 100).toInt()}" else "Kapalı",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    LinearProgressIndicator(
                        progress = { if (isRunning) animatedLevel else 0f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .testTag("audio_vu_meter"),
                        color = when {
                            animatedLevel > 0.75f -> Color(0xFFFF5252)
                            animatedLevel > 0.40f -> Color(0xFFFFD600)
                            else -> MaterialTheme.colorScheme.primary
                        },
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = if (isRunning)
                            "Video oynatılırken bu çubuğun hareket etmesi dahili sesin başarıyla yakalandığını gösterir."
                        else
                            "Servis başlatıldığında yakalanan ses dalgaları burada canlı olarak görüntülenir.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Çeviri Motoru Seçimi
            Text(
                text = "Çeviri Motoru",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EngineType.values().forEach { engine ->
                    FilterChip(
                        selected = engineType == engine,
                        onClick = { viewModel.setEngineType(engine) },
                        label = { Text(engine.titleTr) },
                        modifier = Modifier.testTag("engine_chip_${engine.name.lowercase()}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Gemini API Anahtarı
            Text(
                text = "Gemini API Anahtarı (Bulut Çeviri İçin)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Anahtarınız cihazınızın Android Keystore donanımında AES-GCM ile şifrelenir.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = apiKey,
                onValueChange = { viewModel.updateApiKeyInput(it) },
                label = { Text("API Anahtarı") },
                placeholder = { Text("AIzaSy...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("api_key_input"),
                singleLine = true,
                visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Key, contentDescription = null)
                },
                trailingIcon = {
                    IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                        Icon(
                            imageVector = if (isKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (isKeyVisible) "Gizle" else "Göster"
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { viewModel.saveApiKey() },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("save_api_key_button")
            ) {
                Text("API Anahtarını Kaydet")
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Alt Yazı Yazı Boyutu Slider
            Text(
                text = "Yüzen Alt Yazı Boyutu: ${fontSize.toInt()} sp",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Slider(
                value = fontSize,
                onValueChange = { viewModel.setFontSize(it) },
                valueRange = 12f..28f,
                steps = 7,
                modifier = Modifier.testTag("font_size_slider")
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Arka Plan Şeffaflığı (Android 12+ dokunma kuralı: maks %80)
            Text(
                text = "Kutu Şeffaflığı: %${(bgOpacity.coerceAtMost(0.8f) * 100).toInt()} (Maks %80)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Slider(
                value = bgOpacity.coerceAtMost(0.8f),
                onValueChange = { viewModel.setBgOpacity(it.coerceAtMost(0.8f)) },
                valueRange = 0.2f..0.8f,
                steps = 5,
                modifier = Modifier.testTag("opacity_slider")
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Metin Rengi Seçimi
            Text(
                text = "Alt Yazı Metin Rengi",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))

            val colorOptions = listOf(
                0xFFFFFFFF to "Beyaz",
                0xFFFFEB3B to "Sarı",
                0xFF00E676 to "Yeşil",
                0xFF00E5FF to "Açık Mavi",
                0xFFFF4081 to "Pembe"
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                colorOptions.forEach { (hex, name) ->
                    val isSelected = fontColor == hex
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(hex))
                            .border(
                                width = if (isSelected) 3.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                                shape = CircleShape
                            )
                            .clickable { viewModel.setFontColor(hex) }
                            .testTag("color_picker_${name.lowercase()}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}
