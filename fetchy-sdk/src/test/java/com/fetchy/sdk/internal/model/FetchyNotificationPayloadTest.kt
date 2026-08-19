package com.fetchy.sdk.internal.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class FetchyNotificationPayloadTest {
    @Test
    fun dedupeKey_ignoresSourceForBroadcastAndExclusive() {
        val pull = sample(FetchySource.PULL, FetchyScope.BROADCAST, id = 42, runId = null)
        val push = sample(FetchySource.PUSH, FetchyScope.BROADCAST, id = 42, runId = null)
        assertEquals("broadcast:42", pull.dedupeKey())
        assertEquals(pull.dedupeKey(), push.dedupeKey())

        val exclusivePull = sample(FetchySource.PULL, FetchyScope.EXCLUSIVE, id = 9, runId = null)
        val exclusivePush = sample(FetchySource.PUSH, FetchyScope.EXCLUSIVE, id = 9, runId = null)
        assertEquals("exclusive:9", exclusivePull.dedupeKey())
        assertEquals(exclusivePull.dedupeKey(), exclusivePush.dedupeKey())
    }

    @Test
    fun dedupeKey_includesRunIdOnlyForRecurringBroadcast() {
        val first = sample(
            FetchySource.PUSH,
            FetchyScope.BROADCAST,
            id = 42,
            runId = 7,
            scheduleType = "recurring"
        )
        val second = sample(
            FetchySource.PUSH,
            FetchyScope.BROADCAST,
            id = 42,
            runId = 8,
            scheduleType = "recurring"
        )
        assertEquals("broadcast:42:7", first.dedupeKey())
        assertEquals("broadcast:42:8", second.dedupeKey())
        assertNotEquals(first.dedupeKey(), second.dedupeKey())

        val oneShot = sample(
            FetchySource.PUSH,
            FetchyScope.BROADCAST,
            id = 42,
            runId = 7,
            scheduleType = "immediate"
        )
        assertEquals("broadcast:42", oneShot.dedupeKey())
    }

    private fun sample(
        source: FetchySource,
        scope: FetchyScope,
        id: Long,
        runId: Long?,
        scheduleType: String? = null
    ): FetchyNotificationPayload {
        return FetchyNotificationPayload(
            source = source,
            scope = scope,
            title = "t",
            body = "b",
            remoteNotificationId = id,
            runId = runId,
            pushScheduleType = scheduleType
        )
    }
}
