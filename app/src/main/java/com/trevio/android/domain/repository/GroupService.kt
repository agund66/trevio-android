package com.trevio.android.domain.repository

import com.trevio.android.domain.model.Group
import com.trevio.android.domain.model.GroupTemplate

interface GroupService {
    suspend fun createGroup(
        name: String,
        description: String,
        template: GroupTemplate,
        currency: String,
        memberUids: List<String>
    ): Result<Pair<String, String>>

    suspend fun joinGroupViaCode(inviteCode: String): Result<Pair<String, String>>
    suspend fun sendGroupInvitation(groupId: String, username: String): Result<Unit>
    suspend fun acceptInvitation(invitationId: String): Result<Pair<String, String>>
    suspend fun declineInvitation(invitationId: String): Result<Unit>
    suspend fun leaveGroup(groupId: String): Result<Unit>
    suspend fun getUserGroups(): Result<List<Group>>
}
