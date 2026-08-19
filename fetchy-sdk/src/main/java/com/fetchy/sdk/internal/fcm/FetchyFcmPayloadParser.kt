package com.fetchy.sdk.internal.fcm

import com.fetchy.sdk.internal.FetchyJson
import com.fetchy.sdk.internal.model.FetchyNotificationPayload
import com.fetchy.sdk.internal.model.FetchyScope
import com.fetchy.sdk.internal.model.FetchySource

internal object FetchyFcmPayloadParser {
    fun parse(data: Map<String, String>): FetchyNotificationPayload? {
        val title = data["title"].orEmpty()
        val body = data["body"].orEmpty()
        if (title.isBlank() && body.isBlank()) {
            return null
        }

        val scope = when (data["scope"]?.trim()?.lowercase()) {
            "exclusive" -> FetchyScope.EXCLUSIVE
            else -> FetchyScope.BROADCAST
        }

        return FetchyNotificationPayload(
            source = FetchySource.PUSH,
            scope = scope,
            title = title.ifBlank { "Fetchy" },
            body = body,
            badgeUrl = data["badge_url"]?.takeIf { it.isNotBlank() },
            imageUrl = data["image_url"]?.takeIf { it.isNotBlank() },
            linkUrl = data["link_url"]?.takeIf { it.isNotBlank() },
            actionButtons = FetchyJson.decodeActionButtonList(data["action_buttons"].orEmpty()),
            remoteNotificationId = data["notification_id"]?.toLongOrNull()?.takeIf { it != 0L },
            orgId = data["org_id"]?.toLongOrNull()?.takeIf { it != 0L },
            appId = data["app_id"]?.toLongOrNull()?.takeIf { it != 0L },
            clickAckSignature = data["click_ack_signature"]?.takeIf { it.isNotBlank() },
            runId = data["run_id"]?.toLongOrNull()?.takeIf { it != 0L },
            pushScheduleType = data["push_schedule_type"]?.takeIf { it.isNotBlank() }
        )
    }
}
