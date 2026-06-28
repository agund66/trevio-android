package com.trevio.android.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.trevio.android.domain.model.User
import com.trevio.android.domain.model.UserSearchResult
import com.trevio.android.domain.repository.UserService
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseUserServiceImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val functions: FirebaseFunctions
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
                    defaultCurrency = data["defaultCurrency"] as? String ?: "INR",
                    acceptedTnC = data["acceptedTnC"] as? Boolean ?: false,
                    upiId = data["upiId"] as? String ?: ""
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateUser(user: User): Result<Unit> {
        return try {
            val updates = mapOf(
                "displayName" to user.displayName,
                "firstName" to user.firstName,
                "lastName" to user.lastName,
                "photoURL" to user.photoURL,
                "defaultCurrency" to user.defaultCurrency,
                "upiId" to user.upiId
            )
            firestore.collection("users").document(user.uid)
                .update(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun acceptTnC(): Result<String> {
        return try {
            val result = functions.getHttpsCallable("acceptTnC").call().await()
            val data = result.getData() as Map<*, *>
            Result.success(data["username"] as? String ?: "")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun checkUsernameAvailability(username: String): Result<Pair<Boolean, String>> {
        return try {
            val result = functions.getHttpsCallable("checkUsernameAvailability")
                .call(mapOf("username" to username)).await()
            val data = result.getData() as Map<*, *>
            Result.success(Pair(data["available"] as Boolean, data["suggestedUsername"] as String))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateUsername(username: String): Result<String> {
        return try {
            val result = functions.getHttpsCallable("updateUsername")
                .call(mapOf("username" to username)).await()
            val data = result.getData() as Map<*, *>
            Result.success(data["username"] as String)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun searchUsers(query: String): Result<List<UserSearchResult>> {
        return try {
            val result = functions.getHttpsCallable("searchUsers")
                .call(mapOf("query" to query)).await()
            val data = result.getData() as Map<*, *>
            @Suppress("UNCHECKED_CAST")
            val users = data["users"] as List<Map<*, *>>
            Result.success(users.map { u ->
                UserSearchResult(
                    uid = u["uid"] as String,
                    username = u["username"] as String,
                    displayName = u["displayName"] as String,
                    photoURL = u["photoURL"] as? String ?: ""
                )
            })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateFcmToken(token: String): Result<Unit> {
        return try {
            functions.getHttpsCallable("updateFcmToken")
                .call(mapOf("token" to token)).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
