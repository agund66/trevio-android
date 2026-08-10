package com.trevio.android.core.notification

/**
 * Canonical IDs for the app's [android.app.NotificationChannel]s.
 * Used when posting notifications via [androidx.core.app.NotificationCompat].
 */
object NotificationChannels {
    const val GENERAL = "general"
    const val BROADCAST = "broadcast"
}
