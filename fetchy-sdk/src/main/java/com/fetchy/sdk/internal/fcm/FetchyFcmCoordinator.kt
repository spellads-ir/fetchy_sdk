package com.fetchy.sdk.internal.fcm

import android.content.Context
import com.fetchy.sdk.internal.FetchyRepositoryProvider
import com.fetchy.sdk.internal.work.FetchySyncWorker
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.fetchy.sdk.internal.FetchyConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

internal object FetchyFcmCoordinator {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun saveTokenAndSync(context: Context, token: String) {
        val appContext = context.applicationContext
        scope.launch {
            FetchyRepositoryProvider.get(appContext).saveFcmToken(token)
            val request = OneTimeWorkRequestBuilder<FetchySyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setInputData(
                    Data.Builder()
                        .putString(FetchySyncWorker.KEY_REASON, "fcm_token")
                        .putBoolean(FetchySyncWorker.KEY_ALLOW_FEED_FETCH, false)
                        .build()
                )
                .build()
            WorkManager.getInstance(appContext).enqueueUniqueWork(
                FetchyConstants.uniqueSyncWorkName,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
