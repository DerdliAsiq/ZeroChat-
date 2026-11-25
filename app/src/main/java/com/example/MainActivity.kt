package com.example

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.ScheduledMessageWorker
import com.example.data.ZeroChatServices
import com.example.ui.UiState
import com.example.ui.ZeroChatViewModel
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.getPreferredHandle
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
import com.example.util.ZeroChatNotificationHelper
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

/**
 * Keeps one ViewModelStore per signed-in account so that switching accounts clears the previous
 * account's ViewModel (and its Firestore listeners) while surviving configuration changes.
 */
class AccountScopeHolder : ViewModel() {
    private val stores = mutableMapOf<String, ViewModelStore>()

    fun ownerFor(uid: String): ViewModelStoreOwner {
        stores.keys.filter { it != uid }.forEach { stores.remove(it)?.clear() }
        val store = stores.getOrPut(uid) { ViewModelStore() }
        return object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore = store
        }
    }

    fun clearAll() {
        stores.values.forEach { it.clear() }
        stores.clear()
    }

    override fun onCleared() = clearAll()
}

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ZeroChatNotificationHelper.ensureChannels(this)
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
    val authFlow = remember(auth) { auth.authStateFlow() }
    val user by authFlow.collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val currentUser = user

    val accountScopes: AccountScopeHolder = viewModel()
    if (currentUser == null) {
        LaunchedEffect(Unit) { accountScopes.clearAll() }
        AuthScreen(onAuthSuccess = {})
    } else {
        key(currentUser.uid) {
            ZeroChatAuthenticatedShell(
                currentUserId = currentUser.uid,
                displayNameHint = currentUser.displayName
            )
        }
    }
}

