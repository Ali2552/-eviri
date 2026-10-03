package com.example.ui.translation

import android.Manifest
import android.app.Activity
import android.content.Context
import android.media.projection.MediaProjectionManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.preferences.AudioSourceType
import com.example.data.preferences.SubtitleMode
import com.example.ui.viewmodel.TranslationViewModel

@Composable
fun TranslationScreen(
    viewModel: TranslationViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val subtitleMode by viewModel.subtitleMode.collectAsStateWithLifecycle()
    val audioSource by viewModel.audioSource.collectAsStateWithLifecycle()
    val currentSubtitle by viewModel.currentSubtitle.collectAsStateWithLifecycle()
    val isRunning by viewModel.isServiceRunning.collectAsStateWithLifecycle()
    val detectedLang by viewModel.detectedLanguage.collectAsStateWithLifecycle()
    val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()
    val showNotifWarning by viewModel.showNotificationPermissionWarning.collectAsStateWithLifecycle()

    var showSettingsSheet by remember { mutableStateOf(false) }
    var showPermissionWizard by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(userMessage) {
        userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearUserMessage()
        }
    }

    // MediaProjection Başlatıcısı (Dahili Ekran/Video Ses Yakalama)
    val mediaProjectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            viewModel.startService(result.resultCode, result.data)
        }
    }

    // Android 13+ Bildirim İzni İsteme (Zorunlu POST_NOTIFICATIONS)
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            viewModel.setNotificationWarning(true)
        } else {
            viewModel.setNotificationWarning(false)
        }
    }

    // Mikrofon İzni İsteme (RECORD_AUDIO)
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    val hasNotifPermission = viewModel.hasNotificationPermission()
    val hasMicPermission = viewModel.hasRecordAudioPermission()
    val hasOverlayPermission = viewModel.canDrawOverlays()

    val allCorePermissionsGranted = (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || hasNotifPermission) &&
            hasOverlayPermission

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Başlık Çubuğu
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Video Alt Yazı",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Android 10-14 Uyumlu Dahili Ses Çevirisi",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = { showSettingsSheet = true },
                    modifier = Modifier.testTag("open_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Ayarlar",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 1. Android 13 Bildirim İzni Reddedildi Uyarısı
            AnimatedVisibility(visible = showNotifWarning || (!hasNotifPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .testTag("notification_warning_banner"),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Bildirim İzni Kapalı",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = "Bildirim izni kapalı olduğunda çubuktaki Durdur butonu görünmeyebilir.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        FilledTonalButton(
                            onClick = { viewModel.openAppNotificationSettings(context) },
                            modifier = Modifier.testTag("open_notification_settings_button")
                        ) {
                            Text("Ayarlar")
                        }
                    }
                }
            }

            // 7. İLK AÇILIŞTA İZİN SİHİRBAZI KARTI
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp)
                    .testTag("permission_wizard_card"),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (allCorePermissionsGranted) Icons.Default.CheckCircle else Icons.Default.Info,
                                contentDescription = null,
                                tint = if (allCorePermissionsGranted) Color(0xFF00E676) else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "İzin Sihirbazı (Android 13 / 10-14)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        TextButton(onClick = { showPermissionWizard = !showPermissionWizard }) {
                            Text(if (showPermissionWizard) "Daralt" else if (allCorePermissionsGranted) "Tamamlandı" else "Görüntüle")
                        }
                    }

                    AnimatedVisibility(visible = showPermissionWizard || !allCorePermissionsGranted) {
                        Column(modifier = Modifier.padding(top = 10.dp)) {
                            // Adım 1: Bildirim İzni (Android 13 zorunlu)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                PermissionWizardStepItem(
                                    stepNumber = 1,
                                    title = "Bildirim İzni (Android 13+)",
                                    description = "Servisi arka planda kontrol etmek ve bildirim panelinde Durdur butonu için gereklidir.",
                                    isGranted = hasNotifPermission,
                                    icon = Icons.Default.Notifications,
                                    buttonText = "İzin Ver",
                                    onRequest = {
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            // Adım 2: Mikrofon İzni (RECORD_AUDIO)
                            PermissionWizardStepItem(
                                stepNumber = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) 2 else 1,
                                title = "Mikrofon İzni (Yedek Mod)",
                                description = "Yalnızca video uygulaması iç ses yakalamayı kısıtlarsa yedek olarak ortam sesini dinler.",
                                isGranted = hasMicPermission,
                                icon = Icons.Default.Mic,
                                buttonText = "İzin Ver",
                                onRequest = {
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Adım 3: Yüzen Pencere İzni (SYSTEM_ALERT_WINDOW)
                            PermissionWizardStepItem(
                                stepNumber = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) 3 else 2,
                                title = "Yüzen Pencere İzni (Overlay)",
                                description = "YouTube, TikTok vb. uygulamaların üzerinde sürüklenebilir alt yazı kutusu açar.",
                                isGranted = hasOverlayPermission,
                                icon = Icons.Default.Widgets,
                                buttonText = "İzin Ver",
                                onRequest = {
                                    viewModel.requestOverlayPermission(context)
                                }
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Adım 4: Ekran/Ses Kaydı (MediaProjection)
                            PermissionWizardStepItem(
                                stepNumber = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) 4 else 3,
                                title = "Dahili Ses Yakalama (MediaProjection)",
                                description = "Diğer uygulamaların hoparlör çıkış sesini doğrudan yakalamak için ekran/ses izni istenir.",
                                isGranted = isRunning,
                                icon = Icons.Default.ScreenShare,
                                buttonText = if (isRunning) "Aktif" else "Başlatınca İstenecek",
                                onRequest = {}
                            )
                        }
                    }
                }
            }

            // Canlı Alt Yazı Ekran Kartı (Hero Card)
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("live_subtitle_card"),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Durum Rozeti
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isRunning) Color(0xFF00E676)
                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isRunning) "DAHİLİ SES DİNLENİYOR" else "BEKLEMEDE",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isRunning) Color(0xFF00E676) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Dil Göstergesi
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "Otomatik Algıla → Türkçe",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Canlı Metin Alanı (2 satır çerçevesi)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .border(
                                1.dp,
                                if (isRunning) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                else Color.Transparent,
                                RoundedCornerShape(14.dp)
                            )
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = currentSubtitle,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 17.sp,
                                lineHeight = 24.sp
                            ),
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Notlara Kaydet ve Geçmişi Temizle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.clearHistory() },
                            modifier = Modifier.testTag("clear_subtitles_button")
                        ) {
                            Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Temizle")
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = { viewModel.saveCurrentSubtitleToNotes {} },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary
                            ),
                            modifier = Modifier.testTag("save_to_notes_button")
                        ) {
                            Icon(imageVector = Icons.Default.BookmarkAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Notlara Kaydet")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 1) ÇALIŞMA MODU SEÇİMİ
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Çalışma Modu",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SubtitleMode.values().forEach { mode ->
                        val isSelected = subtitleMode == mode
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.setSubtitleMode(mode) },
                            label = { Text(mode.titleTr) },
                            leadingIcon = {
                                when (mode) {
                                    SubtitleMode.SUBTITLE_ONLY -> Icon(Icons.Default.Subtitles, contentDescription = null, modifier = Modifier.size(16.dp))
                                    SubtitleMode.DUBBING_ONLY -> Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                                    SubtitleMode.BOTH -> Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            },
                            modifier = Modifier.weight(1f).testTag("mode_${mode.name.lowercase()}"),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2) SES KAYNAĞI SEÇİMİ
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Ses Kaynağı",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AudioSourceType.values().forEach { source ->
                        val isSelected = audioSource == source
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.setAudioSource(source) },
                            label = { Text(source.titleTr) },
                            leadingIcon = {
                                if (source == AudioSourceType.MEDIA_PROJECTION) {
                                    Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                                } else {
                                    Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            },
                            modifier = Modifier.weight(1f).testTag("source_${source.name.lowercase()}"),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        )
                    }
                }

                // Bilgilendirme Kutusu
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (audioSource == AudioSourceType.MEDIA_PROJECTION)
                                "Dahili ses: 44.1 kHz stereo yakalanır, 16 kHz mono'ya dönüştürülür. Video sesi kesilmez."
                            else
                                "Mikrofon yedeği: Sadece iç ses yakalamayı engelleyen uygulamalarda ortam sesini almak için kullanılır.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ANA BAŞLAT / DURDUR BUTONU
            Button(
                onClick = {
                    if (isRunning) {
                        viewModel.stopService()
                    } else {
                        if (!hasOverlayPermission) {
                            viewModel.requestOverlayPermission(context)
                            return@Button
                        }

                        if (audioSource == AudioSourceType.MEDIA_PROJECTION) {
                            val mpManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                            mediaProjectionLauncher.launch(mpManager.createScreenCaptureIntent())
                        } else {
                            if (hasMicPermission) {
                                viewModel.startService()
                            } else {
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("toggle_service_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(
                    imageVector = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isRunning) "Çeviriyi Durdur" else "Çeviriyi Başlat",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    if (showSettingsSheet) {
        SettingsSheet(
            viewModel = viewModel,
            onDismiss = { showSettingsSheet = false }
        )
    }
}

@Composable
fun PermissionWizardStepItem(
    stepNumber: Int,
    title: String,
    description: String,
    isGranted: Boolean,
    icon: ImageVector,
    buttonText: String,
    onRequest: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isGranted) Color(0xFF00E676).copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isGranted) Icons.Default.CheckCircle else icon,
            contentDescription = null,
            tint = if (isGranted) Color(0xFF00E676) else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "$stepNumber. $title",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (isGranted) Color(0xFF00E676) else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        if (!isGranted) {
            FilledTonalButton(
                onClick = onRequest,
                modifier = Modifier.testTag("grant_perm_step_$stepNumber")
            ) {
                Text(buttonText)
            }
        }
    }
}
