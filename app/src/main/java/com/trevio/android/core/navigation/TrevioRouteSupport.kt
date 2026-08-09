package com.trevio.android.core.navigation

/**
 * Navigation routes for the support system.
 * Kept separate from TrevioRoute to avoid merge conflicts and keep
 * support-related routes self-contained.
 */
sealed class TrevioRouteSupport(val route: String) {
    data object Support : TrevioRouteSupport("support")
    data object CreateTicket : TrevioRouteSupport("create_ticket?groupId={groupId}&groupName={groupName}&screen={screen}") {
        fun createRoute(groupId: String? = null, groupName: String? = null, screen: String? = null): String {
            val gid = groupId ?: ""
            val gname = groupName ?: ""
            val scr = screen ?: ""
            return "create_ticket?groupId=$gid&groupName=$gname&screen=$scr"
        }
    }
    data object MyTickets : TrevioRouteSupport("my_tickets")
    data object TicketDetail : TrevioRouteSupport("ticket_detail/{ticketId}") {
        fun createRoute(ticketId: String) = "ticket_detail/$ticketId"
    }
}
