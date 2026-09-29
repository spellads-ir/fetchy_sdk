package com.fetchy.sdk.internal.fcm

import android.content.Context
import com.fetchy.sdk.internal.FetchyEngineProvider
import com.fetchy.sdk.internal.FetchyScope
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.launch

class FetchyFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        FetchyFcmBridge.onNewToken(applicationContext, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        FetchyScope.launch {
            FetchyFcmBridge.onMessageReceived(applicationContext, message.data)
        }
    }
}

internal object FetchyFcmBridge {
    fun onNewToken(context: Context, token: String) {
        FetchyFcmCoordinator.saveTokenAndSync(context.applicationContext, token)
    }

    suspend fun onMessageReceived(context: Context, data: Map<String, String>) {
        val payload = FetchyFcmPayloadParser.parse(data) ?: return
        FetchyEngineProvider.get(context.applicationContext).ingestFromPush(payload)
    }
}
