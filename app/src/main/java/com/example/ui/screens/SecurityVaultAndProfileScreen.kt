package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.crypto.HardwareKeyStatus
import com.example.data.local.CachedVaultMessageEntity
import com.example.data.model.UserProfile
import com.example.ui.components.CyberQrMatrix
import com.example.ui.theme.AmberTimerWarning
import com.example.ui.theme.BurnCoralRed
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.NeonCyberGreen
import com.example.ui.theme.ObsidianBlack

@Composable
fun SecurityVaultAndProfileScreen(
    myProfile: UserProfile?,
    hardwareKeyStatus: HardwareKeyStatus? = null,
    isVaultUnlocked: Boolean,
    isPanicModeActive: Boolean,
    isScreenShieldEnabled: Boolean = false,
    isOfflineQueueMode: Boolean = false,
    biometricUnlockEnabled: Boolean = true,
    autoLockOnBackground: Boolean = true,
    pendingOutboxCount: Int = 0,
    cachedMessagesCount: Int = 0,
    starredMessages: List<CachedVaultMessageEntity> = emptyList(),
    exportedVaultBackupText: String? = null,
    onToggleScreenShield: (Boolean) -> Unit = {},
    onToggleOfflineQueueMode: (Boolean) -> Unit = {},
    onToggleBiometricUnlock: (Boolean) -> Unit = {},
    onToggleAutoLockOnBackground: (Boolean) -> Unit = {},
    onBiometricUnlockNow: () -> Unit = {},
    onFlushOfflineQueue: () -> Unit = {},
    onExportEncryptedBackup: (String) -> Unit = {},
    onImportEncryptedBackup: (String, String) -> Unit = { _, _ -> },
    onUpdateProfile: (String, String, String) -> Unit,
    onUpdateVaultPins: (String, String) -> Unit,
    onVerifyPin: (String) -> String,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current

    var usernameInput by remember { mutableStateOf(myProfile?.username ?: "") }
    var displayNameInput by remember { mutableStateOf(myProfile?.displayName ?: "") }
    var bioInput by remember { mutableStateOf(myProfile?.bio ?: "") }

    var normalPinInput by remember { mutableStateOf("1337") }
    var panicPinInput by remember { mutableStateOf("9999") }
    var testPinInput by remember { mutableStateOf("") }

    var backupPassword by remember { mutableStateOf("ZeroVault#2026") }
    var importVaultInput by remember { mutableStateOf("") }

    LaunchedEffect(myProfile) {
        if (myProfile != null) {
            usernameInput = myProfile.username
            displayNameInput = myProfile.displayName
            bioInput = myProfile.bio
        }
    }

    LaunchedEffect(exportedVaultBackupText) {
        if (!exportedVaultBackupText.isNullOrBlank() && importVaultInput.isBlank()) {
            importVaultInput = exportedVaultBackupText
        }
    }

    // Physical .zcvault File Saver Launcher (CreateDocument)
    val saveVaultFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            val payloadToWrite = exportedVaultBackupText?.ifBlank { importVaultInput } ?: importVaultInput
            if (payloadToWrite.isNotBlank()) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(payloadToWrite.toByteArray(Charsets.UTF_8))
                    }
                } catch (_: Exception) {
                }
            }
        }
    }

    // Physical .zcvault File Picker Launcher (OpenDocument)
    val openVaultFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val content = input.bufferedReader(Charsets.UTF_8).readText().trim()
                    if (content.isNotBlank()) {
                        importVaultInput = content
                        onImportEncryptedBackup(content, backupPassword)
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = "Profil & Güvenlik Kasası",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Kullanıcı profilinizi, biyometrik kasa kilidini, yıldızlı mesajları ve şifreli yedeklerinizi yönetin.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Starred Encrypted Messages Vault Card (⭐)
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, AmberTimerWarning.copy(alpha = 0.55f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = AmberTimerWarning,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Yıldızlı Mesajlar (${starredMessages.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (starredMessages.isEmpty()) {
                    Text(
                        text = "Henüz yıldızlanmış mesaj yok. Sohbet içindeki bir mesaja dokunup ⭐ simgesiyle buraya sabitleyebilirsiniz.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        starredMessages.take(6).forEach { item ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = ObsidianBlack,
                                border = BorderStroke(1.dp, AmberTimerWarning.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "@${item.senderUsername}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = CyberCyan
                                        )
                                        Text(
                                            text = item.messageType,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = AmberTimerWarning
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = item.decryptedText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = NeonCyberGreen,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Screen Security, Biometric Lock, Auto-Lock & Offline Outbox Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, NeonCyberGreen.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Default.VisibilityOff,
                            contentDescription = null,
                            tint = CyberCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Ekran Görüntüsü Engeli",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Ekran kaydını ve son uygulamalar önizlemesini engeller",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = isScreenShieldEnabled,
                        onCheckedChange = onToggleScreenShield,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ObsidianBlack,
                            checkedTrackColor = NeonCyberGreen
                        ),
                        modifier = Modifier.testTag("screen_shield_switch")
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Default.Fingerprint,
                            contentDescription = null,
                            tint = NeonCyberGreen,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Biyometrik Kasa Kilidi (Parmak İzi)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Parmak izi doğrulaması ile gizli kasayı aç",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = biometricUnlockEnabled,
                        onCheckedChange = onToggleBiometricUnlock,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ObsidianBlack,
                            checkedTrackColor = NeonCyberGreen
                        ),
                        modifier = Modifier.testTag("biometric_unlock_switch")
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = CyberCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Arka Planda Otomatik Kasa Kilitleme",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Uygulama alta alındığında kasayı anında kilitle",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = autoLockOnBackground,
                        onCheckedChange = onToggleAutoLockOnBackground,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ObsidianBlack,
                            checkedTrackColor = CyberCyan
                        ),
                        modifier = Modifier.testTag("auto_lock_background_switch")
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Default.CloudSync,
                            contentDescription = null,
                            tint = AmberTimerWarning,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Çevrimdışı Mesaj Kuyruğu",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Bekleyen mesaj: $pendingOutboxCount adet",
                                style = MaterialTheme.typography.labelSmall,
                                color = AmberTimerWarning
                            )
                        }
                    }
                    Switch(
                        checked = isOfflineQueueMode,
                        onCheckedChange = onToggleOfflineQueueMode,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ObsidianBlack,
                            checkedTrackColor = AmberTimerWarning
                        ),
                        modifier = Modifier.testTag("offline_queue_switch")
                    )
                }

                if (pendingOutboxCount > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onFlushOfflineQueue,
                        border = BorderStroke(1.dp, NeonCyberGreen),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Kuyruktaki $pendingOutboxCount Mesajı Şimdi Gönder",
                            color = NeonCyberGreen,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // QR & Safety Number Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, NeonCyberGreen.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = NeonCyberGreen,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Kişisel QR Kodum & Güvenlik Numaram",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                val personalQrUri = remember(myProfile) {
                    val handle = myProfile?.username ?: "zerochat_user"
                    val uid = myProfile?.userId ?: "uid_$handle"
                    val fp = myProfile?.safetyFingerprint ?: ""
                    val pk = myProfile?.publicKey ?: "ZEROCHAT_KEY"
                    "zerochat://peer?u=${android.net.Uri.encode(handle)}&uid=${android.net.Uri.encode(uid)}&fp=${android.net.Uri.encode(fp)}&pk=${android.net.Uri.encode(pk)}"
                }

                CyberQrMatrix(
                    seedData = personalQrUri
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "@${myProfile?.username ?: "kullanici"}",
                    style = MaterialTheme.typography.titleMedium,
                    color = NeonCyberGreen,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "60 Haneli Güvenlik Numaram:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ObsidianBlack,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = myProfile?.safetyFingerprint ?: "94810 28491 77261 00291\n88412 19283 44910 55821\n77319 00912 66418 33109",
                        fontFamily = JetBrainsMonoFontFamily,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Encrypted .zcvault Backup & Restore Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.55f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Backup,
                        contentDescription = null,
                        tint = CyberCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Şifreli Mesaj Yedekleme (.zcvault)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Kasadaki $cachedMessagesCount mesajı belirlediğiniz parola ile şifreleyip cihazınıza dosya olarak indirin veya geri yükleyin.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = backupPassword,
                    onValueChange = { backupPassword = it },
                    label = { Text("Yedekleme Parolası") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("backup_password_input")
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { onExportEncryptedBackup(backupPassword) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyberCyan,
                        contentColor = Color(0xFF001F26)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("export_backup_button")
                ) {
                    Icon(imageVector = Icons.Default.Backup, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Şifreli Yedek Oluştur", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            onExportEncryptedBackup(backupPassword)
                            saveVaultFileLauncher.launch("zerochat_backup_${System.currentTimeMillis()}.zcvault")
                        },
                        border = BorderStroke(1.dp, CyberCyan),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("download_zcvault_file_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = null,
                            tint = CyberCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Dosya İndir", color = CyberCyan)
                    }

                    OutlinedButton(
                        onClick = {
                            openVaultFileLauncher.launch(arrayOf("*/*"))
                        },
                        border = BorderStroke(1.dp, NeonCyberGreen),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("upload_zcvault_file_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileUpload,
                            contentDescription = null,
                            tint = NeonCyberGreen,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Dosya Seç", color = NeonCyberGreen)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = importVaultInput,
                    onValueChange = { importVaultInput = it },
                    label = { Text(".zcvault Şifreli Yedek Verisi") },
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("import_backup_input")
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        if (importVaultInput.isNotBlank()) {
                            onImportEncryptedBackup(importVaultInput, backupPassword)
                        }
                    },
                    border = BorderStroke(1.dp, NeonCyberGreen),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("restore_backup_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Restore,
                        contentDescription = null,
                        tint = NeonCyberGreen
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Yedeği Çöz ve Geri Yükle",
                        color = NeonCyberGreen,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Global @username Directory Editor Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        tint = CyberCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Profil ve @kullanici_adi Bilgilerim",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = usernameInput,
                    onValueChange = { usernameInput = it },
                    label = { Text("Kullanıcı Adı (@ olmadan)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_username_input")
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = displayNameInput,
                    onValueChange = { displayNameInput = it },
                    label = { Text("Görünen Ad") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = bioInput,
                    onValueChange = { bioInput = it },
                    label = { Text("Hakkımda / Durum Notu") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { onUpdateProfile(usernameInput, displayNameInput, bioInput) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonCyberGreen,
                        contentColor = Color(0xFF002110)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("save_profile_button")
                ) {
                    Icon(imageVector = Icons.Default.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Profil Bilgilerini Güncelle", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Secret Vault & Panic PIN Configuration Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, AmberTimerWarning.copy(alpha = 0.6f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = AmberTimerWarning,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Gizli Kasa PIN & Acil Durum PIN Kodu",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Durum: " + when {
                        isPanicModeActive -> "🚨 GİZLEME MODU AKTİF"
                        isVaultUnlocked -> "🔓 Gizli Kasa Kilidi Açık"
                        else -> "🔒 Gizli Kasa Kilitli"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isPanicModeActive) BurnCoralRed else NeonCyberGreen
                )
                Spacer(modifier = Modifier.height(10.dp))

                if (biometricUnlockEnabled && !isVaultUnlocked) {
                    OutlinedButton(
                        onClick = onBiometricUnlockNow,
                        border = BorderStroke(1.dp, NeonCyberGreen),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("vault_biometric_unlock_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fingerprint,
                            contentDescription = null,
                            tint = NeonCyberGreen
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Parmak İzi ile Kasayı Şimdi Aç",
                            color = NeonCyberGreen,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = normalPinInput,
                        onValueChange = { normalPinInput = it.take(8) },
                        label = { Text("Normal Kasa PIN") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = panicPinInput,
                        onValueChange = { panicPinInput = it.take(8) },
                        label = { Text("Acil Gizleme PIN") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = { onUpdateVaultPins(normalPinInput, panicPinInput) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AmberTimerWarning,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("PIN Kodlarını Kaydet", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = testPinInput,
                        onValueChange = { testPinInput = it.take(8) },
                        label = { Text("PIN Kodu Girin") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            onVerifyPin(testPinInput)
                            testPinInput = ""
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BurnCoralRed,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(imageVector = Icons.Default.WarningAmber, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Uygula")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        OutlinedButton(
            onClick = onSignOut,
            border = BorderStroke(1.dp, BurnCoralRed),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("sign_out_button")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Logout,
                contentDescription = null,
                tint = BurnCoralRed
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Oturumu Kapat",
                color = BurnCoralRed,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(90.dp))
    }
}
