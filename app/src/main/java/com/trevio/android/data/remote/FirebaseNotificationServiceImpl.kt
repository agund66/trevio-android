package com.trevio.android.data.remote

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.trevio.android.domain.model.AppNotification
import com.trevio.android.domain.repository.NotificationService
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseNotificationServiceImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : NotificationService {

    override suspend fun getNotifications(pageSize: Int, lastNotificationId: String?): Result<List<AppNotification>> {
        return try {
            val uid = auth.currentUser?.uid ?: return Result.failure(Exception("User not authenticated"))

            var query = firestore.collection("users").document(uid).collection("notifications")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(pageSize.toLong())

            if (lastNotificationId != null) {
                val lastDoc = firestore.collection("users").document(uid).collection("notifications")
                    .document(lastNotificationId).get().await()
                if (lastDoc.exists()) {
                    query = firestore.collection("users").document(uid).collection("notifications")
                        .orderBy("createdAt", Query.Direction.DESCENDING)
                        .startAfter(lastDoc)
                        .limit(pageSize.toLong())
                }
            }

            val snapshot = query.get().await()
            val notifications = snapshot.documents.map { doc ->
                val data = doc.data ?: emptyMap()
                @Suppress("UNCHECKED_CAST")
                AppNotification(
                    notificationId = doc.id,
                    type = data["type"] as? String ?: "",
                    title = data["title"] as? String ?: "",
                    body = data["body"] as? String ?: "",
                    read = data["read"] as? Boolean ?: false,
                    createdAt = (data["createdAt"] as? Long ?: 0L),
                    data = (data["data"] as? Map<String, String>) ?: emptyMap()
                )
            }
            Result.success(notifications)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun markNotificationRead(notificationId: String): Result<Unit> {
        return try {
            val uid = auth.currentUser?.uid ?: return Result.failure(Exception("User not authenticated"))
            if (notificationId.isBlank()) return Result.failure(Exception("Notification ID is required"))

            firestore.collection("users").document(uid).collection("notifications")
                .document(notificationId).update("read", true).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun markAllNotificationsRead(): Result<Unit> {
        return try {
            val uid = auth.currentUser?.uid ?: return Result.failure(Exception("User not authenticated"))

            val snapshot = firestore.collection("users").document(uid).collection("notifications")
                .whereEqualTo("read", false).get().await()

            val batch = firestore.runBatch { b ->
                snapshot.documents.forEach { doc ->
                    b.update(doc.reference, "read", true)
                }
            }
            batch.await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
