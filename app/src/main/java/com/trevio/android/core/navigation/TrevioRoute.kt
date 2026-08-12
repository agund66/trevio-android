package com.trevio.android.core.navigation

sealed class TrevioRoute(val route: String) {
    data object Splash : TrevioRoute("splash")
    data object Login : TrevioRoute("login")
    data object Terms : TrevioRoute("terms")
    data object PhoneSetup : TrevioRoute("phone_setup")
    data object Main : TrevioRoute("main")
    data object Home : TrevioRoute("home")
    data object Groups : TrevioRoute("groups")
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
    data object GroupSettings : TrevioRoute("group_settings/{groupId}") {
        fun createRoute(groupId: String) = "group_settings/$groupId"
    }
    data object EditExpense : TrevioRoute("edit_expense/{groupId}/{expenseId}") {
        fun createRoute(groupId: String, expenseId: String) = "edit_expense/$groupId/$expenseId"
    }
    data object PublicProfile : TrevioRoute("user/{uid}") {
        fun createRoute(uid: String) = "user/$uid"
    }
    data object Admin : TrevioRoute("admin")
}
