package com.fetchy.sdk.internal

import android.util.Log
import com.fetchy.sdk.FetchyLogLevel

internal object FetchyLog {
    const val TAG = "Fetchy"

    @Volatile
    var level: FetchyLogLevel = FetchyLogLevel.NONE

    fun e(message: String, error: Throwable? = null) {
        write(Log.ERROR, FetchyLogLevel.ERROR, message, error)
    }

    fun w(message: String) {
        write(Log.WARN, FetchyLogLevel.ERROR, message, null)
    }

    fun i(message: String) {
        write(Log.INFO, FetchyLogLevel.INFO, message, null)
    }

    fun d(message: String) {
        write(Log.DEBUG, FetchyLogLevel.DEBUG, message, null)
    }

    fun redact(secret: String?): String {
        if (secret.isNullOrEmpty()) return ""
        return secret.take(8) + "…"
    }

    internal fun enabled(minimum: FetchyLogLevel): Boolean = level.ordinal >= minimum.ordinal

    private fun write(priority: Int, minimum: FetchyLogLevel, message: String, error: Throwable?) {
        if (!enabled(minimum)) return
        val line = if (error == null) message else "$message (${error.javaClass.simpleName}: ${error.message})"
        try {
            Log.println(priority, TAG, line)
        } catch (_: RuntimeException) {
            // android.util.Log is unavailable in JVM unit tests.
        }
    }
}
