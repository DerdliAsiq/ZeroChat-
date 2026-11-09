package com.example.ui.components

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.crypto.CryptoEngine
import com.example.data.model.ChatRoom
import com.example.data.model.DecryptedMessageItem
import com.example.data.model.UserProfile
import com.example.ui.theme.AmberTimerWarning
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.NeonCyberGreen
import com.example.ui.theme.ObsidianBlack

@Composable
fun CyberQrMatrix(
    seedData: String,
    modifier: Modifier = Modifier.size(168.dp),
    activeColor: Color = NeonCyberGreen
) {
    val hashHex = remember(seedData) {
        CryptoEngine.sha256Hex(seedData) + CryptoEngine.sha256Hex("ALT::$seedData")
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = ObsidianBlack,
        border = BorderStroke(1.5.dp, activeColor.copy(alpha = 0.7f)),
        modifier = modifier
    ) {
        Canvas(
            modifier = Modifier
                .padding(14.dp)
        ) {
            val grid = 15
            val cellW = size.width / grid
            val cellH = size.height / grid

            fun isFinderPattern(r: Int, c: Int): Boolean {
                val topLeft = r in 0..4 && c in 0..4
                val topRight = r in 0..4 && c in (grid - 5) until grid
                val bottomLeft = r in (grid - 5) until grid && c in 0..4
                return topLeft || topRight || bottomLeft
            }

            fun drawFinder(startR: Int, startC: Int) {
                for (dr in 0..4) {
                    for (dc in 0..4) {
                        val isBorder = dr == 0 || dr == 4 || dc == 0 || dc == 4
                        val isCenter = dr == 2 && dc == 2
                        if (isBorder || isCenter) {
                            drawRoundRect(
                                color = if (isCenter) CyberCyan else activeColor,
                                topLeft = Offset((startC + dc) * cellW, (startR + dr) * cellH),
                                size = Size(cellW * 0.9f, cellH * 0.9f),
                                cornerRadius = CornerRadius(2f, 2f)
                            )
                        }
                    }
                }
            }

            drawFinder(0, 0)
            drawFinder(0, grid - 5)
            drawFinder(grid - 5, 0)

            var bitIndex = 0
            for (r in 0 until grid) {
                for (c in 0 until grid) {
                    if (isFinderPattern(r, c)) continue
                    val charVal = hashHex[bitIndex % hashHex.length].digitToIntOrNull(16) ?: 0
                    val bitOn = ((charVal + r + c) % 2) == 0
                    bitIndex++
                    if (bitOn) {
                        drawRoundRect(
                            color = activeColor,
                            topLeft = Offset(c * cellW, r * cellH),
                            size = Size(cellW * 0.85f, cellH * 0.85f),
                            cornerRadius = CornerRadius(2f, 2f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RoomSafetyAndCipherInspectorDialog(
    room: ChatRoom,
    inspectedMessage: DecryptedMessageItem?,
    isPeerVerified: Boolean = false,
    hasKeyChangedWarning: Boolean = false,
    onVerifyIdentity: () -> Unit = {},
    onSimulateKeyChange: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val safetyNumber = remember(room.roomKeyHint) {
        CryptoEngine.generateIdentityMaterial(room.id, room.roomKeyHint).second
    }
    val emojis = remember(room.roomKeyHint) {
        CryptoEngine.deriveSafetyEmojis(room.roomKeyHint)
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(1.dp, NeonCyberGreen.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = NeonCyberGreen,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "ECDH + AES-256-GCM Denetçi",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_inspector_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Kapat"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                CyberQrMatrix(seedData = "${room.id}::${room.roomKeyHint}")

                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Text(
                        text = "Anahtar Emojileri:  $emojis",
                        style = MaterialTheme.typography.labelLarge,
                        color = NeonCyberGreen,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = ObsidianBlack,
                    border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "Anahtar Değişimi: ECDH P-256 (secp256r1) + SHA-256 Ratchet",
                        style = MaterialTheme.typography.labelSmall,
                        color = CyberCyan,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "60 Haneli Güvenlik Numarası (Safety Number)",
                    style = MaterialTheme.typography.labelSmall,
                    color = CyberCyan
                )
                Spacer(modifier = Modifier.height(4.dp))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ObsidianBlack,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = safetyNumber,
                        fontFamily = JetBrainsMonoFontFamily,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                if (inspectedMessage != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = ObsidianBlack
                        ),
                        border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = CyberCyan,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Zarf Denetimi (AES-256-GCM)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = CyberCyan
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = NeonCyberGreen.copy(alpha = 0.16f)
                                ) {
                                    Text(
                                        text = "Ratchet Adımı #${inspectedMessage.ratchetStep}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NeonCyberGreen,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Ağ Üzerindeki Şifreli Yük (Ciphertext):",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = inspectedMessage.ciphertext,
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.labelSmall,
                                color = NeonCyberGreen
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "GCM Başlatma Vektörü (96-bit IV): ${inspectedMessage.iv}",
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "SHA-256 İmza: ${inspectedMessage.signatureHash}",
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Okundu Bilgisi: ✓✓ ${inspectedMessage.readByCount} Düğüm Tarafından Çözüldü",
                                style = MaterialTheme.typography.labelSmall,
                                color = AmberTimerWarning
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Cihazda Çözülen Metin (Plaintext):",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = inspectedMessage.decryptedText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onVerifyIdentity,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPeerVerified && !hasKeyChangedWarning) NeonCyberGreen else CyberCyan,
                        contentColor = Color(0xFF002110)
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("verify_peer_identity_button")
                ) {
                    Icon(
                        imageVector = if (isPeerVerified && !hasKeyChangedWarning) Icons.Default.CheckCircle else Icons.Default.QrCode2,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when {
                            hasKeyChangedWarning -> "Yeni Anahtarı Onayla ve MITM Uyarısını Kaldır"
                            isPeerVerified -> "🛡️ Doğrulanmış Düğüm (Verified Mühürlü)"
                            else -> "Parmak İzini Doğrula ve Mühürle (Verify)"
                        },
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onSimulateKeyChange,
                    border = BorderStroke(1.dp, AmberTimerWarning),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("simulate_mitm_key_change_button")
                ) {
                    Text(
                        text = "⚠️ MITM Anahtar Değişimi Uyarısını Test Et",
                        color = AmberTimerWarning,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Composable
fun QrScannerAndVerifierDialog(
    availablePeers: List<UserProfile>,
    onDismiss: () -> Unit,
    onScanVerified: (String) -> Unit
) {
    var manualKeyOrHandle by remember { mutableStateOf("") }
    var cameraAuthorized by remember { mutableStateOf(false) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        cameraAuthorized = granted
    }

    val infiniteTransition = rememberInfiniteTransition(label = "qr_laser")
    val laserProgress by infiniteTransition.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser_y"
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, NeonCyberGreen),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            tint = NeonCyberGreen,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Siber QR Tarayıcı & Doğrulama",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Kapat")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Interactive Cyber Laser Viewfinder
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = ObsidianBlack,
                    border = BorderStroke(1.5.dp, CyberCyan.copy(alpha = 0.7f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(175.dp)
                        .clickable {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val strokeW = 4.dp.toPx()
                            val cornerLen = 26.dp.toPx()
                            val pad = 18.dp.toPx()

                            // Top-Left Bracket
                            drawLine(NeonCyberGreen, Offset(pad, pad), Offset(pad + cornerLen, pad), strokeW)
                            drawLine(NeonCyberGreen, Offset(pad, pad), Offset(pad, pad + cornerLen), strokeW)
                            // Top-Right Bracket
                            drawLine(NeonCyberGreen, Offset(size.width - pad, pad), Offset(size.width - pad - cornerLen, pad), strokeW)
                            drawLine(NeonCyberGreen, Offset(size.width - pad, pad), Offset(size.width - pad, pad + cornerLen), strokeW)
                            // Bottom-Left Bracket
                            drawLine(NeonCyberGreen, Offset(pad, size.height - pad), Offset(pad + cornerLen, size.height - pad), strokeW)
                            drawLine(NeonCyberGreen, Offset(pad, size.height - pad), Offset(pad, size.height - pad - cornerLen), strokeW)
                            // Bottom-Right Bracket
                            drawLine(NeonCyberGreen, Offset(size.width - pad, size.height - pad), Offset(size.width - pad - cornerLen, size.height - pad), strokeW)
                            drawLine(NeonCyberGreen, Offset(size.width - pad, size.height - pad), Offset(size.width - pad, size.height - pad - cornerLen), strokeW)

                            // Animated Laser Line
                            val yPos = size.height * laserProgress
                            drawLine(
                                color = CyberCyan,
                                start = Offset(pad * 1.4f, yPos),
                                end = Offset(size.width - pad * 1.4f, yPos),
                                strokeWidth = 2.5.dp.toPx()
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = if (cameraAuthorized) NeonCyberGreen else CyberCyan,
                                modifier = Modifier.size(30.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (cameraAuthorized) {
                                    "Kamera Tarayıcı Aktif • QR Kodunu Hizalayın"
                                } else {
                                    "Kamera Sensörünü Etkinleştirmek İçin Dokunun"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = NeonCyberGreen,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "ECDH P-256 Açık Anahtar ve 60 Haneli Parmak İzi Okuyucu",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                if (availablePeers.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Yakındaki Düğümlerin QR Kimliğini Doğrula:",
                        style = MaterialTheme.typography.labelSmall,
                        color = CyberCyan,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(availablePeers, key = { it.userId }) { peer ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                border = BorderStroke(1.dp, NeonCyberGreen.copy(alpha = 0.5f)),
                                modifier = Modifier.clickable {
                                    onScanVerified(peer.username)
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Key,
                                        contentDescription = null,
                                        tint = NeonCyberGreen,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "@${peer.username} QR Tara",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NeonCyberGreen
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = manualKeyOrHandle,
                    onValueChange = { manualKeyOrHandle = it },
                    label = { Text("@kullanici_adi veya ZC-ECDH-P256 Anahtarı Yapıştır") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("qr_manual_input")
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(onClick = onDismiss) {
                        Text("Kapat")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (manualKeyOrHandle.isNotBlank()) {
                                onScanVerified(manualKeyOrHandle)
                            } else if (availablePeers.isNotEmpty()) {
                                onScanVerified(availablePeers.first().username)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonCyberGreen,
                            contentColor = Color(0xFF002110)
                        ),
                        modifier = Modifier.testTag("qr_verify_confirm_button")
                    ) {
                        Text("QR Doğrula ve Bağlan", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
