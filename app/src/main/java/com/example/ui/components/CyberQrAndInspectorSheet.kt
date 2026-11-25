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
import com.example.ui.SafetyInfo
import com.example.data.model.ChatRoom
import com.example.data.model.DecryptedMessageItem
import com.example.data.model.UserProfile
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
                    contentDescription = "Güvenlik Doğrulama QR Kodu",
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
    safetyInfo: SafetyInfo?,
    inspectedMessage: DecryptedMessageItem?,
    isPeerVerified: Boolean = false,
    hasKeyChangedWarning: Boolean = false,
    onVerifyIdentity: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val safetyNumber = safetyInfo?.safetyNumber
        ?: if (room.isGroup) "Grup sohbetlerinde her üyeyi kendi sohbetinizden doğrulayın." else "Karşı tarafın şifreleme anahtarları henüz yüklenmedi."
    val emojis = safetyInfo?.emojis ?: "—"

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(1.dp, NeonCyberGreen.copy(alpha = 0.45f)),
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
                            text = "Güvenlik ve Doğrulama",
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

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    border = BorderStroke(1.dp, NeonCyberGreen.copy(alpha = 0.5f)),
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Ortak Doğrulama Emojileri",
                            style = MaterialTheme.typography.labelSmall,
                            color = CyberCyan
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = emojis,
                            style = MaterialTheme.typography.headlineMedium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Bu değerler iki tarafın şifreleme anahtarlarından hesaplanır. Karşı cihazda da aynı emojileri ve numarayı görüyorsanız arada kimse yoktur. Anahtarlar değişirse değerler de değişir.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "60 Haneli Güvenlik Numarası",
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
                    Spacer(modifier = Modifier.height(14.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = ObsidianBlack
                        ),
                        border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = NeonCyberGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Mesaj Güvenlik Bilgisi",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NeonCyberGreen,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Gönderen: @${inspectedMessage.senderUsername}",
                                style = MaterialTheme.typography.labelSmall,
                                color = CyberCyan
                            )
                            Text(
                                text = when {
                                    inspectedMessage.isLegacy -> "Eski şifreleme (V1): bu mesaj sunucu tarafından okunabilir durumdaydı"
                                    inspectedMessage.isSignatureVerified -> "İmza doğrulandı: mesaj gönderen tarafından yazılmış ve değiştirilmemiş"
                                    inspectedMessage.isSignerKeyChanged -> "Gönderen anahtarını değiştirmiş: imza eski anahtarla atılmış"
                                    else -> "İmza doğrulanamadı"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Okuyan: ${inspectedMessage.readByCount} kişi",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                            hasKeyChangedWarning -> "Yeni Güvenlik Anahtarını Onayla"
                            isPeerVerified -> "🛡️ Doğrulanmış Kişi"
                            else -> "Kişiyi Doğrulanmış Olarak İşaretle"
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
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Kamerayla Tara, 1 = Benim QR Kodum

    val myQrPayload = remember(myProfile) {
        val handle = myProfile?.username ?: "zerochat_user"
        val uid = myProfile?.userId ?: "uid_$handle"
        // The fingerprint is recomputed from the published keys; the scanner checks it against the server.
        val fp = myProfile?.takeIf { it.hasE2eeKeys }
            ?.let { CryptoEngine.fingerprint(it.publicKey, it.signingKey).filter { c -> c.isDigit() } }
            .orEmpty()
        "zerochat://peer?u=${Uri.encode(handle)}&uid=${Uri.encode(uid)}&fp=${Uri.encode(fp)}"
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
                            text = "QR Kod ile Kişi Ekle & Doğrula",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Kapat")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

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
                    RealCameraQrScannerView(
                        onQrCodeDetected = { rawQrText ->
                            onScanVerified(rawQrText)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp)
                    )
                } else {
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
                                text = "@${myProfile?.username ?: "kullanici"}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = NeonCyberGreen
                            )
                            Text(
                                text = "Karşı cihazın kamerasıyla bu QR kodu okuttuğunuzda kişi anında doğrulanır ve sohbet başlar.",
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
                        text = "Rehberdeki Kişilerle Hızlı Bağlan:",
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
                                        text = "@${peer.username}",
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
                    label = { Text("@kullanici_adi Girin") },
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
                        Text("Doğrula ve Bağlan", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
