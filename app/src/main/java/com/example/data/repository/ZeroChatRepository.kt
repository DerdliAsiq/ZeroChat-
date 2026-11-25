package com.example.data.repository

import com.example.crypto.CryptoEngine
import com.example.crypto.IdentityKeyManager
import com.example.crypto.IdentityKeys
import com.example.crypto.LegacyCrypto
import com.example.crypto.MessageCodec
import com.example.data.model.AnnotationKind
import com.example.data.model.CallMediaChunk
import com.example.data.model.CallSignal
import com.example.data.model.CallStatus
import com.example.data.model.ChatRoom
import com.example.data.model.EncryptedEnvelope
import com.example.data.model.EncryptedStatus
import com.example.data.model.RoomAnnotation
import com.example.data.model.RoomKeyVersion
import com.example.data.model.RoomPresence
import com.example.data.model.RoomStatus
import com.example.data.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** Result of decrypting one envelope. [payload] is null when it could not be decrypted. */
data class OpenedEnvelope(
    val payload: JSONObject?,
    val isLegacy: Boolean,
    val isVerified: Boolean,
    val signerKeyChanged: Boolean
)

/**
 * All Firestore I/O for ZeroChat, implementing the V2 end-to-end protocol described in
 * [CryptoEngine]. Every public function either returns [Result] or a [Flow] that fails on
 * error; nothing is silently replaced with local data.
 */
