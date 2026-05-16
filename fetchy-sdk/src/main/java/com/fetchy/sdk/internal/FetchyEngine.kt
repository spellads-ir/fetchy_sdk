package com.fetchy.sdk.internal

import android.os.SystemClock
import android.content.Context
import com.fetchy.sdk.internal.data.SpNotificationEntity
import com.fetchy.sdk.internal.model.FetchyNotificationPayload
import com.fetchy.sdk.internal.model.RegisterTokenRequest
import com.fetchy.sdk.internal.network.FetchyApiClient
import com.fetchy.sdk.internal.notification.FetchyNotifier
import java.io.IOException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class FetchyEngine(private val context: Context) {
    private val repository = FetchyRepositoryProvider.get(context)
    private val notifier = FetchyNotifier(context)
    private val syncMutex = Mutex()
    private var lastFeedFetchCompletedAtElapsedMs = 0L

    companion object {
        // Prevent duplicate near-simultaneous /feed calls from startup + periodic workers.
        private const val MIN_FEED_FETCH_INTERVAL_MS = 10_000L
    }

    suspend fun syncNow(allowFeedFetch: Boolean = true) {
        syncMutex.withLock {
        val config = repository.getConfig()
        if (config == null) {
            return@withLock
        }

        val apiClient = FetchyApiClient(config.baseUrl)
        val existingToken = repository.getBackendToken()?.takeIf { it.isNotBlank() }
        val registerRequest = repository.buildRegisterRequest(config).copy(
            existingToken = existingToken
        )

        val currentFingerprint = registerFingerprint(registerRequest)
        val backendToken: String
        if (existingToken != null && currentFingerprint == repository.getRegisterFingerprint()) {
            backendToken = existingToken
        } else {
            backendToken = registerTokenWithRecovery(apiClient, registerRequest)
            repository.saveBackendToken(backendToken)
            repository.saveRegisterFingerprint(currentFingerprint)
        }

        if (!config.pull.enabled || !config.pull.workerEnabled || !allowFeedFetch) {
            return@withLock
        }

        val nowElapsed = SystemClock.elapsedRealtime()
        if (
            lastFeedFetchCompletedAtElapsedMs > 0L &&
            nowElapsed - lastFeedFetchCompletedAtElapsedMs < MIN_FEED_FETCH_INTERVAL_MS
        ) {
            return@withLock
        }

        val lastRetrieve = repository.getLastRetrieve()
        val feedResponse = apiClient.getFeed(backendToken, lastRetrieve)
        val receivedAt = System.currentTimeMillis()
        lastFeedFetchCompletedAtElapsedMs = SystemClock.elapsedRealtime()
        val fetchedNotifications = feedResponse.notifications + feedResponse.exclusiveNotifications
        var storedCount = 0
        var successfulDisplayCount = 0
        var failedDisplayCount = 0

        fetchedNotifications.forEach { payload ->
            val entity = resolveNotificationEntity(payload, receivedAt)
            if (entity == null) {
                failedDisplayCount += 1
                return@forEach
            }

            if (entity.displayedAtEpochMs != null) {
                successfulDisplayCount += 1
                return@forEach
            }

            if (entity.receivedAtEpochMs == receivedAt) {
                storedCount += 1
            }

            if (notifier.showNotification(entity.localId, config)) {
                successfulDisplayCount += 1
            } else {
                failedDisplayCount += 1
            }
        }

        if (failedDisplayCount == 0) {
            repository.saveLastRetrieve(computeNextCursor(feedResponse, receivedAt))
        }
        }
    }

    private suspend fun resolveNotificationEntity(
        payload: FetchyNotificationPayload,
        receivedAtEpochMs: Long
    ): SpNotificationEntity? {
        return repository.persistNotification(payload, receivedAtEpochMs)
            ?: repository.getNotificationByDedupeKey(payload.dedupeKey())
    }

    private fun registerFingerprint(request: RegisterTokenRequest): String =
        listOf(
            request.appApiKey,
            request.clientType,
            request.deviceBrand,
            request.deviceModel,
            request.androidVersion,
            request.androidApiLevel.toString(),
            request.appVersion,
            request.sdkVersion
        ).joinToString(separator = "|")

    private fun registerTokenWithRecovery(
        apiClient: FetchyApiClient,
        request: RegisterTokenRequest
    ): String {
        return try {
            apiClient.registerToken(request)
        } catch (error: IOException) {
            val shouldRetryWithoutExistingToken =
                request.existingToken != null &&
                    error.message?.contains("invalid existing token", ignoreCase = true) == true
            if (!shouldRetryWithoutExistingToken) throw error
            apiClient.registerToken(request.copy(existingToken = null))
        }
    }

    private fun computeNextCursor(feedResponse: com.fetchy.sdk.internal.model.FeedResponse, now: Long): Long {
        val maxCreatedAt = (feedResponse.notifications + feedResponse.exclusiveNotifications)
            .mapNotNull { it.createdAtEpochMs }
            .maxOrNull()
        return maxCreatedAt ?: now
    }
}

internal object FetchyEngineProvider {
    @Volatile
    private var instance: FetchyEngine? = null

    fun get(context: Context): FetchyEngine {
        return instance ?: synchronized(this) {
            instance ?: FetchyEngine(context.applicationContext).also { instance = it }
        }
    }
}
