package com.trevio.android.core.navigation

sealed class TrevioRoute(val route: String) {
    data object Splash : TrevioRoute("splash")
    data object Login : TrevioRoute("login")
    data object Terms : TrevioRoute("terms")
    data object Home : TrevioRoute("home")
    data object Notifications : TrevioRoute("notifications")
    data object Profile : TrevioRoute("profile")
    data object CreateGroup : TrevioRoute("create_group")
    data object JoinGroup : TrevioRoute("join_group/{inviteCode}") {
        fun createRoute(inviteCode: String) = "join_group/$inviteCode"
    }
    data object GroupDetail : TrevioRoute("group/{groupId}") {
        fun createRoute(groupId: String) = "group/$groupId"
    }
    data object AddExpense : TrevioRoute("add_expense/{groupId}") {
        fun createRoute(groupId: String) = "add_expense/$groupId"
    }
    data object SettleUp : TrevioRoute("settle_up/{groupId}") {
        fun createRoute(groupId: String) = "settle_up/$groupId"
    }
}
