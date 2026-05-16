package com.fetchy.sdk.internal.notification

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Bundle
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import com.fetchy.sdk.internal.FetchyConstants
import com.fetchy.sdk.internal.FetchyRepositoryProvider
import com.fetchy.sdk.internal.data.SpNotificationEntity
import com.fetchy.sdk.internal.model.AckLinkRequest
import com.fetchy.sdk.internal.network.FetchyApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FetchyNotificationProxyActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scope.launch {
            handleIntent()
            finish()
        }
    }

    private suspend fun handleIntent() {
        val repository = FetchyRepositoryProvider.get(applicationContext)
        val localNotificationId = intent.getLongExtra(FetchyConstants.intentExtraLocalNotificationId, -1L)
        if (localNotificationId == -1L) {
            openFallbackScreen()
            return
        }

        val entity = repository.getNotification(localNotificationId)
        if (entity == null) {
            openFallbackScreen()
            return
        }

        repository.markNotificationOpened(localNotificationId, System.currentTimeMillis())
        NotificationManagerCompat.from(applicationContext).cancel(localNotificationId.hashCode())

        val explicitActionUrl = intent.getStringExtra(FetchyConstants.intentExtraActionUrl)
        if (!explicitActionUrl.isNullOrBlank()) {
            if (!openUri(explicitActionUrl.toUri())) {
                openFallbackScreen()
            }
            return
        }

        val linkUrl = entity.linkUrl
        val linkOpened = linkUrl?.let(::tryOpenUri) ?: false

        if (!linkOpened) {
            openFallbackScreen()
            return
        }

        acknowledgeLinkIfRequired(entity, linkUrl)
    }

    private suspend fun acknowledgeLinkIfRequired(entity: SpNotificationEntity, linkUrl: String?) {
        if (!FetchyLinkPolicy.shouldSendDirectClickAck(linkUrl)) {
            return
        }
        val linkUrlToAck = linkUrl ?: return

        if (
            entity.source != "PULL" && entity.source != "PUSH" ||
            entity.scope != "BROADCAST" ||
            entity.clickAckSignature.isNullOrBlank() ||
            entity.remoteNotificationId == null ||
            entity.orgId == null ||
            entity.appId == null
        ) {
            return
        }

        val repository = FetchyRepositoryProvider.get(applicationContext)
        val ackKey = listOf(
            FetchyConstants.ackTypeLinkClick,
            entity.remoteNotificationId,
            entity.orgId,
            entity.appId,
            linkUrlToAck,
            entity.clickAckSignature
        ).joinToString(separator = ":")
        val reserved = repository.reserveAck(ackKey, FetchyConstants.ackTypeLinkClick)
        if (!reserved) return

        val config = repository.getConfig() ?: return
        try {
            withContext(Dispatchers.IO) {
                FetchyApiClient(config.baseUrl).ackLink(
                    AckLinkRequest(
                        notificationId = entity.remoteNotificationId,
                        orgId = entity.orgId,
                        appId = entity.appId,
                        linkUrl = linkUrlToAck,
                        signature = entity.clickAckSignature
                    )
                )
            }
        } catch (_: Exception) {
        }
    }

    private fun tryOpenUri(raw: String): Boolean = openUri(raw.toUri())

    private fun openUri(uri: Uri): Boolean {
        val resolveInfo = resolveActivity(uri) ?: return false
        return startActivitySafely(buildLaunchIntent(uri, resolveInfo))
    }

    private fun resolveActivity(uri: Uri, targetPackage: String? = null): ResolveInfo? {
        val intent = Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)
        if (!targetPackage.isNullOrBlank()) {
            intent.setPackage(targetPackage)
        }
        return packageManager.resolveActivity(intent, 0)
    }

    private fun buildLaunchIntent(uri: Uri, resolveInfo: ResolveInfo): Intent {
        return Intent(Intent.ACTION_VIEW, uri)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .setComponent(ComponentName(resolveInfo.activityInfo.packageName, resolveInfo.activityInfo.name))
    }

    private fun startActivitySafely(intent: Intent): Boolean {
        return try {
            startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    private fun openFallbackScreen() {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(launchIntent)
        }
    }

    companion object {
        fun createIntent(
            context: Context,
            localNotificationId: Long,
            actionUrl: String? = null
        ): Intent {
            return Intent(context, FetchyNotificationProxyActivity::class.java).apply {
                putExtra(FetchyConstants.intentExtraLocalNotificationId, localNotificationId)
                putExtra(FetchyConstants.intentExtraActionUrl, actionUrl)
            }
        }
    }
}

