package com.trevio.android.core.notification

import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
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
            ?: getString(R.string.app_name)

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
        try {
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
            FirebaseFirestore.getInstance().collection("users").document(uid)
                .update(
                    mapOf(
                        "fcmToken" to token,
                        "updatedAt" to System.currentTimeMillis()
                    )
                )
                .addOnSuccessListener {
                    Logger.i(tag = "FCM", message = "FCM token persisted to Firestore")
                }
                .addOnFailureListener { e ->
                    Logger.e(tag = "FCM", message = "Failed to persist FCM token: ${e.message}")
                }
        } catch (e: Exception) {
            Logger.e(tag = "FCM", message = "Error persisting FCM token: ${e.message}")
        }
    }
}
