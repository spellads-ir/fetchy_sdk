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
import com.fetchy.sdk.internal.FetchyConstants
import com.fetchy.sdk.internal.FetchyRepositoryProvider
import com.fetchy.sdk.internal.notification.FetchyNotifier
import com.fetchy.sdk.internal.notification.FetchyPermissionStateResolver
import com.fetchy.sdk.internal.work.FetchySyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

    private val bootstrapScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val bootstrapMutex = Mutex()

    @JvmStatic
    fun initialize(context: Context) {
        initialize(context, FetchyClientType.ANDROID_NATIVE)
    }

    @JvmStatic
    fun initialize(context: Context, clientType: FetchyClientType) {
        val appContext = context.applicationContext
        val config = prepareRuntime(appContext, clientType)
        bootstrapScope.launch {
            bootstrapMutex.withLock {
                persistRuntime(appContext, config, clientType)
                scheduleSync(appContext, config)
            }
        }
    }

    @JvmStatic
    fun getNotificationPermissionStatus(context: Context): FetchyNotificationPermissionStatus {
        return FetchyPermissionStateResolver.resolve(context.applicationContext)
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
                FetchyConstants.periodicPullIntervalMinutes,
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

        scheduleImmediateSync(
            context = context,
            reason = "initialize",
            allowFeedFetch = config.pull.enabled && config.pull.workerEnabled
        )
    }

    private fun scheduleImmediateSync(context: Context, reason: String, allowFeedFetch: Boolean) {
        val request = OneTimeWorkRequestBuilder<FetchySyncWorker>()
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

        WorkManager.getInstance(context).enqueueUniqueWork(
            FetchyConstants.uniqueSyncWorkName,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    @Synchronized
    private fun prepareRuntime(context: Context, clientType: FetchyClientType): FetchyConfig {
        runtimeClientType = clientType
        return runtimeConfig ?: FetchyConfigLoader.fromAsset(context).also { config ->
            runtimeConfig = config
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

}
