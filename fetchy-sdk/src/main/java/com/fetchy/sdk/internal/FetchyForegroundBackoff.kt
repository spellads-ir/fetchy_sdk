package com.fetchy.sdk.internal

internal fun foregroundBackoffDelayMs(consecutiveFailures: Int): Long {
    return when {
        consecutiveFailures <= 1 -> FetchyConstants.foregroundPullIntervalMs
        consecutiveFailures == 2 -> 120_000L
        else -> 300_000L
    }
}
