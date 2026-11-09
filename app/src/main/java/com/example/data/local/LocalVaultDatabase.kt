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
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "locked_rooms")
data class LockedRoomEntity(
    @PrimaryKey val roomId: String,
    val isSecretVault: Boolean = true,
    val lockedAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "pinned_rooms", primaryKeys = ["ownerUid", "roomId"])
data class PinnedRoomEntity(
    val ownerUid: String,
    val roomId: String,
    val pinnedAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "verified_peers", primaryKeys = ["ownerUid", "peerUsername"])
data class VerifiedPeerEntity(
    val ownerUid: String,
    val peerUsername: String,
    val verifiedPublicKey: String,
    val verifiedSafetyFingerprint: String,
    val verifiedAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "scheduled_envelopes")
data class ScheduledEnvelopeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val ownerUid: String,
    val roomId: String,
    val roomTitle: String,
    val plaintext: String,
    val ttlSeconds: Long = 0L,
    val scheduledAtMillis: Long,
    val createdAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "vault_security_config")
data class VaultSecurityConfigEntity(
    @PrimaryKey val ownerUid: String,
    val normalPinHash: String,
    val panicPinHash: String,
    val autoBurnDefaultSeconds: Long = 0L,
    val screenShieldEnabled: Boolean = false,
    val biometricUnlockEnabled: Boolean = true,
    val autoLockOnBackground: Boolean = true,
    val updatedAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "offline_outbox_queue")
data class PendingOutboxEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0L,
    val ownerUid: String,
    val roomId: String,
    val roomTitle: String,
    val roomKeyHint: String,
    val participantUidsCsv: String,
    val senderUsername: String,
    val plaintext: String,
    val messageType: String,
    val encryptedMeta: String,
    val ttlSeconds: Long,
    val createdAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "cached_vault_messages")
data class CachedVaultMessageEntity(
    @PrimaryKey val messageId: String,
    val ownerUid: String,
    val roomId: String,
    val roomTitle: String,
    val senderUsername: String,
    val decryptedText: String,
    val ciphertext: String,
    val iv: String,
    val messageType: String,
    val isStarred: Boolean = false,
    val createdAtMillis: Long
)

@Dao
interface LocalVaultDao {
    @Query("SELECT * FROM locked_rooms")
    fun observeLockedRooms(): Flow<List<LockedRoomEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setRoomLocked(entity: LockedRoomEntity)

    @Query("DELETE FROM locked_rooms WHERE roomId = :roomId")
    suspend fun unlockRoom(roomId: String)

    @Query("SELECT * FROM pinned_rooms WHERE ownerUid = :uid")
    fun observePinnedRooms(uid: String): Flow<List<PinnedRoomEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun pinRoom(entity: PinnedRoomEntity)

    @Query("DELETE FROM pinned_rooms WHERE ownerUid = :uid AND roomId = :roomId")
    suspend fun unpinRoom(uid: String, roomId: String)

    @Query("SELECT * FROM verified_peers WHERE ownerUid = :uid")
    fun observeVerifiedPeers(uid: String): Flow<List<VerifiedPeerEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveVerifiedPeer(entity: VerifiedPeerEntity)

    @Query("DELETE FROM verified_peers WHERE ownerUid = :uid AND peerUsername = :peerUsername")
    suspend fun deleteVerifiedPeer(uid: String, peerUsername: String)

    @Query("SELECT * FROM scheduled_envelopes WHERE ownerUid = :uid ORDER BY scheduledAtMillis ASC")
    fun observeScheduledEnvelopes(uid: String): Flow<List<ScheduledEnvelopeEntity>>

    @Query("SELECT * FROM scheduled_envelopes WHERE ownerUid = :uid AND scheduledAtMillis <= :nowMillis ORDER BY scheduledAtMillis ASC")
    suspend fun getDueScheduledEnvelopes(uid: String, nowMillis: Long): List<ScheduledEnvelopeEntity>

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

    @Query("SELECT * FROM cached_vault_messages WHERE ownerUid = :uid AND isStarred = 1 ORDER BY createdAtMillis DESC")
    fun observeStarredMessages(uid: String): Flow<List<CachedVaultMessageEntity>>

    @Query("UPDATE cached_vault_messages SET isStarred = :isStarred WHERE messageId = :messageId")
    suspend fun updateMessageStarred(messageId: String, isStarred: Boolean)

    @Query("DELETE FROM cached_vault_messages WHERE ownerUid = :uid AND roomId = :roomId")
    suspend fun deleteCachedMessagesForRoom(uid: String, roomId: String)

    @Query("SELECT * FROM cached_vault_messages WHERE ownerUid = :uid ORDER BY createdAtMillis DESC")
    suspend fun getAllCachedMessages(uid: String): List<CachedVaultMessageEntity>
}

@Database(
    entities = [
        LockedRoomEntity::class,
        PinnedRoomEntity::class,
        VerifiedPeerEntity::class,
        ScheduledEnvelopeEntity::class,
        VaultSecurityConfigEntity::class,
        PendingOutboxEntity::class,
        CachedVaultMessageEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class LocalVaultDatabase : RoomDatabase() {
    abstract fun vaultDao(): LocalVaultDao

    companion object {
        @Volatile
        private var INSTANCE: LocalVaultDatabase? = null

        fun getInstance(context: Context): LocalVaultDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    LocalVaultDatabase::class.java,
                    "zerochat_local_vault.db"
                ).fallbackToDestructiveMigration(true).build().also { INSTANCE = it }
            }
        }
    }
}
