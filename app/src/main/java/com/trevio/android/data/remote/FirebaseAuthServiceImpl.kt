package com.trevio.android.data.remote

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
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

    override suspend fun signInWithGoogle(idToken: String): Result<String> {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = auth.signInWithCredential(credential).await()
            val firebaseUser = result.user!!

            val userDoc = firestore.collection("users").document(firebaseUser.uid).get().await()
            if (!userDoc.exists()) {
                val displayName = firebaseUser.displayName ?: ""
                val nameParts = displayName.split(" ", limit = 2)
                val firstName = nameParts.getOrElse(0) { "" }
                val lastName = nameParts.getOrElse(1) { "" }

                val newUser = User(
                    uid = firebaseUser.uid,
                    email = firebaseUser.email ?: "",
                    displayName = displayName,
                    firstName = firstName,
                    lastName = lastName,
                    photoURL = firebaseUser.photoUrl?.toString() ?: "",
                    defaultCurrency = "INR",
                    acceptedTnC = false
                )

                firestore.collection("users").document(firebaseUser.uid)
                    .set(newUser).await()
            }

            Result.success(firebaseUser.uid)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getCurrentUserId(): String? {
        return auth.currentUser?.uid
    }

    override suspend fun signOut() {
        auth.signOut()
    }

    override suspend fun getCurrentUser(): User? {
        val firebaseUser = auth.currentUser ?: return null
        return try {
            val doc = firestore.collection("users").document(firebaseUser.uid).get().await()
            if (doc.exists()) {
                val data = doc.data ?: return null
                User(
                    uid = firebaseUser.uid,
                    email = data["email"] as? String ?: "",
                    displayName = data["displayName"] as? String ?: "",
                    firstName = data["firstName"] as? String ?: "",
                    lastName = data["lastName"] as? String ?: "",
                    username = data["username"] as? String ?: "",
                    photoURL = data["photoURL"] as? String ?: "",
                    defaultCurrency = data["defaultCurrency"] as? String ?: "INR",
                    acceptedTnC = data["acceptedTnC"] as? Boolean ?: false,
                    upiId = data["upiId"] as? String ?: "",
                    phoneNumber = data["phoneNumber"] as? String ?: "",
                    countryCode = data["countryCode"] as? String ?: ""
                )
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
            Result.failure(e)
        }
    }

    override suspend fun isUserAuthenticated(): Boolean {
        return auth.currentUser != null
    }
}
