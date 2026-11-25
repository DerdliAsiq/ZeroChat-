package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName

/**
 * Firestore documents. Fields marked @get:Exclude are never written to Firestore: the
 * ViewModel fills them from other sources (profiles, decrypted payloads) for the UI.
 */

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val username: String = "",
    val displayName: String = "",
    val bio: String = "",
    /** X.509 base64 P-256 ECDH public key (V2). Legacy profiles hold a non-usable value. */
    val publicKey: String = "",
    /** X.509 base64 P-256 ECDSA public key (V2). Empty on legacy profiles. */
    val signingKey: String = "",
    val safetyFingerprint: String = "",
    val onlineStatus: String = "OFFLINE",
    val protocolVersion: Long = 0L,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    /** True when the profile has published V2 identity keys and can receive encrypted messages. */
    @get:Exclude
    val hasE2eeKeys: Boolean
        get() = protocolVersion >= 2L && publicKey.isNotBlank() && signingKey.isNotBlank()
}

/** `usernames/{handle}`: guarantees that a handle belongs to exactly one uid. */
@IgnoreExtraProperties
data class UsernameClaim(
    @DocumentId val handle: String = "",
    val uid: String = "",
    val createdAt: Timestamp? = null
)

object RoomStatus {
    const val ACTIVE = "ACTIVE"
    const val PENDING = "PENDING"
    const val REJECTED = "REJECTED"
}

@IgnoreExtraProperties
data class ChatRoom(
    @DocumentId val id: String = "",
    val ownerId: String = "",
    val participantUids: List<String> = emptyList(),
    /** Comma-separated handles, display only (handles are resolved from profiles when known). */
    val participantNames: String = "",
    val title: String = "",
    @get:PropertyName("isGroup") @set:PropertyName("isGroup")
    var isGroup: Boolean = false,
    /** ACTIVE, PENDING (direct-message request) or REJECTED. Empty on legacy rooms (= ACTIVE). */
    val status: String = "",
    val requesterUid: String = "",
    /** Current room key version; 0 means no V2 key has been created yet. */
    val keyVersion: Long = 0L,
    val defaultTtlSeconds: Long = 0L,
    val lastPreviewKv: Long = 0L,
    val lastPreviewIv: String = "",
    val lastPreviewCt: String = "",
    val lastSenderUid: String = "",
    val lastActivityAt: Timestamp? = null,
    /** Legacy V1 fields, kept only to read old history. */
    val roomKeyHint: String = "",
    val lastCipherPreview: String = "",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null,
    // ---- filled by the ViewModel
    @get:Exclude val previewText: String = "",
    @get:Exclude val lastSenderName: String = "",
    @get:Exclude val requesterUsername: String = ""
) {
    private val legacyPending: Boolean
        get() = status.isBlank() && roomKeyHint.startsWith("REQ_FROM::")

    private val legacyRejected: Boolean
        get() = status.isBlank() && roomKeyHint.startsWith("REQ_REJECTED::")

    @get:Exclude
    val isPendingRequest: Boolean
        get() = !isGroup && (status == RoomStatus.PENDING || legacyPending)

    @get:Exclude
    val isRejectedRequest: Boolean
        get() = !isGroup && (status == RoomStatus.REJECTED || legacyRejected)

    @get:Exclude
    val isApprovedConnection: Boolean
        get() = isGroup || (!isPendingRequest && !isRejectedRequest)

    @get:Exclude
    val isLegacy: Boolean
        get() = status.isBlank()

    @get:Exclude
    val activityMillis: Long
        get() = (lastActivityAt ?: updatedAt ?: createdAt)?.toDate()?.time ?: 0L

    @get:Exclude
    val participantHandles: List<String>
        get() = participantNames.split(",").map { it.trim().removePrefix("@") }.filter { it.isNotEmpty() }

    fun isIncomingRequestFor(myUid: String?): Boolean =
        isPendingRequest && myUid != null && requesterUid.isNotBlank() && requesterUid != myUid

    fun isOutgoingRequestFrom(myUid: String?): Boolean =
        isPendingRequest && myUid != null && requesterUid == myUid

    fun peerUidFor(myUid: String?): String? =
        if (isGroup) null else participantUids.firstOrNull { it != myUid }

    fun peerHandleFor(myUsername: String?): String {
        if (isGroup) return title
        val me = myUsername?.trim()?.removePrefix("@")?.lowercase().orEmpty()
        val other = participantHandles.firstOrNull { it.lowercase() != me }
        if (!other.isNullOrBlank()) return other
        return title.trim().removePrefix("@")
    }

    fun displayTitleFor(myUsername: String?): String {
        if (isGroup) return title
        val peer = peerHandleFor(myUsername)
        return if (peer.isNotBlank()) "@$peer" else title
    }
}

