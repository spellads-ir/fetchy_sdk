package com.fetchy.sdk.internal.fcm

import com.fetchy.sdk.internal.model.FetchyScope
import com.fetchy.sdk.internal.model.FetchySource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FetchyFcmPayloadParserTest {
    @Test
    fun parse_readsDataOnlyPayload() {
        val payload = FetchyFcmPayloadParser.parse(
            mapOf(
                "title" to "Hello",
                "body" to "World",
                "notification_id" to "42",
                "scope" to "broadcast",
                "run_id" to "11",
                "push_schedule_type" to "recurring",
                "org_id" to "3",
                "app_id" to "8"
            )
        )
        assertNotNull(payload)
        assertEquals(FetchySource.PUSH, payload!!.source)
        assertEquals(FetchyScope.BROADCAST, payload.scope)
        assertEquals(42L, payload.remoteNotificationId)
        assertEquals(11L, payload.runId)
        assertEquals("broadcast:42:11", payload.dedupeKey())
    }

    @Test
    fun parse_returnsNullWhenTitleAndBodyMissing() {
        assertNull(FetchyFcmPayloadParser.parse(mapOf("notification_id" to "1")))
    }
}
