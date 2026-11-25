package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.crypto.AndroidKeystoreSecretWrapper
import com.example.crypto.CryptoEngine
import kotlinx.coroutines.flow.Flow
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File

/** Rooms moved into the PIN-protected secret vault, per account. */
@Entity(tableName = "locked_rooms", primaryKeys = ["ownerUid", "roomId"])
data class LockedRoomEntity(
    val ownerUid: String,
    val roomId: String,
    val lockedAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "pinned_rooms", primaryKeys = ["ownerUid", "roomId"])
data class PinnedRoomEntity(
    val ownerUid: String,
    val roomId: String,
    val pinnedAtMillis: Long = System.currentTimeMillis()
)

/** Conversations removed from this user's list only ("delete chat" for direct messages). */
@Entity(tableName = "hidden_rooms", primaryKeys = ["ownerUid", "roomId"])
data class HiddenRoomEntity(
    val ownerUid: String,
    val roomId: String,
    /** Messages older than this stay hidden if the conversation comes back. */
    val hiddenAtMillis: Long = System.currentTimeMillis()
)

/** A peer whose identity keys were verified (QR / safety number) by this user. */
@Entity(tableName = "verified_peers", primaryKeys = ["ownerUid", "peerUid"])
data class VerifiedPeerEntity(
    val ownerUid: String,
    val peerUid: String,
    val peerUsername: String,
    /** CryptoEngine.identityKeyId of the keys that were verified. */
    val verifiedKeyId: String,
    val verifiedAtMillis: Long = System.currentTimeMillis()
)

/** A message to send later. [payloadJson] is the full message payload, encrypted on send. */
@Entity(tableName = "scheduled_envelopes")
data class ScheduledEnvelopeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val ownerUid: String,
    val roomId: String,
    val roomTitle: String,
    val envelopeId: String,
    val payloadJson: String,
    val plaintext: String,
    val ttlSeconds: Long = 0L,
    val scheduledAtMillis: Long,
    val createdAtMillis: Long = System.currentTimeMillis()
)

/** Local vault settings. PINs are stored as salted PBKDF2 hashes; there are no defaults. */
@Entity(tableName = "vault_security_config")
data class VaultSecurityConfigEntity(
    @PrimaryKey val ownerUid: String,
    val pinSalt: String = "",
    val normalPinHash: String = "",
    val panicPinHash: String = "",
    val screenShieldEnabled: Boolean = false,
    val biometricUnlockEnabled: Boolean = true,
    val autoLockOnBackground: Boolean = true,
    val failedAttempts: Int = 0,
    val lockoutUntilMillis: Long = 0L,
    val updatedAtMillis: Long = System.currentTimeMillis()
) {
    val hasPins: Boolean get() = pinSalt.isNotBlank() && normalPinHash.isNotBlank() && panicPinHash.isNotBlank()
}

/** A message that could not be sent yet. [envelopeId] makes retries idempotent. */
@Entity(tableName = "offline_outbox_queue")
data class PendingOutboxEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0L,
    val ownerUid: String,
    val roomId: String,
    val roomTitle: String,
    val envelopeId: String,
    val payloadJson: String,
    val ttlSeconds: Long,
    val createdAtMillis: Long = System.currentTimeMillis()
)

/** Decrypted copy of a received message (for search, starring and backups). */
@Entity(tableName = "cached_vault_messages")
data class CachedVaultMessageEntity(
    @PrimaryKey val messageId: String,
    val ownerUid: String,
    val roomId: String,
    val roomTitle: String,
    val senderUsername: String,
    val decryptedText: String,
    val messageType: String,
    val isStarred: Boolean = false,
    val createdAtMillis: Long,
    /** 0 = never; self-destructing messages are removed from the cache when they expire. */
    val expireAtMillis: Long = 0L
)

@Dao
interface LocalVaultDao {
    @Query("SELECT * FROM locked_rooms WHERE ownerUid = :uid")
    fun observeLockedRooms(uid: String): Flow<List<LockedRoomEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setRoomLocked(entity: LockedRoomEntity)

