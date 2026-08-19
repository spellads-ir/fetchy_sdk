package com.fetchy.sdk.internal

import androidx.annotation.DrawableRes
import com.fetchy.sdk.R

internal data class FetchyConfig(
    val apiKey: String,
    val baseUrl: String,
    val pull: FetchyPullConfig = FetchyPullConfig(),
    val push: FetchyPushConfig = FetchyPushConfig(),
    val externalUserId: String? = null,
    val notification: FetchyNotificationConfig = FetchyNotificationConfig(),
    val environment: String = "production"
) {
    fun normalized(): FetchyConfig {
        return copy(
            apiKey = apiKey.trim(),
            baseUrl = baseUrl.trim().trimEnd('/'),
            pull = pull.normalized(),
            notification = notification.normalized()
        )
    }
}

internal data class FetchyPullConfig(
    val enabled: Boolean = true,
    val workerEnabled: Boolean = true,
    val pollIntervalMinutes: Long = FetchyConstants.periodicPullIntervalMinutes,
    val apiKeyOverride: String? = null
) {
    fun normalized(): FetchyPullConfig = copy(
        pollIntervalMinutes = pollIntervalMinutes.coerceAtLeast(1L),
        apiKeyOverride = apiKeyOverride?.trim()?.takeIf { it.isNotEmpty() }
    )

    val effectiveApiKey: String?
        get() = apiKeyOverride?.takeIf { it.isNotBlank() }

    val backgroundPollIntervalMinutes: Long
        get() = pollIntervalMinutes.coerceAtLeast(FetchyConstants.periodicPullIntervalMinutes)
}

internal data class FetchyPushConfig(
    val enabled: Boolean = true
)

internal data class FetchyNotificationConfig(
    val channelId: String = "pn_notification_channel",
    val channelName: String = "Fetchy Notifications",
    val channelDescription: String = "Notifications delivered by the Fetchy SDK.",
    @DrawableRes val smallIconResId: Int? = R.drawable.pn_ic_notification_bell
) {
    fun normalized(): FetchyNotificationConfig = copy(
        channelId = channelId.trim().ifEmpty { "pn_notification_channel" },
        channelName = channelName.trim().ifEmpty { "Fetchy Notifications" },
        channelDescription = channelDescription.trim().ifEmpty { "Notifications delivered by the Fetchy SDK." }
    )
}


