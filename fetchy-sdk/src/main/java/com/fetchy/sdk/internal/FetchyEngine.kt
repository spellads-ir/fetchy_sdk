package com.fetchy.sdk.internal

import android.os.SystemClock
import android.content.Context
import com.fetchy.sdk.internal.model.FetchyNotificationPayload
import com.fetchy.sdk.internal.model.RegisterTokenRequest
import com.fetchy.sdk.internal.model.selectFeedCursor
import com.fetchy.sdk.internal.network.FetchyApiClient
import com.fetchy.sdk.internal.notification.FetchyNotifier
import com.google.android.gms.tasks.Tasks
import com.google.firebase.messaging.FirebaseMessaging
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class FetchyEngine(private val context: Context) {
    private val repository = FetchyRepositoryProvider.get(context)
    private val notifier = FetchyNotifier(context)
    private val syncMutex = Mutex()
    private var lastFeedFetchCompletedAtElapsedMs = 0L

    private data class PendingDisplay(val config: FetchyConfig, val ids: List<Long>)

    companion object {
        // Prevent duplicate near-simultaneous /feed calls from startup + periodic workers.
        private const val MIN_FEED_FETCH_INTERVAL_MS = 10_000L
    }

    suspend fun syncNow(allowFeedFetch: Boolean = true) {
        val pending = syncMutex.withLock {
        repository.reconcileInstallMarker()
        val config = repository.getConfig()
        if (config == null) {
            return@withLock null
        }

        val apiClient = FetchyApiClient(config.baseUrl)
        val suppressForeignProject = FetchyFirebaseGate.action == FirebaseBootstrapAction.PROJECT_MISMATCH
        val fcmTokenStatus = if (suppressForeignProject) "project_mismatch" else readFcmTokenStatus()
        val existingToken = repository.getBackendToken()?.takeIf { it.isNotBlank() }
        val builtRequest = repository.buildRegisterRequest(config).copy(
            existingToken = existingToken,
            fcmTokenStatus = fcmTokenStatus
        )
        val registerRequest = if (suppressForeignProject) builtRequest.copy(fcmToken = null) else builtRequest

        val currentFingerprint = registerFingerprint(registerRequest)
        val nowEpochMs = System.currentTimeMillis()
        val backendToken: String
        if (
            !shouldRegisterDevice(
                existingToken = existingToken,
                fingerprint = currentFingerprint,
                storedFingerprint = repository.getRegisterFingerprint(),
                lastRegisterAtEpochMs = repository.getLastRegisterAt(),
                nowEpochMs = nowEpochMs
            )
        ) {
            backendToken = existingToken!!
        } else {
            backendToken = registerTokenWithRecovery(apiClient, registerRequest)
            repository.saveBackendToken(backendToken)
            repository.saveRegisterFingerprint(currentFingerprint)
            val recordedAt = registerTimestampAfterAttempt(
                previous = repository.getLastRegisterAt(),
                succeeded = true,
                nowEpochMs = nowEpochMs
            )
            if (recordedAt != null) {
                repository.saveLastRegisterAt(recordedAt)
            }
        }

        repository.purgeExpiredNotifications()

        val fetchFeed = config.pull.enabled && config.pull.workerEnabled && allowFeedFetch
        val nowElapsed = SystemClock.elapsedRealtime()
        val rateLimited = lastFeedFetchCompletedAtElapsedMs > 0L &&
            nowElapsed - lastFeedFetchCompletedAtElapsedMs < MIN_FEED_FETCH_INTERVAL_MS
        if (fetchFeed && !rateLimited) {
            val lastRetrieve = repository.getLastRetrieve()
            val encoded = encodePendingReports(
                repository.oldestPendingReports(FetchyConstants.maxPendingReportsPerRequest)
            )
            val feedResponse = apiClient.getFeed(
                token = backendToken,
                lastRetrieve = lastRetrieve,
                exclusiveAck = encoded.exclusiveAck,
                delivered = encoded.delivered
            )
            repository.deletePendingReports(pendingReportIdsToDelete(true, encoded.includedLocalIds))
            val receivedAt = System.currentTimeMillis()
            lastFeedFetchCompletedAtElapsedMs = SystemClock.elapsedRealtime()
            (feedResponse.notifications + feedResponse.exclusiveNotifications).forEach { payload ->
                repository.persistNotification(payload, receivedAt)
            }
            repository.saveLastRetrieve(
                selectFeedCursor(
                    nextCursor = feedResponse.nextCursor,
                    notifications = feedResponse.notifications,
                    exclusiveNotifications = feedResponse.exclusiveNotifications,
                    nowEpochMs = receivedAt
                )
            )
        }
        PendingDisplay(config, repository.pendingDisplayIds())
        }
        pending?.let { showPending(it) }
    }

    suspend fun ingestFromPush(payload: FetchyNotificationPayload) {
        val pending = syncMutex.withLock {
            repository.purgeExpiredNotifications()
            val config = repository.getConfig() ?: return@withLock null
            repository.persistNotification(payload, System.currentTimeMillis())
            repository.markUndisplayedAsPush(payload.dedupeKey())
            PendingDisplay(config, repository.pendingDisplayIds())
        }
        pending?.let { showPending(it) }
    }

    private suspend fun showPending(pending: PendingDisplay) {
        pending.ids.forEach { localId ->
            val shown = try {
                notifier.showNotification(localId, pending.config)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                FetchyLog.e("display failed localId=$localId", error)
                false
            }
            if (!shown) {
                repository.incrementDisplayAttempts(localId)
            }
        }
    }

    private suspend fun readFcmTokenStatus(): String? {
        return try {
            val token = Tasks.await(FirebaseMessaging.getInstance().token, 10, TimeUnit.SECONDS)
            if (token.isNullOrBlank()) {
                FetchyLog.e("fcm token empty")
                "unavailable"
            } else {
                if (token != repository.getFcmToken()) {
                    repository.saveFcmToken(token)
                    FetchyLog.i("fcm token updated ${FetchyLog.redact(token)}")
                }
                null
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: IllegalStateException) {
            FetchyLog.e("firebase not initialized", error)
            "unavailable"
        } catch (error: Exception) {
            FetchyLog.e("fcm token unavailable", error)
            "unavailable"
        }
    }

    private fun registerFingerprint(request: RegisterTokenRequest): String =
        listOf(
            request.appApiKey,
            request.clientType,
            request.fcmToken.orEmpty(),
            request.fcmTokenStatus.orEmpty(),
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

}

internal fun shouldRegisterDevice(
    existingToken: String?,
    fingerprint: String,
    storedFingerprint: String?,
    lastRegisterAtEpochMs: Long?,
    nowEpochMs: Long,
    registerRefreshIntervalMs: Long = FetchyConstants.registerRefreshIntervalMs
): Boolean {
    if (existingToken.isNullOrBlank()) return true
    if (fingerprint != storedFingerprint) return true
    if (lastRegisterAtEpochMs == null) return true
    return nowEpochMs - lastRegisterAtEpochMs > registerRefreshIntervalMs
}

internal fun registerTimestampAfterAttempt(
    previous: Long?,
    succeeded: Boolean,
    nowEpochMs: Long
): Long? = if (succeeded) nowEpochMs else previous

internal object FetchyEngineProvider {
    @Volatile
    private var instance: FetchyEngine? = null

    fun get(context: Context): FetchyEngine {
        return instance ?: synchronized(this) {
            instance ?: FetchyEngine(context.applicationContext).also { instance = it }
        }
    }
}
