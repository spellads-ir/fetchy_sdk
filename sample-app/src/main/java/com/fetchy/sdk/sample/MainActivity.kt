package com.fetchy.sdk.sample

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.fetchy.sdk.Fetchy
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var statusView: TextView

    private val requestNotifications = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { updateStatus() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusView = findViewById(R.id.statusText)
        findViewById<Button>(R.id.refreshButton).setOnClickListener { updateStatus() }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        lifecycleScope.launch {
            while (isActive) {
                updateStatus()
                delay(5_000)
            }
        }
    }

    private fun updateStatus() {
        val token = Fetchy.getToken(this) ?: "(registering...)"
        val permission = Fetchy.getNotificationPermissionStatus(this)
        statusView.text = buildString {
            appendLine("Fetchy SDK sample app")
            appendLine()
            appendLine("Package: com.fetchy.sdk")
            appendLine("Backend token:")
            appendLine(token)
            appendLine()
            appendLine("Notification permission: $permission")
            appendLine()
            appendLine("Keep this app open to poll /feed every ~60s.")
            appendLine("Send a test notification from fetchy admin or seed script.")
        }
    }
}
