package com.fetchy.sdk.internal.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.fetchy.sdk.internal.FetchyConstants

@Entity(tableName = "pn_state")
internal data class SpStateEntity(
    @PrimaryKey val key: String,
    val value: String,
    val updatedAt: Long
)

@Entity(
    tableName = "pn_notifications",
    indices = [Index(value = ["dedupeKey"], unique = true)]
)
internal data class SpNotificationEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val dedupeKey: String,
    val source: String,
    val scope: String,
    val fetchyId: String? = null,
    val schemaVersion: Int? = null,
    val remoteNotificationId: Long?,
    val orgId: Long?,
    val appId: Long?,
    val title: String,
    val body: String,
    val badgeUrl: String?,
    val imageUrl: String?,
    val linkUrl: String?,
    val actionButtonsJson: String,
    val clickAckSignature: String?,
    val createdAtEpochMs: Long?,
    val receivedAtEpochMs: Long,
    val displayedAtEpochMs: Long?,
    val openedAtEpochMs: Long?,
    val displayAttempts: Int = 0,
    val expiresAtEpochMs: Long = 0
)

@Entity(tableName = "pn_pending_reports")
internal data class SpPendingReportEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scope: String,
    val remoteNotificationId: Long,
    val channel: String,
    val createdAtEpochMs: Long
)

@Entity(tableName = "pn_ack_records")
internal data class SpAckRecordEntity(
    @PrimaryKey val ackKey: String,
    val ackType: String,
    val createdAt: Long
)

@Dao
internal interface SpStateDao {
    @Query("SELECT value FROM pn_state WHERE `key` = :key LIMIT 1")
    suspend fun getValue(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SpStateEntity): Long

    @Query("DELETE FROM pn_state WHERE `key` IN (:keys)")
    suspend fun deleteKeys(keys: List<String>): Int
}

@Dao
internal interface SpNotificationDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: SpNotificationEntity): Long

    @Query("SELECT * FROM pn_notifications WHERE dedupeKey = :dedupeKey LIMIT 1")
    suspend fun getByDedupeKey(dedupeKey: String): SpNotificationEntity?

    @Query("SELECT * FROM pn_notifications WHERE localId = :localId LIMIT 1")
    suspend fun getById(localId: Long): SpNotificationEntity?

    @Query("UPDATE pn_notifications SET displayedAtEpochMs = :displayedAt WHERE localId = :localId")
    suspend fun markDisplayed(localId: Long, displayedAt: Long): Int

    @Query("UPDATE pn_notifications SET openedAtEpochMs = :openedAt WHERE localId = :localId")
    suspend fun markOpened(localId: Long, openedAt: Long): Int

    @Query("DELETE FROM pn_notifications WHERE expiresAtEpochMs > 0 AND expiresAtEpochMs <= :nowEpochMs")
    suspend fun deleteExpired(nowEpochMs: Long): Int

    @Query(
        "SELECT localId FROM pn_notifications " +
            "WHERE displayedAtEpochMs IS NULL AND displayAttempts < :maxAttempts"
    )
    suspend fun pendingDisplayIds(maxAttempts: Int): List<Long>

    @Query("UPDATE pn_notifications SET displayAttempts = displayAttempts + 1 WHERE localId = :localId")
    suspend fun incrementDisplayAttempts(localId: Long): Int

    @Query(
        "UPDATE pn_notifications SET source = 'PUSH' " +
            "WHERE dedupeKey = :dedupeKey AND displayedAtEpochMs IS NULL"
    )
    suspend fun markUndisplayedAsPush(dedupeKey: String): Int

    @Query("UPDATE pn_notifications SET expiresAtEpochMs = :expiresAtEpochMs WHERE localId = :localId")
    suspend fun updateExpiresAt(localId: Long, expiresAtEpochMs: Long): Int

    @Query("DELETE FROM pn_notifications")
    suspend fun clearAll(): Int
}

@Dao
internal interface SpAckDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: SpAckRecordEntity): Long
}

@Dao
internal interface SpPendingReportDao {
    @Insert
    suspend fun insert(entity: SpPendingReportEntity): Long

    @Query(
        "SELECT * FROM pn_pending_reports ORDER BY createdAtEpochMs ASC, id ASC LIMIT :limit"
    )
    suspend fun oldest(limit: Int): List<SpPendingReportEntity>

    @Query("DELETE FROM pn_pending_reports WHERE id IN (:ids)")
    suspend fun deleteIds(ids: List<Long>): Int
}

@Database(
    entities = [
        SpStateEntity::class,
        SpNotificationEntity::class,
        SpAckRecordEntity::class,
        SpPendingReportEntity::class
    ],
    version = 8,
    exportSchema = false
)
internal abstract class FetchyDatabase : RoomDatabase() {
    abstract fun stateDao(): SpStateDao
    abstract fun notificationDao(): SpNotificationDao
    abstract fun ackDao(): SpAckDao
    abstract fun pendingReportDao(): SpPendingReportDao

    companion object {
        @Volatile
        private var instance: FetchyDatabase? = null

        fun get(context: Context): FetchyDatabase {
            return instance ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    FetchyDatabase::class.java,
                    FetchyConstants.databaseName
                )
                    .addMigrations(
                        FetchyMigrations.MIGRATION_5_6,
                        FetchyMigrations.MIGRATION_6_7,
                        FetchyMigrations.MIGRATION_7_8
                    )
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                    .also { instance = it }
            }
        }
    }
}


