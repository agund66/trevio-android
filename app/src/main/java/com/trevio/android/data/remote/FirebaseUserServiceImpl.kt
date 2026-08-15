package com.trevio.android.data.remote

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.trevio.android.domain.model.User
import com.trevio.android.domain.model.UserSearchResult
import com.trevio.android.domain.repository.UserService
import com.trevio.android.util.AppConstants
import com.trevio.android.util.Calculations
import com.trevio.android.util.friendlyNetworkMessage
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseUserServiceImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : UserService {

    override suspend fun getUser(uid: String): Result<User> {
        return try {
            val doc = firestore.collection("users").document(uid).get().await()
            val data = doc.data ?: return Result.failure(Exception("User not found"))
            Result.success(
                User(
                    uid = uid,
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
            )
        } catch (e: Exception) {
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    override suspend fun updateUser(user: User): Result<Unit> {
        return try {
            val uid = auth.currentUser?.uid ?: return Result.failure(Exception("User not authenticated"))
            if (uid != user.uid) return Result.failure(Exception("Cannot update another user's profile"))
            val updates = mapOf(
                "displayName" to user.displayName,
                "firstName" to user.firstName,
                "lastName" to user.lastName,
                "photoURL" to user.photoURL,
                "defaultCurrency" to user.defaultCurrency,
                "upiId" to user.upiId,
                "phoneNumber" to user.phoneNumber,
                "countryCode" to user.countryCode,
                "timezone" to user.timezone,
                "updatedAt" to System.currentTimeMillis()
            )
            firestore.collection("users").document(user.uid)
                .update(updates).await()
            // Sync denormalized profile info to all member docs
            syncUserProfileToGroups()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    /**
     * Syncs the current user's displayName, username, and photoURL to
     * all member docs across all groups they belong to.  This keeps
     * denormalized profile info fresh when the user updates their profile.
     */
    override suspend fun syncUserProfileToGroups(): Result<Unit> {
        return try {
            val uid = auth.currentUser?.uid ?: return Result.failure(Exception("User not authenticated"))

            val (userDoc, memberSnapshot) = coroutineScope {
                val userDocAsync = async { firestore.collection("users").document(uid).get().await() }
                val memberSnapshotAsync = async {
                    firestore.collectionGroup("members")
                        .whereEqualTo("uid", uid)
                        .whereEqualTo("status", "active")
                        .get().await()
                }
                userDocAsync.await() to memberSnapshotAsync.await()
            }
            val displayName = userDoc.getString("displayName") ?: ""
            val username = userDoc.getString("username") ?: ""
            val photoURL = userDoc.getString("photoURL") ?: ""

            val toUpdate = memberSnapshot.documents.filter { it.getBoolean("isOffline") != true }
            val batchSize = 400
            for (i in toUpdate.indices step batchSize) {
                val chunk = toUpdate.subList(i, minOf(i + batchSize, toUpdate.size))
                val batch = firestore.batch()
                for (memberDoc in chunk) {
                    batch.update(memberDoc.reference, mapOf(
                        "displayName" to displayName,
                        "username" to username,
                        "photoURL" to photoURL
                    ))
                }
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    override suspend fun acceptTnC(): Result<String> {
        return try {
            val uid = auth.currentUser?.uid ?: return Result.failure(Exception("User not authenticated"))
            val userDocRef = firestore.collection("users").document(uid)
            val userDoc = userDocRef.get().await()
            val data = userDoc.data

            // If already accepted TnC AND has a username, return it
            if (data != null && data["acceptedTnC"] as? Boolean == true && !(data["username"] as? String?).isNullOrEmpty()) {
                return Result.success(data["username"] as String)
            }

            val now = System.currentTimeMillis()

            // Set acceptedTnC if not already set
            if (data?.get("acceptedTnC") as? Boolean != true) {
                userDocRef.set(
                    mapOf(
                        "acceptedTnC" to true,
                        "acceptedTnCAt" to now,
                        "updatedAt" to now
                    ),
                    com.google.firebase.firestore.SetOptions.merge()
                ).await()
            }

            val existingUsername = data?.get("username") as? String
            if (existingUsername.isNullOrEmpty()) {
                val firstName = data?.get("firstName") as? String ?: ""
                val lastName = data?.get("lastName") as? String ?: ""
                val email = data?.get("email") as? String ?: ""
                var baseUsername = Calculations.generateBaseUsername(firstName, lastName)
                if (baseUsername.isEmpty()) {
                    // Fall back to email prefix
                    val emailPrefix = email.substringBefore("@").lowercase().replace(Regex("[^a-z0-9]"), "")
                    baseUsername = emailPrefix.ifEmpty { "user" }
                }
                val finalUsername = findUniqueUsername(baseUsername)

                firestore.collection("usernames").document(finalUsername)
                    .set(mapOf("uid" to uid)).await()
                userDocRef.update(mapOf("username" to finalUsername, "updatedAt" to now)).await()

                Result.success(finalUsername)
            } else {
                Result.success(existingUsername)
            }
        } catch (e: Exception) {
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    private suspend fun findUniqueUsername(base: String): String {
        var username = base
        var suffix = 0
        val maxAttempts = 20
        var attempts = 0
        while (attempts < maxAttempts) {
            val doc = firestore.collection("usernames").document(username).get().await()
            if (!doc.exists()) return username
            suffix++
            username = "$base$suffix"
            attempts++
        }
        return username
    }

    override suspend fun checkUsernameAvailability(username: String): Result<Pair<Boolean, String>> {
        return try {
            if (username.length < 3) return Result.success(Pair(false, ""))
            val normalized = username.lowercase().replace(Regex("[^a-z0-9._]"), "")
            val doc = firestore.collection("usernames").document(normalized).get().await()
            Result.success(Pair(!doc.exists(), normalized))
        } catch (e: Exception) {
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    override suspend fun updateUsername(username: String): Result<String> {
        return try {
            val uid = auth.currentUser?.uid ?: return Result.failure(Exception("User not authenticated"))
            if (username.length < 3) return Result.failure(Exception("Username must be at least 3 characters"))

            val normalized = username.lowercase().replace(Regex("[^a-z0-9._]"), "")
            if (normalized.length < 3) return Result.failure(Exception("Username too short after normalization"))

            val userDocRef = firestore.collection("users").document(uid)
            val userDoc = userDocRef.get().await()
            if (!userDoc.exists()) return Result.failure(Exception("User document not found"))

            val currentUsername = userDoc.data?.get("username") as? String
            if (currentUsername == normalized) return Result.success(normalized)

            val usernameDoc = firestore.collection("usernames").document(normalized).get().await()
            if (usernameDoc.exists()) return Result.failure(Exception("Username is already taken"))

            val batch = firestore.batch()
            if (currentUsername != null) {
                batch.delete(firestore.collection("usernames").document(currentUsername))
            }
            batch.set(firestore.collection("usernames").document(normalized), mapOf("uid" to uid))
            batch.update(userDocRef, mapOf("username" to normalized, "updatedAt" to System.currentTimeMillis()))
            batch.commit().await()

            // Sync username to all member docs
            syncUserProfileToGroups()

            Result.success(normalized)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    override suspend fun searchUsers(query: String): Result<List<UserSearchResult>> {
        return try {
            if (query.isEmpty()) return Result.success(emptyList())

            val normalized = query.lowercase().replace(Regex("[^a-z0-9._]"), "")
            val snapshot = firestore.collection("users")
                .whereGreaterThanOrEqualTo("username", normalized)
                .whereLessThanOrEqualTo("username", normalized + "\uf8ff")
                .limit(10)
                .get().await()

            val currentUid = auth.currentUser?.uid
            val users = snapshot.documents
                .filter { it.id != currentUid }
                .mapNotNull { doc ->
                    val data = doc.data ?: return@mapNotNull null
                    val uname = data["username"] as? String ?: ""
                    if (uname.isEmpty()) return@mapNotNull null
                    UserSearchResult(
                        uid = data["uid"] as? String ?: doc.id,
                        username = uname,
                        displayName = data["displayName"] as? String ?: "",
                        photoURL = data["photoURL"] as? String ?: ""
                    )
                }
            Result.success(users)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    override suspend fun updateFcmToken(token: String): Result<Unit> {
        return try {
            val uid = auth.currentUser?.uid ?: return Result.failure(Exception("User not authenticated"))
            firestore.collection("users").document(uid)
                .update(mapOf("fcmToken" to token, "updatedAt" to System.currentTimeMillis())).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    override suspend fun deleteAccount(): Result<Unit> {
        return try {
            val uid = auth.currentUser?.uid ?: return Result.failure(Exception("User not authenticated"))
            val currentUser = auth.currentUser ?: return Result.failure(Exception("User not authenticated"))

            // 1. Get user doc to find username
            // 2. Find all group memberships and set status to "left"
            val (userDoc, membersSnapshot) = coroutineScope {
                val userDocAsync = async { firestore.collection("users").document(uid).get().await() }
                val membersSnapshotAsync = async {
                    firestore.collectionGroup("members")
                        .whereEqualTo("uid", uid)
                        .whereEqualTo("status", "active")
                        .get().await()
                }
                userDocAsync.await() to membersSnapshotAsync.await()
            }
            val username = userDoc.getString("username")

            val memberDocs = membersSnapshot.documents
            val batchSize = 200
            for (i in memberDocs.indices step batchSize) {
                val chunk = memberDocs.subList(i, minOf(i + batchSize, memberDocs.size))
                val batch = firestore.batch()
                for (memberDoc in chunk) {
                    val pathSegments = memberDoc.reference.path.split("/")
                    val groupId = pathSegments.getOrNull(1) ?: continue
                    batch.update(memberDoc.reference, mapOf("status" to "left", "leftAt" to System.currentTimeMillis()))
                    batch.update(firestore.collection("groups").document(groupId), mapOf("memberCount" to FieldValue.increment(-1)))
                }
                batch.commit().await()
            }

            // 3. Delete username doc if exists
            if (!username.isNullOrBlank()) {
                firestore.collection("usernames").document(username).delete().await()
            }

            // 4. Delete user doc
            firestore.collection("users").document(uid).delete().await()

            // 5. Delete Firebase Auth account
            currentUser.delete().await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }
}
