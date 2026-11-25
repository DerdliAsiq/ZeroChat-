package com.example.ui.screens

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.crypto.CryptoEngine
import com.example.data.model.ChatRoom
import com.example.data.model.UserProfile
import com.example.ui.UiState
import com.example.ui.components.QrScannerAndVerifierDialog
import com.example.ui.theme.AmberTimerWarning
import com.example.ui.theme.BurnCoralRed
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.NeonCyberGreen
import com.example.ui.theme.ObsidianBlack
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatListAndDiscoveryScreen(
    myProfile: UserProfile?,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    directoryState: UiState<List<UserProfile>>,
    roomsState: UiState<List<ChatRoom>>,
    lockedRoomIds: Set<String>,
    pinnedRoomIds: Set<String> = emptySet(),
    verifiedPeerHandles: Set<String> = emptySet(),
    keyChangedPeerHandles: Set<String> = emptySet(),
    isVaultUnlocked: Boolean,
    isPanicModeActive: Boolean,
    isScreenShieldEnabled: Boolean = false,
    biometricUnlockEnabled: Boolean = true,
    onBiometricUnlock: () -> Unit = {},
    pendingOutboxCount: Int = 0,
    onFlushOutboxQueue: () -> Unit = {},
    onScanQrVerified: (String) -> Unit = {},
    onSelectPeerForChat: (UserProfile, String) -> Unit,
    onApproveConnectionRequest: (ChatRoom) -> Unit = {},
    onRejectConnectionRequest: (ChatRoom) -> Unit = {},
    onOpenRoom: (ChatRoom) -> Unit,
    onDeleteRoom: (ChatRoom) -> Unit = {},
    onTogglePinRoom: (String, Boolean) -> Unit = { _, _ -> },
    onToggleRoomVaultLock: (String, Boolean) -> Unit,
    onCreateGroup: (String, List<UserProfile>, Long) -> Unit,
    onVerifyPin: (String) -> String,
    onExitPanicOrLockVault: () -> Unit
) {
    var selectedFilter by remember { mutableStateOf("ALL") }
    var showPinDialog by remember { mutableStateOf(false) }
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var showQrScannerDialog by remember { mutableStateOf(false) }
    var peerForRequestDialog by remember { mutableStateOf<UserProfile?>(null) }
    var roomPendingDelete by remember { mutableStateOf<ChatRoom?>(null) }

    LaunchedEffect(isVaultUnlocked) {
        if (isVaultUnlocked) {
            showPinDialog = false
        }
    }

    val allRooms = (roomsState as? UiState.Success)?.data ?: emptyList()
    val pendingRequestRooms = remember(allRooms) {
        allRooms.filter { it.isPendingRequest }
    }
    val incomingRequestRooms = remember(allRooms, myProfile?.username) {
        allRooms.filter { it.isIncomingRequestFor(myProfile?.userId) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Top Cyber Identity Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = NeonCyberGreen,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "ZeroChat",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        if (isScreenShieldEnabled) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = CyberCyan.copy(alpha = 0.18f),
                                border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.VisibilityOff,
                                        contentDescription = "FLAG_SECURE Aktif",
                                        tint = CyberCyan,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "Kalkan",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = CyberCyan
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        text = if (myProfile != null) "@${myProfile.username} • Çevrimiçi" else "Bağlanıyor...",
                        style = MaterialTheme.typography.labelSmall,
                        color = NeonCyberGreen
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { showQrScannerDialog = true },
                        modifier = Modifier.testTag("qr_scanner_top_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = "QR Kod Tarayıcı",
                            tint = CyberCyan
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isPanicModeActive) {
                            MaterialTheme.colorScheme.errorContainer
                        } else if (isVaultUnlocked) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        border = BorderStroke(
                            1.dp,
                            if (isPanicModeActive) BurnCoralRed else NeonCyberGreen.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier
                            .clickable {
                                if (isVaultUnlocked || isPanicModeActive) {
                                    onExitPanicOrLockVault()
                                } else {
                                    showPinDialog = true
                                }
                            }
                            .testTag("vault_pin_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isVaultUnlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                                contentDescription = "Gizli Kasa PIN",
                                tint = NeonCyberGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                // Panic mode deliberately looks exactly like a locked vault.
                                text = if (isVaultUnlocked) "Kasa Açık" else "PIN Kasa",
                                style = MaterialTheme.typography.labelSmall,
                                color = NeonCyberGreen
                            )
                        }
                    }
                }
            }

            if (pendingOutboxCount > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, AmberTimerWarning),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = Icons.Default.CloudSync,
                                contentDescription = null,
                                tint = AmberTimerWarning,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Çevrimdışı Kuyruk: $pendingOutboxCount mesaj bekliyor",
                                style = MaterialTheme.typography.labelSmall,
                                color = AmberTimerWarning
                            )
                        }
                        OutlinedButton(
                            onClick = onFlushOutboxQueue,
                            border = BorderStroke(1.dp, NeonCyberGreen),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("Şimdi Gönder", style = MaterialTheme.typography.labelSmall, color = NeonCyberGreen)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Global @username search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = {
                    Text(
                        text = stringResource(R.string.search_username_hint),
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.AlternateEmail,
                        contentDescription = null,
                        tint = NeonCyberGreen
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Aramayı Temizle"
                            )
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonCyberGreen,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("username_search_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Filter Chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    CyberFilterChip(
                        label = "Tümü",
                        selected = selectedFilter == "ALL",
                        onClick = { selectedFilter = "ALL" }
                    )
                }
                item {
                    CyberFilterChip(
                        label = "İstekler (${pendingRequestRooms.size})",
                        selected = selectedFilter == "REQUESTS",
                        onClick = { selectedFilter = "REQUESTS" }
                    )
                }
                item {
                    CyberFilterChip(
                        label = "Kişiler",
                        selected = selectedFilter == "DIRECT",
                        onClick = { selectedFilter = "DIRECT" }
                    )
                }
                item {
                    CyberFilterChip(
                        label = "Gruplar",
                        selected = selectedFilter == "GROUPS",
                        onClick = { selectedFilter = "GROUPS" }
                    )
                }
                item {
                    CyberFilterChip(
                        label = "Gizli Kasa 🔒 (${lockedRoomIds.size})",
                        selected = selectedFilter == "VAULT",
                        onClick = {
                            if (!isVaultUnlocked) {
                                showPinDialog = true
                            }
                            selectedFilter = "VAULT"
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                contentPadding = PaddingValues(bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // Incoming Connection / Follow Requests Inbox Card
                if (!isPanicModeActive && incomingRequestRooms.isNotEmpty()) {
                    item {
                        IncomingRequestsInboxSection(
                            incomingRooms = incomingRequestRooms,
                            onApprove = onApproveConnectionRequest,
                            onReject = onRejectConnectionRequest,
                            onInspect = onOpenRoom
                        )
                    }
                }

                // Global @username peer discovery section (always active regardless of directory loading state)
                if (!isPanicModeActive) {
                    val peers = (directoryState as? UiState.Success)?.data ?: emptyList()
                    val cleanSearchedHandle = searchQuery.trim().removePrefix("@").lowercase(Locale.ROOT)
                        .replace("[^a-z0-9_\\-.]".toRegex(), "")
                    val exactMatchedPeer = if (cleanSearchedHandle.isNotBlank()) {
                        peers.firstOrNull { it.username.equals(cleanSearchedHandle, ignoreCase = true) }
                    } else {
                        null
                    }

                    if (searchQuery.isNotBlank() || peers.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (searchQuery.isNotBlank()) {
                                        "Kullanıcı Arama Sonuçları (${peers.size} kişi)"
                                    } else {
                                        "Kişiler (@kullanici_adi ile Bağlan)"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CyberCyan
                                )

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = CyberCyan.copy(alpha = 0.14f),
                                    border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.55f)),
                                    modifier = Modifier
                                        .clickable { showQrScannerDialog = true }
                                        .testTag("open_qr_from_directory_button")
                                ) {
                                    Text(
                                        text = "📷 QR Tara / Kodum",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = CyberCyan,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        // Always show instant match / connection card when user searches a @username!
                        if (cleanSearchedHandle.isNotBlank() && exactMatchedPeer == null && cleanSearchedHandle.length >= 2) {
                            item {
                                Text(
                                    text = "@$cleanSearchedHandle ile eşleşen kullanıcı bulunamadı. Kullanıcı adını tam yazın veya QR kodu tarayın.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 6.dp)
                                )
                            }
                        }
                        if (exactMatchedPeer != null) {
                            item {
                                val targetPeer = exactMatchedPeer
                                val existingRoomForTarget = allRooms.firstOrNull { room ->
                                    !room.isGroup &&
                                        (room.participantUids.contains(targetPeer.userId) ||
                                            room.peerHandleFor(myProfile?.username).equals(targetPeer.username, ignoreCase = true))
                                }

                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    ),
                                    border = BorderStroke(1.5.dp, NeonCyberGreen),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "✅ Kullanıcı Bulundu: @${targetPeer.username}",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = NeonCyberGreen
                                                )
                                                Text(
                                                    text = targetPeer.displayName,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Button(
                                                onClick = {
                                                    if (existingRoomForTarget != null && !existingRoomForTarget.isRejectedRequest) {
                                                        onOpenRoom(existingRoomForTarget)
                                                    } else {
                                                        onSelectPeerForChat(targetPeer, "")
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = NeonCyberGreen,
                                                    contentColor = Color(0xFF002110)
                                                ),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .testTag("direct_match_connect_button")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.PersonAdd,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = if (existingRoomForTarget != null && !existingRoomForTarget.isRejectedRequest) {
                                                        "💬 @${targetPeer.username} Sohbetini Aç"
                                                    } else {
                                                        "💬 @${targetPeer.username} İle Şifreli Sohbet Başlat"
                                                    },
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            OutlinedButton(
                                                onClick = { peerForRequestDialog = targetPeer },
                                                border = BorderStroke(1.dp, CyberCyan)
                                            ) {
                                                Text("Not İle", color = CyberCyan)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (peers.isNotEmpty()) {
                            item {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(peers, key = { it.userId }) { peer ->
                                        val handle = peer.username.lowercase(Locale.ROOT)
                                        val existingRoom = allRooms.firstOrNull { room ->
                                            !room.isGroup &&
                                                (room.participantUids.contains(peer.userId) ||
                                                    room.title.removePrefix("@").equals(peer.username, ignoreCase = true))
                                        }
                                        val connectionStatus = when {
                                            existingRoom == null || existingRoom.isRejectedRequest -> "NONE"
                                            existingRoom.isApprovedConnection -> "APPROVED"
                                            existingRoom.isIncomingRequestFor(myProfile?.userId) -> "INCOMING_PENDING"
                                            else -> "OUTGOING_PENDING"
                                        }
                                        DirectoryPeerChipCard(
                                            peer = peer,
                                            connectionStatus = connectionStatus,
                                            isVerified = verifiedPeerHandles.contains(handle),
                                            hasKeyChangedWarning = keyChangedPeerHandles.contains(handle),
                                            onConnect = {
                                                if (existingRoom != null && !existingRoom.isRejectedRequest) {
                                                    onOpenRoom(existingRoom)
                                                } else {
                                                    onSelectPeerForChat(peer, "")
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Active Encrypted Rooms Section
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = when (selectedFilter) {
                            "REQUESTS" -> "Mesaj İstekleri"
                            "VAULT" -> "Gizli Sohbetler"
                            "GROUPS" -> "Gruplar"
                            "DIRECT" -> "Kişiler"
                            else -> "Sohbetler"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = NeonCyberGreen
                    )
                }

                when (roomsState) {
                    is UiState.Loading -> {
                        item {
                            Text(
                                text = "Sohbetler yükleniyor...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 16.dp)
                            )
                        }
                    }
                    is UiState.Error -> {
                        item {
                            Text(
                                text = roomsState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    is UiState.Success -> {
                        val filteredRooms = roomsState.data.filter { room ->
                            val isLocked = lockedRoomIds.contains(room.id)
                            when (selectedFilter) {
                                "REQUESTS" -> room.isPendingRequest || room.isRejectedRequest
                                "VAULT" -> isLocked && isVaultUnlocked
                                "DIRECT" -> !isLocked && !room.isGroup && room.isApprovedConnection
                                "GROUPS" -> !isLocked && room.isGroup
                                else -> !isLocked || isVaultUnlocked
                            }
                        }

                        if (selectedFilter == "VAULT" && !isVaultUnlocked) {
                            item {
                                LockedVaultPromptCard(onUnlockClick = { showPinDialog = true })
                            }
                        } else if (filteredRooms.isEmpty()) {
                            item {
                                EmptyRoomsCard(
                                    onNewGroupClick = { showCreateGroupDialog = true }
                                )
                            }
                        } else {
                            items(filteredRooms, key = { it.id }) { room ->
                                val isLocked = lockedRoomIds.contains(room.id)
                                val isPinned = pinnedRoomIds.contains(room.id)
                                val roomHandle = room.peerHandleFor(myProfile?.username).trim().lowercase(Locale.ROOT)
                                val isVerified = verifiedPeerHandles.contains(roomHandle)
                                val hasKeyChanged = keyChangedPeerHandles.contains(roomHandle)
                                EncryptedRoomCard(
                                    room = room,
                                    myUsername = myProfile?.username,
                                    myUid = myProfile?.userId,
                                    isVaultLocked = isLocked,
                                    isPinned = isPinned,
                                    isPeerVerified = isVerified,
                                    hasKeyChangedWarning = hasKeyChanged,
                                    onClick = { onOpenRoom(room) },
                                    onApproveRequest = { onApproveConnectionRequest(room) },
                                    onRejectOrCancelRequest = { onRejectConnectionRequest(room) },
                                    onTogglePin = { onTogglePinRoom(room.id, isPinned) },
                                    onToggleLock = { onToggleRoomVaultLock(room.id, isLocked) },
                                    onDelete = { roomPendingDelete = room }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Floating Action Button for New Encrypted Group
        FloatingActionButton(
            onClick = { showCreateGroupDialog = true },
            containerColor = NeonCyberGreen,
            contentColor = Color(0xFF002110),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("create_group_fab")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(imageVector = Icons.Default.GroupAdd, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.new_group_button),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    peerForRequestDialog?.let { target ->
        SendConnectionRequestDialog(
            peer = target,
            onDismiss = { peerForRequestDialog = null },
            onSendRequest = { introNote ->
                peerForRequestDialog = null
                onSelectPeerForChat(target, introNote)
            }
        )
    }

    if (showPinDialog) {
        VaultPinEntryDialog(
            biometricUnlockEnabled = biometricUnlockEnabled,
            onBiometricUnlock = {
                onBiometricUnlock()
            },
            onDismiss = { showPinDialog = false },
            onVerifyPin = { pin ->
                val status = onVerifyPin(pin)
                if (status == "UNLOCKED" || status == "PANIC") {
                    showPinDialog = false
                }
                status
            }
        )
    }

    roomPendingDelete?.let { targetRoom ->
        Dialog(onDismissRequest = { roomPendingDelete = null }) {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.5.dp, BurnCoralRed)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = null,
                            tint = BurnCoralRed
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Sohbeti Kalıcı Olarak Sil",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = BurnCoralRed
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "'${targetRoom.title}' sohbetini ve içindeki tüm şifreli zarfları son sohbetler listesinden kalıcı olarak silmek istediğinize emin misiniz?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(onClick = { roomPendingDelete = null }) {
                            Text("Vazgeç")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                roomPendingDelete = null
                                onDeleteRoom(targetRoom)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BurnCoralRed,
                                contentColor = Color.White
                            ),
                            modifier = Modifier.testTag("confirm_delete_room_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Evet, Sohbeti Sil", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (showCreateGroupDialog) {
        val peers = (directoryState as? UiState.Success)?.data ?: emptyList()
        CreateEncryptedGroupDialog(
            availablePeers = peers,
            onDismiss = { showCreateGroupDialog = false },
            onCreate = { title, selectedPeers, ttl ->
                onCreateGroup(title, selectedPeers, ttl)
                showCreateGroupDialog = false
            }
        )
    }

    if (showQrScannerDialog) {
        val peers = (directoryState as? UiState.Success)?.data ?: emptyList()
        QrScannerAndVerifierDialog(
            myProfile = myProfile,
            availablePeers = peers,
            onDismiss = { showQrScannerDialog = false },
            onScanVerified = { scanned ->
                showQrScannerDialog = false
                onScanQrVerified(scanned)
            }
        )
    }
}

@Composable
private fun IncomingRequestsInboxSection(
    incomingRooms: List<ChatRoom>,
    onApprove: (ChatRoom) -> Unit,
    onReject: (ChatRoom) -> Unit,
    onInspect: (ChatRoom) -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.5.dp, CyberCyan),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.MarkEmailUnread,
                    contentDescription = null,
                    tint = CyberCyan,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "📨 Gelen Sohbet İstekleri (${incomingRooms.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = CyberCyan
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Sizinle mesajlaşmak isteyen kişiler:",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))

            incomingRooms.forEach { reqRoom -> key(reqRoom.id) {
                val notePreview = reqRoom.previewText.ifBlank { "🤝 @${reqRoom.requesterUsername} sizinle sohbet başlatmak istiyor." }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ObsidianBlack,
                    border = BorderStroke(1.dp, NeonCyberGreen.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { onInspect(reqRoom) }
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "@${reqRoom.requesterUsername.ifBlank { reqRoom.title.removePrefix("@") }}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = NeonCyberGreen
                            )
                            Text(
                                text = "Yeni İstek",
                                style = MaterialTheme.typography.labelSmall,
                                color = CyberCyan
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = notePreview,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            OutlinedButton(
                                onClick = { onReject(reqRoom) },
                                border = BorderStroke(1.dp, BurnCoralRed),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("reject_request_button_${reqRoom.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = null,
                                    tint = BurnCoralRed,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Reddet", style = MaterialTheme.typography.labelSmall, color = BurnCoralRed)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { onApprove(reqRoom) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NeonCyberGreen,
                                    contentColor = Color(0xFF002110)
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("approve_request_button_${reqRoom.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Onayla & Bağlan",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            } }
        }
    }
}

@Composable
private fun SendConnectionRequestDialog(
    peer: UserProfile,
    onDismiss: () -> Unit,
    onSendRequest: (String) -> Unit
) {
    var introNote by remember(peer.username) {
        mutableStateOf("👋 Merhaba @${peer.username}, seni kullanıcı adınla buldum. Şifreli iletişim isteğimi onaylar mısın?")
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.5.dp, NeonCyberGreen)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.PersonAdd,
                        contentDescription = null,
                        tint = NeonCyberGreen
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "İletişim & Takip İsteği Gönder",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ObsidianBlack,
                    border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "@${peer.username} (${peer.displayName})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = NeonCyberGreen
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "🔒 Uçtan uca şifreli bağlantı",
                            style = MaterialTheme.typography.labelSmall,
                            color = CyberCyan
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Bu kullanıcıya ilk mesajınızla birlikte bir sohbet isteği gönderilecektir.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = introNote,
                    onValueChange = { introNote = it },
                    label = { Text("Şifreli İstek & Tanışma Notu") },
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("connection_request_note_input")
                )
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
                        onClick = { onSendRequest(introNote) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonCyberGreen,
                            contentColor = Color(0xFF002110)
                        ),
                        modifier = Modifier.testTag("confirm_send_request_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PersonAdd,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("İstek Gönder", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun CyberFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text = label, style = MaterialTheme.typography.labelLarge) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = NeonCyberGreen
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outline,
            selectedBorderColor = NeonCyberGreen
        )
    )
}

@Composable
private fun DirectoryPeerChipCard(
    peer: UserProfile,
    connectionStatus: String = "NONE",
    isVerified: Boolean = false,
    hasKeyChangedWarning: Boolean = false,
    onConnect: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            1.dp,
            when {
                hasKeyChangedWarning -> BurnCoralRed
                connectionStatus == "INCOMING_PENDING" -> CyberCyan
                connectionStatus == "OUTGOING_PENDING" -> AmberTimerWarning
                isVerified || connectionStatus == "APPROVED" -> NeonCyberGreen
                else -> CyberCyan.copy(alpha = 0.45f)
            }
        ),
        modifier = Modifier
            .width(220.dp)
            .clickable(onClick = onConnect)
            .testTag("peer_card_${peer.username}")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = peer.username.take(2).uppercase(Locale.ROOT),
                            style = MaterialTheme.typography.labelLarge,
                            color = CyberCyan,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "@${peer.username}",
                            style = MaterialTheme.typography.titleMedium,
                            color = NeonCyberGreen,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isVerified && !hasKeyChangedWarning) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Doğrulanmış Düğüm",
                                tint = NeonCyberGreen,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    Text(
                        text = peer.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = when (connectionStatus) {
                    "APPROVED" -> MaterialTheme.colorScheme.primaryContainer
                    "OUTGOING_PENDING" -> AmberTimerWarning.copy(alpha = 0.18f)
                    "INCOMING_PENDING" -> CyberCyan.copy(alpha = 0.2f)
                    else -> MaterialTheme.colorScheme.secondaryContainer
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = when (connectionStatus) {
                        "APPROVED" -> "✓ Mesajlaş →"
                        "OUTGOING_PENDING" -> "⏳ İstek Gönderildi"
                        "INCOMING_PENDING" -> "📨 İstek Geldi • Onayla →"
                        else -> "➕ Mesaj Gönder"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = when (connectionStatus) {
                        "APPROVED" -> NeonCyberGreen
                        "OUTGOING_PENDING" -> AmberTimerWarning
                        "INCOMING_PENDING" -> CyberCyan
                        else -> CyberCyan
                    },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                )
            }
        }
    }
}

@Composable
private fun EncryptedRoomCard(
    room: ChatRoom,
    myUsername: String? = null,
    myUid: String? = null,
    isVaultLocked: Boolean,
    isPinned: Boolean = false,
    isPeerVerified: Boolean = false,
    hasKeyChangedWarning: Boolean = false,
    onClick: () -> Unit,
    onApproveRequest: () -> Unit = {},
    onRejectOrCancelRequest: () -> Unit = {},
    onTogglePin: () -> Unit = {},
    onToggleLock: () -> Unit,
    onDelete: () -> Unit = {}
) {
    val isIncomingReq = room.isIncomingRequestFor(myUid)
    val isOutgoingReq = room.isOutgoingRequestFrom(myUid)
    val displayTitle = remember(room.title, room.participantNames, room.isGroup, myUsername) {
        room.displayTitleFor(myUsername)
    }

    val decryptedPreview = room.previewText
    val timeText = remember(room.activityMillis) {
        val date = Date(room.activityMillis.takeIf { it > 0L } ?: System.currentTimeMillis())
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            1.dp,
            when {
                hasKeyChangedWarning || room.isRejectedRequest -> BurnCoralRed
                isIncomingReq -> CyberCyan
                isOutgoingReq -> AmberTimerWarning
                isPinned -> CyberCyan.copy(alpha = 0.7f)
                isVaultLocked -> AmberTimerWarning.copy(alpha = 0.6f)
                else -> MaterialTheme.colorScheme.outline
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("room_card_${room.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = if (room.isGroup) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = displayTitle.removePrefix("@").take(2).uppercase(Locale.ROOT),
                            style = MaterialTheme.typography.titleMedium,
                            color = if (room.isGroup) CyberCyan else NeonCyberGreen,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isPinned) {
                                Icon(
                                    imageVector = Icons.Default.PushPin,
                                    contentDescription = "Sabitlenmiş Oda",
                                    tint = CyberCyan,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(
                                text = displayTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (isPeerVerified && !hasKeyChangedWarning) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Doğrulanmış Kişi",
                                    tint = NeonCyberGreen,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            if (hasKeyChangedWarning) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.WarningAmber,
                                    contentDescription = "Anahtar Değişimi Uyarısı",
                                    tint = BurnCoralRed,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                            if (room.defaultTtlSeconds > 0L) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = AmberTimerWarning.copy(alpha = 0.2f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Timer,
                                            contentDescription = null,
                                            tint = AmberTimerWarning,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text(
                                            text = "${room.defaultTtlSeconds}s",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = AmberTimerWarning
                                        )
                                    }
                                }
                            }
                        }

                        Text(
                            text = timeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Connection request status pill if pending/rejected
                    if (room.isPendingRequest || room.isRejectedRequest) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = when {
                                room.isRejectedRequest -> BurnCoralRed.copy(alpha = 0.2f)
                                isIncomingReq -> CyberCyan.copy(alpha = 0.2f)
                                else -> AmberTimerWarning.copy(alpha = 0.2f)
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (room.isRejectedRequest) Icons.Default.Close else Icons.Default.HourglassTop,
                                    contentDescription = null,
                                    tint = when {
                                        room.isRejectedRequest -> BurnCoralRed
                                        isIncomingReq -> CyberCyan
                                        else -> AmberTimerWarning
                                    },
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = when {
                                        room.isRejectedRequest -> "⛔ İstek Reddedildi"
                                        isIncomingReq -> "📨 Yeni Sohbet İsteği"
                                        else -> "⏳ İstek Gönderildi"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = when {
                                        room.isRejectedRequest -> BurnCoralRed
                                        isIncomingReq -> CyberCyan
                                        else -> AmberTimerWarning
                                    }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    Text(
                        text = if (room.isGroup && room.lastSenderName.isNotBlank()) {
                            "@${room.lastSenderName}: $decryptedPreview"
                        } else {
                            decryptedPreview
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (room.isGroup && room.participantNames.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Üyeler: ${room.participantNames}",
                            style = MaterialTheme.typography.labelSmall,
                            color = CyberCyan.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(
                    onClick = onTogglePin,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("pin_room_button_${room.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = "Sohbeti Sabitle",
                        tint = if (isPinned) CyberCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = onToggleLock,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("lock_room_button_${room.id}")
                ) {
                    Icon(
                        imageVector = if (isVaultLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = "Kasaya Kilitle",
                        tint = if (isVaultLocked) AmberTimerWarning else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("delete_room_button_${room.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Sohbeti Sil",
                        tint = BurnCoralRed,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (room.isPendingRequest) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = onRejectOrCancelRequest,
                        border = BorderStroke(1.dp, BurnCoralRed),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (isIncomingReq) "Reddet" else "İsteği Geri Çek",
                            style = MaterialTheme.typography.labelSmall,
                            color = BurnCoralRed
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onApproveRequest,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonCyberGreen,
                            contentColor = Color(0xFF002110)
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (isIncomingReq) "İsteği Onayla" else "Şifreli Kanalı Aç",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LockedVaultPromptCard(onUnlockClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, AmberTimerWarning.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = AmberTimerWarning,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Gizli Sohbet Arşivi Kilitli",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Gizli sohbetleri görüntülemek için kasa PIN kodunuzu girin. PIN belirlemediyseniz Profil ve Kasa sekmesinden belirleyin.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onUnlockClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AmberTimerWarning,
                    contentColor = Color.Black
                )
            ) {
                Text(text = "PIN Girerek Kasayı Aç", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun EmptyRoomsCard(onNewGroupClick: () -> Unit) {
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
                imageVector = Icons.Default.PersonSearch,
                contentDescription = null,
                tint = NeonCyberGreen,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.empty_chats_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.empty_chats_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(14.dp))
            OutlinedButton(
                onClick = onNewGroupClick,
                border = BorderStroke(1.dp, NeonCyberGreen)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = NeonCyberGreen
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Şifreli Grup Kanalı Oluştur", color = NeonCyberGreen)
            }
        }
    }
}

@Composable
private fun VaultPinEntryDialog(
    biometricUnlockEnabled: Boolean = true,
    onBiometricUnlock: () -> Unit = {},
    onDismiss: () -> Unit,
    onVerifyPin: (String) -> String
) {
    var pinInput by remember { mutableStateOf("") }
    var feedback by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, NeonCyberGreen)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = NeonCyberGreen,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.vault_pin_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Normal PIN gizli sohbetleri açar. Panik PIN tüm sohbetleri gizler; panik modundan yalnızca normal PIN ile çıkılır.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = pinInput,
                    onValueChange = { pinInput = it.filter(Char::isDigit).take(12) },
                    label = { Text("PIN Kodu") },
                    singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pin_dialog_input")
                )
                feedback?.let { feedbackText ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = feedbackText,
                        style = MaterialTheme.typography.labelSmall,
                        color = BurnCoralRed
                    )
                }
                if (biometricUnlockEnabled) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onBiometricUnlock,
                        border = BorderStroke(1.dp, CyberCyan),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("biometric_unlock_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fingerprint,
                            contentDescription = null,
                            tint = CyberCyan
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Parmak İzi / Biyometrik Sensör ile Aç", color = CyberCyan, fontWeight = FontWeight.Bold)
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
                            val result = onVerifyPin(pinInput)
                            pinInput = ""
                            feedback = when (result) {
                                "INVALID" -> "Hatalı PIN kodu!"
                                "LOCKED_OUT" -> "Çok fazla hatalı deneme, lütfen bekleyin"
                                "NOT_SET" -> "Önce Profil ve Kasa sekmesinden PIN belirleyin"
                                else -> null
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonCyberGreen,
                            contentColor = Color(0xFF002110)
                        ),
                        modifier = Modifier.testTag("pin_dialog_confirm")
                    ) {
                        Text("Doğrula", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateEncryptedGroupDialog(
    availablePeers: List<UserProfile>,
    onDismiss: () -> Unit,
    onCreate: (String, List<UserProfile>, Long) -> Unit
) {
    var groupTitle by remember { mutableStateOf("") }
    var selectedTtl by remember { mutableStateOf(0L) }
    val selectedPeers = remember { mutableStateListOf<UserProfile>() }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, NeonCyberGreen)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Yeni Uçtan Uca Şifreli Grup",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = groupTitle,
                    onValueChange = { groupTitle = it },
                    label = { Text("Grup Adı (Örn: Siber Operasyon)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("group_title_input")
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Varsayılan Mesaj İmha Süresi:",
                    style = MaterialTheme.typography.labelSmall,
                    color = CyberCyan
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0L to "Kapalı", 15L to "15 sn", 60L to "1 dk", 3600L to "1 saat").forEach { (sec, label) ->
                        FilterChip(
                            selected = selectedTtl == sec,
                            onClick = { selectedTtl = sec },
                            label = { Text(label) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Eklenecek @kullanici_adi Düğümleri (${selectedPeers.size} seçildi):",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                ) {
                    if (availablePeers.isEmpty()) {
                        item {
                            Text(
                                text = "Dizinde henüz başka kullanıcı yok; grubu tek başınıza kurup daha sonra üye ekleyebilirsiniz.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        items(availablePeers, key = { it.userId }) { peer ->
                            val isChecked = selectedPeers.any { it.userId == peer.userId }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isChecked) {
                                            selectedPeers.removeAll { it.userId == peer.userId }
                                        } else {
                                            selectedPeers.add(peer)
                                        }
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = {
                                        if (isChecked) {
                                            selectedPeers.removeAll { it.userId == peer.userId }
                                        } else {
                                            selectedPeers.add(peer)
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
                }
                Spacer(modifier = Modifier.height(12.dp))
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
                            onCreate(
                                groupTitle.ifBlank { "Siber Grup" },
                                selectedPeers.toList(),
                                selectedTtl
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonCyberGreen,
                            contentColor = Color(0xFF002110)
                        ),
                        modifier = Modifier.testTag("confirm_create_group_button")
                    ) {
                        Text("Şifreli Grubu Kur", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
