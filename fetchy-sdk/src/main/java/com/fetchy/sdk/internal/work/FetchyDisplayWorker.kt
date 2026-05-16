package com.fetchy.sdk.internal.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.fetchy.sdk.internal.notification.FetchyNotifier
import kotlinx.coroutines.CancellationException

internal class FetchyDisplayWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val localNotificationId = inputData.getLong(KEY_LOCAL_NOTIFICATION_ID, -1L)
        if (localNotificationId == -1L) return Result.failure()

        return try {
            val repository = com.fetchy.sdk.internal.FetchyRepositoryProvider.get(applicationContext)
            val config = repository.getConfig() ?: return Result.retry()
            if (FetchyNotifier(applicationContext).showNotification(localNotificationId, config)) {
                Result.success()
            } else {
                Result.retry()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val KEY_LOCAL_NOTIFICATION_ID = "pn_local_notification_id"

        fun enqueue(context: Context, localNotificationId: Long) {
            val request = OneTimeWorkRequestBuilder<FetchyDisplayWorker>()
                .setInputData(
                    Data.Builder()
                        .putLong(KEY_LOCAL_NOTIFICATION_ID, localNotificationId)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "pn_display_$localNotificationId",
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }
}
