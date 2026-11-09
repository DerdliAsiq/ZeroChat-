package com.example

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.local.LocalVaultDatabase
import com.example.data.repository.ZeroChatRepository
import com.example.ui.UiState
import com.example.ui.ZeroChatViewModel
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.getSavedLocalNodeSession
import com.example.ui.auth.signOutZeroChat
import com.example.ui.screens.ActiveP2PCallOverlay
import com.example.ui.screens.CallsTabScreen
import com.example.ui.screens.ChatConversationScreen
import com.example.ui.screens.ChatListAndDiscoveryScreen
import com.example.ui.screens.IncomingCallRingingOverlay
import com.example.ui.screens.SecurityVaultAndProfileScreen
import com.example.ui.screens.StatusesTabScreen
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NeonCyberGreen
import com.example.ui.theme.ObsidianBlack
import com.example.util.authenticateWithRealBiometrics
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                ZeroChatRootApp()
            }
        }
    }
}

@Composable
fun ZeroChatRootApp(auth: FirebaseAuth = Firebase.auth) {
    val context = LocalContext.current
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    var localNodeSession by remember { mutableStateOf(getSavedLocalNodeSession(context)) }
    val user = currentUser

    val activeUid = user?.uid ?: localNodeSession?.first
    val activeHint = user?.email ?: localNodeSession?.second

    if (activeUid == null) {
        AuthScreen(
            onAuthSuccess = {
                localNodeSession = getSavedLocalNodeSession(context)
            },
            onNodeSessionSuccess = { uid, handle ->
                localNodeSession = uid to handle
            }
        )
    } else {
        key(activeUid) {
            ZeroChatAuthenticatedShell(
                currentUserId = activeUid,
                userEmailHint = activeHint,
                onSignedOut = {
                    localNodeSession = null
                }
            )
        }
    }
}

