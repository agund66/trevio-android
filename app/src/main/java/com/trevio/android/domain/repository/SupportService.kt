package com.trevio.android.domain.repository

import com.trevio.android.domain.model.HelpArticle
import com.trevio.android.domain.model.PaginatedResult
import com.trevio.android.domain.model.SupportCategory
import com.trevio.android.domain.model.SupportMessage
import com.trevio.android.domain.model.SupportPriority
import com.trevio.android.domain.model.SupportStatus
import com.trevio.android.domain.model.SupportTicket
import com.trevio.android.domain.model.SupportTicketContext

interface SupportService {
    // ─── Tickets (user side) ───────────────────────────────────────
    suspend fun createTicket(
        subject: String,
        description: String,
        category: SupportCategory,
        context: SupportTicketContext?
    ): Result<String>

    suspend fun getMyTickets(pageSize: Int = 50, lastTicketId: String? = null): Result<PaginatedResult<SupportTicket>>
    suspend fun getTicket(ticketId: String): Result<SupportTicket?>
    suspend fun markTicketReadByUser(ticketId: String): Result<Unit>

    // ─── Messages ──────────────────────────────────────────────────
    suspend fun getMessages(ticketId: String): Result<List<SupportMessage>>
    suspend fun sendMessage(ticketId: String, body: String): Result<Unit>

    // ─── Admin: tickets ────────────────────────────────────────────
    suspend fun getAllTickets(
        status: SupportStatus? = null,
        category: SupportCategory? = null,
        priority: SupportPriority? = null,
        pageSize: Int = 50,
        lastTicketId: String? = null
    ): Result<PaginatedResult<SupportTicket>>

    suspend fun updateTicketStatus(ticketId: String, status: SupportStatus): Result<Unit>
    suspend fun updateTicketPriority(ticketId: String, priority: SupportPriority): Result<Unit>
    suspend fun markTicketReadByAdmin(ticketId: String): Result<Unit>

    // ─── Admin: messages ───────────────────────────────────────────
    suspend fun sendAdminMessage(ticketId: String, body: String): Result<Unit>

    // ─── Help articles ─────────────────────────────────────────────
    suspend fun getHelpArticles(): Result<List<HelpArticle>>
    suspend fun getAllHelpArticles(): Result<List<HelpArticle>>
    suspend fun createHelpArticle(
        title: String,
        content: String,
        category: String,
        tags: List<String>,
        order: Int
    ): Result<String>

    suspend fun updateHelpArticle(
        articleId: String,
        title: String?,
        content: String?,
        category: String?,
        tags: List<String>?,
        order: Int?,
        active: Boolean?
    ): Result<Unit>

    suspend fun deleteHelpArticle(articleId: String): Result<Unit>
}
