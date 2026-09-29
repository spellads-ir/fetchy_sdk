package com.fetchy.sdk.internal

import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

internal object FetchyScope : CoroutineScope by CoroutineScope(
    SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, throwable ->
        try {
            Log.e("Fetchy", "uncaught coroutine failure", throwable)
        } catch (_: RuntimeException) {
            // android.util.Log is unavailable in JVM unit tests.
        }
    }
) {
    const val LOG_TAG = "Fetchy"
}
