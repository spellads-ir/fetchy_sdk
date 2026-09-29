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
}
