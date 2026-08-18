package com.trevio.android.data.remote

import android.app.Activity
import com.trevio.android.util.AppConstants
import com.trevio.android.util.friendlyNetworkMessage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.OAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.trevio.android.domain.model.User
import com.trevio.android.domain.repository.AuthService
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseAuthServiceImpl @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : AuthService {

    @Volatile
    private var cachedUser: User? = null
    @Volatile
    private var cachedUserTime: Long = 0

    override suspend fun signInWithGoogle(idToken: String): Result<String> {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = auth.signInWithCredential(credential).await()
            val firebaseUser = result.user ?: return Result.failure(Exception("Authentication failed: no user returned"))

            val userDoc = firestore.collection("users").document(firebaseUser.uid).get().await()
            if (!userDoc.exists()) {
                val displayName = firebaseUser.displayName ?: ""
                val nameParts = displayName.split(" ", limit = 2)
                val firstName = nameParts.getOrElse(0) { "" }
                val lastName = nameParts.getOrElse(1) { "" }

                val newUser = mapOf(
                    "uid" to firebaseUser.uid,
                    "email" to (firebaseUser.email ?: ""),
                    "displayName" to displayName,
                    "firstName" to firstName,
                    "lastName" to lastName,
                    "photoURL" to (firebaseUser.photoUrl?.toString() ?: ""),
                    "defaultCurrency" to AppConstants.BASE_CURRENCY,
                    "acceptedTnC" to false,
                    "role" to "user",
                    "blocked" to false,
                    "timezone" to AppConstants.DEFAULT_TIMEZONE,
                    "createdAt" to System.currentTimeMillis(),
                    "updatedAt" to System.currentTimeMillis()
                )

                firestore.collection("users").document(firebaseUser.uid)
                    .set(newUser).await()
            }

            Result.success(firebaseUser.uid)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    override suspend fun signInWithGoogleWeb(activity: Activity): Result<String> {
        return try {
            val provider = OAuthProvider.newBuilder("google.com")
            provider.addCustomParameter("prompt", "select_account")

            val pendingResult = auth.pendingAuthResult
            if (pendingResult != null) {
                pendingResult.await()
            } else {
                auth.startActivityForSignInWithProvider(activity, provider.build()).await()
            }

            val firebaseUser = auth.currentUser ?: return Result.failure(Exception("Authentication failed: no user returned"))

            val userDoc = firestore.collection("users").document(firebaseUser.uid).get().await()
            if (!userDoc.exists()) {
                val displayName = firebaseUser.displayName ?: ""
                val nameParts = displayName.split(" ", limit = 2)
                val firstName = nameParts.getOrElse(0) { "" }
                val lastName = nameParts.getOrElse(1) { "" }

                val newUser = mapOf(
                    "uid" to firebaseUser.uid,
                    "email" to (firebaseUser.email ?: ""),
                    "displayName" to displayName,
                    "firstName" to firstName,
                    "lastName" to lastName,
                    "photoURL" to (firebaseUser.photoUrl?.toString() ?: ""),
                    "defaultCurrency" to AppConstants.BASE_CURRENCY,
                    "acceptedTnC" to false,
                    "role" to "user",
                    "blocked" to false,
                    "timezone" to AppConstants.DEFAULT_TIMEZONE,
                    "createdAt" to System.currentTimeMillis(),
                    "updatedAt" to System.currentTimeMillis()
                )

                firestore.collection("users").document(firebaseUser.uid)
                    .set(newUser).await()
            }

            Result.success(firebaseUser.uid)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    override suspend fun getCurrentUserId(): String? {
        return auth.currentUser?.uid
    }

    override suspend fun signOut() {
        clearUserCache()
        auth.signOut()
    }

    override fun clearUserCache() {
        cachedUser = null
        cachedUserTime = 0
    }

    override suspend fun getCurrentUser(): User? {
        val cached = cachedUser
        if (cached != null && System.currentTimeMillis() - cachedUserTime < 30_000) {
            return cached
        }
        val firebaseUser = auth.currentUser ?: return null
        return try {
            val doc = firestore.collection("users").document(firebaseUser.uid).get().await()
            if (doc.exists()) {
                val data = doc.data ?: return null
                val user = User(
                    uid = firebaseUser.uid,
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
                    timezone = data["timezone"] as? String ?: AppConstants.DEFAULT_TIMEZONE,
                    karmaScore = (data["karmaScore"] as? Number)?.toInt() ?: 0,
                    karmaTier = data["karmaTier"] as? String ?: "bronze",
                    karmaPublic = data["karmaPublic"] as? Boolean ?: false,
                    karmaUpdatedAt = (data["karmaUpdatedAt"] as? Number)?.toLong() ?: 0
                )
                cachedUser = user
                cachedUserTime = System.currentTimeMillis()
                user
            } else null
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun createUserDocument(user: User): Result<Unit> {
        return try {
            firestore.collection("users").document(user.uid).set(user).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    override suspend fun isUserAuthenticated(): Boolean {
        return auth.currentUser != null
    }
}
