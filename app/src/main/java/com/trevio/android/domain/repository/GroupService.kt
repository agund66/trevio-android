package com.trevio.android.domain.repository

import com.trevio.android.domain.model.Activity
import com.trevio.android.domain.model.Group
import com.trevio.android.domain.model.GroupTemplate

data class GroupInfo(
    val groupId: String = "",
    val name: String = "",
    val description: String = "",
    val template: GroupTemplate = GroupTemplate.CASUAL,
    val currency: String = "INR",
    val inviteCode: String = "",
    val createdBy: String = "",
    val memberCount: Int = 0,
    val totalExpenses: Double = 0.0
)

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
    suspend fun getGroupInfo(groupId: String): Result<GroupInfo>
    suspend fun getGroupActivities(groupId: String, pageSize: Int = 50): Result<List<Activity>>
}
