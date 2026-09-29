package com.fetchy.sdk.internal.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal object FetchyMigrations {
    const val REWRITE_LEGACY_DEDUPE_KEYS = """
        UPDATE OR IGNORE pn_notifications
        SET dedupeKey = CASE
            WHEN UPPER(scope) = 'EXCLUSIVE' THEN 'exclusive:' || remoteNotificationId
            ELSE 'broadcast:' || remoteNotificationId
        END
        WHERE remoteNotificationId IS NOT NULL
          AND instr(dedupeKey, '::') > 0
    """

    const val DELETE_DUPLICATE_LEGACY_KEYS = """
        DELETE FROM pn_notifications
        WHERE instr(dedupeKey, '::') > 0
          AND remoteNotificationId IS NOT NULL
          AND EXISTS (
            SELECT 1 FROM pn_notifications AS kept
            WHERE kept.localId != pn_notifications.localId
              AND kept.dedupeKey = CASE
                WHEN UPPER(pn_notifications.scope) = 'EXCLUSIVE' THEN 'exclusive:' || pn_notifications.remoteNotificationId
                ELSE 'broadcast:' || pn_notifications.remoteNotificationId
              END
          )
    """

    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(REWRITE_LEGACY_DEDUPE_KEYS)
            db.execSQL(DELETE_DUPLICATE_LEGACY_KEYS)
        }
    }

    const val ADD_DISPLAY_ATTEMPTS = """
        ALTER TABLE pn_notifications ADD COLUMN displayAttempts INTEGER NOT NULL DEFAULT 0
    """

    const val ADD_EXPIRES_AT = """
        ALTER TABLE pn_notifications ADD COLUMN expiresAtEpochMs INTEGER NOT NULL DEFAULT 0
    """

    val BACKFILL_EXPIRES_AT =
        "UPDATE pn_notifications SET expiresAtEpochMs = receivedAtEpochMs + ${com.fetchy.sdk.internal.FetchyConstants.notificationDedupeTtlMs}"

    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(ADD_DISPLAY_ATTEMPTS)
            db.execSQL(ADD_EXPIRES_AT)
            db.execSQL(BACKFILL_EXPIRES_AT)
        }
    }
}
