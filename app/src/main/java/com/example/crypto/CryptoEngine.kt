package com.example.crypto

import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** AES-GCM output: base64 IV and base64 ciphertext (with the 128-bit tag appended). */
data class SealedBox(val iv: String, val ciphertext: String)

/**
 * Cryptographic primitives and the ZeroChat V2 end-to-end protocol helpers.
 *
 * Protocol summary (see CLAUDE.md for the full model):
 * - Every user has two P-256 identity key pairs: one for ECDH (key wrapping) and one for ECDSA
 *   (message signatures). Only the public halves are published in `users/{uid}`.
 * - Every room has random 256-bit keys, one per `keyVersion`. Each version is stored in
 *   `rooms/{roomId}/keys/{version}` wrapped separately for every participant with ECIES
 *   (ephemeral P-256 ECDH + HKDF-SHA256 + AES-256-GCM). The server never sees a usable key.
 * - Message payloads are AES-256-GCM encrypted with the room key and bound to their routing
 *   metadata through AAD, then signed with the sender's ECDSA key.
 */
object CryptoEngine {
    private const val AES_MODE = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val GCM_IV_LENGTH_BYTES = 12
    private const val EC_CURVE = "secp256r1"

    const val PROTOCOL_VERSION = 2
    const val PIN_PBKDF2_ITERATIONS = 120_000
    const val BACKUP_PBKDF2_ITERATIONS = 310_000
    const val MIN_BACKUP_PASSWORD_LENGTH = 10
    const val MIN_PIN_LENGTH = 6

    private val secureRandom = SecureRandom()

    private val verificationEmojisPool = listOf(
        "🛡️", "⚡", "🔑", "🛰️", "🧬", "🔮", "🪐", "🔒",
        "🐉", "🦅", "💎", "🔥", "🌊", "🍀", "⚓", "🎯"
    )

    // ---------------------------------------------------------------- encoding & hashing

    fun b64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    fun unb64(value: String): ByteArray = Base64.decode(value, Base64.NO_WRAP)

    fun randomBytes(size: Int): ByteArray = ByteArray(size).also { secureRandom.nextBytes(it) }

    fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

    fun sha256Hex(input: String): String = sha256(input.utf8()).toHex()

    fun String.utf8(): ByteArray = toByteArray(StandardCharsets.UTF_8)

    fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    /** HKDF-SHA256 (RFC 5869). */
    fun hkdf(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int = 32): ByteArray {
        val extractMac = Mac.getInstance("HmacSHA256")
        extractMac.init(SecretKeySpec(if (salt.isEmpty()) ByteArray(32) else salt, "HmacSHA256"))
        val prk = extractMac.doFinal(ikm)

        val expandMac = Mac.getInstance("HmacSHA256")
        expandMac.init(SecretKeySpec(prk, "HmacSHA256"))
        val out = ByteArray(length)
        var previous = ByteArray(0)
        var offset = 0
        var counter = 1
        while (offset < length) {
            expandMac.update(previous)
            expandMac.update(info)
            expandMac.update(counter.toByte())
            previous = expandMac.doFinal()
            val toCopy = minOf(previous.size, length - offset)
            System.arraycopy(previous, 0, out, offset, toCopy)
            offset += toCopy
            counter++
        }
        return out
    }

    // ---------------------------------------------------------------- AES-256-GCM

    fun aesGcmEncrypt(key: ByteArray, plaintext: ByteArray, aad: ByteArray? = null): SealedBox {
        require(key.size == 32) { "AES-256 key must be 32 bytes" }
        val iv = randomBytes(GCM_IV_LENGTH_BYTES)
        val cipher = Cipher.getInstance(AES_MODE)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        if (aad != null) cipher.updateAAD(aad)
        return SealedBox(iv = b64(iv), ciphertext = b64(cipher.doFinal(plaintext)))
    }

