package com.fetchy.sdk.internal.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.fetchy.sdk.internal.FetchyEngineProvider
import kotlinx.coroutines.CancellationException

class FetchySyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val allowFeedFetch = inputData.getBoolean(KEY_ALLOW_FEED_FETCH, true)
            FetchyEngineProvider.get(applicationContext).syncNow(allowFeedFetch)
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val KEY_REASON = "pn_reason"
        const val KEY_ALLOW_FEED_FETCH = "pn_allow_feed_fetch"
    }
}