    @Query("DELETE FROM locked_rooms WHERE ownerUid = :uid AND roomId = :roomId")
    suspend fun unlockRoom(uid: String, roomId: String)

    @Query("SELECT * FROM pinned_rooms WHERE ownerUid = :uid")
    fun observePinnedRooms(uid: String): Flow<List<PinnedRoomEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun pinRoom(entity: PinnedRoomEntity)

    @Query("DELETE FROM pinned_rooms WHERE ownerUid = :uid AND roomId = :roomId")
    suspend fun unpinRoom(uid: String, roomId: String)

    @Query("SELECT * FROM hidden_rooms WHERE ownerUid = :uid")
    fun observeHiddenRooms(uid: String): Flow<List<HiddenRoomEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun hideRoom(entity: HiddenRoomEntity)

    @Query("DELETE FROM hidden_rooms WHERE ownerUid = :uid AND roomId = :roomId")
    suspend fun unhideRoom(uid: String, roomId: String)

    @Query("SELECT * FROM verified_peers WHERE ownerUid = :uid")
    fun observeVerifiedPeers(uid: String): Flow<List<VerifiedPeerEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveVerifiedPeer(entity: VerifiedPeerEntity)

    @Query("DELETE FROM verified_peers WHERE ownerUid = :uid AND peerUid = :peerUid")
    suspend fun deleteVerifiedPeer(uid: String, peerUid: String)

    @Query("SELECT * FROM scheduled_envelopes WHERE ownerUid = :uid ORDER BY scheduledAtMillis ASC")
    fun observeScheduledEnvelopes(uid: String): Flow<List<ScheduledEnvelopeEntity>>

    @Query("SELECT * FROM scheduled_envelopes WHERE ownerUid = :uid AND scheduledAtMillis <= :nowMillis ORDER BY scheduledAtMillis ASC")
    suspend fun getDueScheduledEnvelopes(uid: String, nowMillis: Long): List<ScheduledEnvelopeEntity>

