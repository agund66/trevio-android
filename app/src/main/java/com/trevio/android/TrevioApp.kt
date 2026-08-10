package com.trevio.android

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.trevio.android.core.notification.NotificationChannels
import com.trevio.android.util.Logger
import com.trevio.android.util.NetworkMonitor
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class TrevioApp : Application() {

    @Inject
    lateinit var networkMonitor: NetworkMonitor

    override fun onCreate() {
        super.onCreate()

        // Start observing connectivity so the UI can react to offline state.
        networkMonitor.startMonitoring()

        // Create notification channels (required for Android 8.0+ to display notifications).
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val generalChannel = NotificationChannel(
            NotificationChannels.GENERAL,
            "General",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "General app notifications — group invites, expense updates, and settlements"
        }

        val broadcastChannel = NotificationChannel(
            NotificationChannels.BROADCAST,
            "Announcements",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Important announcements from the Trevio team"
        }

        notificationManager.createNotificationChannels(listOf(generalChannel, broadcastChannel))
        Logger.i(message = "Notification channels created")
    }
}
