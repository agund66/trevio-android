package com.trevio.android.domain.repository

import com.trevio.android.domain.model.Activity
import com.trevio.android.domain.model.Group
import com.trevio.android.domain.model.GroupTemplate
import com.trevio.android.domain.model.PaginatedResult

data class GroupInfo(
    val groupId: String = "",
    val name: String = "",
    val description: String = "",
    val template: GroupTemplate = GroupTemplate.CASUAL,
    val currency: String = "INR",
    val inviteCode: String = "",
    val createdBy: String = "",
    val memberCount: Int = 0,
    val totalExpenses: Double = 0.0,
    val archived: Boolean = false,
    val monthlyBudget: Double? = null,
    val budgetCategories: Map<String, Double>? = null
)

interface GroupService {
    suspend fun createGroup(
        name: String,
        description: String,
        template: GroupTemplate,
        memberUids: List<String>,
        monthlyBudget: Double? = null
    ): Result<Pair<String, String>>

    suspend fun joinGroupViaCode(inviteCode: String): Result<Pair<String, String>>
    suspend fun sendGroupInvitation(groupId: String, username: String): Result<Unit>
    suspend fun acceptInvitation(invitationId: String): Result<Pair<String, String>>
    suspend fun declineInvitation(invitationId: String): Result<Unit>
    suspend fun leaveGroup(groupId: String): Result<Unit>
    suspend fun archiveGroup(groupId: String): Result<Unit>
    suspend fun unarchiveGroup(groupId: String): Result<Unit>
    suspend fun deleteGroup(groupId: String): Result<Unit>
    suspend fun updateGroup(groupId: String, name: String, description: String): Result<Unit>
    suspend fun updateGroupBudget(groupId: String, monthlyBudget: Double?, budgetCategories: Map<String, Double>?): Result<Unit>
    suspend fun transferAdminRole(groupId: String, newAdminUid: String): Result<Unit>
    suspend fun getUserGroups(): Result<List<Group>>
    suspend fun getGroupInfo(groupId: String): Result<GroupInfo>
    suspend fun getGroupActivities(groupId: String, pageSize: Int = 50, lastActivityId: String? = null): Result<PaginatedResult<Activity>>
    suspend fun addOfflineMember(groupId: String, displayName: String): Result<String>
    suspend fun claimOfflineMember(groupId: String, memberDocId: String): Result<Unit>
    suspend fun linkOfflineMember(groupId: String, memberDocId: String, realUid: String): Result<Unit>
    suspend fun removeMember(groupId: String, memberUid: String): Result<Unit>
}
