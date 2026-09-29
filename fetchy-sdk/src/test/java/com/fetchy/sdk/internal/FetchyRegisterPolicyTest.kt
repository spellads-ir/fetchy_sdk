package com.fetchy.sdk.internal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FetchyRegisterPolicyTest {
    private val day = FetchyConstants.registerRefreshIntervalMs

    @Test
    fun registersWhenThereIsNoBackendToken() {
        assertTrue(shouldRegisterDevice(null, "fp", "fp", lastRegisterAtEpochMs = 0L, nowEpochMs = 0L))
    }

    @Test
    fun skipsUnavailableRepeatWhenTheFingerprintIsUnchanged() {
        assertFalse(
            shouldRegisterDevice(
                "token",
                "fp|unavailable",
                "fp|unavailable",
                lastRegisterAtEpochMs = 1_000L,
                nowEpochMs = 1_000L
            )
        )
    }

    @Test
    fun registersAgainWhenFirebaseStatusChanges() {
        assertTrue(
            shouldRegisterDevice(
                "token",
                "fp|unavailable",
                "fp|",
                lastRegisterAtEpochMs = 1_000L,
                nowEpochMs = 1_000L
            )
        )
    }

    @Test
    fun tenSyncsWithAFreshRegisterDoNotRegisterAgain() {
        val now = 10_000L
        var registers = 0
        repeat(10) {
            if (
                shouldRegisterDevice(
                    existingToken = "token",
                    fingerprint = "fp",
                    storedFingerprint = "fp",
                    lastRegisterAtEpochMs = now,
                    nowEpochMs = now
                )
            ) {
                registers += 1
            }
        }
        assertEquals(0, registers)
    }

    @Test
    fun registersOnceWhenTheLastSuccessIsOlderThanADay() {
        val last = 1_000L
        assertTrue(
            shouldRegisterDevice(
                "token",
                "fp",
                "fp",
                lastRegisterAtEpochMs = last,
                nowEpochMs = last + day + 1L
            )
        )
        assertFalse(
            shouldRegisterDevice(
                "token",
                "fp",
                "fp",
                lastRegisterAtEpochMs = last,
                nowEpochMs = last + day
            )
        )
    }

    @Test
    fun failedRegisterDoesNotUpdateLastRegisterAt() {
        val previous = 1_000L
        assertEquals(previous, registerTimestampAfterAttempt(previous, succeeded = false, nowEpochMs = 5_000L))
        assertEquals(5_000L, registerTimestampAfterAttempt(previous, succeeded = true, nowEpochMs = 5_000L))
    }
}
