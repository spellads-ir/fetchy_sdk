package com.fetchy.sdk.internal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class FetchyWorkNamesTest {
    @Test
    fun registerAndFeedUseDifferentUniqueNames() {
        assertEquals("pn_register", FetchyConstants.uniqueRegisterWorkName)
        assertEquals("pn_notif_sync", FetchyConstants.uniqueSyncWorkName)
        assertEquals("pn_notif_worker", FetchyConstants.uniquePeriodicWorkName)
        assertNotEquals(FetchyConstants.uniqueRegisterWorkName, FetchyConstants.uniqueSyncWorkName)
        assertNotEquals(FetchyConstants.uniqueSyncWorkName, FetchyConstants.uniquePeriodicWorkName)
    }
}
