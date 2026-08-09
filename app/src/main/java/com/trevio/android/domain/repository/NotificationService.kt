package com.trevio.android.domain.repository

import com.trevio.android.domain.model.AppNotification
import com.trevio.android.domain.model.PaginatedResult

interface NotificationService {
    suspend fun getNotifications(pageSize: Int, lastNotificationId: String?): Result<PaginatedResult<AppNotification>>
    suspend fun markNotificationRead(notificationId: String): Result<Unit>
    suspend fun updateNotificationData(notificationId: String, data: Map<String, String>): Result<Unit>
    suspend fun markAllNotificationsRead(): Result<Unit>
}
