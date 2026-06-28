package com.trevio.android.data.remote

import com.google.firebase.functions.FirebaseFunctions
import com.trevio.android.domain.model.Group
import com.trevio.android.domain.model.GroupTemplate
import com.trevio.android.domain.repository.GroupInfo
import com.trevio.android.domain.repository.GroupService
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseGroupServiceImpl @Inject constructor(
    private val functions: FirebaseFunctions
) : GroupService {

    override suspend fun createGroup(
        name: String,
        description: String,
        template: GroupTemplate,
        currency: String,
        memberUids: List<String>
    ): Result<Pair<String, String>> {
        return try {
            val data = mapOf(
                "name" to name,
                "description" to description,
                "template" to template.name.lowercase(),
                "currency" to currency,
                "memberUids" to memberUids
            )
            val result = functions.getHttpsCallable("createGroup").call(data).await()
            val res = result.getData() as Map<*, *>
            Result.success(Pair(res["groupId"] as String, res["inviteCode"] as String))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun joinGroupViaCode(inviteCode: String): Result<Pair<String, String>> {
        return try {
            val result = functions.getHttpsCallable("joinGroupViaCode")
                .call(mapOf("inviteCode" to inviteCode)).await()
            val res = result.getData() as Map<*, *>
            Result.success(Pair(res["groupId"] as String, res["groupName"] as String))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun sendGroupInvitation(groupId: String, username: String): Result<Unit> {
        return try {
            functions.getHttpsCallable("sendGroupInvitation")
                .call(mapOf("groupId" to groupId, "username" to username)).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun acceptInvitation(invitationId: String): Result<Pair<String, String>> {
        return try {
            val result = functions.getHttpsCallable("acceptInvitation")
                .call(mapOf("invitationId" to invitationId)).await()
            val res = result.getData() as Map<*, *>
            Result.success(Pair(res["groupId"] as String, res["groupName"] as String))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun declineInvitation(invitationId: String): Result<Unit> {
        return try {
            functions.getHttpsCallable("declineInvitation")
                .call(mapOf("invitationId" to invitationId)).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun leaveGroup(groupId: String): Result<Unit> {
        return try {
            functions.getHttpsCallable("leaveGroup")
                .call(mapOf("groupId" to groupId)).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getUserGroups(): Result<List<Group>> {
        return try {
            val result = functions.getHttpsCallable("getUserGroups").call().await()
            val data = result.getData() as Map<*, *>
            @Suppress("UNCHECKED_CAST")
            val groups = data["groups"] as List<Map<*, *>>
            Result.success(groups.map { g ->
                Group(
                    groupId = g["groupId"] as String,
                    name = g["name"] as String,
                    description = g["description"] as? String ?: "",
                    template = GroupTemplate.valueOf((g["template"] as? String ?: "casual").uppercase()),
                    currency = g["currency"] as? String ?: "INR",
                    createdBy = g["createdBy"] as? String ?: "",
                    inviteCode = g["inviteCode"] as? String ?: "",
                    memberCount = (g["memberCount"] as? Long ?: 0L).toInt(),
                    totalExpenses = (g["totalExpenses"] as? Double ?: 0.0),
                    yourBalance = (g["yourBalance"] as? Double ?: 0.0),
                    yourRole = g["yourRole"] as? String ?: "member"
                )
            })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getGroupInfo(groupId: String): Result<GroupInfo> {
        return try {
            val result = functions.getHttpsCallable("getGroupInfo")
                .call(mapOf("groupId" to groupId)).await()
            val g = result.getData() as Map<*, *>
            Result.success(
                GroupInfo(
                    groupId = g["groupId"] as String,
                    name = g["name"] as String,
                    description = g["description"] as? String ?: "",
                    template = GroupTemplate.valueOf((g["template"] as? String ?: "casual").uppercase()),
                    currency = g["currency"] as? String ?: "INR",
                    inviteCode = g["inviteCode"] as? String ?: "",
                    createdBy = g["createdBy"] as? String ?: "",
                    memberCount = (g["memberCount"] as? Long ?: 0L).toInt(),
                    totalExpenses = (g["totalExpenses"] as? Double ?: 0.0)
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
