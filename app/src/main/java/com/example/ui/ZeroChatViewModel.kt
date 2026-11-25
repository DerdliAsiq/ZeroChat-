package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.crypto.CryptoEngine
import com.example.crypto.HardwareKeyStatus
import com.example.data.local.CachedVaultMessageEntity
import com.example.data.local.HiddenRoomEntity
import com.example.data.local.LocalVaultDao
import com.example.data.local.LockedRoomEntity
import com.example.data.local.PendingOutboxEntity
import com.example.data.local.PinnedRoomEntity
import com.example.data.local.ScheduledEnvelopeEntity
import com.example.data.local.VaultSecurityConfigEntity
import com.example.data.local.VerifiedPeerEntity
import com.example.data.model.AnnotationKind
import com.example.data.model.CallSignal
import com.example.data.model.CallStatus
import com.example.data.model.ChatRoom
import com.example.data.model.DecryptedMessageItem
import com.example.data.model.DecryptedStatusItem
import com.example.data.model.EncryptedEnvelope
import com.example.data.model.EncryptedStatus
import com.example.data.model.PollOptionItem
import com.example.data.model.RoomAnnotation
import com.example.data.model.RoomPresence
import com.example.data.model.UserProfile
import com.example.data.repository.OpenedEnvelope
import com.example.data.repository.ZeroChatRepository
import com.example.data.repository.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

