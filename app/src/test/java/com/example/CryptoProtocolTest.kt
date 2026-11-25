package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.crypto.CryptoEngine
import com.example.crypto.IdentityKeyManager
import com.example.crypto.IdentityKeys
import com.example.crypto.InMemorySecretWrapper
import com.example.crypto.LegacyCrypto
import com.example.crypto.MessageCodec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CryptoProtocolTest {

    @Test
    fun appNameResolves() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("ZeroChat", context.getString(R.string.app_name))
    }

    @Test
    fun roomKeyWrapsForRecipientOnly() {
        val alice = IdentityKeys.generate("alice")
        val eve = IdentityKeys.generate("eve")
        val roomKey = CryptoEngine.randomBytes(32)
        val context = CryptoEngine.roomKeyWrapContext("room1", 1L, "alice")
        val wrapped = CryptoEngine.wrapKey(roomKey, alice.publicKey, context)

        assertArrayEquals(roomKey, alice.unwrapKey(wrapped, context))
        // Another identity cannot unwrap it.
        assertThrows(Exception::class.java) { eve.unwrapKey(wrapped, context) }
        // Replaying the wrap into another room / version / recipient fails.
        assertThrows(Exception::class.java) {
            alice.unwrapKey(wrapped, CryptoEngine.roomKeyWrapContext("room2", 1L, "alice"))
        }
        assertThrows(Exception::class.java) {
            alice.unwrapKey(wrapped, CryptoEngine.roomKeyWrapContext("room1", 2L, "alice"))
        }
    }

    @Test
    fun envelopeRoundTripAndTamperDetection() {
        val alice = IdentityKeys.generate("alice")
        val key = CryptoEngine.randomBytes(32)
        val payload = """{"type":"TEXT","text":"Merhaba Bob, şifreli 🔒"}"""
        val sealed = MessageCodec.sealEnvelope(key, alice, "room1", "env_1", 1L, payload)

        assertEquals(payload, MessageCodec.openEnvelope(key, "room1", "env_1", "alice", 1L, sealed.iv, sealed.ciphertext))
        assertTrue(MessageCodec.verifyEnvelope(alice.signingKey, "room1", "env_1", "alice", 1L, sealed.iv, sealed.ciphertext, sealed.sig))

        // Moving the ciphertext to another room / envelope / sender breaks the AAD.
        assertThrows(Exception::class.java) {
            MessageCodec.openEnvelope(key, "room2", "env_1", "alice", 1L, sealed.iv, sealed.ciphertext)
        }
        assertThrows(Exception::class.java) {
            MessageCodec.openEnvelope(key, "room1", "env_1", "bob", 1L, sealed.iv, sealed.ciphertext)
        }
        // A signature from another identity is rejected.
        val bob = IdentityKeys.generate("bob")
        assertFalse(MessageCodec.verifyEnvelope(bob.signingKey, "room1", "env_1", "alice", 1L, sealed.iv, sealed.ciphertext, sealed.sig))
        // A re-encrypted (edited by someone else) ciphertext no longer matches the signature.
        val forged = MessageCodec.sealEnvelope(key, bob, "room1", "env_1", 1L, """{"text":"forged"}""")
        assertFalse(MessageCodec.verifyEnvelope(alice.signingKey, "room1", "env_1", "alice", 1L, forged.iv, forged.ciphertext, sealed.sig))
    }

    @Test
    fun safetyNumberIsSymmetricAndChangesWithKeys() {
        val alice = IdentityKeys.generate("alice")
        val bob = IdentityKeys.generate("bob")
        val ab = CryptoEngine.safetyNumber("alice", alice.publicKey, alice.signingKey, "bob", bob.publicKey, bob.signingKey)
        val ba = CryptoEngine.safetyNumber("bob", bob.publicKey, bob.signingKey, "alice", alice.publicKey, alice.signingKey)
        assertEquals(ab, ba)
        assertEquals(60, ab.count { it.isDigit() })

        val bobReinstalled = IdentityKeys.generate("bob")
        val changed = CryptoEngine.safetyNumber("alice", alice.publicKey, alice.signingKey, "bob", bobReinstalled.publicKey, bobReinstalled.signingKey)
        assertNotEquals(ab, changed)
        assertEquals(
            CryptoEngine.safetyEmojis("alice", alice.publicKey, alice.signingKey, "bob", bob.publicKey, bob.signingKey),
            CryptoEngine.safetyEmojis("bob", bob.publicKey, bob.signingKey, "alice", alice.publicKey, alice.signingKey)
        )
    }

    @Test
    fun identityPersistsAcrossRestarts() {
        val dir: File = Files.createTempDirectory("zc_identity").toFile()
        val wrapper = InMemorySecretWrapper()
        val first = IdentityKeyManager(dir) { wrapper }.getOrCreate("alice")
        val second = IdentityKeyManager(dir) { wrapper }.getOrCreate("alice")
        assertEquals(first.publicKey, second.publicKey)
        assertEquals(first.signingKey, second.signingKey)
        assertNotEquals(first.publicKey, IdentityKeyManager(dir) { wrapper }.getOrCreate("bob").publicKey)
        dir.deleteRecursively()
    }

    @Test
    fun pinHashIsSaltedAndVerifies() {
        val saltA = CryptoEngine.newSaltB64()
        val saltB = CryptoEngine.newSaltB64()
        val hashA = CryptoEngine.hashPin("482913", saltA, iterations = 1000)
        assertNotEquals(hashA, CryptoEngine.hashPin("482913", saltB, iterations = 1000))
        assertTrue(CryptoEngine.verifyPin("482913", saltA, hashA))
        assertFalse(CryptoEngine.verifyPin("482914", saltA, hashA))
    }

    @Test
    fun backupRoundTripAndWrongPassword() {
        val archive = CryptoEngine.encryptBackupArchive("""{"messages":[]}""", "correct horse battery")
        assertTrue(archive.startsWith("ZCVAULT_V2::"))
        assertEquals("""{"messages":[]}""", CryptoEngine.decryptBackupArchive(archive, "correct horse battery").getOrThrow())
        assertTrue(CryptoEngine.decryptBackupArchive(archive, "wrong password!!").isFailure)
        assertThrows(IllegalArgumentException::class.java) { CryptoEngine.encryptBackupArchive("{}", "short") }
    }

    @Test
    fun hkdfMatchesRfc5869TestCase1() {
        val ikm = ByteArray(22) { 0x0b }
        val salt = ByteArray(13) { it.toByte() }
        val info = ByteArray(10) { (0xf0 + it).toByte() }
        val okm = CryptoEngine.hkdf(ikm, salt, info, 42)
        val expected = "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865"
        assertEquals(expected, okm.joinToString("") { "%02x".format(it) })
    }

    @Test
    fun legacyMessagesStillDecrypt() {
        // Reproduces how V1 encrypted: key = SHA-256("ZEROCHAT_E2EE_V1::" + hint).
        val hint = "DIRECT_abc123"
        val key = CryptoEngine.sha256("ZEROCHAT_E2EE_V1::$hint".toByteArray(StandardCharsets.UTF_8))
        val box = CryptoEngine.aesGcmEncrypt(key, "eski mesaj".toByteArray(StandardCharsets.UTF_8))
        assertEquals("eski mesaj", LegacyCrypto.decrypt(box.ciphertext, box.iv, hint))
        assertEquals("eski mesaj", LegacyCrypto.decrypt(box.ciphertext, box.iv, "REQ_FROM::alice::$hint"))
        assertNull(LegacyCrypto.decrypt(box.ciphertext, box.iv, "DIRECT_other"))
    }
}
