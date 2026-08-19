package com.fetchy.sdk.internal.model

internal enum class FetchySource {
    PULL,
    PUSH
}

internal enum class FetchyScope {
    BROADCAST,
    EXCLUSIVE
}

internal data class ActionButton(
    val title: String,
    val url: String
)

internal data class FetchyNotificationPayload(
    val source: FetchySource,
    val scope: FetchyScope,
    val title: String,
    val body: String,
    val badgeUrl: String? = null,
    val imageUrl: String? = null,
    val linkUrl: String? = null,
    val actionButtons: List<ActionButton> = emptyList(),
    val remoteNotificationId: Long? = null,
    val orgId: Long? = null,
    val appId: Long? = null,
    val clickAckSignature: String? = null,
    val createdAtEpochMs: Long? = null,
    val fetchyId: String? = null,
    val schemaVersion: Int? = null,
    val runId: Long? = null,
    val pushScheduleType: String? = null
) {
    fun dedupeKey(): String {
        val id = remoteNotificationId?.toString()
            ?: fetchyId?.takeIf { it.isNotBlank() }
            ?: "na"
        return when (scope) {
            FetchyScope.EXCLUSIVE -> "exclusive:$id"
            FetchyScope.BROADCAST -> {
                val run = runId?.takeIf { it != 0L }
                if (pushScheduleType == "recurring" && run != null) {
                    "broadcast:$id:$run"
                } else {
                    "broadcast:$id"
                }
            }
        }
    }

    fun trayNotificationId(): Int = dedupeKey().hashCode()
}

internal data class RegisterTokenRequest(
    val appApiKey: String,
    val existingToken: String?,
    val clientType: String,
    val fcmToken: String? = null,
    val deviceBrand: String,
    val deviceModel: String,
    val androidVersion: String,
    val androidApiLevel: Int,
    val appVersion: String,
    val sdkVersion: String
)

internal data class AckLinkRequest(
    val notificationId: Long,
    val orgId: Long,
    val appId: Long,
    val linkUrl: String,
    val signature: String
)

internal data class FeedResponse(
    val notifications: List<FetchyNotificationPayload>,
    val exclusiveNotifications: List<FetchyNotificationPayload>
)