    @Query("SELECT MIN(scheduledAtMillis) FROM scheduled_envelopes WHERE ownerUid = :uid")
    suspend fun nextScheduledAt(uid: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScheduledEnvelope(entity: ScheduledEnvelopeEntity)

    @Query("DELETE FROM scheduled_envelopes WHERE id = :id")
    suspend fun deleteScheduledEnvelope(id: Long)

    @Query("SELECT * FROM vault_security_config WHERE ownerUid = :uid LIMIT 1")
    fun observeSecurityConfig(uid: String): Flow<VaultSecurityConfigEntity?>

    @Query("SELECT * FROM vault_security_config WHERE ownerUid = :uid LIMIT 1")
    suspend fun getSecurityConfig(uid: String): VaultSecurityConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSecurityConfig(config: VaultSecurityConfigEntity)

    @Query("SELECT * FROM offline_outbox_queue WHERE ownerUid = :uid ORDER BY createdAtMillis ASC")
    fun observePendingOutbox(uid: String): Flow<List<PendingOutboxEntity>>

    @Query("SELECT * FROM offline_outbox_queue WHERE ownerUid = :uid ORDER BY createdAtMillis ASC")
    suspend fun getPendingOutboxList(uid: String): List<PendingOutboxEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueuePendingMessage(item: PendingOutboxEntity)

    @Query("DELETE FROM offline_outbox_queue WHERE localId = :localId")
    suspend fun deletePendingMessage(localId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun cacheDecryptedMessages(items: List<CachedVaultMessageEntity>)

    @Query("SELECT * FROM cached_vault_messages WHERE ownerUid = :uid ORDER BY createdAtMillis DESC")
    fun observeCachedMessages(uid: String): Flow<List<CachedVaultMessageEntity>>

    @Query("SELECT COUNT(*) FROM cached_vault_messages WHERE ownerUid = :uid")
    fun observeCachedMessageCount(uid: String): Flow<Int>

    @Query("SELECT * FROM cached_vault_messages WHERE ownerUid = :uid AND isStarred = 1 ORDER BY createdAtMillis DESC")
    fun observeStarredMessages(uid: String): Flow<List<CachedVaultMessageEntity>>

    @Query("SELECT messageId FROM cached_vault_messages WHERE ownerUid = :uid AND roomId = :roomId AND isStarred = 1")
    fun observeStarredIdsInRoom(uid: String, roomId: String): Flow<List<String>>

    @Query("UPDATE cached_vault_messages SET isStarred = :isStarred WHERE ownerUid = :uid AND messageId = :messageId")
    suspend fun updateMessageStarred(uid: String, messageId: String, isStarred: Boolean)

    @Query("DELETE FROM cached_vault_messages WHERE ownerUid = :uid AND roomId = :roomId")
    suspend fun deleteCachedMessagesForRoom(uid: String, roomId: String)

    @Query("DELETE FROM cached_vault_messages WHERE messageId IN (:messageIds)")
    suspend fun deleteCachedMessages(messageIds: List<String>)

    @Query("DELETE FROM cached_vault_messages WHERE expireAtMillis > 0 AND expireAtMillis <= :nowMillis")
    suspend fun deleteExpiredCachedMessages(nowMillis: Long)

    @Query("SELECT * FROM cached_vault_messages WHERE ownerUid = :uid AND expireAtMillis = 0 ORDER BY createdAtMillis DESC")
    suspend fun getBackupableMessages(uid: String): List<CachedVaultMessageEntity>

    @Query("DELETE FROM cached_vault_messages WHERE ownerUid = :uid")
    suspend fun clearCachedMessages(uid: String)

    @Query("DELETE FROM offline_outbox_queue WHERE ownerUid = :uid")
    suspend fun clearOutbox(uid: String)

    @Query("DELETE FROM scheduled_envelopes WHERE ownerUid = :uid")
    suspend fun clearScheduled(uid: String)
}

@Database(
    entities = [
        LockedRoomEntity::class,
        PinnedRoomEntity::class,
        HiddenRoomEntity::class,
        VerifiedPeerEntity::class,
        ScheduledEnvelopeEntity::class,
        VaultSecurityConfigEntity::class,
        PendingOutboxEntity::class,
        CachedVaultMessageEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class LocalVaultDatabase : RoomDatabase() {
    abstract fun vaultDao(): LocalVaultDao

    companion object {
        /** Encrypted database file. The old plaintext file is deleted on first start. */
        private const val DB_NAME = "zerochat_vault_encrypted.db"
        private const val LEGACY_PLAINTEXT_DB = "zerochat_local_vault.db"
        private const val PASSPHRASE_FILE = "zc_vault_db.key"

        @Volatile
        private var INSTANCE: LocalVaultDatabase? = null

        fun getInstance(context: Context): LocalVaultDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: build(context.applicationContext).also { INSTANCE = it }
            }
        }

        private fun build(app: Context): LocalVaultDatabase {
            System.loadLibrary("sqlcipher")
            deleteLegacyPlaintextDatabase(app)
            val passphrase = loadOrCreatePassphrase(app)
            return Room.databaseBuilder(app, LocalVaultDatabase::class.java, DB_NAME)
                .openHelperFactory(SupportOpenHelperFactory(passphrase))
                // Add Migration objects here for every future schema change; there is no
                // destructive fallback, so local data (PINs, outbox) is never silently wiped.
                .build()
        }

        /** 32 random bytes, stored encrypted with a non-exportable Android Keystore key. */
        private fun loadOrCreatePassphrase(app: Context): ByteArray {
            val wrapper = AndroidKeystoreSecretWrapper("zerochat_vault_db_wrap")
            val file = File(app.noBackupFilesDir, PASSPHRASE_FILE)
            if (file.exists()) {
                runCatching { return wrapper.unwrap(file.readBytes()) }
                // The Keystore key is gone (e.g. data restored to another device): start over.
                app.deleteDatabase(DB_NAME)
            }
            val passphrase = CryptoEngine.randomBytes(32)
            file.writeBytes(wrapper.wrap(passphrase))
            return passphrase
        }

        private fun deleteLegacyPlaintextDatabase(app: Context) {
            if (app.getDatabasePath(LEGACY_PLAINTEXT_DB).exists()) {
                app.deleteDatabase(LEGACY_PLAINTEXT_DB)
            }
        }
    }
}
