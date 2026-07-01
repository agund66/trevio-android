package com.trevio.android.core.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import androidx.navigation.compose.rememberNavController
import com.trevio.android.core.designsystem.theme.TrevioBorder
import com.trevio.android.ui.expense.AddExpenseScreen
import com.trevio.android.ui.group.CreateGroupScreen
import com.trevio.android.ui.group.GroupDetailScreen
import com.trevio.android.ui.group.GroupsListScreen
import com.trevio.android.ui.group.JoinGroupScreen
import com.trevio.android.ui.home.HomeScreen
import com.trevio.android.ui.notifications.NotificationsScreen
import com.trevio.android.ui.profile.ProfileScreen
import com.trevio.android.ui.profile.PublicProfileScreen
import com.trevio.android.ui.settlement.SettleUpScreen

data class BottomNavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

val bottomNavItems = listOf(
    BottomNavItem(TrevioRoute.Home.route, "Home", Icons.Filled.Home, Icons.Outlined.Home),
    BottomNavItem(TrevioRoute.Groups.route, "Groups", Icons.Filled.Group, Icons.Outlined.Group),
    BottomNavItem(TrevioRoute.Profile.route, "Profile", Icons.Filled.Person, Icons.Outlined.Person)
)

@Composable
fun MainShell(
    navController: NavHostController,
    pendingInviteCode: String?
) {
    val innerNavController = rememberNavController()
    val navBackStackEntry by innerNavController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute in bottomNavItems.map { it.route }

    LaunchedEffect(pendingInviteCode) {
        if (pendingInviteCode != null) {
            navController.navigate(TrevioRoute.JoinGroup.createRoute(pendingInviteCode))
        }
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                    modifier = Modifier.border(
                        width = 1.dp,
                        color = TrevioBorder,
                        shape = RoundedCornerShape(0.dp)
                    )
                ) {
                    bottomNavItems.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick = {
                                if (currentRoute != item.route) {
                                    innerNavController.navigate(item.route) {
                                        popUpTo(TrevioRoute.Home.route) {
                                            inclusive = false
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (currentRoute == item.route) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.label
                                )
                            },
                            label = {
                                Text(
                                    item.label,
                                    fontWeight = if (currentRoute == item.route) FontWeight.SemiBold else FontWeight.Normal,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = innerNavController,
            startDestination = TrevioRoute.Home.route,
            modifier = Modifier.fillMaxSize().padding(innerPadding)
        ) {
            mainTabGraph(
                innerNavController = innerNavController,
                onSignOut = {
                    navController.navigate(TrevioRoute.Login.route) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                }
            )
            detailGraph(innerNavController)
        }
    }
}

private fun NavGraphBuilder.mainTabGraph(
    innerNavController: NavHostController,
    onSignOut: () -> Unit
) {
    composable(TrevioRoute.Home.route) {
        HomeScreen(navController = innerNavController, onSignOut = onSignOut)
    }
    composable(TrevioRoute.Groups.route) {
        GroupsListScreen(navController = innerNavController)
    }
    composable(TrevioRoute.Notifications.route) {
        NotificationsScreen(navController = innerNavController)
    }
    composable(TrevioRoute.Profile.route) {
        ProfileScreen(navController = innerNavController, onSignOut = onSignOut)
    }
}

private fun NavGraphBuilder.detailGraph(navController: NavHostController) {
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

    composable(
        route = TrevioRoute.PublicProfile.route,
        arguments = listOf(navArgument("uid") { type = NavType.StringType })
    ) {
        val uid = it.arguments?.getString("uid") ?: ""
        PublicProfileScreen(navController = navController, uid = uid)
    }
}