class ZeroChatRepository(
    private val db: FirebaseFirestore,
    private val identityKeyManager: IdentityKeyManager,
    private val auth: FirebaseAuth = Firebase.auth
) {
    private val roomKeyCache = ConcurrentHashMap<String, ByteArray>()
    private val profileCache = ConcurrentHashMap<String, UserProfile>()
    private val rekeyMutex = Mutex()

    val uid: String
        get() = auth.currentUser?.uid ?: throw ZeroChatException("Oturum açık değil")

    fun identity(): IdentityKeys = identityKeyManager.getOrCreate(uid)

    private val users get() = db.collection("users")
    private val usernames get() = db.collection("usernames")
    private val rooms get() = db.collection("rooms")
    private val envelopes get() = db.collection("envelopes")
    private val statuses get() = db.collection("statuses")
    private val calls get() = db.collection("calls")

    private inline fun <reified T> DocumentSnapshot.toModel(): T? =
        toObject(T::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)

    // =====================================================================================
    // Profiles & usernames
    // =====================================================================================

    companion object {
        private val HANDLE_REGEX = Regex("^[a-z0-9_]{3,24}$")
        const val MAX_DISPLAY_NAME = 64
        const val MAX_BIO = 200
        const val MAX_GROUP_MEMBERS = 16
        const val MAX_TTL_SECONDS = 604_800L
        const val MAX_ENVELOPE_CIPHERTEXT = 700_000
        const val MAX_STATUS_TEXT = 1_000
        const val ENVELOPE_PAGE_SIZE = 300L

        fun sanitizeHandle(raw: String): String = raw.trim()
            .removePrefix("@")
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9_]"), "")
            .take(24)

        fun isValidHandle(handle: String) = HANDLE_REGEX.matches(handle)

        fun directRoomId(uidA: String, uidB: String): String {
            val sorted = listOf(uidA, uidB).sorted()
            return "dm_${sorted[0]}_${sorted[1]}"
        }

        fun newId(prefix: String): String =
            prefix + CryptoEngine.randomBytes(12).joinToString("") { "%02x".format(it) }
    }

    /**
     * Makes sure this user has a profile with a claimed, unique handle and the current device's
     * identity keys. Never overwrites a handle, display name or bio the user already chose.
     */
    suspend fun ensureProfile(preferredHandle: String?, fallbackDisplayName: String?): Result<UserProfile> =
        firestoreCatching(OperationType.WRITE, "users/{uid}") {
            val me = uid
            val id = identity()
            val existing = users.document(me).get().await().toModel<UserProfile>()

            val currentHandle = existing?.username?.takeIf { isValidHandle(it) }
            val claimedByMe = currentHandle != null &&
                usernames.document(currentHandle).get().await().getString("uid") == me
            val handle = if (claimedByMe) currentHandle!! else claimFreeHandle(
                listOfNotNull(currentHandle, preferredHandle?.let(::sanitizeHandle), "zero_${me.take(6).lowercase(Locale.ROOT)}")
                    .map(::sanitizeHandle)
                    .firstOrNull { isValidHandle(it) } ?: "zero_${me.take(6).lowercase(Locale.ROOT)}"
            )

            val keysChanged = existing == null ||
                existing.publicKey != id.publicKey ||
                existing.signingKey != id.signingKey ||
                existing.protocolVersion < CryptoEngine.PROTOCOL_VERSION ||
                existing.displayName.isBlank()
            if (existing != null && claimedByMe && !keysChanged) return@firestoreCatching existing

            val batch = db.batch()
            if (!claimedByMe) {
                batch.set(usernames.document(handle), mapOf("uid" to me, "createdAt" to FieldValue.serverTimestamp()))
            }
            val ref = users.document(me)
            if (existing == null) {
                batch.set(
                    ref,
                    mapOf(
                        "userId" to me,
                        "username" to handle,
                        "displayName" to (fallbackDisplayName?.trim()?.take(MAX_DISPLAY_NAME)?.ifBlank { null } ?: handle),
                        "bio" to "",
                        "publicKey" to id.publicKey,
                        "signingKey" to id.signingKey,
                        "safetyFingerprint" to id.fingerprint,
                        "onlineStatus" to "ONLINE",
                        "protocolVersion" to CryptoEngine.PROTOCOL_VERSION.toLong(),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            } else {
                val updateFields = mutableMapOf<String, Any>(
                    "username" to handle,
                    "publicKey" to id.publicKey,
                    "signingKey" to id.signingKey,
                    "safetyFingerprint" to id.fingerprint,
                    "protocolVersion" to CryptoEngine.PROTOCOL_VERSION.toLong(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (existing.displayName.isBlank()) {
                    updateFields["displayName"] = fallbackDisplayName?.trim()?.take(MAX_DISPLAY_NAME)?.ifBlank { null } ?: handle
                }
                batch.update(ref, updateFields)
            }
            batch.commit().await()
            users.document(me).get().await().toModel<UserProfile>()!!.also { profileCache[me] = it }
        }

    /** Finds a free handle starting from [base], adding a numeric suffix if it is taken. */
    private suspend fun claimFreeHandle(base: String): String {
        val me = uid
        val candidates = listOf(base) + (1..8).map { "${base.take(19)}_${(1000..9999).random()}" }
        for (candidate in candidates) {
            if (!isValidHandle(candidate)) continue
            val owner = usernames.document(candidate).get().await().getString("uid")
            if (owner == null || owner == me) return candidate
        }
        throw ZeroChatException("Uygun kullanıcı adı bulunamadı")
    }

    suspend fun updateProfile(rawHandle: String, displayName: String, bio: String): Result<UserProfile> =
        firestoreCatching(OperationType.UPDATE, "users/{uid}") {
            val me = uid
            val handle = sanitizeHandle(rawHandle)
            if (!isValidHandle(handle)) {
                throw ZeroChatException("Kullanıcı adı 3-24 karakter olmalı ve yalnızca a-z, 0-9, _ içermeli")
            }
            val current = users.document(me).get().await().toModel<UserProfile>()
                ?: throw ZeroChatException("Profil bulunamadı")
            val batch = db.batch()
            if (handle != current.username) {
                val owner = usernames.document(handle).get().await().getString("uid")
                if (owner != null && owner != me) throw ZeroChatException("@$handle kullanıcı adı başkasına ait")
                batch.set(usernames.document(handle), mapOf("uid" to me, "createdAt" to FieldValue.serverTimestamp()))
                if (isValidHandle(current.username)) batch.delete(usernames.document(current.username))
            }
            batch.update(
                users.document(me),
                mapOf(
                    "username" to handle,
                    "displayName" to displayName.trim().take(MAX_DISPLAY_NAME).ifBlank { handle },
                    "bio" to bio.trim().take(MAX_BIO),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            users.document(me).get().await().toModel<UserProfile>()!!.also { profileCache[me] = it }
        }

    suspend fun setPresence(online: Boolean): Result<Unit> = firestoreCatching(OperationType.UPDATE, "users/{uid}") {
        users.document(uid).update(
            mapOf("onlineStatus" to if (online) "ONLINE" else "OFFLINE", "updatedAt" to FieldValue.serverTimestamp())
        ).await()
        Unit
    }

    fun observeMyProfile(): Flow<UserProfile?> =
        users.document(uid).snapshots().map { snap -> snap.toModel<UserProfile>()?.also { profileCache[it.userId] = it } }

    /** Live profiles of the given users (rules allow at most 30 per query). */
    fun observeProfiles(uids: Set<String>): Flow<Map<String, UserProfile>> {
        val ids = uids.filter { it.isNotBlank() }.sorted()
        if (ids.isEmpty()) return flowOf(emptyMap())
        val chunks = ids.chunked(30).map { chunk ->
            users.whereIn(FieldPath.documentId(), chunk).limit(30).snapshots().map { snap ->
                snap.documents.mapNotNull { it.toModel<UserProfile>() }
            }
        }
        return combine(chunks) { parts ->
            parts.flatMap { it }.associateBy { it.userId }.also { profileCache.putAll(it) }
        }
    }

    suspend fun getProfile(userId: String, forceRefresh: Boolean = false): UserProfile? {
        if (!forceRefresh) profileCache[userId]?.let { return it }
        return users.document(userId).get().await().toModel<UserProfile>()?.also { profileCache[userId] = it }
    }

    /** Searches by handle across both `usernames` claims and `users` profiles (exact + prefix). */
    suspend fun searchUsers(rawQuery: String): Result<List<UserProfile>> =
        firestoreCatching(OperationType.LIST, "users") {
            val me = uid
            val trimmedNoAt = rawQuery.trim().removePrefix("@")
            val lowerNoAt = trimmedNoAt.lowercase(Locale.ROOT)
            val prefix = sanitizeHandle(rawQuery)
            if (prefix.length < 2 && lowerNoAt.length < 2) return@firestoreCatching emptyList()

            val found = LinkedHashMap<String, UserProfile>()
            var successCount = 0
            var lastError: Throwable? = null

            suspend fun runPart(block: suspend () -> Unit) {
                try {
                    block()
                    successCount++
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    lastError = e
                }
            }

            if (isValidHandle(prefix)) {
                runPart {
                    val owner = usernames.document(prefix).get().await().getString("uid")
                    if (!owner.isNullOrBlank() && owner != me) {
                        getProfile(owner, forceRefresh = true)?.let { found[it.userId] = it }
                    }
                }
            }

            val exactCandidates = listOf(prefix, lowerNoAt, trimmedNoAt).filter { it.length >= 2 }.distinct()
            for (candidate in exactCandidates) {
                runPart {
                    users.whereEqualTo("username", candidate).limit(10).get().await()
                        .documents.mapNotNull { it.toModel<UserProfile>() }
                        .filter { it.userId != me }
                        .forEach { found[it.userId] = it }
                }
            }

            if (prefix.length >= 2) {
                runPart {
                    users.orderBy("username")
                        .startAt(prefix)
                        .endAt(prefix + "\uf8ff")
                        .limit(20)
                        .get()
                        .await()
                        .documents.mapNotNull { it.toModel<UserProfile>() }
                        .filter { it.userId != me }
                        .forEach { found[it.userId] = it }
                }
                runPart {
                    val snap = usernames
                        .orderBy(FieldPath.documentId())
                        .startAt(prefix)
                        .endAt(prefix + "\uf8ff")
                        .limit(20)
                        .get()
                        .await()
                    val missingIds = snap.documents
                        .mapNotNull { it.getString("uid") }
                        .distinct()
                        .filter { it != me && !found.containsKey(it) }
                    missingIds.chunked(30).forEach { chunk ->
                        val docs = try {
                            users.whereIn(FieldPath.documentId(), chunk).limit(30).get().await()
                                .documents.mapNotNull { it.toModel<UserProfile>() }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Throwable) {
                            chunk.mapNotNull { id -> runCatching { getProfile(id, forceRefresh = true) }.getOrNull() }
                        }
                        docs.filter { it.userId != me }.forEach { found[it.userId] = it }
                    }
                }
            }

            if (successCount == 0 && lastError != null) throw lastError!!
            found.values.onEach { profileCache[it.userId] = it }.toList()
        }

    suspend fun findUserByHandle(rawHandle: String): Result<UserProfile?> =
        firestoreCatching(OperationType.GET, "usernames/{handle}") {
            val trimmedNoAt = rawHandle.trim().removePrefix("@")
            val lowerNoAt = trimmedNoAt.lowercase(Locale.ROOT)
            val handle = sanitizeHandle(rawHandle)
            if (isValidHandle(handle)) {
                val owner = usernames.document(handle).get().await().getString("uid")
                if (!owner.isNullOrBlank()) {
                    getProfile(owner, forceRefresh = true)?.let { return@firestoreCatching it }
                }
            }
            val candidates = listOf(handle, lowerNoAt, trimmedNoAt).filter { it.length >= 2 }.distinct()
            for (candidate in candidates) {
                val doc = users.whereEqualTo("username", candidate).limit(1).get().await()
                    .documents.firstOrNull()?.toModel<UserProfile>()
                if (doc != null) {
                    profileCache[doc.userId] = doc
                    return@firestoreCatching doc
                }
            }
            null
        }

    // =====================================================================================
    // Room keys
    // =====================================================================================

    private fun cacheKey(roomId: String, version: Long) = "$roomId|$version"

    /** Returns the room key for [version], unwrapping it from Firestore if needed. */
    suspend fun roomKey(roomId: String, version: Long): ByteArray? {
        if (version <= 0L) return null
        roomKeyCache[cacheKey(roomId, version)]?.let { return it }
        val doc = rooms.document(roomId).collection("keys").document(version.toString()).get().await()
        val keyDoc = doc.toModel<RoomKeyVersion>() ?: return null
        val wrapped = keyDoc.wrappedKeys[uid] ?: return null
        val key = runCatching {
            identity().unwrapKey(wrapped, CryptoEngine.roomKeyWrapContext(roomId, version, uid))
        }.getOrNull() ?: return null
        roomKeyCache[cacheKey(roomId, version)] = key
        return key
    }

    /**
     * Returns the key version and key to encrypt new content with. Creates a new version when the
     * room has none yet, when membership changed, or when a participant's identity changed.
     */
    suspend fun currentRoomKey(roomId: String): Pair<Long, ByteArray> = rekeyMutex.withLock {
        repeat(3) {
            val room = rooms.document(roomId).get().await().toModel<ChatRoom>()
                ?: throw ZeroChatException("Sohbet bulunamadı")
            val version = room.keyVersion
            val participants = room.participantUids.toSet()
            val profiles = participants.associateWith { getProfile(it, forceRefresh = true) }
            val missing = profiles.filterValues { it == null || !it.hasE2eeKeys }.keys
            if (version > 0L) {
                val keyDoc = rooms.document(roomId).collection("keys").document(version.toString()).get().await()
                    .toModel<RoomKeyVersion>()
                val upToDate = keyDoc != null &&
                    keyDoc.wrappedKeys.keys == participants &&
                    profiles.all { (pUid, profile) ->
                        profile == null || !profile.hasE2eeKeys ||
                            keyDoc.recipientKeyIds[pUid] == CryptoEngine.identityKeyId(profile.publicKey, profile.signingKey)
                    }
                if (upToDate || missing.isNotEmpty()) {
                    roomKey(roomId, version)?.let { return@withLock version to it }
                }
            }
            if (missing.isNotEmpty()) {
                val names = missing.joinToString { profiles[it]?.username?.let { h -> "@$h" } ?: "bir üye" }
                throw ZeroChatException("$names uygulamanın yeni sürümüne geçmeden şifreli mesaj gönderilemez")
            }
            try {
                return@withLock createKeyVersion(room, profiles.mapValues { it.value!! }, bumpRoom = true)
            } catch (e: FirebaseFirestoreException) {
                // Another participant rotated at the same time: re-read and retry.
                if (e.code != FirebaseFirestoreException.Code.PERMISSION_DENIED &&
                    e.code != FirebaseFirestoreException.Code.ALREADY_EXISTS
                ) throw e
            }
        }
        throw ZeroChatException("Oda anahtarı oluşturulamadı, lütfen tekrar deneyin")
    }

    private fun buildKeyVersion(
        roomId: String,
        version: Long,
        recipients: Map<String, UserProfile>
    ): Pair<ByteArray, Map<String, Any>> {
        val key = CryptoEngine.randomBytes(32)
        val wrapped = recipients.mapValues { (rUid, profile) ->
            CryptoEngine.wrapKey(key, profile.publicKey, CryptoEngine.roomKeyWrapContext(roomId, version, rUid))
        }
        val keyIds = recipients.mapValues { (_, p) -> CryptoEngine.identityKeyId(p.publicKey, p.signingKey) }
        return key to mapOf(
            "createdBy" to uid,
            "wrappedKeys" to wrapped,
            "recipientKeyIds" to keyIds,
            "createdAt" to FieldValue.serverTimestamp()
        )
    }

    private suspend fun createKeyVersion(
        room: ChatRoom,
        recipients: Map<String, UserProfile>,
        bumpRoom: Boolean
    ): Pair<Long, ByteArray> {
        val newVersion = room.keyVersion + 1
        val (key, keyDoc) = buildKeyVersion(room.id, newVersion, recipients)
        val batch = db.batch()
        batch.set(rooms.document(room.id).collection("keys").document(newVersion.toString()), keyDoc)
        if (bumpRoom) {
            batch.update(
                rooms.document(room.id),
                mapOf("keyVersion" to newVersion, "updatedAt" to FieldValue.serverTimestamp())
            )
        }
        batch.commit().await()
        roomKeyCache[cacheKey(room.id, newVersion)] = key
        return newVersion to key
    }

    // =====================================================================================
    // Rooms
    // =====================================================================================

    fun observeUserRooms(): Flow<List<ChatRoom>> =
        rooms.whereArrayContains("participantUids", uid).snapshots().map { snap ->
            snap.documents.mapNotNull { it.toModel<ChatRoom>() }.sortedByDescending { it.activityMillis }
        }

    fun observeRoom(roomId: String): Flow<ChatRoom?> =
        rooms.document(roomId).snapshots().map { it.toModel<ChatRoom>() }

    private fun requireE2ee(profile: UserProfile) {
        if (!profile.hasE2eeKeys) {
            throw ZeroChatException("@${profile.username} uygulamanın yeni sürümüne geçmeden şifreli sohbet başlatılamaz")
        }
    }

    private fun previewFields(roomId: String, version: Long, key: ByteArray, text: String): Map<String, Any> {
        val box = CryptoEngine.aesGcmEncrypt(
            key,
            text.take(120).toByteArray(StandardCharsets.UTF_8),
            MessageCodec.previewAad(roomId, version, uid)
        )
        return mapOf(
            "lastPreviewKv" to version,
            "lastPreviewIv" to box.iv,
            "lastPreviewCt" to box.ciphertext,
            "lastSenderUid" to uid,
            "lastActivityAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }

    suspend fun decryptPreview(room: ChatRoom): String? {
        if (room.lastPreviewCt.isNotBlank() && room.lastPreviewKv > 0L) {
            val key = roomKey(room.id, room.lastPreviewKv) ?: return null
            return runCatching {
                String(
                    CryptoEngine.aesGcmDecrypt(
                        key, room.lastPreviewIv, room.lastPreviewCt,
                        MessageCodec.previewAad(room.id, room.lastPreviewKv, room.lastSenderUid)
                    ),
                    StandardCharsets.UTF_8
                )
            }.getOrNull()
        }
        if (room.lastCipherPreview.isNotBlank()) {
            return LegacyCrypto.decryptPreviewPacket(room.lastCipherPreview, room.roomKeyHint)
        }
        return null
    }

    /**
     * Opens (or creates) the direct conversation with [peer]. A new conversation starts as a
     * PENDING request that only the peer can accept; the requester may send an intro message.
     */
    suspend fun requestDirectChat(me: UserProfile, peer: UserProfile, introNote: String): Result<ChatRoom> =
        firestoreCatching(OperationType.WRITE, "rooms/dm") {
            val myUid = uid
            if (peer.userId == myUid) throw ZeroChatException("Kendinizle sohbet başlatamazsınız")
            val livePeer = getProfile(peer.userId, forceRefresh = true) ?: throw ZeroChatException("Kullanıcı bulunamadı")
            requireE2ee(livePeer)
            val roomId = directRoomId(myUid, livePeer.userId)
            val ref = rooms.document(roomId)
            // Rules deny reads of rooms that do not exist (to avoid leaking whether two other
            // users talk); since this id contains our own uid, a denial simply means "absent".
            val existing = try {
                ref.get().await().toModel<ChatRoom>()
            } catch (e: FirebaseFirestoreException) {
                if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) null else throw e
            }
            if (existing != null && existing.status != RoomStatus.REJECTED) {
                if (introNote.isNotBlank()) sendMessage(existing, textPayload(introNote, me.username), existing.defaultTtlSeconds)
                return@firestoreCatching existing
            }

            val participants = listOf(myUid, livePeer.userId).sorted()
            val myProfile = getProfile(myUid, forceRefresh = true) ?: throw ZeroChatException("Profiliniz bulunamadı")
            requireE2ee(myProfile)
            val recipients = mapOf(myUid to myProfile, livePeer.userId to livePeer)
            val version = (existing?.keyVersion ?: 0L) + 1
            val (key, keyDoc) = buildKeyVersion(roomId, version, recipients)
            val previewText = introNote.trim().ifBlank { "🤝 Sohbet isteği" }

            val batch = db.batch()
            if (existing == null) {
                batch.set(
                    ref,
                    mapOf(
                        "ownerId" to myUid,
                        "participantUids" to participants,
                        "participantNames" to listOf(me.username, livePeer.username).joinToString(","),
                        "title" to "@${livePeer.username}",
                        "isGroup" to false,
                        "status" to RoomStatus.PENDING,
                        "requesterUid" to myUid,
                        "keyVersion" to version,
                        "defaultTtlSeconds" to 0L,
                        "createdAt" to FieldValue.serverTimestamp()
                    ) + previewFields(roomId, version, key, previewText)
                )
            } else {
                // Re-request after a rejection.
                batch.update(
                    ref,
                    mapOf(
                        "status" to RoomStatus.PENDING,
                        "requesterUid" to myUid,
                        "keyVersion" to version
                    ) + previewFields(roomId, version, key, previewText)
                )
            }
            batch.set(ref.collection("keys").document(version.toString()), keyDoc)
            batch.commit().await()
            roomKeyCache[cacheKey(roomId, version)] = key

            val room = ref.get().await().toModel<ChatRoom>()!!
            if (introNote.isNotBlank()) {
                sendMessage(room, textPayload(introNote, me.username), 0L).getOrThrow()
            }
            room
        }

    fun textPayload(text: String, senderUsername: String, meta: JSONObject = JSONObject()): JSONObject =
        JSONObject().apply {
            put("type", "TEXT")
            put("text", text.trim())
            put("sender", senderUsername)
            put("meta", meta)
        }

    suspend fun approveConnectionRequest(room: ChatRoom): Result<Unit> =
        firestoreCatching(OperationType.UPDATE, "rooms/${room.id}") {
            rooms.document(room.id).update(
                mapOf("status" to RoomStatus.ACTIVE, "updatedAt" to FieldValue.serverTimestamp())
            ).await()
            Unit
        }

    /** Recipient rejects (room stays, marked REJECTED); requester cancels (room deleted). */
    suspend fun rejectOrCancelConnectionRequest(room: ChatRoom): Result<Unit> =
        firestoreCatching(OperationType.UPDATE, "rooms/${room.id}") {
            if (room.requesterUid == uid && room.ownerId == uid) {
                deleteRoomDocuments(room.id)
            } else {
                rooms.document(room.id).update(
                    mapOf("status" to RoomStatus.REJECTED, "updatedAt" to FieldValue.serverTimestamp())
                ).await()
            }
            Unit
        }

    suspend fun createGroupRoom(me: UserProfile, title: String, peers: List<UserProfile>, ttlSeconds: Long): Result<ChatRoom> =
        firestoreCatching(OperationType.CREATE, "rooms/group") {
            val myUid = uid
            val members = (listOf(me) + peers).distinctBy { it.userId }
            if (members.size > MAX_GROUP_MEMBERS) throw ZeroChatException("Bir grupta en fazla $MAX_GROUP_MEMBERS kişi olabilir")
            val live = members.associate { it.userId to (getProfile(it.userId, forceRefresh = true) ?: it) }
            live.values.forEach(::requireE2ee)
            val ref = rooms.document()
            val (key, keyDoc) = buildKeyVersion(ref.id, 1L, live)
            val cleanTitle = title.trim().ifBlank { "Grup" }.take(80)
            val batch = db.batch()
            batch.set(
                ref,
                mapOf(
                    "ownerId" to myUid,
                    "participantUids" to live.keys.toList(),
                    "participantNames" to live.values.joinToString(",") { it.username },
                    "title" to cleanTitle,
                    "isGroup" to true,
                    "status" to RoomStatus.ACTIVE,
                    "requesterUid" to "",
                    "keyVersion" to 1L,
                    "defaultTtlSeconds" to ttlSeconds.coerceIn(0L, MAX_TTL_SECONDS),
                    "createdAt" to FieldValue.serverTimestamp()
                ) + previewFields(ref.id, 1L, key, "👥 Grup oluşturuldu")
            )
            batch.set(ref.collection("keys").document("1"), keyDoc)
            batch.commit().await()
            roomKeyCache[cacheKey(ref.id, 1L)] = key
            ref.get().await().toModel<ChatRoom>()!!
        }

    /** Owner only. Membership changes always rotate the room key so removed members cannot read on. */
    suspend fun updateGroupMembersAndTitle(room: ChatRoom, title: String, peers: List<UserProfile>): Result<ChatRoom> =
        firestoreCatching(OperationType.UPDATE, "rooms/${room.id}") {
            val myUid = uid
            if (room.ownerId != myUid) throw ZeroChatException("Grubu yalnızca grup sahibi düzenleyebilir")
            val fresh = rooms.document(room.id).get().await().toModel<ChatRoom>()
                ?: throw ZeroChatException("Grup bulunamadı")
            val me = getProfile(myUid, forceRefresh = true) ?: throw ZeroChatException("Profiliniz bulunamadı")
            val members = (listOf(me) + peers).distinctBy { it.userId }
            if (members.size > MAX_GROUP_MEMBERS) throw ZeroChatException("Bir grupta en fazla $MAX_GROUP_MEMBERS kişi olabilir")
            val live = members.associate { it.userId to (getProfile(it.userId, forceRefresh = true) ?: it) }
            val cleanTitle = title.trim().ifBlank { fresh.title }.take(80)
            val membershipChanged = live.keys != fresh.participantUids.toSet()

            val batch = db.batch()
            val update = mutableMapOf<String, Any>(
                "title" to cleanTitle,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (membershipChanged) {
                live.values.forEach(::requireE2ee)
                val newVersion = fresh.keyVersion + 1
                val (key, keyDoc) = buildKeyVersion(fresh.id, newVersion, live)
                batch.set(rooms.document(fresh.id).collection("keys").document(newVersion.toString()), keyDoc)
                update["participantUids"] = live.keys.toList()
                update["participantNames"] = live.values.joinToString(",") { it.username }
                update["keyVersion"] = newVersion
                roomKeyCache[cacheKey(fresh.id, newVersion)] = key
            }
            batch.update(rooms.document(fresh.id), update)
            batch.commit().await()
            rooms.document(fresh.id).get().await().toModel<ChatRoom>()!!
        }

    /** Leaves a group. The remaining members rotate the key on their next send. */
    suspend fun leaveGroup(room: ChatRoom): Result<Unit> = firestoreCatching(OperationType.UPDATE, "rooms/${room.id}") {
        val myUid = uid
        val fresh = rooms.document(room.id).get().await().toModel<ChatRoom>() ?: return@firestoreCatching Unit
        val remaining = fresh.participantUids.filter { it != myUid }
        val myHandle = profileCache[myUid]?.username
        rooms.document(room.id).update(
            mapOf(
                "participantUids" to remaining,
                "participantNames" to fresh.participantHandles.filter { it != myHandle }.joinToString(","),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Unit
    }

    /** Owner only: deletes the room, its keys and all of its messages for everyone. */
    suspend fun deleteRoomForEveryone(room: ChatRoom): Result<Unit> =
        firestoreCatching(OperationType.DELETE, "rooms/${room.id}") {
            if (room.ownerId != uid) throw ZeroChatException("Sohbeti herkes için yalnızca sahibi silebilir")
            deleteRoomDocuments(room.id)
        }

    private suspend fun deleteRoomDocuments(roomId: String) {
        val myUid = uid
        val envs = envelopes.whereEqualTo("roomId", roomId).whereArrayContains("participantUids", myUid).get().await()
        envs.documents.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { batch.delete(it.reference) }
            batch.commit().await()
        }
        val roomRef = rooms.document(roomId)
        listOf("annotations", "presence", "keys").forEach { sub ->
            roomRef.collection(sub).get().await().documents.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
        }
        roomRef.delete().await()
    }

    suspend fun updateRoomTtl(room: ChatRoom, ttlSeconds: Long): Result<Unit> =
        firestoreCatching(OperationType.UPDATE, "rooms/${room.id}") {
            rooms.document(room.id).update(
                mapOf(
                    "defaultTtlSeconds" to ttlSeconds.coerceIn(0L, MAX_TTL_SECONDS),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Unit
        }

    /** Creates a fresh key version (e.g. after a suspected compromise). */
    suspend fun rotateRoomKey(room: ChatRoom): Result<Long> = firestoreCatching(OperationType.WRITE, "rooms/${room.id}/keys") {
        rekeyMutex.withLock {
            val fresh = rooms.document(room.id).get().await().toModel<ChatRoom>() ?: throw ZeroChatException("Sohbet bulunamadı")
            val profiles = fresh.participantUids.associateWith { getProfile(it, forceRefresh = true) }
            profiles.values.forEach { if (it == null) throw ZeroChatException("Üye profili bulunamadı") else requireE2ee(it) }
            createKeyVersion(fresh, profiles.mapValues { it.value!! }, bumpRoom = true).first
        }
    }

    // =====================================================================================
    // Messages
    // =====================================================================================

    fun observeRoomEnvelopes(roomId: String): Flow<List<EncryptedEnvelope>> =
        envelopes
            .whereEqualTo("roomId", roomId)
            .whereArrayContains("participantUids", uid)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(ENVELOPE_PAGE_SIZE)
            .snapshots()
            .map { snap -> snap.documents.mapNotNull { it.toModel<EncryptedEnvelope>() }.reversed() }

    suspend fun openEnvelope(room: ChatRoom, env: EncryptedEnvelope): OpenedEnvelope {
        if (env.v < 2L) {
            val meta = runCatching { JSONObject(env.encryptedMeta.ifBlank { "{}" }) }.getOrDefault(JSONObject())
            val text = LegacyCrypto.decrypt(env.ciphertext, env.iv, room.roomKeyHint, meta.optInt("ratchetStep", 0))
                ?: return OpenedEnvelope(null, isLegacy = true, isVerified = false, signerKeyChanged = false)
            val payload = JSONObject().apply {
                put("type", env.messageType.ifBlank { "TEXT" })
                put("text", text)
                put("sender", env.senderUsername)
                put("meta", meta)
            }
            return OpenedEnvelope(payload, isLegacy = true, isVerified = false, signerKeyChanged = false)
        }
        val key = runCatching { roomKey(room.id, env.keyVersion) }.getOrNull()
            ?: return OpenedEnvelope(null, isLegacy = false, isVerified = false, signerKeyChanged = false)
        val json = runCatching {
            MessageCodec.openEnvelope(key, room.id, env.id, env.senderId, env.keyVersion, env.iv, env.ciphertext)
        }.getOrNull() ?: return OpenedEnvelope(null, isLegacy = false, isVerified = false, signerKeyChanged = false)

        val sender = runCatching { getProfile(env.senderId) }.getOrNull()
        val currentSenderKeyId = sender?.let { CryptoEngine.identityKeyId(it.publicKey, it.signingKey) }
        val verified = sender != null && MessageCodec.verifyEnvelope(
            sender.signingKey, room.id, env.id, env.senderId, env.keyVersion, env.iv, env.ciphertext, env.sig
        )
        val keyChanged = !verified && currentSenderKeyId != null && env.signerKeyId.isNotBlank() &&
            env.signerKeyId != currentSenderKeyId
        if (!verified && !keyChanged) {
            // Signed by the sender's current key but the signature does not match: tampered.
            return OpenedEnvelope(null, isLegacy = false, isVerified = false, signerKeyChanged = false)
        }
        return OpenedEnvelope(
            runCatching { JSONObject(json) }.getOrNull(),
            isLegacy = false,
            isVerified = verified,
            signerKeyChanged = keyChanged
        )
    }

    /**
     * Encrypts, signs and sends [payload]. [envelopeId] may be pre-generated so that retries
     * (offline outbox) are idempotent: an already-delivered id is treated as success.
     */
    suspend fun sendMessage(
        room: ChatRoom,
        payload: JSONObject,
        ttlSeconds: Long,
        envelopeId: String = newId("env_")
    ): Result<String> = firestoreCatching(OperationType.CREATE, "envelopes/$envelopeId") {
        val myUid = uid
        val envRef = envelopes.document(envelopeId)
        if (runCatching { envRef.get().await().exists() }.getOrDefault(false)) return@firestoreCatching envelopeId

        val fresh = rooms.document(room.id).get().await().toModel<ChatRoom>()
            ?: throw ZeroChatException("Sohbet bulunamadı")
        if (fresh.isRejectedRequest) throw ZeroChatException("Bu sohbet isteği reddedildi")
        if (fresh.isPendingRequest && fresh.requesterUid != myUid) {
            throw ZeroChatException("Mesaj göndermek için önce sohbet isteğini kabul edin")
        }
        val (version, key) = currentRoomKey(fresh.id)
        val sealed = MessageCodec.sealEnvelope(key, identity(), fresh.id, envelopeId, version, payload.toString())
        if (sealed.ciphertext.length > MAX_ENVELOPE_CIPHERTEXT) {
            throw ZeroChatException("Mesaj çok büyük (en fazla yaklaşık 500 KB)")
        }
        val ttl = ttlSeconds.coerceIn(0L, MAX_TTL_SECONDS)
        val data = mutableMapOf<String, Any>(
            "roomId" to fresh.id,
            "senderId" to myUid,
            "participantUids" to fresh.participantUids,
            "v" to CryptoEngine.PROTOCOL_VERSION.toLong(),
            "keyVersion" to version,
            "iv" to sealed.iv,
            "ciphertext" to sealed.ciphertext,
            "sig" to sealed.sig,
            "signerKeyId" to sealed.signerKeyId,
            "ttlSeconds" to ttl,
            "edited" to false,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (ttl > 0L) data["expireAt"] = Timestamp(Date(System.currentTimeMillis() + ttl * 1000L))

        val batch = db.batch()
        batch.set(envRef, data)
        batch.update(rooms.document(fresh.id), previewFields(fresh.id, version, key, previewTextFor(payload)))
        batch.commit().await()
        envelopeId
    }

    fun previewTextFor(payload: JSONObject): String {
        val text = payload.optString("text")
        return when (payload.optString("type")) {
            "IMAGE" -> "📷 ${text.ifBlank { "Fotoğraf" }}"
            "VOICE" -> "🎙️ Sesli mesaj"
            "FILE" -> "📄 ${payload.optJSONObject("meta")?.optString("fileName").orEmpty().ifBlank { "Dosya" }}"
            "LOCATION" -> "📍 Konum"
            "POLL" -> "📊 ${payload.optJSONObject("meta")?.optString("question").orEmpty().ifBlank { "Anket" }}"
            else -> text
        }
    }

    /** Sender only: re-encrypts the envelope with new content. */
    suspend fun editMessage(room: ChatRoom, envelopeId: String, payload: JSONObject): Result<Unit> =
        firestoreCatching(OperationType.UPDATE, "envelopes/$envelopeId") {
            val env = envelopes.document(envelopeId).get().await().toModel<EncryptedEnvelope>()
                ?: throw ZeroChatException("Mesaj bulunamadı")
            if (env.senderId != uid) throw ZeroChatException("Yalnızca kendi mesajlarınızı düzenleyebilirsiniz")
            if (env.v < 2L) throw ZeroChatException("Eski şifrelemeli mesajlar düzenlenemez")
            val key = roomKey(room.id, env.keyVersion) ?: throw ZeroChatException("Oda anahtarı bulunamadı")
            val sealed = MessageCodec.sealEnvelope(key, identity(), room.id, envelopeId, env.keyVersion, payload.toString())
            envelopes.document(envelopeId).update(
                mapOf(
                    "iv" to sealed.iv,
                    "ciphertext" to sealed.ciphertext,
                    "sig" to sealed.sig,
                    "signerKeyId" to sealed.signerKeyId,
                    "edited" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Unit
        }

    /** Deletes a message: the sender any time, any participant once it has expired. */
    suspend fun deleteMessage(envelopeId: String): Result<Unit> =
        firestoreCatching(OperationType.DELETE, "envelopes/$envelopeId") {
            envelopes.document(envelopeId).delete().await()
            Unit
        }

    /** Deletes every message this user sent in the room. Returns the number deleted. */
    suspend fun deleteMyMessages(roomId: String): Result<Int> = firestoreCatching(OperationType.DELETE, "envelopes") {
        val snap = envelopes.whereEqualTo("roomId", roomId)
            .whereArrayContains("participantUids", uid)
            .get().await()
        val mine = snap.documents.filter { it.getString("senderId") == uid }
        mine.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { batch.delete(it.reference) }
            batch.commit().await()
        }
        mine.size
    }

    // =====================================================================================
    // Annotations (reactions, poll votes, pins) and presence
    // =====================================================================================

    fun observeAnnotations(roomId: String): Flow<List<RoomAnnotation>> =
        rooms.document(roomId).collection("annotations").snapshots().map { snap ->
            snap.documents.mapNotNull { it.toModel<RoomAnnotation>() }
        }

    private fun annotationId(envelopeId: String, kind: String) = "${envelopeId}_${uid}_$kind"

    /** Sets (or clears with null) this user's annotation of [kind] on a message. */
    suspend fun setAnnotation(room: ChatRoom, envelopeId: String, kind: String, value: String?): Result<Unit> =
        firestoreCatching(OperationType.WRITE, "rooms/${room.id}/annotations") {
            val annId = annotationId(envelopeId, kind)
            val ref = rooms.document(room.id).collection("annotations").document(annId)
            if (value == null) {
                ref.delete().await()
                return@firestoreCatching Unit
            }
            val (version, key) = currentRoomKey(room.id)
            val box = CryptoEngine.aesGcmEncrypt(
                key, value.toByteArray(StandardCharsets.UTF_8), MessageCodec.annotationAad(room.id, annId, version)
            )
            ref.set(
                mapOf(
                    "envelopeId" to envelopeId,
                    "uid" to uid,
                    "kind" to kind,
                    "keyVersion" to version,
                    "iv" to box.iv,
                    "ciphertext" to box.ciphertext,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Unit
        }

    suspend fun decryptAnnotation(roomId: String, ann: RoomAnnotation): String? {
        if (ann.kind == AnnotationKind.PIN) return "1"
        val key = roomKey(roomId, ann.keyVersion) ?: return null
        return runCatching {
            String(
                CryptoEngine.aesGcmDecrypt(key, ann.iv, ann.ciphertext, MessageCodec.annotationAad(roomId, ann.id, ann.keyVersion)),
                StandardCharsets.UTF_8
            )
        }.getOrNull()
    }

    /** Pins are shared: any participant may remove any pin on a message. */
    suspend fun clearPins(roomId: String, envelopeId: String, pinAnnotationIds: List<String>): Result<Unit> =
        firestoreCatching(OperationType.DELETE, "rooms/$roomId/annotations") {
            val batch = db.batch()
            pinAnnotationIds.forEach { batch.delete(rooms.document(roomId).collection("annotations").document(it)) }
            batch.commit().await()
            Unit
        }

    fun observePresence(roomId: String): Flow<List<RoomPresence>> =
        rooms.document(roomId).collection("presence").snapshots().map { snap ->
            snap.documents.mapNotNull { it.toModel<RoomPresence>() }
        }

    suspend fun markRoomRead(roomId: String): Result<Unit> = firestoreCatching(OperationType.WRITE, "presence") {
        rooms.document(roomId).collection("presence").document(uid)
            .set(mapOf("lastReadAt" to FieldValue.serverTimestamp()), com.google.firebase.firestore.SetOptions.merge())
            .await()
        Unit
    }

    suspend fun markTyping(roomId: String): Result<Unit> = firestoreCatching(OperationType.WRITE, "presence") {
        rooms.document(roomId).collection("presence").document(uid)
            .set(mapOf("typingAt" to FieldValue.serverTimestamp()), com.google.firebase.firestore.SetOptions.merge())
            .await()
        Unit
    }

    // =====================================================================================
    // Statuses (24 h stories) — encrypted for the author's contacts only
    // =====================================================================================

    fun observeStatuses(): Flow<List<EncryptedStatus>> =
        statuses.whereArrayContains("audienceUids", uid)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(100)
            .snapshots()
            .map { snap -> snap.documents.mapNotNull { it.toModel<EncryptedStatus>() } }

    suspend fun postStatus(text: String, colorHex: String, audience: Collection<UserProfile>): Result<String> =
        firestoreCatching(OperationType.CREATE, "statuses") {
            val myUid = uid
            val clean = text.trim()
            if (clean.isEmpty()) throw ZeroChatException("Durum metni boş olamaz")
            if (clean.length > MAX_STATUS_TEXT) throw ZeroChatException("Durum en fazla $MAX_STATUS_TEXT karakter olabilir")
            val me = getProfile(myUid, forceRefresh = true) ?: throw ZeroChatException("Profiliniz bulunamadı")
            val recipients = (audience.filter { it.hasE2eeKeys && it.userId != myUid } + me).distinctBy { it.userId }.take(200)
            val statusId = newId("st_")
            val statusKey = CryptoEngine.randomBytes(32)
            val sealed = MessageCodec.sealStatus(statusKey, identity(), statusId, clean)
            val wrapped = recipients.associate { p ->
                p.userId to CryptoEngine.wrapKey(statusKey, p.publicKey, CryptoEngine.statusKeyWrapContext(statusId, p.userId))
            }
            statuses.document(statusId).set(
                mapOf(
                    "userId" to myUid,
                    "audienceUids" to recipients.map { it.userId },
                    "wrappedKeys" to wrapped,
                    "iv" to sealed.iv,
                    "ciphertext" to sealed.ciphertext,
                    "sig" to sealed.sig,
                    "themeColorHex" to (if (Regex("^#[0-9A-Fa-f]{6}$").matches(colorHex)) colorHex else "#082E1E"),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "expireAt" to Timestamp(Date(System.currentTimeMillis() + 24L * 3600L * 1000L))
                )
            ).await()
            statusId
        }

    suspend fun openStatus(status: EncryptedStatus): String? {
        val myUid = uid
        val wrapped = status.wrappedKeys[myUid] ?: return null
        val author = runCatching { getProfile(status.userId) }.getOrNull() ?: return null
        return runCatching {
            val key = identity().unwrapKey(wrapped, CryptoEngine.statusKeyWrapContext(status.id, myUid))
            MessageCodec.openStatus(key, status.id, status.userId, status.iv, status.ciphertext, status.sig, author.signingKey)
        }.getOrNull()
    }

    suspend fun markStatusViewed(statusId: String): Result<Unit> =
        firestoreCatching(OperationType.WRITE, "statuses/$statusId/views") {
            statuses.document(statusId).collection("views").document(uid)
                .set(mapOf("viewedAt" to FieldValue.serverTimestamp())).await()
            Unit
        }

    /** Author only: uids that viewed the status. */
    suspend fun statusViewers(statusId: String): Result<List<String>> =
        firestoreCatching(OperationType.LIST, "statuses/$statusId/views") {
            statuses.document(statusId).collection("views").get().await().documents.map { it.id }
        }

    suspend fun deleteStatus(statusId: String): Result<Unit> = firestoreCatching(OperationType.DELETE, "statuses/$statusId") {
        statuses.document(statusId).delete().await()
        Unit
    }

    // =====================================================================================
    // Calls — signalling on calls/{id}, encrypted media on calls/{id}/media/{uid}
    // =====================================================================================

    fun observeCalls(): Flow<List<CallSignal>> =
        calls.whereArrayContains("participantUids", uid)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(30)
            .snapshots()
            .map { snap -> snap.documents.mapNotNull { it.toModel<CallSignal>() } }

    suspend fun startCall(room: ChatRoom, callType: String): Result<CallSignal> =
        firestoreCatching(OperationType.CREATE, "calls") {
            val myUid = uid
            if (room.isGroup) throw ZeroChatException("Grup araması desteklenmiyor")
            if (!room.isApprovedConnection) throw ZeroChatException("Arama için sohbet isteğinin kabul edilmesi gerekiyor")
            val peerUid = room.peerUidFor(myUid) ?: throw ZeroChatException("Aranacak kişi bulunamadı")
            val (version, _) = currentRoomKey(room.id)
            val callId = newId("call_")
            calls.document(callId).set(
                mapOf(
                    "callerId" to myUid,
                    "calleeUid" to peerUid,
                    "roomId" to room.id,
                    "keyVersion" to version,
                    "participantUids" to listOf(myUid, peerUid),
                    "callType" to if (callType == "VIDEO") "VIDEO" else "AUDIO",
                    "status" to CallStatus.RINGING,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            calls.document(callId).get().await().toModel<CallSignal>()!!
        }

    suspend fun setCallStatus(callId: String, status: String): Result<Unit> =
        firestoreCatching(OperationType.UPDATE, "calls/$callId") {
            calls.document(callId).update(mapOf("status" to status, "updatedAt" to FieldValue.serverTimestamp())).await()
            Unit
        }

    fun observeCallMedia(callId: String, senderUid: String): Flow<CallMediaChunk?> =
        calls.document(callId).collection("media").document(senderUid).snapshots().map { it.toModel<CallMediaChunk>() }

    private suspend fun callKey(call: CallSignal): ByteArray {
        val cacheId = "call|${call.id}"
        roomKeyCache[cacheId]?.let { return it }
        val roomKey = roomKey(call.roomId, call.keyVersion) ?: throw ZeroChatException("Arama anahtarı bulunamadı")
        return CryptoEngine.deriveCallKey(roomKey, call.id).also { roomKeyCache[cacheId] = it }
    }

    suspend fun pushCallMedia(call: CallSignal, seq: Long, audioB64: String?, videoB64: String?): Result<Unit> =
        firestoreCatching(OperationType.WRITE, "calls/${call.id}/media") {
            val myUid = uid
            val content = JSONObject().apply {
                if (!audioB64.isNullOrBlank()) put("a", audioB64)
                if (!videoB64.isNullOrBlank()) put("v", videoB64)
            }.toString()
            val box = CryptoEngine.aesGcmEncrypt(
                callKey(call), content.toByteArray(StandardCharsets.UTF_8), MessageCodec.callMediaAad(call.id, myUid, seq)
            )
            calls.document(call.id).collection("media").document(myUid).set(
                mapOf("seq" to seq, "iv" to box.iv, "ciphertext" to box.ciphertext, "updatedAt" to FieldValue.serverTimestamp())
            ).await()
            Unit
        }

    /** Returns (audioB64, videoB64) or null if the chunk cannot be decrypted. */
    suspend fun openCallMedia(call: CallSignal, senderUid: String, chunk: CallMediaChunk): Pair<String, String>? =
        runCatching {
            val json = JSONObject(
                String(
                    CryptoEngine.aesGcmDecrypt(callKey(call), chunk.iv, chunk.ciphertext, MessageCodec.callMediaAad(call.id, senderUid, chunk.seq)),
                    StandardCharsets.UTF_8
                )
            )
            json.optString("a") to json.optString("v")
        }.getOrNull()

    fun clearCaches() {
        roomKeyCache.clear()
        profileCache.clear()
    }
}
