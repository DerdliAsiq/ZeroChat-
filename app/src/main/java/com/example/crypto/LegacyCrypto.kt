package com.example.crypto

import java.nio.charset.StandardCharsets

/**
 * Read-only support for messages written by the pre-V2 protocol.
 *
 * V1 derived the AES key from `roomKeyHint`, a value stored in plaintext on the room document,
 * so V1 messages were never confidential against anyone who could read the database. New
 * messages are never written with this scheme; it only keeps old history readable and is
 * surfaced in the UI as "eski şifreleme".
 */
object LegacyCrypto {

    fun stripKeyHintPrefix(rawKeyHint: String): String =
        if (rawKeyHint.startsWith("REQ_FROM::") || rawKeyHint.startsWith("REQ_REJECTED::")) {
            val parts = rawKeyHint.split("::")
            if (parts.size >= 3) parts.drop(2).joinToString("::") else rawKeyHint
        } else {
            rawKeyHint
        }

    private fun legacyKey(roomKeyHint: String, ratchetStep: Int): ByteArray {
        val seed = if (ratchetStep <= 0) {
            "ZEROCHAT_E2EE_V1::$roomKeyHint"
        } else {
            "ZEROCHAT_E2EE_RATCHET_V2::$roomKeyHint::STEP_$ratchetStep"
        }
        return CryptoEngine.sha256(seed.toByteArray(StandardCharsets.UTF_8))
    }

    /** Returns null when the payload cannot be decrypted with the legacy key. */
    fun decrypt(ciphertextB64: String, ivB64: String, roomKeyHint: String, ratchetStep: Int = 0): String? {
        if (ciphertextB64.isBlank() || ivB64.isBlank() || roomKeyHint.isBlank()) return null
        val hints = listOf(roomKeyHint, stripKeyHintPrefix(roomKeyHint)).distinct()
        val steps = listOf(ratchetStep, 0).distinct()
        for (hint in hints) {
            for (step in steps) {
                try {
                    val plain = CryptoEngine.aesGcmDecrypt(legacyKey(hint, step), ivB64, ciphertextB64)
                    return String(plain, StandardCharsets.UTF_8)
                } catch (_: Exception) {
                }
            }
        }
        return null
    }

    /** Legacy room-list preview packet: `ZCPREV_V1::iv::ciphertext`. */
    fun decryptPreviewPacket(previewPacket: String, roomKeyHint: String): String? {
        val parts = previewPacket.trim().split("::")
        if (parts.size < 3 || parts[0] != "ZCPREV_V1") return null
        return decrypt(parts[2], parts[1], stripKeyHintPrefix(roomKeyHint), 0)
    }
}
