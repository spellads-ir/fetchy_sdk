package com.fetchy.sdk.internal.model

import com.fetchy.sdk.internal.FetchyConstants
import org.junit.Assert.assertEquals
import org.junit.Test

class FetchyFeedCursorTest {
    @Test
    fun nextCursorIsStoredVerbatim() {
        val item = sample(createdAt = 5_000L)
        assertEquals(
            1_700L,
            selectFeedCursor(
                nextCursor = 1_700L,
                notifications = listOf(item),
                exclusiveNotifications = emptyList(),
                nowEpochMs = 9_000L
            )
        )
    }

    @Test
    fun missingNextCursorUsesTheNewestCreatedAt() {
        val older = sample(createdAt = 100L)
        val newer = sample(createdAt = 400L)
        assertEquals(
            400L,
            selectFeedCursor(null, listOf(older, newer), emptyList(), nowEpochMs = 900L)
        )
    }

    @Test
    fun missingNextCursorAndCreatedAtUsesNow() {
        assertEquals(900L, selectFeedCursor(null, emptyList(), emptyList(), nowEpochMs = 900L))
    }

    @Test
    fun expiryExtendsToAnHourAfterEndTime() {
        val receivedAt = 1_000L
        val endTime = receivedAt + FetchyConstants.notificationDedupeTtlMs
        assertEquals(
            endTime + FetchyConstants.notificationEndGraceMs,
            notificationExpiresAtEpochMs(receivedAt, endTime)
        )
    }

    @Test
    fun expiryWithoutEndTimeIsFortyEightHours() {
        val receivedAt = 1_000L
        assertEquals(
            receivedAt + FetchyConstants.notificationDedupeTtlMs,
            notificationExpiresAtEpochMs(receivedAt, null)
        )
    }

    @Test
    fun shortEndTimeDoesNotShortenTheFortyEightHourWindow() {
        val receivedAt = 10_000L
        assertEquals(
            receivedAt + FetchyConstants.notificationDedupeTtlMs,
            notificationExpiresAtEpochMs(receivedAt, receivedAt + 1_000L)
        )
    }

    private fun sample(createdAt: Long) = FetchyNotificationPayload(
        source = FetchySource.PULL,
        scope = FetchyScope.BROADCAST,
        title = "t",
        body = "b",
        remoteNotificationId = 1,
        createdAtEpochMs = createdAt
    )
}
