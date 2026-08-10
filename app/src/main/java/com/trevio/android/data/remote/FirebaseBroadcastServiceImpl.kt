package com.trevio.android.data.remote

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.trevio.android.domain.model.BroadcastMessage
import com.trevio.android.domain.model.BroadcastPriority
import com.trevio.android.domain.model.BroadcastRead
import com.trevio.android.domain.model.BroadcastTargetType
import com.trevio.android.domain.repository.BroadcastService
import com.trevio.android.util.DateUtils
import com.trevio.android.util.ErrorMessages
import com.trevio.android.util.friendlyNetworkMessage
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseBroadcastServiceImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : BroadcastService {

    private suspend fun requireSuperadmin(): Result<Unit> {
        return try {
            val uid = auth.currentUser?.uid ?: return Result.failure(Exception(ErrorMessages.USER_NOT_AUTHENTICATED))
            val currentUserDoc = firestore.collection("users").document(uid).get().await()
            val currentRole = currentUserDoc.data?.get("role") as? String ?: "user"
            if (currentRole != "superadmin") return Result.failure(Exception(ErrorMessages.ACCESS_DENIED_SUPERADMIN))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    override suspend fun createBroadcast(
        title: String,
        htmlContent: String,
        priority: BroadcastPriority,
        targetType: BroadcastTargetType,
        targetUids: List<String>,
        startAt: Long,
        endAt: Long?
    ): Result<String> {
        return try {
            requireSuperadmin().onFailure { return Result.failure(it) }
            val currentUid = auth.currentUser?.uid ?: return Result.failure(Exception("Not authenticated"))
            val currentUserDoc = firestore.collection("users").document(currentUid).get().await()
            val createdByName = currentUserDoc.data?.get("displayName") as? String ?: ""

            val data = mapOf(
                "title" to title,
                "htmlContent" to htmlContent,
                "priority" to priority.name.lowercase(),
                "targetType" to targetType.name.lowercase(),
                "targetUids" to targetUids,
                "startAt" to startAt,
                "endAt" to endAt,
                "active" to true,
                "createdBy" to currentUid,
                "createdByName" to createdByName,
                "createdAt" to System.currentTimeMillis(),
                "stoppedAt" to null
            )

            val ref = firestore.collection("broadcasts").add(data).await()
            Result.success(ref.id)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    override suspend fun getAllBroadcasts(): Result<List<BroadcastMessage>> {
        return try {
            requireSuperadmin().onFailure { return Result.failure(it) }
            val snapshot = firestore.collection("broadcasts")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(100)
                .get().await()

            val broadcasts = snapshot.documents.mapNotNull { doc ->
                val data = doc.data ?: return@mapNotNull null
                BroadcastMessage(
                    id = doc.id,
                    title = data["title"] as? String ?: "",
                    htmlContent = data["htmlContent"] as? String ?: "",
                    priority = parsePriority(data["priority"] as? String ?: "info"),
                    targetType = parseTargetType(data["targetType"] as? String ?: "all"),
                    targetUids = (data["targetUids"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                    startAt = DateUtils.toMillis(data["startAt"]) ?: 0L,
                    endAt = data["endAt"] as? Long,
                    active = data["active"] as? Boolean ?: true,
                    createdBy = data["createdBy"] as? String ?: "",
                    createdByName = data["createdByName"] as? String ?: "",
                    createdAt = DateUtils.toMillis(data["createdAt"]) ?: 0L,
                    stoppedAt = data["stoppedAt"] as? Long
                )
            }
            Result.success(broadcasts)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    override suspend fun stopBroadcast(id: String): Result<Unit> {
        return try {
            requireSuperadmin().onFailure { return Result.failure(it) }
            firestore.collection("broadcasts").document(id)
                .update(mapOf("active" to false, "stoppedAt" to System.currentTimeMillis()))
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    override suspend fun getReadCount(broadcastId: String): Result<Int> {
        return try {
            val snapshot = firestore.collection("broadcasts")
                .document(broadcastId)
                .collection("reads")
                .limit(500)
                .get().await()
            Result.success(snapshot.size())
        } catch (e: Exception) {
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    override suspend fun getBroadcastReads(broadcastId: String): Result<List<BroadcastRead>> {
        return try {
            val snapshot = firestore.collection("broadcasts")
                .document(broadcastId)
                .collection("reads")
                .limit(500)
                .get().await()
            val reads = snapshot.documents.map { doc ->
                val data = doc.data ?: emptyMap()
                BroadcastRead(
                    uid = (data["uid"] as? String) ?: doc.id,
                    readAt = DateUtils.toMillis(data["readAt"]) ?: 0L
                )
            }
            Result.success(reads)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    override suspend fun getActiveBroadcastsForUser(
        uid: String,
        isBlocked: Boolean
    ): Result<List<BroadcastMessage>> {
        return try {
            val now = System.currentTimeMillis()
            val snapshot = firestore.collection("broadcasts")
                .whereEqualTo("active", true)
                .whereLessThanOrEqualTo("startAt", now)
                .limit(50)
                .get().await()

            val broadcasts = snapshot.documents.mapNotNull { doc ->
                val data = doc.data ?: return@mapNotNull null
                val endAt = data["endAt"] as? Long
                if (endAt != null && endAt < now) return@mapNotNull null

                BroadcastMessage(
                    id = doc.id,
                    title = data["title"] as? String ?: "",
                    htmlContent = data["htmlContent"] as? String ?: "",
                    priority = parsePriority(data["priority"] as? String ?: "info"),
                    targetType = parseTargetType(data["targetType"] as? String ?: "all"),
                    targetUids = (data["targetUids"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                    startAt = DateUtils.toMillis(data["startAt"]) ?: 0L,
                    endAt = endAt,
                    active = data["active"] as? Boolean ?: true,
                    createdBy = data["createdBy"] as? String ?: "",
                    createdByName = data["createdByName"] as? String ?: "",
                    createdAt = DateUtils.toMillis(data["createdAt"]) ?: 0L,
                    stoppedAt = data["stoppedAt"] as? Long
                )
            }

            val filtered = broadcasts.filter { b ->
                if (b.createdBy == uid) return@filter false
                when (b.targetType) {
                    BroadcastTargetType.ALL -> true
                    BroadcastTargetType.ALL_EXCEPT_BLOCKED -> !isBlocked
                    BroadcastTargetType.SPECIFIC -> b.targetUids.contains(uid)
                }
            }
            Result.success(filtered)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    override suspend fun getUnreadBroadcastsForUser(
        uid: String,
        isBlocked: Boolean
    ): Result<List<BroadcastMessage>> {
        return try {
            val activeResult = getActiveBroadcastsForUser(uid, isBlocked)
            if (activeResult.isFailure) return activeResult

            val active = activeResult.getOrThrow()

            val readDocs = coroutineScope {
                active.associate { b ->
                    b.id to async {
                        firestore.collection("broadcasts")
                            .document(b.id)
                            .collection("reads")
                            .document(uid)
                            .get().await()
                    }
                }.mapValues { it.value.await() }
            }

            val unread = active.filter { b ->
                !(readDocs[b.id]?.exists() ?: false)
            }
            Result.success(unread)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    override suspend fun acknowledgeBroadcast(broadcastId: String, uid: String): Result<Unit> {
        return try {
            val data = mapOf(
                "uid" to uid,
                "readAt" to System.currentTimeMillis()
            )
            firestore.collection("broadcasts")
                .document(broadcastId)
                .collection("reads")
                .document(uid)
                .set(data)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    private fun parsePriority(value: String): BroadcastPriority {
        return when (value.lowercase()) {
            "critical" -> BroadcastPriority.CRITICAL
            "maintenance" -> BroadcastPriority.MAINTENANCE
            else -> BroadcastPriority.INFO
        }
    }

    private fun parseTargetType(value: String): BroadcastTargetType {
        return when (value.lowercase()) {
            "all_except_blocked" -> BroadcastTargetType.ALL_EXCEPT_BLOCKED
            "specific" -> BroadcastTargetType.SPECIFIC
            else -> BroadcastTargetType.ALL
        }
    }
}
