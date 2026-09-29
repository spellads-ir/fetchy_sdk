package com.fetchy.sdk.internal

internal fun isFetchyMessageData(data: Map<String, String>): Boolean {
    if (data["_fetchy"] == "1") return true
    return !data["notification_id"].isNullOrBlank() && !data["scope"].isNullOrBlank()
}
