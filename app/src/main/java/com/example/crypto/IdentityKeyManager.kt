package com.example.crypto

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.KeyPair
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec

/** Encrypts small secrets at rest (identity private keys, the local database passphrase). */
interface SecretWrapper {
    val isHardwareBacked: Boolean
    fun wrap(plaintext: ByteArray): ByteArray
    fun unwrap(blob: ByteArray): ByteArray
}

/**
 * AES-256-GCM key that never leaves the Android Keystore. Works from API 23, so it is the
 * storage layer for identity keys on every supported device (ECDH in the Keystore itself needs
 * API 31).
 */
class AndroidKeystoreSecretWrapper(private val alias: String) : SecretWrapper {

    private val keyStore: KeyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }

    private fun secretKey(): SecretKey {
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    override val isHardwareBacked: Boolean by lazy {
        try {
            val key = secretKey()
            val info = SecretKeyFactory.getInstance(key.algorithm, PROVIDER)
                .getKeySpec(key, KeyInfo::class.java) as KeyInfo
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                info.securityLevel == KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT ||
                    info.securityLevel == KeyProperties.SECURITY_LEVEL_STRONGBOX
            } else {
                @Suppress("DEPRECATION")
                info.isInsideSecureHardware
            }
        } catch (_: Exception) {
            false
        }
    }

    override fun wrap(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        return byteArrayOf(iv.size.toByte()) + iv + cipher.doFinal(plaintext)
    }

    override fun unwrap(blob: ByteArray): ByteArray {
        val ivSize = blob[0].toInt()
        val iv = blob.copyOfRange(1, 1 + ivSize)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(blob, 1 + ivSize, blob.size - 1 - ivSize)
    }

    private companion object {
        const val PROVIDER = "AndroidKeyStore"
    }
}

/** Test-only wrapper; keeps its key in memory. */
class InMemorySecretWrapper : SecretWrapper {
    private val key = CryptoEngine.randomBytes(32)
    override val isHardwareBacked: Boolean = false
    override fun wrap(plaintext: ByteArray): ByteArray {
        val box = CryptoEngine.aesGcmEncrypt(key, plaintext)
        return "${box.iv}.${box.ciphertext}".toByteArray(StandardCharsets.UTF_8)
    }

    override fun unwrap(blob: ByteArray): ByteArray {
        val (iv, ct) = String(blob, StandardCharsets.UTF_8).split(".")
        return CryptoEngine.aesGcmDecrypt(key, iv, ct)
    }
}

/** What the profile screen shows about the local identity keys. */
data class HardwareKeyStatus(
    val algorithm: String,
    val isHardwareBacked: Boolean,
    val publicKeyBase64: String,
    val safetyFingerprint: String
)

/** A user's long-term identity on this device. */
class IdentityKeys(
    val uid: String,
    private val dhKeyPair: KeyPair,
    private val signingKeyPair: KeyPair,
    val isHardwareProtected: Boolean
) {
    val publicKey: String = CryptoEngine.encodePublicKey(dhKeyPair.public)
    val signingKey: String = CryptoEngine.encodePublicKey(signingKeyPair.public)
    val keyId: String = CryptoEngine.identityKeyId(publicKey, signingKey)
    val fingerprint: String = CryptoEngine.fingerprint(publicKey, signingKey)

    fun unwrapKey(wrapped: String, context: String): ByteArray =
        CryptoEngine.unwrapKey(wrapped, dhKeyPair.private, context)

    fun sign(data: ByteArray): String = CryptoEngine.sign(signingKeyPair.private, data)

    fun status(): HardwareKeyStatus = HardwareKeyStatus(
        algorithm = if (isHardwareProtected) {
            "P-256 ECDH + ECDSA (anahtarlar Android Keystore ile korunuyor)"
        } else {
            "P-256 ECDH + ECDSA (yazılım anahtar deposu)"
        },
        isHardwareBacked = isHardwareProtected,
        publicKeyBase64 = publicKey,
        safetyFingerprint = fingerprint
    )

    internal fun serialize(): ByteArray = JSONObject().apply {
        put("v", 2)
        put("dh", CryptoEngine.b64(dhKeyPair.private.encoded))
        put("dhPub", publicKey)
        put("sign", CryptoEngine.b64(signingKeyPair.private.encoded))
        put("signPub", signingKey)
    }.toString().toByteArray(StandardCharsets.UTF_8)

    companion object {
        internal fun deserialize(uid: String, bytes: ByteArray, hardware: Boolean): IdentityKeys {
            val json = JSONObject(String(bytes, StandardCharsets.UTF_8))
            val dh = KeyPair(
                CryptoEngine.decodePublicKey(json.getString("dhPub")),
                CryptoEngine.decodePrivateKey(CryptoEngine.unb64(json.getString("dh")))
            )
            val sign = KeyPair(
                CryptoEngine.decodePublicKey(json.getString("signPub")),
                CryptoEngine.decodePrivateKey(CryptoEngine.unb64(json.getString("sign")))
            )
            return IdentityKeys(uid, dh, sign, hardware)
        }

        fun generate(uid: String, hardware: Boolean = false): IdentityKeys =
            IdentityKeys(uid, CryptoEngine.generateEcKeyPair(), CryptoEngine.generateEcKeyPair(), hardware)
    }
}

/**
 * Loads or creates the identity for a user. Private keys are stored in [directory] encrypted by
 * [wrapper], so the identity survives restarts (but not uninstalls or a new device; history from
 * a previous device is not readable, by design).
 */
class IdentityKeyManager(
    private val directory: File,
    private val wrapperFactory: (uid: String) -> SecretWrapper = { uid ->
        AndroidKeystoreSecretWrapper("zerochat_identity_wrap_$uid")
    }
) {
    private val cache = mutableMapOf<String, IdentityKeys>()

    @Synchronized
    fun getOrCreate(uid: String): IdentityKeys {
        cache[uid]?.let { return it }
        val wrapper = wrapperFactory(uid)
        val file = File(directory, "zc_identity_${CryptoEngine.sha256Hex(uid).take(24)}.bin")
        val loaded = if (file.exists()) {
            runCatching { IdentityKeys.deserialize(uid, wrapper.unwrap(file.readBytes()), wrapper.isHardwareBacked) }
                .getOrNull()
        } else {
            null
        }
        val identity = loaded ?: IdentityKeys.generate(uid, wrapper.isHardwareBacked).also { fresh ->
            directory.mkdirs()
            val tmp = File(directory, file.name + ".tmp")
            tmp.writeBytes(wrapper.wrap(fresh.serialize()))
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        }
        cache[uid] = identity
        return identity
    }

    @Synchronized
    fun forget(uid: String) {
        cache.remove(uid)
    }
}
