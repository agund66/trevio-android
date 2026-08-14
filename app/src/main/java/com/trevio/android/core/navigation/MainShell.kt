package com.trevio.android.core.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import com.trevio.android.R
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import androidx.navigation.compose.rememberNavController
import com.trevio.android.core.designsystem.theme.TrevioBorder
import com.trevio.android.core.designsystem.theme.TrevioBorderDark
import com.trevio.android.core.designsystem.components.OfflineBanner
import com.trevio.android.ui.admin.AdminScreen
import com.trevio.android.util.NetworkMonitor
import com.trevio.android.ui.broadcast.BroadcastPopup
import com.trevio.android.ui.expense.AddExpenseScreen
import com.trevio.android.ui.expense.EditExpenseScreen
import com.trevio.android.ui.group.CreateGroupScreen
import com.trevio.android.ui.group.GroupDetailScreen
import com.trevio.android.ui.group.GroupSettingsScreen
import com.trevio.android.ui.group.GroupsListScreen
import com.trevio.android.ui.group.JoinGroupScreen
import com.trevio.android.ui.home.HomeScreen
import com.trevio.android.ui.more.MoreScreen
import com.trevio.android.ui.notifications.NotificationsScreen
import com.trevio.android.ui.profile.ProfileScreen
import com.trevio.android.ui.profile.PublicProfileScreen
import com.trevio.android.ui.settlement.SettleUpScreen
import com.trevio.android.ui.support.CreateTicketScreen
import com.trevio.android.ui.support.MyTicketsScreen
import com.trevio.android.ui.support.SupportScreen
import com.trevio.android.ui.support.TicketDetailScreen
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class MainShellViewModel @Inject constructor(
    networkMonitor: NetworkMonitor
) : ViewModel() {

    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline
}

data class BottomNavItem(
    val route: String,
    @StringRes val labelResId: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

val baseBottomNavItems = listOf(
    BottomNavItem(TrevioRoute.Home.route, R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home),
    BottomNavItem(TrevioRoute.Groups.route, R.string.nav_groups, Icons.Filled.Group, Icons.Outlined.Group),
    BottomNavItem(TrevioRoute.Notifications.route, R.string.nav_notifications, Icons.Filled.Notifications, Icons.Outlined.Notifications),
    BottomNavItem(TrevioRoute.More.route, R.string.nav_more, Icons.Filled.MoreHoriz, Icons.Outlined.MoreHoriz)
)

@Composable
fun MainShell(
    navController: NavHostController,
    pendingInviteCode: String?,
    pendingNavRoute: Pair<String, Int>? = null
) {
    val innerNavController = rememberNavController()
    val navBackStackEntry by innerNavController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val viewModel = androidx.hilt.navigation.compose.hiltViewModel<MainShellViewModel>()
    val isOnline by viewModel.isOnline.collectAsState()

    val bottomNavItems = baseBottomNavItems
    val showBottomBar = currentRoute in bottomNavItems.map { it.route }

    LaunchedEffect(pendingInviteCode) {
        if (pendingInviteCode != null) {
            innerNavController.navigate(TrevioRoute.JoinGroup.createRoute(pendingInviteCode))
        }
    }

    // Navigate to a deep-linked route from a notification (e.g. "group/{groupId}").
    // The nonce in the Pair ensures the same route can re-trigger on subsequent
    // notifications (LaunchedEffect re-fires when the key object changes).
    LaunchedEffect(pendingNavRoute) {
        val route = pendingNavRoute?.first ?: return@LaunchedEffect
        if (currentRoute != route) {
            innerNavController.navigate(route)
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
                        color = if (isSystemInDarkTheme()) TrevioBorderDark else TrevioBorder,
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
                                    contentDescription = stringResource(item.labelResId)
                                )
                            },
                            label = {
                                Text(
                                    stringResource(item.labelResId),
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
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (!isOnline) {
                OfflineBanner()
            }
            Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                NavHost(
                    navController = innerNavController,
                    startDestination = TrevioRoute.Home.route,
                    modifier = Modifier.fillMaxSize()
                ) {
                    mainTabGraph(
                        innerNavController = innerNavController,
                        onSignOut = {
                            navController.navigate(TrevioRoute.Login.route) {
                                popUpTo(navController.graph.id) { inclusive = true }
                            }
                        }
                    )
                    detailGraph(
                        navController = innerNavController,
                        onSignOut = {
                            navController.navigate(TrevioRoute.Login.route) {
                                popUpTo(navController.graph.id) { inclusive = true }
                            }
                        }
                    )
                }
                BroadcastPopup()
            }
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
    composable(TrevioRoute.More.route) {
        MoreScreen(
            navController = innerNavController,
            onSignOut = onSignOut
        )
    }
}

private fun NavGraphBuilder.detailGraph(navController: NavHostController, onSignOut: () -> Unit) {
    composable(TrevioRoute.Profile.route) {
        ProfileScreen(navController = navController, onSignOut = onSignOut)
    }
    composable(TrevioRoute.Admin.route) {
        AdminScreen(navController = navController)
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

    composable(
        route = TrevioRoute.GroupSettings.route,
        arguments = listOf(navArgument("groupId") { type = NavType.StringType })
    ) {
        GroupSettingsScreen(navController = navController)
    }

    composable(
        route = TrevioRoute.EditExpense.route,
        arguments = listOf(
            navArgument("groupId") { type = NavType.StringType },
            navArgument("expenseId") { type = NavType.StringType }
        )
    ) {
        EditExpenseScreen(navController = navController)
    }

    composable(
        route = TrevioRoute.PublicProfile.route,
        arguments = listOf(navArgument("uid") { type = NavType.StringType })
    ) {
        val uid = it.arguments?.getString("uid") ?: ""
        PublicProfileScreen(navController = navController, uid = uid)
    }

    // ─── Support routes ───────────────────────────────────────────
    composable(TrevioRouteSupport.Support.route) {
        SupportScreen(navController = navController)
    }

    composable(
        route = TrevioRouteSupport.CreateTicket.route,
        arguments = listOf(
            navArgument("groupId") { type = NavType.StringType; defaultValue = "" },
            navArgument("groupName") { type = NavType.StringType; defaultValue = "" },
            navArgument("screen") { type = NavType.StringType; defaultValue = "" }
        )
    ) { entry ->
        val groupId = entry.arguments?.getString("groupId")?.takeIf { it.isNotEmpty() }
        val groupName = entry.arguments?.getString("groupName")?.takeIf { it.isNotEmpty() }
        val screen = entry.arguments?.getString("screen")?.takeIf { it.isNotEmpty() }
        CreateTicketScreen(
            navController = navController,
            contextGroupId = groupId,
            contextGroupName = groupName,
            contextScreen = screen
        )
    }

    composable(TrevioRouteSupport.MyTickets.route) {
        MyTicketsScreen(navController = navController)
    }

    composable(
        route = TrevioRouteSupport.TicketDetail.route,
        arguments = listOf(navArgument("ticketId") { type = NavType.StringType })
    ) { entry ->
        val ticketId = entry.arguments?.getString("ticketId") ?: ""
        TicketDetailScreen(navController = navController, ticketId = ticketId)
    }
}
