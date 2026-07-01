package com.trevio.android.domain.repository

import com.trevio.android.domain.model.User
import com.trevio.android.domain.model.UserSearchResult

interface UserService {
    suspend fun getUser(uid: String): Result<User>
    suspend fun updateUser(user: User): Result<Unit>
    suspend fun acceptTnC(): Result<String>
    suspend fun checkUsernameAvailability(username: String): Result<Pair<Boolean, String>>
    suspend fun updateUsername(username: String): Result<String>
    suspend fun searchUsers(query: String): Result<List<UserSearchResult>>
    suspend fun updateFcmToken(token: String): Result<Unit>
    suspend fun deleteAccount(): Result<Unit>
}
