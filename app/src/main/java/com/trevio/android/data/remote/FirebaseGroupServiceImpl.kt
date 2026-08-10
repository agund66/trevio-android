package com.trevio.android.data.remote

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.trevio.android.domain.model.Activity
import com.trevio.android.domain.model.Group
import com.trevio.android.domain.model.GroupTemplate
import com.trevio.android.domain.model.PaginatedResult
import com.trevio.android.domain.repository.GroupInfo
import com.trevio.android.domain.repository.GroupService
import com.trevio.android.domain.model.SplitEntry
import com.trevio.android.util.Calculations
import com.trevio.android.util.DateUtils
import com.trevio.android.util.ErrorMessages
import com.trevio.android.util.friendlyNetworkMessage
import com.trevio.android.util.Logger
import com.trevio.android.util.MemberRole
import com.trevio.android.util.MemberStatus
import com.trevio.android.util.toStorageString
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
        memberUids: List<String>,
        monthlyBudget: Double?
    ): Result<Pair<String, String>> {
        return try {
            val uid = auth.currentUser?.uid ?: return Result.failure(Exception(ErrorMessages.USER_NOT_AUTHENTICATED))
            if (name.isBlank()) return Result.failure(Exception(ErrorMessages.GROUP_NAME_REQUIRED))

            val userDoc = firestore.collection("users").document(uid).get().await()
            val userCurrency = userDoc.getString("defaultCurrency") ?: "INR"

            val now = System.currentTimeMillis()
            val inviteCode = Calculations.generateInviteCode()
            val groupRef = firestore.collection("groups").document()
            val groupId = groupRef.id

            val groupData = mutableMapOf(
                "name" to name.trim(),
                "description" to description.trim(),
                "template" to template.toStorageString(),
                "currency" to userCurrency,
                "createdBy" to uid,
                "inviteCode" to inviteCode,
                "memberCount" to 1,
                "totalExpenses" to 0.0,
                "createdAt" to now,
                "updatedAt" to now
            )
            if (template == GroupTemplate.HOUSEHOLD && monthlyBudget != null && monthlyBudget > 0) {
                groupData["monthlyBudget"] = monthlyBudget
            }

            val batch = firestore.batch()
            batch.set(groupRef, groupData)
            batch.set(groupRef.collection("members").document(uid), mapOf(
                "uid" to uid,
                "role" to MemberRole.ADMIN,
                "joinedAt" to now,
                "balance" to 0.0,
                "status" to MemberStatus.ACTIVE
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
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    override suspend fun joinGroupViaCode(inviteCode: String): Result<Pair<String, String>> {
        return try {
            val uid = auth.currentUser?.uid ?: return Result.failure(Exception(ErrorMessages.USER_NOT_AUTHENTICATED))
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
            if (memberDoc.exists() && memberDoc.data?.get("status") == MemberStatus.ACTIVE && memberDoc.data?.get("isOffline") != true) {
                return Result.failure(Exception("You are already a member of this group"))
            }

            val now = System.currentTimeMillis()
            val batch = firestore.batch()

            if (memberDoc.exists() && memberDoc.data?.get("status") == "pending") {
                batch.update(memberDoc.reference, mapOf("status" to MemberStatus.ACTIVE, "joinedAt" to now))
            } else {
                batch.set(groupDoc.reference.collection("members").document(uid), mapOf(
                    "uid" to uid,
                    "role" to "member",
                    "joinedAt" to now,
                    "balance" to 0.0,
                    "status" to MemberStatus.ACTIVE
                ))
                batch.update(groupDoc.reference, mapOf(
                    "memberCount" to FieldValue.increment(1),
                    "updatedAt" to now
                ))
            }
            batch.set(groupDoc.reference.collection("activities").document(), mapOf(
                "type" to "member_joined",
                "description" to "Member joined via invite code",
                "userId" to uid,
                "data" to mapOf("groupId" to groupId),
                "createdAt" to now
            ))
            batch.commit().await()

            Result.success(Pair(groupId, groupData["name"] as? String ?: ""))
        } catch (e: Exception) {
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    override suspend fun sendGroupInvitation(groupId: String, username: String): Result<Unit> {
        return try {
            val uid = auth.currentUser?.uid ?: return Result.failure(Exception(ErrorMessages.USER_NOT_AUTHENTICATED))
            if (groupId.isBlank() || username.isBlank()) return Result.failure(Exception("Group ID and username are required"))

            val normalized = username.lowercase().replace(Regex("[^a-z0-9._]"), "")
            val usernameDoc = firestore.collection("usernames").document(normalized).get().await()
            if (!usernameDoc.exists()) return Result.failure(Exception(ErrorMessages.USER_NOT_FOUND))

            val toUid = usernameDoc.data?.get("uid") as? String ?: return Result.failure(Exception("Invalid user"))
            if (toUid == uid) return Result.failure(Exception("You cannot invite yourself"))
            val groupDoc = firestore.collection("groups").document(groupId).get().await()
            if (!groupDoc.exists()) return Result.failure(Exception(ErrorMessages.GROUP_NOT_FOUND))

            val groupData = groupDoc.data ?: return Result.failure(Exception("Invalid group data"))
            val existingMember = groupDoc.reference.collection("members").document(toUid).get().await()
            if (existingMember.exists()) return Result.failure(Exception("User is already a member of this group"))

            sendInvitationInternal(uid, toUid, groupId, groupData["name"] as? String ?: "", groupData["inviteCode"] as? String ?: "")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
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
            groupRef.update(mapOf("memberCount" to FieldValue.increment(1), "updatedAt" to now)).await()
        }

        try {
            firestore.collection("users").document(toUid).collection("notifications").document()
                .set(mapOf(
                    "type" to "invitation",
                    "title" to "Group Invitation",
                    "body" to "$invitedByName invited you to join \"$groupName\"",
                    "data" to mapOf("groupId" to groupId, "groupName" to groupName, "invitationId" to inviteRef.id, "type" to "invitation"),
                    "read" to false,
                    "createdAt" to now
                )).await()
        } catch (notifError: Exception) {
            Logger.w("FirebaseGroupService", "Failed to send invitation notification", notifError)
        }
    }

    override suspend fun acceptInvitation(invitationId: String): Result<Pair<String, String>> {
        return try {
            val uid = auth.currentUser?.uid ?: return Result.failure(Exception(ErrorMessages.USER_NOT_AUTHENTICATED))
            if (invitationId.isBlank()) return Result.failure(Exception("Invitation ID is required"))

            val inviteDoc = firestore.collection("invitations").document(invitationId).get().await()
            if (!inviteDoc.exists()) return Result.failure(Exception("Invitation not found"))

            val inviteData = inviteDoc.data ?: return Result.failure(Exception("Invalid invitation"))
            if (inviteData["toUid"] != uid) return Result.failure(Exception("This invitation is not for you"))
            if (inviteData["status"] != "pending") return Result.failure(Exception("Invitation is no longer pending"))

            val groupId = inviteData["groupId"] as? String ?: return Result.failure(Exception("Invalid group ID"))
            val groupDoc = firestore.collection("groups").document(groupId).get().await()
            if (!groupDoc.exists()) return Result.failure(Exception(ErrorMessages.GROUP_NOT_FOUND))

            val groupData = groupDoc.data ?: return Result.failure(Exception("Invalid group data"))
            val now = System.currentTimeMillis()

            val existingMemberDoc = groupDoc.reference.collection("members").document(uid).get().await()
            val batch = firestore.batch()
            batch.update(inviteDoc.reference, mapOf("status" to "accepted"))

            if (existingMemberDoc.exists() && existingMemberDoc.data?.get("status") == "pending") {
                batch.update(groupDoc.reference.collection("members").document(uid), mapOf("status" to MemberStatus.ACTIVE, "joinedAt" to now))
            } else {
                batch.set(groupDoc.reference.collection("members").document(uid), mapOf(
                    "uid" to uid, "role" to "member", "joinedAt" to now, "balance" to 0.0, "status" to MemberStatus.ACTIVE
                ))
                batch.update(groupDoc.reference, mapOf(
                    "memberCount" to FieldValue.increment(1),
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
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    override suspend fun declineInvitation(invitationId: String): Result<Unit> {
        return try {
            val uid = auth.currentUser?.uid ?: return Result.failure(Exception(ErrorMessages.USER_NOT_AUTHENTICATED))
            val inviteDoc = firestore.collection("invitations").document(invitationId).get().await()
            if (!inviteDoc.exists()) return Result.failure(Exception("Invitation not found"))

            val inviteData = inviteDoc.data ?: return Result.failure(Exception("Invalid invitation"))
            if (inviteData["toUid"] != uid) return Result.failure(Exception("This invitation is not for you"))
            if (inviteData["status"] != "pending") return Result.failure(Exception("Invitation is no longer pending"))

            val groupId = inviteData["groupId"] as? String ?: ""
            val groupRef = firestore.collection("groups").document(groupId)
            val memberDoc = groupRef.collection("members").document(uid).get().await()

            val batch = firestore.batch()
            batch.update(inviteDoc.reference, mapOf("status" to "declined"))

            if (memberDoc.exists() && memberDoc.data?.get("status") == "pending") {
                batch.delete(memberDoc.reference)
                batch.update(groupRef, mapOf("memberCount" to FieldValue.increment(-1), "updatedAt" to System.currentTimeMillis()))
            }

            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    override suspend fun leaveGroup(groupId: String): Result<Unit> {
        return try {
            val uid = auth.currentUser?.uid ?: return Result.failure(Exception(ErrorMessages.USER_NOT_AUTHENTICATED))
            val groupDoc = firestore.collection("groups").document(groupId).get().await()
            if (!groupDoc.exists()) return Result.failure(Exception(ErrorMessages.GROUP_NOT_FOUND))

            val memberDoc = groupDoc.reference.collection("members").document(uid).get().await()
            if (!memberDoc.exists()) return Result.failure(Exception("You are not a member of this group"))

            val memberData = memberDoc.data
            if (memberData?.get("role") == MemberRole.ADMIN) {
                val activeMembers = groupDoc.reference.collection("members")
                    .whereEqualTo("status", MemberStatus.ACTIVE).get().await()
                if (activeMembers.size() <= 1) {
                    return Result.failure(Exception("Admin cannot leave. Transfer admin role or delete the group."))
                }
            }

            val now = System.currentTimeMillis()
            val batch = firestore.batch()
            batch.update(memberDoc.reference, mapOf("status" to "left"))
            batch.update(groupDoc.reference, mapOf(
                "memberCount" to FieldValue.increment(-1),
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
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    override suspend fun getUserGroups(): Result<List<Group>> {
        return try {
            val uid = auth.currentUser?.uid ?: return Result.failure(Exception(ErrorMessages.USER_NOT_AUTHENTICATED))

            val membersSnapshot = firestore.collectionGroup("members")
                .whereEqualTo("uid", uid)
                .whereEqualTo("status", MemberStatus.ACTIVE)
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
                    archived = data["archived"] as? Boolean ?: false,
                    monthlyBudget = (data["monthlyBudget"] as? Number)?.toDouble(),
                    budgetCategories = (data["budgetCategories"] as? Map<String, Any>)?.mapValues { (_, v) ->
                        (v as? Number)?.toDouble() ?: 0.0
                    }
                )
            }
            Result.success(groups)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    override suspend fun getGroupInfo(groupId: String): Result<GroupInfo> {
        return try {
            val uid = auth.currentUser?.uid ?: return Result.failure(Exception(ErrorMessages.USER_NOT_AUTHENTICATED))
            val groupDoc = firestore.collection("groups").document(groupId).get().await()
            if (!groupDoc.exists()) return Result.failure(Exception(ErrorMessages.GROUP_NOT_FOUND))

            val memberDoc = groupDoc.reference.collection("members").document(uid).get().await()
            if (!memberDoc.exists()) return Result.failure(Exception("You are not a member of this group"))

            val data = groupDoc.data ?: return Result.failure(Exception("Invalid group data"))
            @Suppress("UNCHECKED_CAST")
            val budgetCategoriesRaw = data["budgetCategories"] as? Map<String, Any>
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
                    archived = data["archived"] as? Boolean ?: false,
                    monthlyBudget = (data["monthlyBudget"] as? Number)?.toDouble(),
                    budgetCategories = budgetCategoriesRaw?.mapValues { (_, v) ->
                        (v as? Number)?.toDouble() ?: 0.0
                    }
                )
            )
        } catch (e: Exception) {
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    override suspend fun getGroupActivities(groupId: String, pageSize: Int, lastActivityId: String?): Result<PaginatedResult<Activity>> {
        return try {
            val uid = auth.currentUser?.uid ?: return Result.failure(Exception(ErrorMessages.USER_NOT_AUTHENTICATED))
            val groupRef = firestore.collection("groups").document(groupId)
            val memberDoc = groupRef.collection("members").document(uid).get().await()
            if (!memberDoc.exists()) return Result.failure(Exception("You are not a member of this group"))

            var query = groupRef.collection("activities")
                .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(pageSize.toLong())

            if (lastActivityId != null) {
                val lastDoc = groupRef.collection("activities").document(lastActivityId).get().await()
                if (lastDoc.exists()) {
                    query = groupRef.collection("activities")
                        .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                        .startAfter(lastDoc)
                        .limit(pageSize.toLong())
                }
            }

            val snapshot = query.get().await()

            val activities = mutableListOf<Activity>()

            val uniqueUserIds = snapshot.documents.mapNotNull { (it.data ?: emptyMap())["userId"] as? String }.filter { it.isNotEmpty() }.distinct()
            val userDocs = coroutineScope {
                uniqueUserIds.associateWith { uid ->
                    async { firestore.collection("users").document(uid).get().await() }
                }.mapValues { it.value.await() }
            }
            val userMap = mutableMapOf<String, Map<String, Any>?>()
            userDocs.forEach { (uid, doc) -> userMap[uid] = doc.data }

            for (doc in snapshot.documents) {
                val data = doc.data ?: emptyMap()
                val userId = data["userId"] as? String ?: ""
                val userData = userMap[userId]
                activities.add(
                    Activity(
                        activityId = doc.id,
                        type = data["type"] as? String ?: "unknown",
                        description = data["description"] as? String ?: "",
                        userId = userId,
                        userName = userData?.get("displayName") as? String ?: "Someone",
                        userPhotoURL = userData?.get("photoURL") as? String ?: "",
                        createdAt = DateUtils.toMillis(data["createdAt"]) ?: 0
                    )
                )
            }
            Result.success(PaginatedResult(
                items = activities,
                hasMore = snapshot.size() == pageSize,
                lastId = if (snapshot.size() > 0) snapshot.documents.last().id else null
            ))
        } catch (e: Exception) {
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    override suspend fun archiveGroup(groupId: String): Result<Unit> {
        return try {
            val uid = auth.currentUser?.uid ?: return Result.failure(Exception(ErrorMessages.USER_NOT_AUTHENTICATED))
            val groupRef = firestore.collection("groups").document(groupId)
            val memberDoc = groupRef.collection("members").document(uid).get().await()
            if (!memberDoc.exists()) return Result.failure(Exception("You are not a member of this group"))
            if (memberDoc.data?.get("role") != MemberRole.ADMIN) return Result.failure(Exception("Only group admin can archive the group"))

            groupRef.update(mapOf("archived" to true, "updatedAt" to System.currentTimeMillis())).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    override suspend fun unarchiveGroup(groupId: String): Result<Unit> {
        return try {
            val uid = auth.currentUser?.uid ?: return Result.failure(Exception(ErrorMessages.USER_NOT_AUTHENTICATED))
            val groupRef = firestore.collection("groups").document(groupId)
            val memberDoc = groupRef.collection("members").document(uid).get().await()
            if (!memberDoc.exists()) return Result.failure(Exception("You are not a member of this group"))
            if (memberDoc.data?.get("role") != MemberRole.ADMIN) return Result.failure(Exception("Only group admin can unarchive the group"))

            groupRef.update(mapOf("archived" to false, "updatedAt" to System.currentTimeMillis())).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    override suspend fun deleteGroup(groupId: String): Result<Unit> {
        return try {
            val uid = auth.currentUser?.uid ?: return Result.failure(Exception(ErrorMessages.USER_NOT_AUTHENTICATED))
            val groupRef = firestore.collection("groups").document(groupId)
            val groupDoc = groupRef.get().await()
            if (!groupDoc.exists()) return Result.failure(Exception(ErrorMessages.GROUP_NOT_FOUND))

            val memberDoc = groupRef.collection("members").document(uid).get().await()
            if (!memberDoc.exists()) return Result.failure(Exception("You are not a member of this group"))
            if (memberDoc.data?.get("role") != MemberRole.ADMIN) return Result.failure(Exception("Only group admin can delete the group"))

            val membersSnapshot = groupRef.collection("members").get().await()
            val activeCount = membersSnapshot.documents.count { it.data?.get("status") == MemberStatus.ACTIVE }
            if (activeCount > 1) return Result.failure(Exception("Cannot delete group with other active members. Remove all members first."))

            val expensesSnapshot = groupRef.collection("expenses").get().await()
            val settlementsSnapshot = groupRef.collection("settlements").get().await()
            val activitiesSnapshot = groupRef.collection("activities").get().await()

            val allRefs = membersSnapshot.documents.map { it.reference } +
                expensesSnapshot.documents.map { it.reference } +
                settlementsSnapshot.documents.map { it.reference } +
                activitiesSnapshot.documents.map { it.reference } +
                listOf(groupRef)

            val batchSize = 400
            for (i in allRefs.indices step batchSize) {
                val chunk = allRefs.subList(i, minOf(i + batchSize, allRefs.size))
                val batch = firestore.batch()
                for (ref in chunk) batch.delete(ref)
                batch.commit().await()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    override suspend fun updateGroup(groupId: String, name: String, description: String): Result<Unit> {
        return try {
            val uid = auth.currentUser?.uid ?: return Result.failure(Exception(ErrorMessages.USER_NOT_AUTHENTICATED))
            if (name.isBlank()) return Result.failure(Exception(ErrorMessages.GROUP_NAME_REQUIRED))

            val groupRef = firestore.collection("groups").document(groupId)
            val memberDoc = groupRef.collection("members").document(uid).get().await()
            if (!memberDoc.exists()) return Result.failure(Exception("You are not a member of this group"))
            if (memberDoc.data?.get("role") != MemberRole.ADMIN) return Result.failure(Exception("Only group admin can update group settings"))

            groupRef.update(mapOf(
                "name" to name.trim(),
                "description" to description.trim(),
                "updatedAt" to System.currentTimeMillis()
            )).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    override suspend fun updateGroupBudget(
        groupId: String,
        monthlyBudget: Double?,
        budgetCategories: Map<String, Double>?
    ): Result<Unit> {
        return try {
            val uid = auth.currentUser?.uid ?: return Result.failure(Exception(ErrorMessages.USER_NOT_AUTHENTICATED))
            val groupRef = firestore.collection("groups").document(groupId)
            val memberDoc = groupRef.collection("members").document(uid).get().await()
            if (!memberDoc.exists()) return Result.failure(Exception("You are not a member of this group"))
            if (memberDoc.data?.get("role") != MemberRole.ADMIN) return Result.failure(Exception("Only group admin can update budget settings"))

            val updateData = mutableMapOf<String, Any>("updatedAt" to System.currentTimeMillis())
            if (monthlyBudget != null && monthlyBudget > 0) {
                updateData["monthlyBudget"] = monthlyBudget
            } else {
                updateData["monthlyBudget"] = com.google.firebase.firestore.FieldValue.delete()
            }
            if (budgetCategories != null && budgetCategories.isNotEmpty()) {
                updateData["budgetCategories"] = budgetCategories
            } else {
                updateData["budgetCategories"] = com.google.firebase.firestore.FieldValue.delete()
            }

            groupRef.update(updateData).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    override suspend fun transferAdminRole(groupId: String, newAdminUid: String): Result<Unit> {
        return try {
            val uid = auth.currentUser?.uid ?: return Result.failure(Exception(ErrorMessages.USER_NOT_AUTHENTICATED))
            if (newAdminUid == uid) return Result.failure(Exception("You are already the admin"))

            val groupRef = firestore.collection("groups").document(groupId)
            val currentMemberDoc = groupRef.collection("members").document(uid).get().await()
            if (!currentMemberDoc.exists()) return Result.failure(Exception("You are not a member of this group"))
            if (currentMemberDoc.data?.get("role") != MemberRole.ADMIN) return Result.failure(Exception("Only group admin can transfer admin role"))

            val targetMemberDoc = groupRef.collection("members").document(newAdminUid).get().await()
            if (!targetMemberDoc.exists()) return Result.failure(Exception("Target user is not a member of this group"))
            if (targetMemberDoc.data?.get("status") != MemberStatus.ACTIVE) return Result.failure(Exception("Target user is not an active member"))

            val now = System.currentTimeMillis()
            val batch = firestore.batch()
            batch.update(groupRef.collection("members").document(uid), mapOf("role" to "member"))
            batch.update(groupRef.collection("members").document(newAdminUid), mapOf("role" to MemberRole.ADMIN))
            batch.set(groupRef.collection("activities").document(), mapOf(
                "type" to "admin_transferred",
                "description" to "Admin role transferred",
                "userId" to uid,
                "data" to mapOf("newAdminUid" to newAdminUid),
                "createdAt" to now
            ))
            batch.commit().await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    override suspend fun addOfflineMember(groupId: String, displayName: String): Result<String> {
        return try {
            val uid = auth.currentUser?.uid ?: return Result.failure(Exception(ErrorMessages.USER_NOT_AUTHENTICATED))
            if (displayName.isBlank()) return Result.failure(Exception("Name is required"))

            val groupRef = firestore.collection("groups").document(groupId)
            val groupDoc = groupRef.get().await()
            if (!groupDoc.exists()) return Result.failure(Exception(ErrorMessages.GROUP_NOT_FOUND))

            val callerMemberDoc = groupRef.collection("members").document(uid).get().await()
            if (!callerMemberDoc.exists() || callerMemberDoc.data?.get("status") != MemberStatus.ACTIVE) {
                return Result.failure(Exception("You are not a member of this group"))
            }

            val now = System.currentTimeMillis()
            val memberRef = groupRef.collection("members").document()
            val batch = firestore.batch()
            batch.set(memberRef, mapOf(
                "uid" to "",
                "displayName" to displayName.trim(),
                "role" to "member",
                "joinedAt" to now,
                "balance" to 0.0,
                "status" to MemberStatus.ACTIVE,
                "isOffline" to true,
                "addedBy" to uid
            ))
            batch.update(groupRef, mapOf(
                "memberCount" to FieldValue.increment(1),
                "updatedAt" to now
            ))
            batch.set(groupRef.collection("activities").document(), mapOf(
                "type" to "member_added",
                "description" to "Added offline member \"$displayName\"",
                "userId" to uid,
                "data" to mapOf("groupId" to groupId, "memberName" to displayName.trim()),
                "createdAt" to now
            ))
            batch.commit().await()

            Result.success(memberRef.id)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    override suspend fun claimOfflineMember(groupId: String, memberDocId: String): Result<Unit> {
        return try {
            val uid = auth.currentUser?.uid ?: return Result.failure(Exception(ErrorMessages.USER_NOT_AUTHENTICATED))

            val groupRef = firestore.collection("groups").document(groupId)
            val memberDoc = groupRef.collection("members").document(memberDocId).get().await()
            if (!memberDoc.exists()) return Result.failure(Exception(ErrorMessages.MEMBER_NOT_FOUND))
            if (memberDoc.data?.get("isOffline") != true) return Result.failure(Exception("This member is not an offline profile"))

            val existingMemberDoc = groupRef.collection("members").document(uid).get().await()

            val memberData = memberDoc.data ?: return Result.failure(Exception(ErrorMessages.MEMBER_NOT_FOUND))
            val now = System.currentTimeMillis()
            val batch = firestore.batch()

            if (existingMemberDoc.exists()) {
                // User already has a member doc (e.g. joined via invite code)
                // Keep existing doc, just delete the offline profile doc
                batch.delete(memberDoc.reference)
                batch.update(groupRef, mapOf("memberCount" to FieldValue.increment(-1)))
            } else {
                // No existing doc — create one with offline member's data
                val claimedData = memberData.toMutableMap()
                claimedData["uid"] = uid
                claimedData["isOffline"] = false
                claimedData["claimedAt"] = now
                claimedData["claimedBy"] = uid
                batch.set(groupRef.collection("members").document(uid), claimedData)
                batch.delete(memberDoc.reference)
            }

            batch.set(groupRef.collection("activities").document(), mapOf(
                "type" to "member_claimed",
                "description" to "Member claimed offline profile",
                "userId" to uid,
                "data" to mapOf("groupId" to groupId, "memberDocId" to memberDocId),
                "createdAt" to now
            ))

            batch.commit().await()

            migrateMemberReferences(groupId, memberDocId, uid)
            recalculateBalances(groupId)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    override suspend fun linkOfflineMember(groupId: String, memberDocId: String, realUid: String): Result<Unit> {
        return try {
            val uid = auth.currentUser?.uid ?: return Result.failure(Exception(ErrorMessages.USER_NOT_AUTHENTICATED))

            val groupRef = firestore.collection("groups").document(groupId)
            val adminDoc = groupRef.collection("members").document(uid).get().await()
            if (adminDoc.data?.get("role") != MemberRole.ADMIN) return Result.failure(Exception("Only admins can link members"))

            val memberDoc = groupRef.collection("members").document(memberDocId).get().await()
            if (!memberDoc.exists()) return Result.failure(Exception(ErrorMessages.MEMBER_NOT_FOUND))
            if (memberDoc.data?.get("isOffline") != true) return Result.failure(Exception("This member is not an offline profile"))

            val existingMemberDoc = groupRef.collection("members").document(realUid).get().await()

            val memberData = memberDoc.data ?: return Result.failure(Exception(ErrorMessages.MEMBER_NOT_FOUND))
            val now = System.currentTimeMillis()
            val batch = firestore.batch()

            if (existingMemberDoc.exists()) {
                // Target user already has a member doc (e.g. joined via invite code)
                // Keep existing doc, just delete the offline profile doc
                batch.delete(memberDoc.reference)
                batch.update(groupRef, mapOf("memberCount" to FieldValue.increment(-1)))
            } else {
                // No existing doc — create one with offline member's data
                val linkedData = memberData.toMutableMap()
                linkedData["uid"] = realUid
                linkedData["isOffline"] = false
                linkedData["claimedAt"] = now
                linkedData["claimedBy"] = uid
                batch.set(groupRef.collection("members").document(realUid), linkedData)
                batch.delete(memberDoc.reference)
            }

            batch.set(groupRef.collection("activities").document(), mapOf(
                "type" to "member_linked",
                "description" to "Admin linked offline profile to user",
                "userId" to uid,
                "data" to mapOf("groupId" to groupId, "memberDocId" to memberDocId, "linkedUid" to realUid),
                "createdAt" to now
            ))

            batch.commit().await()

            migrateMemberReferences(groupId, memberDocId, realUid)
            recalculateBalances(groupId)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    override suspend fun removeMember(groupId: String, memberUid: String): Result<Unit> {
        return try {
            val callerUid = auth.currentUser?.uid ?: return Result.failure(Exception(ErrorMessages.USER_NOT_AUTHENTICATED))
            if (callerUid == memberUid) return Result.failure(Exception("Use leave group to remove yourself"))

            val groupRef = firestore.collection("groups").document(groupId)
            val groupDoc = groupRef.get().await()
            if (!groupDoc.exists()) return Result.failure(Exception(ErrorMessages.GROUP_NOT_FOUND))

            val callerDoc = groupRef.collection("members").document(callerUid).get().await()
            if (!callerDoc.exists() || callerDoc.getString("role") != MemberRole.ADMIN) {
                return Result.failure(Exception("Only admins can remove members"))
            }

            val memberDoc = groupRef.collection("members").document(memberUid).get().await()
            if (!memberDoc.exists()) return Result.failure(Exception(ErrorMessages.MEMBER_NOT_FOUND))
            val memberData = memberDoc.data ?: return Result.failure(Exception(ErrorMessages.MEMBER_NOT_FOUND))
            if (memberData["role"] == MemberRole.ADMIN) return Result.failure(Exception("Cannot remove another admin"))

            // Check if member has any expenses
            val expensesQuery = groupRef.collection("expenses")
                .whereEqualTo("paidBy", memberUid)
                .limit(1)
                .get()
                .await()
            val hasExpenses = !expensesQuery.isEmpty

            val now = System.currentTimeMillis()
            val batch = firestore.batch()

            if (hasExpenses) {
                // Convert to offline member to preserve transaction history
                batch.update(
                    groupRef.collection("members").document(memberUid),
                    mapOf(
                        "uid" to "",
                        "isOffline" to true,
                        "status" to "removed",
                        "updatedAt" to now
                    )
                )
            } else {
                // No expenses, safe to fully remove
                batch.delete(groupRef.collection("members").document(memberUid))
                batch.update(groupRef, mapOf(
                    "memberCount" to FieldValue.increment(-1),
                    "updatedAt" to now
                ))
            }

            batch.set(
                groupRef.collection("activities").document(),
                mapOf(
                    "type" to "member_removed",
                    "description" to "Removed member \"${memberData["displayName"]}\"",
                    "userId" to callerUid,
                    "data" to mapOf(
                        "removedUid" to memberUid,
                        "memberName" to (memberData["displayName"] ?: ""),
                        "convertedToOffline" to hasExpenses
                    ),
                    "createdAt" to now
                )
            )

            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    private suspend fun migrateMemberReferences(groupId: String, oldId: String, newId: String) {
        val groupRef = firestore.collection("groups").document(groupId)
        val ops = mutableListOf<Pair<com.google.firebase.firestore.DocumentReference, Map<String, Any>>>()

        val expenses = groupRef.collection("expenses").get().await()
        for (expenseDoc in expenses.documents) {
            val data = expenseDoc.data ?: continue
            val updates = mutableMapOf<String, Any>()
            var changed = false

            if (data["paidBy"] == oldId) {
                updates["paidBy"] = newId
                changed = true
            }

            @Suppress("UNCHECKED_CAST")
            val splits = data["splits"] as? Map<String, Any>
            if (splits != null && splits.containsKey(oldId)) {
                val newSplits = splits.toMutableMap()
                val oldSplit = newSplits.remove(oldId)
                if (oldSplit != null) {
                    newSplits[newId] = oldSplit
                }
                updates["splits"] = newSplits
                changed = true
            }

            if (changed) ops.add(expenseDoc.reference to updates)
        }

        val settlements = groupRef.collection("settlements").get().await()
        for (settlementDoc in settlements.documents) {
            val data = settlementDoc.data ?: continue
            val updates = mutableMapOf<String, Any>()
            var changed = false

            if (data["fromUid"] == oldId) {
                updates["fromUid"] = newId
                changed = true
            }
            if (data["toUid"] == oldId) {
                updates["toUid"] = newId
                changed = true
            }

            if (changed) ops.add(settlementDoc.reference to updates)
        }

        val batchSize = 400
        for (i in ops.indices step batchSize) {
            val chunk = ops.subList(i, minOf(i + batchSize, ops.size))
            val batch = firestore.batch()
            for ((ref, updates) in chunk) batch.update(ref, updates)
            batch.commit().await()
        }
    }

    private suspend fun recalculateBalances(groupId: String) {
        val groupRef = firestore.collection("groups").document(groupId)

        val expensesSnapshot = groupRef.collection("expenses").get().await()
        val settlementsSnapshot = groupRef.collection("settlements").get().await()
        val membersSnapshot = groupRef.collection("members").whereEqualTo("status", MemberStatus.ACTIVE).get().await()

        val memberUids = membersSnapshot.documents.map { it.id }

        val expenses = expensesSnapshot.documents.map { doc ->
            val data = doc.data ?: emptyMap()
            @Suppress("UNCHECKED_CAST")
            val splitsRaw = data["splits"] as? Map<String, Map<String, Any>> ?: emptyMap()
            Calculations.ExpenseBalanceData(
                paidBy = data["paidBy"] as? String ?: "",
                splits = splitsRaw.mapValues { (_, v) ->
                    SplitEntry(
                        amount = (v["amount"] as? Number)?.toDouble() ?: 0.0,
                        shareValue = (v["shareValue"] as? Number)?.toDouble() ?: 0.0
                    )
                },
                amount = (data["amount"] as? Number)?.toDouble() ?: 0.0,
                exchangeRateToBase = (data["exchangeRateToBase"] as? Number)?.toDouble() ?: 1.0
            )
        }

        val settlements = settlementsSnapshot.documents.map { doc ->
            val data = doc.data ?: emptyMap()
            Triple(
                data["fromUid"] as? String ?: "",
                data["toUid"] as? String ?: "",
                (data["amount"] as? Number)?.toDouble() ?: 0.0
            )
        }

        val balances = Calculations.calculateBalances(expenses, settlements, memberUids)

        val balanceEntries = balances.entries.toList()
        val batchSize = 400
        for (i in balanceEntries.indices step batchSize) {
            val chunk = balanceEntries.subList(i, minOf(i + batchSize, balanceEntries.size))
            val batch = firestore.batch()
            for ((memberUid, balance) in chunk) {
                val roundedBalance = kotlin.math.round(balance * 100) / 100
                batch.update(groupRef.collection("members").document(memberUid), mapOf("balance" to roundedBalance))
            }
            batch.commit().await()
        }
    }
}
