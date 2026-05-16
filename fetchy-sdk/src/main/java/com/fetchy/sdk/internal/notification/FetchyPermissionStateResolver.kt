package com.fetchy.sdk.internal.notification

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.fetchy.sdk.FetchyNotificationPermissionStatus

internal object FetchyPermissionStateResolver {
    fun resolve(context: Context): FetchyNotificationPermissionStatus {
        return when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> {
                // Before Android 13, notification permission is implicitly granted
                FetchyNotificationPermissionStatus.GRANTED
            }
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED -> FetchyNotificationPermissionStatus.GRANTED
            else -> FetchyNotificationPermissionStatus.DENIED
        }
    }
}
