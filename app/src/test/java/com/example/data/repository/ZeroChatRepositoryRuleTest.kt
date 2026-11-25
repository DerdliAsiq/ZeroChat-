package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.crypto.IdentityKeyManager
import com.example.crypto.InMemorySecretWrapper
import com.example.crypto.SecretWrapper
import com.example.data.model.AnnotationKind
import com.example.data.model.ChatRoom
import com.example.data.model.UserProfile
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.UUID

/**
 * End-to-end tests of the V2 protocol against the Firestore + Auth emulators
 * (`npm run emulators`). Each user gets its own repository instance, so nothing can pass by
 * reading another user's local state.
 */
class ZeroChatRepositoryRuleTest : FirestoreEmulatorTestBase() {

    private lateinit var identityDir: File
    private val wrappers = mutableMapOf<String, SecretWrapper>()
    private lateinit var identities: IdentityKeyManager

    @Before
    fun setUpIdentities() {
        identityDir = Files.createTempDirectory("zc_test_identity").toFile()
        identities = IdentityKeyManager(identityDir) { uid -> wrappers.getOrPut(uid) { InMemorySecretWrapper() } }
    }

    @After
    fun cleanUpIdentities() {
        identityDir.deleteRecursively()
    }

    private val suffix = UUID.randomUUID().toString().replace("-", "").take(6)

    private suspend fun signInAs(name: String): Pair<ZeroChatRepository, UserProfile> {
        signInTestUser("${name}_$suffix@zerochat.test")
        val repo = ZeroChatRepository(firestore, identities, auth)
        val profile = withTimeout(TIMEOUT) { repo.ensureProfile("${name}_$suffix", name).getOrThrow() }
        return repo to profile
    }

    @Test
    fun directMessage_requestAcceptSendAndDecrypt() = runBlocking {
        val (aliceRepo, alice) = signInAs("alice")
        val (_, bob) = signInAs("bob")

        signInAs("alice")
        val room = withTimeout(TIMEOUT) { aliceRepo.requestDirectChat(alice, bob, "Merhaba Bob").getOrThrow() }
        assertTrue(room.isPendingRequest)

        val (bobRepo, _) = signInAs("bob")
        val bobRoom = withTimeout(TIMEOUT) { bobRepo.observeUserRooms().first { list -> list.any { it.id == room.id } } }
            .first { it.id == room.id }
        assertTrue(bobRoom.isIncomingRequestFor(bob.userId))

        // The recipient cannot reply before accepting.
        val early = withTimeout(TIMEOUT) { bobRepo.sendMessage(bobRoom, bobRepo.textPayload("hi", bob.username), 0L) }
        assertTrue(early.isFailure)

        // The intro note is readable by the recipient and signed by Alice.
        val intro = withTimeout(TIMEOUT) { bobRepo.observeRoomEnvelopes(room.id).first { it.isNotEmpty() } }.first()
        val opened = bobRepo.openEnvelope(bobRoom, intro)
        assertEquals("Merhaba Bob", opened.payload?.optString("text"))
        assertTrue(opened.isVerified)

        withTimeout(TIMEOUT) { bobRepo.approveConnectionRequest(bobRoom).getOrThrow() }
        val envId = withTimeout(TIMEOUT) {
            bobRepo.sendMessage(bobRoom.copy(status = "ACTIVE"), bobRepo.textPayload("Selam Alice", bob.username), 0L).getOrThrow()
        }

        val (aliceRepo2, _) = signInAs("alice")
        val envelopes = withTimeout(TIMEOUT) { aliceRepo2.observeRoomEnvelopes(room.id).first { list -> list.any { it.id == envId } } }
        val env = envelopes.first { it.id == envId }
        // Nothing readable is stored on the server.
        assertFalse(env.ciphertext.contains("Selam"))
        val aliceRoom = aliceRepo2.observeUserRooms().first().first { it.id == room.id }
        val decrypted = aliceRepo2.openEnvelope(aliceRoom, env)
        assertEquals("Selam Alice", decrypted.payload?.optString("text"))
        assertTrue(decrypted.isVerified)
        assertEquals("Selam Alice", aliceRepo2.decryptPreview(aliceRoom))
    }

