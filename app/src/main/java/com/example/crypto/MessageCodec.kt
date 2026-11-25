package com.example.crypto

import java.nio.charset.StandardCharsets

/** A sealed, signed message ready to be stored in an envelope document. */
data class SealedMessage(val iv: String, val ciphertext: String, val sig: String, val signerKeyId: String)

/**
 * Builds the associated data and signatures for every encrypted object in the V2 protocol.
 * AAD binds a ciphertext to its location, so a copy pasted into another room, envelope,
 * annotation or call is rejected on decryption.
 */
object MessageCodec {

    private fun bytes(value: String) = value.toByteArray(StandardCharsets.UTF_8)

    fun envelopeAad(roomId: String, envelopeId: String, senderId: String, keyVersion: Long) =
        bytes("ZC_MSG_V2|$roomId|$envelopeId|$senderId|$keyVersion")

    fun previewAad(roomId: String, keyVersion: Long, senderId: String) =
        bytes("ZC_PREVIEW_V2|$roomId|$keyVersion|$senderId")

    fun annotationAad(roomId: String, annotationId: String, keyVersion: Long) =
        bytes("ZC_ANN_V2|$roomId|$annotationId|$keyVersion")

    fun callMediaAad(callId: String, senderUid: String, seq: Long) =
        bytes("ZC_CALL_V2|$callId|$senderUid|$seq")

    fun statusAad(statusId: String, authorUid: String) =
        bytes("ZC_STATUS_V2|$statusId|$authorUid")

    private fun signedData(aad: ByteArray, iv: String, ciphertext: String): ByteArray =
        aad + bytes("|$iv|$ciphertext")

    fun sealEnvelope(
        roomKey: ByteArray,
        identity: IdentityKeys,
        roomId: String,
        envelopeId: String,
        keyVersion: Long,
        payloadJson: String
    ): SealedMessage {
        val aad = envelopeAad(roomId, envelopeId, identity.uid, keyVersion)
        val box = CryptoEngine.aesGcmEncrypt(roomKey, bytes(payloadJson), aad)
        val sig = identity.sign(signedData(aad, box.iv, box.ciphertext))
        return SealedMessage(box.iv, box.ciphertext, sig, identity.keyId)
    }

    /** Throws when the key or AAD does not match (wrong room / tampered metadata). */
    fun openEnvelope(
        roomKey: ByteArray,
        roomId: String,
        envelopeId: String,
        senderId: String,
        keyVersion: Long,
        iv: String,
        ciphertext: String
    ): String {
        val aad = envelopeAad(roomId, envelopeId, senderId, keyVersion)
        return String(CryptoEngine.aesGcmDecrypt(roomKey, iv, ciphertext, aad), StandardCharsets.UTF_8)
    }

    fun verifyEnvelope(
        senderSigningKey: String,
        roomId: String,
        envelopeId: String,
        senderId: String,
        keyVersion: Long,
        iv: String,
        ciphertext: String,
        sig: String
    ): Boolean {
        if (senderSigningKey.isBlank() || sig.isBlank()) return false
        val aad = envelopeAad(roomId, envelopeId, senderId, keyVersion)
        return CryptoEngine.verify(senderSigningKey, signedData(aad, iv, ciphertext), sig)
    }

    fun sealStatus(statusKey: ByteArray, identity: IdentityKeys, statusId: String, content: String): SealedMessage {
        val aad = statusAad(statusId, identity.uid)
        val box = CryptoEngine.aesGcmEncrypt(statusKey, bytes(content), aad)
        val sig = identity.sign(signedData(aad, box.iv, box.ciphertext))
        return SealedMessage(box.iv, box.ciphertext, sig, identity.keyId)
    }

    fun openStatus(
        statusKey: ByteArray,
        statusId: String,
        authorUid: String,
        iv: String,
        ciphertext: String,
        sig: String,
        authorSigningKey: String
    ): String? {
        val aad = statusAad(statusId, authorUid)
        if (!CryptoEngine.verify(authorSigningKey, signedData(aad, iv, ciphertext), sig)) return null
        return String(CryptoEngine.aesGcmDecrypt(statusKey, iv, ciphertext, aad), StandardCharsets.UTF_8)
    }
}
