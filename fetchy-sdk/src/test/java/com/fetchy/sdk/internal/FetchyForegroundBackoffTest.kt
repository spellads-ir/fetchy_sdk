package com.fetchy.sdk.internal

import org.junit.Assert.assertEquals
import org.junit.Test

class FetchyForegroundBackoffTest {
    @Test
    fun backoffStepsFromOneMinuteToFiveMinutes() {
        assertEquals(60_000L, foregroundBackoffDelayMs(1))
        assertEquals(120_000L, foregroundBackoffDelayMs(2))
        assertEquals(300_000L, foregroundBackoffDelayMs(3))
        assertEquals(300_000L, foregroundBackoffDelayMs(9))
    }
}
