package com.trevio.android.ui.group

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Warning
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
import com.trevio.android.core.designsystem.components.TrevioHeader
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
import com.trevio.android.core.navigation.TrevioRoute
import com.trevio.android.data.remote.FirestoreObservers
import com.trevio.android.domain.model.Group
import com.trevio.android.domain.model.GroupTemplate
import com.trevio.android.domain.repository.GroupService
import com.trevio.android.util.rememberCurrencyFormatter
import com.trevio.android.util.toStringResId
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GroupsListViewModel @Inject constructor(
    private val groupService: GroupService,
    private val userRefreshNotifier: UserRefreshNotifier,
    private val firestoreObservers: FirestoreObservers
) : ViewModel() {

    data class GroupsListState(
        val groups: List<Group> = emptyList(),
        val isLoading: Boolean = true,
        @StringRes val error: Int? = null
    )

    private val _state = MutableStateFlow(GroupsListState())
    val state: StateFlow<GroupsListState> = _state

    /// Tracks the current groups listener so repeated loadGroups()
    /// calls don't create multiple Firestore listeners.
    private var groupsListenerJob: kotlinx.coroutines.Job? = null

    init {
        loadGroups()
        viewModelScope.launch {
            userRefreshNotifier.userRefreshed.collect {
                // Listener auto-updates; refresh is a no-op but kept
                // for API compatibility with the UserRefreshNotifier pattern.
            }
        }
    }

    /**
     * Starts a real-time listener for the user's groups.  With offline
     * persistence enabled, the first emission comes from cache (instant)
     * and subsequent emissions arrive from the server silently.
     */
    fun loadGroups() {
        _state.value = _state.value.copy(isLoading = true)
        // Cancel any existing listener before starting a new one
        // to prevent duplicate Firestore listeners.
        groupsListenerJob?.cancel()
        groupsListenerJob = viewModelScope.launch {
            try {
                firestoreObservers.observeUserGroups().collect { groups ->
                    _state.value = GroupsListState(groups = groups, isLoading = false)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = GroupsListState(isLoading = false, error = e.toStringResId())
            }
        }
    }

    /**
     * Groups are kept fresh by the real-time listener.  This method
     * is kept for API compatibility (called from needsRefresh and
     * JoinGroupSheet callbacks).
     */
    fun refreshGroups() {
        // No-op: the listener in loadGroups() handles updates automatically.
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsListScreen(
    navController: androidx.navigation.NavHostController,
    viewModel: GroupsListViewModel = hiltViewModel()
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

    // Only show skeletons on a true cold start (no cached data).
    // With real-time listeners + offline persistence, repeat visits
    // emit cached data instantly — skeletons are barely visible.
    if (state.isLoading && state.groups.isEmpty() && state.error == null) {
        Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            TrevioHeader(title = stringResource(R.string.groups_title))
            repeat(5) {
                ListItemSkeleton()
                Spacer(Modifier.height(8.dp))
            }
        }
        return
    }

    state.error?.let { errMsg ->
        Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            TrevioHeader(title = stringResource(R.string.groups_title))
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(stringResource(R.string.groups_failed_to_load), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(stringResource(errMsg), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            TrevioHeader(title = stringResource(R.string.groups_title))

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
            if (state.groups.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.Group,
                        title = stringResource(R.string.groups_no_groups_yet),
                        message = stringResource(R.string.groups_create_message),
                        actionText = stringResource(R.string.groups_create_group),
                        onAction = { navController.navigate(TrevioRoute.CreateGroup.route) }
                    )
                }
            } else {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.groups_active_count, state.groups.size),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                items(state.groups, key = { it.groupId }) { group ->
                    GroupsListItem(
                        group = group,
                        onClick = {
                            navController.navigate(TrevioRoute.GroupDetail.createRoute(group.groupId))
                        },
                        formatBase = currencyFormatter.formatBase
                    )
                }
            }
            }
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
                icon = { Icon(Icons.Default.GroupAdd, contentDescription = stringResource(R.string.groups_join_group_desc)) },
                text = { Text(stringResource(R.string.groups_join_group)) },
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
            ExtendedFloatingActionButton(
                onClick = { navController.navigate(TrevioRoute.CreateGroup.route) },
                icon = { Icon(Icons.Default.Add, contentDescription = stringResource(R.string.groups_new_group_desc)) },
                text = { Text(stringResource(R.string.groups_new_group)) },
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
private fun GroupsListItem(
    group: Group,
    onClick: () -> Unit,
    formatBase: (Double) -> String
) {
    val balance = group.yourBalance
    val isDark = isSystemInDarkTheme()
    val accentColor = when (group.template) {
        GroupTemplate.TRIP -> if (isDark) TemplateTripDark else TemplateTrip
        GroupTemplate.TURF -> if (isDark) TemplateTurfDark else TemplateTurf
        GroupTemplate.CASUAL -> if (isDark) TemplateCasualDark else TemplateCasual
        GroupTemplate.HOUSEHOLD -> if (isDark) TemplateHouseholdDark else TemplateHousehold
    }
    val balanceColor = if (balance > 0) if (isDark) BalancePositiveDark else BalancePositive else if (balance < 0) if (isDark) BalanceNegativeDark else BalanceNegative else MaterialTheme.colorScheme.onSurfaceVariant
    val balanceText = when {
        group.template == GroupTemplate.HOUSEHOLD -> {
            if (group.totalExpenses > 0) stringResource(R.string.group_item_spent, formatBase(group.totalExpenses)) else stringResource(R.string.group_item_no_entries)
        }
        balance > 0 -> stringResource(R.string.group_item_owes_you, formatBase(balance))
        balance < 0 -> stringResource(R.string.group_item_you_owe, formatBase(-balance))
        else -> stringResource(R.string.group_item_settled_up)
    }

    TrevioCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accentColor.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = templateIcon(group.template),
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = group.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (group.archived) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                stringResource(R.string.group_detail_archived),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.group_detail_members_count, group.memberCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = balanceText,
                style = MaterialTheme.typography.labelMedium,
                color = balanceColor,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private fun templateIcon(template: GroupTemplate): ImageVector = when (template) {
    GroupTemplate.TRIP -> Icons.Default.Flight
    GroupTemplate.TURF -> Icons.Default.SportsSoccer
    GroupTemplate.CASUAL -> Icons.Default.LocalCafe
    GroupTemplate.HOUSEHOLD -> Icons.Default.Home
}
