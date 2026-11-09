package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.crypto.CryptoEngine
import com.example.data.model.CallSignal
import com.example.data.model.ChatRoom
import com.example.data.model.EncryptedEnvelope
import com.example.data.model.EncryptedStatus
import com.example.data.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

class ZeroChatRepository(
    private val db: FirebaseFirestore,
    private val fallbackUserId: String = ""
) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun isLocalNodeMode(): Boolean {
        return auth.currentUser == null && fallbackUserId.isNotBlank()
    }

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: fallbackUserId.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    // Reactive in-memory state for Local Hardware Node fallback mode
    private val localUsersFlow = MutableStateFlow<List<UserProfile>>(
        listOf(
            UserProfile(
                id = "peer_siber_analist",
                userId = "peer_siber_analist",
                username = "siber_analist",
                displayName = "Siber Analist Düğümü",
                bio = "Zero-Knowledge Denetçisi • ECDH P-256",
                publicKey = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE9xCyberKey9912==",
                safetyFingerprint = "88412 90341 77219 00418 55120 66391",
                onlineStatus = "ONLINE",
                createdAt = Timestamp.now(),
                updatedAt = Timestamp.now()
            ),
            UserProfile(
                id = "peer_kripto_kalkan",
                userId = "peer_kripto_kalkan",
                username = "kripto_kalkan",
                displayName = "Kripto Kalkan",
                bio = "AES-256-GCM Uçtan Uca Güvenlik Uzmanı",
                publicKey = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE77ShieldKey4410==",
                safetyFingerprint = "19283 74650 91827 36455 10293 84756",
                onlineStatus = "ONLINE",
                createdAt = Timestamp.now(),
                updatedAt = Timestamp.now()
            ),
            UserProfile(
                id = "peer_gece_gozcusu",
                userId = "peer_gece_gozcusu",
                username = "gece_gozcusu",
                displayName = "Gece Gözcüsü",
                bio = "Steganografi & P2P Tünel Operatörü",
                publicKey = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE33NightKey1188==",
                safetyFingerprint = "55443 22119 88776 44332 99001 23456",
                onlineStatus = "ONLINE",
                createdAt = Timestamp.now(),
                updatedAt = Timestamp.now()
            )
        )
    )
    private val localRoomsFlow = MutableStateFlow<List<ChatRoom>>(emptyList())
    private val deletedRoomIdsFlow = MutableStateFlow<Set<String>>(emptySet())
    private val localEnvelopesFlow = MutableStateFlow<List<EncryptedEnvelope>>(emptyList())
    private val localStatusesFlow = MutableStateFlow<List<EncryptedStatus>>(
        run {
            val enc = CryptoEngine.encrypt(
                "🛡️ Sıfır-Bilgi (Zero-Knowledge) P2P şifreli ağ düğümü aktif. Tüm zarflar AES-256-GCM ile korunmaktadır.",
                "ZEROCHAT_PUBLIC_STATUS_STREAM"
            )
            listOf(
                EncryptedStatus(
                    id = "status_seed_1",
                    userId = "peer_siber_analist",
                    authorUsername = "siber_analist",
                    ciphertext = enc.ciphertext,
                    iv = enc.iv,
                    themeColorHex = "#00E676",
                    viewCount = 4L,
                    createdAt = Timestamp.now(),
                    updatedAt = Timestamp.now()
                )
            )
        }
    )
    private val localCallsFlow = MutableStateFlow<List<CallSignal>>(emptyList())

    fun registerDiscoveredPeer(peer: UserProfile) {
        val cleanHandle = peer.username.trim().removePrefix("@").lowercase(Locale.ROOT)
        if (cleanHandle.isBlank()) return
        localUsersFlow.value = localUsersFlow.value.filterNot {
            it.userId == peer.userId || it.username.lowercase(Locale.ROOT) == cleanHandle
        } + peer.copy(username = cleanHandle)
    }

    suspend fun registerOrUpdateProfile(
        rawUsername: String,
        displayName: String,
        bio: String,
        onlineStatus: String = "ONLINE"
    ): Result<UserProfile> = runCatching {
        val uid = requireUserId()
        val sanitizedUsername = rawUsername.trim()
            .lowercase(Locale.ROOT)
            .replace("[^a-z0-9_\\-]".toRegex(), "")
            .let { if (it.length < 3) "${it}_node" else it.take(28) }

        val hwStatus = CryptoEngine.getOrCreateHardwareIdentity(uid, sanitizedUsername)
        val pubKey = hwStatus.publicKeyBase64
        val fingerprint = hwStatus.safetyFingerprint

        val localProfile = UserProfile(
            id = uid,
            userId = uid,
            username = sanitizedUsername,
            displayName = displayName.trim().ifBlank { sanitizedUsername },
            bio = bio.trim().ifBlank { "ECDH P-256 + AES-256-GCM Doğrulanmış Düğüm" }.take(200),
            publicKey = pubKey,
            safetyFingerprint = fingerprint,
            onlineStatus = onlineStatus.take(32),
            createdAt = Timestamp.now(),
            updatedAt = Timestamp.now()
        )

        if (isLocalNodeMode()) {
            localUsersFlow.value = localUsersFlow.value.filterNot { it.userId == uid } + localProfile
            return@runCatching localProfile
        }

        val docRef = db.collection("users").document(uid)
        val existingSnap = try {
            docRef.get().await()
        } catch (e: Exception) {
            null
        }

        if (existingSnap != null && existingSnap.exists()) {
            val updatePayload = mapOf(
                "username" to sanitizedUsername,
                "displayName" to displayName.trim().ifBlank { sanitizedUsername },
                "bio" to bio.trim().take(200),
                "publicKey" to pubKey,
                "safetyFingerprint" to fingerprint,
                "onlineStatus" to onlineStatus.take(32),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            try {
                docRef.update(updatePayload).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.UPDATE, docRef.path)
                throw e
            }
        } else {
            val createPayload = mapOf(
                "userId" to uid,
                "username" to sanitizedUsername,
                "displayName" to displayName.trim().ifBlank { sanitizedUsername },
                "bio" to bio.trim().ifBlank { "ECDH P-256 + AES-256-GCM Doğrulanmış Düğüm" }.take(200),
                "publicKey" to pubKey,
                "safetyFingerprint" to fingerprint,
                "onlineStatus" to onlineStatus.take(32),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            try {
                docRef.set(createPayload).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.CREATE, docRef.path)
                throw e
            }
        }

        val updatedDoc = docRef.get().await()
        updatedDoc.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            ?: localProfile
    }

    suspend fun updatePresenceOrTypingStatus(statusLabel: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        if (isLocalNodeMode()) {
            localUsersFlow.value = localUsersFlow.value.map {
                if (it.userId == uid) it.copy(onlineStatus = statusLabel.take(32), updatedAt = Timestamp.now()) else it
            }
            return@runCatching
        }
        val docRef = db.collection("users").document(uid)
        try {
            docRef.update(
                mapOf(
                    "onlineStatus" to statusLabel.take(32),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    fun observeDirectoryUsers(): Flow<List<UserProfile>> = flow {
        val uid = requireUserId()
        if (isLocalNodeMode()) {
            emitAll(localUsersFlow)
            return@flow
        }
        val path = "users"
        emitAll(
            combine(
                db.collection("users").snapshots(),
                localUsersFlow
            ) { snapshot, localList ->
                val cloudUsers = snapshot.toObjects(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                val cloudIds = cloudUsers.map { it.userId }.toSet()
                val cloudHandles = cloudUsers.map { it.username.lowercase(Locale.ROOT) }.toSet()
                val seedPeers = localList.filter {
                    it.userId !in cloudIds && it.username.lowercase(Locale.ROOT) !in cloudHandles
                }
                cloudUsers + seedPeers
            }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (fallbackUserId.isNotBlank()) {
                        emitAll(localUsersFlow)
                    } else {
                        val exceptionToLog = firestoreError ?: (error as? Exception)
                        if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                            handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                        }
                        throw error
                    }
                }
        )
    }

    fun observeUserRooms(userId: String): Flow<List<ChatRoom>> = flow {
        if (isLocalNodeMode()) {
            emitAll(
                combine(localRoomsFlow, deletedRoomIdsFlow) { list, deletedIds ->
                    list.filter { it.participantUids.contains(userId) && it.id !in deletedIds }
                        .sortedByDescending { (it.updatedAt ?: it.createdAt)?.toDate()?.time ?: 0L }
                }
            )
            return@flow
        }
        val path = "rooms"
        emitAll(
            combine(
                db.collection("rooms")
                    .whereArrayContains("participantUids", userId)
                    .snapshots(),
                localRoomsFlow,
                deletedRoomIdsFlow
            ) { snapshot, localList, deletedIds ->
                val cloudRooms = snapshot.toObjects(ChatRoom::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                val cloudIds = cloudRooms.map { it.id }.toSet()
                val localExtras = localList.filter { it.id !in cloudIds && it.participantUids.contains(userId) }
                (cloudRooms + localExtras)
                    .filterNot { it.id in deletedIds }
                    .sortedByDescending { (it.updatedAt ?: it.createdAt)?.toDate()?.time ?: 0L }
            }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (fallbackUserId.isNotBlank()) {
                        emitAll(
                            combine(localRoomsFlow, deletedRoomIdsFlow) { list, deletedIds ->
                                list.filterNot { it.id in deletedIds }
                            }
                        )
                    } else {
                        val exceptionToLog = firestoreError ?: (error as? Exception)
                        if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                            handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                        }
                        throw error
                    }
                }
        )
    }

    suspend fun createOrGetDirectRoom(
        myProfile: UserProfile,
        peerProfile: UserProfile
    ): Result<ChatRoom> = runCatching {
        val uid = requireUserId()
        registerDiscoveredPeer(peerProfile)
        val sortedUids = listOf(uid, peerProfile.userId).distinct().sorted()
        val deterministicRoomId = "dm_" + CryptoEngine.sha256Hex(sortedUids.joinToString("_")).take(24)
        deletedRoomIdsFlow.value = deletedRoomIdsFlow.value - deterministicRoomId
        val roomKeyHint = CryptoEngine.computeDirectRoomKeyHint(uid, peerProfile.userId)
        val initialCipher = CryptoEngine.encrypt("ECDH P-256 + AES-256-GCM uçtan uca şifreli tünel kuruldu", roomKeyHint)
        val participantNames = listOf(myProfile.username, peerProfile.username).distinct().joinToString(",")

        val localRoom = ChatRoom(
            id = deterministicRoomId,
            ownerId = uid,
            participantUids = sortedUids,
            participantNames = participantNames,
            title = "@${peerProfile.username}",
            isGroup = false,
            roomKeyHint = roomKeyHint,
            lastCipherPreview = initialCipher.ciphertext,
            lastSenderName = myProfile.username,
            defaultTtlSeconds = 0L,
            createdAt = Timestamp.now(),
            updatedAt = Timestamp.now()
        )

        if (isLocalNodeMode()) {
            val existingLocal = localRoomsFlow.value.firstOrNull { it.id == deterministicRoomId }
            if (existingLocal != null) return@runCatching existingLocal
            localRoomsFlow.value = localRoomsFlow.value + localRoom
            return@runCatching localRoom
        }

        val docRef = db.collection("rooms").document(deterministicRoomId)
        val existing = try {
            docRef.get().await()
        } catch (e: Exception) {
            null
        }

        if (existing != null && existing.exists()) {
            return@runCatching existing.toObject(
                ChatRoom::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )!!
        }

        val payload = mapOf(
            "ownerId" to uid,
            "participantUids" to sortedUids,
            "participantNames" to participantNames,
            "title" to "@${peerProfile.username}",
            "isGroup" to false,
            "roomKeyHint" to roomKeyHint,
            "lastCipherPreview" to initialCipher.ciphertext,
            "lastSenderName" to myProfile.username,
            "defaultTtlSeconds" to 0L,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }

        docRef.get().await().toObject(ChatRoom::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            ?: localRoom
    }

    suspend fun sendDirectConnectionRequest(
        myProfile: UserProfile,
        peerProfile: UserProfile,
        introNote: String = "",
        simulatedIncomingFromPeer: Boolean = false
    ): Result<ChatRoom> = runCatching {
        val uid = requireUserId()
        registerDiscoveredPeer(peerProfile)
        val sortedUids = listOf(uid, peerProfile.userId).distinct().sorted()
        val deterministicRoomId = "dm_" + CryptoEngine.sha256Hex(sortedUids.joinToString("_")).take(24)
        deletedRoomIdsFlow.value = deletedRoomIdsFlow.value - deterministicRoomId

        val baseKeyHint = CryptoEngine.computeDirectRoomKeyHint(uid, peerProfile.userId)
        val requesterHandle = if (simulatedIncomingFromPeer) {
            peerProfile.username.ifBlank { "siber_dugum" }
        } else {
            myProfile.username.ifBlank { "zero_${uid.take(5)}" }
        }
        val pendingKeyHint = "REQ_FROM::${requesterHandle}::$baseKeyHint"
        val previewPlaintext = introNote.trim().ifBlank {
            "🤝 @$requesterHandle şifreli iletişim ve takip isteği gönderdi (Onay Bekleniyor)"
        }
        val initialCipher = CryptoEngine.encrypt(previewPlaintext, pendingKeyHint)
        val participantNames = listOf(myProfile.username, peerProfile.username).distinct().joinToString(",")

        val localRoomObj = ChatRoom(
            id = deterministicRoomId,
            ownerId = uid,
            participantUids = sortedUids,
            participantNames = participantNames,
            title = "@${peerProfile.username}",
            isGroup = false,
            roomKeyHint = pendingKeyHint,
            lastCipherPreview = initialCipher.ciphertext.take(180),
            lastSenderName = requesterHandle,
            defaultTtlSeconds = 0L,
            createdAt = Timestamp.now(),
            updatedAt = Timestamp.now()
        )

        if (isLocalNodeMode()) {
            val existingLocal = localRoomsFlow.value.firstOrNull { it.id == deterministicRoomId }
            if (existingLocal != null && existingLocal.isApprovedConnection && !simulatedIncomingFromPeer) {
                return@runCatching existingLocal
            }
            localRoomsFlow.value = localRoomsFlow.value.filterNot { it.id == deterministicRoomId } + localRoomObj
            return@runCatching localRoomObj
        }

        val docRef = db.collection("rooms").document(deterministicRoomId)
        val existingSnap = try {
            docRef.get().await()
        } catch (e: Exception) {
            null
        }

        val existingRoom = existingSnap?.takeIf { it.exists() }?.toObject(
            ChatRoom::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
        )

        if (existingRoom != null && existingRoom.isApprovedConnection && !simulatedIncomingFromPeer) {
            return@runCatching existingRoom
        }

        if (existingSnap != null && existingSnap.exists()) {
            val updatePayload = mapOf(
                "participantUids" to sortedUids,
                "participantNames" to participantNames,
                "title" to "@${peerProfile.username}",
                "roomKeyHint" to pendingKeyHint,
                "lastCipherPreview" to initialCipher.ciphertext.take(180),
                "lastSenderName" to requesterHandle,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            try {
                docRef.update(updatePayload).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                localRoomsFlow.value = localRoomsFlow.value.filterNot { it.id == deterministicRoomId } + localRoomObj
                return@runCatching localRoomObj
            }
        } else {
            val payload = mapOf(
                "ownerId" to uid,
                "participantUids" to sortedUids,
                "participantNames" to participantNames,
                "title" to "@${peerProfile.username}",
                "isGroup" to false,
                "roomKeyHint" to pendingKeyHint,
                "lastCipherPreview" to initialCipher.ciphertext.take(180),
                "lastSenderName" to requesterHandle,
                "defaultTtlSeconds" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            try {
                docRef.set(payload).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                localRoomsFlow.value = localRoomsFlow.value.filterNot { it.id == deterministicRoomId } + localRoomObj
                return@runCatching localRoomObj
            }
        }

        docRef.get().await().toObject(ChatRoom::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            ?: localRoomObj
    }

    suspend fun approveConnectionRequest(
        room: ChatRoom,
        approverUsername: String
    ): Result<ChatRoom> = runCatching {
        val uid = requireUserId()
        val approvedKeyHint = room.effectiveRoomKeyHint
        val approvedText = "🤝 @$approverUsername iletişim ve takip isteğini onayladı! Uçtan uca şifreli tünel aktif."
        val approvedCipher = CryptoEngine.encrypt(approvedText, approvedKeyHint)
        val updatedRoom = room.copy(
            roomKeyHint = approvedKeyHint,
            lastCipherPreview = approvedCipher.ciphertext.take(180),
            lastSenderName = approverUsername,
            updatedAt = Timestamp.now()
        )

        if (isLocalNodeMode() || localRoomsFlow.value.any { it.id == room.id }) {
            localRoomsFlow.value = localRoomsFlow.value.map { if (it.id == room.id) updatedRoom else it }
            val metaJson = JSONObject().apply {
                put("ratchetStep", 1)
                put("readByUids", JSONArray().put(uid))
            }.toString()
            sendEncryptedMessage(
                room = updatedRoom,
                senderUsername = approverUsername,
                plaintext = approvedText,
                messageType = "TEXT",
                encryptedMeta = metaJson,
                ttlSeconds = updatedRoom.defaultTtlSeconds,
                ratchetStep = 1
            )
            if (isLocalNodeMode()) return@runCatching updatedRoom
        }

        val docRef = db.collection("rooms").document(room.id)
        try {
            docRef.update(
                mapOf(
                    "roomKeyHint" to approvedKeyHint,
                    "lastCipherPreview" to approvedCipher.ciphertext.take(180),
                    "lastSenderName" to approverUsername,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            localRoomsFlow.value = localRoomsFlow.value.filterNot { it.id == room.id } + updatedRoom
            return@runCatching updatedRoom
        }

        val metaJson = JSONObject().apply {
            put("ratchetStep", 1)
            put("readByUids", JSONArray().put(uid))
        }.toString()
        sendEncryptedMessage(
            room = updatedRoom,
            senderUsername = approverUsername,
            plaintext = approvedText,
            messageType = "TEXT",
            encryptedMeta = metaJson,
            ttlSeconds = updatedRoom.defaultTtlSeconds,
            ratchetStep = 1
        )

        updatedRoom
    }

    suspend fun rejectOrCancelConnectionRequest(
        room: ChatRoom,
        actorUsername: String
    ): Result<ChatRoom> = runCatching {
        requireUserId()
        val rejectedKeyHint = "REQ_REJECTED::${actorUsername}::${room.effectiveRoomKeyHint}"
        val rejectedPreview = CryptoEngine.encrypt(
            "⛔ İletişim ve takip isteği @$actorUsername tarafından reddedildi / iptal edildi.",
            rejectedKeyHint
        )
        val updatedRoom = room.copy(
            roomKeyHint = rejectedKeyHint,
            lastCipherPreview = rejectedPreview.ciphertext.take(180),
            lastSenderName = actorUsername,
            updatedAt = Timestamp.now()
        )

        if (isLocalNodeMode() || localRoomsFlow.value.any { it.id == room.id }) {
            localRoomsFlow.value = localRoomsFlow.value.map { if (it.id == room.id) updatedRoom else it }
            if (isLocalNodeMode()) return@runCatching updatedRoom
        }

        val docRef = db.collection("rooms").document(room.id)
        try {
            docRef.update(
                mapOf(
                    "roomKeyHint" to rejectedKeyHint,
                    "lastCipherPreview" to rejectedPreview.ciphertext.take(180),
                    "lastSenderName" to actorUsername,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            localRoomsFlow.value = localRoomsFlow.value.filterNot { it.id == room.id } + updatedRoom
            return@runCatching updatedRoom
        }
        updatedRoom
    }

    suspend fun createGroupRoom(
        myProfile: UserProfile,
        groupTitle: String,
        selectedPeers: List<UserProfile>,
        defaultTtlSeconds: Long = 0L
    ): Result<ChatRoom> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("rooms").document()
        val allParticipants = (listOf(uid) + selectedPeers.map { it.userId }).distinct().take(10)
        val allNames = (listOf(myProfile.username) + selectedPeers.map { it.username }).distinct().joinToString(",")
        val roomKeyHint = "GRP_" + CryptoEngine.sha256Hex("${docRef.id}::$uid").take(24)
        val initialCipher = CryptoEngine.encrypt("Şifreli grup kanalı oluşturuldu", roomKeyHint)
        val cleanedTitle = groupTitle.trim().ifBlank { "Siber Grup" }.take(80)

        val localGroup = ChatRoom(
            id = docRef.id,
            ownerId = uid,
            participantUids = allParticipants,
            participantNames = allNames,
            title = cleanedTitle,
            isGroup = true,
            roomKeyHint = roomKeyHint,
            lastCipherPreview = initialCipher.ciphertext,
            lastSenderName = myProfile.username,
            defaultTtlSeconds = defaultTtlSeconds,
            createdAt = Timestamp.now(),
            updatedAt = Timestamp.now()
        )

        if (isLocalNodeMode()) {
            localRoomsFlow.value = listOf(localGroup) + localRoomsFlow.value
            return@runCatching localGroup
        }

        val payload = mapOf(
            "ownerId" to uid,
            "participantUids" to allParticipants,
            "participantNames" to allNames,
            "title" to cleanedTitle,
            "isGroup" to true,
            "roomKeyHint" to roomKeyHint,
            "lastCipherPreview" to initialCipher.ciphertext,
            "lastSenderName" to myProfile.username,
            "defaultTtlSeconds" to defaultTtlSeconds,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }

        docRef.get().await().toObject(ChatRoom::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            ?: localGroup
    }

    suspend fun updateRoomTtl(roomId: String, ttlSeconds: Long): Result<Unit> = runCatching {
        requireUserId()
        if (isLocalNodeMode() || localRoomsFlow.value.any { it.id == roomId }) {
            localRoomsFlow.value = localRoomsFlow.value.map {
                if (it.id == roomId) it.copy(defaultTtlSeconds = ttlSeconds, updatedAt = Timestamp.now()) else it
            }
            if (isLocalNodeMode()) return@runCatching
        }
        val docRef = db.collection("rooms").document(roomId)
        try {
            docRef.update(
                mapOf(
                    "defaultTtlSeconds" to ttlSeconds,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun updateGroupMembersAndTitle(
        room: ChatRoom,
        newTitle: String,
        selectedPeers: List<UserProfile>,
        myProfile: UserProfile
    ): Result<ChatRoom> = runCatching {
        val uid = requireUserId()
        val allParticipants = (listOf(uid) + selectedPeers.map { it.userId }).distinct().take(10)
        val allNames = (listOf(myProfile.username) + selectedPeers.map { it.username }).distinct().joinToString(",")
        val cleanedTitle = newTitle.trim().ifBlank { room.title }.take(80)
        val updatedRoom = room.copy(
            title = cleanedTitle,
            participantUids = allParticipants,
            participantNames = allNames,
            updatedAt = Timestamp.now()
        )
        if (isLocalNodeMode() || localRoomsFlow.value.any { it.id == room.id }) {
            localRoomsFlow.value = localRoomsFlow.value.map { if (it.id == room.id) updatedRoom else it }
            if (isLocalNodeMode()) return@runCatching updatedRoom
        }
        val docRef = db.collection("rooms").document(room.id)
        try {
            docRef.update(
                mapOf(
                    "title" to cleanedTitle,
                    "participantUids" to allParticipants,
                    "participantNames" to allNames,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
        updatedRoom
    }

    suspend fun rotateRoomEncryptionKey(
        room: ChatRoom,
        senderUsername: String
    ): Result<ChatRoom> = runCatching {
        val uid = requireUserId()
        val newKeyHint = "ROT_" + CryptoEngine.sha256Hex("${room.id}::$uid::${System.currentTimeMillis()}").take(24)
        val rotatedPreview = CryptoEngine.encrypt("🔄 Oda şifreleme anahtarı yenilendi (Key Rotation)", newKeyHint)
        val updatedRoom = room.copy(
            roomKeyHint = newKeyHint,
            lastCipherPreview = rotatedPreview.ciphertext.take(180),
            lastSenderName = senderUsername,
            updatedAt = Timestamp.now()
        )
        if (isLocalNodeMode() || localRoomsFlow.value.any { it.id == room.id }) {
            localRoomsFlow.value = localRoomsFlow.value.map { if (it.id == room.id) updatedRoom else it }
            if (isLocalNodeMode()) return@runCatching updatedRoom
        }
        val docRef = db.collection("rooms").document(room.id)
        try {
            docRef.update(
                mapOf(
                    "roomKeyHint" to newKeyHint,
                    "lastCipherPreview" to rotatedPreview.ciphertext.take(180),
                    "lastSenderName" to senderUsername,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
        updatedRoom
    }

    fun observeRoomEnvelopes(roomId: String, userId: String): Flow<List<EncryptedEnvelope>> = flow {
        if (isLocalNodeMode()) {
            emitAll(
                localEnvelopesFlow.map { list ->
                    list.filter { it.roomId == roomId }
                        .sortedBy { (it.createdAt ?: it.updatedAt)?.toDate()?.time ?: 0L }
                }
            )
            return@flow
        }
        val path = "envelopes"
        emitAll(
            combine(
                db.collection("envelopes")
                    .whereEqualTo("roomId", roomId)
                    .whereArrayContains("participantUids", userId)
                    .snapshots(),
                localEnvelopesFlow
            ) { snapshot, localList ->
                val cloudList = snapshot.toObjects(EncryptedEnvelope::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                val cloudIds = cloudList.map { it.id }.toSet()
                val localExtras = localList.filter { it.roomId == roomId && it.id !in cloudIds }
                (cloudList + localExtras).sortedBy { (it.createdAt ?: it.updatedAt)?.toDate()?.time ?: 0L }
            }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (fallbackUserId.isNotBlank()) {
                        emitAll(localEnvelopesFlow.map { list -> list.filter { it.roomId == roomId } })
                    } else {
                        val exceptionToLog = firestoreError ?: (error as? Exception)
                        if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                            handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                        }
                        throw error
                    }
                }
        )
    }

    suspend fun sendEncryptedMessage(
        room: ChatRoom,
        senderUsername: String,
        plaintext: String,
        messageType: String = "TEXT",
        encryptedMeta: String = "{}",
        ttlSeconds: Long = room.defaultTtlSeconds,
        ratchetStep: Int = 0
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val encrypted = CryptoEngine.encrypt(plaintext, room.roomKeyHint, ratchetStep)
        val envId = "env_" + UUID.randomUUID().toString().replace("-", "").take(20)
        val localEnvelope = EncryptedEnvelope(
            id = envId,
            roomId = room.id,
            senderId = uid,
            senderUsername = senderUsername,
            participantUids = room.participantUids,
            ciphertext = encrypted.ciphertext,
            iv = encrypted.iv,
            signatureHash = encrypted.signatureHash,
            messageType = messageType,
            encryptedMeta = encryptedMeta.take(8000),
            reactionsSummary = "",
            ttlSeconds = ttlSeconds,
            createdAt = Timestamp.now(),
            updatedAt = Timestamp.now()
        )

        if (isLocalNodeMode() || localRoomsFlow.value.any { it.id == room.id }) {
            localEnvelopesFlow.value = localEnvelopesFlow.value + localEnvelope
            localRoomsFlow.value = localRoomsFlow.value.map {
                if (it.id == room.id) {
                    it.copy(
                        lastCipherPreview = encrypted.ciphertext.take(180),
                        lastSenderName = senderUsername,
                        updatedAt = Timestamp.now()
                    )
                } else it
            }
            if (isLocalNodeMode()) return@runCatching envId
        }

        val envRef = db.collection("envelopes").document()
        val payload = mapOf(
            "roomId" to room.id,
            "senderId" to uid,
            "senderUsername" to senderUsername,
            "participantUids" to room.participantUids,
            "ciphertext" to encrypted.ciphertext,
            "iv" to encrypted.iv,
            "signatureHash" to encrypted.signatureHash,
            "messageType" to messageType,
            "encryptedMeta" to encryptedMeta.take(8000),
            "reactionsSummary" to "",
            "ttlSeconds" to ttlSeconds,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            envRef.set(payload).await()
            db.collection("rooms").document(room.id).update(
                mapOf(
                    "lastCipherPreview" to encrypted.ciphertext.take(180),
                    "lastSenderName" to senderUsername,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (fallbackUserId.isNotBlank()) {
                return@runCatching envId
            }
            handleFirestoreError(e, OperationType.CREATE, envRef.path)
            throw e
        }
        envRef.id
    }

    suspend fun editEncryptedMessage(
        envelopeId: String,
        roomKeyHint: String,
        newPlaintext: String,
        existingMetaJson: String,
        ratchetStep: Int = 0
    ): Result<Unit> = runCatching {
        requireUserId()
        val encrypted = CryptoEngine.encrypt(newPlaintext, roomKeyHint, ratchetStep)
        val metaObj = try {
            JSONObject(existingMetaJson)
        } catch (_: Exception) {
            JSONObject()
        }.apply {
            put("isEdited", true)
            put("ratchetStep", ratchetStep)
        }
        val newMetaStr = metaObj.toString().take(8000)
        if (isLocalNodeMode() || localEnvelopesFlow.value.any { it.id == envelopeId }) {
            localEnvelopesFlow.value = localEnvelopesFlow.value.map {
                if (it.id == envelopeId) {
                    it.copy(
                        ciphertext = encrypted.ciphertext,
                        iv = encrypted.iv,
                        signatureHash = encrypted.signatureHash,
                        encryptedMeta = newMetaStr,
                        updatedAt = Timestamp.now()
                    )
                } else it
            }
            if (isLocalNodeMode()) return@runCatching
        }
        val docRef = db.collection("envelopes").document(envelopeId)
        try {
            docRef.update(
                mapOf(
                    "ciphertext" to encrypted.ciphertext,
                    "iv" to encrypted.iv,
                    "signatureHash" to encrypted.signatureHash,
                    "encryptedMeta" to newMetaStr,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun updateEnvelopeReaction(
        envelopeId: String,
        updatedReactionsSummary: String
    ): Result<Unit> = runCatching {
        requireUserId()
        if (isLocalNodeMode() || localEnvelopesFlow.value.any { it.id == envelopeId }) {
            localEnvelopesFlow.value = localEnvelopesFlow.value.map {
                if (it.id == envelopeId) it.copy(reactionsSummary = updatedReactionsSummary.take(2000)) else it
            }
            if (isLocalNodeMode()) return@runCatching
        }
        val docRef = db.collection("envelopes").document(envelopeId)
        try {
            docRef.update(
                mapOf(
                    "reactionsSummary" to updatedReactionsSummary.take(2000),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun updateEnvelopeMeta(
        envelopeId: String,
        updatedMeta: String
    ): Result<Unit> = runCatching {
        requireUserId()
        if (isLocalNodeMode() || localEnvelopesFlow.value.any { it.id == envelopeId }) {
            localEnvelopesFlow.value = localEnvelopesFlow.value.map {
                if (it.id == envelopeId) it.copy(encryptedMeta = updatedMeta.take(8000)) else it
            }
            if (isLocalNodeMode()) return@runCatching
        }
        val docRef = db.collection("envelopes").document(envelopeId)
        try {
            docRef.update(
                mapOf(
                    "encryptedMeta" to updatedMeta.take(8000),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun burnEnvelope(envelopeId: String): Result<Unit> = runCatching {
        requireUserId()
        if (isLocalNodeMode() || localEnvelopesFlow.value.any { it.id == envelopeId }) {
            localEnvelopesFlow.value = localEnvelopesFlow.value.filterNot { it.id == envelopeId }
            if (isLocalNodeMode()) return@runCatching
        }
        val docRef = db.collection("envelopes").document(envelopeId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    suspend fun nukeRoomHistory(room: ChatRoom, senderUsername: String): Result<Int> = runCatching {
        val uid = requireUserId()
        val localCount = localEnvelopesFlow.value.count { it.roomId == room.id }
        localEnvelopesFlow.value = localEnvelopesFlow.value.filterNot { it.roomId == room.id }
        val nukedPreview = CryptoEngine.encrypt("☢️ Sohbet geçmişi tamamen imha edildi (Nuke)", room.roomKeyHint)
        localRoomsFlow.value = localRoomsFlow.value.map {
            if (it.id == room.id) {
                it.copy(
                    lastCipherPreview = nukedPreview.ciphertext.take(180),
                    lastSenderName = senderUsername,
                    updatedAt = Timestamp.now()
                )
            } else it
        }
        if (isLocalNodeMode()) {
            return@runCatching localCount
        }

        val snap = try {
            db.collection("envelopes")
                .whereEqualTo("roomId", room.id)
                .whereArrayContains("participantUids", uid)
                .get()
                .await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return@runCatching localCount
        }

        var deletedCount = localCount
        for (doc in snap.documents) {
            try {
                doc.reference.delete().await()
                deletedCount++
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }

        val roomRef = db.collection("rooms").document(room.id)
        try {
            roomRef.update(
                mapOf(
                    "lastCipherPreview" to nukedPreview.ciphertext.take(180),
                    "lastSenderName" to senderUsername,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (_: Exception) {
        }

        deletedCount
    }

    fun observeStatuses(): Flow<List<EncryptedStatus>> = flow {
        val uid = requireUserId()
        if (isLocalNodeMode()) {
            emitAll(localStatusesFlow)
            return@flow
        }
        val path = "statuses"
        emitAll(
            db.collection("statuses")
                .snapshots()
                .map { snapshot ->
                    val cloudList = snapshot.toObjects(EncryptedStatus::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    val cloudIds = cloudList.map { it.id }.toSet()
                    val localExtras = localStatusesFlow.value.filter { it.id !in cloudIds }
                    (cloudList + localExtras).sortedByDescending { (it.createdAt ?: it.updatedAt)?.toDate()?.time ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (fallbackUserId.isNotBlank()) {
                        emitAll(localStatusesFlow)
                    } else {
                        val exceptionToLog = firestoreError ?: (error as? Exception)
                        if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                            handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                        }
                        throw error
                    }
                }
        )
    }

    suspend fun postEncryptedStatus(
        authorUsername: String,
        plaintextStatus: String,
        themeColorHex: String
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val encrypted = CryptoEngine.encrypt(plaintextStatus, "ZEROCHAT_PUBLIC_STATUS_STREAM")
        val localId = "st_" + UUID.randomUUID().toString().take(16)
        val localSt = EncryptedStatus(
            id = localId,
            userId = uid,
            authorUsername = authorUsername,
            ciphertext = encrypted.ciphertext,
            iv = encrypted.iv,
            themeColorHex = themeColorHex,
            viewCount = 1L,
            createdAt = Timestamp.now(),
            updatedAt = Timestamp.now()
        )
        if (isLocalNodeMode()) {
            localStatusesFlow.value = listOf(localSt) + localStatusesFlow.value
            return@runCatching localId
        }
        val statusRef = db.collection("statuses").document()
        val payload = mapOf(
            "userId" to uid,
            "authorUsername" to authorUsername,
            "ciphertext" to encrypted.ciphertext,
            "iv" to encrypted.iv,
            "themeColorHex" to themeColorHex,
            "viewCount" to 1L,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            statusRef.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            localStatusesFlow.value = listOf(localSt) + localStatusesFlow.value
            return@runCatching localId
        }
        statusRef.id
    }

    suspend fun incrementStatusViewCount(
        statusId: String,
        currentViewCount: Long
    ): Result<Unit> = runCatching {
        requireUserId()
        if (isLocalNodeMode() || localStatusesFlow.value.any { it.id == statusId }) {
            localStatusesFlow.value = localStatusesFlow.value.map {
                if (it.id == statusId) it.copy(viewCount = currentViewCount + 1L) else it
            }
            if (isLocalNodeMode()) return@runCatching
        }
        val statusRef = db.collection("statuses").document(statusId)
        try {
            statusRef.update(
                mapOf(
                    "viewCount" to (currentViewCount + 1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (_: Exception) {
        }
    }

    fun observeCallSignals(userId: String): Flow<List<CallSignal>> = flow {
        if (isLocalNodeMode()) {
            emitAll(localCallsFlow)
            return@flow
        }
        val path = "calls"
        emitAll(
            db.collection("calls")
                .whereArrayContains("participantUids", userId)
                .snapshots()
                .map { snapshot ->
                    val cloudCalls = snapshot.toObjects(CallSignal::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    val cloudIds = cloudCalls.map { it.id }.toSet()
                    val localExtras = localCallsFlow.value.filter { it.id !in cloudIds }
                    (cloudCalls + localExtras).sortedByDescending { (it.createdAt ?: it.updatedAt)?.toDate()?.time ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (fallbackUserId.isNotBlank()) {
                        emitAll(localCallsFlow)
                    } else {
                        val exceptionToLog = firestoreError ?: (error as? Exception)
                        if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                            handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                        }
                        throw error
                    }
                }
        )
    }

    suspend fun startEncryptedCall(
        callerUsername: String,
        calleeUid: String,
        calleeUsername: String,
        callType: String,
        initialStatus: String = "RINGING"
    ): Result<CallSignal> = runCatching {
        val uid = requireUserId()
        val callId = "call_" + UUID.randomUUID().toString().take(16)
        val participants = listOf(uid, calleeUid).distinct()
        val emojis = CryptoEngine.deriveSafetyEmojis("$callId::$uid::$calleeUid")
        val localCall = CallSignal(
            id = callId,
            callerId = uid,
            callerUsername = callerUsername,
            calleeUsername = calleeUsername,
            participantUids = participants,
            callType = callType,
            status = initialStatus,
            safetyEmojis = emojis,
            createdAt = Timestamp.now(),
            updatedAt = Timestamp.now()
        )
        if (isLocalNodeMode()) {
            localCallsFlow.value = listOf(localCall) + localCallsFlow.value
            return@runCatching localCall
        }

        val callRef = db.collection("calls").document()
        val payload = mapOf(
            "callerId" to uid,
            "callerUsername" to callerUsername,
            "calleeUsername" to calleeUsername,
            "participantUids" to participants,
            "callType" to callType,
            "status" to initialStatus,
            "safetyEmojis" to emojis,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            callRef.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            localCallsFlow.value = listOf(localCall) + localCallsFlow.value
            return@runCatching localCall
        }
        callRef.get().await().toObject(CallSignal::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            ?: localCall
    }

    suspend fun acceptEncryptedCall(callId: String): Result<Unit> = runCatching {
        requireUserId()
        if (isLocalNodeMode() || localCallsFlow.value.any { it.id == callId }) {
            localCallsFlow.value = localCallsFlow.value.map {
                if (it.id == callId) it.copy(status = "CONNECTED") else it
            }
            if (isLocalNodeMode()) return@runCatching
        }
        val docRef = db.collection("calls").document(callId)
        try {
            docRef.update(
                mapOf(
                    "status" to "CONNECTED",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (_: Exception) {
        }
    }

    suspend fun endEncryptedCall(callId: String): Result<Unit> = runCatching {
        requireUserId()
        if (isLocalNodeMode() || localCallsFlow.value.any { it.id == callId }) {
            localCallsFlow.value = localCallsFlow.value.map {
                if (it.id == callId) it.copy(status = "ENDED") else it
            }
            if (isLocalNodeMode()) return@runCatching
        }
        val docRef = db.collection("calls").document(callId)
        try {
            docRef.update(
                mapOf(
                    "status" to "ENDED",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (_: Exception) {
        }
    }

    suspend fun deleteChatRoomCompletely(
        roomId: String,
        currentUid: String
    ): Result<Unit> = runCatching {
        requireUserId()
        deletedRoomIdsFlow.value = deletedRoomIdsFlow.value + roomId
        localRoomsFlow.value = localRoomsFlow.value.filterNot { it.id == roomId }
        localEnvelopesFlow.value = localEnvelopesFlow.value.filterNot { it.roomId == roomId }
        if (isLocalNodeMode()) return@runCatching

        try {
            val envSnap = db.collection("envelopes")
                .whereEqualTo("roomId", roomId)
                .whereArrayContains("participantUids", currentUid)
                .get()
                .await()
            for (doc in envSnap.documents) {
                runCatching { doc.reference.delete().await() }
            }
        } catch (_: Exception) {
        }

        val roomRef = db.collection("rooms").document(roomId)
        try {
            roomRef.delete().await()
        } catch (_: Exception) {
            // If current user is not ownerId, remove currentUid from participantUids
            runCatching {
                val snap = roomRef.get().await()
                val existingUids = (snap.get("participantUids") as? List<*>)
                    ?.mapNotNull { it as? String }
                    .orEmpty()
                val remaining = existingUids.filterNot { it == currentUid }
                if (remaining.isNotEmpty()) {
                    roomRef.update(
                        mapOf(
                            "participantUids" to remaining,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
        }
    }
}
