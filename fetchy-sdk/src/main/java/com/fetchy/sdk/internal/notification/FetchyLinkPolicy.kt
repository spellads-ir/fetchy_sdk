package com.fetchy.sdk.internal.notification

internal object FetchyLinkPolicy {
    fun isHttpUrl(linkUrl: String): Boolean {
        val normalized = linkUrl.trim().lowercase()
        return normalized.startsWith("http://") || normalized.startsWith("https://")
    }

    fun shouldSendDirectClickAck(linkUrl: String?): Boolean {
        if (linkUrl.isNullOrBlank()) return false
        return !isHttpUrl(linkUrl)
    }
}
