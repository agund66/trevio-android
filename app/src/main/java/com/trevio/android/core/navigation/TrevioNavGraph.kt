package com.trevio.android.core.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.trevio.android.ui.auth.AuthScreen
import com.trevio.android.ui.auth.TermsScreen
import com.trevio.android.ui.expense.AddExpenseScreen
import com.trevio.android.ui.group.CreateGroupScreen
import com.trevio.android.ui.group.GroupDetailScreen
import com.trevio.android.ui.group.JoinGroupScreen
import com.trevio.android.ui.home.HomeScreen
import com.trevio.android.ui.notifications.NotificationsScreen
import com.trevio.android.ui.profile.ProfileScreen
import com.trevio.android.ui.settlement.SettleUpScreen
import com.trevio.android.ui.splash.SplashScreen

@Composable
fun TrevioNavGraph(
    navController: NavHostController,
    startDestination: String = TrevioRoute.Splash.route,
    pendingInviteCode: String? = null
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(TrevioRoute.Splash.route) {
            SplashScreen(navController = navController, pendingInviteCode = pendingInviteCode)
        }

        composable(TrevioRoute.Login.route) {
            AuthScreen(navController = navController)
        }

        composable(TrevioRoute.Terms.route) {
            TermsScreen(navController = navController)
        }

        composable(TrevioRoute.Home.route) {
            HomeScreen(navController = navController)
        }

        composable(TrevioRoute.Notifications.route) {
            NotificationsScreen(navController = navController)
        }

        composable(TrevioRoute.Profile.route) {
            ProfileScreen(navController = navController)
        }

        composable(TrevioRoute.CreateGroup.route) {
            CreateGroupScreen(navController = navController)
        }

        composable(
            route = TrevioRoute.JoinGroup.route,
            arguments = listOf(navArgument("inviteCode") { type = NavType.StringType })
        ) {
            JoinGroupScreen(navController = navController)
        }

        composable(
            route = TrevioRoute.GroupDetail.route,
            arguments = listOf(navArgument("groupId") { type = NavType.StringType })
        ) {
            GroupDetailScreen(navController = navController)
        }

        composable(
            route = TrevioRoute.AddExpense.route,
            arguments = listOf(navArgument("groupId") { type = NavType.StringType })
        ) {
            AddExpenseScreen(navController = navController)
        }

        composable(
            route = TrevioRoute.SettleUp.route,
            arguments = listOf(navArgument("groupId") { type = NavType.StringType })
        ) {
            SettleUpScreen(navController = navController)
        }
    }
}