@Composable
fun ZeroChatAuthenticatedShell(
    currentUserId: String,
    displayNameHint: String?
) {
    val accountScopes: AccountScopeHolder = viewModel()
    val viewModelStoreOwner = remember(currentUserId) { accountScopes.ownerFor(currentUserId) }
    val app = LocalContext.current.applicationContext
    val viewModel: ZeroChatViewModel = viewModel(
        viewModelStoreOwner = viewModelStoreOwner,
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                ZeroChatViewModel(
                    repository = ZeroChatServices.repository(app),
                    vaultDao = ZeroChatServices.vaultDao(app),
                    currentUserId = currentUserId,
                    preferredHandle = getPreferredHandle(app),
                    displayNameHint = displayNameHint,
                    onScheduledMessagesChanged = { next ->
                        if (next == null) ScheduledMessageWorker.cancel(app) else ScheduledMessageWorker.schedule(app, next)
                    }
                )
            }
        }
    )

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        ZeroChatNotificationHelper.ensureChannels(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPerm = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPerm) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

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
    val cachedMessageCount by viewModel.cachedMessageCount.collectAsStateWithLifecycle()
    val starredVaultMessages by viewModel.starredVaultMessages.collectAsStateWithLifecycle()
    val exportedVaultBackupText by viewModel.exportedVaultBackupText.collectAsStateWithLifecycle()
    val bannerMessage by viewModel.statusBannerMessage.collectAsStateWithLifecycle()

    // Real Android notifications for incoming messages, requests and calls (one per event).
    LaunchedEffect(viewModel) {
        viewModel.notificationEvents.collect { evt ->
            if (evt.isCall) {
                ZeroChatNotificationHelper.showIncomingCallNotification(
                    context = context,
                    notificationId = evt.id,
                    callerUsername = evt.callerUsername,
                    isVideo = evt.isVideoCall
                )
            } else {
                ZeroChatNotificationHelper.showMessageNotification(
                    context = context,
                    notificationId = evt.id,
                    senderTitle = evt.title,
                    messagePreview = evt.body
                )
            }
        }
    }

    // Remove the incoming-call notification once the call was answered, declined or timed out.
    var lastRingingCallId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(incomingRingingCall?.id) {
        val previous = lastRingingCallId
        if (previous != null && previous != incomingRingingCall?.id) {
            ZeroChatNotificationHelper.cancel(context, previous.hashCode())
        }
        lastRingingCallId = incomingRingingCall?.id
    }

    // Automatic Vault Lock when app moves to background (ON_STOP)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            // Rotation also stops the activity; only lock when the app really leaves the screen.
            val changingConfig = (context as? Activity)?.isChangingConfigurations == true
            if (event == Lifecycle.Event.ON_STOP && !changingConfig) {
                viewModel.onAppBackgrounded()
            } else if (event == Lifecycle.Event.ON_START) {
                viewModel.onAppForegrounded()
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
    val currentCall = activeCall
    if (currentCall != null) {
        ActiveP2PCallOverlay(
            callSignal = currentCall,
            currentUserId = currentUserId,
            myUsername = myProfile?.username,
            onPushMediaChunk = viewModel::pushActiveCallMediaChunk,
            onEndCall = { viewModel.endActiveCall() }
        )
        return
    }

    // Full-screen Incoming P2P Call Ringing Overlay
    val ringingCall = incomingRingingCall
    if (ringingCall != null) {
        IncomingCallRingingOverlay(
            callSignal = ringingCall,
            onAcceptCall = { viewModel.acceptIncomingCall(ringingCall) },
            onDeclineCall = { viewModel.declineIncomingCall(ringingCall) }
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
                val peerHandleInRoom = activeRoom.peerHandleFor(myProfile?.username).lowercase()

                ChatConversationScreen(
                    room = activeRoom,
                    currentUserId = currentUserId,
                    myUsername = myProfile?.username,
                    messages = activeRoomMessages,
                    allRooms = allRoomsList,
                    directoryPeers = directoryPeersList,
                    isRoomPeerVerified = verifiedPeerHandles.contains(peerHandleInRoom),
                    hasRoomKeyChangedWarning = keyChangedPeerHandles.contains(peerHandleInRoom),
                    safetyInfo = remember(activeRoom.id, activeRoom.participantUids, myProfile, directoryState) {
                        viewModel.safetyInfoFor(activeRoom)
                    },
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
                    onScheduleMessage = viewModel::scheduleEncryptedMessage,
                    onCancelScheduledMessage = viewModel::cancelScheduledMessage,
                    onNukeRoomHistory = viewModel::nukeActiveRoomHistory,
                    onNotifySecureClipboard = viewModel::notifySecureClipboardCopied,
                    onShowMessage = viewModel::postBannerMessage,
                    onViewOnceConsumed = viewModel::markViewOnceConsumed,
                    peerOnline = activeRoom.peerUidFor(currentUserId)
                        ?.let { uid -> directoryPeersList.firstOrNull { it.userId == uid } }
                        ?.let { it.onlineStatus == "ONLINE" },
                    onApproveConnectionRequest = { viewModel.approveConnectionRequest(activeRoom) },
                    onRejectOrCancelConnectionRequest = { viewModel.rejectOrCancelConnectionRequest(activeRoom) },
                    onBack = { viewModel.closeRoom() },
                    onSendText = { text, ttl, isViewOnce, isSpoiler ->
                        viewModel.sendTextMessage(text, ttl, isViewOnce, isSpoiler)
                    },
                    onSendVoiceNote = { dur, wave, b64, ttl ->
                        viewModel.sendVoiceNoteMessage(dur, wave, b64, ttl)
                    },
                    onSendImageAttachment = { uri, cap, ttl, isViewOnce ->
                        viewModel.sendImageAttachmentMessage(uri, cap, ttl, isViewOnce)
                    },
                    onSendFileAttachment = { name, sizeKb, mime, fileB64, ttl ->
                        viewModel.sendFileAttachmentMessage(name, sizeKb, mime, fileB64, ttl)
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
                        val peerHandle = activeRoom.peerHandleFor(myProfile?.username)
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
                            cachedMessagesCount = cachedMessageCount,
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
                                viewModel.prepareSignOut {
                                    signOutZeroChat(
                                        credentialManager = credentialManager,
                                        scope = scope,
                                        context = context
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
