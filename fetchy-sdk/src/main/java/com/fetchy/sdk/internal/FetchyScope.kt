package com.fetchy.sdk.internal

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

internal object FetchyScope : CoroutineScope by CoroutineScope(
    SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, throwable ->
        FetchyLog.e("uncaught coroutine failure", throwable)
    }
)
