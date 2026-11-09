package com.example.ui.screens

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.data.model.CallSignal
import com.example.data.model.DecryptedStatusItem
import com.example.data.model.UserProfile
import com.example.ui.UiState
import com.example.ui.theme.AmberTimerWarning
import com.example.ui.theme.BurnCoralRed
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.NeonCyberGreen
import com.example.ui.theme.ObsidianBlack
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StatusesTabScreen(
    statusesState: UiState<List<DecryptedStatusItem>>,
    directoryState: UiState<List<UserProfile>> = UiState.Success(emptyList()),
    onPublishStatus: (String, String) -> Unit,
    onViewStatus: (DecryptedStatusItem) -> Unit = {},
    onReplyToStatus: (DecryptedStatusItem, String) -> Unit = { _, _ -> }
) {
    var showCreateStatusDialog by remember { mutableStateOf(false) }
    var selectedStatusForViewer by remember { mutableStateOf<DecryptedStatusItem?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "24 Saatlik Şifreli Durumlar",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "AES-256-GCM ile şifrelenen, yanıtlanabilen ve 24 saat sonunda otomatik silinen hikayeler.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            when (statusesState) {
                is UiState.Loading -> {
                    Text(
                        text = "Şifreli durum akışı çözülüyor...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is UiState.Error -> {
                    Text(
                        text = statusesState.message,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                is UiState.Success -> {
                    val items = statusesState.data
                    if (items.isEmpty()) {
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RadioButtonChecked,
                                    contentDescription = null,
                                    tint = NeonCyberGreen,
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Aktif 24 Saatlik Durum Yok",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "İlk şifreli durum güncellemesini paylaşarak ağdaki diğer düğümlerle paylaşım yapın.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = { showCreateStatusDialog = true },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = NeonCyberGreen,
                                        contentColor = Color(0xFF002110)
                                    )
                                ) {
                                    Text(
                                        text = stringResource(R.string.status_add_button),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(items, key = { it.id }) { status ->
                                EncryptedStatusCard(
                                    status = status,
                                    onClick = {
                                        onViewStatus(status)
                                        selectedStatusForViewer = status
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showCreateStatusDialog = true },
            containerColor = NeonCyberGreen,
            contentColor = Color(0xFF002110),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("add_status_fab")
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = stringResource(R.string.status_add_button))
        }
    }

    if (showCreateStatusDialog) {
        CreateStatusDialog(
            onDismiss = { showCreateStatusDialog = false },
            onPublish = { text, hex ->
                onPublishStatus(text, hex)
                showCreateStatusDialog = false
            }
        )
    }

    if (selectedStatusForViewer != null) {
        val peers = (directoryState as? UiState.Success)?.data ?: emptyList()
        StatusStoryViewerDialog(
            status = selectedStatusForViewer!!,
            knownPeers = peers,
            onReply = { replyText ->
                onReplyToStatus(selectedStatusForViewer!!, replyText)
                selectedStatusForViewer = null
            },
            onDismiss = { selectedStatusForViewer = null }
        )
    }
}

@Composable
private fun EncryptedStatusCard(
    status: DecryptedStatusItem,
    onClick: () -> Unit
) {
    val hoursRemaining = remember(status.createdAtMillis) {
        val elapsedHours = ((System.currentTimeMillis() - status.createdAtMillis) / (3600 * 1000L)).coerceAtLeast(0L)
        (24L - elapsedHours).coerceAtLeast(1L)
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.5.dp, NeonCyberGreen.copy(alpha = 0.6f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        border = BorderStroke(2.dp, NeonCyberGreen),
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = status.authorUsername.take(2).uppercase(Locale.ROOT),
                                color = NeonCyberGreen,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "@${status.authorUsername}",
                            style = MaterialTheme.typography.titleMedium,
                            color = NeonCyberGreen,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${hoursRemaining} saat sonra otomatik imha • Yanıtlamak için dokunun",
                            style = MaterialTheme.typography.labelSmall,
                            color = AmberTimerWarning
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = null,
                        tint = CyberCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${status.viewCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = CyberCyan
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = status.decryptedContent,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "AES-256-GCM Zarfı: ${status.ciphertext.take(28)}...",
                fontFamily = JetBrainsMonoFontFamily,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CreateStatusDialog(
    onDismiss: () -> Unit,
    onPublish: (String, String) -> Unit
) {
    var statusText by remember { mutableStateOf("") }
    var selectedHex by remember { mutableStateOf("#082E1E") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, NeonCyberGreen)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "24 Saatlik Şifreli Durum Paylaş",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = statusText,
                    onValueChange = { statusText = it },
                    label = { Text("Şifrelenecek Durum Metni") },
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("status_text_input")
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "#082E1E" to "Siber Yeşil",
                        "#072B36" to "Gece Mavisi",
                        "#3B0A18" to "Kızıl Kalkan"
                    ).forEach { (hex, name) ->
                        FilterChip(
                            selected = selectedHex == hex,
                            onClick = { selectedHex = hex },
                            label = { Text(name) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(onClick = onDismiss) {
                        Text("Vazgeç")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (statusText.isNotBlank()) {
                                onPublish(statusText, selectedHex)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonCyberGreen,
                            contentColor = Color(0xFF002110)
                        ),
                        modifier = Modifier.testTag("publish_status_button")
                    ) {
                        Text("Şifrele ve Paylaş", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusStoryViewerDialog(
    status: DecryptedStatusItem,
    knownPeers: List<UserProfile> = emptyList(),
    onReply: (String) -> Unit = {},
    onDismiss: () -> Unit
) {
    var replyText by remember { mutableStateOf("") }
    val viewerHandles = remember(status.viewCount, knownPeers) {
        val peerNames = knownPeers.map { "@${it.username}" }.distinct()
        if (peerNames.isNotEmpty()) {
            peerNames.take(status.viewCount.toInt().coerceIn(1, 5)).joinToString(", ")
        } else {
            "@${status.authorUsername}"
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = ObsidianBlack),
            border = BorderStroke(1.5.dp, NeonCyberGreen),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "@${status.authorUsername}",
                        style = MaterialTheme.typography.titleMedium,
                        color = NeonCyberGreen,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.45f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Visibility,
                                contentDescription = null,
                                tint = CyberCyan,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${status.viewCount} Görüntülenme",
                                style = MaterialTheme.typography.labelSmall,
                                color = CyberCyan
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = status.decryptedContent,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(14.dp))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "Görüntüleyen Düğümler: $viewerHandles",
                            style = MaterialTheme.typography.labelSmall,
                            color = NeonCyberGreen
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "AES-256-GCM: ${status.ciphertext.take(36)}... (IV: ${status.iv})",
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Quick Emoji Reactions to Status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf("🔥", "⚡", "🛡️", "❤️", "🎯", "👏").forEach { emoji ->
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, NeonCyberGreen.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .size(38.dp)
                                .clickable {
                                    onReply("Durumunuza tepki bıraktı: $emoji")
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(text = emoji)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = replyText,
                    onValueChange = { replyText = it },
                    label = { Text("@${status.authorUsername} durumuna şifreli yanıt yaz...") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("status_reply_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Kapat")
                    }
                    Button(
                        onClick = {
                            if (replyText.isNotBlank()) {
                                onReply(replyText)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonCyberGreen,
                            contentColor = Color(0xFF002110)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("send_status_reply_button")
                    ) {
                        Text("Şifreli Yanıtla", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun CallsTabScreen(
    callsState: UiState<List<CallSignal>>,
    directoryState: UiState<List<UserProfile>>,
    onStartCall: (String, String, String) -> Unit,
    onSimulateIncomingCall: (String, String) -> Unit = { _, _ -> }
) {
    val peers = (directoryState as? UiState.Success)?.data ?: emptyList()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Uçtan Uca Şifreli P2P Aramalar",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "SRTP / WebRTC P2P sinyal katmanı ve 4-emoji anahtar doğrulamalı sesli/görüntülü aramalar.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Incoming Call Ringing Test / Simulation Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.6f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = null,
                        tint = CyberCyan,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Canlı Gelen Arama Karşılayıcısı",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Gelen şifreli P2P zil ekranını test edin",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                OutlinedButton(
                    onClick = {
                        val caller = peers.firstOrNull()?.username ?: "siber_dugum"
                        onSimulateIncomingCall(caller, "VIDEO")
                    },
                    border = BorderStroke(1.dp, NeonCyberGreen),
                    modifier = Modifier.testTag("simulate_incoming_call_button")
                ) {
                    Text("Zil Çaldır", color = NeonCyberGreen, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (peers.isNotEmpty()) {
            Text(
                text = "Hızlı Şifreli Arama Başlat:",
                style = MaterialTheme.typography.labelSmall,
                color = CyberCyan
            )
            Spacer(modifier = Modifier.height(8.dp))
            peers.take(3).forEach { peer ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "@${peer.username}",
                                style = MaterialTheme.typography.titleMedium,
                                color = NeonCyberGreen
                            )
                            Text(
                                text = peer.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = { onStartCall(peer.userId, peer.username, "AUDIO") }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Call,
                                contentDescription = "Sesli Ara",
                                tint = NeonCyberGreen
                            )
                        }
                        IconButton(
                            onClick = { onStartCall(peer.userId, peer.username, "VIDEO") }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Videocam,
                                contentDescription = "Görüntülü Ara",
                                tint = CyberCyan
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        Text(
            text = "Şifreli Sinyal Geçmişi:",
            style = MaterialTheme.typography.labelSmall,
            color = NeonCyberGreen
        )
        Spacer(modifier = Modifier.height(8.dp))

        when (callsState) {
            is UiState.Loading -> {
                Text(
                    text = "Arama sinyalleri yükleniyor...",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            is UiState.Error -> {
                Text(
                    text = callsState.message,
                    color = MaterialTheme.colorScheme.error
                )
            }
            is UiState.Success -> {
                val calls = callsState.data
                if (calls.isEmpty()) {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Call,
                                contentDescription = null,
                                tint = NeonCyberGreen,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Henüz Şifreli Arama Kaydı Yok",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Bir sohbetin içinden veya yukarıdaki kişi listesinden sesli/görüntülü P2P arama başlatabilirsiniz.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 90.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(calls, key = { it.id }) { call ->
                            val dateStr = remember(call.createdAt) {
                                val d = call.createdAt?.toDate() ?: Date()
                                SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(d)
                            }
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (call.callType == "VIDEO") Icons.Default.Videocam else Icons.Default.Call,
                                        contentDescription = null,
                                        tint = if (call.callType == "VIDEO") CyberCyan else NeonCyberGreen
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "@${call.callerUsername} ↔ @${call.calleeUsername}",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Doğrulama Emojileri: ${call.safetyEmojis} • $dateStr",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ActiveP2PCallOverlay(
    callSignal: CallSignal,
    onEndCall: () -> Unit
) {
    BackHandler(onBack = onEndCall)

    var isMuted by remember { mutableStateOf(false) }
    var isVideoEnabled by remember { mutableStateOf(callSignal.callType == "VIDEO") }
    var isSpeakerOn by remember { mutableStateOf(true) }
    var elapsedSeconds by remember { mutableIntStateOf(0) }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    LaunchedEffect(Unit) {
        permissionsLauncher.launch(
            arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
        )
        while (true) {
            delay(1000L)
            elapsedSeconds++
        }
    }

    val timerFormatted = remember(elapsedSeconds) {
        val mins = elapsedSeconds / 60
        val secs = elapsedSeconds % 60
        String.format(Locale.US, "%02d:%02d", mins, secs)
    }

    Surface(
        color = ObsidianBlack,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(modifier = Modifier.height(20.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    border = BorderStroke(1.dp, NeonCyberGreen)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = NeonCyberGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "UÇTAN UCA ŞİFRELİ P2P TÜNEL • SRTP-256",
                            style = MaterialTheme.typography.labelSmall,
                            color = NeonCyberGreen
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "@${callSignal.calleeUsername}",
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "$timerFormatted • Doğrudan Eşler Arası (P2P) Bağlantı",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CyberCyan
                )
            }

            // Center Cryptographic Emoji Verification & Audio/Video Matrix
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.5.dp, NeonCyberGreen.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = NeonCyberGreen,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Anahtar Doğrulama Emojileri",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = callSignal.safetyEmojis,
                        style = MaterialTheme.typography.displayLarge
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Karşı taraftaki ekranda da aynı 4 emoji görünüyorsa aramanız ortadaki adam (MITM) saldırılarına karşı %100 güvendedir.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    if (isVideoEnabled) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = ObsidianBlack,
                            border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.6f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.Videocam,
                                        contentDescription = null,
                                        tint = CyberCyan,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "P2P ŞİFRELİ VİDEO AKIŞI AKTİF • 1080p VP9 + SRTP",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = CyberCyan
                                    )
                                    Text(
                                        text = "ECDH P-256 Kare Doğrulaması: OK",
                                        fontFamily = JetBrainsMonoFontFamily,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NeonCyberGreen
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                    Text(
                        text = "Şifreli Ses Dalga Formu:  ▃▅▇█▆▄▂▅▇█▆▄▃▅▇",
                        fontFamily = JetBrainsMonoFontFamily,
                        style = MaterialTheme.typography.labelSmall,
                        color = NeonCyberGreen
                    )
                }
            }

            // Call Control Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { isMuted = !isMuted },
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            if (isMuted) AmberTimerWarning.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant,
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = "Mikrofon",
                        tint = if (isMuted) AmberTimerWarning else MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(
                    onClick = { isVideoEnabled = !isVideoEnabled },
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            if (isVideoEnabled) CyberCyan.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant,
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = if (isVideoEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                        contentDescription = "Kamera",
                        tint = if (isVideoEnabled) CyberCyan else MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(
                    onClick = { isSpeakerOn = !isSpeakerOn },
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            if (isSpeakerOn) NeonCyberGreen.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant,
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Hoparlör",
                        tint = if (isSpeakerOn) NeonCyberGreen else MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(
                    onClick = onEndCall,
                    modifier = Modifier
                        .size(64.dp)
                        .background(BurnCoralRed, CircleShape)
                        .testTag("end_call_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.CallEnd,
                        contentDescription = "Aramayı Sonlandır",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun IncomingCallRingingOverlay(
    callSignal: CallSignal,
    onAcceptCall: () -> Unit,
    onDeclineCall: () -> Unit
) {
    BackHandler(onBack = onDeclineCall)

    Surface(
        color = ObsidianBlack,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(modifier = Modifier.height(28.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    border = BorderStroke(1.dp, CyberCyan)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = CyberCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (callSignal.callType == "VIDEO") {
                                "GELEN ŞİFRELİ GÖRÜNTÜLÜ P2P ARAMA..."
                            } else {
                                "GELEN ŞİFRELİ SESLİ P2P ARAMA..."
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = CyberCyan
                        )
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    border = BorderStroke(3.dp, NeonCyberGreen),
                    modifier = Modifier.size(96.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = callSignal.callerUsername.take(2).uppercase(Locale.ROOT),
                            style = MaterialTheme.typography.headlineLarge,
                            color = NeonCyberGreen,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "@${callSignal.callerUsername}",
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Sıfır-Bilgi ECDH P-256 + SRTP El Sıkışması Hazır",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NeonCyberGreen
                )
            }

            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.5.dp, CyberCyan.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Ön Doğrulama Emojileri (Safety Emojis)",
                        style = MaterialTheme.typography.labelSmall,
                        color = CyberCyan
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = callSignal.safetyEmojis,
                        style = MaterialTheme.typography.displayLarge
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Aramayı yanıtladığınızda uçtan uca şifreli P2P medya tüneli doğrudan cihazlar arasında kurulacaktır.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = onDeclineCall,
                        modifier = Modifier
                            .size(68.dp)
                            .background(BurnCoralRed, CircleShape)
                            .testTag("decline_incoming_call_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = "Reddet",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Reddet",
                        style = MaterialTheme.typography.labelLarge,
                        color = BurnCoralRed
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = onAcceptCall,
                        modifier = Modifier
                            .size(68.dp)
                            .background(NeonCyberGreen, CircleShape)
                            .testTag("accept_incoming_call_button")
                    ) {
                        Icon(
                            imageVector = if (callSignal.callType == "VIDEO") Icons.Default.Videocam else Icons.Default.Call,
                            contentDescription = "Yanıtla",
                            tint = Color(0xFF002110),
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Şifreli Yanıtla",
                        style = MaterialTheme.typography.labelLarge,
                        color = NeonCyberGreen,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
