package com.trevio.android.domain.repository

import com.trevio.android.domain.model.AppNotification

interface NotificationService {
    suspend fun getNotifications(pageSize: Int, lastNotificationId: String?): Result<List<AppNotification>>
    suspend fun markNotificationRead(notificationId: String): Result<Unit>
    suspend fun markAllNotificationsRead(): Result<Unit>
}