    @Test
    fun outsider_cannotReadRoomOrKeys() = runBlocking {
        val (aliceRepo, alice) = signInAs("alice")
        val (_, bob) = signInAs("bob")
        signInAs("alice")
        val room = withTimeout(TIMEOUT) { aliceRepo.requestDirectChat(alice, bob, "gizli").getOrThrow() }
        val (eveRepo, _) = signInAs("eve")
        var error: FirebaseFirestoreException? = null
        try {
            withTimeout(TIMEOUT) { firestore.collection("rooms").document(room.id).get().await() }
        } catch (e: Exception) {
            error = e.unwrapFirestoreException()
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, error?.code)
        assertNull(runCatching { eveRepo.roomKey(room.id, 1L) }.getOrNull())
    }

    @Test
    fun groupMembershipChange_rotatesKey() = runBlocking {
        val (_, bob) = signInAs("bob")
        val (_, carol) = signInAs("carol")
        val (aliceRepo, alice) = signInAs("alice")
        val group = withTimeout(TIMEOUT) { aliceRepo.createGroupRoom(alice, "Ekip", listOf(bob, carol), 0L).getOrThrow() }
        assertEquals(1L, group.keyVersion)

        val updated = withTimeout(TIMEOUT) { aliceRepo.updateGroupMembersAndTitle(group, "Ekip", listOf(bob)).getOrThrow() }
        assertEquals(2L, updated.keyVersion)
        assertEquals(setOf(alice.userId, bob.userId), updated.participantUids.toSet())

        val (bobRepo, _) = signInAs("bob")
        assertNotNull(withTimeout(TIMEOUT) { bobRepo.roomKey(group.id, 2L) })
        val (carolRepo, _) = signInAs("carol")
        assertNull(runCatching { withTimeout(TIMEOUT) { carolRepo.roomKey(group.id, 2L) } }.getOrNull())
    }

    @Test
    fun reactionsAreEncryptedAndPerUser() = runBlocking {
        val (_, bob) = signInAs("bob")
        val (aliceRepo, alice) = signInAs("alice")
        val group = withTimeout(TIMEOUT) { aliceRepo.createGroupRoom(alice, "Tepki", listOf(bob), 0L).getOrThrow() }
        val envId = withTimeout(TIMEOUT) { aliceRepo.sendMessage(group, aliceRepo.textPayload("oyla", alice.username), 0L).getOrThrow() }

        val (bobRepo, _) = signInAs("bob")
        withTimeout(TIMEOUT) { bobRepo.setAnnotation(group, envId, AnnotationKind.REACTION, "🔥").getOrThrow() }

        val (aliceRepo2, _) = signInAs("alice")
        val anns = withTimeout(TIMEOUT) { aliceRepo2.observeAnnotations(group.id).first { it.isNotEmpty() } }
        assertFalse(anns.first().ciphertext.contains("🔥"))
        assertEquals("🔥", aliceRepo2.decryptAnnotation(group.id, anns.first()))
    }

    @Test
    fun usernamesAreUnique() = runBlocking {
        val (_, alice) = signInAs("alice")
        signInTestUser("impostor_$suffix@zerochat.test")
        val impostorRepo = ZeroChatRepository(firestore, identities, auth)
        val impostor = withTimeout(TIMEOUT) { impostorRepo.ensureProfile(alice.username, "x").getOrThrow() }
        assertNotEquals(alice.username, impostor.username)
        val takeover = withTimeout(TIMEOUT) { impostorRepo.updateProfile(alice.username, "x", "") }
        assertTrue(takeover.isFailure)
    }

    @Test
    fun unauthenticated_cannotListRooms() = runBlocking {
        auth.signOut()
        var error: FirebaseFirestoreException? = null
        try {
            withTimeout(TIMEOUT) { firestore.collection("rooms").get().await() }
        } catch (e: Exception) {
            error = e.unwrapFirestoreException()
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, error?.code)
    }

    private companion object {
        const val TIMEOUT = 8_000L
    }
}
