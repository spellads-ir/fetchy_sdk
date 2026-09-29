package com.fetchy.sdk.internal

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FetchyMessagesTest {
    @Test
    fun markerIdentifiesAFetchyMessage() {
        assertTrue(isFetchyMessageData(mapOf("_fetchy" to "1")))
    }

    @Test
    fun prePhase3PayloadFallsBackToIdAndScope() {
        assertTrue(
            isFetchyMessageData(
                mapOf("notification_id" to "4", "scope" to "broadcast", "title" to "hi")
            )
        )
    }

    @Test
    fun unrelatedDataIsNotAFetchyMessage() {
        assertFalse(isFetchyMessageData(mapOf("title" to "hi")))
        assertFalse(isFetchyMessageData(mapOf("notification_id" to "4")))
        assertFalse(isFetchyMessageData(mapOf("_fetchy" to "0", "scope" to "broadcast")))
    }
}
