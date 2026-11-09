package com.example.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EnhancedEncryption
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.R
import com.example.crypto.CryptoEngine
import com.example.data.local.ScheduledEnvelopeEntity
import com.example.data.model.ChatRoom
import com.example.data.model.DecryptedMessageItem
import com.example.data.model.UserProfile
import com.example.ui.ZeroChatViewModel
import com.example.ui.components.RoomSafetyAndCipherInspectorDialog
import com.example.ui.theme.AmberTimerWarning
import com.example.ui.theme.BurnCoralRed
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.IncomingBubbleSurface
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.NeonCyberGreen
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.OutgoingBubbleSurface
import com.example.util.VoiceAudioManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatConversationScreen(
    room: ChatRoom,
    currentUserId: String,
    myUsername: String? = null,
    messages: List<DecryptedMessageItem>,
    allRooms: List<ChatRoom> = emptyList(),
    directoryPeers: List<UserProfile> = emptyList(),
    isRoomPeerVerified: Boolean = false,
    hasRoomKeyChangedWarning: Boolean = false,
    scheduledEnvelopes: List<ScheduledEnvelopeEntity> = emptyList(),
    inChatSearchQuery: String = "",
    onInChatSearchQueryChange: (String) -> Unit = {},
    replyingToMessage: DecryptedMessageItem? = null,
    onSetReplyingTo: (DecryptedMessageItem?) -> Unit = {},
    editingMessage: DecryptedMessageItem? = null,
    onSetEditingMessage: (DecryptedMessageItem?) -> Unit = {},
    peerTypingHandle: String? = null,
    onUserTyping: () -> Unit = {},
    onForwardMessageToRoom: (DecryptedMessageItem, ChatRoom) -> Unit = { _, _ -> },
    onTogglePinMessage: (DecryptedMessageItem) -> Unit = {},
    onToggleStarMessage: (DecryptedMessageItem) -> Unit = {},
    onUpdateGroupMembersAndTitle: (String, List<UserProfile>) -> Unit = { _, _ -> },
    onRotateRoomKey: () -> Unit = {},
    onVerifyRoomIdentity: () -> Unit = {},
    onSimulateMitmKeyChange: () -> Unit = {},
    onScheduleMessage: (String, Long, Long?) -> Unit = { _, _, _ -> },
    onCancelScheduledMessage: (Long) -> Unit = {},
    onNukeRoomHistory: () -> Unit = {},
    onNotifySecureClipboard: () -> Unit = {},
    onApproveConnectionRequest: () -> Unit = {},
    onRejectOrCancelConnectionRequest: () -> Unit = {},
    onBack: () -> Unit,
    onSendText: (String, Long?, Boolean, Boolean) -> Unit,
    onSendVoiceNote: (Int, String, String, Long?) -> Unit,
    onSendImageAttachment: (String, String, Long?, Boolean, String) -> Unit,
    onSendFileAttachment: (String, Int, String, Long?) -> Unit = { _, _, _, _ -> },
    onSendLocation: (Double, Double, String, Long?) -> Unit,
    onSendPoll: (String, List<String>, Long?) -> Unit,
    onVotePoll: (DecryptedMessageItem, Int) -> Unit,
    onToggleReaction: (DecryptedMessageItem, String) -> Unit,
    onBurnMessageNow: (String) -> Unit,
    onSetRoomDefaultTtl: (Long) -> Unit,
    onStartCall: (String) -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val voiceAudioManager = remember { VoiceAudioManager(context) }

    DisposableEffect(Unit) {
        onDispose {
            voiceAudioManager.stopPlayback()
        }
    }

    var messageText by remember { mutableStateOf("") }
    var showAttachmentTools by remember { mutableStateOf(false) }
    var showTtlSelector by remember { mutableStateOf(false) }
    var showInChatSearch by remember { mutableStateOf(false) }
    var showPollCreator by remember { mutableStateOf(false) }
    var showStegoComposer by remember { mutableStateOf(false) }
    var showScheduleDialog by remember { mutableStateOf(false) }
    var showGroupAndKeyManager by remember { mutableStateOf(false) }
    var forwardingMessage by remember { mutableStateOf<DecryptedMessageItem?>(null) }
    var inspectedMessage by remember { mutableStateOf<DecryptedMessageItem?>(null) }
    var activeViewOnceMessage by remember { mutableStateOf<DecryptedMessageItem?>(null) }
    var showRoomInspector by remember { mutableStateOf(false) }
    var selectedTtlOverride by remember(room.defaultTtlSeconds) { mutableStateOf(room.defaultTtlSeconds) }
    var isViewOnceSelected by remember { mutableStateOf(false) }
    var isSpoilerSelected by remember { mutableStateOf(false) }

    var isRecordingAudio by remember { mutableStateOf(false) }
    var recordingElapsedSeconds by remember { mutableIntStateOf(0) }
    var playingMessageId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(editingMessage) {
        if (editingMessage != null) {
            messageText = editingMessage.decryptedText
        }
    }

    LaunchedEffect(isRecordingAudio) {
        if (isRecordingAudio) {
            recordingElapsedSeconds = 0
            while (isRecordingAudio) {
                delay(1000L)
                voiceAudioManager.sampleAmplitude()
                recordingElapsedSeconds++
            }
        }
    }

    val pinnedMessage = remember(messages) {
        messages.lastOrNull { it.isPinned }
    }

    // Zero-permission Android Photo Picker (Google Play Policy compliant)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onSendImageAttachment(
                uri.toString(),
                if (isViewOnceSelected) "🔥👁️ 1 Kez Görüntülenebilir Şifreli Görsel" else "📷 Şifreli Görsel Paylaşımı",
                selectedTtlOverride,
                isViewOnceSelected,
                ""
            )
            isViewOnceSelected = false
        }
    }

    // Encrypted Document / File Picker
    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            var displayName = "sifreli_belge.bin"
            var sizeKb = 24
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (nameIdx >= 0) {
                            displayName = cursor.getString(nameIdx) ?: displayName
                        }
                        if (sizeIdx >= 0) {
                            sizeKb = ((cursor.getLong(sizeIdx)) / 1024L).toInt().coerceAtLeast(1)
                        }
                    }
                }
            } catch (_: Exception) {
            }
            val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
            onSendFileAttachment(displayName, sizeKb, mime, selectedTtlOverride)
        }
    }

    // Audio Recording runtime permission launcher
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val started = voiceAudioManager.startRecording()
            if (started) {
                isRecordingAudio = true
            } else {
                onSendVoiceNote(8, "▃▅▇█▆▄▂▅▇█▅▃", "", selectedTtlOverride)
            }
        } else {
            onSendVoiceNote(8, "▃▅▇█▆▄▂▅▇█▅▃", "", selectedTtlOverride)
        }
    }

    // Location runtime permission launcher
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        onSendLocation(41.0082, 28.9784, "Şifreli Taktiksel Konum (P2P)", selectedTtlOverride)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
    ) {
        // Top E2EE Room Header
        Surface(
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("chat_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Geri",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            inspectedMessage = null
                            showRoomInspector = true
                        }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = room.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = when {
                                hasRoomKeyChangedWarning -> Icons.Default.Warning
                                isRoomPeerVerified -> Icons.Default.VerifiedUser
                                else -> Icons.Default.Shield
                            },
                            contentDescription = "Doğrulanmış Şifreleme",
                            tint = when {
                                hasRoomKeyChangedWarning -> BurnCoralRed
                                isRoomPeerVerified -> CyberCyan
                                else -> NeonCyberGreen
                            },
                            modifier = Modifier.size(16.dp)
                        )
                        if (isRoomPeerVerified && !hasRoomKeyChangedWarning) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "🛡️ Doğrulandı",
                                style = MaterialTheme.typography.labelSmall,
                                color = CyberCyan
                            )
                        }
                    }
                    Text(
                        text = if (peerTypingHandle != null) {
                            "⚡ @$peerTypingHandle şifreli paket yazıyor..."
                        } else if (room.isGroup) {
                            "${room.participantUids.size} Üye • ECDH + Ratchet"
                        } else {
                            "ECDH P-256 + Ratchet • Anahtarı Denetle"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (peerTypingHandle != null) CyberCyan else NeonCyberGreen
                    )
                }

                IconButton(
                    onClick = { showGroupAndKeyManager = true },
                    modifier = Modifier.testTag("room_group_key_manager_button")
                ) {
                    Icon(
                        imageVector = if (room.isGroup) Icons.Default.Group else Icons.Default.Key,
                        contentDescription = "Grup Üyeleri ve Anahtar Yenileme",
                        tint = CyberCyan
                    )
                }

                IconButton(
                    onClick = {
                        showInChatSearch = !showInChatSearch
                        if (!showInChatSearch) onInChatSearchQueryChange("")
                    },
                    modifier = Modifier.testTag("in_chat_search_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Sohbet İçi Arama",
                        tint = if (showInChatSearch) CyberCyan else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = { showTtlSelector = !showTtlSelector },
                    modifier = Modifier.testTag("ttl_timer_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = stringResource(R.string.burn_timer_label),
                        tint = if (selectedTtlOverride > 0L) AmberTimerWarning else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = { onStartCall("AUDIO") },
                    modifier = Modifier.testTag("start_audio_call_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = stringResource(R.string.call_start_audio),
                        tint = NeonCyberGreen
                    )
                }

                IconButton(
                    onClick = { onStartCall("VIDEO") },
                    modifier = Modifier.testTag("start_video_call_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = stringResource(R.string.call_start_video),
                        tint = CyberCyan
                    )
                }
            }
        }

        // MITM Key Change Warning Banner
        AnimatedVisibility(visible = hasRoomKeyChangedWarning) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                border = BorderStroke(1.5.dp, BurnCoralRed),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = BurnCoralRed,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "⚠️ DİKKAT: Karşı Düğümün Kriptografik Anahtarı Değişti!",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = BurnCoralRed
                            )
                            Text(
                                text = "Olası MITM saldırısı veya cihaz sıfırlaması. Devam etmeden önce güvenlik numarasını doğrulayın.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onVerifyRoomIdentity,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BurnCoralRed,
                            contentColor = Color.White
                        ),
                        modifier = Modifier.testTag("approve_new_key_button")
                    ) {
                        Text("Onayla", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Scheduled Envelopes Banner
        if (scheduledEnvelopes.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = CyberCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "⏳ Zamanlanmış Şifreli Zarflar (${scheduledEnvelopes.size})",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = CyberCyan
                        )
                    }
                    scheduledEnvelopes.take(2).forEach { sched ->
                        val sendTimeStr = remember(sched.scheduledAtMillis) {
                            SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(sched.scheduledAtMillis))
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "[$sendTimeStr] \"${sched.plaintext.take(36)}\"",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { onCancelScheduledMessage(sched.id) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Zamanlamayı İptal Et",
                                    tint = BurnCoralRed,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Expandable In-Chat Keyword Search Bar
        AnimatedVisibility(visible = showInChatSearch) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = inChatSearchQuery,
                    onValueChange = onInChatSearchQueryChange,
                    placeholder = { Text("Çözülmüş mesajlarda veya dosyalarda ara...") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = CyberCyan)
                    },
                    trailingIcon = {
                        if (inChatSearchQuery.isNotEmpty()) {
                            IconButton(onClick = { onInChatSearchQueryChange("") }) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "Temizle")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .testTag("in_chat_search_input")
                )
            }
        }

        // Pinned Message Banner
        if (pinnedMessage != null) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, AmberTimerWarning.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = null,
                            tint = AmberTimerWarning,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Sabitlenen Mesaj (@${pinnedMessage.senderUsername})",
                                style = MaterialTheme.typography.labelSmall,
                                color = AmberTimerWarning
                            )
                            Text(
                                text = pinnedMessage.decryptedText,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    IconButton(
                        onClick = { onTogglePinMessage(pinnedMessage) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Sabitlemeyi Kaldır",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Self-Destruct Timer Bar
        AnimatedVisibility(visible = showTtlSelector) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Süreli Mesaj İmha:",
                        style = MaterialTheme.typography.labelSmall,
                        color = AmberTimerWarning
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0L to "Kapalı", 15L to "15sn", 60L to "1dk", 3600L to "1sa").forEach { (sec, label) ->
                            FilterChip(
                                selected = selectedTtlOverride == sec,
                                onClick = {
                                    selectedTtlOverride = sec
                                    onSetRoomDefaultTtl(sec)
                                },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            }
        }

        // Encrypted Messages List
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                SessionSecurityNoticeBanner(
                    roomKeyHint = room.roomKeyHint,
                    onInspectClick = {
                        inspectedMessage = null
                        showRoomInspector = true
                    }
                )
            }

            items(messages, key = { it.id }) { msg ->
                val isMine = msg.senderId == currentUserId
                EncryptedMessageBubble(
                    message = msg,
                    roomKeyHint = room.roomKeyHint,
                    isMine = isMine,
                    isPlayingAudio = playingMessageId == msg.id,
                    onPlayVoiceNote = { speed ->
                        if (playingMessageId == msg.id) {
                            voiceAudioManager.stopPlayback()
                            playingMessageId = null
                        } else {
                            playingMessageId = msg.id
                            if (msg.audioBase64.isNotBlank()) {
                                voiceAudioManager.playBase64Audio(
                                    audioBase64 = msg.audioBase64,
                                    playbackSpeed = speed,
                                    onCompletion = { playingMessageId = null }
                                )
                            }
                        }
                    },
                    onOpenViewOnce = {
                        activeViewOnceMessage = msg
                    },
                    onInspectCipher = {
                        inspectedMessage = msg
                        showRoomInspector = true
                    },
                    onCopySecurely = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        if (clipboard != null) {
                            val clip = ClipData.newPlainText("ZeroChat Şifreli Metin", msg.decryptedText)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                clip.description.extras = PersistableBundle().apply {
                                    putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                                }
                            }
                            clipboard.setPrimaryClip(clip)
                            onNotifySecureClipboard()
                            coroutineScope.launch {
                                delay(30_000L)
                                try {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                        clipboard.clearPrimaryClip()
                                    } else {
                                        clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
                                    }
                                } catch (_: Exception) {
                                }
                            }
                        }
                    },
                    onReply = { onSetReplyingTo(msg) },
                    onEdit = {
                        if (isMine && msg.messageType == "TEXT") {
                            onSetEditingMessage(msg)
                        }
                    },
                    onPin = { onTogglePinMessage(msg) },
                    onStar = { onToggleStarMessage(msg) },
                    onForward = { forwardingMessage = msg },
                    onToggleReaction = { emoji -> onToggleReaction(msg, emoji) },
                    onVotePoll = { idx -> onVotePoll(msg, idx) },
                    onBurnNow = { onBurnMessageNow(msg.id) }
                )
            }
        }

        // Active Voice Recording Banner
        AnimatedVisibility(visible = isRecordingAudio) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                border = BorderStroke(1.dp, BurnCoralRed),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = BurnCoralRed
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "🎙️ AAC Ses Kaydediliyor (${recordingElapsedSeconds} sn)...",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                voiceAudioManager.stopRecording()
                                isRecordingAudio = false
                            }
                        ) {
                            Text("İptal")
                        }
                        Button(
                            onClick = {
                                val res = voiceAudioManager.stopRecording()
                                isRecordingAudio = false
                                onSendVoiceNote(
                                    res.durationSeconds.coerceAtLeast(1),
                                    res.waveformString,
                                    res.audioBase64,
                                    selectedTtlOverride
                                )
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeonCyberGreen,
                                contentColor = Color(0xFF002110)
                            )
                        ) {
                            Icon(imageVector = Icons.Default.StopCircle, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Şifrele & Gönder", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Reply or Edit Mode Banner above Composer
        AnimatedVisibility(visible = replyingToMessage != null || editingMessage != null) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = if (editingMessage != null) Icons.Default.Edit else Icons.AutoMirrored.Filled.Reply,
                            contentDescription = null,
                            tint = CyberCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (editingMessage != null) {
                                    "Mesaj Yeniden Şifrelenerek Düzenleniyor"
                                } else {
                                    "@${replyingToMessage?.senderUsername} yanıtlanıyor"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = CyberCyan
                            )
                            Text(
                                text = (editingMessage?.decryptedText ?: replyingToMessage?.decryptedText).orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            onSetReplyingTo(null)
                            if (editingMessage != null) {
                                onSetEditingMessage(null)
                                messageText = ""
                            }
                        }
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "İptal")
                    }
                }
            }
        }

        if (!room.isApprovedConnection) {
            val isIncoming = room.isIncomingRequestFor(myUsername)
            val requestNote = remember(room.lastCipherPreview, room.roomKeyHint) {
                CryptoEngine.decrypt(room.lastCipherPreview, "", room.roomKeyHint)
                    .takeIf { !it.startsWith("[Şifre") }
                    ?: "🤝 Şifreli iletişim ve takip isteği"
            }
            Surface(
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    1.5.dp,
                    if (room.isRejectedRequest) BurnCoralRed else if (isIncoming) CyberCyan else AmberTimerWarning
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (room.isRejectedRequest) Icons.Default.Warning else Icons.Default.Lock,
                            contentDescription = null,
                            tint = if (room.isRejectedRequest) BurnCoralRed else if (isIncoming) CyberCyan else AmberTimerWarning
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when {
                                room.isRejectedRequest -> "⛔ İletişim İsteği Reddedildi / İptal Edildi"
                                isIncoming -> "📨 Gelen İletişim & Takip İsteği (@${room.requesterUsername})"
                                else -> "⏳ İletişim Onayı Bekleniyor (${room.title})"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (room.isRejectedRequest) BurnCoralRed else if (isIncoming) CyberCyan else AmberTimerWarning
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = requestNote,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isIncoming) {
                            "Bu düğüm sizi kullanıcı adınızla buldu. Onay verdiğinizde ECDH P-256 + AES-256-GCM şifreli mesajlaşma tüneli açılacaktır."
                        } else {
                            "Karşı düğüm iletişim/takip isteğinizi onaylayana kadar mesaj ve arama özellikleri kilitlidir."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(
                            onClick = onRejectOrCancelConnectionRequest,
                            border = BorderStroke(1.dp, BurnCoralRed)
                        ) {
                            Text(
                                text = if (isIncoming) "İsteği Reddet" else "İsteği Geri Çek",
                                color = BurnCoralRed
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = onApproveConnectionRequest,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeonCyberGreen,
                                contentColor = Color(0xFF002110)
                            ),
                            modifier = Modifier.testTag("chat_approve_connection_button")
                        ) {
                            Text(
                                text = if (isIncoming) "✅ İletişim İsteğini Onayla" else "⚡ Karşı Düğüm Onayını Simüle Et",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        } else {
            // View-Once, Spoiler & Scheduled Message Security Mode Chips Bar
            Surface(
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = isViewOnceSelected,
                        onClick = { isViewOnceSelected = !isViewOnceSelected },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.VisibilityOff,
                                contentDescription = null,
                                tint = if (isViewOnceSelected) BurnCoralRed else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(15.dp)
                            )
                        },
                        label = {
                            Text(
                                text = "🔥👁️ 1 Kez Görüntüle",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        modifier = Modifier.testTag("view_once_toggle_chip")
                    )

                    FilterChip(
                        selected = isSpoilerSelected,
                        onClick = { isSpoilerSelected = !isSpoilerSelected },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Visibility,
                                contentDescription = null,
                                tint = if (isSpoilerSelected) CyberCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(15.dp)
                            )
                        },
                        label = {
                            Text(
                                text = "🕵️ Gizli/Spoiler",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        modifier = Modifier.testTag("spoiler_toggle_chip")
                    )

                    FilterChip(
                        selected = showScheduleDialog,
                        onClick = { showScheduleDialog = true },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = AmberTimerWarning,
                                modifier = Modifier.size(15.dp)
                            )
                        },
                        label = {
                            Text(
                                text = "⏳ Zamanla",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        modifier = Modifier.testTag("schedule_message_chip")
                    )
                }
            }

            // Rich Attachment Tools Bar
            AnimatedVisibility(visible = showAttachmentTools) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        AttachmentToolButton(
                            icon = Icons.Default.Image,
                            label = "Fotoğraf",
                            tint = NeonCyberGreen,
                            onClick = {
                                showAttachmentTools = false
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        )
                        AttachmentToolButton(
                            icon = Icons.Default.EnhancedEncryption,
                            label = "Stego Görsel",
                            tint = CyberCyan,
                            onClick = {
                                showAttachmentTools = false
                                showStegoComposer = true
                            }
                        )
                        AttachmentToolButton(
                            icon = Icons.Default.AttachFile,
                            label = "Belge/Dosya",
                            tint = CyberCyan,
                            onClick = {
                                showAttachmentTools = false
                                documentPickerLauncher.launch(arrayOf("*/*"))
                            }
                        )
                        AttachmentToolButton(
                            icon = Icons.Default.Mic,
                            label = "Ses Kaydet",
                            tint = NeonCyberGreen,
                            onClick = {
                                showAttachmentTools = false
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        )
                        AttachmentToolButton(
                            icon = Icons.Default.Poll,
                            label = "Anket",
                            tint = AmberTimerWarning,
                            onClick = {
                                showAttachmentTools = false
                                showPollCreator = true
                            }
                        )
                        AttachmentToolButton(
                            icon = Icons.Default.LocationOn,
                            label = "Konum",
                            tint = BurnCoralRed,
                            onClick = {
                                showAttachmentTools = false
                                locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                            }
                        )
                    }
                }
            }

            // Bottom Message Composer
            Surface(
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { showAttachmentTools = !showAttachmentTools },
                        modifier = Modifier.testTag("toggle_attachments_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddCircleOutline,
                            contentDescription = "Zengin İçerik Ekle",
                            tint = NeonCyberGreen
                        )
                    }

                    OutlinedTextField(
                        value = messageText,
                        onValueChange = {
                            messageText = it
                            if (it.isNotEmpty()) onUserTyping()
                        },
                        placeholder = {
                            Text(
                                text = when {
                                    editingMessage != null -> "Yeni mesaj metnini girin..."
                                    isViewOnceSelected -> "🔥👁️ 1 kez görüntülenip imha edilecek mesaj..."
                                    isSpoilerSelected -> "🕵️ Gizli maskeli (spoiler) mesaj..."
                                    selectedTtlOverride > 0L -> "🔥 ${selectedTtlOverride}s süreli şifreli mesaj..."
                                    else -> stringResource(R.string.message_input_hint)
                                },
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        shape = RoundedCornerShape(20.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyberGreen,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedContainerColor = ObsidianBlack,
                            unfocusedContainerColor = ObsidianBlack
                        ),
                        maxLines = 4,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_message_input")
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Surface(
                        shape = CircleShape,
                        color = NeonCyberGreen,
                        modifier = Modifier
                            .size(48.dp)
                            .clickable {
                                if (messageText.isNotBlank()) {
                                    onSendText(
                                        messageText,
                                        selectedTtlOverride,
                                        isViewOnceSelected,
                                        isSpoilerSelected
                                    )
                                    messageText = ""
                                    isViewOnceSelected = false
                                    isSpoilerSelected = false
                                } else {
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                            .testTag("send_message_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (messageText.isNotBlank()) {
                                    Icons.AutoMirrored.Filled.Send
                                } else {
                                    Icons.Default.Mic
                                },
                                contentDescription = stringResource(R.string.send_message_desc),
                                tint = Color(0xFF002110)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showRoomInspector) {
        RoomSafetyAndCipherInspectorDialog(
            room = room,
            inspectedMessage = inspectedMessage,
            isPeerVerified = isRoomPeerVerified,
            hasKeyChangedWarning = hasRoomKeyChangedWarning,
            onVerifyIdentity = onVerifyRoomIdentity,
            onSimulateKeyChange = onSimulateMitmKeyChange,
            onDismiss = { showRoomInspector = false }
        )
    }

    if (showScheduleDialog) {
        ScheduleEncryptedMessageDialog(
            initialText = messageText,
            onDismiss = { showScheduleDialog = false },
            onSchedule = { textToSchedule, delaySec ->
                onScheduleMessage(textToSchedule, delaySec, selectedTtlOverride)
                if (messageText == textToSchedule) {
                    messageText = ""
                }
                showScheduleDialog = false
            }
        )
    }

    if (showPollCreator) {
        CreateEncryptedPollDialog(
            onDismiss = { showPollCreator = false },
            onCreatePoll = { q, opts ->
                onSendPoll(q, opts, selectedTtlOverride)
                showPollCreator = false
            }
        )
    }

    if (showStegoComposer) {
        SteganographyComposerDialog(
            onDismiss = { showStegoComposer = false },
            onSendStegoImage = { carrierUri, visibleCaption, secretMessage ->
                onSendImageAttachment(
                    carrierUri,
                    visibleCaption,
                    selectedTtlOverride,
                    isViewOnceSelected,
                    secretMessage
                )
                isViewOnceSelected = false
                showStegoComposer = false
            }
        )
    }

    if (showGroupAndKeyManager) {
        GroupManagementAndKeyRotationDialog(
            room = room,
            directoryPeers = directoryPeers,
            onDismiss = { showGroupAndKeyManager = false },
            onSaveMembersAndTitle = { newTitle, selectedPeers ->
                onUpdateGroupMembersAndTitle(newTitle, selectedPeers)
                showGroupAndKeyManager = false
            },
            onRotateKey = {
                onRotateRoomKey()
                showGroupAndKeyManager = false
            },
            onNukeRoom = {
                onNukeRoomHistory()
                showGroupAndKeyManager = false
            }
        )
    }

    if (activeViewOnceMessage != null) {
        ViewOnceRevealDialog(
            message = activeViewOnceMessage!!,
            onCloseAndBurn = {
                val idToBurn = activeViewOnceMessage!!.id
                activeViewOnceMessage = null
                onBurnMessageNow(idToBurn)
            }
        )
    }

    if (forwardingMessage != null) {
        ForwardMessageRoomPickerDialog(
            message = forwardingMessage!!,
            availableRooms = allRooms,
            onDismiss = { forwardingMessage = null },
            onSelectRoom = { targetRoom ->
                onForwardMessageToRoom(forwardingMessage!!, targetRoom)
                forwardingMessage = null
            }
        )
    }
}

@Composable
private fun SessionSecurityNoticeBanner(
    roomKeyHint: String,
    onInspectClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, NeonCyberGreen.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onInspectClick)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = NeonCyberGreen,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Sıfır-Bilgi (Zero-Knowledge) ECDH + Forward Secrecy Oturumu",
                    style = MaterialTheme.typography.labelLarge,
                    color = NeonCyberGreen
                )
                Text(
                    text = "Anahtar: ${roomKeyHint.take(20)} • QR, Ratchet adımı ve şifreli paketleri denetlemek için dokunun.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EncryptedMessageBubble(
    message: DecryptedMessageItem,
    roomKeyHint: String,
    isMine: Boolean,
    isPlayingAudio: Boolean,
    onPlayVoiceNote: (Float) -> Unit,
    onOpenViewOnce: () -> Unit,
    onInspectCipher: () -> Unit,
    onCopySecurely: () -> Unit,
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onPin: () -> Unit,
    onStar: () -> Unit,
    onForward: () -> Unit,
    onToggleReaction: (String) -> Unit,
    onVotePoll: (Int) -> Unit,
    onBurnNow: () -> Unit
) {
    var showReactionBar by remember { mutableStateOf(false) }
    var isSpoilerRevealed by remember(message.id) { mutableStateOf(false) }
    var extractedStegoPlaintext by remember(message.id) { mutableStateOf<String?>(null) }
    var voicePlaybackSpeed by remember(message.id) { mutableFloatStateOf(1.0f) }

    val timeText = remember(message.createdAtMillis) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.createdAtMillis))
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
    ) {
        Card(
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isMine) 18.dp else 4.dp,
                bottomEnd = if (isMine) 4.dp else 18.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (isMine) OutgoingBubbleSurface else IncomingBubbleSurface
            ),
            border = BorderStroke(
                1.dp,
                when {
                    message.isPinned -> AmberTimerWarning
                    message.isViewOnce -> BurnCoralRed.copy(alpha = 0.7f)
                    isMine -> NeonCyberGreen.copy(alpha = 0.45f)
                    else -> CyberCyan.copy(alpha = 0.35f)
                }
            ),
            modifier = Modifier
                .widthIn(min = 160.dp, max = 330.dp)
                .clickable { showReactionBar = !showReactionBar }
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Sender, Pin, Star & Self-destruct badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "@${message.senderUsername}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isMine) NeonCyberGreen else CyberCyan
                        )
                        if (message.isPinned) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.PushPin,
                                contentDescription = "Sabitlendi",
                                tint = AmberTimerWarning,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                        if (message.isStarred) {
                            Spacer(modifier = Modifier.width(5.dp))
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Yıldızlı Zarf",
                                tint = AmberTimerWarning,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                    if (message.remainingSeconds != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = BurnCoralRed.copy(alpha = 0.22f)
                        ) {
                            Text(
                                text = "🔥 ${message.remainingSeconds}s",
                                style = MaterialTheme.typography.labelSmall,
                                color = BurnCoralRed,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // Forwarded Header Badge
                if (!message.forwardedFrom.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "↪ İletildi (@${message.forwardedFrom})",
                        style = MaterialTheme.typography.labelSmall,
                        color = CyberCyan
                    )
                }

                // Quoted Reply Preview Card
                if (!message.replyToSender.isNullOrBlank() && !message.replyToPreview.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = ObsidianBlack,
                        border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)) {
                            Text(
                                text = "↩ @${message.replyToSender}",
                                style = MaterialTheme.typography.labelSmall,
                                color = CyberCyan
                            )
                            Text(
                                text = message.replyToPreview,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // If View-Once Mode is enabled, render sealed 1-time capsule!
                if (message.isViewOnce) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ObsidianBlack,
                        border = BorderStroke(1.dp, BurnCoralRed),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenViewOnce)
                            .testTag("open_view_once_button_${message.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.VisibilityOff,
                                contentDescription = null,
                                tint = BurnCoralRed,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "🔥👁️ 1 Kez Görüntülenebilir Şifreli Zarf",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = BurnCoralRed
                                )
                                Text(
                                    text = "Açmak için dokunun • Kapatıldığında anında imha edilir",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    // Normal or Spoiler Message Payload by Type
                    when (message.messageType) {
                        "POLL" -> {
                            val (question, options) = remember(message.encryptedMeta) {
                                ZeroChatViewModel.parsePollOptions(message.encryptedMeta)
                            }
                            val totalVotes = options.sumOf { it.voterNames.size }.coerceAtLeast(1)
                            Text(
                                text = "📊 $question",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            options.forEach { opt ->
                                val count = opt.voterNames.size
                                val progress = if (options.sumOf { it.voterNames.size } == 0) {
                                    0f
                                } else {
                                    count.toFloat() / totalVotes.toFloat()
                                }
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = ObsidianBlack,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                        .clickable { onVotePoll(opt.index) }
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = opt.text,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            Text(
                                                text = "$count oy",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = NeonCyberGreen
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        LinearProgressIndicator(
                                            progress = { progress },
                                            color = NeonCyberGreen,
                                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(5.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                        )
                                    }
                                }
                            }
                        }

                        "VOICE" -> {
                            val waveform = remember(message.encryptedMeta) {
                                try {
                                    org.json.JSONObject(message.encryptedMeta).optString("waveform", "▃▅▇█▆▄▂▅▇█▆▄▃▅▇")
                                } catch (_: Exception) {
                                    "▃▅▇█▆▄▂▅▇█▆▄▃▅▇"
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = ObsidianBlack,
                                border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        IconButton(
                                            onClick = { onPlayVoiceNote(voicePlaybackSpeed) },
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isPlayingAudio) Icons.Default.GraphicEq else Icons.Default.PlayArrow,
                                                contentDescription = "Sesli Notu Oynat",
                                                tint = if (isPlayingAudio) NeonCyberGreen else CyberCyan
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = waveform,
                                                fontFamily = JetBrainsMonoFontFamily,
                                                color = if (isPlayingAudio) NeonCyberGreen else CyberCyan
                                            )
                                            Text(
                                                text = if (message.audioBase64.isNotBlank()) {
                                                    "${message.decryptedText} • AAC"
                                                } else {
                                                    message.decryptedText
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    // 1x / 1.5x / 2x Playback Speed Chip
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = CyberCyan.copy(alpha = 0.16f),
                                        border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.6f)),
                                        modifier = Modifier
                                            .clickable {
                                                voicePlaybackSpeed = when (voicePlaybackSpeed) {
                                                    1.0f -> 1.5f
                                                    1.5f -> 2.0f
                                                    else -> 1.0f
                                                }
                                            }
                                            .testTag("voice_speed_chip_${message.id}")
                                    ) {
                                        Text(
                                            text = "${voicePlaybackSpeed}x",
                                            fontFamily = JetBrainsMonoFontFamily,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = CyberCyan,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }

                        "FILE" -> {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = ObsidianBlack,
                                border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.45f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Description,
                                        contentDescription = null,
                                        tint = CyberCyan,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = message.fileName.ifBlank { message.decryptedText },
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${message.fileSizeKb.coerceAtLeast(1)} KB • AES-256-GCM Şifreli Belge",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = NeonCyberGreen
                                        )
                                    }
                                }
                            }
                        }

                        "IMAGE" -> {
                            val mediaUri = remember(message.encryptedMeta) {
                                try {
                                    org.json.JSONObject(message.encryptedMeta).optString("mediaUri", "")
                                } catch (_: Exception) {
                                    ""
                                }
                            }
                            if (mediaUri.isNotBlank()) {
                                AsyncImage(
                                    model = mediaUri,
                                    contentDescription = "Şifreli Görsel",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(170.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                            Text(
                                text = message.decryptedText,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            // Steganography hidden payload detector
                            if (message.stegoPayload.isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = ObsidianBlack,
                                    border = BorderStroke(1.dp, CyberCyan),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            extractedStegoPlaintext = if (extractedStegoPlaintext == null) {
                                                CryptoEngine.extractStegoPayload(
                                                    stegoPacket = message.stegoPayload,
                                                    roomKeyHint = roomKeyHint
                                                )
                                            } else {
                                                null
                                            }
                                        }
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.EnhancedEncryption,
                                                contentDescription = null,
                                                tint = CyberCyan,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "🧬 Steganografik Gizli Katman Algılandı",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = CyberCyan
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = extractedStegoPlaintext
                                                ?: "Görsel içine gömülü AES-256-GCM mesajı çözmek için dokunun.",
                                            fontFamily = if (extractedStegoPlaintext != null) JetBrainsMonoFontFamily else null,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (extractedStegoPlaintext != null) NeonCyberGreen else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        else -> {
                            if (message.isSpoiler && !isSpoilerRevealed) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = ObsidianBlack,
                                    border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.6f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { isSpoilerRevealed = true }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Visibility,
                                            contentDescription = null,
                                            tint = CyberCyan,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "░░░░ Gizli (Spoiler) Mesaj • Görmek İçin Dokun ░░░░",
                                            fontFamily = JetBrainsMonoFontFamily,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = CyberCyan
                                        )
                                    }
                                }
                            } else {
                                Text(
                                    text = message.decryptedText,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = if (message.isSpoiler) {
                                        Modifier.clickable { isSpoilerRevealed = false }
                                    } else {
                                        Modifier
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Ciphertext snippet, Ratchet step, Edited badge, Timestamp & Read Receipt Checkmarks
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable(onClick = onInspectCipher)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = "Zarfı İncele",
                            tint = NeonCyberGreen.copy(alpha = 0.75f),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "R#${message.ratchetStep} • ${message.ciphertext.take(8)}...",
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (message.isEdited) {
                            Text(
                                text = "(düzenlendi) ",
                                style = MaterialTheme.typography.labelSmall,
                                color = CyberCyan
                            )
                        }
                        Text(
                            text = timeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (isMine) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = if (message.readByCount > 1) Icons.Default.DoneAll else Icons.Default.Done,
                                contentDescription = if (message.readByCount > 1) "Okundu" else "İletildi",
                                tint = if (message.readByCount > 1) NeonCyberGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }

                // Reactions Display
                if (message.reactions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        message.reactions.entries.take(5).forEach { (user, emoji) ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = ObsidianBlack,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                Text(
                                    text = "$emoji @$user",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Quick Reaction, Reply, Secure Copy, Edit, Star, Pin, Forward & Burn Action Bar
        AnimatedVisibility(visible = showReactionBar) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        listOf("⚡", "🛡️", "🔥", "👍", "❤️", "🎯").forEach { emoji ->
                            Text(
                                text = emoji,
                                modifier = Modifier
                                    .clickable {
                                        onToggleReaction(emoji)
                                        showReactionBar = false
                                    }
                                    .padding(4.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = {
                                onReply()
                                showReactionBar = false
                            },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Reply,
                                contentDescription = "Yanıtla",
                                tint = CyberCyan,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        IconButton(
                            onClick = {
                                onCopySecurely()
                                showReactionBar = false
                            },
                            modifier = Modifier
                                .size(30.dp)
                                .testTag("secure_copy_button_${message.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Güvenli Kopyala (30sn Otomatik Silinir)",
                                tint = NeonCyberGreen,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        if (isMine && message.messageType == "TEXT") {
                            IconButton(
                                onClick = {
                                    onEdit()
                                    showReactionBar = false
                                },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Düzenle",
                                    tint = NeonCyberGreen,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                onStar()
                                showReactionBar = false
                            },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = if (message.isStarred) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "Yıldızla",
                                tint = AmberTimerWarning,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        IconButton(
                            onClick = {
                                onPin()
                                showReactionBar = false
                            },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PushPin,
                                contentDescription = "Sabitle",
                                tint = AmberTimerWarning,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        IconButton(
                            onClick = {
                                onForward()
                                showReactionBar = false
                            },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Forward,
                                contentDescription = "İlet",
                                tint = CyberCyan,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        IconButton(
                            onClick = {
                                onBurnNow()
                                showReactionBar = false
                            },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteForever,
                                contentDescription = "Mesajı Şimdi İmha Et",
                                tint = BurnCoralRed,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduleEncryptedMessageDialog(
    initialText: String,
    onDismiss: () -> Unit,
    onSchedule: (String, Long) -> Unit
) {
    var textInput by remember(initialText) { mutableStateOf(initialText) }
    var selectedDelaySec by remember { mutableLongStateOf(30L) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, CyberCyan)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = CyberCyan
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "⏳ Zamanlanmış Şifreli Zarf",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Mesajınız cihazda şifrelenerek yerel kasaya alınır ve belirlediğiniz süre sonunda otomatik olarak P2P ağa iletilir.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    label = { Text("Zamanlanacak Mesaj") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Gönderim Gecikmesi Seçin:",
                    style = MaterialTheme.typography.labelSmall,
                    color = CyberCyan
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    listOf(
                        10L to "10 sn",
                        30L to "30 sn",
                        60L to "1 dk",
                        300L to "5 dk"
                    ).forEach { (sec, label) ->
                        FilterChip(
                            selected = selectedDelaySec == sec,
                            onClick = { selectedDelaySec = sec },
                            label = { Text(label) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(onClick = onDismiss) {
                        Text("İptal")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (textInput.isNotBlank()) {
                                onSchedule(textInput, selectedDelaySec)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonCyberGreen,
                            contentColor = Color(0xFF002110)
                        )
                    ) {
                        Text("Zamanla", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ViewOnceRevealDialog(
    message: DecryptedMessageItem,
    onCloseAndBurn: () -> Unit
) {
    Dialog(onDismissRequest = onCloseAndBurn) {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.5.dp, BurnCoralRed)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "🔥👁️ Tek Seferlik Görüntüleme",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = BurnCoralRed
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Gönderen: @${message.senderUsername} • Bu pencereyi kapattığınız anda şifreli zarf ağdan kalıcı olarak imha edilecektir.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = ObsidianBlack,
                    border = BorderStroke(1.dp, NeonCyberGreen),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = message.decryptedText,
                            style = MaterialTheme.typography.bodyLarge,
                            color = NeonCyberGreen
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onCloseAndBurn,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BurnCoralRed,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Default.DeleteForever, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Kapat ve Zarfı Şimdi İmha Et", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun SteganographyComposerDialog(
    onDismiss: () -> Unit,
    onSendStegoImage: (String, String, String) -> Unit
) {
    var visibleCaption by remember { mutableStateOf("📷 Siber Ağ Topolojisi Görseli") }
    var secretMessage by remember { mutableStateOf("") }
    var pickedUri by remember { mutableStateOf("") }

    val stegoPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            pickedUri = uri.toString()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, CyberCyan)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.EnhancedEncryption,
                        contentDescription = null,
                        tint = CyberCyan
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "🧬 Steganografi (Görsele Şifre Göm)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Görsel zarfın içine sadece alıcının STEGO_LAYER alt anahtarıyla çözebileceği gizli bir metin gömün.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        stegoPhotoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    border = BorderStroke(1.dp, CyberCyan),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Default.Image, contentDescription = null, tint = CyberCyan)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (pickedUri.isNotBlank()) "Görsel Seçildi ✓" else "Galeriden Taşıyıcı Görsel Seç (Opsiyonel)",
                        color = CyberCyan
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = visibleCaption,
                    onValueChange = { visibleCaption = it },
                    label = { Text("Herkesin Göreceği Normal Başlık") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = secretMessage,
                    onValueChange = { secretMessage = it },
                    label = { Text("Görsel İçine Gömülecek Gizli Mesaj") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(onClick = onDismiss) {
                        Text("İptal")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (secretMessage.isNotBlank()) {
                                onSendStegoImage(pickedUri, visibleCaption, secretMessage)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonCyberGreen,
                            contentColor = Color(0xFF002110)
                        )
                    ) {
                        Text("Göm & Gönder", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupManagementAndKeyRotationDialog(
    room: ChatRoom,
    directoryPeers: List<UserProfile>,
    onDismiss: () -> Unit,
    onSaveMembersAndTitle: (String, List<UserProfile>) -> Unit,
    onRotateKey: () -> Unit,
    onNukeRoom: () -> Unit
) {
    var titleInput by remember(room.title) { mutableStateOf(room.title) }
    val selectedUids = remember(room.participantUids) {
        mutableStateListOf<String>().apply { addAll(room.participantUids) }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, NeonCyberGreen)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Grup Üyeleri & Anahtar Rotasyonu",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = titleInput,
                    onValueChange = { titleInput = it },
                    label = { Text("Sohbet / Grup Başlığı") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Katılımcı Düğümler (@kullanici_adi):",
                    style = MaterialTheme.typography.labelSmall,
                    color = CyberCyan
                )
                Spacer(modifier = Modifier.height(6.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(directoryPeers, key = { it.userId }) { peer ->
                        val isChecked = selectedUids.contains(peer.userId)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isChecked) {
                                        selectedUids.remove(peer.userId)
                                    } else {
                                        selectedUids.add(peer.userId)
                                    }
                                }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        if (!selectedUids.contains(peer.userId)) selectedUids.add(peer.userId)
                                    } else {
                                        selectedUids.remove(peer.userId)
                                    }
                                }
                            )
                            Text(
                                text = "@${peer.username} (${peer.displayName})",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = onRotateKey,
                    border = BorderStroke(1.dp, AmberTimerWarning),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rotate_room_key_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Autorenew,
                        contentDescription = null,
                        tint = AmberTimerWarning
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Oda Şifreleme Anahtarını Yenile (Key Rotation)",
                        color = AmberTimerWarning,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onNukeRoom,
                    border = BorderStroke(1.dp, BurnCoralRed),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("nuke_room_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteForever,
                        contentDescription = null,
                        tint = BurnCoralRed
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "☢️ Sohbet Geçmişini Tamamen İmha Et (Nuke Room)",
                        color = BurnCoralRed,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

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
                            val chosenPeers = directoryPeers.filter { selectedUids.contains(it.userId) }
                            onSaveMembersAndTitle(titleInput, chosenPeers)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonCyberGreen,
                            contentColor = Color(0xFF002110)
                        )
                    ) {
                        Text("Üyeleri Güncelle", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ForwardMessageRoomPickerDialog(
    message: DecryptedMessageItem,
    availableRooms: List<ChatRoom>,
    onDismiss: () -> Unit,
    onSelectRoom: (ChatRoom) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, CyberCyan)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Mesajı Yeniden Şifreleyerek İlet",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "\"${message.decryptedText.take(60)}\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (availableRooms.isEmpty()) {
                    Text(
                        text = "İletilecek başka aktif oda bulunamadı.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                    ) {
                        items(availableRooms, key = { it.id }) { targetRoom ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = ObsidianBlack,
                                border = BorderStroke(1.dp, NeonCyberGreen.copy(alpha = 0.4f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectRoom(targetRoom) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = targetRoom.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = NeonCyberGreen
                                    )
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Forward,
                                        contentDescription = null,
                                        tint = CyberCyan
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(onClick = onDismiss) {
                        Text("Kapat")
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(6.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = tint.copy(alpha = 0.16f),
            border = BorderStroke(1.dp, tint.copy(alpha = 0.5f)),
            modifier = Modifier.size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(imageVector = icon, contentDescription = label, tint = tint)
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun CreateEncryptedPollDialog(
    onDismiss: () -> Unit,
    onCreatePoll: (String, List<String>) -> Unit
) {
    var question by remember { mutableStateOf("") }
    var option1 by remember { mutableStateOf("") }
    var option2 by remember { mutableStateOf("") }
    var option3 by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, AmberTimerWarning)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Uçtan Uca Şifreli Anket Oluştur",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    label = { Text("Anket Sorusu") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = option1,
                    onValueChange = { option1 = it },
                    label = { Text("1. Seçenek") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = option2,
                    onValueChange = { option2 = it },
                    label = { Text("2. Seçenek") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = option3,
                    onValueChange = { option3 = it },
                    label = { Text("3. Seçenek (Opsiyonel)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(onClick = onDismiss) {
                        Text("İptal")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val opts = listOf(option1, option2, option3).filter { it.isNotBlank() }
                            if (question.isNotBlank() && opts.size >= 2) {
                                onCreatePoll(question, opts)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonCyberGreen,
                            contentColor = Color(0xFF002110)
                        )
                    ) {
                        Text("Şifrele ve Gönder", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
