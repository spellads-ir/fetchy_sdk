package com.fetchy.sdk.internal.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.caverock.androidsvg.SVG
import com.fetchy.sdk.R
import com.fetchy.sdk.internal.FetchyConfig
import com.fetchy.sdk.internal.FetchyConstants
import com.fetchy.sdk.internal.FetchyJson
import com.fetchy.sdk.internal.FetchyRepositoryProvider
import java.io.ByteArrayInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

internal class FetchyNotifier(private val context: Context) {
    private val repository = FetchyRepositoryProvider.get(context)
    private val httpClient = OkHttpClient()

    companion object {
        private const val DEFAULT_REMOTE_BITMAP_SIZE_PX = 128
        private const val SVG_HEADER_SNIFF_BYTES = 512
    }

    fun ensureChannel(config: FetchyConfig) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            config.notification.channelId,
            config.notification.channelName,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = config.notification.channelDescription
        }
        manager.createNotificationChannel(channel)
    }

    suspend fun showNotification(localNotificationId: Long, config: FetchyConfig): Boolean {
        val entity = repository.getNotification(localNotificationId)
        if (entity == null) {
            return false
        }

        return try {
            val notificationId = localNotificationId.hashCode()
            val contentIntent = PendingIntent.getActivity(
                context,
                notificationId,
                FetchyNotificationProxyActivity.createIntent(context, localNotificationId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val smallIconRes = config.notification.smallIconResId ?: R.drawable.pn_ic_notification_bell
            val badgeBitmap = loadBitmapSafely(entity.badgeUrl)
            val largeImage = loadBitmapSafely(entity.imageUrl)

            val builder = NotificationCompat.Builder(context, config.notification.channelId)
                .setSmallIcon(smallIconRes)
                .setContentTitle(entity.title.ifBlank { context.getString(R.string.pn_notification_fallback_title) })
                .setContentText(entity.body.ifBlank { context.getString(R.string.pn_notification_fallback_body) })
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(contentIntent)

            if (badgeBitmap != null) {
                builder.setLargeIcon(badgeBitmap)
            }

            if (largeImage != null) {
                val pictureStyle = NotificationCompat.BigPictureStyle()
                    .bigPicture(largeImage)
                if (badgeBitmap != null) {
                    pictureStyle.bigLargeIcon(badgeBitmap)
                }
                builder.setStyle(pictureStyle)
            } else {
                builder.setStyle(NotificationCompat.BigTextStyle().bigText(entity.body))
            }

            val actions = FetchyJson.decodeActionButtonList(entity.actionButtonsJson)
            actions.take(3).forEachIndexed { index, button ->
                builder.addAction(
                    0,
                    button.title,
                    PendingIntent.getActivity(
                        context,
                        notificationId + index + 1,
                        FetchyNotificationProxyActivity.createIntent(
                            context = context,
                            localNotificationId = localNotificationId,
                            actionUrl = button.url
                        ),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
            }

            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
            repository.markNotificationDisplayed(entity.localId, System.currentTimeMillis())
            true
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun loadBitmapSafely(url: String?): Bitmap? {
        val safeUrl = url?.takeIf { it.isNotBlank() } ?: return null
        return try {
            withContext(Dispatchers.IO) { loadBitmap(safeUrl) }
        } catch (_: Exception) {
            null
        }
    }

    private fun loadBitmap(url: String): Bitmap? {
        val request = Request.Builder().url(url).build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body ?: return null
            val bytes = body.bytes()
            if (bytes.isEmpty()) return null
            return decodeBitmap(bytes, body.contentType()?.toString(), url)
        }
    }

    private fun decodeBitmap(bytes: ByteArray, contentType: String?, url: String): Bitmap? {
        return if (looksLikeSvg(contentType, url, bytes)) {
            decodeSvgBitmap(bytes)
        } else {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
    }

    private fun looksLikeSvg(contentType: String?, url: String, bytes: ByteArray): Boolean {
        if (contentType?.contains("svg", ignoreCase = true) == true) return true
        if (url.substringBefore('?').endsWith(".svg", ignoreCase = true)) return true

        val sniffSize = minOf(bytes.size, SVG_HEADER_SNIFF_BYTES)
        val header = bytes.copyOfRange(0, sniffSize).toString(Charsets.UTF_8)
        return header.contains("<svg", ignoreCase = true)
    }

    private fun decodeSvgBitmap(bytes: ByteArray): Bitmap? {
        val svg = ByteArrayInputStream(bytes).use { inputStream ->
            SVG.getFromInputStream(inputStream)
        }
        val picture = svg.renderToPicture()
        val width = picture.width.takeIf { it > 0 } ?: DEFAULT_REMOTE_BITMAP_SIZE_PX
        val height = picture.height.takeIf { it > 0 } ?: DEFAULT_REMOTE_BITMAP_SIZE_PX
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawPicture(picture)
        return bitmap
    }
}
