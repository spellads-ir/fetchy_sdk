package com.fetchy.sdk.internal

import android.content.Context
import org.json.JSONObject

internal object FetchyConfigLoader {
    fun fromAsset(context: Context, assetName: String = FetchyConstants.configAssetName): FetchyConfig {
        val json = context.assets.open(assetName).bufferedReader().use { it.readText() }
        return fromJson(json)
    }

    fun fromJson(json: String): FetchyConfig {
        val root = JSONObject(json)
        val pullJson = root.optJSONObject("pull")
        val pushJson = root.optJSONObject("push")
        val firebaseJson = root.optJSONObject("firebase")
        val notificationJson = root.optJSONObject("notification")

        return FetchyConfig(
            environment = root.optString("environment", "production"),
            baseUrl = root.getString("base_url"),
            apiKey = pullJson?.optString("api_key")?.takeIf { it.isNotBlank() }
                ?: root.optString("api_key").takeIf { it.isNotBlank() }
                ?: error("${FetchyConstants.configAssetName} must contain pull.api_key or api_key"),
            pull = FetchyPullConfig(
                enabled = pullJson?.optBoolean("enabled", true) ?: true,
                workerEnabled = pullJson?.optBoolean("worker_enabled", true) ?: true,
                pollIntervalMinutes = pullJson?.optLong(
                    "poll_interval_minutes",
                    FetchyConstants.periodicPullIntervalMinutes
                ) ?: FetchyConstants.periodicPullIntervalMinutes
            ),
            push = FetchyPushConfig(
                enabled = pushJson?.optBoolean("enabled", true) ?: true
            ),
            firebase = firebaseJson?.let { firebase ->
                FetchyFirebaseConfig(
                    projectId = firebase.optString("project_id"),
                    applicationId = firebase.optString("application_id"),
                    mobileSdkAppId = firebase.optString("mobile_sdk_app_id"),
                    apiKey = firebase.optString("api_key"),
                    gcmSenderId = firebase.optString("gcm_sender_id"),
                    storageBucket = firebase.optString("storage_bucket")
                )
            },
            notification = FetchyNotificationConfig(
                channelId = notificationJson?.optString("channel_id", "pn_notification_channel")
                    ?: "pn_notification_channel",
                channelName = notificationJson?.optString("channel_name", "Fetchy Notifications")
                    ?: "Fetchy Notifications",
                channelDescription = notificationJson?.optString(
                    "channel_description",
                    "Notifications delivered by the Fetchy SDK."
                ) ?: "Notifications delivered by the Fetchy SDK."
            )
        ).normalized()
    }

    fun toJson(config: FetchyConfig): String {
        val root = JSONObject()
            .put("environment", config.environment)
            .put("base_url", config.baseUrl)
            .put("api_key", config.apiKey)
            .put(
                "pull",
                JSONObject()
                    .put("enabled", config.pull.enabled)
                    .put("worker_enabled", config.pull.workerEnabled)
                    .put("poll_interval_minutes", config.pull.pollIntervalMinutes)
                    .put("api_key", config.pull.effectiveApiKey ?: config.apiKey)
            )
            .put(
                "push",
                JSONObject().put("enabled", config.push.enabled)
            )
            .apply {
                val firebase = config.firebase ?: return@apply
                put(
                    "firebase",
                    JSONObject()
                        .put("project_id", firebase.projectId)
                        .put("application_id", firebase.applicationId)
                        .put("mobile_sdk_app_id", firebase.mobileSdkAppId)
                        .put("api_key", firebase.apiKey)
                        .put("gcm_sender_id", firebase.gcmSenderId)
                        .put("storage_bucket", firebase.storageBucket)
                )
            }
            .put(
                "notification",
                JSONObject()
                    .put("channel_id", config.notification.channelId)
                    .put("channel_name", config.notification.channelName)
                    .put("channel_description", config.notification.channelDescription)
            )

        if (config.externalUserId != null) {
            root.put("external_user_id", config.externalUserId)
        }

        return root.toString()
    }
}
