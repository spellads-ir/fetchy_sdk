package com.fetchy.sdk.internal

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal object FetchyForegroundPoller : DefaultLifecycleObserver {
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var started = false
    private var pollJob: Job? = null
    private var appContext: Context? = null

    fun start(context: Context) {
        appContext = context.applicationContext
        mainHandler.post {
            if (started) return@post
            started = true
            try {
                ProcessLifecycleOwner.get().lifecycle.addObserver(this)
            } catch (error: Exception) {
                logForeground("foreground polling skipped", error)
            }
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        val context = appContext ?: return
        pollJob?.cancel()
        pollJob = FetchyScope.launch {
            var consecutiveFailures = 0
            while (isActive) {
                try {
                    FetchyEngineProvider.get(context).syncNow(allowFeedFetch = true)
                    consecutiveFailures = 0
                    delay(FetchyConstants.foregroundPullIntervalMs)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    consecutiveFailures += 1
                    logForeground("foreground sync failed", error)
                    delay(foregroundBackoffDelayMs(consecutiveFailures))
                }
            }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        pollJob?.cancel()
        pollJob = null
    }

    private fun logForeground(message: String, error: Exception) {
        try {
            android.util.Log.e(FetchyScope.LOG_TAG, message, error)
        } catch (_: RuntimeException) {
        }
    }
}
