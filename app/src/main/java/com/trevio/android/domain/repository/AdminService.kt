package com.trevio.android.domain.repository

import com.trevio.android.domain.model.PaginatedResult
import com.trevio.android.domain.model.ReminderConfig
import com.trevio.android.domain.model.User

interface AdminService {
    suspend fun getAllUsers(pageSize: Int = 50, lastUserUid: String? = null): Result<PaginatedResult<User>>
    suspend fun blockUser(uid: String): Result<Unit>
    suspend fun unblockUser(uid: String): Result<Unit>
    suspend fun promoteToSuperAdmin(uid: String): Result<Unit>
    suspend fun demoteToUser(uid: String): Result<Unit>
    suspend fun getReminderConfig(): Result<ReminderConfig?>
    suspend fun saveReminderConfig(config: ReminderConfig): Result<Unit>
}
