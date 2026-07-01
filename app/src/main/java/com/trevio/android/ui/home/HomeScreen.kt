package com.trevio.android.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trevio.android.core.designsystem.components.EmptyState
import com.trevio.android.core.designsystem.components.TrevioCard
import com.trevio.android.core.designsystem.theme.TrevioBorder

import com.trevio.android.core.navigation.TrevioRoute
import com.trevio.android.domain.model.Group
import com.trevio.android.domain.model.GroupTemplate
import com.trevio.android.domain.repository.AuthService
import com.trevio.android.domain.repository.GroupService
import com.trevio.android.util.rememberCurrencyFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val groupService: GroupService,
    private val authService: AuthService
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
        val error: String? = null,
        val signedOut: Boolean = false
    )

    private val _state = MutableStateFlow(HomeData())
    val state: StateFlow<HomeData> = _state

    init { loadGroups() }

    fun loadGroups() {
        _state.value = _state.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            val user = authService.getCurrentUser()
            groupService.getUserGroups()
                .onSuccess { groups ->
                    val totalOwed = groups.filter { it.yourBalance > 0 }.sumOf { it.yourBalance }
                    val totalOwing = groups.filter { it.yourBalance < 0 }.sumOf { -it.yourBalance }
                    val totalExpenses = groups.sumOf { it.totalExpenses }
                    val activeGroups = groups.count { !it.archived }
                    _state.value = HomeData(
                        groups = groups,
                        totalOwed = totalOwed,
                        totalOwing = totalOwing,
                        netBalance = totalOwed - totalOwing,
                        totalExpenses = totalExpenses,
                        activeGroups = activeGroups,
                        userDisplayName = user?.displayName ?: "",
                        isLoading = false
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = e.message,
                        userDisplayName = user?.displayName ?: ""
                    )
                }
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
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.loadGroups()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(state.signedOut) {
        if (state.signedOut) {
            onSignOut()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (state.isLoading) {
            Column(modifier = Modifier.fillMaxSize()) {
                HomeHeader(
                    displayName = state.userDisplayName,
                    onNotificationsClick = { navController.navigate(TrevioRoute.Notifications.route) }
                )
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
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
                        Text("Failed to load groups", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(errMsg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    title = "No groups yet",
                    message = "Create your first group to start splitting bills with friends. Perfect for trips, turf sessions, or casual splits!",
                    actionText = "Create Group",
                    onAction = { navController.navigate(TrevioRoute.CreateGroup.route) }
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                item {
                    HomeHeader(
                        displayName = state.userDisplayName,
                        onNotificationsClick = { navController.navigate(TrevioRoute.Notifications.route) }
                    )
                }

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
                            text = "Your Groups",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${state.groups.size} active",
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
                        Text("View All Groups")
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { navController.navigate(TrevioRoute.CreateGroup.route) },
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            text = { Text("New Group") },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        )
    }
}

@Composable
private fun HomeHeader(
    displayName: String,
    onNotificationsClick: () -> Unit
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
                text = "Welcome back,",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = displayName.ifEmpty { "there" },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onNotificationsClick) {
            Icon(Icons.Outlined.Notifications, contentDescription = "Notifications", tint = Color.White)
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
    val netColor = if (netBalance >= 0) Color(0xFF22C55E) else Color(0xFFEF4444)

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
                    text = "Total Balance",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    color = netColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (netBalance >= 0) "you're owed" else "you owe",
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
            HorizontalDivider(color = TrevioBorder)
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                BalanceColumn(
                    label = "You'll get",
                    amount = totalOwed,
                    color = Color(0xFF22C55E),
                    formatBase = formatBase
                )
                BalanceColumn(
                    label = "You'll pay",
                    amount = totalOwing,
                    color = Color(0xFFEF4444),
                    formatBase = formatBase
                )
                BalanceColumn(
                    label = "Total Spent",
                    amount = totalExpenses,
                    color = MaterialTheme.colorScheme.onSurface,
                    formatBase = formatBase
                )
            }
            if (activeGroups > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Across $activeGroups active ${if (activeGroups == 1) "group" else "groups"}",
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
}

private fun templateColor(template: GroupTemplate): Color = when (template) {
    GroupTemplate.TRIP -> Color(0xFF6366F1)
    GroupTemplate.TURF -> Color(0xFF22C55E)
    GroupTemplate.CASUAL -> Color(0xFFF59E0B)
}

@Composable
private fun GroupCardItem(
    group: Group,
    onClick: () -> Unit,
    formatBase: (Double) -> String
) {
    val icon = templateIcon(group.template)
    val accentColor = templateColor(group.template)
    val balanceColor = when {
        group.yourBalance > 0.01 -> Color(0xFF22C55E)
        group.yourBalance < -0.01 -> Color(0xFFEF4444)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val balanceText = when {
        group.yourBalance > 0.01 -> "you'll get ${formatBase(group.yourBalance)}"
        group.yourBalance < -0.01 -> "you'll pay ${formatBase(-group.yourBalance)}"
        else -> "settled up"
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
                    text = "${group.memberCount} members · ${formatBase(group.totalExpenses)} total",
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
            Text(
                text = formatBase(group.totalExpenses),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
