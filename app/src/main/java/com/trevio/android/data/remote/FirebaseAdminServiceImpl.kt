package com.trevio.android.data.remote

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.trevio.android.domain.model.User
import com.trevio.android.domain.repository.AdminService
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseAdminServiceImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : AdminService {

    override suspend fun getAllUsers(): Result<List<User>> {
        return try {
            val uid = auth.currentUser?.uid ?: return Result.failure(Exception("User not authenticated"))
            val currentUserDoc = firestore.collection("users").document(uid).get().await()
            val currentRole = currentUserDoc.data?.get("role") as? String ?: "user"
            if (currentRole != "superadmin") return Result.failure(Exception("Access denied"))

            val snapshot = firestore.collection("users")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(500)
                .get().await()

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
                    defaultCurrency = data["defaultCurrency"] as? String ?: "INR",
                    acceptedTnC = data["acceptedTnC"] as? Boolean ?: false,
                    role = data["role"] as? String ?: "user",
                    blocked = data["blocked"] as? Boolean ?: false,
                    upiId = data["upiId"] as? String ?: "",
                    phoneNumber = data["phoneNumber"] as? String ?: "",
                    countryCode = data["countryCode"] as? String ?: ""
                )
            }
            Result.success(users)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun requireSuperadmin(): Result<Unit> {
        return try {
            val uid = auth.currentUser?.uid ?: return Result.failure(Exception("User not authenticated"))
            val currentUserDoc = firestore.collection("users").document(uid).get().await()
            val currentRole = currentUserDoc.data?.get("role") as? String ?: "user"
            if (currentRole != "superadmin") return Result.failure(Exception("Access denied: superadmin only"))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun blockUser(uid: String): Result<Unit> {
        return try {
            val currentUid = auth.currentUser?.uid ?: return Result.failure(Exception("User not authenticated"))
            if (uid == currentUid) return Result.failure(Exception("Cannot block yourself"))
            requireSuperadmin().onFailure { return Result.failure(it) }
            firestore.collection("users").document(uid)
                .update(mapOf("blocked" to true, "updatedAt" to System.currentTimeMillis())).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun unblockUser(uid: String): Result<Unit> {
        return try {
            requireSuperadmin().onFailure { return Result.failure(it) }
            firestore.collection("users").document(uid)
                .update(mapOf("blocked" to false, "updatedAt" to System.currentTimeMillis())).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun promoteToSuperAdmin(uid: String): Result<Unit> {
        return try {
            requireSuperadmin().onFailure { return Result.failure(it) }
            firestore.collection("users").document(uid)
                .update(mapOf("role" to "superadmin", "updatedAt" to System.currentTimeMillis())).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun demoteToUser(uid: String): Result<Unit> {
        return try {
            val currentUid = auth.currentUser?.uid ?: return Result.failure(Exception("User not authenticated"))
            if (uid == currentUid) return Result.failure(Exception("Cannot demote yourself"))
            requireSuperadmin().onFailure { return Result.failure(it) }
            firestore.collection("users").document(uid)
                .update(mapOf("role" to "user", "updatedAt" to System.currentTimeMillis())).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
