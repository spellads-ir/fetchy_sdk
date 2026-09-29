package com.fetchy.sdk

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.fetchy.sdk.internal.FetchyConfig
import com.fetchy.sdk.internal.FetchyConfigLoader
import com.fetchy.sdk.internal.FetchyFirebaseBootstrap
import com.fetchy.sdk.internal.FetchyConstants
import com.fetchy.sdk.internal.FetchyForegroundPoller
import com.fetchy.sdk.internal.FetchyRepositoryProvider
import com.fetchy.sdk.internal.FetchyScope
import com.fetchy.sdk.internal.isFetchyMessageData
import com.fetchy.sdk.internal.notification.FetchyNotifier
import com.fetchy.sdk.internal.notification.FetchyPermissionStateResolver
import com.fetchy.sdk.internal.work.FetchySyncWorker
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

object Fetchy {
    @Volatile
    private var runtimeConfig: FetchyConfig? = null

    @Volatile
    private var runtimeClientType: FetchyClientType? = null

    private val bootstrapMutex = Mutex()

    @JvmStatic
    fun setLogLevel(level: FetchyLogLevel) {
        com.fetchy.sdk.internal.FetchyLog.level = level
    }

    @JvmStatic
    fun initialize(context: Context) {
        initialize(context, FetchyClientType.ANDROID_NATIVE)
    }

    @JvmStatic
    fun initialize(context: Context, clientType: FetchyClientType) {
        val appContext = context.applicationContext
        val config = prepareRuntime(appContext, clientType)
        FetchyScope.launch {
            try {
                bootstrapMutex.withLock {
                    persistRuntime(appContext, config, clientType)
                    FetchyRepositoryProvider.get(appContext).reconcileInstallMarker()
                    scheduleSync(appContext, config)
                    FetchyForegroundPoller.start(appContext)
                    refreshFcmToken(appContext)
                }
            } catch (_: Exception) {
                // Registration/sync failures must not crash the host app.
            }
        }
    }

    @JvmStatic
    fun getToken(context: Context): String? {
        return runBlocking {
            FetchyRepositoryProvider.get(context.applicationContext).getBackendToken()
                ?.takeIf { it.isNotBlank() }
        }
    }

    @JvmStatic
    fun getNotificationPermissionStatus(context: Context): FetchyNotificationPermissionStatus {
        return FetchyPermissionStateResolver.resolve(context.applicationContext)
    }

    @JvmStatic
    fun isFetchyMessage(data: Map<String, String>): Boolean = isFetchyMessageData(data)

    @JvmStatic
    fun onNewToken(context: Context, token: String) {
        com.fetchy.sdk.internal.fcm.FetchyFcmCoordinator.saveTokenAndSync(context.applicationContext, token)
    }

    @JvmStatic
    fun handleRemoteMessage(context: Context, data: Map<String, String>) {
        val appContext = context.applicationContext
        FetchyScope.launch {
            com.fetchy.sdk.internal.fcm.FetchyFcmBridge.onMessageReceived(appContext, data)
        }
    }

    @JvmStatic
    fun syncNotificationPermissionStatus(context: Context): FetchyNotificationPermissionStatus {
        val appContext = context.applicationContext
        ensureRuntimeReady(appContext)
        return runBlocking {
            refreshNotificationPermissionStatus(appContext)
        }
    }

    internal fun ensureRuntimeReady(context: Context): FetchyConfig {
        val appContext = context.applicationContext
        runtimeConfig?.let { return it }

        val repository = FetchyRepositoryProvider.get(appContext)
        val clientType = runtimeClientType ?: runBlocking {
            FetchyClientType.fromWireValueOrDefault(repository.getClientType())
        }
        val config = prepareRuntime(appContext, clientType)
        runBlocking {
            bootstrapMutex.withLock {
                persistRuntime(appContext, config, clientType)
            }
        }
        return config
    }

    private fun scheduleSync(context: Context, config: FetchyConfig) {
        val workManager = WorkManager.getInstance(context)
        if (config.pull.enabled && config.pull.workerEnabled) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val periodicRequest = PeriodicWorkRequestBuilder<FetchySyncWorker>(
                config.pull.backgroundPollIntervalMinutes,
                TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setInputData(
                    Data.Builder()
                        .putString(FetchySyncWorker.KEY_REASON, "periodic_pull")
                        .putBoolean(FetchySyncWorker.KEY_ALLOW_FEED_FETCH, true)
                        .build()
                )
                .build()
            workManager.enqueueUniquePeriodicWork(
                FetchyConstants.uniquePeriodicWorkName,
                ExistingPeriodicWorkPolicy.UPDATE,
                periodicRequest
            )
        } else {
            workManager.cancelUniqueWork(FetchyConstants.uniquePeriodicWorkName)
        }

        workManager.enqueueUniqueWork(
            FetchyConstants.uniqueRegisterWorkName,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            immediateSyncRequest(reason = "initialize", allowFeedFetch = false)
        )
        if (config.pull.enabled && config.pull.workerEnabled) {
            workManager.enqueueUniqueWork(
                FetchyConstants.uniqueSyncWorkName,
                ExistingWorkPolicy.KEEP,
                immediateSyncRequest(reason = "initialize", allowFeedFetch = true)
            )
        } else {
            workManager.cancelUniqueWork(FetchyConstants.uniqueSyncWorkName)
        }
    }

    private fun immediateSyncRequest(reason: String, allowFeedFetch: Boolean) =
        OneTimeWorkRequestBuilder<FetchySyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setInputData(
                Data.Builder()
                    .putString(FetchySyncWorker.KEY_REASON, reason)
                    .putBoolean(FetchySyncWorker.KEY_ALLOW_FEED_FETCH, allowFeedFetch)
                    .build()
            )
            .build()

    @Synchronized
    private fun prepareRuntime(context: Context, clientType: FetchyClientType): FetchyConfig {
        runtimeClientType = clientType
        return runtimeConfig ?: FetchyConfigLoader.fromAsset(context).also { config ->
            runtimeConfig = config
            FetchyFirebaseBootstrap.apply(context, config.firebase)
            FetchyNotifier(context).ensureChannel(config)
        }
    }

    private suspend fun persistRuntime(
        context: Context,
        config: FetchyConfig,
        clientType: FetchyClientType
    ) {
        val repository = FetchyRepositoryProvider.get(context)
        repository.persistConfig(FetchyConfigLoader.toJson(config))
        repository.saveClientType(clientType.wireValue)
        refreshNotificationPermissionStatus(context)
    }

    private suspend fun refreshNotificationPermissionStatus(context: Context): FetchyNotificationPermissionStatus {
        val repository = FetchyRepositoryProvider.get(context)
        val permissionStatus = FetchyPermissionStateResolver.resolve(context)
        val updatedAt = System.currentTimeMillis()
        repository.saveNotificationPermissionStatus(permissionStatus, updatedAt)
        return permissionStatus
    }

    private fun refreshFcmToken(context: Context) {
        if (com.fetchy.sdk.internal.FetchyFirebaseGate.action ==
            com.fetchy.sdk.internal.FirebaseBootstrapAction.PROJECT_MISMATCH
        ) {
            return
        }
        try {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token ->
                    if (!token.isNullOrBlank()) {
                        com.fetchy.sdk.internal.fcm.FetchyFcmCoordinator.saveTokenAndSync(context, token)
                    }
                }
        } catch (_: Throwable) {
            // Missing Firebase class, or the host has not initialized Firebase yet.
        }
    }

}
