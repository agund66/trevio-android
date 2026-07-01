package com.trevio.android.ui.group

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Group
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
import com.trevio.android.core.designsystem.components.TrevioHeader
import com.trevio.android.core.designsystem.theme.TrevioBorder
import com.trevio.android.core.navigation.TrevioRoute
import com.trevio.android.domain.model.Group
import com.trevio.android.domain.model.GroupTemplate
import com.trevio.android.domain.repository.GroupService
import com.trevio.android.util.rememberCurrencyFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GroupsListViewModel @Inject constructor(
    private val groupService: GroupService
) : ViewModel() {

    data class GroupsListState(
        val groups: List<Group> = emptyList(),
        val isLoading: Boolean = true,
        val error: String? = null
    )

    private val _state = MutableStateFlow(GroupsListState())
    val state: StateFlow<GroupsListState> = _state

    init { loadGroups() }

    fun loadGroups() {
        _state.value = _state.value.copy(isLoading = true)
        viewModelScope.launch {
            groupService.getUserGroups()
                .onSuccess { groups ->
                    _state.value = GroupsListState(groups = groups, isLoading = false)
                }
                .onFailure { e ->
                    _state.value = GroupsListState(isLoading = false, error = e.message)
                }
        }
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

    if (state.isLoading) {
        Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            TrevioHeader(title = "Groups")
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        return
    }

    state.error?.let { errMsg ->
        Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            TrevioHeader(title = "Groups")
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
        return
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            item {
                TrevioHeader(title = "Groups")
            }

            if (state.groups.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.Group,
                        title = "No groups yet",
                        message = "Create your first group to start splitting bills with friends.",
                        actionText = "Create Group",
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
                            text = "${state.groups.size} active",
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
private fun GroupsListItem(
    group: Group,
    onClick: () -> Unit,
    formatBase: (Double) -> String
) {
    val balance = group.yourBalance
    val balanceColor = if (balance > 0) Color(0xFF22C55E) else if (balance < 0) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurfaceVariant
    val balanceText = if (balance > 0) "owes you ${formatBase(balance)}" else if (balance < 0) "you owe ${formatBase(-balance)}" else "settled up"

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
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = templateIcon(group.template),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
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
                                "Archived",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Text(
                    text = "${group.memberCount} members",
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
}