/** Safety number and emojis for a direct conversation, derived from both identity keys. */
data class SafetyInfo(val safetyNumber: String, val emojis: String, val peerUsername: String)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class ZeroChatViewModel(
    private val repository: ZeroChatRepository,
    private val vaultDao: LocalVaultDao,
    val currentUserId: String,
    private val preferredHandle: String?,
    private val displayNameHint: String?,
    /** Called with the next due time of a scheduled message (null = none) to arm WorkManager. */
    private val onScheduledMessagesChanged: (Long?) -> Unit = {}
) : ViewModel() {

    // ------------------------------------------------------------------ simple UI state

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

    data class SystemNotificationEvent(
        val id: Int,
        val title: String,
        val body: String,
        val isCall: Boolean = false,
        val isVideoCall: Boolean = false,
        val callerUsername: String = ""
    )

    /** One-shot notification events; every event is delivered exactly once. */
    private val notificationChannel = Channel<SystemNotificationEvent>(Channel.BUFFERED)
    val notificationEvents: Flow<SystemNotificationEvent> = notificationChannel.receiveAsFlow()

    private fun banner(message: String) {
        _statusBannerMessage.value = message
    }

    fun clearBannerMessage() {
        _statusBannerMessage.value = null
    }

    fun postBannerMessage(message: String) = banner(message)

    // ------------------------------------------------------------------ local (Room) state

    val securityConfig: StateFlow<VaultSecurityConfigEntity?> = vaultDao.observeSecurityConfig(currentUserId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val isScreenShieldEnabled: StateFlow<Boolean> = combine(securityConfig, _isVaultUnlocked, _isPanicModeActive) { cfg, unlocked, panic ->
        (cfg?.screenShieldEnabled == true) || unlocked || panic
    }.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val pendingOutboxItems: StateFlow<List<PendingOutboxEntity>> = vaultDao.observePendingOutbox(currentUserId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val cachedMessageCount: StateFlow<Int> = combine(vaultDao.observeCachedMessageCount(currentUserId), _isPanicModeActive) { n, panic ->
        if (panic) 0 else n
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0)

    val starredVaultMessages: StateFlow<List<CachedVaultMessageEntity>> =
        combine(vaultDao.observeStarredMessages(currentUserId), _isPanicModeActive) { list, panic ->
            if (panic) emptyList() else list
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val lockedRoomIds: StateFlow<Set<String>> = vaultDao.observeLockedRooms(currentUserId)
        .map { list -> list.map { it.roomId }.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val pinnedRoomIds: StateFlow<Set<String>> = vaultDao.observePinnedRooms(currentUserId)
        .map { list -> list.map { it.roomId }.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    private val hiddenRooms: StateFlow<Map<String, Long>> = vaultDao.observeHiddenRooms(currentUserId)
        .map { list -> list.associate { it.roomId to it.hiddenAtMillis } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val verifiedPeers: StateFlow<List<VerifiedPeerEntity>> = vaultDao.observeVerifiedPeers(currentUserId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val scheduledEnvelopes: StateFlow<List<ScheduledEnvelopeEntity>> = vaultDao.observeScheduledEnvelopes(currentUserId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val activeRoomScheduledEnvelopes: StateFlow<List<ScheduledEnvelopeEntity>> =
        combine(scheduledEnvelopes, _selectedRoom) { list, room ->
            if (room == null) emptyList() else list.filter { it.roomId == room.id }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    // ------------------------------------------------------------------ profile & identity

    private val _myProfile = MutableStateFlow<UserProfile?>(null)
    val myProfile: StateFlow<UserProfile?> = _myProfile.asStateFlow()

    private val myHandle: String
        get() = _myProfile.value?.username ?: "zero_${currentUserId.take(6).lowercase(Locale.ROOT)}"

    // ------------------------------------------------------------------ rooms & contacts

    private val roomsRaw: StateFlow<UiState<List<ChatRoom>>> = repository.observeUserRooms()
        .map { UiState.Success(it) as UiState<List<ChatRoom>> }
        .catch { error -> emitError(error, "Sohbetler yüklenemedi") }
        .stateIn(viewModelScope, SharingStarted.Eagerly, UiState.Loading)

    private val _searchResults = MutableStateFlow<List<UserProfile>>(emptyList())

    /** Uids whose profiles we keep live: everyone we share a room with plus search hits. */
    private val contactUids: Flow<Set<String>> = combine(roomsRaw, _searchResults) { roomsState, search ->
        val roomUids = (roomsState as? UiState.Success)?.data?.flatMap { it.participantUids }.orEmpty()
        (roomUids + search.map { it.userId } + currentUserId).toSet()
    }.distinctUntilChanged()

    val profilesByUid: StateFlow<Map<String, UserProfile>> = contactUids
        .debounce(150)
        .flatMapLatest { uids ->
            repository.observeProfiles(uids).catch { e ->
                Log.w(TAG, "profiles listener failed", e)
                emit(emptyMap())
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val previewCache = ConcurrentHashMap<String, String>()

    val roomsState: StateFlow<UiState<List<ChatRoom>>> = combine(
        roomsRaw, profilesByUid, _isPanicModeActive, pinnedRoomIds, hiddenRooms
    ) { state, profiles, panic, pinned, hidden ->
        Snapshot(state, profiles, panic, pinned, hidden)
    }.mapLatest { snap ->
        val state = snap.state
        if (state !is UiState.Success) return@mapLatest state
        if (snap.panic) return@mapLatest UiState.Success(emptyList<ChatRoom>())
        val decorated = state.data
            .filter { room -> snap.hidden[room.id]?.let { hiddenAt -> room.activityMillis > hiddenAt } ?: true }
            .map { room -> decorateRoom(room, snap.profiles) }
            .sortedWith(
                compareByDescending<ChatRoom> { snap.pinned.contains(it.id) }.thenByDescending { it.activityMillis }
            )
        UiState.Success(decorated) as UiState<List<ChatRoom>>
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, UiState.Loading)

    private data class Snapshot(
        val state: UiState<List<ChatRoom>>,
        val profiles: Map<String, UserProfile>,
        val panic: Boolean,
        val pinned: Set<String>,
        val hidden: Map<String, Long>
    )

    private suspend fun decorateRoom(room: ChatRoom, profiles: Map<String, UserProfile>): ChatRoom {
        val names = room.participantUids.mapNotNull { profiles[it]?.username }
        val previewKey = "${room.id}|${room.lastPreviewCt.ifBlank { room.lastCipherPreview }}"
        val preview = previewCache[previewKey] ?: (repository.decryptPreview(room) ?: "").also {
            if (it.isNotBlank()) previewCache[previewKey] = it
        }
        val peerTitle = if (!room.isGroup) {
            room.peerUidFor(currentUserId)?.let { profiles[it]?.username }?.let { "@$it" }
        } else {
            null
        }
        return room.copy(
            participantNames = if (names.size == room.participantUids.size) names.joinToString(",") else room.participantNames,
            title = peerTitle ?: room.title,
            previewText = preview.ifBlank { if (room.isPendingRequest) "🤝 Sohbet isteği" else "🔒 Şifreli sohbet" },
            lastSenderName = profiles[room.lastSenderUid]?.username.orEmpty(),
            requesterUsername = profiles[room.requesterUid]?.username.orEmpty()
        )
    }

    val directoryUsersState: StateFlow<UiState<List<UserProfile>>> = combine(
        profilesByUid, _searchResults, _searchQuery, _isPanicModeActive
    ) { profiles, search, query, panic ->
        if (panic) return@combine UiState.Success(emptyList<UserProfile>())
        val q = query.trim().removePrefix("@").lowercase(Locale.ROOT)
        val all = (profiles.values + search).distinctBy { it.userId }.filter { it.userId != currentUserId }
        val filtered = if (q.isEmpty()) all else all.filter {
            it.username.lowercase(Locale.ROOT).contains(q) || it.displayName.lowercase(Locale.ROOT).contains(q)
        }
        UiState.Success(filtered.sortedBy { it.username }) as UiState<List<UserProfile>>
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    /** Handles of peers whose identity keys were verified and still match. */
    val verifiedPeerHandles: StateFlow<Set<String>> = combine(verifiedPeers, profilesByUid) { verified, profiles ->
        verified.mapNotNull { v ->
            val p = profiles[v.peerUid] ?: return@mapNotNull null
            if (CryptoEngine.identityKeyId(p.publicKey, p.signingKey) == v.verifiedKeyId) p.username.lowercase(Locale.ROOT) else null
        }.toSet()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptySet())

    /** Handles of verified peers whose identity keys changed since verification. */
    val keyChangedPeerHandles: StateFlow<Set<String>> = combine(verifiedPeers, profilesByUid) { verified, profiles ->
        verified.mapNotNull { v ->
            val p = profiles[v.peerUid] ?: return@mapNotNull null
            if (p.hasE2eeKeys && CryptoEngine.identityKeyId(p.publicKey, p.signingKey) != v.verifiedKeyId) {
                p.username.lowercase(Locale.ROOT)
            } else {
                null
            }
        }.toSet()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptySet())

    // ------------------------------------------------------------------ active room

    private val ticker: Flow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1000L)
        }
    }

    private val selectedRoomId: Flow<String?> = _selectedRoom.map { it?.id }.distinctUntilChanged()

    private val presenceInRoom: StateFlow<List<RoomPresence>> = selectedRoomId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.observePresence(id).catch { emit(emptyList()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val peerTypingHandleInActiveRoom: StateFlow<String?> = combine(presenceInRoom, profilesByUid, ticker) { presence, profiles, now ->
        presence.firstOrNull { p ->
            p.uid != currentUserId && (p.typingAt?.toDate()?.time ?: 0L) > now - TYPING_WINDOW_MS
        }?.let { profiles[it.uid]?.username }
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    private val openedCache = ConcurrentHashMap<String, OpenedEnvelope>()
    private val annotationValueCache = ConcurrentHashMap<String, String>()

    private suspend fun annotationValue(roomId: String, ann: RoomAnnotation): String? {
        val key = "${ann.id}|${ann.ciphertext.hashCode()}"
        annotationValueCache[key]?.let { return it }
        return repository.decryptAnnotation(roomId, ann)?.also { annotationValueCache[key] = it }
    }
    private val expiredHandled = ConcurrentHashMap.newKeySet<String>()
    private val cachedToVault = ConcurrentHashMap.newKeySet<String>()

    private data class RoomInputs(
        val room: ChatRoom,
        val envelopes: List<EncryptedEnvelope>,
        val annotations: List<RoomAnnotation>,
        val presence: List<RoomPresence>,
        val starred: Set<String>,
        val profiles: Map<String, UserProfile>
    )

    private val decryptedRoomMessages: StateFlow<List<DecryptedMessageItem>> = _selectedRoom
        .distinctUntilChanged { a, b -> a?.id == b?.id && a?.roomKeyHint == b?.roomKeyHint }
        .flatMapLatest { room ->
            if (room == null) return@flatMapLatest flowOf(emptyList())
            combine(
                repository.observeRoomEnvelopes(room.id).catch { e ->
                    banner("Mesajlar yüklenemedi: ${e.userMessage()}")
                    emit(emptyList())
                },
                repository.observeAnnotations(room.id).catch { emit(emptyList()) },
                presenceInRoom,
                vaultDao.observeStarredIdsInRoom(currentUserId, room.id).map { it.toSet() },
                profilesByUid
            ) { envs, anns, presence, starred, profiles ->
                RoomInputs(room, envs, anns, presence, starred, profiles)
            }.mapLatest { inputs -> buildMessages(inputs) }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    /** Decrypted messages of the open room, with live countdowns; expired messages are removed. */
    val activeRoomMessages: StateFlow<List<DecryptedMessageItem>> = combine(
        decryptedRoomMessages,
        _inChatSearchQuery,
        decryptedRoomMessages.flatMapLatest { list ->
            if (list.any { it.ttlSeconds > 0L }) ticker else flowOf(0L)
        }
    ) { list, query, now ->
        val clock = if (now == 0L) System.currentTimeMillis() else now
        val visible = list.mapNotNull { msg ->
            if (msg.ttlSeconds <= 0L) return@mapNotNull msg
            val remaining = (msg.createdAtMillis + msg.ttlSeconds * 1000L - clock) / 1000L
            if (remaining <= 0L) {
                onMessageExpired(msg)
                null
            } else {
                msg.copy(remainingSeconds = remaining)
            }
        }
        val q = query.trim().lowercase(Locale.ROOT)
        if (q.isEmpty()) visible else visible.filter {
            it.decryptedText.lowercase(Locale.ROOT).contains(q) ||
                it.senderUsername.lowercase(Locale.ROOT).contains(q) ||
                it.fileName.lowercase(Locale.ROOT).contains(q)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private fun onMessageExpired(msg: DecryptedMessageItem) {
        if (!expiredHandled.add(msg.id)) return
        viewModelScope.launch {
            vaultDao.deleteCachedMessages(listOf(msg.id))
            // Rules allow any participant to delete an envelope once its expireAt has passed;
            // the Firestore TTL policy removes it server-side even if no client is online.
            repository.deleteMessage(msg.id)
        }
    }

    private suspend fun buildMessages(inputs: RoomInputs): List<DecryptedMessageItem> {
        val room = inputs.room
        val hiddenBefore = hiddenRooms.value[room.id] ?: 0L
        val annotationsByEnvelope = inputs.annotations.groupBy { it.envelopeId }
        val readTimes = inputs.presence
            .filter { it.uid != currentUserId }
            .associate { it.uid to (it.lastReadAt?.toDate()?.time ?: 0L) }
        val toCache = mutableListOf<CachedVaultMessageEntity>()

        val items = inputs.envelopes.mapNotNull { env ->
            val createdMillis = (env.createdAt ?: env.updatedAt)?.toDate()?.time ?: System.currentTimeMillis()
            if (createdMillis < hiddenBefore) return@mapNotNull null

            val cacheKey = "${env.id}|${env.ciphertext.hashCode()}|${env.updatedAt?.seconds}"
            val opened = openedCache[cacheKey] ?: repository.openEnvelope(room, env).also { openedCache[cacheKey] = it }
            val payload = opened.payload
            val meta = payload?.optJSONObject("meta") ?: JSONObject()
            val type = payload?.optString("type")?.ifBlank { "TEXT" } ?: "TEXT"
            val text = when {
                payload == null -> "🔒 Bu mesaj çözülemedi"
                else -> payload.optString("text")
            }
            val senderName = inputs.profiles[env.senderId]?.username
                ?: payload?.optString("sender")?.ifBlank { null }
                ?: "bilinmeyen"

            val anns = annotationsByEnvelope[env.id].orEmpty()
            val reactions = mutableMapOf<String, String>()
            val votesByUid = mutableMapOf<String, Int>()
            var pinned = false
            for (ann in anns) {
                when (ann.kind) {
                    AnnotationKind.PIN -> pinned = true
                    AnnotationKind.REACTION -> annotationValue(room.id, ann)?.let { emoji ->
                        val handle = inputs.profiles[ann.uid]?.username ?: ann.uid.take(6)
                        reactions[handle] = emoji
                    }
                    AnnotationKind.VOTE -> annotationValue(room.id, ann)?.toIntOrNull()?.let {
                        votesByUid[ann.uid] = it
                    }
                }
            }
            if (type == "POLL") applyVotesToPollMeta(meta, votesByUid, inputs.profiles)

            val readBy = 1 + readTimes.count { (uid, readAt) -> uid != env.senderId && readAt >= createdMillis }

            if (payload != null && cachedToVault.add(cacheKey)) {
                toCache.add(
                    CachedVaultMessageEntity(
                        messageId = env.id,
                        ownerUid = currentUserId,
                        roomId = room.id,
                        roomTitle = room.title,
                        senderUsername = senderName,
                        decryptedText = text,
                        messageType = type,
                        isStarred = inputs.starred.contains(env.id),
                        createdAtMillis = createdMillis,
                        expireAtMillis = if (env.ttlSeconds > 0L) createdMillis + env.ttlSeconds * 1000L else 0L
                    )
                )
            }

            DecryptedMessageItem(
                id = env.id,
                roomId = room.id,
                senderId = env.senderId,
                senderUsername = senderName,
                decryptedText = text,
                ciphertext = env.ciphertext,
                iv = env.iv,
                signatureHash = env.sig.ifBlank { env.signatureHash },
                messageType = type,
                encryptedMeta = meta.toString(),
                reactions = reactions,
                ttlSeconds = env.ttlSeconds,
                createdAtMillis = createdMillis,
                replyToSender = meta.optString("replyToSender").ifBlank { null },
                replyToPreview = meta.optString("replyToPreview").ifBlank { null },
                isPinned = pinned,
                isEdited = env.edited,
                forwardedFrom = meta.optString("forwardedFrom").ifBlank { null },
                readByCount = readBy,
                audioBase64 = meta.optString("audioBase64"),
                fileName = meta.optString("fileName"),
                fileSizeKb = meta.optInt("fileSizeKb", 0),
                fileBase64 = meta.optString("fileBase64"),
                fileMime = meta.optString("fileMime", "application/octet-stream"),
                isViewOnce = meta.optBoolean("isViewOnce", false),
                isSpoiler = meta.optBoolean("isSpoiler", false),
                isStarred = inputs.starred.contains(env.id),
                isLegacy = opened.isLegacy,
                isSignatureVerified = opened.isVerified,
                isSignerKeyChanged = opened.signerKeyChanged
            )
        }

        if (toCache.isNotEmpty()) vaultDao.cacheDecryptedMessages(toCache)
        maybeMarkRead(room, items)
        return items
    }

    private fun applyVotesToPollMeta(meta: JSONObject, votesByUid: Map<String, Int>, profiles: Map<String, UserProfile>) {
        val options = meta.optJSONArray("options") ?: return
        for (i in 0 until options.length()) {
            val opt = options.optJSONObject(i) ?: continue
            val index = opt.optInt("index", i)
            val voters = JSONArray()
            votesByUid.filterValues { it == index }.keys.forEach { uid ->
                voters.put(profiles[uid]?.username ?: uid.take(6))
            }
            opt.put("voters", voters)
        }
    }

    private var lastMarkReadAt = 0L

    private fun maybeMarkRead(room: ChatRoom, items: List<DecryptedMessageItem>) {
        if (_selectedRoom.value?.id != room.id) return
        val newestFromOthers = items.lastOrNull { it.senderId != currentUserId }?.createdAtMillis ?: return
        val now = System.currentTimeMillis()
        if (newestFromOthers <= lastMarkReadAt || now - lastMarkReadAt < 2000L) return
        lastMarkReadAt = now
        viewModelScope.launch { repository.markRoomRead(room.id) }
    }

    // ------------------------------------------------------------------ statuses

    private val statusCache = ConcurrentHashMap<String, String>()
    private val statusViewerCache = ConcurrentHashMap<String, List<String>>()

    val statusesState: StateFlow<UiState<List<DecryptedStatusItem>>> = combine(
        repository.observeStatuses()
            .map { UiState.Success(it) as UiState<List<EncryptedStatus>> }
            .catch { e -> emitError(e, "Durumlar yüklenemedi") },
        profilesByUid,
        _isPanicModeActive
    ) { state, profiles, panic -> Triple(state, profiles, panic) }
        .mapLatest { (state, profiles, panic) ->
            when (state) {
                is UiState.Loading -> return@mapLatest UiState.Loading
                is UiState.Error -> return@mapLatest state
                is UiState.Success -> Unit
            }
            if (panic) return@mapLatest UiState.Success(emptyList<DecryptedStatusItem>())
            val now = System.currentTimeMillis()
            val items = state.data.mapNotNull { st ->
                val created = st.createdAt?.toDate()?.time ?: now
                if ((st.expireAt?.toDate()?.time ?: (created + DAY_MS)) < now) return@mapNotNull null
                val content = statusCache[st.id] ?: repository.openStatus(st)?.also { statusCache[st.id] = it }
                    ?: return@mapNotNull null
                val viewers = if (st.userId == currentUserId) {
                    statusViewerCache[st.id] ?: repository.statusViewers(st.id).getOrDefault(emptyList())
                        .also { statusViewerCache[st.id] = it }
                } else {
                    emptyList()
                }
                DecryptedStatusItem(
                    id = st.id,
                    userId = st.userId,
                    authorUsername = profiles[st.userId]?.username ?: "bilinmeyen",
                    decryptedContent = content,
                    ciphertext = st.ciphertext,
                    iv = st.iv,
                    themeColorHex = st.themeColorHex,
                    viewCount = viewers.count { it != currentUserId }.toLong(),
                    createdAtMillis = created,
                    viewerNames = viewers.filter { it != currentUserId }.map { profiles[it]?.username ?: it.take(6) }
                )
            }
            UiState.Success(items) as UiState<List<DecryptedStatusItem>>
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    // ------------------------------------------------------------------ calls

    private val callsRaw: StateFlow<UiState<List<CallSignal>>> = repository.observeCalls()
        .map { UiState.Success(it) as UiState<List<CallSignal>> }
        .catch { e -> emitError(e, "Arama kayıtları alınamadı") }
        .stateIn(viewModelScope, SharingStarted.Eagerly, UiState.Loading)

    val callSignalsState: StateFlow<UiState<List<CallSignal>>> = combine(callsRaw, profilesByUid, _isPanicModeActive) { state, profiles, panic ->
        when {
            state !is UiState.Success -> state
            panic -> UiState.Success(emptyList())
            else -> UiState.Success(state.data.map { decorateCall(it, profiles) })
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    private fun decorateCall(call: CallSignal, profiles: Map<String, UserProfile>): CallSignal {
        val caller = profiles[call.callerId]
        val callee = profiles[call.calleeUid]
        val emojis = if (caller != null && callee != null && caller.hasE2eeKeys && callee.hasE2eeKeys) {
            CryptoEngine.safetyEmojis(
                caller.userId, caller.publicKey, caller.signingKey,
                callee.userId, callee.publicKey, callee.signingKey
            )
        } else {
            ""
        }
        return call.copy(
            callerUsername = caller?.username ?: "bilinmeyen",
            calleeUsername = callee?.username ?: "bilinmeyen",
            safetyEmojis = emojis
        )
    }

    private val activeCallId = MutableStateFlow<String?>(null)
    private val dismissedCallIds = ConcurrentHashMap.newKeySet<String>()
    private val notifiedCallIds = ConcurrentHashMap.newKeySet<String>()
    private var outgoingSeq = 0L

    private val activeCallDoc: StateFlow<CallSignal?> = combine(callSignalsState, activeCallId) { state, id ->
        if (id == null) null else (state as? UiState.Success)?.data?.firstOrNull { it.id == id }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private data class RemoteMedia(val seq: Long, val audio: String, val video: String)

    private val remoteMedia: StateFlow<RemoteMedia?> = activeCallDoc
        .map { call -> call?.takeIf { it.status == CallStatus.CONNECTED } }
        .distinctUntilChanged { a, b -> a?.id == b?.id }
        .flatMapLatest { call ->
            if (call == null) return@flatMapLatest flowOf(null)
            val peer = if (call.callerId == currentUserId) call.calleeUid else call.callerId
            repository.observeCallMedia(call.id, peer)
                .filterNotNull()
                .mapLatest { chunk ->
                    repository.openCallMedia(call, peer, chunk)?.let { (a, v) -> RemoteMedia(chunk.seq, a, v) }
                }
                .catch { emit(null) }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val activeCall: StateFlow<CallSignal?> = combine(activeCallDoc, remoteMedia) { call, media ->
        call?.copy(
            remoteAudioB64 = media?.audio.orEmpty(),
            remoteVideoB64 = media?.video.orEmpty(),
            remoteSeq = media?.seq ?: 0L
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val incomingRingingCall: StateFlow<CallSignal?> = combine(callSignalsState, activeCallId, _isPanicModeActive) { state, active, panic ->
        if (panic || active != null) return@combine null
        val now = System.currentTimeMillis()
        (state as? UiState.Success)?.data?.firstOrNull { call ->
            call.status == CallStatus.RINGING &&
                call.calleeUid == currentUserId &&
                !dismissedCallIds.contains(call.id) &&
                now - (call.createdAt?.toDate()?.time ?: now) < RING_TIMEOUT_MS
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // ------------------------------------------------------------------ init

    init {
        bootstrapIdentity()
        observeMyProfile()
        observeSearch()
        observeRoomsForSelectionAndNotifications()
        observeCallsLifecycle()
        startScheduledDispatcher()
        viewModelScope.launch { vaultDao.deleteExpiredCachedMessages(System.currentTimeMillis()) }
    }

    private fun bootstrapIdentity() {
        viewModelScope.launch {
            val identity = withContext(Dispatchers.Default) { runCatching { repository.identity() } }
            identity.onSuccess { _hardwareKeyStatus.value = it.status() }
                .onFailure { banner("Kimlik anahtarları yüklenemedi: ${it.userMessage()}") }
            repository.ensureProfile(preferredHandle, displayNameHint)
                .onSuccess { _myProfile.value = it }
                .onFailure { banner("Profil oluşturulamadı: ${it.userMessage()}") }
            repository.setPresence(true)
        }
    }

    private fun observeMyProfile() {
        viewModelScope.launch {
            repository.observeMyProfile()
                .catch { e -> Log.w(TAG, "profile listener failed", e) }
                .collect { profile -> if (profile != null) _myProfile.value = profile }
        }
    }

    private fun observeSearch() {
        viewModelScope.launch {
            _searchQuery
                .map { it.trim().removePrefix("@").lowercase(Locale.ROOT) }
                .distinctUntilChanged()
                .debounce(300)
                .collect { q ->
                    if (q.length < 2) {
                        _searchResults.value = emptyList()
                    } else {
                        repository.searchUsers(q)
                            .onSuccess { _searchResults.value = it }
                            .onFailure { Log.w(TAG, "search failed", it) }
                    }
                }
        }
    }

    /** Keeps the open room in sync with the live list and raises message notifications. */
    private fun observeRoomsForSelectionAndNotifications() {
        val lastSeenActivity = mutableMapOf<String, Long>()
        var initialized = false
        viewModelScope.launch {
            roomsState.collect { state ->
                val rooms = (state as? UiState.Success)?.data ?: return@collect
                _selectedRoom.value?.let { current ->
                    rooms.firstOrNull { it.id == current.id }?.let { _selectedRoom.value = it }
                }
                val now = System.currentTimeMillis()
                for (room in rooms) {
                    val activity = room.activityMillis
                    val previous = lastSeenActivity[room.id]
                    lastSeenActivity[room.id] = activity
                    if (!initialized || previous == null || activity <= previous) continue
                    if (room.lastSenderUid.isBlank() || room.lastSenderUid == currentUserId) continue
                    if (_selectedRoom.value?.id == room.id || now - activity > 120_000L) continue
                    val locked = lockedRoomIds.value.contains(room.id)
                    notificationChannel.trySend(
                        SystemNotificationEvent(
                            id = room.id.hashCode(),
                            title = if (locked) "ZeroChat" else if (room.isPendingRequest) {
                                "Yeni sohbet isteği: @${room.lastSenderName}"
                            } else {
                                room.displayTitleFor(myHandle)
                            },
                            body = if (locked) "Yeni mesaj" else room.previewText
                        )
                    )
                }
                initialized = true
            }
        }
    }

    private fun observeCallsLifecycle() {
        viewModelScope.launch {
            activeCallDoc.collect { call ->
                if (activeCallId.value != null && call != null && call.status == CallStatus.ENDED) {
                    activeCallId.value = null
                    banner("Arama sonlandı")
                }
            }
        }
        viewModelScope.launch {
            incomingRingingCall.collect { call ->
                if (call != null && notifiedCallIds.add(call.id)) {
                    notificationChannel.trySend(
                        SystemNotificationEvent(
                            id = call.id.hashCode(),
                            title = "@${call.callerUsername}",
                            body = if (call.callType == "VIDEO") "Görüntülü arama" else "Sesli arama",
                            isCall = true,
                            isVideoCall = call.callType == "VIDEO",
                            callerUsername = call.callerUsername
                        )
                    )
                }
            }
        }
        // End my own outgoing calls nobody answered.
        viewModelScope.launch {
            while (true) {
                delay(5000L)
                val now = System.currentTimeMillis()
                (callsRaw.value as? UiState.Success)?.data
                    ?.filter { it.callerId == currentUserId && it.status == CallStatus.RINGING }
                    ?.filter { now - (it.createdAt?.toDate()?.time ?: now) > RING_TIMEOUT_MS }
                    ?.forEach { repository.setCallStatus(it.id, CallStatus.ENDED) }
            }
        }
    }

    private suspend fun <T> kotlinx.coroutines.flow.FlowCollector<UiState<T>>.emitError(error: Throwable, fallback: String) {
        if (error is CancellationException) throw error
        Log.w(TAG, fallback, error)
        emit(UiState.Error("$fallback: ${error.userMessage()}"))
    }

    // ------------------------------------------------------------------ search, typing, profile

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateInChatSearchQuery(query: String) {
        _inChatSearchQuery.value = query
    }

    fun setReplyingTo(message: DecryptedMessageItem?) {
        _replyingToMessage.value = message
        if (message != null) _editingMessage.value = null
    }

    fun setEditingMessage(message: DecryptedMessageItem?) {
        if (message != null && message.senderId != currentUserId) return
        _editingMessage.value = message
        if (message != null) _replyingToMessage.value = null
    }

    private var lastTypingSentAt = 0L

    fun notifyUserTypingInActiveRoom() {
        val room = _selectedRoom.value ?: return
        val now = System.currentTimeMillis()
        if (now - lastTypingSentAt < TYPING_THROTTLE_MS) return
        lastTypingSentAt = now
        viewModelScope.launch { repository.markTyping(room.id) }
    }

    fun updateMyProfile(username: String, displayName: String, bio: String) {
        viewModelScope.launch {
            repository.updateProfile(username, displayName, bio)
                .onSuccess {
                    _myProfile.value = it
                    banner("Profil güncellendi: @${it.username}")
                }
                .onFailure { banner("Profil güncellenemedi: ${it.userMessage()}") }
        }
    }

    // ------------------------------------------------------------------ rooms

    fun openRoom(room: ChatRoom) {
        if (_isPanicModeActive.value) return
        _selectedRoom.value = room
        _inChatSearchQuery.value = ""
        _replyingToMessage.value = null
        _editingMessage.value = null
        lastMarkReadAt = 0L
        viewModelScope.launch { repository.markRoomRead(room.id) }
    }

    fun closeRoom() {
        _selectedRoom.value = null
        _inChatSearchQuery.value = ""
        _replyingToMessage.value = null
        _editingMessage.value = null
    }

    fun startDirectChatWithPeer(peer: UserProfile, introNote: String = "") {
        if (_isPanicModeActive.value) return
        val me = _myProfile.value ?: run {
            banner("Profiliniz henüz yüklenmedi")
            return
        }
        val existing = (roomsState.value as? UiState.Success)?.data?.firstOrNull {
            !it.isGroup && it.participantUids.contains(peer.userId) && !it.isRejectedRequest
        }
        if (existing != null && introNote.isBlank()) {
            openRoom(existing)
            return
        }
        viewModelScope.launch {
            vaultDao.unhideRoom(currentUserId, ZeroChatRepository.directRoomId(currentUserId, peer.userId))
            repository.requestDirectChat(me, peer, introNote)
                .onSuccess { room ->
                    openRoom(room)
                    banner(
                        if (room.isPendingRequest) "@${peer.username} kişisine sohbet isteği gönderildi"
                        else "@${peer.username} ile sohbet açıldı"
                    )
                }
                .onFailure { banner("Sohbet başlatılamadı: ${it.userMessage()}") }
        }
    }

    fun approveConnectionRequest(room: ChatRoom) {
        viewModelScope.launch {
            repository.approveConnectionRequest(room)
                .onSuccess { banner("Sohbet isteği kabul edildi") }
                .onFailure { banner("İstek kabul edilemedi: ${it.userMessage()}") }
        }
    }

    fun rejectOrCancelConnectionRequest(room: ChatRoom) {
        viewModelScope.launch {
            repository.rejectOrCancelConnectionRequest(room)
                .onSuccess {
                    if (_selectedRoom.value?.id == room.id) closeRoom()
                    banner(if (room.requesterUid == currentUserId) "Sohbet isteği geri çekildi" else "Sohbet isteği reddedildi")
                }
                .onFailure { banner("İşlem başarısız: ${it.userMessage()}") }
        }
    }

    /**
     * "Delete chat": groups are left (owner deletes the group for everyone); direct messages
     * are hidden from this device only and the other person's history is untouched.
     */
    fun deleteChatRoomFromRecent(room: ChatRoom) {
        viewModelScope.launch {
            if (_selectedRoom.value?.id == room.id) closeRoom()
            vaultDao.deleteCachedMessagesForRoom(currentUserId, room.id)
            vaultDao.unpinRoom(currentUserId, room.id)
            vaultDao.unlockRoom(currentUserId, room.id)
            val result = when {
                room.isGroup && room.ownerId == currentUserId -> repository.deleteRoomForEveryone(room)
                room.isGroup -> repository.leaveGroup(room)
                room.isPendingRequest && room.requesterUid == currentUserId -> repository.rejectOrCancelConnectionRequest(room)
                else -> {
                    vaultDao.hideRoom(HiddenRoomEntity(ownerUid = currentUserId, roomId = room.id))
                    Result.success(Unit)
                }
            }
            result
                .onSuccess { banner("${room.title} listeden kaldırıldı") }
                .onFailure { banner("Sohbet silinemedi: ${it.userMessage()}") }
        }
    }

    /**
     * Handles a scanned QR code or typed handle. A QR code carries the peer's identity
     * fingerprint; the peer is marked verified only if it matches the keys on the server.
     */
    fun handleScannedPeerHandleOrKey(scannedInput: String) {
        val raw = scannedInput.trim()
        if (raw.isBlank()) return
        var handle = ""
        var uid = ""
        var fingerprint = ""
        try {
            if (raw.startsWith("zerochat://", ignoreCase = true) && raw.contains("?")) {
                raw.substringAfter("?").split("&").forEach { kv ->
                    val parts = kv.split("=", limit = 2)
                    if (parts.size == 2) {
                        val value = java.net.URLDecoder.decode(parts[1], "UTF-8")
                        when (parts[0].lowercase(Locale.ROOT)) {
                            "u", "user", "username" -> handle = value
                            "uid", "id" -> uid = value
                            "fp", "fingerprint" -> fingerprint = value
                        }
                    }
                }
            } else {
                handle = raw
            }
        } catch (e: IllegalArgumentException) {
            banner("QR kodu okunamadı")
            return
        }
        viewModelScope.launch {
            val peer = (if (uid.isNotBlank()) repository.getProfile(uid, forceRefresh = true) else null)
                ?: repository.findUserByHandle(handle).getOrNull()
            if (peer == null || peer.userId == currentUserId) {
                banner("Kullanıcı bulunamadı")
                return@launch
            }
            if (fingerprint.isNotBlank()) {
                val actual = CryptoEngine.fingerprint(peer.publicKey, peer.signingKey).filter { it.isDigit() }
                if (actual == fingerprint.filter { it.isDigit() } && peer.hasE2eeKeys) {
                    vaultDao.saveVerifiedPeer(
                        VerifiedPeerEntity(
                            ownerUid = currentUserId,
                            peerUid = peer.userId,
                            peerUsername = peer.username,
                            verifiedKeyId = CryptoEngine.identityKeyId(peer.publicKey, peer.signingKey)
                        )
                    )
                    banner("🛡️ @${peer.username} doğrulandı: QR kodu sunucudaki anahtarlarla eşleşiyor")
                } else {
                    banner("⚠️ @${peer.username} DOĞRULANAMADI: QR kodu sunucudaki anahtarlarla eşleşmiyor")
                    return@launch
                }
            }
            startDirectChatWithPeer(peer)
        }
    }

    fun createNewEncryptedGroup(title: String, peers: List<UserProfile>, ttlSeconds: Long) {
        val me = _myProfile.value ?: return
        viewModelScope.launch {
            repository.createGroupRoom(me, title, peers, ttlSeconds)
                .onSuccess {
                    banner("'${it.title}' grubu oluşturuldu")
                    openRoom(it)
                }
                .onFailure { banner("Grup oluşturulamadı: ${it.userMessage()}") }
        }
    }

    fun setRoomDefaultTtl(room: ChatRoom, ttlSeconds: Long) {
        viewModelScope.launch {
            repository.updateRoomTtl(room, ttlSeconds)
                .onSuccess { banner("Süreli mesaj: ${if (ttlSeconds == 0L) "Kapalı" else "$ttlSeconds sn"}") }
                .onFailure { banner("Ayar değiştirilemedi: ${it.userMessage()}") }
        }
    }

    fun updateActiveGroupMembersAndTitle(newTitle: String, selectedPeers: List<UserProfile>) {
        val room = _selectedRoom.value ?: return
        viewModelScope.launch {
            repository.updateGroupMembersAndTitle(room, newTitle, selectedPeers)
                .onSuccess {
                    _selectedRoom.value = it
                    banner("Grup güncellendi (${it.participantUids.size} üye)")
                }
                .onFailure { banner("Grup güncellenemedi: ${it.userMessage()}") }
        }
    }

    fun rotateActiveRoomEncryptionKey() {
        val room = _selectedRoom.value ?: return
        viewModelScope.launch {
            repository.rotateRoomKey(room)
                .onSuccess { banner("🔄 Sohbet anahtarı yenilendi (sürüm $it)") }
                .onFailure { banner("Anahtar yenilenemedi: ${it.userMessage()}") }
        }
    }

    fun togglePinRoom(roomId: String, currentlyPinned: Boolean) {
        viewModelScope.launch {
            if (currentlyPinned) {
                vaultDao.unpinRoom(currentUserId, roomId)
            } else {
                vaultDao.pinRoom(PinnedRoomEntity(ownerUid = currentUserId, roomId = roomId))
            }
        }
    }

    fun toggleRoomVaultLock(roomId: String, currentlyLocked: Boolean) {
        viewModelScope.launch {
            if (currentlyLocked) {
                vaultDao.unlockRoom(currentUserId, roomId)
                banner("Sohbet gizli kasadan çıkarıldı")
            } else {
                vaultDao.setRoomLocked(LockedRoomEntity(ownerUid = currentUserId, roomId = roomId))
                banner("Sohbet PIN korumalı gizli kasaya taşındı")
            }
        }
    }

    /** Safety number of the open direct conversation, or null for groups / unknown keys. */
    fun safetyInfoFor(room: ChatRoom): SafetyInfo? {
        if (room.isGroup) return null
        val me = _myProfile.value ?: return null
        val peerUid = room.peerUidFor(currentUserId) ?: return null
        val peer = profilesByUid.value[peerUid] ?: return null
        if (!me.hasE2eeKeys || !peer.hasE2eeKeys) return null
        return SafetyInfo(
            safetyNumber = CryptoEngine.safetyNumber(me.userId, me.publicKey, me.signingKey, peer.userId, peer.publicKey, peer.signingKey),
            emojis = CryptoEngine.safetyEmojis(me.userId, me.publicKey, me.signingKey, peer.userId, peer.publicKey, peer.signingKey),
            peerUsername = peer.username
        )
    }

    /** Marks the peer of the open conversation as verified after comparing the safety number. */
    fun verifyActiveRoomIdentity() {
        val room = _selectedRoom.value ?: return
        val peerUid = room.peerUidFor(currentUserId) ?: return
        val peer = profilesByUid.value[peerUid] ?: return
        if (!peer.hasE2eeKeys) {
            banner("@${peer.username} henüz şifreleme anahtarı yayınlamadı")
            return
        }
        viewModelScope.launch {
            vaultDao.saveVerifiedPeer(
                VerifiedPeerEntity(
                    ownerUid = currentUserId,
                    peerUid = peer.userId,
                    peerUsername = peer.username,
                    verifiedKeyId = CryptoEngine.identityKeyId(peer.publicKey, peer.signingKey)
                )
            )
            banner("🛡️ @${peer.username} doğrulandı")
        }
    }

    // ------------------------------------------------------------------ sending

    private fun baseMeta(isViewOnce: Boolean = false, isSpoiler: Boolean = false): JSONObject {
        val reply = _replyingToMessage.value
        return JSONObject().apply {
            if (isViewOnce) put("isViewOnce", true)
            if (isSpoiler) put("isSpoiler", true)
            if (reply != null) {
                put("replyToId", reply.id)
                put("replyToSender", reply.senderUsername)
                put("replyToPreview", reply.decryptedText.take(80))
            }
        }
    }

    private fun payload(type: String, text: String, meta: JSONObject): JSONObject = JSONObject().apply {
        put("type", type)
        put("text", text)
        put("sender", myHandle)
        put("meta", meta)
    }

    fun sendTextMessage(text: String, overrideTtlSeconds: Long? = null, isViewOnce: Boolean = false, isSpoiler: Boolean = false) {
        val room = _selectedRoom.value ?: return
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        val editing = _editingMessage.value
        if (editing != null) {
            _editingMessage.value = null
            if (editing.messageType != "TEXT" || editing.isLegacy) {
                banner("Yalnızca metin mesajları düzenlenebilir")
                return
            }
            val meta = runCatching { JSONObject(editing.encryptedMeta) }.getOrDefault(JSONObject())
            viewModelScope.launch {
                repository.editMessage(room, editing.id, payload("TEXT", trimmed, meta))
                    .onSuccess { banner("Mesaj düzenlendi") }
                    .onFailure { banner("Mesaj düzenlenemedi: ${it.userMessage()}") }
            }
            return
        }
        val meta = baseMeta(isViewOnce, isSpoiler)
        _replyingToMessage.value = null
        dispatchOrQueue(room, payload("TEXT", trimmed, meta), overrideTtlSeconds ?: room.defaultTtlSeconds)
    }

    fun forwardMessageToRoom(message: DecryptedMessageItem, targetRoom: ChatRoom) {
        val meta = runCatching { JSONObject(message.encryptedMeta) }.getOrDefault(JSONObject()).apply {
            remove("replyToId"); remove("replyToSender"); remove("replyToPreview")
            put("forwardedFrom", message.senderUsername)
        }
        dispatchOrQueue(targetRoom, payload(message.messageType, message.decryptedText, meta), targetRoom.defaultTtlSeconds) {
            banner("Mesaj '${targetRoom.title}' sohbetine iletildi")
        }
    }

    fun sendVoiceNoteMessage(durationSeconds: Int, amplitudePattern: String, audioBase64: String = "", ttlSeconds: Long? = null, isViewOnce: Boolean = false) {
        val room = _selectedRoom.value ?: return
        if (audioBase64.isBlank()) {
            banner("Ses kaydı boş, gönderilmedi")
            return
        }
        if (audioBase64.length > MAX_MEDIA_B64) {
            banner("Sesli mesaj çok uzun (en fazla ~${MAX_VOICE_SECONDS} sn)")
            return
        }
        val meta = baseMeta(isViewOnce).apply {
            put("durationSeconds", durationSeconds)
            put("waveform", amplitudePattern)
            put("audioBase64", audioBase64)
        }
        _replyingToMessage.value = null
        dispatchOrQueue(room, payload("VOICE", "🎙️ Sesli mesaj ($durationSeconds sn)", meta), ttlSeconds ?: room.defaultTtlSeconds)
    }

    /** [uriString] must be a `data:image/...;base64,` URI produced from the picked image. */
    fun sendImageAttachmentMessage(uriString: String, caption: String, ttlSeconds: Long? = null, isViewOnce: Boolean = false, stegoSecretMessage: String = "") {
        val room = _selectedRoom.value ?: return
        if (!uriString.startsWith("data:image")) {
            banner("Görsel işlenemedi, gönderilmedi")
            return
        }
        if (uriString.length > MAX_MEDIA_B64) {
            banner("Görsel çok büyük")
            return
        }
        val meta = baseMeta(isViewOnce).apply {
            put("mediaUri", uriString)
            put("mime", "image/jpeg")
        }
        _replyingToMessage.value = null
        dispatchOrQueue(room, payload("IMAGE", caption.trim(), meta), ttlSeconds ?: room.defaultTtlSeconds)
    }

    fun sendFileAttachmentMessage(fileName: String, fileSizeKb: Int, mimeType: String, fileBase64: String = "", ttlSeconds: Long? = null) {
        val room = _selectedRoom.value ?: return
        if (fileBase64.isBlank()) {
            banner("Dosya okunamadı, gönderilmedi")
            return
        }
        if (fileBase64.length > MAX_MEDIA_B64) {
            banner("Dosya çok büyük (en fazla ${MAX_FILE_KB} KB)")
            return
        }
        val meta = baseMeta().apply {
            put("fileName", fileName.take(120))
            put("fileSizeKb", fileSizeKb)
            put("fileMime", mimeType)
            put("fileBase64", fileBase64)
        }
        _replyingToMessage.value = null
        dispatchOrQueue(room, payload("FILE", "📄 $fileName ($fileSizeKb KB)", meta), ttlSeconds ?: room.defaultTtlSeconds)
    }

    fun sendLocationMessage(latitude: Double, longitude: Double, label: String, ttlSeconds: Long? = null) {
        val room = _selectedRoom.value ?: return
        val meta = baseMeta().apply {
            put("lat", latitude)
            put("lng", longitude)
            put("label", label)
        }
        _replyingToMessage.value = null
        val text = "📍 $label (${String.format(Locale.US, "%.5f, %.5f", latitude, longitude)})"
        dispatchOrQueue(room, payload("LOCATION", text, meta), ttlSeconds ?: room.defaultTtlSeconds)
    }

    fun sendPollMessage(question: String, options: List<String>, ttlSeconds: Long? = null) {
        val room = _selectedRoom.value ?: return
        val clean = options.map { it.trim() }.filter { it.isNotEmpty() }.take(5)
        if (question.isBlank() || clean.size < 2) return
        val arr = JSONArray()
        clean.forEachIndexed { idx, text ->
            arr.put(JSONObject().apply {
                put("index", idx)
                put("text", text)
                put("voters", JSONArray())
            })
        }
        val meta = baseMeta().apply {
            put("question", question.trim())
            put("options", arr)
        }
        _replyingToMessage.value = null
        dispatchOrQueue(room, payload("POLL", "📊 ${question.trim()}", meta), ttlSeconds ?: room.defaultTtlSeconds)
    }

    private val outboxMutex = Mutex()

    private fun dispatchOrQueue(room: ChatRoom, payload: JSONObject, ttlSeconds: Long, onSent: (() -> Unit)? = null) {
        val envelopeId = ZeroChatRepository.newId("env_")
        viewModelScope.launch {
            if (_isOfflineQueueMode.value) {
                enqueue(room, envelopeId, payload, ttlSeconds)
                banner("Mesaj çevrimdışı kuyruğa eklendi")
                return@launch
            }
            repository.sendMessage(room, payload, ttlSeconds, envelopeId)
                .onSuccess { onSent?.invoke() }
                .onFailure { error ->
                    if (isNetworkError(error)) {
                        enqueue(room, envelopeId, payload, ttlSeconds)
                        banner("Ağ hatası: mesaj kuyruğa alındı, bağlantı gelince gönderilecek")
                    } else {
                        banner("Mesaj gönderilemedi: ${error.userMessage()}")
                    }
                }
        }
    }

    private fun isNetworkError(error: Throwable): Boolean =
        generateSequence(error) { it.cause }
            .filterIsInstance<com.google.firebase.firestore.FirebaseFirestoreException>()
            .any { it.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAVAILABLE }

    private suspend fun enqueue(room: ChatRoom, envelopeId: String, payload: JSONObject, ttlSeconds: Long) {
        vaultDao.enqueuePendingMessage(
            PendingOutboxEntity(
                ownerUid = currentUserId,
                roomId = room.id,
                roomTitle = room.title,
                envelopeId = envelopeId,
                payloadJson = payload.toString(),
                ttlSeconds = ttlSeconds
            )
        )
    }

    fun toggleOfflineQueueMode(enabled: Boolean) {
        _isOfflineQueueMode.value = enabled
        if (!enabled) flushOfflineOutboxQueue() else banner("Çevrimdışı kuyruk modu açık")
    }

    /** Sends queued messages once each; safe to call concurrently (ids make sends idempotent). */
    fun flushOfflineOutboxQueue() {
        viewModelScope.launch {
            outboxMutex.withLock {
                val pending = vaultDao.getPendingOutboxList(currentUserId)
                if (pending.isEmpty()) {
                    banner("Bekleyen mesaj yok")
                    return@withLock
                }
                val rooms = (roomsRaw.value as? UiState.Success)?.data.orEmpty()
                var sent = 0
                for (item in pending) {
                    val room = rooms.firstOrNull { it.id == item.roomId }
                    if (room == null) {
                        vaultDao.deletePendingMessage(item.localId)
                        continue
                    }
                    val payload = runCatching { JSONObject(item.payloadJson) }.getOrNull()
                    if (payload == null) {
                        vaultDao.deletePendingMessage(item.localId)
                        continue
                    }
                    repository.sendMessage(room, payload, item.ttlSeconds, item.envelopeId)
                        .onSuccess {
                            vaultDao.deletePendingMessage(item.localId)
                            sent++
                        }
                }
                banner("$sent / ${pending.size} bekleyen mesaj gönderildi")
            }
        }
    }

    // ------------------------------------------------------------------ message actions

    fun togglePinMessage(message: DecryptedMessageItem) {
        val room = _selectedRoom.value ?: return
        viewModelScope.launch {
            val result = if (message.isPinned) {
                repository.clearPins(room.id, message.id, room.participantUids.map { "${message.id}_${it}_${AnnotationKind.PIN}" })
            } else {
                repository.setAnnotation(room, message.id, AnnotationKind.PIN, "1")
            }
            result.onFailure { banner("Sabitleme değiştirilemedi: ${it.userMessage()}") }
        }
    }

    /** Stars are private to this device. */
    fun toggleStarMessage(message: DecryptedMessageItem) {
        viewModelScope.launch {
            vaultDao.updateMessageStarred(currentUserId, message.id, !message.isStarred)
            banner(if (!message.isStarred) "Mesaj yıldızlandı ⭐" else "Yıldız kaldırıldı")
        }
    }

    fun voteInPoll(message: DecryptedMessageItem, optionIndex: Int) {
        val room = _selectedRoom.value ?: return
        viewModelScope.launch {
            repository.setAnnotation(room, message.id, AnnotationKind.VOTE, optionIndex.toString())
                .onFailure { banner("Oy verilemedi: ${it.userMessage()}") }
        }
    }

    fun toggleMessageReaction(message: DecryptedMessageItem, emoji: String) {
        val room = _selectedRoom.value ?: return
        val mine = message.reactions[myHandle]
        viewModelScope.launch {
            repository.setAnnotation(room, message.id, AnnotationKind.REACTION, if (mine == emoji) null else emoji)
                .onFailure { banner("Tepki eklenemedi: ${it.userMessage()}") }
        }
    }

    /** Deletes one of my messages for everyone. */
    fun burnMessageNow(messageId: String) {
        viewModelScope.launch {
            repository.deleteMessage(messageId)
                .onSuccess {
                    vaultDao.deleteCachedMessages(listOf(messageId))
                    banner("Mesaj silindi")
                }
                .onFailure { banner("Yalnızca kendi mesajlarınızı silebilirsiniz") }
        }
    }

    /** Deletes all messages I sent in the open room and hides older history locally. */
    fun nukeActiveRoomHistory() {
        val room = _selectedRoom.value ?: return
        viewModelScope.launch {
            vaultDao.deleteCachedMessagesForRoom(currentUserId, room.id)
            repository.deleteMyMessages(room.id)
                .onSuccess { count ->
                    vaultDao.hideRoom(HiddenRoomEntity(ownerUid = currentUserId, roomId = room.id))
                    banner("Gönderdiğiniz $count mesaj silindi, geçmiş bu cihazda temizlendi")
                }
                .onFailure { banner("Geçmiş temizlenemedi: ${it.userMessage()}") }
        }
    }

    fun notifySecureClipboardCopied() = banner("📋 Panoya kopyalandı (30 sn sonra temizlenecek)")

    // ------------------------------------------------------------------ scheduled messages

    private val scheduleWakeup = Channel<Unit>(Channel.CONFLATED)

    private suspend fun refreshScheduledWork() {
        val next = runCatching { vaultDao.nextScheduledAt(currentUserId) }.getOrNull()
        onScheduledMessagesChanged(next)
    }

    fun scheduleEncryptedMessage(plaintext: String, delaySeconds: Long, ttlOverride: Long?) {
        val room = _selectedRoom.value ?: return
        val clean = plaintext.trim()
        if (clean.isEmpty()) return
        val target = System.currentTimeMillis() + delaySeconds.coerceAtLeast(5L) * 1000L
        viewModelScope.launch {
            vaultDao.insertScheduledEnvelope(
                ScheduledEnvelopeEntity(
                    ownerUid = currentUserId,
                    roomId = room.id,
                    roomTitle = room.title,
                    envelopeId = ZeroChatRepository.newId("env_"),
                    payloadJson = payload("TEXT", clean, JSONObject()).toString(),
                    plaintext = clean,
                    ttlSeconds = ttlOverride ?: room.defaultTtlSeconds,
                    scheduledAtMillis = target
                )
            )
            scheduleWakeup.trySend(Unit)
            refreshScheduledWork()
            banner("⏳ Mesaj $delaySeconds sn sonra gönderilecek")
        }
    }

    fun cancelScheduledMessage(id: Long) {
        viewModelScope.launch {
            vaultDao.deleteScheduledEnvelope(id)
            refreshScheduledWork()
            banner("Zamanlanmış mesaj iptal edildi")
        }
    }

    /** Sleeps until the next scheduled message is due instead of polling every second. */
    private fun startScheduledDispatcher() {
        viewModelScope.launch {
            while (true) {
                val next = runCatching { vaultDao.nextScheduledAt(currentUserId) }.getOrNull()
                if (next == null) {
                    scheduleWakeup.receive()
                    continue
                }
                val wait = next - System.currentTimeMillis()
                if (wait > 0) {
                    kotlinx.coroutines.withTimeoutOrNull(wait) { scheduleWakeup.receive() }
                    continue
                }
                dispatchDueScheduled()
                delay(1000L)
            }
        }
    }

    private suspend fun dispatchDueScheduled() {
        val due = runCatching { vaultDao.getDueScheduledEnvelopes(currentUserId, System.currentTimeMillis()) }.getOrDefault(emptyList())
        val rooms = (roomsRaw.value as? UiState.Success)?.data.orEmpty()
        for (item in due) {
            val room = rooms.firstOrNull { it.id == item.roomId }
            val payload = runCatching { JSONObject(item.payloadJson) }.getOrNull()
            if (room == null || payload == null) {
                // Rooms may not be loaded yet right after start; retry later unless the data is broken.
                if (payload == null) vaultDao.deleteScheduledEnvelope(item.id)
                continue
            }
            repository.sendMessage(room, payload, item.ttlSeconds, item.envelopeId)
                .onSuccess {
                    vaultDao.deleteScheduledEnvelope(item.id)
                    banner("⏳ Zamanlanmış mesaj gönderildi: ${item.roomTitle}")
                }
                .onFailure { Log.w(TAG, "scheduled send failed, will retry", it) }
        }
        refreshScheduledWork()
    }

    // ------------------------------------------------------------------ statuses

    fun publishStatusStory(text: String, colorHex: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            val rooms = (roomsRaw.value as? UiState.Success)?.data.orEmpty().filter { it.isApprovedConnection }
            val audienceUids = rooms.flatMap { it.participantUids }.toSet() - currentUserId
            val audience = audienceUids.mapNotNull { profilesByUid.value[it] ?: repository.getProfile(it) }
            repository.postStatus(text, colorHex, audience)
                .onSuccess { banner("Durum ${audience.size} kişiyle şifreli olarak paylaşıldı") }
                .onFailure { banner("Durum paylaşılamadı: ${it.userMessage()}") }
        }
    }

    fun onViewStatusStory(status: DecryptedStatusItem) {
        if (status.userId == currentUserId) return
        viewModelScope.launch { repository.markStatusViewed(status.id) }
    }

    fun replyToStatusStory(status: DecryptedStatusItem, replyText: String) {
        val clean = replyText.trim()
        if (clean.isEmpty() || status.userId == currentUserId) return
        val room = (roomsRaw.value as? UiState.Success)?.data?.firstOrNull {
            !it.isGroup && it.isApprovedConnection && it.participantUids.contains(status.userId)
        }
        if (room == null) {
            banner("Duruma yanıt için önce @${status.authorUsername} ile sohbetiniz olmalı")
            return
        }
        val meta = JSONObject().apply {
            put("replyToSender", status.authorUsername)
            put("replyToPreview", "📖 Durum: ${status.decryptedContent.take(60)}")
        }
        dispatchOrQueue(room, payload("TEXT", clean, meta), room.defaultTtlSeconds) {
            banner("@${status.authorUsername} durumuna yanıt gönderildi")
        }
    }

    // ------------------------------------------------------------------ calls

    /** Starts a call with the peer of an existing, accepted direct conversation. */
    fun startP2PCall(calleeUid: String, calleeUsername: String, callType: String) {
        val room = (roomsRaw.value as? UiState.Success)?.data?.firstOrNull {
            !it.isGroup && it.participantUids.contains(calleeUid) && it.participantUids.contains(currentUserId)
        }
        if (room == null) {
            banner("@$calleeUsername ile aramak için önce sohbet başlatın")
            return
        }
        viewModelScope.launch {
            repository.startCall(room, callType)
                .onSuccess {
                    outgoingSeq = 0L
                    activeCallId.value = it.id
                }
                .onFailure { banner("Arama başlatılamadı: ${it.userMessage()}") }
        }
    }

    fun acceptIncomingCall(call: CallSignal) {
        dismissedCallIds.add(call.id)
        viewModelScope.launch {
            repository.setCallStatus(call.id, CallStatus.CONNECTED)
                .onSuccess {
                    outgoingSeq = 0L
                    activeCallId.value = call.id
                }
                .onFailure { banner("Arama yanıtlanamadı: ${it.userMessage()}") }
        }
    }

    fun declineIncomingCall(call: CallSignal) {
        dismissedCallIds.add(call.id)
        viewModelScope.launch { repository.setCallStatus(call.id, CallStatus.ENDED) }
    }

    fun pushActiveCallMediaChunk(audioB64: String? = null, videoB64: String? = null) {
        val call = activeCallDoc.value ?: return
        if (call.status != CallStatus.CONNECTED) return
        val seq = ++outgoingSeq
        viewModelScope.launch(Dispatchers.Default) { repository.pushCallMedia(call, seq, audioB64, videoB64) }
    }

    fun endActiveCall() {
        val id = activeCallId.value ?: return
        activeCallId.value = null
        viewModelScope.launch { repository.setCallStatus(id, CallStatus.ENDED) }
    }

    // ------------------------------------------------------------------ vault, PIN & panic mode

    /** Returns UNLOCKED, PANIC, INVALID, NOT_SET or LOCKED_OUT. */
    fun verifyPinInput(pinInput: String): String {
        val cfg = securityConfig.value
        if (cfg == null || !cfg.hasPins) {
            banner("Önce Profil ve Kasa sekmesinden normal ve panik PIN'lerini belirleyin")
            return "NOT_SET"
        }
        val now = System.currentTimeMillis()
        if (cfg.lockoutUntilMillis > now) {
            banner("Çok fazla hatalı deneme. ${(cfg.lockoutUntilMillis - now) / 1000 + 1} sn bekleyin")
            return "LOCKED_OUT"
        }
        val pin = pinInput.trim()
        return when {
            CryptoEngine.verifyPin(pin, cfg.pinSalt, cfg.panicPinHash) -> {
                resetPinAttempts(cfg)
                _isPanicModeActive.value = true
                _isVaultUnlocked.value = false
                closeRoom()
                endActiveCall()
                "PANIC"
            }
            CryptoEngine.verifyPin(pin, cfg.pinSalt, cfg.normalPinHash) -> {
                resetPinAttempts(cfg)
                _isPanicModeActive.value = false
                _isVaultUnlocked.value = true
                banner("Gizli kasa açıldı")
                "UNLOCKED"
            }
            else -> {
                val attempts = cfg.failedAttempts + 1
                val lockout = if (attempts >= MAX_PIN_ATTEMPTS) {
                    now + (30_000L shl (attempts - MAX_PIN_ATTEMPTS).coerceAtMost(8))
                } else {
                    0L
                }
                viewModelScope.launch {
                    vaultDao.saveSecurityConfig(cfg.copy(failedAttempts = attempts, lockoutUntilMillis = lockout))
                }
                banner("Hatalı PIN")
                "INVALID"
            }
        }
    }

    private fun resetPinAttempts(cfg: VaultSecurityConfigEntity) {
        if (cfg.failedAttempts == 0 && cfg.lockoutUntilMillis == 0L) return
        viewModelScope.launch { vaultDao.saveSecurityConfig(cfg.copy(failedAttempts = 0, lockoutUntilMillis = 0L)) }
    }

    /** Biometrics open the vault but never leave panic mode (that needs the normal PIN). */
    fun unlockVaultWithBiometrics() {
        val cfg = securityConfig.value
        if (cfg?.biometricUnlockEnabled == false) {
            banner("Biyometrik kilit açma kapalı")
            return
        }
        if (_isPanicModeActive.value) return
        _isVaultUnlocked.value = true
        banner("Gizli kasa açıldı 🔓")
    }

    /** Locks the vault. Panic mode is only left by entering the normal PIN. */
    fun lockVaultAndExitPanicMode() {
        _isVaultUnlocked.value = false
    }

    fun onAppBackgrounded() {
        if (securityConfig.value?.autoLockOnBackground != false) _isVaultUnlocked.value = false
        viewModelScope.launch { repository.setPresence(false) }
    }

    fun onAppForegrounded() {
        viewModelScope.launch { repository.setPresence(true) }
    }

    fun updateVaultPins(newNormalPin: String, newPanicPin: String) {
        val normal = newNormalPin.trim()
        val panic = newPanicPin.trim()
        if (normal.length < CryptoEngine.MIN_PIN_LENGTH || panic.length < CryptoEngine.MIN_PIN_LENGTH ||
            !normal.all { it.isDigit() } || !panic.all { it.isDigit() }
        ) {
            banner("PIN'ler en az ${CryptoEngine.MIN_PIN_LENGTH} haneli rakamlardan oluşmalı")
            return
        }
        if (normal == panic) {
            banner("Normal PIN ve panik PIN farklı olmalı")
            return
        }
        viewModelScope.launch {
            val salt = CryptoEngine.newSaltB64()
            val (normalHash, panicHash) = withContext(Dispatchers.Default) {
                CryptoEngine.hashPin(normal, salt) to CryptoEngine.hashPin(panic, salt)
            }
            val existing = vaultDao.getSecurityConfig(currentUserId) ?: VaultSecurityConfigEntity(ownerUid = currentUserId)
            vaultDao.saveSecurityConfig(
                existing.copy(
                    pinSalt = salt,
                    normalPinHash = normalHash,
                    panicPinHash = panicHash,
                    failedAttempts = 0,
                    lockoutUntilMillis = 0L,
                    updatedAtMillis = System.currentTimeMillis()
                )
            )
            banner("Kasa PIN'leri kaydedildi")
        }
    }

    private fun updateConfig(message: String, transform: (VaultSecurityConfigEntity) -> VaultSecurityConfigEntity) {
        viewModelScope.launch {
            val existing = vaultDao.getSecurityConfig(currentUserId) ?: VaultSecurityConfigEntity(ownerUid = currentUserId)
            vaultDao.saveSecurityConfig(transform(existing).copy(updatedAtMillis = System.currentTimeMillis()))
            banner(message)
        }
    }

    fun toggleScreenShield(enabled: Boolean) =
        updateConfig(if (enabled) "Ekran görüntüsü engeli açık" else "Ekran görüntüsü engeli kapalı") { it.copy(screenShieldEnabled = enabled) }

    fun toggleBiometricUnlock(enabled: Boolean) =
        updateConfig(if (enabled) "Biyometrik kilit açma açık" else "Biyometrik kilit açma kapalı") { it.copy(biometricUnlockEnabled = enabled) }

    fun toggleAutoLockOnBackground(enabled: Boolean) =
        updateConfig(if (enabled) "Arka planda otomatik kilit açık" else "Arka planda otomatik kilit kapalı") { it.copy(autoLockOnBackground = enabled) }

    // ------------------------------------------------------------------ backups

    fun exportEncryptedBackup(password: String) {
        if (password.length < CryptoEngine.MIN_BACKUP_PASSWORD_LENGTH) {
            banner("Yedek parolası en az ${CryptoEngine.MIN_BACKUP_PASSWORD_LENGTH} karakter olmalı")
            return
        }
        viewModelScope.launch {
            // Self-destructing messages are never exported.
            val messages = vaultDao.getBackupableMessages(currentUserId)
            val arr = JSONArray()
            messages.forEach { m ->
                arr.put(JSONObject().apply {
                    put("messageId", m.messageId)
                    put("roomId", m.roomId)
                    put("roomTitle", m.roomTitle)
                    put("senderUsername", m.senderUsername)
                    put("decryptedText", m.decryptedText)
                    put("messageType", m.messageType)
                    put("isStarred", m.isStarred)
                    put("createdAtMillis", m.createdAtMillis)
                })
            }
            val root = JSONObject().apply {
                put("ownerUid", currentUserId)
                put("exportedAtMillis", System.currentTimeMillis())
                put("messages", arr)
            }.toString()
            val encrypted = withContext(Dispatchers.Default) { CryptoEngine.encryptBackupArchive(root, password) }
            _exportedVaultBackupText.value = encrypted
            banner("${messages.size} mesaj parolayla şifrelenerek dışa aktarıldı")
        }
    }

    fun clearExportedBackup() {
        _exportedVaultBackupText.value = null
    }

    fun importEncryptedBackup(vaultPayload: String, password: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                CryptoEngine.decryptBackupArchive(vaultPayload, password).mapCatching { json ->
                    val arr = JSONObject(json).optJSONArray("messages") ?: JSONArray()
                    (0 until arr.length()).map { i ->
                        val o = arr.getJSONObject(i)
                        CachedVaultMessageEntity(
                            messageId = o.optString("messageId", "restored_$i"),
                            ownerUid = currentUserId,
                            roomId = o.optString("roomId"),
                            roomTitle = o.optString("roomTitle", "Yedek"),
                            senderUsername = o.optString("senderUsername"),
                            decryptedText = o.optString("decryptedText"),
                            messageType = o.optString("messageType", "TEXT"),
                            isStarred = o.optBoolean("isStarred", false),
                            createdAtMillis = o.optLong("createdAtMillis", System.currentTimeMillis())
                        )
                    }
                }
            }
            result
                .onSuccess {
                    vaultDao.cacheDecryptedMessages(it)
                    banner("Yedekten ${it.size} mesaj geri yüklendi")
                }
                .onFailure { banner("Yedek açılamadı: parola yanlış veya dosya bozuk") }
        }
    }

    // ------------------------------------------------------------------ sign-out

    /** Ends calls, marks the user offline and wipes decrypted local data before signing out. */
    fun prepareSignOut(onDone: () -> Unit) {
        viewModelScope.launch {
            endActiveCall()
            runCatching { repository.setPresence(false) }
            vaultDao.clearCachedMessages(currentUserId)
            vaultDao.clearOutbox(currentUserId)
            vaultDao.clearScheduled(currentUserId)
            onScheduledMessagesChanged(null)
            _exportedVaultBackupText.value = null
            repository.clearCaches()
            onDone()
        }
    }

    @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
    override fun onCleared() {
        activeCallId.value?.let { id ->
            // Best effort: the ViewModel scope is gone, so use a detached call.
            kotlinx.coroutines.GlobalScope.launch { runCatching { repository.setCallStatus(id, CallStatus.ENDED) } }
        }
        super.onCleared()
    }

    companion object {
        private const val TAG = "ZeroChatVM"
        private const val TYPING_WINDOW_MS = 6_000L
        private const val TYPING_THROTTLE_MS = 3_000L
        private const val RING_TIMEOUT_MS = 45_000L
        private const val DAY_MS = 24L * 3600L * 1000L
        private const val MAX_PIN_ATTEMPTS = 5
        /** Base64 budget per media message (encrypted payload stays under the 700 KB rule limit). */
        const val MAX_MEDIA_B64 = 480_000
        const val MAX_FILE_KB = 350
        const val MAX_VOICE_SECONDS = 120

        fun parsePollOptions(meta: String): Pair<String, List<PollOptionItem>> = try {
            val obj = JSONObject(meta)
            val arr = obj.optJSONArray("options") ?: JSONArray()
            obj.optString("question", "Anket") to (0 until arr.length()).map { i ->
                val item = arr.getJSONObject(i)
                val votersArr = item.optJSONArray("voters") ?: JSONArray()
                PollOptionItem(
                    index = item.optInt("index", i),
                    text = item.optString("text"),
                    voterNames = (0 until votersArr.length()).map { votersArr.getString(it) }
                )
            }
        } catch (_: Exception) {
            "Anket" to emptyList()
        }
    }
}
