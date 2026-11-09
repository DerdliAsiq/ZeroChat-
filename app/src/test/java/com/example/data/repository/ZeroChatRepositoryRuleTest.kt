package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.crypto.CryptoEngine
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class ZeroChatRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun registerProfile_andCreateEncryptedRoom_andSendEnvelope_succeedsForParticipants() = runBlocking {
        val uniqueSuffix = UUID.randomUUID().toString().take(6)
        val aliceUid = signInTestUser("alice_$uniqueSuffix@zerochat.test")
        val repo = ZeroChatRepository(firestore)

        val aliceProfile = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.registerOrUpdateProfile(
                rawUsername = "alice_$uniqueSuffix",
                displayName = "Alice Cipher",
                bio = "Zero-Knowledge Node"
            ).getOrThrow()
        }
        assertEquals(aliceUid, aliceProfile.userId)

        val bobUid = signInTestUser("bob_$uniqueSuffix@zerochat.test")
        val bobProfile = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.registerOrUpdateProfile(
                rawUsername = "bob_$uniqueSuffix",
                displayName = "Bob Node",
                bio = "E2EE Peer"
            ).getOrThrow()
        }
        assertEquals(bobUid, bobProfile.userId)

        // Bob creates a direct encrypted room with Alice
        val room = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.createOrGetDirectRoom(bobProfile, aliceProfile).getOrThrow()
        }
        assertTrue(room.participantUids.contains(aliceUid))
        assertTrue(room.participantUids.contains(bobUid))

        // Bob sends an encrypted envelope
        val secretText = "Merhaba Alice, bu mesaj uçtan uca şifrelidir!"
        val envId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.sendEncryptedMessage(
                room = room,
                senderUsername = bobProfile.username,
                plaintext = secretText
            ).getOrThrow()
        }
        assertTrue(envId.isNotEmpty())

        // Switch back to Alice and verify she can observe and decrypt the envelope
        signInTestUser("alice_$uniqueSuffix@zerochat.test")
        val envelopes = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeRoomEnvelopes(room.id, aliceUid).first { list -> list.any { it.id == envId } }
        }
        val received = envelopes.first { it.id == envId }
        val decrypted = CryptoEngine.decrypt(received.ciphertext, received.iv, room.roomKeyHint)
        assertEquals(secretText, decrypted)
    }

    @Test
    fun rotateRoomKey_andSteganography_succeedsForParticipant() = runBlocking {
        val uniqueSuffix = UUID.randomUUID().toString().take(6)
        signInTestUser("rotator_$uniqueSuffix@zerochat.test")
        val repo = ZeroChatRepository(firestore)

        val ownerProfile = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.registerOrUpdateProfile("rotator_$uniqueSuffix", "Rotator", "Key Node").getOrThrow()
        }
        val room = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.createGroupRoom(ownerProfile, "Siber Operasyon Odası", emptyList()).getOrThrow()
        }

        val rotatedRoom = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.rotateRoomEncryptionKey(room, ownerProfile.username).getOrThrow()
        }
        assertNotEquals(room.roomKeyHint, rotatedRoom.roomKeyHint)

        // Verify Steganography embed & extract roundtrip with the rotated key
        val hiddenMessage = "Stego Koordinat: 41.0082N 28.9784E"
        val packet = CryptoEngine.embedStegoPayload(hiddenMessage, rotatedRoom.roomKeyHint)
        val extracted = CryptoEngine.extractStegoPayload(packet, rotatedRoom.roomKeyHint)
        assertEquals(hiddenMessage, extracted)
    }

    @Test
    fun crossUserRoomAccess_failsWithPermissionDenied() = runBlocking {
        val uniqueSuffix = UUID.randomUUID().toString().take(6)
        signInTestUser("alice2_$uniqueSuffix@zerochat.test")
        val repo = ZeroChatRepository(firestore)
        val aliceProfile = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.registerOrUpdateProfile("alice2_$uniqueSuffix", "Alice", "Bio").getOrThrow()
        }
        val room = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.createGroupRoom(aliceProfile, "Gizli Oda", emptyList()).getOrThrow()
        }

        // Sign in as outsider Eve who is not in participantUids
        signInTestUser("eve_$uniqueSuffix@zerochat.test")
        var caughtError: FirebaseFirestoreException? = null
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("rooms").document(room.id).get().await()
            }
        } catch (e: Exception) {
            caughtError = e.unwrapFirestoreException()
        }
        assertNotNull(caughtError)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, caughtError?.code)
    }

    @Test
    fun unauthenticatedUser_observeRooms_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = ZeroChatRepository(firestore)
        var caughtError: FirebaseFirestoreException? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeUserRooms("unauth_uid").first()
            }
        } catch (e: Exception) {
            caughtError = e.unwrapFirestoreException()
        }
        assertNotNull(caughtError)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, caughtError?.code)
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
