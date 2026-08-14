package com.trevio.android

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.work.Configuration
import com.trevio.android.core.notification.NotificationChannels
import com.trevio.android.core.notification.ReminderManager
import com.trevio.android.util.Logger
import com.trevio.android.util.NetworkMonitor
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class TrevioApp : Application(), Configuration.Provider {

    @Inject
    lateinit var networkMonitor: NetworkMonitor

    @Inject
    lateinit var workerFactory: androidx.hilt.work.HiltWorkerFactory

    @Inject
    lateinit var reminderManager: ReminderManager

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        // Start observing connectivity so the UI can react to offline state.
        networkMonitor.startMonitoring()

        // Create notification channels (required for Android 8.0+ to display notifications).
        createNotificationChannels()

        // Start observing auth state, user groups, and reminder config to
        // schedule/cancel daily reminders as needed.
        reminderManager.start()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val generalChannel = NotificationChannel(
            NotificationChannels.GENERAL,
            getString(R.string.notification_channel_general),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = getString(R.string.notification_channel_general_desc)
        }

        val broadcastChannel = NotificationChannel(
            NotificationChannels.BROADCAST,
            getString(R.string.notification_channel_announcements),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.notification_channel_announcements_desc)
        }

        val reminderChannel = NotificationChannel(
            NotificationChannels.REMINDER,
            getString(R.string.notification_channel_reminder),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = getString(R.string.notification_channel_reminder_desc)
        }

        notificationManager.createNotificationChannels(listOf(generalChannel, broadcastChannel, reminderChannel))
        Logger.i(message = "Notification channels created")
    }
}
