package com.fetchy.sdk.internal.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager

class FetchyMigrationsTest {
    private lateinit var connection: Connection

    @Before
    fun setUp() {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:")
        connection.createStatement().use { statement ->
            statement.execute(
                """
                CREATE TABLE pn_notifications (
                    localId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    dedupeKey TEXT NOT NULL,
                    source TEXT NOT NULL,
                    scope TEXT NOT NULL,
                    remoteNotificationId INTEGER,
                    title TEXT NOT NULL,
                    body TEXT NOT NULL,
                    displayedAtEpochMs INTEGER
                )
                """.trimIndent()
            )
            statement.execute(
                "CREATE UNIQUE INDEX index_pn_notifications_dedupeKey ON pn_notifications(dedupeKey)"
            )
        }
    }

    @After
    fun tearDown() {
        connection.close()
    }

    @Test
    fun rewritesLegacyBroadcastKeyAndDropsTheDuplicate() {
        insert(
            dedupeKey = "PULL::BROADCAST::7::title::body::link::100",
            scope = "BROADCAST",
            remoteId = 7,
            displayedAt = 50L
        )
        insert(
            dedupeKey = "broadcast:7",
            scope = "BROADCAST",
            remoteId = 7,
            displayedAt = 50L
        )

        migrate()

        assertNull(key("PULL::BROADCAST::7::title::body::link::100"))
        assertEquals(50L, displayedAt("broadcast:7"))
    }

    @Test
    fun rewritesASingleLegacyRowInPlace() {
        insert(
            dedupeKey = "PULL::EXCLUSIVE::4::t::b::::9",
            scope = "EXCLUSIVE",
            remoteId = 4,
            displayedAt = 12L
        )

        migrate()

        assertEquals(12L, displayedAt("exclusive:4"))
        assertNull(key("PULL::EXCLUSIVE::4::t::b::::9"))
    }

    @Test
    fun backfillsExpiryFromReceivedAt() {
        connection.createStatement().use { statement ->
            statement.execute("ALTER TABLE pn_notifications ADD COLUMN receivedAtEpochMs INTEGER NOT NULL DEFAULT 0")
            statement.execute("UPDATE pn_notifications SET receivedAtEpochMs = 0")
        }
        insert(
            dedupeKey = "broadcast:1",
            scope = "BROADCAST",
            remoteId = 1,
            displayedAt = 5L
        )
        connection.createStatement().use { statement ->
            statement.execute("UPDATE pn_notifications SET receivedAtEpochMs = 1000")
            statement.execute(FetchyMigrations.ADD_DISPLAY_ATTEMPTS)
            statement.execute(FetchyMigrations.ADD_EXPIRES_AT)
            statement.execute(FetchyMigrations.BACKFILL_EXPIRES_AT)
        }
        connection.createStatement().use { statement ->
            statement.executeQuery(
                "SELECT displayAttempts, expiresAtEpochMs FROM pn_notifications WHERE dedupeKey = 'broadcast:1'"
            ).use { rows ->
                assertEquals(true, rows.next())
                assertEquals(0, rows.getInt(1))
                assertEquals(1000L + 48L * 60L * 60L * 1000L, rows.getLong(2))
            }
        }
    }

    @Test
    fun leavesLegacyRowsWithoutARemoteId() {
        insert(
            dedupeKey = "PULL::BROADCAST::na::t::b::::na",
            scope = "BROADCAST",
            remoteId = null,
            displayedAt = 1L
        )

        migrate()

        assertEquals(1L, displayedAt("PULL::BROADCAST::na::t::b::::na"))
    }

    @Test
    fun createsPendingReportsOnTheV7ToV8Migration() {
        assertEquals(7, FetchyMigrations.MIGRATION_7_8.startVersion)
        assertEquals(8, FetchyMigrations.MIGRATION_7_8.endVersion)
        connection.createStatement().use { statement ->
            statement.execute(FetchyMigrations.CREATE_PENDING_REPORTS)
        }
        val columns = mutableListOf<String>()
        connection.createStatement().use { statement ->
            statement.executeQuery("PRAGMA table_info(pn_pending_reports)").use { rows ->
                while (rows.next()) columns.add(rows.getString(2))
            }
        }
        assertEquals(
            listOf("id", "scope", "remoteNotificationId", "channel", "createdAtEpochMs"),
            columns
        )
    }

    private fun migrate() {
        connection.createStatement().use { statement ->
            statement.execute(FetchyMigrations.REWRITE_LEGACY_DEDUPE_KEYS)
            statement.execute(FetchyMigrations.DELETE_DUPLICATE_LEGACY_KEYS)
        }
    }

    private fun insert(dedupeKey: String, scope: String, remoteId: Long?, displayedAt: Long?) {
        connection.prepareStatement(
            """
            INSERT INTO pn_notifications
                (dedupeKey, source, scope, remoteNotificationId, title, body, displayedAtEpochMs)
            VALUES (?, 'PULL', ?, ?, 't', 'b', ?)
            """.trimIndent()
        ).use { statement ->
            statement.setString(1, dedupeKey)
            statement.setString(2, scope)
            if (remoteId == null) statement.setNull(3, java.sql.Types.INTEGER) else statement.setLong(3, remoteId)
            if (displayedAt == null) statement.setNull(4, java.sql.Types.INTEGER) else statement.setLong(4, displayedAt)
            statement.executeUpdate()
        }
    }

    private fun key(dedupeKey: String): String? = displayedAt(dedupeKey)?.let { dedupeKey }

    private fun displayedAt(dedupeKey: String): Long? {
        connection.prepareStatement(
            "SELECT displayedAtEpochMs FROM pn_notifications WHERE dedupeKey = ?"
        ).use { statement ->
            statement.setString(1, dedupeKey)
            statement.executeQuery().use { rows ->
                if (!rows.next()) return null
                return rows.getLong(1)
            }
        }
    }
}
