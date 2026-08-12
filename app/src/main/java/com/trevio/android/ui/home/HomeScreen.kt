package com.trevio.android.ui.home

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trevio.android.R
import com.trevio.android.core.UserRefreshNotifier
import com.trevio.android.core.designsystem.components.EmptyState
import com.trevio.android.core.designsystem.components.ListItemSkeleton
import com.trevio.android.core.designsystem.components.TrevioCard
import com.trevio.android.core.designsystem.theme.BalanceNegative
import com.trevio.android.core.designsystem.theme.BalanceNegativeDark
import com.trevio.android.core.designsystem.theme.BalancePositive
import com.trevio.android.core.designsystem.theme.BalancePositiveDark
import com.trevio.android.core.designsystem.theme.TemplateCasual
import com.trevio.android.core.designsystem.theme.TemplateCasualDark
import com.trevio.android.core.designsystem.theme.TemplateHousehold
import com.trevio.android.core.designsystem.theme.TemplateHouseholdDark
import com.trevio.android.core.designsystem.theme.TemplateTrip
import com.trevio.android.core.designsystem.theme.TemplateTripDark
import com.trevio.android.core.designsystem.theme.TemplateTurf
import com.trevio.android.core.designsystem.theme.TemplateTurfDark
import com.trevio.android.core.designsystem.theme.TrevioBorder
import com.trevio.android.core.designsystem.theme.TrevioBorderDark

