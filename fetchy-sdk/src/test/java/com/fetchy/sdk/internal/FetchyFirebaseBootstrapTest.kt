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
    fun customerProviderDoesNotInitializeWhenTheHostHasNoFirebaseApp() {
        assertEquals(
            FirebaseBootstrapAction.SKIP,
            decideFirebaseBootstrap(
                defaultAppExists = false,
                defaultProjectId = null,
                configuredProjectId = "customer-project",
                provider = "customer"
            )
        )
    }

    @Test
    fun customerProviderUsesTheHostAppWhenTheProjectMatches() {
        assertEquals(
            FirebaseBootstrapAction.USE_EXISTING,
            decideFirebaseBootstrap(
                defaultAppExists = true,
                defaultProjectId = "customer-project",
                configuredProjectId = "customer-project",
                provider = "customer"
            )
        )
    }

    @Test
    fun customerProviderRejectsADifferentHostProject() {
        assertEquals(
            FirebaseBootstrapAction.PROJECT_MISMATCH,
            decideFirebaseBootstrap(
                defaultAppExists = true,
                defaultProjectId = "other-project",
                configuredProjectId = "customer-project",
                provider = "customer"
            )
        )
    }

    @Test
    fun usesApplicationIdWhenItIsAMobilesdkAppId() {
        assertEquals(
            "1:1:android:abc",
            effectiveFirebaseApplicationId("1:1:android:abc", "1:2:android:def")
        )
    }

    @Test
    fun usesMobileSdkAppIdWhenApplicationIdIsAPackageName() {
        assertEquals(
            "1:2:android:def",
            effectiveFirebaseApplicationId("com.example.app", "1:2:android:def")
        )
    }

    @Test
    fun skipsBootstrapWhenNeitherAppIdMatches() {
        assertEquals(null, effectiveFirebaseApplicationId("com.example.app", "not-an-id"))
        assertEquals(
            "application_id",
            invalidFirebaseApplicationIdField("com.example.app", "1:2:android:zz")
        )
        assertEquals(
            "mobile_sdk_app_id",
            invalidFirebaseApplicationIdField("", "com.example.app")
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
