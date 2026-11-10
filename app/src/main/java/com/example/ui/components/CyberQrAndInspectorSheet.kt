package com.example.ui.components

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
import com.example.util.RealCameraQrScannerView
import com.example.util.generateRealQrCodeBitmap

@Composable
fun CyberQrMatrix(
    seedData: String,
    modifier: Modifier = Modifier.size(176.dp),
    activeColor: Color = NeonCyberGreen
) {
    val qrPayload = remember(seedData) {
        if (seedData.startsWith("zerochat://", ignoreCase = true) ||
            seedData.startsWith("ZEROCHAT://", ignoreCase = true)
        ) {
            seedData
        } else {
            "ZEROCHAT://VERIFY/${Uri.encode(seedData)}"
        }
    }
    val qrBitmap = remember(qrPayload) {
        generateRealQrCodeBitmap(content = qrPayload, sizePx = 512)
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = ObsidianBlack,
        border = BorderStroke(1.5.dp, activeColor.copy(alpha = 0.8f)),
        modifier = modifier
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp)
        ) {
            if (qrBitmap != null) {
                Image(
                    bitmap = qrBitmap,
                    contentDescription = "Gerçek Taranabilir Kriptografik QR Kod",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
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
    onDismiss: () -> Unit
) {
    val safetyNumber = remember(room.roomKeyHint) {
        CryptoEngine.generateIdentityMaterial(room.id, room.roomKeyHint).second
    }
    val emojis = remember(room.roomKeyHint) {
        CryptoEngine.deriveSafetyEmojis(room.roomKeyHint)
    }
    val peerHandle = remember(room.title) {
        room.title.removePrefix("@").trim()
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

                CyberQrMatrix(
                    seedData = "zerochat://peer?u=${Uri.encode(peerHandle)}&fp=${Uri.encode(safetyNumber)}"
                )

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
            }
        }
    }
}

@Composable
fun QrScannerAndVerifierDialog(
    myProfile: UserProfile? = null,
    availablePeers: List<UserProfile>,
    onDismiss: () -> Unit,
    onScanVerified: (String) -> Unit
) {
    var manualKeyOrHandle by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Canlı Kamera QR Tarayıcı, 1 = Benim QR Kodum

    val myQrPayload = remember(myProfile) {
        val handle = myProfile?.username ?: "zerochat_node"
        val uid = myProfile?.userId ?: "uid_$handle"
        val fp = myProfile?.safetyFingerprint ?: CryptoEngine.sha256Hex(handle).take(30)
        val pk = myProfile?.publicKey ?: "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE"
        "zerochat://peer?u=${Uri.encode(handle)}&uid=${Uri.encode(uid)}&fp=${Uri.encode(fp)}&pk=${Uri.encode(pk)}"
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, NeonCyberGreen),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
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
                            text = "Canlı Kamera QR & Kimlik",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Kapat")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Mode Switcher: Live Camera Scanner vs My QR Code
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text("Kamerayla Tara", style = MaterialTheme.typography.labelMedium) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = NeonCyberGreen
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.QrCode2,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text("QR Kodum", style = MaterialTheme.typography.labelMedium) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = CyberCyan
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (selectedTab == 0) {
                    // REAL CAMERAX + ZXING QR SCANNER VIEW
                    RealCameraQrScannerView(
                        onQrCodeDetected = { rawQrText ->
                            onScanVerified(rawQrText)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp)
                    )
                } else {
                    // MY REAL SCANNABLE QR CODE
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = ObsidianBlack,
                        border = BorderStroke(1.5.dp, CyberCyan),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CyberQrMatrix(
                                seedData = myQrPayload,
                                modifier = Modifier.size(200.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "@${myProfile?.username ?: "zerochat_node"}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = NeonCyberGreen
                            )
                            Text(
                                text = "Karşı cihazın kamerasıyla bu QR kodu okuttuğunuzda ECDH P-256 anahtarınız doğrulanır ve anında eşleşir.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                if (availablePeers.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Dizindeki Düğümleri Hızlı Doğrula & Eşleştir:",
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
                                        text = "@${peer.username} Bağlan",
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
                    label = { Text("@kullanici_adi veya ZC-ECDH-P256 Anahtarı Gir") },
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
                        Text("Doğrula ve Eşleş", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
