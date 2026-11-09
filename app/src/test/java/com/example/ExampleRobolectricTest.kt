package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.crypto.CryptoEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app_name from context and verify AES-256-GCM roundtrip`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("ZeroChat", appName)

        val secretPlaintext = "ZeroChat uçtan uca şifreli test mesajı!"
        val keySeed = CryptoEngine.computeDirectRoomKeyHint("uid_alice", "uid_bob")
        val encrypted = CryptoEngine.encrypt(secretPlaintext, keySeed)
        assertNotEquals(secretPlaintext, encrypted.ciphertext)

        val decrypted = CryptoEngine.decrypt(encrypted.ciphertext, encrypted.iv, keySeed)
        assertEquals(secretPlaintext, decrypted)
    }
}
