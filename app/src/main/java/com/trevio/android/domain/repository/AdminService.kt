package com.trevio.android.domain.repository

import com.trevio.android.domain.model.User

interface AdminService {
    suspend fun getAllUsers(): Result<List<User>>
    suspend fun blockUser(uid: String): Result<Unit>
    suspend fun unblockUser(uid: String): Result<Unit>
    suspend fun promoteToSuperAdmin(uid: String): Result<Unit>
    suspend fun demoteToUser(uid: String): Result<Unit>
}
