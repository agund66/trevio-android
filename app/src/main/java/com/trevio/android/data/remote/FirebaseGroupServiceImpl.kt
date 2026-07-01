package com.trevio.android.data.remote

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.trevio.android.domain.model.Activity
import com.trevio.android.domain.model.Group
import com.trevio.android.domain.model.GroupTemplate
import com.trevio.android.domain.repository.GroupInfo
import com.trevio.android.domain.repository.GroupService
import com.trevio.android.util.Calculations
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseGroupServiceImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : GroupService {

    override suspend fun createGroup(
        name: String,
        description: String,
        template: GroupTemplate,
        memberUids: List<String>
    ): Result<Pair<String, String>> {
        return try {
            val uid = auth.currentUser?.uid ?: return Result.failure(Exception("User not authenticated"))
            if (name.isBlank()) return Result.failure(Exception("Group name is required"))

            val userDoc = firestore.collection("users").document(uid).get().await()
            val userCurrency = userDoc.getString("defaultCurrency") ?: "INR"

            val now = System.currentTimeMillis()
            val inviteCode = Calculations.generateInviteCode()
            val groupRef = firestore.collection("groups").document()
            val groupId = groupRef.id

            val batch = firestore.batch()
            batch.set(groupRef, mapOf(
                "name" to name.trim(),
                "description" to description.trim(),
                "template" to template.name.lowercase(),
                "currency" to userCurrency,
                "createdBy" to uid,
                "inviteCode" to inviteCode,
                "memberCount" to 1,
                "totalExpenses" to 0.0,
                "createdAt" to now,
                "updatedAt" to now
            ))
            batch.set(groupRef.collection("members").document(uid), mapOf(
                "uid" to uid,
                "role" to "admin",
                "joinedAt" to now,
                "balance" to 0.0,
                "status" to "active"
            ))
            batch.set(groupRef.collection("activities").document(), mapOf(
                "type" to "group_created",
                "description" to "Group created",
                "userId" to uid,
                "data" to mapOf("groupName" to name.trim()),
                "createdAt" to now
            ))
            batch.commit().await()

            for (memberUid in memberUids) {
                if (memberUid != uid) {
                    sendInvitationInternal(uid, memberUid, groupId, name.trim(), inviteCode)
                }
            }

            Result.success(Pair(groupId, inviteCode))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun joinGroupViaCode(inviteCode: String): Result<Pair<String, String>> {
        return try {
            val uid = auth.currentUser?.uid ?: return Result.failure(Exception("User not authenticated"))
            if (inviteCode.isBlank()) return Result.failure(Exception("Invite code is required"))

            val snapshot = firestore.collection("groups")
                .whereEqualTo("inviteCode", inviteCode.uppercase())
                .limit(1)
                .get().await()
            if (snapshot.isEmpty) return Result.failure(Exception("Invalid invite code"))

            val groupDoc = snapshot.documents[0]
            val groupId = groupDoc.id
            val groupData = groupDoc.data ?: return Result.failure(Exception("Invalid group data"))

            val memberDoc = groupDoc.reference.collection("members").document(uid).get().await()
            if (memberDoc.exists()) return Result.failure(Exception("You are already a member of this group"))

            val now = System.currentTimeMillis()
            val batch = firestore.batch()
            batch.set(groupDoc.reference.collection("members").document(uid), mapOf(
                "uid" to uid,
                "role" to "member",
                "joinedAt" to now,
                "balance" to 0.0,
                "status" to "active"
            ))
            batch.update(groupDoc.reference, mapOf(
                "memberCount" to ((groupData["memberCount"] as? Number)?.toLong() ?: 0L) + 1,
                "userId" to uid,
                "data" to mapOf("groupId" to groupId),
                "createdAt" to now
            ))
            batch.commit().await()

            Result.success(Pair(groupId, groupData["name"] as? String ?: ""))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun sendGroupInvitation(groupId: String, username: String): Result<Unit> {
        return try {
            val uid = auth.currentUser?.uid ?: return Result.failure(Exception("User not authenticated"))
            if (groupId.isBlank() || username.isBlank()) return Result.failure(Exception("Group ID and username are required"))

            val normalized = username.lowercase().replace(Regex("[^a-z0-9._]"), "")
            val usernameDoc = firestore.collection("usernames").document(normalized).get().await()
            if (!usernameDoc.exists()) return Result.failure(Exception("User not found"))

            val toUid = usernameDoc.data?.get("uid") as? String ?: return Result.failure(Exception("Invalid user"))
            val groupDoc = firestore.collection("groups").document(groupId).get().await()
            if (!groupDoc.exists()) return Result.failure(Exception("Group not found"))

            val groupData = groupDoc.data ?: return Result.failure(Exception("Invalid group data"))
            val existingMember = groupDoc.reference.collection("members").document(toUid).get().await()
            if (existingMember.exists()) return Result.failure(Exception("User is already a member of this group"))

            sendInvitationInternal(uid, toUid, groupId, groupData["name"] as? String ?: "", groupData["inviteCode"] as? String ?: "")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun sendInvitationInternal(
        invitedByUid: String,
        toUid: String,
        groupId: String,
        groupName: String,
        inviteCode: String
    ) {
        val inviterDoc = firestore.collection("users").document(invitedByUid).get().await()
        val invitedByName = inviterDoc.data?.get("displayName") as? String ?: "Someone"

        val now = System.currentTimeMillis()
        val inviteRef = firestore.collection("invitations").document()
        inviteRef.set(mapOf(
            "groupId" to groupId,
            "groupName" to groupName,
            "invitedByUid" to invitedByUid,
            "invitedByName" to invitedByName,
            "toUid" to toUid,
            "inviteCode" to inviteCode,
            "status" to "pending",
            "createdAt" to now
        )).await()

        val groupRef = firestore.collection("groups").document(groupId)
        val pendingMemberDoc = groupRef.collection("members").document(toUid).get().await()
        if (!pendingMemberDoc.exists()) {
            groupRef.collection("members").document(toUid).set(mapOf(
                "uid" to toUid,
                "role" to "member",
                "joinedAt" to now,
                "balance" to 0.0,
                "status" to "pending"
            )).await()
            val groupDoc = groupRef.get().await()
            val currentCount = (groupDoc.data?.get("memberCount") as? Number)?.toLong() ?: 1L
            groupRef.update(mapOf("memberCount" to (currentCount + 1), "updatedAt" to now)).await()
        }

        firestore.collection("users").document(toUid).collection("notifications").document()
            .set(mapOf(
                "type" to "invitation",
                "title" to "Group Invitation",
                "body" to "$invitedByName invited you to join \"$groupName\"",
                "data" to mapOf("groupId" to groupId, "groupName" to groupName, "invitationId" to inviteRef.id, "type" to "invitation"),
                "read" to false,
                "createdAt" to now
            )).await()
    }

    override suspend fun acceptInvitation(invitationId: String): Result<Pair<String, String>> {
        return try {
            val uid = auth.currentUser?.uid ?: return Result.failure(Exception("User not authenticated"))
            if (invitationId.isBlank()) return Result.failure(Exception("Invitation ID is required"))

            val inviteDoc = firestore.collection("invitations").document(invitationId).get().await()
            if (!inviteDoc.exists()) return Result.failure(Exception("Invitation not found"))

            val inviteData = inviteDoc.data ?: return Result.failure(Exception("Invalid invitation"))
            if (inviteData["toUid"] != uid) return Result.failure(Exception("This invitation is not for you"))
            if (inviteData["status"] != "pending") return Result.failure(Exception("Invitation is no longer pending"))

            val groupId = inviteData["groupId"] as? String ?: return Result.failure(Exception("Invalid group ID"))
            val groupDoc = firestore.collection("groups").document(groupId).get().await()
            if (!groupDoc.exists()) return Result.failure(Exception("Group not found"))

            val groupData = groupDoc.data ?: return Result.failure(Exception("Invalid group data"))
            val now = System.currentTimeMillis()

            val existingMemberDoc = groupDoc.reference.collection("members").document(uid).get().await()
            val batch = firestore.batch()
            batch.update(inviteDoc.reference, mapOf("status" to "accepted"))

            if (existingMemberDoc.exists() && existingMemberDoc.data?.get("status") == "pending") {
                batch.update(groupDoc.reference.collection("members").document(uid), mapOf("status" to "active", "joinedAt" to now))
            } else {
                batch.set(groupDoc.reference.collection("members").document(uid), mapOf(
                    "uid" to uid, "role" to "member", "joinedAt" to now, "balance" to 0.0, "status" to "active"
                ))
                batch.update(groupDoc.reference, mapOf(
                    "memberCount" to ((groupData["memberCount"] as? Number)?.toLong() ?: 0L) + 1,
                    "updatedAt" to now
                ))
            }
            batch.set(groupDoc.reference.collection("activities").document(), mapOf(
                "type" to "member_joined",
                "description" to "Member joined via invitation",
                "userId" to uid,
                "data" to mapOf("groupId" to groupId, "invitationId" to invitationId),
                "createdAt" to now
            ))
            batch.commit().await()

            Result.success(Pair(groupId, groupData["name"] as? String ?: ""))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun declineInvitation(invitationId: String): Result<Unit> {
        return try {
            val uid = auth.currentUser?.uid ?: return Result.failure(Exception("User not authenticated"))
            val inviteDoc = firestore.collection("invitations").document(invitationId).get().await()
            if (!inviteDoc.exists()) return Result.failure(Exception("Invitation not found"))

            val inviteData = inviteDoc.data ?: return Result.failure(Exception("Invalid invitation"))
            if (inviteData["toUid"] != uid) return Result.failure(Exception("This invitation is not for you"))

            inviteDoc.reference.update(mapOf("status" to "declined")).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun leaveGroup(groupId: String): Result<Unit> {
        return try {
            val uid = auth.currentUser?.uid ?: return Result.failure(Exception("User not authenticated"))
            val groupDoc = firestore.collection("groups").document(groupId).get().await()
            if (!groupDoc.exists()) return Result.failure(Exception("Group not found"))

            val memberDoc = groupDoc.reference.collection("members").document(uid).get().await()
            if (!memberDoc.exists()) return Result.failure(Exception("You are not a member of this group"))

            val memberData = memberDoc.data
            if (memberData?.get("role") == "admin") {
                val activeMembers = groupDoc.reference.collection("members")
                    .whereEqualTo("status", "active").get().await()
                if (activeMembers.size() <= 1) {
                    return Result.failure(Exception("Admin cannot leave. Transfer admin role or delete the group."))
                }
            }

            val now = System.currentTimeMillis()
            val batch = firestore.batch()
            batch.update(memberDoc.reference, mapOf("status" to "left"))
            batch.update(groupDoc.reference, mapOf(
                "memberCount" to ((groupDoc.data?.get("memberCount") as? Number)?.toLong() ?: 1L) - 1,
                "updatedAt" to now
            ))
            batch.set(groupDoc.reference.collection("activities").document(), mapOf(
                "type" to "member_left",
                "description" to "Member left the group",
                "userId" to uid,
                "data" to mapOf("groupId" to groupId),
                "createdAt" to now
            ))
            batch.commit().await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getUserGroups(): Result<List<Group>> {
        return try {
            val uid = auth.currentUser?.uid ?: return Result.failure(Exception("User not authenticated"))

            val membersSnapshot = firestore.collectionGroup("members")
                .whereEqualTo("uid", uid)
                .whereEqualTo("status", "active")
                .get().await()

            if (membersSnapshot.isEmpty) return Result.success(emptyList())

            // Build list of (memberDoc, groupId) pairs, then batch-fetch group docs in parallel
            val memberGroupPairs = membersSnapshot.documents.mapNotNull { memberDoc ->
                val pathSegments = memberDoc.reference.path.split("/")
                val groupId = pathSegments.getOrNull(1) ?: return@mapNotNull null
                memberDoc to groupId
            }

            val groupDocs = coroutineScope {
                memberGroupPairs.map { (_, groupId) ->
                    async { firestore.collection("groups").document(groupId).get().await() }
                }.map { it.await() }
            }

            val groups = memberGroupPairs.zip(groupDocs).mapNotNull { (pair, groupDoc) ->
                val (memberDoc, _) = pair
                if (!groupDoc.exists()) return@mapNotNull null
                val data = groupDoc.data ?: return@mapNotNull null
                val memberData = memberDoc.data ?: emptyMap()
                Group(
                    groupId = groupDoc.id,
                    name = data["name"] as? String ?: "",
                    description = data["description"] as? String ?: "",
                    template = GroupTemplate.valueOf((data["template"] as? String ?: "casual").uppercase()),
                    currency = data["currency"] as? String ?: "INR",
                    createdBy = data["createdBy"] as? String ?: "",
                    inviteCode = data["inviteCode"] as? String ?: "",
                    memberCount = (data["memberCount"] as? Number)?.toInt() ?: 0,
                    totalExpenses = (data["totalExpenses"] as? Number)?.toDouble() ?: 0.0,
                    yourBalance = (memberData["balance"] as? Number)?.toDouble() ?: 0.0,
                    yourRole = memberData["role"] as? String ?: "member",
                    archived = data["archived"] as? Boolean ?: false
                )
            }
            Result.success(groups)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getGroupInfo(groupId: String): Result<GroupInfo> {
        return try {
            val uid = auth.currentUser?.uid ?: return Result.failure(Exception("User not authenticated"))
            val groupDoc = firestore.collection("groups").document(groupId).get().await()
            if (!groupDoc.exists()) return Result.failure(Exception("Group not found"))

            val memberDoc = groupDoc.reference.collection("members").document(uid).get().await()
            if (!memberDoc.exists()) return Result.failure(Exception("You are not a member of this group"))

            val data = groupDoc.data ?: return Result.failure(Exception("Invalid group data"))
            Result.success(
                GroupInfo(
                    groupId = groupId,
                    name = data["name"] as? String ?: "",
                    description = data["description"] as? String ?: "",
                    template = GroupTemplate.valueOf((data["template"] as? String ?: "casual").uppercase()),
                    currency = data["currency"] as? String ?: "INR",
                    inviteCode = data["inviteCode"] as? String ?: "",
                    createdBy = data["createdBy"] as? String ?: "",
                    memberCount = (data["memberCount"] as? Number)?.toInt() ?: 0,
                    totalExpenses = (data["totalExpenses"] as? Number)?.toDouble() ?: 0.0,
                    archived = data["archived"] as? Boolean ?: false
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getGroupActivities(groupId: String, pageSize: Int): Result<List<Activity>> {
        return try {
            val uid = auth.currentUser?.uid ?: return Result.failure(Exception("User not authenticated"))
            val groupRef = firestore.collection("groups").document(groupId)
            val memberDoc = groupRef.collection("members").document(uid).get().await()
            if (!memberDoc.exists()) return Result.failure(Exception("You are not a member of this group"))

            val snapshot = groupRef.collection("activities")
                .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(pageSize.toLong())
                .get().await()

            val activities = mutableListOf<Activity>()
            for (doc in snapshot.documents) {
                val data = doc.data ?: emptyMap()
                val userId = data["userId"] as? String ?: ""
                val userDoc = firestore.collection("users").document(userId).get().await()
                val userData = userDoc.data
                activities.add(
                    Activity(
                        activityId = doc.id,
                        type = data["type"] as? String ?: "unknown",
                        description = data["description"] as? String ?: "",
                        userId = userId,
                        userName = userData?.get("displayName") as? String ?: "Someone",
                        userPhotoURL = userData?.get("photoURL") as? String ?: "",
                        createdAt = (data["createdAt"] as? Long ?: 0L)
                    )
                )
            }
            Result.success(activities)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun archiveGroup(groupId: String): Result<Unit> {
        return try {
            val uid = auth.currentUser?.uid ?: return Result.failure(Exception("User not authenticated"))
            val groupRef = firestore.collection("groups").document(groupId)
            val memberDoc = groupRef.collection("members").document(uid).get().await()
            if (!memberDoc.exists()) return Result.failure(Exception("You are not a member of this group"))

            groupRef.update(mapOf("archived" to true, "updatedAt" to System.currentTimeMillis())).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun unarchiveGroup(groupId: String): Result<Unit> {
        return try {
            val uid = auth.currentUser?.uid ?: return Result.failure(Exception("User not authenticated"))
            val groupRef = firestore.collection("groups").document(groupId)
            val memberDoc = groupRef.collection("members").document(uid).get().await()
            if (!memberDoc.exists()) return Result.failure(Exception("You are not a member of this group"))

            groupRef.update(mapOf("archived" to false, "updatedAt" to System.currentTimeMillis())).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
