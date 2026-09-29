package com.fetchy.sdk.internal

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FetchyInstallMarkerTest {
    @Test
    fun freshInstallWithoutATokenWritesTheMarkerAndKeepsNothingToDrop() {
        assertFalse(
            FetchyInstallMarker.shouldDropRestoredIdentity(
                markerPresent = false,
                hasBackendToken = false,
                firstInstallTimeMs = 1_000L,
                lastUpdateTimeMs = 1_000L
            )
        )
    }

    @Test
    fun inPlaceUpgradeKeepsTheExistingToken() {
        assertFalse(
            FetchyInstallMarker.shouldDropRestoredIdentity(
                markerPresent = false,
                hasBackendToken = true,
                firstInstallTimeMs = 1_000L,
                lastUpdateTimeMs = 50_000L
            )
        )
    }

    @Test
    fun backupRestoreOntoANewInstallDropsTheToken() {
        assertTrue(
            FetchyInstallMarker.shouldDropRestoredIdentity(
                markerPresent = false,
                hasBackendToken = true,
                firstInstallTimeMs = 80_000L,
                lastUpdateTimeMs = 80_000L
            )
        )
    }

    @Test
    fun existingMarkerIsLeftAlone() {
        assertFalse(
            FetchyInstallMarker.shouldDropRestoredIdentity(
                markerPresent = true,
                hasBackendToken = true,
                firstInstallTimeMs = 80_000L,
                lastUpdateTimeMs = 80_000L
            )
        )
    }
}
