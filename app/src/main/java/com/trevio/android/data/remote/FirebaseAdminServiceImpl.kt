package com.trevio.android.data.remote

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.trevio.android.domain.model.FeaturedMessage
import com.trevio.android.domain.model.PaginatedResult
import com.trevio.android.domain.model.ReminderConfig
import com.trevio.android.domain.model.User
import com.trevio.android.domain.repository.AdminService
import com.trevio.android.util.AppConstants
import com.trevio.android.util.ErrorMessages
import com.trevio.android.util.friendlyNetworkMessage
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseAdminServiceImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : AdminService {

    override suspend fun getAllUsers(pageSize: Int, lastUserUid: String?): Result<PaginatedResult<User>> {
        return try {
            val uid = auth.currentUser?.uid ?: return Result.failure(Exception(ErrorMessages.USER_NOT_AUTHENTICATED))
            val currentUserDoc = firestore.collection("users").document(uid).get().await()
            val currentRole = currentUserDoc.data?.get("role") as? String ?: "user"
            if (currentRole != "superadmin") return Result.failure(Exception(ErrorMessages.ACCESS_DENIED))

            var query = firestore.collection("users")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(pageSize.toLong())

            if (lastUserUid != null) {
                val lastDoc = firestore.collection("users").document(lastUserUid).get().await()
                if (lastDoc.exists()) {
                    query = firestore.collection("users")
                        .orderBy("createdAt", Query.Direction.DESCENDING)
                        .startAfter(lastDoc)
                        .limit(pageSize.toLong())
                }
            }

            val snapshot = query.get().await()

            val users = snapshot.documents.mapNotNull { doc ->
                val data = doc.data ?: return@mapNotNull null
                User(
                    uid = doc.id,
                    email = data["email"] as? String ?: "",
                    displayName = data["displayName"] as? String ?: "",
                    firstName = data["firstName"] as? String ?: "",
                    lastName = data["lastName"] as? String ?: "",
                    username = data["username"] as? String ?: "",
                    photoURL = data["photoURL"] as? String ?: "",
                    defaultCurrency = data["defaultCurrency"] as? String ?: AppConstants.BASE_CURRENCY,
                    acceptedTnC = data["acceptedTnC"] as? Boolean ?: false,
                    role = data["role"] as? String ?: "user",
                    blocked = data["blocked"] as? Boolean ?: false,
                    upiId = data["upiId"] as? String ?: "",
                    phoneNumber = data["phoneNumber"] as? String ?: "",
                    countryCode = data["countryCode"] as? String ?: "",
                    timezone = data["timezone"] as? String ?: AppConstants.DEFAULT_TIMEZONE
                )
            }
            Result.success(PaginatedResult(
                items = users,
                hasMore = snapshot.size() == pageSize,
                lastId = if (snapshot.size() > 0) snapshot.documents.last().id else null
            ))
        } catch (e: Exception) {
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    private suspend fun requireSuperadmin(): Result<Unit> {
        return try {
            val uid = auth.currentUser?.uid ?: return Result.failure(Exception(ErrorMessages.USER_NOT_AUTHENTICATED))
            val currentUserDoc = firestore.collection("users").document(uid).get().await()
            val currentRole = currentUserDoc.data?.get("role") as? String ?: "user"
            if (currentRole != "superadmin") return Result.failure(Exception(ErrorMessages.ACCESS_DENIED_SUPERADMIN))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    override suspend fun blockUser(uid: String): Result<Unit> {
        return try {
            val currentUid = auth.currentUser?.uid ?: return Result.failure(Exception(ErrorMessages.USER_NOT_AUTHENTICATED))
            if (uid == currentUid) return Result.failure(Exception("Cannot block yourself"))
            requireSuperadmin().onFailure { return Result.failure(it) }
            firestore.collection("users").document(uid)
                .update(mapOf("blocked" to true, "updatedAt" to System.currentTimeMillis())).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    override suspend fun unblockUser(uid: String): Result<Unit> {
        return try {
            requireSuperadmin().onFailure { return Result.failure(it) }
            firestore.collection("users").document(uid)
                .update(mapOf("blocked" to false, "updatedAt" to System.currentTimeMillis())).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    override suspend fun promoteToSuperAdmin(uid: String): Result<Unit> {
        return try {
            requireSuperadmin().onFailure { return Result.failure(it) }
            firestore.collection("users").document(uid)
                .update(mapOf("role" to "superadmin", "updatedAt" to System.currentTimeMillis())).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    override suspend fun demoteToUser(uid: String): Result<Unit> {
        return try {
            val currentUid = auth.currentUser?.uid ?: return Result.failure(Exception(ErrorMessages.USER_NOT_AUTHENTICATED))
            if (uid == currentUid) return Result.failure(Exception("Cannot demote yourself"))
            requireSuperadmin().onFailure { return Result.failure(it) }
            firestore.collection("users").document(uid)
                .update(mapOf("role" to "user", "updatedAt" to System.currentTimeMillis())).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    override suspend fun getReminderConfig(): Result<ReminderConfig?> {
        return try {
            requireSuperadmin().onFailure { return Result.failure(it) }
            val doc = firestore.collection("config").document("dailyReminder").get().await()
            if (!doc.exists()) return Result.success(null)
            val data = doc.data ?: emptyMap()
            @Suppress("UNCHECKED_CAST")
            val overrides = data["timezoneOverrides"] as? Map<String, String> ?: emptyMap()
            val featured = (data["featuredMessage"] as? Map<String, Any>)?.let { fm ->
                FeaturedMessage(
                    title = fm["title"] as? String,
                    body = fm["body"] as? String ?: return@let null,
                    startAt = (fm["startAt"] as? Number)?.toLong() ?: 0,
                    endAt = (fm["endAt"] as? Number)?.toLong() ?: 0
                )
            }
            Result.success(ReminderConfig(
                enabled = data["enabled"] as? Boolean ?: true,
                featuredMessage = featured,
                defaultLocalTime = data["defaultLocalTime"] as? String ?: "20:00",
                timezoneOverrides = overrides,
                updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: 0
            ))
        } catch (e: Exception) {
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    override suspend fun saveReminderConfig(config: ReminderConfig): Result<Unit> {
        return try {
            requireSuperadmin().onFailure { return Result.failure(it) }
            val data = mutableMapOf<String, Any>(
                "enabled" to config.enabled,
                "defaultLocalTime" to config.defaultLocalTime,
                "timezoneOverrides" to config.timezoneOverrides,
                "updatedAt" to System.currentTimeMillis()
            )
            val featured = config.featuredMessage
            if (featured != null) {
                data["featuredMessage"] = mapOf(
                    "title" to (featured.title ?: ""),
                    "body" to featured.body,
                    "startAt" to featured.startAt,
                    "endAt" to featured.endAt
                )
            } else {
                data["featuredMessage"] = FieldValue.delete()
            }
            firestore.collection("config").document("dailyReminder")
                .set(data, SetOptions.merge()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }
}
