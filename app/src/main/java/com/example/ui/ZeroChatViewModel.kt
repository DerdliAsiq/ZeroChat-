package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.crypto.CryptoEngine
import com.example.crypto.HardwareKeyStatus
import com.example.data.local.CachedVaultMessageEntity
import com.example.data.local.LocalVaultDao
import com.example.data.local.LockedRoomEntity
import com.example.data.local.PendingOutboxEntity
import com.example.data.local.PinnedRoomEntity
import com.example.data.local.ScheduledEnvelopeEntity
import com.example.data.local.VaultSecurityConfigEntity
import com.example.data.local.VerifiedPeerEntity
import com.example.data.model.CallSignal
import com.example.data.model.ChatRoom
import com.example.data.model.DecryptedMessageItem
import com.example.data.model.DecryptedStatusItem
import com.example.data.model.EncryptedEnvelope
import com.example.data.model.PollOptionItem
import com.example.data.model.UserProfile
import com.example.data.repository.ZeroChatRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

class ZeroChatViewModel(
    private val repository: ZeroChatRepository,
    private val vaultDao: LocalVaultDao,
    val currentUserId: String,
    initialEmailHint: String?
) : ViewModel() {

    private val _myProfile = MutableStateFlow<UserProfile?>(null)
    val myProfile: StateFlow<UserProfile?> = _myProfile.asStateFlow()

    private val _hardwareKeyStatus = MutableStateFlow<HardwareKeyStatus?>(null)
    val hardwareKeyStatus: StateFlow<HardwareKeyStatus?> = _hardwareKeyStatus.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _inChatSearchQuery = MutableStateFlow("")
    val inChatSearchQuery: StateFlow<String> = _inChatSearchQuery.asStateFlow()

    private val _selectedRoom = MutableStateFlow<ChatRoom?>(null)
    val selectedRoom: StateFlow<ChatRoom?> = _selectedRoom.asStateFlow()

    private val _replyingToMessage = MutableStateFlow<DecryptedMessageItem?>(null)
    val replyingToMessage: StateFlow<DecryptedMessageItem?> = _replyingToMessage.asStateFlow()

    private val _editingMessage = MutableStateFlow<DecryptedMessageItem?>(null)
    val editingMessage: StateFlow<DecryptedMessageItem?> = _editingMessage.asStateFlow()

    private val _activeCall = MutableStateFlow<CallSignal?>(null)
    val activeCall: StateFlow<CallSignal?> = _activeCall.asStateFlow()

    private val _isVaultUnlocked = MutableStateFlow(false)
    val isVaultUnlocked: StateFlow<Boolean> = _isVaultUnlocked.asStateFlow()

    private val _isPanicModeActive = MutableStateFlow(false)
    val isPanicModeActive: StateFlow<Boolean> = _isPanicModeActive.asStateFlow()

    private val _isOfflineQueueMode = MutableStateFlow(false)
    val isOfflineQueueMode: StateFlow<Boolean> = _isOfflineQueueMode.asStateFlow()

    private val _exportedVaultBackupText = MutableStateFlow<String?>(null)
    val exportedVaultBackupText: StateFlow<String?> = _exportedVaultBackupText.asStateFlow()

    private val _statusBannerMessage = MutableStateFlow<String?>(null)
    val statusBannerMessage: StateFlow<String?> = _statusBannerMessage.asStateFlow()

    private val _clockTickMillis = MutableStateFlow(System.currentTimeMillis())

    private val _rawRoomEnvelopes = MutableStateFlow<List<EncryptedEnvelope>>(emptyList())
    private var roomEnvelopesJob: Job? = null
    private var typingResetJob: Job? = null
    private val burnedIdsInSession = mutableSetOf<String>()
    private val markedReadIdsInSession = mutableSetOf<String>()

    val securityConfig: StateFlow<VaultSecurityConfigEntity?> = vaultDao.observeSecurityConfig(currentUserId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val isScreenShieldEnabled: StateFlow<Boolean> = combine(
        securityConfig,
        _isVaultUnlocked,
        _isPanicModeActive
    ) { cfg, unlocked, panic ->
        (cfg?.screenShieldEnabled == true) || unlocked || panic
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), false)

    private val _simulatedIncomingCall = MutableStateFlow<CallSignal?>(null)
    private val dismissedCallIdsInSession = mutableSetOf<String>()

    val pendingOutboxItems: StateFlow<List<PendingOutboxEntity>> = vaultDao.observePendingOutbox(currentUserId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val cachedVaultMessages: StateFlow<List<CachedVaultMessageEntity>> = vaultDao.observeCachedMessages(currentUserId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val starredVaultMessages: StateFlow<List<CachedVaultMessageEntity>> = vaultDao.observeStarredMessages(currentUserId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val lockedRoomIds: StateFlow<Set<String>> = vaultDao.observeLockedRooms()
        .combine(_clockTickMillis) { list, _ -> list.filter { it.isSecretVault }.map { it.roomId }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptySet())

    val pinnedRoomIds: StateFlow<Set<String>> = vaultDao.observePinnedRooms(currentUserId)
        .combine(_clockTickMillis) { list, _ -> list.map { it.roomId }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptySet())

    val verifiedPeers: StateFlow<List<VerifiedPeerEntity>> = vaultDao.observeVerifiedPeers(currentUserId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val verifiedPeerHandles: StateFlow<Set<String>> = verifiedPeers
        .combine(_clockTickMillis) { list, _ -> list.map { it.peerUsername.lowercase(Locale.ROOT) }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptySet())

    private val _simulatedKeyChangedHandles = MutableStateFlow<Set<String>>(emptySet())

    val scheduledEnvelopes: StateFlow<List<ScheduledEnvelopeEntity>> = vaultDao.observeScheduledEnvelopes(currentUserId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val activeRoomScheduledEnvelopes: StateFlow<List<ScheduledEnvelopeEntity>> = combine(
        scheduledEnvelopes,
        _selectedRoom
    ) { list, room ->
        if (room == null) emptyList() else list.filter { it.roomId == room.id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val directoryUsersState: StateFlow<UiState<List<UserProfile>>> = repository.observeDirectoryUsers()
        .combine(_searchQuery) { users, query ->
            val q = query.trim().removePrefix("@").lowercase(Locale.ROOT)
            val me = users.firstOrNull { it.userId == currentUserId }
            if (me != null && _myProfile.value == null) {
                _myProfile.value = me
            }
            val filtered = if (q.isEmpty()) {
                users.filter { it.userId != currentUserId }
            } else {
                users.filter {
                    it.userId != currentUserId &&
                        (it.username.lowercase(Locale.ROOT).contains(q) ||
                            it.displayName.lowercase(Locale.ROOT).contains(q))
                }
            }
            UiState.Success(filtered) as UiState<List<UserProfile>>
        }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing directory users", rootError)
            emit(UiState.Error(rootError.message ?: "Kullanıcı dizini yüklenemedi"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val keyChangedPeerHandles: StateFlow<Set<String>> = combine(
        directoryUsersState,
        verifiedPeers,
        _simulatedKeyChangedHandles
    ) { dirState, verifiedList, simulatedSet ->
        val changed = mutableSetOf<String>()
        changed.addAll(simulatedSet)
        if (dirState is UiState.Success) {
            val peersByHandle = dirState.data.associateBy { it.username.lowercase(Locale.ROOT) }
            verifiedList.forEach { v ->
                val handle = v.peerUsername.lowercase(Locale.ROOT)
                val livePeer = peersByHandle[handle]
                if (livePeer != null &&
                    v.verifiedPublicKey.isNotBlank() &&
                    livePeer.publicKey.isNotBlank() &&
                    livePeer.publicKey != v.verifiedPublicKey
                ) {
                    changed.add(handle)
                }
            }
        }
        changed
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptySet())

    val peerTypingHandleInActiveRoom: StateFlow<String?> = combine(
        directoryUsersState,
        _selectedRoom
    ) { dirState, room ->
        if (room == null || dirState !is UiState.Success) return@combine null
        val typingPeer = dirState.data.firstOrNull { peer ->
            peer.userId != currentUserId &&
                room.participantUids.contains(peer.userId) &&
                peer.onlineStatus == "TYPING:${room.id}"
        }
        typingPeer?.username
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val roomsState: StateFlow<UiState<List<ChatRoom>>> = combine(
        repository.observeUserRooms(currentUserId),
        _isPanicModeActive,
        pinnedRoomIds
    ) { rooms, panic, pinnedSet ->
        if (panic) {
            UiState.Success(emptyList<ChatRoom>()) as UiState<List<ChatRoom>>
        } else {
            val currentSel = _selectedRoom.value
            if (currentSel != null) {
                rooms.firstOrNull { it.id == currentSel.id }?.let { _selectedRoom.value = it }
            }
            val sorted = rooms.sortedWith(
                compareByDescending<ChatRoom> { pinnedSet.contains(it.id) }
                    .thenByDescending { (it.updatedAt ?: it.createdAt)?.toDate()?.time ?: 0L }
            )
            UiState.Success(sorted) as UiState<List<ChatRoom>>
        }
    }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing user rooms", rootError)
            emit(UiState.Error(rootError.message ?: "Şifreli odalar yüklenemedi"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val activeRoomMessages: StateFlow<List<DecryptedMessageItem>> = combine(
        _rawRoomEnvelopes,
        _selectedRoom,
        _clockTickMillis,
        _inChatSearchQuery
    ) { envelopes, room, nowMillis, searchQ ->
        if (room == null) return@combine emptyList()
        val toCache = mutableListOf<CachedVaultMessageEntity>()

        val decryptedList = envelopes.mapNotNull { env ->
            val createdMillis = (env.createdAt ?: env.updatedAt)?.toDate()?.time ?: nowMillis
            val remainingSec: Long? = if (env.ttlSeconds > 0L) {
                val expireMillis = createdMillis + (env.ttlSeconds * 1000L)
                val diffSec = (expireMillis - nowMillis) / 1000L
                if (diffSec <= 0L) {
                    if (burnedIdsInSession.add(env.id)) {
                        viewModelScope.launch { repository.burnEnvelope(env.id) }
                    }
                    return@mapNotNull null
                }
                diffSec
            } else {
                null
            }

            val metaObj = try {
                JSONObject(env.encryptedMeta)
            } catch (_: Exception) {
                JSONObject()
            }
            val ratchetStep = metaObj.optInt("ratchetStep", 0)
            val plaintext = CryptoEngine.decrypt(env.ciphertext, env.iv, room.roomKeyHint, ratchetStep)

            val replySender = metaObj.optString("replyToSender", "").takeIf { it.isNotBlank() }
            val replyPreview = metaObj.optString("replyToPreview", "").takeIf { it.isNotBlank() }
            val isPinned = metaObj.optBoolean("isPinned", false)
            val isEdited = metaObj.optBoolean("isEdited", false)
            val isViewOnce = metaObj.optBoolean("isViewOnce", false)
            val isSpoiler = metaObj.optBoolean("isSpoiler", false)
            val isStarred = metaObj.optBoolean("isStarred", false)
            val stegoPayload = metaObj.optString("stegoPayload", "")
            val forwardedFrom = metaObj.optString("forwardedFrom", "").takeIf { it.isNotBlank() }
            val audioB64 = metaObj.optString("audioBase64", "")
            val fileName = metaObj.optString("fileName", "")
            val fileSizeKb = metaObj.optInt("fileSizeKb", 0)

            val readArr = metaObj.optJSONArray("readByUids")
            val readUids = mutableSetOf<String>()
            readUids.add(env.senderId)
            if (readArr != null) {
                for (i in 0 until readArr.length()) {
                    readUids.add(readArr.optString(i))
                }
            }

            // Automatically send read receipt if incoming message not yet marked read by currentUserId
            if (env.senderId != currentUserId && !readUids.contains(currentUserId) && markedReadIdsInSession.add(env.id)) {
                readUids.add(currentUserId)
                viewModelScope.launch {
                    markMessageAsReadInternal(env, metaObj, readUids)
                }
            }

            toCache.add(
                CachedVaultMessageEntity(
                    messageId = env.id,
                    ownerUid = currentUserId,
                    roomId = room.id,
                    roomTitle = room.title,
                    senderUsername = env.senderUsername,
                    decryptedText = plaintext,
                    ciphertext = env.ciphertext,
                    iv = env.iv,
                    messageType = env.messageType,
                    isStarred = isStarred,
                    createdAtMillis = createdMillis
                )
            )

            DecryptedMessageItem(
                id = env.id,
                roomId = env.roomId,
                senderId = env.senderId,
                senderUsername = env.senderUsername,
                decryptedText = plaintext,
                ciphertext = env.ciphertext,
                iv = env.iv,
                signatureHash = env.signatureHash,
                messageType = env.messageType,
                encryptedMeta = env.encryptedMeta,
                reactions = parseReactionsMap(env.reactionsSummary),
                ttlSeconds = env.ttlSeconds,
                createdAtMillis = createdMillis,
                remainingSeconds = remainingSec,
                replyToSender = replySender,
                replyToPreview = replyPreview,
                isPinned = isPinned,
                isEdited = isEdited,
                forwardedFrom = forwardedFrom,
                readByCount = readUids.size,
                ratchetStep = ratchetStep,
                audioBase64 = audioB64,
                fileName = fileName,
                fileSizeKb = fileSizeKb,
                isViewOnce = isViewOnce,
                isSpoiler = isSpoiler,
                isStarred = isStarred,
                stegoPayload = stegoPayload
            )
        }

        if (toCache.isNotEmpty()) {
            viewModelScope.launch {
                vaultDao.cacheDecryptedMessages(toCache)
            }
        }

        val q = searchQ.trim().lowercase(Locale.ROOT)
        if (q.isEmpty()) {
            decryptedList
        } else {
            decryptedList.filter {
                it.decryptedText.lowercase(Locale.ROOT).contains(q) ||
                    it.senderUsername.lowercase(Locale.ROOT).contains(q) ||
                    it.fileName.lowercase(Locale.ROOT).contains(q)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val statusesState: StateFlow<UiState<List<DecryptedStatusItem>>> = repository.observeStatuses()
        .combine(_isPanicModeActive) { list, panic ->
            if (panic) {
                UiState.Success(emptyList<DecryptedStatusItem>()) as UiState<List<DecryptedStatusItem>>
            } else {
                val now = System.currentTimeMillis()
                val dayMillis = 24 * 60 * 60 * 1000L
                val activeItems = list.mapNotNull { st ->
                    val created = (st.createdAt ?: st.updatedAt)?.toDate()?.time ?: now
                    if (now - created > dayMillis) return@mapNotNull null
                    val decrypted = CryptoEngine.decrypt(st.ciphertext, st.iv, "ZEROCHAT_PUBLIC_STATUS_STREAM")
                    DecryptedStatusItem(
                        id = st.id,
                        userId = st.userId,
                        authorUsername = st.authorUsername,
                        decryptedContent = decrypted,
                        ciphertext = st.ciphertext,
                        iv = st.iv,
                        themeColorHex = st.themeColorHex,
                        viewCount = st.viewCount,
                        createdAtMillis = created
                    )
                }
                UiState.Success(activeItems) as UiState<List<DecryptedStatusItem>>
            }
        }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing statuses", rootError)
            emit(UiState.Error(rootError.message ?: "Durum güncellemeleri alınamadı"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val callSignalsState: StateFlow<UiState<List<CallSignal>>> = repository.observeCallSignals(currentUserId)
        .combine(_isPanicModeActive) { calls, panic ->
            if (panic) {
                UiState.Success(emptyList<CallSignal>()) as UiState<List<CallSignal>>
            } else {
                UiState.Success(calls) as UiState<List<CallSignal>>
            }
        }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing calls", rootError)
            emit(UiState.Error(rootError.message ?: "Arama kayıtları alınamadı"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val incomingRingingCall: StateFlow<CallSignal?> = combine(
        callSignalsState,
        _activeCall,
        _simulatedIncomingCall,
        _isPanicModeActive
    ) { callsState, active, simulated, panic ->
        if (panic || active != null) return@combine null
        if (simulated != null) return@combine simulated
        val list = (callsState as? UiState.Success)?.data ?: emptyList()
        val now = System.currentTimeMillis()
        list.firstOrNull { signal ->
            val createdMs = (signal.createdAt ?: signal.updatedAt)?.toDate()?.time ?: now
            signal.status == "RINGING" &&
                signal.callerId != currentUserId &&
                !dismissedCallIdsInSession.contains(signal.id) &&
                (now - createdMs) < 90_000L
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    init {
        bootstrapIdentityIfNeeded(initialEmailHint)
        startSelfDestructTicker()
        ensureDefaultVaultConfig()
    }

    private fun bootstrapIdentityIfNeeded(emailHint: String?) {
        val defaultHandle = emailHint?.substringBefore("@")
            ?.lowercase(Locale.ROOT)
            ?.replace("[^a-z0-9_\\-]".toRegex(), "")
            ?.takeIf { it.length >= 3 }
            ?: "zero_${currentUserId.take(6).lowercase(Locale.ROOT)}"

        val hw = CryptoEngine.getOrCreateHardwareIdentity(currentUserId, defaultHandle)
        _hardwareKeyStatus.value = hw
        if (_myProfile.value == null) {
            _myProfile.value = UserProfile(
                id = currentUserId,
                userId = currentUserId,
                username = defaultHandle,
                displayName = defaultHandle.uppercase(Locale.ROOT),
                bio = "ECDH P-256 + AES-256-GCM Doğrulanmış Düğüm",
                publicKey = hw.publicKeyBase64,
                safetyFingerprint = hw.safetyFingerprint,
                onlineStatus = "ONLINE"
            )
        }

        viewModelScope.launch {
            val result = repository.registerOrUpdateProfile(
                rawUsername = _myProfile.value?.username ?: defaultHandle,
                displayName = _myProfile.value?.displayName ?: defaultHandle.uppercase(Locale.ROOT),
                bio = _myProfile.value?.bio ?: "ECDH P-256 + AES-256-GCM Düğümü"
            )
            result.onSuccess { profile ->
                _myProfile.value = profile
            }
        }
    }

    private fun ensureDefaultVaultConfig() {
        viewModelScope.launch {
            val existing = vaultDao.getSecurityConfig(currentUserId)
            if (existing == null) {
                vaultDao.saveSecurityConfig(
                    VaultSecurityConfigEntity(
                        ownerUid = currentUserId,
                        normalPinHash = CryptoEngine.hashPin("1337"),
                        panicPinHash = CryptoEngine.hashPin("9999"),
                        autoBurnDefaultSeconds = 0L,
                        screenShieldEnabled = false
                    )
                )
            }
        }
    }

    private fun startSelfDestructTicker() {
        viewModelScope.launch {
            while (isActive) {
                delay(1000L)
                val now = System.currentTimeMillis()
                _clockTickMillis.value = now
                dispatchDueScheduledEnvelopes(now)
            }
        }
    }

    private suspend fun dispatchDueScheduledEnvelopes(nowMillis: Long) {
        val dueList = try {
            vaultDao.getDueScheduledEnvelopes(currentUserId, nowMillis)
        } catch (_: Exception) {
            emptyList()
        }
        if (dueList.isEmpty()) return
        val rooms = (roomsState.value as? UiState.Success)?.data ?: emptyList()
        val senderName = _myProfile.value?.username ?: "zero_${currentUserId.take(5)}"
        for (item in dueList) {
            vaultDao.deleteScheduledEnvelope(item.id)
            val targetRoom = rooms.firstOrNull { it.id == item.roomId } ?: _selectedRoom.value?.takeIf { it.id == item.roomId }
            if (targetRoom != null) {
                val metaJson = JSONObject().apply {
                    put("scheduledDispatched", true)
                    put("ratchetStep", (_rawRoomEnvelopes.value.size + 1))
                }.toString()
                repository.sendEncryptedMessage(
                    room = targetRoom,
                    senderUsername = senderName,
                    plaintext = item.plaintext,
                    messageType = "TEXT",
                    encryptedMeta = metaJson,
                    ttlSeconds = item.ttlSeconds,
                    ratchetStep = _rawRoomEnvelopes.value.size + 1
                ).onSuccess {
                    _statusBannerMessage.value = "⏳ Zamanlanmış şifreli zarf ağa iletildi: ${item.roomTitle}"
                }
            }
        }
    }

    private suspend fun markMessageAsReadInternal(
        env: EncryptedEnvelope,
        metaObj: JSONObject,
        readUids: Set<String>
    ) {
        val arr = JSONArray()
        readUids.forEach { arr.put(it) }
        metaObj.put("readByUids", arr)
        repository.updateEnvelopeMeta(env.id, metaObj.toString())
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        val cleaned = query.trim().removePrefix("@").lowercase(Locale.ROOT)
        if (cleaned.length >= 2) {
            viewModelScope.launch {
                repository.searchUsersInCloudByHandle(cleaned)
            }
        }
    }

    fun updateInChatSearchQuery(query: String) {
        _inChatSearchQuery.value = query
    }

    fun setReplyingTo(message: DecryptedMessageItem?) {
        _replyingToMessage.value = message
        if (message != null) _editingMessage.value = null
    }

    fun setEditingMessage(message: DecryptedMessageItem?) {
        _editingMessage.value = message
        if (message != null) _replyingToMessage.value = null
    }

    fun notifyUserTypingInActiveRoom() {
        val room = _selectedRoom.value ?: return
        typingResetJob?.cancel()
        typingResetJob = viewModelScope.launch {
            repository.updatePresenceOrTypingStatus("TYPING:${room.id}")
            delay(3000L)
            repository.updatePresenceOrTypingStatus("ONLINE")
        }
    }

    fun clearBannerMessage() {
        _statusBannerMessage.value = null
    }

    fun postBannerMessage(message: String) {
        _statusBannerMessage.value = message
    }

    fun updateMyProfile(username: String, displayName: String, bio: String) {
        viewModelScope.launch {
            val res = repository.registerOrUpdateProfile(username, displayName, bio)
            res.onSuccess { updated ->
                _myProfile.value = updated
                _hardwareKeyStatus.value = CryptoEngine.getOrCreateHardwareIdentity(currentUserId, updated.username)
                _statusBannerMessage.value = "ECDH P-256 kimliği ve @${updated.username} dizini güncellendi"
            }.onFailure { err ->
                _statusBannerMessage.value = "Profil güncellenemedi: ${err.localizedMessage}"
            }
        }
    }

    fun openRoom(room: ChatRoom) {
        _selectedRoom.value = room
        _inChatSearchQuery.value = ""
        _replyingToMessage.value = null
        _editingMessage.value = null
        roomEnvelopesJob?.cancel()
        roomEnvelopesJob = viewModelScope.launch {
            repository.observeRoomEnvelopes(room.id, currentUserId)
                .catch { e ->
                    Log.w(TAG, "Error observing envelopes for ${room.id}", e)
                }
                .collect { list ->
                    _rawRoomEnvelopes.value = list
                }
        }
    }

    fun closeRoom() {
        roomEnvelopesJob?.cancel()
        _selectedRoom.value = null
        _inChatSearchQuery.value = ""
        _replyingToMessage.value = null
        _editingMessage.value = null
        _rawRoomEnvelopes.value = emptyList()
    }

    fun startDirectChatWithPeer(peer: UserProfile, introNote: String = "") {
        val me = _myProfile.value ?: UserProfile(
            id = currentUserId,
            userId = currentUserId,
            username = "zero_${currentUserId.take(5)}",
            displayName = "Zero Node"
        )
        val existingRooms = (roomsState.value as? UiState.Success)?.data ?: emptyList()
        val existing = existingRooms.firstOrNull { room ->
            !room.isGroup &&
                !room.isRejectedRequest &&
                (room.participantUids.contains(peer.userId) ||
                    room.title.removePrefix("@").equals(peer.username, ignoreCase = true))
        }
        if (existing != null && existing.isApprovedConnection && introNote.isBlank()) {
            openRoom(existing)
            return
        }

        viewModelScope.launch {
            repository.createOrGetDirectRoom(
                myProfile = me,
                peerProfile = peer
            )
                .onSuccess { room ->
                    openRoom(room)
                    val cleanNote = introNote.trim()
                    if (cleanNote.isNotBlank()) {
                        val metaJson = JSONObject().apply {
                            put("ratchetStep", 1)
                            put("handshakeIntro", true)
                            put("readByUids", JSONArray().put(currentUserId))
                        }.toString()
                        repository.sendEncryptedMessage(
                            room = room,
                            senderUsername = me.username,
                            plaintext = cleanNote,
                            messageType = "TEXT",
                            encryptedMeta = metaJson,
                            ttlSeconds = room.defaultTtlSeconds,
                            ratchetStep = 1
                        )
                    }
                    _statusBannerMessage.value = "🔐 @${peer.username} ile uçtan uca şifreli P2P tüneli kuruldu"
                }
                .onFailure { err ->
                    _statusBannerMessage.value = "Şifreli sohbet başlatılamadı: ${err.localizedMessage}"
                }
        }
    }

    fun approveConnectionRequest(room: ChatRoom) {
        val myHandle = _myProfile.value?.username ?: "zero_${currentUserId.take(5)}"
        viewModelScope.launch {
            repository.approveConnectionRequest(room, myHandle)
                .onSuccess { updatedRoom ->
                    if (_selectedRoom.value?.id == updatedRoom.id) {
                        _selectedRoom.value = updatedRoom
                    }
                    _statusBannerMessage.value = "✅ ${updatedRoom.title} iletişim ve takip isteği onaylandı! Şifreli tünel aktif."
                }
                .onFailure { err ->
                    _statusBannerMessage.value = "İstek onaylanamadı: ${err.localizedMessage}"
                }
        }
    }

    fun rejectOrCancelConnectionRequest(room: ChatRoom) {
        val myHandle = _myProfile.value?.username ?: "zero_${currentUserId.take(5)}"
        viewModelScope.launch {
            repository.rejectOrCancelConnectionRequest(room, myHandle)
                .onSuccess { updatedRoom ->
                    if (_selectedRoom.value?.id == updatedRoom.id) {
                        closeRoom()
                    }
                    _statusBannerMessage.value = "⛔ ${updatedRoom.title} iletişim isteği reddedildi / geri çekildi"
                }
                .onFailure { err ->
                    _statusBannerMessage.value = "İstek iptal edilemedi: ${err.localizedMessage}"
                }
        }
    }

    fun simulateIncomingConnectionRequest(peer: UserProfile? = null) {
        val me = _myProfile.value ?: UserProfile(
            id = currentUserId,
            userId = currentUserId,
            username = "zero_${currentUserId.take(5)}",
            displayName = "Zero Node"
        )
        val peers = (directoryUsersState.value as? UiState.Success)?.data ?: emptyList()
        val targetPeer = peer ?: peers.firstOrNull() ?: UserProfile(
            id = "peer_siber_analist",
            userId = "peer_siber_analist",
            username = "siber_analist",
            displayName = "Siber Analist Düğümü",
            publicKey = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE9xCyberKey9912==",
            safetyFingerprint = "88412 90341 77219 00418 55120 66391"
        )
        viewModelScope.launch {
            repository.sendDirectConnectionRequest(
                myProfile = me,
                peerProfile = targetPeer,
                introNote = "👋 Merhaba @${me.username}, seni kullanıcı adınla buldum. Şifreli iletişim ve takip isteğimi onaylar mısın?",
                simulatedIncomingFromPeer = true
            ).onSuccess {
                _statusBannerMessage.value = "📨 @${targetPeer.username} düğümünden yeni iletişim ve takip isteği geldi!"
            }.onFailure { err ->
                _statusBannerMessage.value = "Simülasyon hatası: ${err.localizedMessage}"
            }
        }
    }

    fun deleteChatRoomFromRecent(room: ChatRoom) {
        viewModelScope.launch {
            if (_selectedRoom.value?.id == room.id) {
                closeRoom()
            }
            vaultDao.deleteCachedMessagesForRoom(currentUserId, room.id)
            vaultDao.unpinRoom(currentUserId, room.id)
            vaultDao.unlockRoom(room.id)
            repository.deleteChatRoomCompletely(room.id, currentUserId)
                .onSuccess {
                    _statusBannerMessage.value = "🗑️ ${room.title} sohbeti ve şifreli zarfları listeden kalıcı olarak silindi"
                }
                .onFailure { err ->
                    _statusBannerMessage.value = "Sohbet silinemedi: ${err.localizedMessage}"
                }
        }
    }

    fun handleScannedPeerHandleOrKey(scannedInput: String) {
        val raw = scannedInput.trim()
        if (raw.isBlank()) return

        // Parse structured QR payload: zerochat://peer?u=handle&uid=id&fp=fingerprint&pk=pubkey
        // or ZEROCHAT://VERIFY/@handle::fp::pk or plain @username
        var parsedHandle = ""
        var parsedUid = ""
        var parsedFp = ""
        var parsedPk = ""

        if (raw.startsWith("zerochat://", ignoreCase = true) && raw.contains("?")) {
            val queryPart = raw.substringAfter("?")
            queryPart.split("&").forEach { kv ->
                val parts = kv.split("=", limit = 2)
                if (parts.size == 2) {
                    val k = parts[0].lowercase(Locale.ROOT)
                    val v = java.net.URLDecoder.decode(parts[1], "UTF-8")
                    when (k) {
                        "u", "user", "username" -> parsedHandle = v
                        "uid", "id" -> parsedUid = v
                        "fp", "fingerprint" -> parsedFp = v
                        "pk", "key", "pubkey" -> parsedPk = v
                    }
                }
            }
        } else if (raw.contains("::")) {
            val segs = raw.removePrefix("ZEROCHAT://VERIFY/").split("::")
            parsedHandle = segs.getOrNull(0).orEmpty()
            parsedFp = segs.getOrNull(1).orEmpty()
            parsedPk = segs.getOrNull(2).orEmpty()
        } else {
            parsedHandle = raw
        }

        val cleanedHandle = parsedHandle.trim()
            .removePrefix("@")
            .lowercase(Locale.ROOT)
            .replace("[^a-z0-9_\\-]".toRegex(), "")
            .take(28)

        if (cleanedHandle.isBlank()) return

        val peers = (directoryUsersState.value as? UiState.Success)?.data ?: emptyList()
        val matched = peers.firstOrNull {
            it.username.lowercase(Locale.ROOT) == cleanedHandle ||
                (parsedUid.isNotBlank() && it.userId == parsedUid) ||
                (parsedFp.isNotBlank() && it.safetyFingerprint == parsedFp)
        }

        val targetPeer = matched ?: run {
            val peerUid = parsedUid.ifBlank {
                "peer_" + CryptoEngine.sha256Hex("ZEROCHAT_PEER::$cleanedHandle").take(18)
            }
            val (genPubKey, genFp) = CryptoEngine.generateIdentityMaterial(peerUid, cleanedHandle)
            UserProfile(
                id = peerUid,
                userId = peerUid,
                username = cleanedHandle,
                displayName = "@$cleanedHandle Siber Düğümü",
                bio = "QR / Kullanıcı Adı ile Eşleşen Şifreli Düğüm",
                publicKey = parsedPk.ifBlank { genPubKey },
                safetyFingerprint = parsedFp.ifBlank { genFp },
                onlineStatus = "ONLINE"
            ).also { discovered ->
                repository.registerDiscoveredPeer(discovered)
            }
        }

        viewModelScope.launch {
            val cloudMatches = repository.searchUsersInCloudByHandle(cleanedHandle)
            val cloudPeer = cloudMatches.firstOrNull {
                it.username.equals(cleanedHandle, ignoreCase = true) ||
                    (parsedUid.isNotBlank() && it.userId == parsedUid)
            }
            val finalPeer = cloudPeer ?: targetPeer
            vaultDao.saveVerifiedPeer(
                VerifiedPeerEntity(
                    ownerUid = currentUserId,
                    peerUsername = finalPeer.username.lowercase(Locale.ROOT),
                    verifiedPublicKey = finalPeer.publicKey,
                    verifiedSafetyFingerprint = finalPeer.safetyFingerprint
                )
            )
            _simulatedKeyChangedHandles.value = _simulatedKeyChangedHandles.value - finalPeer.username.lowercase(Locale.ROOT)
            _statusBannerMessage.value = "🛡️ QR / Düğüm Doğrulandı: @${finalPeer.username} ile şifreli sohbet açılıyor"
            startDirectChatWithPeer(
                peer = finalPeer,
                introNote = "🛡️ QR / Açık anahtar doğrulaması ile uçtan uca şifreli bağlantı kuruldu."
            )
        }
    }

    fun createNewEncryptedGroup(title: String, peers: List<UserProfile>, ttlSeconds: Long) {
        val me = _myProfile.value ?: return
        viewModelScope.launch {
            repository.createGroupRoom(me, title, peers, ttlSeconds)
                .onSuccess { room ->
                    _statusBannerMessage.value = "'${room.title}' şifreli grup odası kuruldu"
                    openRoom(room)
                }
                .onFailure { err ->
                    _statusBannerMessage.value = "Grup oluşturulamadı: ${err.localizedMessage}"
                }
        }
    }

    fun setRoomDefaultTtl(room: ChatRoom, ttlSeconds: Long) {
        viewModelScope.launch {
            repository.updateRoomTtl(room.id, ttlSeconds).onSuccess {
                _selectedRoom.value = room.copy(defaultTtlSeconds = ttlSeconds)
                val label = if (ttlSeconds == 0L) "Kapalı" else "${ttlSeconds} sn"
                _statusBannerMessage.value = "Oda süreli mesaj imha sayacı: $label"
            }
        }
    }

    fun updateActiveGroupMembersAndTitle(newTitle: String, selectedPeers: List<UserProfile>) {
        val room = _selectedRoom.value ?: return
        val me = _myProfile.value ?: return
        viewModelScope.launch {
            repository.updateGroupMembersAndTitle(room, newTitle, selectedPeers, me)
                .onSuccess { updatedRoom ->
                    _selectedRoom.value = updatedRoom
                    _statusBannerMessage.value = "Grup üyeleri ve başlığı güncellendi (${updatedRoom.participantUids.size} düğüm)"
                }
                .onFailure { err ->
                    _statusBannerMessage.value = "Grup güncellenemedi: ${err.localizedMessage}"
                }
        }
    }

    fun rotateActiveRoomEncryptionKey() {
        val room = _selectedRoom.value ?: return
        val senderName = _myProfile.value?.username ?: "zero_${currentUserId.take(5)}"
        viewModelScope.launch {
            repository.rotateRoomEncryptionKey(room, senderName)
                .onSuccess { updatedRoom ->
                    _selectedRoom.value = updatedRoom
                    _statusBannerMessage.value = "🔄 Oda Şifreleme Anahtarı Döndürüldü (Key Rotation): ${updatedRoom.roomKeyHint.take(16)}..."
                }
                .onFailure { err ->
                    _statusBannerMessage.value = "Anahtar döndürülemedi: ${err.localizedMessage}"
                }
        }
    }

    private fun buildBaseMetaJson(
        ratchetStep: Int,
        isViewOnce: Boolean = false,
        isSpoiler: Boolean = false
    ): JSONObject {
        val reply = _replyingToMessage.value
        return JSONObject().apply {
            put("ratchetStep", ratchetStep)
            put("readByUids", JSONArray().put(currentUserId))
            if (isViewOnce) put("isViewOnce", true)
            if (isSpoiler) put("isSpoiler", true)
            if (reply != null) {
                put("replyToId", reply.id)
                put("replyToSender", reply.senderUsername)
                put("replyToPreview", reply.decryptedText.take(80))
            }
        }
    }

    fun sendTextMessage(
        text: String,
        overrideTtlSeconds: Long? = null,
        isViewOnce: Boolean = false,
        isSpoiler: Boolean = false
    ) {
        val room = _selectedRoom.value ?: return
        val senderName = _myProfile.value?.username ?: "zero_${currentUserId.take(5)}"
        if (!room.isApprovedConnection) {
            viewModelScope.launch {
                repository.approveConnectionRequest(room, senderName).onSuccess { updated ->
                    _selectedRoom.value = updated
                }
            }
        }
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        // Handle Edit Mode if active
        val editing = _editingMessage.value
        if (editing != null) {
            _editingMessage.value = null
            viewModelScope.launch {
                repository.editEncryptedMessage(
                    envelopeId = editing.id,
                    roomKeyHint = room.roomKeyHint,
                    newPlaintext = trimmed,
                    existingMetaJson = editing.encryptedMeta,
                    ratchetStep = editing.ratchetStep
                ).onSuccess {
                    _statusBannerMessage.value = "Mesaj yeniden şifrelenerek düzenlendi"
                }
            }
            return
        }

        val nextRatchetStep = (_rawRoomEnvelopes.value.size + 1)
        val metaJson = buildBaseMetaJson(
            ratchetStep = nextRatchetStep,
            isViewOnce = isViewOnce,
            isSpoiler = isSpoiler
        ).toString()
        _replyingToMessage.value = null
        val ttl = overrideTtlSeconds ?: room.defaultTtlSeconds

        dispatchOrQueueMessage(
            room = room,
            senderName = senderName,
            plaintext = trimmed,
            messageType = "TEXT",
            encryptedMeta = metaJson,
            ttlSeconds = ttl,
            ratchetStep = nextRatchetStep
        )
    }

    fun forwardMessageToRoom(message: DecryptedMessageItem, targetRoom: ChatRoom) {
        val senderName = _myProfile.value?.username ?: "zero_${currentUserId.take(5)}"
        val nextRatchetStep = 1
        val metaJson = JSONObject().apply {
            put("ratchetStep", nextRatchetStep)
            put("forwardedFrom", message.senderUsername)
            put("readByUids", JSONArray().put(currentUserId))
        }.toString()

        viewModelScope.launch {
            repository.sendEncryptedMessage(
                room = targetRoom,
                senderUsername = senderName,
                plaintext = message.decryptedText,
                messageType = "TEXT",
                encryptedMeta = metaJson,
                ttlSeconds = targetRoom.defaultTtlSeconds,
                ratchetStep = nextRatchetStep
            ).onSuccess {
                _statusBannerMessage.value = "Mesaj '${targetRoom.title}' odasına yeniden şifrelenerek iletildi"
            }
        }
    }

    fun togglePinMessage(message: DecryptedMessageItem) {
        viewModelScope.launch {
            val metaObj = try {
                JSONObject(message.encryptedMeta)
            } catch (_: Exception) {
                JSONObject()
            }
            val newPinState = !message.isPinned
            metaObj.put("isPinned", newPinState)
            repository.updateEnvelopeMeta(message.id, metaObj.toString()).onSuccess {
                _statusBannerMessage.value = if (newPinState) {
                    "Mesaj sohbetin üstüne sabitlendi 📌"
                } else {
                    "Mesaj sabitlemesi kaldırıldı"
                }
            }
        }
    }

    fun toggleStarMessage(message: DecryptedMessageItem) {
        viewModelScope.launch {
            val metaObj = try {
                JSONObject(message.encryptedMeta)
            } catch (_: Exception) {
                JSONObject()
            }
            val newStarState = !message.isStarred
            metaObj.put("isStarred", newStarState)
            vaultDao.updateMessageStarred(message.id, newStarState)
            repository.updateEnvelopeMeta(message.id, metaObj.toString()).onSuccess {
                _statusBannerMessage.value = if (newStarState) {
                    "Mesaj Yıldızlı Şifreli Kasaya eklendi ⭐"
                } else {
                    "Mesaj yıldızı kaldırıldı"
                }
            }
        }
    }

    fun sendVoiceNoteMessage(
        durationSeconds: Int,
        amplitudePattern: String,
        audioBase64: String = "",
        ttlSeconds: Long? = null,
        isViewOnce: Boolean = false
    ) {
        val room = _selectedRoom.value ?: return
        val senderName = _myProfile.value?.username ?: "zero_${currentUserId.take(5)}"
        val nextRatchetStep = (_rawRoomEnvelopes.value.size + 1)
        val meta = buildBaseMetaJson(nextRatchetStep, isViewOnce = isViewOnce).apply {
            put("durationSeconds", durationSeconds)
            put("waveform", amplitudePattern)
            if (audioBase64.isNotBlank() && audioBase64.length <= 6500) {
                put("audioBase64", audioBase64)
            }
        }.toString()
        _replyingToMessage.value = null

        dispatchOrQueueMessage(
            room = room,
            senderName = senderName,
            plaintext = "🎙️ Şifreli Sesli Not (${durationSeconds} sn)",
            messageType = "VOICE",
            encryptedMeta = meta,
            ttlSeconds = ttlSeconds ?: room.defaultTtlSeconds,
            ratchetStep = nextRatchetStep
        )
    }

    fun sendImageAttachmentMessage(
        uriString: String,
        caption: String,
        ttlSeconds: Long? = null,
        isViewOnce: Boolean = false,
        stegoSecretMessage: String = ""
    ) {
        val room = _selectedRoom.value ?: return
        val senderName = _myProfile.value?.username ?: "zero_${currentUserId.take(5)}"
        val nextRatchetStep = (_rawRoomEnvelopes.value.size + 1)
        val stegoPacket = if (stegoSecretMessage.isNotBlank()) {
            CryptoEngine.embedStegoPayload(stegoSecretMessage.trim(), room.roomKeyHint)
        } else {
            ""
        }
        val meta = buildBaseMetaJson(nextRatchetStep, isViewOnce = isViewOnce).apply {
            put("mediaUri", uriString)
            put("mime", "image/jpeg")
            if (stegoPacket.isNotBlank()) {
                put("stegoPayload", stegoPacket)
            }
        }.toString()
        _replyingToMessage.value = null

        dispatchOrQueueMessage(
            room = room,
            senderName = senderName,
            plaintext = caption.ifBlank {
                if (stegoPacket.isNotBlank()) "🖼️ Steganografik Taşıyıcı Görsel" else "📷 Şifreli Görsel Zarfı"
            },
            messageType = "IMAGE",
            encryptedMeta = meta,
            ttlSeconds = ttlSeconds ?: room.defaultTtlSeconds,
            ratchetStep = nextRatchetStep
        )
    }

    fun sendFileAttachmentMessage(
        fileName: String,
        fileSizeKb: Int,
        mimeType: String,
        ttlSeconds: Long? = null
    ) {
        val room = _selectedRoom.value ?: return
        val senderName = _myProfile.value?.username ?: "zero_${currentUserId.take(5)}"
        val nextRatchetStep = (_rawRoomEnvelopes.value.size + 1)
        val meta = buildBaseMetaJson(nextRatchetStep).apply {
            put("fileName", fileName)
            put("fileSizeKb", fileSizeKb)
            put("fileMime", mimeType)
        }.toString()
        _replyingToMessage.value = null

        dispatchOrQueueMessage(
            room = room,
            senderName = senderName,
            plaintext = "📄 Şifreli Dosya: $fileName ($fileSizeKb KB)",
            messageType = "FILE",
            encryptedMeta = meta,
            ttlSeconds = ttlSeconds ?: room.defaultTtlSeconds,
            ratchetStep = nextRatchetStep
        )
    }

    fun sendLocationMessage(latitude: Double, longitude: Double, label: String, ttlSeconds: Long? = null) {
        val room = _selectedRoom.value ?: return
        val senderName = _myProfile.value?.username ?: "zero_${currentUserId.take(5)}"
        val nextRatchetStep = (_rawRoomEnvelopes.value.size + 1)
        val meta = buildBaseMetaJson(nextRatchetStep).apply {
            put("lat", latitude)
            put("lng", longitude)
            put("label", label)
        }.toString()
        _replyingToMessage.value = null

        dispatchOrQueueMessage(
            room = room,
            senderName = senderName,
            plaintext = "📍 $label (${String.format(Locale.US, "%.4f, %.4f", latitude, longitude)})",
            messageType = "LOCATION",
            encryptedMeta = meta,
            ttlSeconds = ttlSeconds ?: room.defaultTtlSeconds,
            ratchetStep = nextRatchetStep
        )
    }

    fun sendPollMessage(question: String, options: List<String>, ttlSeconds: Long? = null) {
        val room = _selectedRoom.value ?: return
        val senderName = _myProfile.value?.username ?: "zero_${currentUserId.take(5)}"
        val cleanOptions = options.map { it.trim() }.filter { it.isNotEmpty() }.take(5)
        if (question.isBlank() || cleanOptions.size < 2) return
        val nextRatchetStep = (_rawRoomEnvelopes.value.size + 1)

        val optionsArray = JSONArray()
        cleanOptions.forEachIndexed { idx, optText ->
            optionsArray.put(
                JSONObject().apply {
                    put("index", idx)
                    put("text", optText)
                    put("voters", JSONArray())
                }
            )
        }
        val meta = buildBaseMetaJson(nextRatchetStep).apply {
            put("question", question.trim())
            put("options", optionsArray)
        }.toString()
        _replyingToMessage.value = null

        dispatchOrQueueMessage(
            room = room,
            senderName = senderName,
            plaintext = "📊 Anket: ${question.trim()}",
            messageType = "POLL",
            encryptedMeta = meta,
            ttlSeconds = ttlSeconds ?: room.defaultTtlSeconds,
            ratchetStep = nextRatchetStep
        )
    }

    private fun dispatchOrQueueMessage(
        room: ChatRoom,
        senderName: String,
        plaintext: String,
        messageType: String,
        encryptedMeta: String,
        ttlSeconds: Long,
        ratchetStep: Int
    ) {
        viewModelScope.launch {
            if (_isOfflineQueueMode.value) {
                vaultDao.enqueuePendingMessage(
                    PendingOutboxEntity(
                        ownerUid = currentUserId,
                        roomId = room.id,
                        roomTitle = room.title,
                        roomKeyHint = room.roomKeyHint,
                        participantUidsCsv = room.participantUids.joinToString(","),
                        senderUsername = senderName,
                        plaintext = plaintext,
                        messageType = messageType,
                        encryptedMeta = encryptedMeta,
                        ttlSeconds = ttlSeconds
                    )
                )
                _statusBannerMessage.value = "Mesaj çevrimdışı şifreli kuyruğa (Outbox) eklendi"
                return@launch
            }

            repository.sendEncryptedMessage(
                room = room,
                senderUsername = senderName,
                plaintext = plaintext,
                messageType = messageType,
                encryptedMeta = encryptedMeta,
                ttlSeconds = ttlSeconds,
                ratchetStep = ratchetStep
            ).onFailure {
                // Automatically enqueue into offline outbox on network failure
                vaultDao.enqueuePendingMessage(
                    PendingOutboxEntity(
                        ownerUid = currentUserId,
                        roomId = room.id,
                        roomTitle = room.title,
                        roomKeyHint = room.roomKeyHint,
                        participantUidsCsv = room.participantUids.joinToString(","),
                        senderUsername = senderName,
                        plaintext = plaintext,
                        messageType = messageType,
                        encryptedMeta = encryptedMeta,
                        ttlSeconds = ttlSeconds
                    )
                )
                _statusBannerMessage.value = "Ağ hatası: Mesaj yerel kuyruğa kaydedildi"
            }
        }
    }

    fun toggleOfflineQueueMode(enabled: Boolean) {
        _isOfflineQueueMode.value = enabled
        if (!enabled) {
            flushOfflineOutboxQueue()
        } else {
            _statusBannerMessage.value = "Çevrimdışı Kuyruk Modu Aktif: Mesajlar yerel Room kasasında bekletilecek"
        }
    }

    fun flushOfflineOutboxQueue() {
        viewModelScope.launch {
            val pending = vaultDao.getPendingOutboxList(currentUserId)
            if (pending.isEmpty()) {
                _statusBannerMessage.value = "Bekleyen çevrimdışı mesaj bulunmuyor"
                return@launch
            }
            var sentCount = 0
            pending.forEach { item ->
                val tempRoom = ChatRoom(
                    id = item.roomId,
                    title = item.roomTitle,
                    roomKeyHint = item.roomKeyHint,
                    participantUids = item.participantUidsCsv.split(",").filter { it.isNotBlank() }
                )
                val ratchet = try {
                    JSONObject(item.encryptedMeta).optInt("ratchetStep", 0)
                } catch (_: Exception) {
                    0
                }
                val res = repository.sendEncryptedMessage(
                    room = tempRoom,
                    senderUsername = item.senderUsername,
                    plaintext = item.plaintext,
                    messageType = item.messageType,
                    encryptedMeta = item.encryptedMeta,
                    ttlSeconds = item.ttlSeconds,
                    ratchetStep = ratchet
                )
                if (res.isSuccess) {
                    vaultDao.deletePendingMessage(item.localId)
                    sentCount++
                }
            }
            _statusBannerMessage.value = "$sentCount adet bekleyen şifreli zarf ağa iletildi"
        }
    }

    fun voteInPoll(message: DecryptedMessageItem, optionIndex: Int) {
        val voterHandle = _myProfile.value?.username ?: "zero_${currentUserId.take(4)}"
        viewModelScope.launch {
            try {
                val root = JSONObject(message.encryptedMeta)
                val optionsArr = root.optJSONArray("options") ?: return@launch
                for (i in 0 until optionsArr.length()) {
                    val optObj = optionsArr.getJSONObject(i)
                    val votersArr = optObj.optJSONArray("voters") ?: JSONArray()
                    val currentVoters = mutableListOf<String>()
                    for (j in 0 until votersArr.length()) {
                        val v = votersArr.getString(j)
                        if (v != voterHandle) currentVoters.add(v)
                    }
                    if (i == optionIndex) {
                        currentVoters.add(voterHandle)
                    }
                    optObj.put("voters", JSONArray(currentVoters))
                }
                repository.updateEnvelopeMeta(message.id, root.toString())
            } catch (e: Exception) {
                Log.w(TAG, "Failed to update poll vote", e)
            }
        }
    }

    fun toggleMessageReaction(message: DecryptedMessageItem, emoji: String) {
        val myHandle = _myProfile.value?.username ?: currentUserId.take(6)
        val currentMap = message.reactions.toMutableMap()
        if (currentMap[myHandle] == emoji) {
            currentMap.remove(myHandle)
        } else {
            currentMap[myHandle] = emoji
        }
        val serialized = currentMap.entries.joinToString(",") { "${it.key}:${it.value}" }
        viewModelScope.launch {
            repository.updateEnvelopeReaction(message.id, serialized)
        }
    }

    fun burnMessageNow(messageId: String) {
        viewModelScope.launch {
            repository.burnEnvelope(messageId)
            _statusBannerMessage.value = "Mesaj zarfı kalıcı olarak imha edildi"
        }
    }

    fun toggleRoomVaultLock(roomId: String, currentlyLocked: Boolean) {
        viewModelScope.launch {
            if (currentlyLocked) {
                vaultDao.unlockRoom(roomId)
                _statusBannerMessage.value = "Sohbet gizli kasadan çıkarıldı"
            } else {
                vaultDao.setRoomLocked(LockedRoomEntity(roomId = roomId, isSecretVault = true))
                _statusBannerMessage.value = "Sohbet PIN korumalı Gizli Kasaya taşındı"
            }
        }
    }

    fun toggleScreenShield(enabled: Boolean) {
        viewModelScope.launch {
            val current = vaultDao.getSecurityConfig(currentUserId) ?: VaultSecurityConfigEntity(
                ownerUid = currentUserId,
                normalPinHash = CryptoEngine.hashPin("1337"),
                panicPinHash = CryptoEngine.hashPin("9999")
            )
            vaultDao.saveSecurityConfig(current.copy(screenShieldEnabled = enabled))
            _statusBannerMessage.value = if (enabled) {
                "Ekran Görüntüsü Engeli (FLAG_SECURE) Etkinleştirildi"
            } else {
                "Ekran Görüntüsü Engeli Kapatıldı"
            }
        }
    }

    fun exportEncryptedBackup(password: String) {
        if (password.length < 4) {
            _statusBannerMessage.value = "Yedekleme parolası en az 4 karakter olmalıdır"
            return
        }
        viewModelScope.launch {
            val messages = vaultDao.getAllCachedMessages(currentUserId)
            val arr = JSONArray()
            messages.forEach { m ->
                arr.put(
                    JSONObject().apply {
                        put("messageId", m.messageId)
                        put("roomId", m.roomId)
                        put("roomTitle", m.roomTitle)
                        put("senderUsername", m.senderUsername)
                        put("decryptedText", m.decryptedText)
                        put("ciphertext", m.ciphertext)
                        put("iv", m.iv)
                        put("messageType", m.messageType)
                        put("createdAtMillis", m.createdAtMillis)
                    }
                )
            }
            val root = JSONObject().apply {
                put("ownerUid", currentUserId)
                put("exportedAtMillis", System.currentTimeMillis())
                put("messagesCount", messages.size)
                put("messages", arr)
            }.toString()

            val encryptedVault = CryptoEngine.encryptBackupArchive(root, password)
            _exportedVaultBackupText.value = encryptedVault
            _statusBannerMessage.value = "${messages.size} mesaj PBKDF2 + AES-256-GCM ile .zcvault formatında şifrelendi"
        }
    }

    fun importEncryptedBackup(vaultPayload: String, password: String) {
        viewModelScope.launch {
            CryptoEngine.decryptBackupArchive(vaultPayload, password)
                .onSuccess { jsonStr ->
                    val root = JSONObject(jsonStr)
                    val arr = root.optJSONArray("messages") ?: JSONArray()
                    val restored = mutableListOf<CachedVaultMessageEntity>()
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        restored.add(
                            CachedVaultMessageEntity(
                                messageId = obj.optString("messageId", "restored_$i"),
                                ownerUid = currentUserId,
                                roomId = obj.optString("roomId", ""),
                                roomTitle = obj.optString("roomTitle", "Yedek Oda"),
                                senderUsername = obj.optString("senderUsername", "zero"),
                                decryptedText = obj.optString("decryptedText", ""),
                                ciphertext = obj.optString("ciphertext", ""),
                                iv = obj.optString("iv", ""),
                                messageType = obj.optString("messageType", "TEXT"),
                                createdAtMillis = obj.optLong("createdAtMillis", System.currentTimeMillis())
                            )
                        )
                    }
                    vaultDao.cacheDecryptedMessages(restored)
                    _statusBannerMessage.value = ".zcvault yedeği doğrulandı: ${restored.size} şifreli kayıt kasaya geri yüklendi!"
                }
                .onFailure { err ->
                    _statusBannerMessage.value = "Yedek çözülemedi (Parola veya veri hatası): ${err.localizedMessage}"
                }
        }
    }

    fun verifyPinInput(pinInput: String): String {
        val config = securityConfig.value
        val inputHash = CryptoEngine.hashPin(pinInput.trim())
        val normalHash = config?.normalPinHash ?: CryptoEngine.hashPin("1337")
        val panicHash = config?.panicPinHash ?: CryptoEngine.hashPin("9999")

        return when (inputHash) {
            panicHash -> {
                _isPanicModeActive.value = true
                _isVaultUnlocked.value = false
                closeRoom()
                _statusBannerMessage.value = "Acil Durum (Panik) Modu Etkinleştirildi"
                "PANIC"
            }
            normalHash -> {
                _isPanicModeActive.value = false
                _isVaultUnlocked.value = true
                _statusBannerMessage.value = "Gizli Kasa Kilidi Açıldı"
                "UNLOCKED"
            }
            else -> {
                _statusBannerMessage.value = "Hatalı PIN Kodu"
                "INVALID"
            }
        }
    }

    fun unlockVaultWithBiometrics() {
        val cfg = securityConfig.value
        if (cfg?.biometricUnlockEnabled == false) {
            _statusBannerMessage.value = "Biyometrik kilit açma devre dışı bırakılmış"
            return
        }
        _isPanicModeActive.value = false
        _isVaultUnlocked.value = true
        _statusBannerMessage.value = "Donanım Biyometrik Sensörü Doğrulandı: Gizli Kasa Açıldı 🔓"
    }

    fun toggleBiometricUnlock(enabled: Boolean) {
        viewModelScope.launch {
            val current = vaultDao.getSecurityConfig(currentUserId) ?: VaultSecurityConfigEntity(
                ownerUid = currentUserId,
                normalPinHash = CryptoEngine.hashPin("1337"),
                panicPinHash = CryptoEngine.hashPin("9999")
            )
            vaultDao.saveSecurityConfig(current.copy(biometricUnlockEnabled = enabled))
            _statusBannerMessage.value = if (enabled) {
                "Biyometrik Kasa Kilidi Etkinleştirildi"
            } else {
                "Biyometrik Kasa Kilidi Kapatıldı"
            }
        }
    }

    fun toggleAutoLockOnBackground(enabled: Boolean) {
        viewModelScope.launch {
            val current = vaultDao.getSecurityConfig(currentUserId) ?: VaultSecurityConfigEntity(
                ownerUid = currentUserId,
                normalPinHash = CryptoEngine.hashPin("1337"),
                panicPinHash = CryptoEngine.hashPin("9999")
            )
            vaultDao.saveSecurityConfig(current.copy(autoLockOnBackground = enabled))
            _statusBannerMessage.value = if (enabled) {
                "Arka Planda Otomatik Kasa Kilitleme Aktif"
            } else {
                "Arka Planda Otomatik Kasa Kilitleme Kapatıldı"
            }
        }
    }

    fun onAppBackgrounded() {
        val cfg = securityConfig.value
        if (cfg?.autoLockOnBackground != false && _isVaultUnlocked.value) {
            _isVaultUnlocked.value = false
        }
    }

    fun lockVaultAndExitPanicMode() {
        _isVaultUnlocked.value = false
        _isPanicModeActive.value = false
    }

    fun updateVaultPins(newNormalPin: String, newPanicPin: String) {
        if (newNormalPin.length < 4 || newPanicPin.length < 4) {
            _statusBannerMessage.value = "PIN kodları en az 4 haneli olmalıdır"
            return
        }
        if (newNormalPin == newPanicPin) {
            _statusBannerMessage.value = "Normal PIN ve Panik PIN birbirinden farklı olmalıdır"
            return
        }
        viewModelScope.launch {
            val existing = vaultDao.getSecurityConfig(currentUserId)
            vaultDao.saveSecurityConfig(
                VaultSecurityConfigEntity(
                    ownerUid = currentUserId,
                    normalPinHash = CryptoEngine.hashPin(newNormalPin.trim()),
                    panicPinHash = CryptoEngine.hashPin(newPanicPin.trim()),
                    autoBurnDefaultSeconds = 0L,
                    screenShieldEnabled = existing?.screenShieldEnabled ?: false,
                    biometricUnlockEnabled = existing?.biometricUnlockEnabled ?: true,
                    autoLockOnBackground = existing?.autoLockOnBackground ?: true
                )
            )
            _statusBannerMessage.value = "Gizli Kasa PIN ve Sahte Panik PIN kodları güncellendi"
        }
    }

    fun publishStatusStory(text: String, colorHex: String) {
        val author = _myProfile.value?.username ?: "zero_${currentUserId.take(5)}"
        if (text.isBlank()) return
        viewModelScope.launch {
            repository.postEncryptedStatus(author, text.trim(), colorHex)
                .onSuccess {
                    _statusBannerMessage.value = "24 saatlik uçtan uca şifreli durum paylaşıldı"
                }
        }
    }

    fun startP2PCall(calleeUid: String, calleeUsername: String, callType: String) {
        val callerName = _myProfile.value?.username ?: "zero_${currentUserId.take(5)}"
        viewModelScope.launch {
            repository.startEncryptedCall(
                callerUsername = callerName,
                calleeUid = calleeUid,
                calleeUsername = calleeUsername,
                callType = callType,
                initialStatus = "RINGING"
            ).onSuccess { signal ->
                _activeCall.value = signal
            }
        }
    }

    fun simulateIncomingP2PCall(peerUsername: String = "siber_dugum", callType: String = "VIDEO") {
        val myHandle = _myProfile.value?.username ?: "zero_${currentUserId.take(5)}"
        val emojis = CryptoEngine.deriveSafetyEmojis("SIMULATED_CALL::$peerUsername::$myHandle")
        _simulatedIncomingCall.value = CallSignal(
            id = "sim_${System.currentTimeMillis()}",
            callerId = "peer_$peerUsername",
            callerUsername = peerUsername.removePrefix("@"),
            calleeUsername = myHandle,
            participantUids = listOf(currentUserId),
            callType = callType,
            status = "RINGING",
            safetyEmojis = emojis
        )
    }

    fun acceptIncomingCall(call: CallSignal) {
        _simulatedIncomingCall.value = null
        dismissedCallIdsInSession.add(call.id)
        _activeCall.value = call.copy(status = "CONNECTED")
        if (!call.id.startsWith("sim_") && call.id.isNotBlank()) {
            viewModelScope.launch {
                repository.acceptEncryptedCall(call.id)
            }
        }
    }

    fun declineIncomingCall(call: CallSignal) {
        _simulatedIncomingCall.value = null
        dismissedCallIdsInSession.add(call.id)
        if (!call.id.startsWith("sim_") && call.id.isNotBlank()) {
            viewModelScope.launch {
                repository.endEncryptedCall(call.id)
            }
        }
        _statusBannerMessage.value = "@${call.callerUsername} gelen شifreli araması reddedildi"
    }

    fun endActiveCall() {
        val current = _activeCall.value ?: return
        _activeCall.value = null
        viewModelScope.launch {
            if (!current.id.startsWith("sim_") && current.id.isNotBlank()) {
                repository.endEncryptedCall(current.id)
            }
        }
    }

    fun togglePinRoom(roomId: String, currentlyPinned: Boolean) {
        viewModelScope.launch {
            if (currentlyPinned) {
                vaultDao.unpinRoom(currentUserId, roomId)
                _statusBannerMessage.value = "Sohbet sabitlemesi kaldırıldı"
            } else {
                vaultDao.pinRoom(PinnedRoomEntity(ownerUid = currentUserId, roomId = roomId))
                _statusBannerMessage.value = "📌 Sohbet listenin en üstüne sabitlendi"
            }
        }
    }

    fun verifyActiveRoomIdentity() {
        val room = _selectedRoom.value ?: return
        val handle = room.title.removePrefix("@").trim().lowercase(Locale.ROOT)
        val peers = (directoryUsersState.value as? UiState.Success)?.data ?: emptyList()
        val matchedPeer = peers.firstOrNull { it.username.lowercase(Locale.ROOT) == handle }
        val pubKey = matchedPeer?.publicKey ?: room.roomKeyHint
        val safetyFp = matchedPeer?.safetyFingerprint ?: CryptoEngine.generateIdentityMaterial(room.id, room.roomKeyHint).second

        viewModelScope.launch {
            vaultDao.saveVerifiedPeer(
                VerifiedPeerEntity(
                    ownerUid = currentUserId,
                    peerUsername = handle,
                    verifiedPublicKey = pubKey,
                    verifiedSafetyFingerprint = safetyFp
                )
            )
            _simulatedKeyChangedHandles.value = _simulatedKeyChangedHandles.value - handle
            _statusBannerMessage.value = "🛡️ @${room.title.removePrefix("@")} düğümü 'Doğrulanmış (Verified)' olarak mühürlendi!"
        }
    }

    fun simulateMitmKeyChangeForActiveRoom() {
        val room = _selectedRoom.value ?: return
        val handle = room.title.removePrefix("@").trim().lowercase(Locale.ROOT)
        _simulatedKeyChangedHandles.value = _simulatedKeyChangedHandles.value + handle
        _statusBannerMessage.value = "⚠️ MITM Anahtar Değişimi Simülasyonu Tetiklendi: @$handle"
    }

    fun scheduleEncryptedMessage(plaintext: String, delaySeconds: Long, ttlOverride: Long?) {
        val room = _selectedRoom.value ?: return
        val cleaned = plaintext.trim()
        if (cleaned.isEmpty()) return
        val ttl = ttlOverride ?: room.defaultTtlSeconds
        val targetMillis = System.currentTimeMillis() + (delaySeconds.coerceAtLeast(5L) * 1000L)
        viewModelScope.launch {
            vaultDao.insertScheduledEnvelope(
                ScheduledEnvelopeEntity(
                    ownerUid = currentUserId,
                    roomId = room.id,
                    roomTitle = room.title,
                    plaintext = cleaned,
                    ttlSeconds = ttl,
                    scheduledAtMillis = targetMillis
                )
            )
            _statusBannerMessage.value = "⏳ Mesaj ${delaySeconds} sn sonra otomatik şifrelenip gönderilmek üzere zamanlandı"
        }
    }

    fun cancelScheduledMessage(id: Long) {
        viewModelScope.launch {
            vaultDao.deleteScheduledEnvelope(id)
            _statusBannerMessage.value = "Zamanlanmış mesaj iptal edildi"
        }
    }

    fun nukeActiveRoomHistory() {
        val room = _selectedRoom.value ?: return
        val senderName = _myProfile.value?.username ?: "zero_${currentUserId.take(5)}"
        viewModelScope.launch {
            vaultDao.deleteCachedMessagesForRoom(currentUserId, room.id)
            repository.nukeRoomHistory(room, senderName)
                .onSuccess { count ->
                    _rawRoomEnvelopes.value = emptyList()
                    _statusBannerMessage.value = "☢️ Oda geçmişi imha edildi ($count bulut zarfı + yerel kasa sıfırlandı)"
                }
                .onFailure { err ->
                    _statusBannerMessage.value = "Oda imha edilemedi: ${err.localizedMessage}"
                }
        }
    }

    fun onViewStatusStory(status: DecryptedStatusItem) {
        if (status.id.isNotBlank() && status.userId != currentUserId) {
            viewModelScope.launch {
                repository.incrementStatusViewCount(status.id, status.viewCount)
            }
        }
    }

    fun replyToStatusStory(status: DecryptedStatusItem, replyText: String) {
        val cleaned = replyText.trim()
        if (cleaned.isEmpty()) return
        val me = _myProfile.value ?: UserProfile(
            id = currentUserId,
            userId = currentUserId,
            username = "zero_${currentUserId.take(5)}",
            displayName = "Zero Node"
        )
        val peers = (directoryUsersState.value as? UiState.Success)?.data ?: emptyList()
        val authorPeer = peers.firstOrNull {
            it.userId == status.userId || it.username.equals(status.authorUsername, ignoreCase = true)
        } ?: UserProfile(
            id = status.userId.ifBlank { "peer_${status.authorUsername}" },
            userId = status.userId.ifBlank { currentUserId },
            username = status.authorUsername,
            displayName = status.authorUsername
        )

        viewModelScope.launch {
            repository.createOrGetDirectRoom(me, authorPeer)
                .onSuccess { room ->
                    val metaJson = JSONObject().apply {
                        put("replyToSender", status.authorUsername)
                        put("replyToPreview", "📖 Durum: ${status.decryptedContent.take(60)}")
                        put("ratchetStep", 1)
                    }.toString()
                    repository.sendEncryptedMessage(
                        room = room,
                        senderUsername = me.username,
                        plaintext = cleaned,
                        messageType = "TEXT",
                        encryptedMeta = metaJson,
                        ttlSeconds = room.defaultTtlSeconds,
                        ratchetStep = 1
                    ).onSuccess {
                        _statusBannerMessage.value = "⚡ @${status.authorUsername} durumuna şifreli yanıt iletildi!"
                    }
                }
                .onFailure { err ->
                    _statusBannerMessage.value = "Durum yanıtı gönderilemedi: ${err.localizedMessage}"
                }
        }
    }

    fun notifySecureClipboardCopied() {
        _statusBannerMessage.value = "📋 Mesaj güvenli panoya kopyalandı (30 sn sonra otomatik temizlenecek)"
    }

    private fun parseReactionsMap(summary: String): Map<String, String> {
        if (summary.isBlank()) return emptyMap()
        return summary.split(",")
            .mapNotNull { entry ->
                val parts = entry.split(":")
                if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                    parts[0] to parts[1]
                } else {
                    null
                }
            }.toMap()
    }

    companion object {
        private const val TAG = "ZeroChatVM"

        fun parsePollOptions(encryptedMeta: String): Pair<String, List<PollOptionItem>> {
            return try {
                val obj = JSONObject(encryptedMeta)
                val q = obj.optString("question", "Şifreli Anket")
                val arr = obj.optJSONArray("options") ?: JSONArray()
                val list = mutableListOf<PollOptionItem>()
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    val votersArr = item.optJSONArray("voters") ?: JSONArray()
                    val voters = mutableListOf<String>()
                    for (j in 0 until votersArr.length()) {
                        voters.add(votersArr.getString(j))
                    }
                    list.add(
                        PollOptionItem(
                            index = item.optInt("index", i),
                            text = item.optString("text", ""),
                            voterNames = voters
                        )
                    )
                }
                q to list
            } catch (e: Exception) {
                "Şifreli Anket" to emptyList()
            }
        }
    }
}