@Composable
fun ZeroChatAuthenticatedShell(
    currentUserId: String,
    userEmailHint: String?,
    onSignedOut: () -> Unit = {}
) {
    val viewModel: ZeroChatViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                val vaultDb = LocalVaultDatabase.getInstance(app)
                ZeroChatViewModel(
                    repository = ZeroChatRepository(db, currentUserId),
                    vaultDao = vaultDb.vaultDao(),
                    currentUserId = currentUserId,
                    initialEmailHint = userEmailHint
                )
            }
        }
    )

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    val myProfile by viewModel.myProfile.collectAsStateWithLifecycle()
    val hardwareKeyStatus by viewModel.hardwareKeyStatus.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val inChatSearchQuery by viewModel.inChatSearchQuery.collectAsStateWithLifecycle()
    val directoryState by viewModel.directoryUsersState.collectAsStateWithLifecycle()
    val roomsState by viewModel.roomsState.collectAsStateWithLifecycle()
    val selectedRoom by viewModel.selectedRoom.collectAsStateWithLifecycle()
    val activeRoomMessages by viewModel.activeRoomMessages.collectAsStateWithLifecycle()
    val replyingToMessage by viewModel.replyingToMessage.collectAsStateWithLifecycle()
    val editingMessage by viewModel.editingMessage.collectAsStateWithLifecycle()
    val peerTypingHandle by viewModel.peerTypingHandleInActiveRoom.collectAsStateWithLifecycle()
    val statusesState by viewModel.statusesState.collectAsStateWithLifecycle()
    val callSignalsState by viewModel.callSignalsState.collectAsStateWithLifecycle()
    val activeCall by viewModel.activeCall.collectAsStateWithLifecycle()
    val incomingRingingCall by viewModel.incomingRingingCall.collectAsStateWithLifecycle()
    val lockedRoomIds by viewModel.lockedRoomIds.collectAsStateWithLifecycle()
    val pinnedRoomIds by viewModel.pinnedRoomIds.collectAsStateWithLifecycle()
    val verifiedPeerHandles by viewModel.verifiedPeerHandles.collectAsStateWithLifecycle()
    val keyChangedPeerHandles by viewModel.keyChangedPeerHandles.collectAsStateWithLifecycle()
    val activeRoomScheduledEnvelopes by viewModel.activeRoomScheduledEnvelopes.collectAsStateWithLifecycle()
    val securityConfig by viewModel.securityConfig.collectAsStateWithLifecycle()
    val isVaultUnlocked by viewModel.isVaultUnlocked.collectAsStateWithLifecycle()
    val isPanicModeActive by viewModel.isPanicModeActive.collectAsStateWithLifecycle()
    val isScreenShieldEnabled by viewModel.isScreenShieldEnabled.collectAsStateWithLifecycle()
    val isOfflineQueueMode by viewModel.isOfflineQueueMode.collectAsStateWithLifecycle()
    val pendingOutboxItems by viewModel.pendingOutboxItems.collectAsStateWithLifecycle()
    val cachedVaultMessages by viewModel.cachedVaultMessages.collectAsStateWithLifecycle()
    val starredVaultMessages by viewModel.starredVaultMessages.collectAsStateWithLifecycle()
    val exportedVaultBackupText by viewModel.exportedVaultBackupText.collectAsStateWithLifecycle()
    val bannerMessage by viewModel.statusBannerMessage.collectAsStateWithLifecycle()

    // Automatic Vault Lock when app moves to background (ON_STOP)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                viewModel.onAppBackgrounded()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Dynamically enforce WindowManager.LayoutParams.FLAG_SECURE when Screen Shield, Vault, or Panic Mode is active
    LaunchedEffect(isScreenShieldEnabled) {
        val activity = context as? Activity
        if (activity != null) {
            if (isScreenShieldEnabled) {
                activity.window.setFlags(
                    WindowManager.LayoutParams.FLAG_SECURE,
                    WindowManager.LayoutParams.FLAG_SECURE
                )
            } else {
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }

    var currentTab by remember { mutableStateOf("CHATS") }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(bannerMessage) {
        val msg = bannerMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearBannerMessage()
        }
    }

    // Full-screen Active P2P Call Overlay takes precedence
    if (activeCall != null) {
        ActiveP2PCallOverlay(
            callSignal = activeCall!!,
            onEndCall = { viewModel.endActiveCall() }
        )
        return
    }

    // Full-screen Incoming P2P Call Ringing Overlay
    if (incomingRingingCall != null) {
        IncomingCallRingingOverlay(
            callSignal = incomingRingingCall!!,
            onAcceptCall = { viewModel.acceptIncomingCall(incomingRingingCall!!) },
            onDeclineCall = { viewModel.declineIncomingCall(incomingRingingCall!!) }
        )
        return
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = ObsidianBlack,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = CarbonSurface,
                    contentColor = NeonCyberGreen
                )
            }
        },
        bottomBar = {
            if (selectedRoom == null) {
                NavigationBar(
                    containerColor = CarbonSurface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    NavigationBarItem(
                        selected = currentTab == "CHATS",
                        onClick = { currentTab = "CHATS" },
                        icon = {
                            Icon(
                                imageVector = if (currentTab == "CHATS") Icons.AutoMirrored.Filled.Chat else Icons.Outlined.ChatBubbleOutline,
                                contentDescription = stringResource(R.string.tab_chats)
                            )
                        },
                        label = { Text(stringResource(R.string.tab_chats)) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = NeonCyberGreen,
                            selectedTextColor = NeonCyberGreen,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.testTag("nav_tab_chats")
                    )

                    NavigationBarItem(
                        selected = currentTab == "STATUSES",
                        onClick = { currentTab = "STATUSES" },
                        icon = {
                            Icon(
                                imageVector = if (currentTab == "STATUSES") Icons.Filled.RadioButtonChecked else Icons.Outlined.RadioButtonUnchecked,
                                contentDescription = stringResource(R.string.tab_statuses)
                            )
                        },
                        label = { Text(stringResource(R.string.tab_statuses)) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = NeonCyberGreen,
                            selectedTextColor = NeonCyberGreen,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.testTag("nav_tab_statuses")
                    )

                    NavigationBarItem(
                        selected = currentTab == "CALLS",
                        onClick = { currentTab = "CALLS" },
                        icon = {
                            Icon(
                                imageVector = if (currentTab == "CALLS") Icons.Filled.Call else Icons.Outlined.Call,
                                contentDescription = stringResource(R.string.tab_calls)
                            )
                        },
                        label = { Text(stringResource(R.string.tab_calls)) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = NeonCyberGreen,
                            selectedTextColor = NeonCyberGreen,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.testTag("nav_tab_calls")
                    )

                    NavigationBarItem(
                        selected = currentTab == "VAULT",
                        onClick = { currentTab = "VAULT" },
                        icon = {
                            Icon(
                                imageVector = if (currentTab == "VAULT") Icons.Filled.Shield else Icons.Outlined.Shield,
                                contentDescription = stringResource(R.string.tab_vault)
                            )
                        },
                        label = { Text(stringResource(R.string.tab_vault)) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = NeonCyberGreen,
                            selectedTextColor = NeonCyberGreen,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.testTag("nav_tab_vault")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val activeRoom = selectedRoom
            val allRoomsList = (roomsState as? UiState.Success)?.data ?: emptyList()
            val directoryPeersList = (directoryState as? UiState.Success)?.data ?: emptyList()

            if (activeRoom != null) {
                val peerHandleInRoom = activeRoom.title.removePrefix("@").trim().lowercase()

                ChatConversationScreen(
                    room = activeRoom,
                    currentUserId = currentUserId,
                    myUsername = myProfile?.username,
                    messages = activeRoomMessages,
                    allRooms = allRoomsList,
                    directoryPeers = directoryPeersList,
                    isRoomPeerVerified = verifiedPeerHandles.contains(peerHandleInRoom),
                    hasRoomKeyChangedWarning = keyChangedPeerHandles.contains(peerHandleInRoom),
                    scheduledEnvelopes = activeRoomScheduledEnvelopes,
                    inChatSearchQuery = inChatSearchQuery,
                    onInChatSearchQueryChange = viewModel::updateInChatSearchQuery,
                    replyingToMessage = replyingToMessage,
                    onSetReplyingTo = viewModel::setReplyingTo,
                    editingMessage = editingMessage,
                    onSetEditingMessage = viewModel::setEditingMessage,
                    peerTypingHandle = peerTypingHandle,
                    onUserTyping = viewModel::notifyUserTypingInActiveRoom,
                    onForwardMessageToRoom = viewModel::forwardMessageToRoom,
                    onTogglePinMessage = viewModel::togglePinMessage,
                    onToggleStarMessage = viewModel::toggleStarMessage,
                    onUpdateGroupMembersAndTitle = viewModel::updateActiveGroupMembersAndTitle,
                    onRotateRoomKey = viewModel::rotateActiveRoomEncryptionKey,
                    onVerifyRoomIdentity = viewModel::verifyActiveRoomIdentity,
                    onSimulateMitmKeyChange = viewModel::simulateMitmKeyChangeForActiveRoom,
                    onScheduleMessage = viewModel::scheduleEncryptedMessage,
                    onCancelScheduledMessage = viewModel::cancelScheduledMessage,
                    onNukeRoomHistory = viewModel::nukeActiveRoomHistory,
                    onNotifySecureClipboard = viewModel::notifySecureClipboardCopied,
                    onApproveConnectionRequest = { viewModel.approveConnectionRequest(activeRoom) },
                    onRejectOrCancelConnectionRequest = { viewModel.rejectOrCancelConnectionRequest(activeRoom) },
                    onBack = { viewModel.closeRoom() },
                    onSendText = { text, ttl, isViewOnce, isSpoiler ->
                        viewModel.sendTextMessage(text, ttl, isViewOnce, isSpoiler)
                    },
                    onSendVoiceNote = { dur, wave, b64, ttl ->
                        viewModel.sendVoiceNoteMessage(dur, wave, b64, ttl)
                    },
                    onSendImageAttachment = { uri, cap, ttl, isViewOnce, stegoSecret ->
                        viewModel.sendImageAttachmentMessage(uri, cap, ttl, isViewOnce, stegoSecret)
                    },
                    onSendFileAttachment = { name, sizeKb, mime, ttl ->
                        viewModel.sendFileAttachmentMessage(name, sizeKb, mime, ttl)
                    },
                    onSendLocation = { lat, lng, lbl, ttl ->
                        viewModel.sendLocationMessage(lat, lng, lbl, ttl)
                    },
                    onSendPoll = { q, opts, ttl -> viewModel.sendPollMessage(q, opts, ttl) },
                    onVotePoll = { msg, idx -> viewModel.voteInPoll(msg, idx) },
                    onToggleReaction = { msg, emoji -> viewModel.toggleMessageReaction(msg, emoji) },
                    onBurnMessageNow = { id -> viewModel.burnMessageNow(id) },
                    onSetRoomDefaultTtl = { ttl -> viewModel.setRoomDefaultTtl(activeRoom, ttl) },
                    onStartCall = { callType ->
                        val peerUid = activeRoom.participantUids.firstOrNull { it != currentUserId } ?: currentUserId
                        val peerHandle = activeRoom.title.removePrefix("@")
                        viewModel.startP2PCall(peerUid, peerHandle, callType)
                    }
                )
            } else {
                when (currentTab) {
                    "CHATS" -> {
                        ChatListAndDiscoveryScreen(
                            myProfile = myProfile,
                            searchQuery = searchQuery,
                            onSearchQueryChange = viewModel::updateSearchQuery,
                            directoryState = directoryState,
                            roomsState = roomsState,
                            lockedRoomIds = lockedRoomIds,
                            pinnedRoomIds = pinnedRoomIds,
                            verifiedPeerHandles = verifiedPeerHandles,
                            keyChangedPeerHandles = keyChangedPeerHandles,
                            isVaultUnlocked = isVaultUnlocked,
                            isPanicModeActive = isPanicModeActive,
                            isScreenShieldEnabled = isScreenShieldEnabled,
                            biometricUnlockEnabled = securityConfig?.biometricUnlockEnabled ?: true,
                            pendingOutboxCount = pendingOutboxItems.size,
                            onFlushOutboxQueue = viewModel::flushOfflineOutboxQueue,
                            onScanQrVerified = viewModel::handleScannedPeerHandleOrKey,
                            onSelectPeerForChat = { peer, note ->
                                viewModel.startDirectChatWithPeer(peer, note)
                            },
                            onApproveConnectionRequest = viewModel::approveConnectionRequest,
                            onRejectConnectionRequest = viewModel::rejectOrCancelConnectionRequest,
                            onSimulateIncomingRequest = { viewModel.simulateIncomingConnectionRequest() },
                            onOpenRoom = viewModel::openRoom,
                            onDeleteRoom = viewModel::deleteChatRoomFromRecent,
                            onToggleRoomVaultLock = viewModel::toggleRoomVaultLock,
                            onTogglePinRoom = viewModel::togglePinRoom,
                            onCreateGroup = viewModel::createNewEncryptedGroup,
                            onVerifyPin = viewModel::verifyPinInput,
                            onBiometricUnlock = {
                                authenticateWithRealBiometrics(
                                    context = context,
                                    onSuccess = { viewModel.unlockVaultWithBiometrics() },
                                    onError = { errMsg -> viewModel.postBannerMessage(errMsg) }
                                )
                            },
                            onExitPanicOrLockVault = viewModel::lockVaultAndExitPanicMode
                        )
                    }
                    "STATUSES" -> {
                        StatusesTabScreen(
                            statusesState = statusesState,
                            onPublishStatus = viewModel::publishStatusStory,
                            onViewStatus = viewModel::onViewStatusStory,
                            onReplyToStatus = viewModel::replyToStatusStory
                        )
                    }
                    "CALLS" -> {
                        CallsTabScreen(
                            callsState = callSignalsState,
                            directoryState = directoryState,
                            onStartCall = { uid, handle, type ->
                                viewModel.startP2PCall(uid, handle, type)
                            },
                            onSimulateIncomingCall = { peerHandle, callType ->
                                viewModel.simulateIncomingP2PCall(peerHandle, callType)
                            }
                        )
                    }
                    "VAULT" -> {
                        SecurityVaultAndProfileScreen(
                            myProfile = myProfile,
                            hardwareKeyStatus = hardwareKeyStatus,
                            isVaultUnlocked = isVaultUnlocked,
                            isPanicModeActive = isPanicModeActive,
                            isScreenShieldEnabled = isScreenShieldEnabled,
                            isOfflineQueueMode = isOfflineQueueMode,
                            biometricUnlockEnabled = securityConfig?.biometricUnlockEnabled ?: true,
                            autoLockOnBackground = securityConfig?.autoLockOnBackground ?: true,
                            pendingOutboxCount = pendingOutboxItems.size,
                            cachedMessagesCount = cachedVaultMessages.size,
                            starredMessages = starredVaultMessages,
                            exportedVaultBackupText = exportedVaultBackupText,
                            onToggleScreenShield = viewModel::toggleScreenShield,
                            onToggleOfflineQueueMode = viewModel::toggleOfflineQueueMode,
                            onToggleBiometricUnlock = viewModel::toggleBiometricUnlock,
                            onToggleAutoLockOnBackground = viewModel::toggleAutoLockOnBackground,
                            onBiometricUnlockNow = {
                                authenticateWithRealBiometrics(
                                    context = context,
                                    onSuccess = { viewModel.unlockVaultWithBiometrics() },
                                    onError = { errMsg -> viewModel.postBannerMessage(errMsg) }
                                )
                            },
                            onFlushOfflineQueue = viewModel::flushOfflineOutboxQueue,
                            onExportEncryptedBackup = viewModel::exportEncryptedBackup,
                            onImportEncryptedBackup = viewModel::importEncryptedBackup,
                            onUpdateProfile = viewModel::updateMyProfile,
                            onUpdateVaultPins = viewModel::updateVaultPins,
                            onVerifyPin = viewModel::verifyPinInput,
                            onSignOut = {
                                signOutZeroChat(
                                    credentialManager = credentialManager,
                                    scope = scope,
                                    context = context,
                                    onSignOutComplete = onSignedOut
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}
