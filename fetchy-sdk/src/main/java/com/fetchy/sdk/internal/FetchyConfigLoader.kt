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
