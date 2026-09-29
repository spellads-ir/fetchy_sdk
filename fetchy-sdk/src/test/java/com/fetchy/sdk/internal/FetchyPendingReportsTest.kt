package com.fetchy.sdk.internal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class FetchyPendingReportsTest {
    @Test
    fun encodesExclusiveAckAndDeliveredWireTokens() {
        val encoded = encodePendingReports(
            listOf(
                row(1, 10, "e", 7, "l"),
                row(2, 20, "b", 12, "p"),
                row(3, 30, "e", 5, "p")
            )
        )
        assertEquals("7,5", encoded.exclusiveAck)
        assertEquals("e7l,b12p,e5p", encoded.delivered)
        assertEquals(listOf(1L, 2L, 3L), encoded.includedLocalIds)
    }

    @Test
    fun sendsOldestFirstAndCapsAtTheLimit() {
        val reports = (1..5).map { index ->
            row(
                localId = index.toLong(),
                createdAt = (100 - index).toLong(),
                scope = "b",
                remoteId = index.toLong(),
                channel = if (index % 2 == 0) "p" else "l"
            )
        }
        val encoded = encodePendingReports(reports, limit = 2)
        assertEquals("b5l,b4p", encoded.delivered)
        assertEquals("", encoded.exclusiveAck)
        assertEquals(listOf(5L, 4L), encoded.includedLocalIds)
    }

    @Test
    fun breaksTiesByLocalId() {
        val encoded = encodePendingReports(
            listOf(
                row(9, 50, "e", 2, "p"),
                row(3, 50, "e", 1, "l")
            ),
            limit = 2
        )
        assertEquals("1,2", encoded.exclusiveAck)
        assertEquals("e1l,e2p", encoded.delivered)
    }

    @Test
    fun defaultCapDropsTheNewestPastTwoHundred() {
        val reports = (1..201).map { index ->
            row(index.toLong(), index.toLong(), "b", index.toLong(), "l")
        }
        val encoded = encodePendingReports(reports)
        assertEquals(200, encoded.includedLocalIds.size)
        assertEquals(1L, encoded.includedLocalIds.first())
        assertEquals(200L, encoded.includedLocalIds.last())
        assertEquals("b1l", encoded.delivered.substringBefore(","))
        assertFalse(encoded.delivered.contains("b201l"))
    }

    @Test
    fun keepsReportsWhenTheFeedDidNotSucceed() {
        val ids = listOf(4L, 5L)
        assertEquals(emptyList<Long>(), pendingReportIdsToDelete(succeeded = false, ids))
        assertEquals(ids, pendingReportIdsToDelete(succeeded = true, ids))
    }

    private fun row(localId: Long, createdAt: Long, scope: String, remoteId: Long, channel: String) =
        PendingReportRow(localId, createdAt, scope, remoteId, channel)
}
