package com.fetchy.sdk.internal

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

internal enum class FirebaseBootstrapAction {
    INITIALIZE,
    USE_EXISTING,
    PROJECT_MISMATCH,
    SKIP
}

internal object FetchyFirebaseGate {
    @Volatile
    var action: FirebaseBootstrapAction = FirebaseBootstrapAction.SKIP
}

internal fun decideFirebaseBootstrap(
    defaultAppExists: Boolean,
    defaultProjectId: String?,
    configuredProjectId: String?
): FirebaseBootstrapAction {
    val configured = configuredProjectId?.trim()?.takeIf { it.isNotEmpty() }
    if (!defaultAppExists) {
        return if (configured != null) FirebaseBootstrapAction.INITIALIZE else FirebaseBootstrapAction.SKIP
    }
    val existing = defaultProjectId?.trim()?.takeIf { it.isNotEmpty() }
    if (configured != null && existing != null && configured != existing) {
        return FirebaseBootstrapAction.PROJECT_MISMATCH
    }
    return FirebaseBootstrapAction.USE_EXISTING
}

internal fun catchFirebaseBootstrap(block: () -> FirebaseBootstrapAction): FirebaseBootstrapAction {
    return try {
        block()
    } catch (error: Throwable) {
        FetchyLog.e("firebase bootstrap failed", error)
        FirebaseBootstrapAction.SKIP
    }
}

internal object FetchyFirebaseBootstrap {
    fun apply(context: Context, firebase: FetchyFirebaseConfig?) {
        val action = catchFirebaseBootstrap {
            Class.forName("com.google.firebase.FirebaseApp")
            FetchyFirebaseApps.apply(context, firebase)
        }
        FetchyFirebaseGate.action = action
    }
}

private object FetchyFirebaseApps {
    fun apply(context: Context, firebase: FetchyFirebaseConfig?): FirebaseBootstrapAction {
        val defaultApp = FirebaseApp.getApps(context)
            .firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }
        var action = decideFirebaseBootstrap(
            defaultAppExists = defaultApp != null,
            defaultProjectId = defaultApp?.options?.projectId,
            configuredProjectId = firebase?.projectId
        )
        when (action) {
            FirebaseBootstrapAction.INITIALIZE -> {
                val applicationId = effectiveFirebaseApplicationId(
                    firebase?.applicationId.orEmpty(),
                    firebase?.mobileSdkAppId.orEmpty()
                )
                val options = firebase.toFirebaseOptions()
                if (applicationId == null) {
                    FetchyLog.w(
                        "firebase ${invalidFirebaseApplicationIdField(firebase?.applicationId.orEmpty(), firebase?.mobileSdkAppId.orEmpty())} " +
                            "is not a mobilesdk app id; Firebase was not initialized"
                    )
                    action = FirebaseBootstrapAction.SKIP
                } else if (options == null) {
                    FetchyLog.e("firebase block is missing api_key; Firebase was not initialized")
                    action = FirebaseBootstrapAction.SKIP
                } else {
                    FirebaseApp.initializeApp(context, options)
                }
            }
            FirebaseBootstrapAction.PROJECT_MISMATCH -> FetchyLog.w(
                "firebase project mismatch: default FirebaseApp projectId=${defaultApp?.options?.projectId} " +
                    "fetchy-config.json firebase.project_id=${firebase?.projectId}. " +
                    "The FCM token will not be uploaded (fcm_token_status=project_mismatch). Pull delivery still works."
            )
            FirebaseBootstrapAction.USE_EXISTING,
            FirebaseBootstrapAction.SKIP -> Unit
        }
        FetchyLog.i("firebase bootstrap action=$action")
        return action
    }
}

internal val mobilesdkApplicationId = Regex("""^\d+:\d+:android:[0-9a-f]+$""")

internal fun effectiveFirebaseApplicationId(applicationId: String, mobileSdkAppId: String): String? {
    for (candidate in listOf(applicationId, mobileSdkAppId)) {
        val value = candidate.trim()
        if (mobilesdkApplicationId.matches(value)) return value
    }
    return null
}

internal fun invalidFirebaseApplicationIdField(applicationId: String, mobileSdkAppId: String): String {
    val application = applicationId.trim()
    if (application.isNotEmpty() && !mobilesdkApplicationId.matches(application)) return "application_id"
    val mobile = mobileSdkAppId.trim()
    if (mobile.isNotEmpty() && !mobilesdkApplicationId.matches(mobile)) return "mobile_sdk_app_id"
    return "application_id"
}

private fun FetchyFirebaseConfig?.toFirebaseOptions(): FirebaseOptions? {
    val firebase = this ?: return null
    val applicationId = effectiveFirebaseApplicationId(firebase.applicationId, firebase.mobileSdkAppId)
        ?: return null
    if (firebase.apiKey.isBlank()) return null
    val builder = FirebaseOptions.Builder()
        .setApplicationId(applicationId)
        .setApiKey(firebase.apiKey)
    if (firebase.projectId.isNotBlank()) builder.setProjectId(firebase.projectId)
    if (firebase.gcmSenderId.isNotBlank()) builder.setGcmSenderId(firebase.gcmSenderId)
    if (firebase.storageBucket.isNotBlank()) builder.setStorageBucket(firebase.storageBucket)
    return builder.build()
}
