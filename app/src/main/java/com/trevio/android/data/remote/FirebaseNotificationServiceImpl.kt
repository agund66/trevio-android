package com.trevio.android.data.remote

import com.google.firebase.functions.FirebaseFunctions
import com.trevio.android.domain.model.AppNotification
import com.trevio.android.domain.repository.NotificationService
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseNotificationServiceImpl @Inject constructor(
    private val functions: FirebaseFunctions
) : NotificationService {

    override suspend fun getNotifications(pageSize: Int, lastNotificationId: String?): Result<List<AppNotification>> {
        return try {
            val data = mutableMapOf<String, Any>("pageSize" to pageSize)
            if (lastNotificationId != null) data["lastNotificationId"] = lastNotificationId

            val result = functions.getHttpsCallable("getNotifications").call(data).await()
            val res = result.getData() as Map<*, *>
            @Suppress("UNCHECKED_CAST")
            val notifications = res["notifications"] as List<Map<*, *>>
            Result.success(notifications.map { n ->
                @Suppress("UNCHECKED_CAST")
                AppNotification(
                    notificationId = n["notificationId"] as String,
                    type = n["type"] as? String ?: "",
                    title = n["title"] as? String ?: "",
                    body = n["body"] as? String ?: "",
                    read = n["read"] as? Boolean ?: false,
                    data = (n["data"] as? Map<String, String>) ?: emptyMap()
                )
            })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun markNotificationRead(notificationId: String): Result<Unit> {
        return try {
            functions.getHttpsCallable("markNotificationRead")
                .call(mapOf("notificationId" to notificationId)).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun markAllNotificationsRead(): Result<Unit> {
        return try {
            functions.getHttpsCallable("markAllNotificationsRead").call().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