import com.trevio.android.core.navigation.TrevioRoute
import com.trevio.android.domain.model.Group
import com.trevio.android.domain.model.GroupTemplate
import com.trevio.android.data.remote.FirestoreObservers
import com.trevio.android.domain.repository.AuthService
import com.trevio.android.domain.repository.GroupService
import com.trevio.android.ui.group.JoinGroupSheet
import com.trevio.android.util.rememberCurrencyFormatter
import com.trevio.android.util.toStringResId
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val groupService: GroupService,
    private val authService: AuthService,
    private val userRefreshNotifier: UserRefreshNotifier,
    private val firestoreObservers: FirestoreObservers
) : ViewModel() {

    data class HomeData(
        val groups: List<Group> = emptyList(),
        val totalOwed: Double = 0.0,
        val totalOwing: Double = 0.0,
        val netBalance: Double = 0.0,
        val totalExpenses: Double = 0.0,
        val activeGroups: Int = 0,
        val userDisplayName: String = "",
        val isLoading: Boolean = true,
        @StringRes val error: Int? = null,
        val signedOut: Boolean = false
    )

    private val _state = MutableStateFlow(HomeData())
    val state: StateFlow<HomeData> = _state

    /// Tracks the current groups listener so repeated loadGroups()
    /// calls don't create multiple Firestore listeners.
    private var groupsListenerJob: kotlinx.coroutines.Job? = null

    init {
        loadGroups()
        viewModelScope.launch {
            userRefreshNotifier.userRefreshed.collect {
                refreshGroups()
            }
        }
    }

    /**
     * Starts a real-time listener for the user's groups.  With offline
     * persistence enabled, the first emission comes from cache (instant)
     * and subsequent emissions arrive from the server silently.
     */
    fun loadGroups() {
        _state.value = _state.value.copy(isLoading = true, error = null)
        // Cancel any existing listener before starting a new one
        // to prevent duplicate Firestore listeners.
        groupsListenerJob?.cancel()
        groupsListenerJob = viewModelScope.launch {
            val user = authService.getCurrentUser()
            try {
                firestoreObservers.observeUserGroups().collect { groups ->
                    val totalOwed = groups.filter { it.yourBalance > 0 }.sumOf { it.yourBalance }
                    val totalOwing = groups.filter { it.yourBalance < 0 }.sumOf { -it.yourBalance }
                    val totalExpenses = groups.sumOf { it.totalExpenses }
                    val activeGroups = groups.count { !it.archived }
                    _state.value = _state.value.copy(
                        groups = groups,
                        totalOwed = totalOwed,
                        totalOwing = totalOwing,
                        netBalance = totalOwed - totalOwing,
                        totalExpenses = totalExpenses,
                        activeGroups = activeGroups,
                        userDisplayName = user?.displayName ?: _state.value.userDisplayName,
                        isLoading = false,
                        error = null
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.toStringResId()
                )
            }
        }
    }

    /**
     * Re-fetches the user's display name.  Groups are already kept
     * fresh by the real-time listener started in [loadGroups].
     */
    fun refreshGroups() {
        viewModelScope.launch {
            val user = authService.getCurrentUser()
            _state.value = _state.value.copy(
                userDisplayName = user?.displayName ?: _state.value.userDisplayName
            )
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authService.signOut()
            _state.value = _state.value.copy(signedOut = true)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: androidx.navigation.NavHostController,
    onSignOut: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val currencyFormatter = rememberCurrencyFormatter()
    var showJoinSheet by remember { mutableStateOf(false) }

    val needsRefresh by navController.currentBackStackEntry
        ?.savedStateHandle?.getStateFlow<Boolean>("needsRefresh", false)
        ?.collectAsState() ?: remember { mutableStateOf(false) }

    LaunchedEffect(needsRefresh) {
        if (needsRefresh) {
            viewModel.refreshGroups()
            navController.currentBackStackEntry?.savedStateHandle?.set("needsRefresh", false)
        }
    }

    LaunchedEffect(state.signedOut) {
        if (state.signedOut) {
            onSignOut()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Only show skeletons on a true cold start (no cached data).
        // With real-time listeners + offline persistence, repeat visits
        // emit cached data instantly — skeletons are barely visible.
        if (state.isLoading && state.groups.isEmpty() && state.error == null) {
            Column(modifier = Modifier.fillMaxSize()) {
                HomeHeader(
                    displayName = state.userDisplayName,
                    onNotificationsClick = { navController.navigate(TrevioRoute.Notifications.route) },
                    isLoading = true
                )
                // Skeleton placeholders instead of a spinner — shows the
                // approximate layout before data arrives from cache/server.
                repeat(4) {
                    ListItemSkeleton()
                    Spacer(Modifier.height(8.dp))
                }
            }
            return@Box
        }

        state.error?.let { errMsg ->
            Column(modifier = Modifier.fillMaxSize()) {
                HomeHeader(
                    displayName = state.userDisplayName,
                    onNotificationsClick = { navController.navigate(TrevioRoute.Notifications.route) }
                )
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(stringResource(R.string.home_failed_to_load), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(stringResource(errMsg), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            return@Box
        }
        if (state.groups.isEmpty()) {
            Column(modifier = Modifier.fillMaxSize()) {
                HomeHeader(
                    displayName = state.userDisplayName,
                    onNotificationsClick = { navController.navigate(TrevioRoute.Notifications.route) }
                )
                EmptyState(
                    icon = Icons.Default.Group,
                    title = stringResource(R.string.home_no_groups_yet),
                    message = stringResource(R.string.home_no_groups_message),
                    actionText = stringResource(R.string.home_create_group),
                    onAction = { navController.navigate(TrevioRoute.CreateGroup.route) }
                )
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                HomeHeader(
                    displayName = state.userDisplayName,
                    onNotificationsClick = { navController.navigate(TrevioRoute.Notifications.route) }
                )
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                item {
                    BalanceCard(
                        totalOwed = state.totalOwed,
                        totalOwing = state.totalOwing,
                        netBalance = state.netBalance,
                        totalExpenses = state.totalExpenses,
                        activeGroups = state.activeGroups,
                        formatBase = currencyFormatter.formatBase
                    )
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.home_your_groups),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(R.string.home_active_count, state.groups.size),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                items(state.groups, key = { it.groupId }) { group ->
                    GroupCardItem(
                        group = group,
                        onClick = {
                            navController.navigate(TrevioRoute.GroupDetail.createRoute(group.groupId))
                        },
                        formatBase = currencyFormatter.formatBase
                    )
                }

                item {
                    TextButton(
                        onClick = { navController.navigate(TrevioRoute.Groups.route) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Text(stringResource(R.string.home_view_all_groups))
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }
            }
            } // end Column
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.End
        ) {
            ExtendedFloatingActionButton(
                onClick = { showJoinSheet = true },
                icon = { Icon(Icons.Default.GroupAdd, contentDescription = stringResource(R.string.home_join_group)) },
                text = { Text(stringResource(R.string.home_join_group)) },
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
            ExtendedFloatingActionButton(
                onClick = { navController.navigate(TrevioRoute.CreateGroup.route) },
                icon = { Icon(Icons.Default.Add, contentDescription = stringResource(R.string.home_new_group)) },
                text = { Text(stringResource(R.string.home_new_group)) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    }

    if (showJoinSheet) {
        JoinGroupSheet(
            onDismiss = { showJoinSheet = false },
            onNavigateToLogin = {
                navController.navigate(TrevioRoute.Login.route) {
                    popUpTo(TrevioRoute.Home.route) { inclusive = true }
                }
            },
            onNavigateToTerms = {
                navController.navigate(TrevioRoute.Terms.route) {
                    popUpTo(TrevioRoute.Home.route) { inclusive = true }
                }
            },
            onJoined = { viewModel.loadGroups() }
        )
    }
}

@Composable
private fun HomeHeader(
    displayName: String,
    onNotificationsClick: () -> Unit,
    isLoading: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.home_welcome_back),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(2.dp))
            if (isLoading && displayName.isEmpty()) {
                // Shimmer placeholder for the name while data loads,
                // instead of showing "there" on first login.
                Box(
                    modifier = Modifier
                        .width(120.dp)
                        .height(24.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White.copy(alpha = 0.2f))
                )
            } else {
                Text(
                    text = displayName.ifEmpty { stringResource(R.string.home_welcome_back) },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        IconButton(onClick = onNotificationsClick) {
            Icon(Icons.Outlined.Notifications, contentDescription = stringResource(R.string.home_notifications), tint = Color.White)
        }
    }
}

@Composable
private fun BalanceCard(
    totalOwed: Double,
    totalOwing: Double,
    netBalance: Double,
    totalExpenses: Double,
    activeGroups: Int,
    formatBase: (Double) -> String
) {
    val isDark = isSystemInDarkTheme()
    val greenColor = if (isDark) BalancePositiveDark else BalancePositive
    val redColor = if (isDark) BalanceNegativeDark else BalanceNegative
    val netColor = if (netBalance >= 0) greenColor else redColor

    TrevioCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.home_total_balance),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    color = netColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (netBalance >= 0) stringResource(R.string.home_youre_owed) else stringResource(R.string.home_you_owe),
                        style = MaterialTheme.typography.labelSmall,
                        color = netColor,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = formatBase(kotlin.math.abs(netBalance)),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = netColor
            )
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = if (isSystemInDarkTheme()) TrevioBorderDark else TrevioBorder)
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                BalanceColumn(
                    label = stringResource(R.string.home_youll_get),
                    amount = totalOwed,
                    color = greenColor,
                    formatBase = formatBase
                )
                BalanceColumn(
                    label = stringResource(R.string.home_youll_pay),
                    amount = totalOwing,
                    color = redColor,
                    formatBase = formatBase
                )
                BalanceColumn(
                    label = stringResource(R.string.home_total_spent),
                    amount = totalExpenses,
                    color = MaterialTheme.colorScheme.onSurface,
                    formatBase = formatBase
                )
            }
            if (activeGroups > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.home_across_groups, activeGroups, if (activeGroups == 1) stringResource(R.string.home_group) else stringResource(R.string.home_groups)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun BalanceColumn(
    label: String,
    amount: Double,
    color: Color,
    formatBase: (Double) -> String
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = formatBase(amount),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

private fun templateIcon(template: GroupTemplate): ImageVector = when (template) {
    GroupTemplate.TRIP -> Icons.Default.Flight
    GroupTemplate.TURF -> Icons.Default.SportsSoccer
    GroupTemplate.CASUAL -> Icons.Default.LocalCafe
    GroupTemplate.HOUSEHOLD -> Icons.Default.Home
}

@Composable
private fun templateColorAdaptive(template: GroupTemplate): Color {
    val isDark = isSystemInDarkTheme()
    return when (template) {
        GroupTemplate.TRIP -> if (isDark) TemplateTripDark else TemplateTrip
        GroupTemplate.TURF -> if (isDark) TemplateTurfDark else TemplateTurf
        GroupTemplate.CASUAL -> if (isDark) TemplateCasualDark else TemplateCasual
        GroupTemplate.HOUSEHOLD -> if (isDark) TemplateHouseholdDark else TemplateHousehold
    }
}

@Composable
private fun GroupCardItem(
    group: Group,
    onClick: () -> Unit,
    formatBase: (Double) -> String
) {
    val icon = templateIcon(group.template)
    val accentColor = templateColorAdaptive(group.template)
    val balanceColor = when {
        group.yourBalance > 0.01 -> if (isSystemInDarkTheme()) BalancePositiveDark else BalancePositive
        group.yourBalance < -0.01 -> if (isSystemInDarkTheme()) BalanceNegativeDark else BalanceNegative
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val balanceText = when {
        group.template == GroupTemplate.HOUSEHOLD -> {
            if (group.totalExpenses > 0) stringResource(R.string.group_item_spent, formatBase(group.totalExpenses)) else stringResource(R.string.group_item_no_entries)
        }
        group.yourBalance > 0.01 -> stringResource(R.string.group_item_owes_you, formatBase(group.yourBalance))
        group.yourBalance < -0.01 -> stringResource(R.string.group_item_you_owe, formatBase(-group.yourBalance))
        else -> stringResource(R.string.group_item_settled_up)
    }

    TrevioCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.home_members_total, group.memberCount, formatBase(group.totalExpenses)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = balanceText,
                    style = MaterialTheme.typography.labelSmall,
                    color = balanceColor,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (group.template != GroupTemplate.HOUSEHOLD) {
                Text(
                    text = formatBase(group.totalExpenses),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
