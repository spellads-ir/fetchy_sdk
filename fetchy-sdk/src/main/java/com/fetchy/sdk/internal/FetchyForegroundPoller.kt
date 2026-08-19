package com.fetchy.sdk.internal

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal object FetchyForegroundPoller : DefaultLifecycleObserver {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
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
            ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        val context = appContext ?: return
        pollJob?.cancel()
        pollJob = scope.launch {
            FetchyEngineProvider.get(context).syncNow(allowFeedFetch = true)
            while (isActive) {
                delay(FetchyConstants.foregroundPullIntervalMs)
                FetchyEngineProvider.get(context).syncNow(allowFeedFetch = true)
            }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        pollJob?.cancel()
        pollJob = null
    }
}
