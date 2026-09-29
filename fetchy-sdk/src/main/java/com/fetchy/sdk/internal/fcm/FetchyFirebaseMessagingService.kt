package com.fetchy.sdk.internal.fcm

import android.content.Context
import com.fetchy.sdk.internal.FetchyEngineProvider
import com.fetchy.sdk.internal.FetchyLog
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class FetchyFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        FetchyFcmBridge.onNewToken(applicationContext, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        try {
            runBlocking {
                withTimeout(8_000) {
                    FetchyFcmBridge.onMessageReceived(applicationContext, message.data)
                }
            }
        } catch (timeout: TimeoutCancellationException) {
            FetchyLog.e("push timed out", timeout)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            FetchyLog.e("push receive failed", error)
        }
    }
}

internal object FetchyFcmBridge {
    fun onNewToken(context: Context, token: String) {
        FetchyFcmCoordinator.saveTokenAndSync(context.applicationContext, token)
    }

    suspend fun onMessageReceived(context: Context, data: Map<String, String>) {
        val started = System.nanoTime()
        val payload = FetchyFcmPayloadParser.parse(data)
        if (payload == null) {
            FetchyLog.d("push ignored")
            return
        }
        try {
            FetchyEngineProvider.get(context.applicationContext).ingestFromPush(payload)
            val durationMs = (System.nanoTime() - started) / 1_000_000L
            FetchyLog.i(
                "push status=ok durationMs=$durationMs id=${payload.remoteNotificationId} scope=${payload.scope.name}"
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            val durationMs = (System.nanoTime() - started) / 1_000_000L
            FetchyLog.e("push failed durationMs=$durationMs ${error.message}", error)
            throw error
        }
    }
}
