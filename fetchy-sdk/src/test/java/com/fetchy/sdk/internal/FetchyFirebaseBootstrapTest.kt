package com.fetchy.sdk.internal

import org.junit.Assert.assertEquals
import org.junit.Test

class FetchyFirebaseBootstrapTest {
    @Test
    fun initializesWhenNoDefaultAppAndTheConfigNamesAProject() {
        assertEquals(
            FirebaseBootstrapAction.INITIALIZE,
            decideFirebaseBootstrap(
                defaultAppExists = false,
                defaultProjectId = null,
                configuredProjectId = "pull-notif"
            )
        )
    }

    @Test
    fun skipsWhenNoDefaultAppAndNoConfiguredProject() {
        assertEquals(
            FirebaseBootstrapAction.SKIP,
            decideFirebaseBootstrap(
                defaultAppExists = false,
                defaultProjectId = null,
                configuredProjectId = " "
            )
        )
    }

    @Test
    fun usesAnExistingAppWithTheSameProject() {
        assertEquals(
            FirebaseBootstrapAction.USE_EXISTING,
            decideFirebaseBootstrap(
                defaultAppExists = true,
                defaultProjectId = " pull-notif ",
                configuredProjectId = "pull-notif"
            )
        )
    }

    @Test
    fun reportsAMismatchWhenTheDefaultProjectDiffers() {
        assertEquals(
            FirebaseBootstrapAction.PROJECT_MISMATCH,
            decideFirebaseBootstrap(
                defaultAppExists = true,
                defaultProjectId = "host-app",
                configuredProjectId = "pull-notif"
            )
        )
    }

    @Test
    fun keepsAnExistingAppWhenTheConfigHasNoProject() {
        assertEquals(
            FirebaseBootstrapAction.USE_EXISTING,
            decideFirebaseBootstrap(
                defaultAppExists = true,
                defaultProjectId = "host-app",
                configuredProjectId = null
            )
        )
    }

    @Test
    fun missingFirebaseClassDoesNotEscape() {
        assertEquals(
            FirebaseBootstrapAction.SKIP,
            catchFirebaseBootstrap { throw NoClassDefFoundError("com.google.firebase.FirebaseApp") }
        )
        assertEquals(
            FirebaseBootstrapAction.SKIP,
            catchFirebaseBootstrap { throw ClassNotFoundException("com.google.firebase.FirebaseApp") }
        )
    }
}
