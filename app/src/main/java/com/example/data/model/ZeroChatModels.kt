package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val username: String = "",
    val displayName: String = "",
    val bio: String = "",
    val publicKey: String = "",
    val safetyFingerprint: String = "",
    val onlineStatus: String = "ONLINE",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ChatRoom(
    @DocumentId val id: String = "",
    val ownerId: String = "",
    val participantUids: List<String> = emptyList(),
    val participantNames: String = "",
    val title: String = "",
    val isGroup: Boolean = false,
    val roomKeyHint: String = "",
    val lastCipherPreview: String = "",
    val lastSenderName: String = "",
    val defaultTtlSeconds: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val isPendingRequest: Boolean
        get() = !isGroup && roomKeyHint.startsWith("REQ_FROM::")

    val isRejectedRequest: Boolean
        get() = !isGroup && roomKeyHint.startsWith("REQ_REJECTED::")

    val isApprovedConnection: Boolean
        get() = isGroup || (!isPendingRequest && !isRejectedRequest)

    val participantHandles: List<String>
        get() = participantNames.split(",").map { it.trim().removePrefix("@") }.filter { it.isNotEmpty() }

    val requesterUsername: String
        get() = if (roomKeyHint.startsWith("REQ_FROM::") || roomKeyHint.startsWith("REQ_REJECTED::")) {
            roomKeyHint.split("::").getOrNull(1).orEmpty()
        } else {
            ""
        }

    val effectiveRoomKeyHint: String
        get() = when {
            roomKeyHint.startsWith("REQ_FROM::") -> {
                val parts = roomKeyHint.split("::")
                if (parts.size >= 3) parts.drop(2).joinToString("::") else roomKeyHint
            }
            roomKeyHint.startsWith("REQ_REJECTED::") -> {
                val parts = roomKeyHint.split("::")
                if (parts.size >= 3) parts.drop(2).joinToString("::") else roomKeyHint
            }
            else -> roomKeyHint
        }

    fun isIncomingRequestFor(myUsername: String?): Boolean {
        if (!isPendingRequest) return false
        val me = myUsername?.trim()?.lowercase() ?: return false
        return requesterUsername.trim().lowercase() != me
    }

    fun isOutgoingRequestFrom(myUsername: String?): Boolean {
        if (!isPendingRequest) return false
        val me = myUsername?.trim()?.lowercase() ?: return true
        return requesterUsername.trim().lowercase() == me
    }

    fun peerHandleFor(myUsername: String?): String {
        if (isGroup) return title
        val me = myUsername?.trim()?.removePrefix("@")?.lowercase().orEmpty()
        val otherFromList = participantHandles.firstOrNull { it.lowercase() != me }
        if (!otherFromList.isNullOrBlank()) return otherFromList
        return title.trim().removePrefix("@")
    }

    fun displayTitleFor(myUsername: String?): String {
        if (isGroup) return title
        val peer = peerHandleFor(myUsername)
        return if (peer.isNotBlank()) "@$peer" else title
    }
}

@IgnoreExtraProperties
data class EncryptedEnvelope(
    @DocumentId val id: String = "",
    val roomId: String = "",
    val senderId: String = "",
    val senderUsername: String = "",
    val participantUids: List<String> = emptyList(),
    val ciphertext: String = "",
    val iv: String = "",
    val signatureHash: String = "",
    val messageType: String = "TEXT",
    val encryptedMeta: String = "{}",
    val reactionsSummary: String = "",
    val ttlSeconds: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class EncryptedStatus(
    @DocumentId val id: String = "",
    val userId: String = "",
    val authorUsername: String = "",
    val ciphertext: String = "",
    val iv: String = "",
    val themeColorHex: String = "#082E1E",
    val viewCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CallSignal(
    @DocumentId val id: String = "",
    val callerId: String = "",
    val callerUsername: String = "",
    val calleeUsername: String = "",
    val participantUids: List<String> = emptyList(),
    val callType: String = "AUDIO",
    val status: String = "RINGING",
    val safetyEmojis: String = "🛡️  ⚡  🔑  🔒",
    val callerAudioB64: String = "",
    val calleeAudioB64: String = "",
    val callerVideoB64: String = "",
    val calleeVideoB64: String = "",
    val mediaSeq: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
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
    val stegoPayload: String = ""
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
    val createdAtMillis: Long
)
