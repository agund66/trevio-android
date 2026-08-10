package com.trevio.android.core.notification

import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.trevio.android.MainActivity
import com.trevio.android.R
import com.trevio.android.util.Logger
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.atomic.AtomicInteger

@AndroidEntryPoint
class TrevioMessagingService : FirebaseMessagingService() {

    companion object {
        // Monotonically incrementing notification ID to avoid collisions and
        // integer overflow issues with System.currentTimeMillis().toInt().
        private val notificationIdCounter = AtomicInteger(1000)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val title = message.notification?.title
            ?: message.data["title"]
            ?: "Trevio"

        val body = message.notification?.body
            ?: message.data["body"]
            ?: return

        // Broadcast/announcement messages use a high-importance channel.
        val priority = message.data["priority"]
        val channelId = if (priority == "high" || priority == "urgent") {
            NotificationChannels.BROADCAST
        } else {
            NotificationChannels.GENERAL
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val notificationId = notificationIdCounter.incrementAndGet()
        val pendingIntent = PendingIntent.getActivity(
            this,
            notificationId,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(
                if (channelId == NotificationChannels.BROADCAST)
                    NotificationCompat.PRIORITY_HIGH
                else
                    NotificationCompat.PRIORITY_DEFAULT
            )
            .build()

        try {
            NotificationManagerCompat.from(this).notify(notificationId, notification)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS not granted — silently skip; in-app notifications still work.
            Logger.w(tag = "FCM", message = "Cannot post notification: permission not granted")
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Logger.i(tag = "FCM", message = "New FCM token registered")
    }
}