    /** Throws [javax.crypto.AEADBadTagException] when the key, IV, AAD or ciphertext do not match. */
    fun aesGcmDecrypt(key: ByteArray, ivB64: String, ciphertextB64: String, aad: ByteArray? = null): ByteArray {
        val cipher = Cipher.getInstance(AES_MODE)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_LENGTH_BITS, unb64(ivB64)))
        if (aad != null) cipher.updateAAD(aad)
        return cipher.doFinal(unb64(ciphertextB64))
    }

    // ---------------------------------------------------------------- P-256 keys

    fun generateEcKeyPair(): KeyPair = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec(EC_CURVE), secureRandom)
    }.generateKeyPair()

    fun encodePublicKey(key: PublicKey): String = b64(key.encoded)

    fun decodePublicKey(b64Key: String): PublicKey =
        KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(unb64(b64Key)))

    fun decodePrivateKey(pkcs8: ByteArray): PrivateKey =
        KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(pkcs8))

    fun ecdh(privateKey: PrivateKey, peerPublicKey: PublicKey): ByteArray {
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(privateKey)
        agreement.doPhase(peerPublicKey, true)
        return agreement.generateSecret()
    }

    fun sign(privateKey: PrivateKey, data: ByteArray): String {
        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(privateKey)
        signer.update(data)
        return b64(signer.sign())
    }

    fun verify(publicKeyB64: String, data: ByteArray, signatureB64: String): Boolean = try {
        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(decodePublicKey(publicKeyB64))
        verifier.update(data)
        verifier.verify(unb64(signatureB64))
    } catch (_: Exception) {
        false
    }

    // ---------------------------------------------------------------- ECIES key wrapping

    /**
     * Wraps [secret] for the holder of [recipientPublicKeyB64]. Output format:
     * `ephemeralPublicKey.iv.ciphertext` (base64 parts). [context] is bound as AAD and HKDF info,
     * so a wrapped key cannot be replayed into another room, version or recipient.
     */
    fun wrapKey(secret: ByteArray, recipientPublicKeyB64: String, context: String): String {
        val ephemeral = generateEcKeyPair()
        val shared = ecdh(ephemeral.private, decodePublicKey(recipientPublicKeyB64))
        val ephPub = encodePublicKey(ephemeral.public)
        val kek = hkdf(shared, ephPub.utf8(), "ZC_WRAP_V2|$context".utf8())
        val box = aesGcmEncrypt(kek, secret, context.utf8())
        return "$ephPub.${box.iv}.${box.ciphertext}"
    }

    fun unwrapKey(wrapped: String, recipientPrivateKey: PrivateKey, context: String): ByteArray {
        val parts = wrapped.split(".")
        require(parts.size == 3) { "Invalid wrapped key" }
        val shared = ecdh(recipientPrivateKey, decodePublicKey(parts[0]))
        val kek = hkdf(shared, parts[0].utf8(), "ZC_WRAP_V2|$context".utf8())
        return aesGcmDecrypt(kek, parts[1], parts[2], context.utf8())
    }

    fun roomKeyWrapContext(roomId: String, keyVersion: Long, recipientUid: String): String =
        "room|$roomId|$keyVersion|$recipientUid"

    fun statusKeyWrapContext(statusId: String, recipientUid: String): String =
        "status|$statusId|$recipientUid"

    /** Per-call media key derived from the room key, so each call uses fresh key material. */
    fun deriveCallKey(roomKey: ByteArray, callId: String): ByteArray =
        hkdf(roomKey, callId.utf8(), "ZC_CALL_V2".utf8())

    // ---------------------------------------------------------------- fingerprints & safety numbers

    /** Short stable id of an identity, derived from both public keys. */
    fun identityKeyId(dhPublicKeyB64: String, signingKeyB64: String): String =
        sha256Hex("ZC_ID_V2|$dhPublicKeyB64|$signingKeyB64").take(16)

    /** 60-digit fingerprint of one identity (shown on the user's own QR / profile). */
    fun fingerprint(dhPublicKeyB64: String, signingKeyB64: String): String =
        digitsFrom(sha256("ZC_FP_V2|$dhPublicKeyB64|$signingKeyB64".utf8()), iterations = 12)

    /**
     * Safety number for a pair of identities. Both sides compute the same value because the
     * per-user parts are sorted. It changes whenever either user's identity keys change.
     */
    fun safetyNumber(
        uidA: String, dhA: String, signA: String,
        uidB: String, dhB: String, signB: String
    ): String {
        val parts = listOf("$uidA|$dhA|$signA", "$uidB|$dhB|$signB").sorted()
        return digitsFrom(sha256("ZC_SAFETY_V2|${parts[0]}|${parts[1]}".utf8()), iterations = 12)
    }

    fun safetyEmojis(
        uidA: String, dhA: String, signA: String,
        uidB: String, dhB: String, signB: String
    ): String {
        val parts = listOf("$uidA|$dhA|$signA", "$uidB|$dhB|$signB").sorted()
        val bytes = sha256("ZC_EMOJI_V2|${parts[0]}|${parts[1]}".utf8())
        return (0 until 4).joinToString("  ") { idx ->
            verificationEmojisPool[(bytes[idx].toInt() and 0xFF) % verificationEmojisPool.size]
        }
    }

    private fun digitsFrom(seed: ByteArray, iterations: Int): String {
        val builder = StringBuilder()
        for (i in 0 until iterations) {
            val block = sha256(seed + byteArrayOf(i.toByte()))
            val num = ((block[0].toInt() and 0xFF) shl 16) or
                ((block[1].toInt() and 0xFF) shl 8) or
                (block[2].toInt() and 0xFF)
            if (i > 0) builder.append(if (i % 4 == 0) "\n" else " ")
            builder.append((num % 100000).toString().padStart(5, '0'))
        }
        return builder.toString()
    }

    // ---------------------------------------------------------------- PIN hashing

    fun newSaltB64(): String = b64(randomBytes(16))

    /** Salted PBKDF2-HMAC-SHA256 PIN hash. Returns `iterations$hashB64`. */
    fun hashPin(pin: String, saltB64: String, iterations: Int = PIN_PBKDF2_ITERATIONS): String {
        val hash = pbkdf2(pin, unb64(saltB64), iterations)
        return "$iterations$${b64(hash)}"
    }

    fun verifyPin(pin: String, saltB64: String, storedHash: String): Boolean {
        val iterations = storedHash.substringBefore('$').toIntOrNull() ?: return false
        val candidate = hashPin(pin, saltB64, iterations)
        return MessageDigest.isEqual(candidate.utf8(), storedHash.utf8())
    }

    private fun pbkdf2(password: String, salt: ByteArray, iterations: Int): ByteArray {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, 256)
        return try {
            factory.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    // ---------------------------------------------------------------- password-protected backups

    /** `ZCVAULT_V2::iterations::salt::iv::ciphertext` (PBKDF2-HMAC-SHA256 + AES-256-GCM). */
    fun encryptBackupArchive(jsonPlaintext: String, password: String): String {
        require(password.length >= MIN_BACKUP_PASSWORD_LENGTH) { "Backup password too short" }
        val salt = randomBytes(16)
        val key = pbkdf2(password, salt, BACKUP_PBKDF2_ITERATIONS)
        val box = aesGcmEncrypt(key, jsonPlaintext.utf8(), "ZCVAULT_V2".utf8())
        return listOf("ZCVAULT_V2", BACKUP_PBKDF2_ITERATIONS.toString(), b64(salt), box.iv, box.ciphertext)
            .joinToString("::")
    }

    fun decryptBackupArchive(vaultPayload: String, password: String): Result<String> = runCatching {
        val parts = vaultPayload.trim().split("::")
        when (parts.firstOrNull()) {
            "ZCVAULT_V2" -> {
                require(parts.size == 5) { "Bozuk .zcvault yedek başlığı" }
                val iterations = parts[1].toInt()
                require(iterations in 10_000..5_000_000) { "Geçersiz yedek parametresi" }
                val key = pbkdf2(password, unb64(parts[2]), iterations)
                String(aesGcmDecrypt(key, parts[3], parts[4], "ZCVAULT_V2".utf8()), StandardCharsets.UTF_8)
            }
            "ZCVAULT_V1" -> {
                require(parts.size == 4) { "Bozuk .zcvault yedek başlığı" }
                val key = pbkdf2(password, unb64(parts[1]), 10_000)
                String(aesGcmDecrypt(key, parts[2], parts[3]), StandardCharsets.UTF_8)
            }
            else -> throw IllegalArgumentException("Geçersiz .zcvault yedek formatı")
        }
    }
}
