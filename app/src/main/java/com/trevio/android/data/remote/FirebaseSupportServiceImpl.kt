package com.trevio.android.data.remote

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.trevio.android.domain.model.HelpArticle
import com.trevio.android.domain.model.PaginatedResult
import com.trevio.android.domain.model.SupportCategory
import com.trevio.android.domain.model.SupportMessage
import com.trevio.android.domain.model.SupportMessageRole
import com.trevio.android.domain.model.SupportPriority
import com.trevio.android.domain.model.SupportStatus
import com.trevio.android.domain.model.SupportTicket
import com.trevio.android.domain.model.SupportTicketContext
import com.trevio.android.domain.repository.SupportService
import com.trevio.android.util.DateUtils
import com.trevio.android.util.ErrorMessages
import com.trevio.android.util.friendlyNetworkMessage
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

private val DEFAULT_PRIORITY = mapOf(
    SupportCategory.BUG to SupportPriority.URGENT,
    SupportCategory.CALCULATION to SupportPriority.HIGH,
    SupportCategory.SETTLEMENT to SupportPriority.HIGH,
    SupportCategory.EXPENSE to SupportPriority.MEDIUM,
    SupportCategory.GROUP_ACCESS to SupportPriority.MEDIUM,
    SupportCategory.PAYMENT_INFO to SupportPriority.MEDIUM,
    SupportCategory.ACCOUNT to SupportPriority.LOW,
    SupportCategory.OTHER to SupportPriority.LOW
)

@Singleton
class FirebaseSupportServiceImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : SupportService {

