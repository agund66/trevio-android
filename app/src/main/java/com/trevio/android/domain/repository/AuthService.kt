package com.trevio.android.domain.repository

import com.trevio.android.domain.model.User

interface AuthService {
    suspend fun signInWithGoogle(idToken: String): Result<String>
    suspend fun getCurrentUserId(): String?
    suspend fun signOut()
    suspend fun getCurrentUser(): User?
    suspend fun createUserDocument(user: User): Result<Unit>
    suspend fun isUserAuthenticated(): Boolean
}
