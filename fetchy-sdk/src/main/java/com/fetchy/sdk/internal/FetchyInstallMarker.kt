package com.fetchy.sdk.internal

import android.content.Context
import java.io.File
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal object FetchyInstallMarker {
    const val FILE_NAME = "fetchy_install_id"
    private val mutex = Mutex()

    fun shouldDropRestoredIdentity(
        markerPresent: Boolean,
        hasBackendToken: Boolean,
        firstInstallTimeMs: Long,
        lastUpdateTimeMs: Long
    ): Boolean {
        if (markerPresent || !hasBackendToken) return false
        // A pre-1.5 install has a token and no marker. An in-place upgrade updates the package
        // after it was first installed. A backup restored onto a new install does not.
        return lastUpdateTimeMs <= firstInstallTimeMs
    }

    suspend fun reconcile(context: Context, repository: FetchyRepository) {
        mutex.withLock {
            val marker = markerFile(context)
            val hasToken = !repository.getBackendToken().isNullOrBlank()
            val drop = try {
                val info = context.packageManager.getPackageInfo(context.packageName, 0)
                shouldDropRestoredIdentity(
                    markerPresent = marker.isFile && marker.length() > 0L,
                    hasBackendToken = hasToken,
                    firstInstallTimeMs = info.firstInstallTime,
                    lastUpdateTimeMs = info.lastUpdateTime
                )
            } catch (error: Exception) {
                FetchyLog.e("install marker check skipped", error)
                false
            }
            if (drop) {
                repository.clearDeviceIdentity()
                FetchyLog.i("dropped restored device identity")
            }
            if (!marker.isFile || marker.length() == 0L) {
                marker.parentFile?.mkdirs()
                marker.writeText(UUID.randomUUID().toString())
            }
        }
    }

    private fun markerFile(context: Context): File = File(context.noBackupFilesDir, FILE_NAME)
}