    private suspend fun requireSuperadmin(): Result<Unit> {
        return try {
            val uid = auth.currentUser?.uid ?: return Result.failure(Exception(ErrorMessages.USER_NOT_AUTHENTICATED))
            val userDoc = firestore.collection("users").document(uid).get().await()
            val role = (userDoc.data?.get("role") as? String) ?: "user"
            if (role != "superadmin") return Result.failure(Exception(ErrorMessages.ACCESS_DENIED_SUPERADMIN))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    // ─── Tickets (user side) ───────────────────────────────────────

    override suspend fun createTicket(
        subject: String,
        description: String,
        category: SupportCategory,
        context: SupportTicketContext?
    ): Result<String> {
        return try {
            val user = auth.currentUser ?: return Result.failure(Exception(ErrorMessages.USER_NOT_AUTHENTICATED))
            val userDoc = firestore.collection("users").document(user.uid).get().await()
            val userData = userDoc.data ?: return Result.failure(Exception("User profile not found"))

            val now = System.currentTimeMillis()
            val priority = DEFAULT_PRIORITY[category] ?: SupportPriority.LOW

            val ticketData = mapOf(
                "userId" to user.uid,
                "userEmail" to (userData["email"] as? String ?: ""),
                "userDisplayName" to (userData["displayName"] as? String ?: ""),
                "userUsername" to (userData["username"] as? String ?: ""),
                "subject" to subject,
                "description" to description,
                "category" to category.name.lowercase(),
                "priority" to priority.name.lowercase(),
                "status" to SupportStatus.OPEN.name.lowercase(),
                "context" to mapOf(
                    "groupId" to (context?.groupId ?: ""),
                    "groupName" to (context?.groupName ?: ""),
                    "expenseId" to (context?.expenseId ?: ""),
                    "screen" to (context?.screen ?: "")
                ),
                "createdAt" to now,
                "updatedAt" to now,
                "resolvedAt" to null,
                "resolvedBy" to null,
                "lastMessageAt" to now,
                "lastMessageBy" to "user",
                "unreadByUser" to false,
                "unreadByAdmin" to true
            )

            val ticketRef = firestore.collection("supportTickets").add(ticketData).await()

            // Add initial message
            val msgData = mapOf(
                "fromUid" to user.uid,
                "fromName" to (userData["displayName"] as? String ?: ""),
                "fromRole" to "user",
                "body" to description,
                "createdAt" to now
            )
            firestore.collection("supportTickets").document(ticketRef.id)
                .collection("messages").document().set(msgData).await()

            Result.success(ticketRef.id)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    override suspend fun getMyTickets(pageSize: Int, lastTicketId: String?): Result<PaginatedResult<SupportTicket>> {
        return try {
            val uid = auth.currentUser?.uid ?: return Result.failure(Exception(ErrorMessages.USER_NOT_AUTHENTICATED))
            var query = firestore.collection("supportTickets")
                .whereEqualTo("userId", uid)
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .limit(pageSize.toLong())

            if (lastTicketId != null) {
                val lastDoc = firestore.collection("supportTickets").document(lastTicketId).get().await()
                if (lastDoc.exists()) {
                    query = firestore.collection("supportTickets")
                        .whereEqualTo("userId", uid)
                        .orderBy("updatedAt", Query.Direction.DESCENDING)
                        .startAfter(lastDoc)
                        .limit(pageSize.toLong())
                }
            }

            val snapshot = query.get().await()

            val tickets = snapshot.documents.mapNotNull { doc ->
                doc.data?.let { mapTicket(doc.id, it) }
            }
            Result.success(PaginatedResult(
                items = tickets,
                hasMore = snapshot.size() == pageSize,
                lastId = if (snapshot.size() > 0) snapshot.documents.last().id else null
            ))
        } catch (e: Exception) {
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    override suspend fun getTicket(ticketId: String): Result<SupportTicket?> {
        return try {
            val uid = auth.currentUser?.uid ?: return Result.failure(Exception(ErrorMessages.USER_NOT_AUTHENTICATED))
            val ticketDoc = firestore.collection("supportTickets").document(ticketId).get().await()
            if (!ticketDoc.exists()) return Result.success(null)

            val data = ticketDoc.data ?: return Result.success(null)
            if ((data["userId"] as? String) != uid) {
                requireSuperadmin().onFailure { return Result.failure(it) }
            }
            Result.success(mapTicket(ticketDoc.id, data))
        } catch (e: Exception) {
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    override suspend fun markTicketReadByUser(ticketId: String): Result<Unit> {
        return try {
            val uid = auth.currentUser?.uid ?: return Result.failure(Exception(ErrorMessages.USER_NOT_AUTHENTICATED))
            val ticketDoc = firestore.collection("supportTickets").document(ticketId).get().await()
            if (!ticketDoc.exists()) return Result.failure(Exception("Ticket not found"))
            if ((ticketDoc.data?.get("userId") as? String) != uid) {
                return Result.failure(Exception(ErrorMessages.ACCESS_DENIED))
            }
            firestore.collection("supportTickets").document(ticketId)
                .update("unreadByUser", false).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    // ─── Messages ──────────────────────────────────────────────────

    override suspend fun getMessages(ticketId: String): Result<List<SupportMessage>> {
        return try {
            val uid = auth.currentUser?.uid ?: return Result.failure(Exception(ErrorMessages.USER_NOT_AUTHENTICATED))
            val ticketDoc = firestore.collection("supportTickets").document(ticketId).get().await()
            if (!ticketDoc.exists()) return Result.failure(Exception("Ticket not found"))
            if ((ticketDoc.data?.get("userId") as? String) != uid) {
                requireSuperadmin().onFailure { return Result.failure(it) }
            }

            val snapshot = firestore.collection("supportTickets").document(ticketId)
                .collection("messages")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .limit(200)
                .get().await()

            val messages = snapshot.documents.map { doc ->
                val data = doc.data ?: emptyMap()
                SupportMessage(
                    messageId = doc.id,
                    fromUid = data["fromUid"] as? String ?: "",
                    fromName = data["fromName"] as? String ?: "",
                    fromRole = if (data["fromRole"] as? String == "superadmin") SupportMessageRole.SUPERADMIN else SupportMessageRole.USER,
                    body = data["body"] as? String ?: "",
                    createdAt = DateUtils.toMillis(data["createdAt"]) ?: 0L
                )
            }
            Result.success(messages)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    override suspend fun sendMessage(ticketId: String, body: String): Result<Unit> {
        return try {
            val user = auth.currentUser ?: return Result.failure(Exception(ErrorMessages.USER_NOT_AUTHENTICATED))
            val userDoc = firestore.collection("users").document(user.uid).get().await()
            val displayName = (userDoc.data?.get("displayName") as? String) ?: ""

            val ticketDoc = firestore.collection("supportTickets").document(ticketId).get().await()
            if (!ticketDoc.exists()) return Result.failure(Exception("Ticket not found"))
            if ((ticketDoc.data?.get("userId") as? String) != user.uid) {
                return Result.failure(Exception("Access denied: you can only reply to your own tickets"))
            }

            val now = System.currentTimeMillis()
            val ticketData = ticketDoc.data ?: return Result.failure(Exception("Ticket data not found"))
            val currentStatus = (ticketData["status"] as? String) ?: "open"

            val msgData = mapOf(
                "fromUid" to user.uid,
                "fromName" to displayName,
                "fromRole" to "user",
                "body" to body,
                "createdAt" to now
            )
            firestore.collection("supportTickets").document(ticketId)
                .collection("messages").document().set(msgData).await()

            val update = mutableMapOf<String, Any?>(
                "updatedAt" to now,
                "lastMessageAt" to now,
                "lastMessageBy" to "user",
                "unreadByAdmin" to true,
                "unreadByUser" to false
            )

            if (currentStatus == "resolved" || currentStatus == "closed") {
                update["status"] = "open"
                update["resolvedAt"] = null
                update["resolvedBy"] = null
            }

            firestore.collection("supportTickets").document(ticketId).update(update).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    // ─── Admin: tickets ────────────────────────────────────────────

    override suspend fun getAllTickets(
        status: SupportStatus?,
        category: SupportCategory?,
        priority: SupportPriority?,
        pageSize: Int,
        lastTicketId: String?
    ): Result<PaginatedResult<SupportTicket>> {
        return try {
            requireSuperadmin().onFailure { return Result.failure(it) }

            var query: Query = firestore.collection("supportTickets")

            if (status != null) {
                query = query.whereEqualTo("status", status.name.lowercase())
            }
            if (category != null) {
                query = query.whereEqualTo("category", category.name.lowercase())
            }
            if (priority != null) {
                query = query.whereEqualTo("priority", priority.name.lowercase())
            }

            query = query.orderBy("updatedAt", Query.Direction.DESCENDING)

            if (lastTicketId != null) {
                val lastDoc = firestore.collection("supportTickets").document(lastTicketId).get().await()
                if (lastDoc.exists()) {
                    query = query.startAfter(lastDoc)
                }
            }

            query = query.limit(pageSize.toLong())

            val snapshot = query.get().await()
            val tickets = snapshot.documents.mapNotNull { doc ->
                doc.data?.let { mapTicket(doc.id, it) }
            }

            Result.success(PaginatedResult(
                items = tickets,
                hasMore = snapshot.size() == pageSize,
                lastId = if (snapshot.size() > 0) snapshot.documents.last().id else null
            ))
        } catch (e: Exception) {
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    override suspend fun updateTicketStatus(ticketId: String, status: SupportStatus): Result<Unit> {
        return try {
            requireSuperadmin().onFailure { return Result.failure(it) }
            val now = System.currentTimeMillis()
            val update = mutableMapOf<String, Any?>(
                "status" to status.name.lowercase(),
                "updatedAt" to now
            )
            if (status == SupportStatus.RESOLVED || status == SupportStatus.CLOSED) {
                update["resolvedAt"] = now
                update["resolvedBy"] = (auth.currentUser?.uid ?: "")
            } else {
                update["resolvedAt"] = null
                update["resolvedBy"] = null
            }
            firestore.collection("supportTickets").document(ticketId).update(update).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    override suspend fun updateTicketPriority(ticketId: String, priority: SupportPriority): Result<Unit> {
        return try {
            requireSuperadmin().onFailure { return Result.failure(it) }
            firestore.collection("supportTickets").document(ticketId)
                .update(mapOf(
                    "priority" to priority.name.lowercase(),
                    "updatedAt" to System.currentTimeMillis()
                )).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    override suspend fun markTicketReadByAdmin(ticketId: String): Result<Unit> {
        return try {
            requireSuperadmin().onFailure { return Result.failure(it) }
            firestore.collection("supportTickets").document(ticketId)
                .update("unreadByAdmin", false).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    // ─── Admin: messages ───────────────────────────────────────────

    override suspend fun sendAdminMessage(ticketId: String, body: String): Result<Unit> {
        return try {
            val user = auth.currentUser ?: return Result.failure(Exception(ErrorMessages.USER_NOT_AUTHENTICATED))
            val userDoc = firestore.collection("users").document(user.uid).get().await()
            val displayName = (userDoc.data?.get("displayName") as? String) ?: "Admin"
            requireSuperadmin().onFailure { return Result.failure(it) }

            val ticketDoc = firestore.collection("supportTickets").document(ticketId).get().await()
            if (!ticketDoc.exists()) return Result.failure(Exception("Ticket not found"))

            val now = System.currentTimeMillis()
            val ticketData = ticketDoc.data ?: return Result.failure(Exception("Ticket data not found"))
            val currentStatus = (ticketData["status"] as? String) ?: "open"
            val targetUid = (ticketData["userId"] as? String) ?: ""

            val msgData = mapOf(
                "fromUid" to user.uid,
                "fromName" to displayName,
                "fromRole" to "superadmin",
                "body" to body,
                "createdAt" to now
            )
            firestore.collection("supportTickets").document(ticketId)
                .collection("messages").document().set(msgData).await()

            val update = mutableMapOf<String, Any?>(
                "updatedAt" to now,
                "lastMessageAt" to now,
                "lastMessageBy" to "superadmin",
                "unreadByUser" to true,
                "unreadByAdmin" to false
            )

            if (currentStatus == "open") {
                update["status"] = "in_progress"
            }

            firestore.collection("supportTickets").document(ticketId).update(update).await()

            // Send notification to user
            if (targetUid.isNotEmpty()) {
                val subject = (ticketData["subject"] as? String) ?: ""
                val notifData = mapOf(
                    "type" to "support_response",
                    "title" to "Support Update",
                    "body" to "Admin responded to your ticket: \"$subject\"",
                    "read" to false,
                    "createdAt" to now,
                    "data" to mapOf(
                        "ticketId" to ticketId,
                        "type" to "support"
                    )
                )
                firestore.collection("users").document(targetUid)
                    .collection("notifications").document().set(notifData).await()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    // ─── Help articles ─────────────────────────────────────────────

    override suspend fun getHelpArticles(): Result<List<HelpArticle>> {
        return try {
            val snapshot = firestore.collection("helpArticles")
                .whereEqualTo("active", true)
                .orderBy("order", Query.Direction.ASCENDING)
                .limit(100)
                .get().await()

            val articles = snapshot.documents.mapNotNull { doc ->
                doc.data?.let { mapArticle(doc.id, it) }
            }
            Result.success(articles)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    override suspend fun getAllHelpArticles(): Result<List<HelpArticle>> {
        return try {
            requireSuperadmin().onFailure { return Result.failure(it) }
            val snapshot = firestore.collection("helpArticles")
                .orderBy("order", Query.Direction.ASCENDING)
                .limit(200)
                .get().await()

            val articles = snapshot.documents.mapNotNull { doc ->
                doc.data?.let { mapArticle(doc.id, it) }
            }
            Result.success(articles)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    override suspend fun createHelpArticle(
        title: String,
        content: String,
        category: String,
        tags: List<String>,
        order: Int
    ): Result<String> {
        return try {
            requireSuperadmin().onFailure { return Result.failure(it) }
            val now = System.currentTimeMillis()
            val data = mapOf(
                "title" to title,
                "content" to content,
                "category" to category,
                "tags" to tags,
                "order" to order,
                "active" to true,
                "createdAt" to now,
                "updatedAt" to now,
                "createdBy" to (auth.currentUser?.uid ?: "")
            )
            val ref = firestore.collection("helpArticles").add(data).await()
            Result.success(ref.id)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    override suspend fun updateHelpArticle(
        articleId: String,
        title: String?,
        content: String?,
        category: String?,
        tags: List<String>?,
        order: Int?,
        active: Boolean?
    ): Result<Unit> {
        return try {
            requireSuperadmin().onFailure { return Result.failure(it) }
            val update = mutableMapOf<String, Any?>("updatedAt" to System.currentTimeMillis())
            title?.let { update["title"] = it }
            content?.let { update["content"] = it }
            category?.let { update["category"] = it }
            tags?.let { update["tags"] = it }
            order?.let { update["order"] = it }
            active?.let { update["active"] = it }
            firestore.collection("helpArticles").document(articleId).update(update).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    override suspend fun deleteHelpArticle(articleId: String): Result<Unit> {
        return try {
            requireSuperadmin().onFailure { return Result.failure(it) }
            firestore.collection("helpArticles").document(articleId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    // ─── Helpers ───────────────────────────────────────────────────

    private fun mapTicket(id: String, data: Map<String, Any>): SupportTicket {
        @Suppress("UNCHECKED_CAST")
        val ctx = (data["context"] as? Map<String, Any>) ?: emptyMap()
        return SupportTicket(
            ticketId = id,
            userId = data["userId"] as? String ?: "",
            userEmail = data["userEmail"] as? String ?: "",
            userDisplayName = data["userDisplayName"] as? String ?: "",
            userUsername = data["userUsername"] as? String ?: "",
            subject = data["subject"] as? String ?: "",
            description = data["description"] as? String ?: "",
            category = SupportCategory.fromString(data["category"] as? String),
            priority = run {
                val p = data["priority"] as? String ?: "low"
                runCatching { SupportPriority.valueOf(p.uppercase()) }.getOrDefault(SupportPriority.LOW)
            },
            status = run {
                val s = data["status"] as? String ?: "open"
                runCatching { SupportStatus.valueOf(s.uppercase()) }.getOrDefault(SupportStatus.OPEN)
            },
            context = SupportTicketContext(
                groupId = ctx["groupId"] as? String ?: "",
                groupName = ctx["groupName"] as? String ?: "",
                expenseId = ctx["expenseId"] as? String ?: "",
                screen = ctx["screen"] as? String ?: ""
            ),
            createdAt = DateUtils.toMillis(data["createdAt"]) ?: 0L,
            updatedAt = DateUtils.toMillis(data["updatedAt"]) ?: 0L,
            resolvedAt = (data["resolvedAt"] as? Number)?.toLong(),
            resolvedBy = data["resolvedBy"] as? String,
            lastMessageAt = DateUtils.toMillis(data["lastMessageAt"]) ?: 0L,
            lastMessageBy = data["lastMessageBy"] as? String,
            unreadByUser = data["unreadByUser"] as? Boolean ?: false,
            unreadByAdmin = data["unreadByAdmin"] as? Boolean ?: false
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun mapArticle(id: String, data: Map<String, Any>): HelpArticle {
        return HelpArticle(
            articleId = id,
            title = data["title"] as? String ?: "",
            content = data["content"] as? String ?: "",
            category = data["category"] as? String ?: "general",
            tags = (data["tags"] as? List<String>) ?: emptyList(),
            order = (data["order"] as? Number)?.toInt() ?: 0,
            active = data["active"] as? Boolean ?: true,
            createdAt = DateUtils.toMillis(data["createdAt"]) ?: 0L,
            updatedAt = DateUtils.toMillis(data["updatedAt"]) ?: 0L,
            createdBy = data["createdBy"] as? String ?: ""
        )
    }
}