/** `rooms/{roomId}/keys/{version}`: the room key wrapped for every participant. */
@IgnoreExtraProperties
data class RoomKeyVersion(
    @DocumentId val id: String = "",
    val createdBy: String = "",
    /** uid -> ECIES-wrapped 32-byte room key. */
    val wrappedKeys: Map<String, String> = emptyMap(),
    /** uid -> identity key id the key was wrapped for (detects identity changes). */
    val recipientKeyIds: Map<String, String> = emptyMap(),
    val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class EncryptedEnvelope(
    @DocumentId val id: String = "",
    val roomId: String = "",
    val senderId: String = "",
    val participantUids: List<String> = emptyList(),
    /** 2 for the current protocol; 0 for legacy envelopes. */
    val v: Long = 0L,
    val keyVersion: Long = 0L,
    val iv: String = "",
    val ciphertext: String = "",
    /** ECDSA signature by the sender over AAD + iv + ciphertext. */
    val sig: String = "",
    /** Identity key id of the signing key, to explain signature failures after key changes. */
    val signerKeyId: String = "",
    val ttlSeconds: Long = 0L,
    val expireAt: Timestamp? = null,
    val edited: Boolean = false,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null,
    /** Legacy V1 fields. */
    val senderUsername: String = "",
    val signatureHash: String = "",
    val messageType: String = "",
    val encryptedMeta: String = "",
    val reactionsSummary: String = ""
)

object AnnotationKind {
    const val REACTION = "REACTION"
    const val VOTE = "VOTE"
    const val PIN = "PIN"
}

/**
 * `rooms/{roomId}/annotations/{envelopeId}_{uid}_{kind}`: one document per user, message and
 * kind, so concurrent reactions / votes never overwrite each other. Values are encrypted.
 */
@IgnoreExtraProperties
data class RoomAnnotation(
    @DocumentId val id: String = "",
    val envelopeId: String = "",
    val uid: String = "",
    val kind: String = "",
    val keyVersion: Long = 0L,
    val iv: String = "",
    val ciphertext: String = "",
    val updatedAt: Timestamp? = null
)

/** `rooms/{roomId}/presence/{uid}`: read position and typing indicator of one participant. */
@IgnoreExtraProperties
data class RoomPresence(
    @DocumentId val uid: String = "",
    val lastReadAt: Timestamp? = null,
    val typingAt: Timestamp? = null
)

@IgnoreExtraProperties
data class EncryptedStatus(
    @DocumentId val id: String = "",
    val userId: String = "",
    val audienceUids: List<String> = emptyList(),
    /** uid -> ECIES-wrapped per-status key. */
    val wrappedKeys: Map<String, String> = emptyMap(),
    val iv: String = "",
    val ciphertext: String = "",
    val sig: String = "",
    val themeColorHex: String = "#082E1E",
    val createdAt: Timestamp? = null,
    val expireAt: Timestamp? = null
)

object CallStatus {
    const val RINGING = "RINGING"
    const val CONNECTED = "CONNECTED"
    const val ENDED = "ENDED"
}

@IgnoreExtraProperties
data class CallSignal(
    @DocumentId val id: String = "",
    val callerId: String = "",
    val calleeUid: String = "",
    val roomId: String = "",
    val keyVersion: Long = 0L,
    val participantUids: List<String> = emptyList(),
    val callType: String = "AUDIO",
    val status: String = CallStatus.RINGING,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null,
    // ---- filled by the ViewModel
    @get:Exclude val callerUsername: String = "",
    @get:Exclude val calleeUsername: String = "",
    @get:Exclude val safetyEmojis: String = "",
    @get:Exclude val remoteAudioB64: String = "",
    @get:Exclude val remoteVideoB64: String = "",
    @get:Exclude val remoteSeq: Long = 0L
)

/** `calls/{callId}/media/{uid}`: the latest encrypted media chunk from one side of a call. */
@IgnoreExtraProperties
data class CallMediaChunk(
    @DocumentId val uid: String = "",
    val seq: Long = 0L,
    val iv: String = "",
    val ciphertext: String = "",
    val updatedAt: Timestamp? = null
)

data class PollOptionItem(
    val index: Int,
    val text: String,
    val voterNames: List<String>
)

data class DecryptedMessageItem(
    val id: String,
    val roomId: String,
    val senderId: String,
    val senderUsername: String,
    val decryptedText: String,
    val ciphertext: String,
    val iv: String,
    val signatureHash: String,
    val messageType: String,
    /** Decrypted metadata JSON (media, poll, reply, file...). Never stored in plaintext remotely. */
    val encryptedMeta: String,
    val reactions: Map<String, String>,
    val ttlSeconds: Long,
    val createdAtMillis: Long,
    val remainingSeconds: Long? = null,
    val replyToSender: String? = null,
    val replyToPreview: String? = null,
    val isPinned: Boolean = false,
    val isEdited: Boolean = false,
    val forwardedFrom: String? = null,
    val readByCount: Int = 1,
    val ratchetStep: Int = 0,
    val audioBase64: String = "",
    val fileName: String = "",
    val fileSizeKb: Int = 0,
    val fileBase64: String = "",
    val fileMime: String = "application/octet-stream",
    val isViewOnce: Boolean = false,
    val isSpoiler: Boolean = false,
    val isStarred: Boolean = false,
    val stegoPayload: String = "",
    /** Message decrypted with the insecure pre-V2 scheme. */
    val isLegacy: Boolean = false,
    /** Signature checked against the sender's current signing key. */
    val isSignatureVerified: Boolean = false,
    /** Signed with a key the sender no longer uses (e.g. reinstall); not proof of tampering. */
    val isSignerKeyChanged: Boolean = false
)

data class DecryptedStatusItem(
    val id: String,
    val userId: String,
    val authorUsername: String,
    val decryptedContent: String,
    val ciphertext: String,
    val iv: String,
    val themeColorHex: String,
    val viewCount: Long,
    val createdAtMillis: Long,
    val viewerNames: List<String> = emptyList()
)
