package com.fetchy.sdk.internal

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FetchyRegisterPolicyTest {
    @Test
    fun registersWhenThereIsNoBackendToken() {
        assertTrue(shouldRegisterDevice(null, "fp", "fp", null))
    }

    @Test
    fun skipsUnavailableRepeatWhenTheFingerprintIsUnchanged() {
        assertFalse(shouldRegisterDevice("token", "fp|unavailable", "fp|unavailable", null))
    }

    @Test
    fun registersAgainWhenFirebaseStatusChanges() {
        assertTrue(shouldRegisterDevice("token", "fp|unavailable", "fp|", null))
    }

    @Test
    fun reuploadsAKnownFcmTokenOnEverySync() {
        assertTrue(shouldRegisterDevice("token", "fp", "fp", "fcm-real"))
    }
}
