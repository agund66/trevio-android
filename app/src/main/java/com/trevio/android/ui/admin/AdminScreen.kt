package com.trevio.android.ui.admin

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trevio.android.R
import com.trevio.android.core.designsystem.components.LoadingIndicator
import com.trevio.android.core.designsystem.components.TrevioCard
import com.trevio.android.core.designsystem.theme.*
import com.trevio.android.domain.model.User
import com.trevio.android.domain.repository.AdminService
import com.trevio.android.domain.repository.AuthService
import com.trevio.android.util.toStringResId
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AdminViewModel @Inject constructor(
    private val adminService: AdminService,
    private val authService: AuthService
) : ViewModel() {

    data class AdminState(
        val isLoading: Boolean = true,
        val isCheckingAdmin: Boolean = true,
        val isSuperadmin: Boolean = false,
        val users: List<User> = emptyList(),
        val usersHasMore: Boolean = false,
        val usersLoadingMore: Boolean = false,
        @StringRes val error: Int? = null,
        val actionLoading: String? = null,
        val currentUid: String? = null,
        val selectedTab: Int = 0
    )

    private val _state = MutableStateFlow(AdminState())
    val state: StateFlow<AdminState> = _state

    init {
        viewModelScope.launch {
            val user = authService.getCurrentUser()
            if (user == null) {
                _state.value = _state.value.copy(isCheckingAdmin = false, isLoading = false, error = R.string.admin_not_authenticated)
                return@launch
            }
            if (user.role != "superadmin") {
                _state.value = _state.value.copy(isCheckingAdmin = false, isLoading = false, error = R.string.error_access_denied_superadmin)
                return@launch
            }
            val currentUid = authService.getCurrentUserId()
            _state.value = _state.value.copy(isCheckingAdmin = false, isSuperadmin = true, currentUid = currentUid)
            loadUsers()
        }
    }

    fun selectTab(index: Int) {
        _state.value = _state.value.copy(selectedTab = index)
    }

    fun loadUsers() {
        _state.value = _state.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            adminService.getAllUsers(50, null)
                .onSuccess { result -> _state.value = _state.value.copy(isLoading = false, users = result.items, usersHasMore = result.hasMore) }
                .onFailure { e -> _state.value = _state.value.copy(isLoading = false, error = e.toStringResId()) }
        }
    }

    fun loadMoreUsers() {
        if (!_state.value.usersHasMore || _state.value.usersLoadingMore) return
        _state.value = _state.value.copy(usersLoadingMore = true)
        val lastId = _state.value.users.lastOrNull()?.uid
        viewModelScope.launch {
            adminService.getAllUsers(50, lastId)
                .onSuccess { result ->
                    _state.value = _state.value.copy(
                        users = _state.value.users + result.items,
                        usersLoadingMore = false,
                        usersHasMore = result.hasMore
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(usersLoadingMore = false)
                }
        }
    }

    fun blockUser(uid: String) {
        _state.value = _state.value.copy(actionLoading = uid)
        viewModelScope.launch {
            adminService.blockUser(uid)
                .onSuccess { loadUsers() }
                .onFailure { e -> _state.value = _state.value.copy(error = e.toStringResId()) }
            _state.value = _state.value.copy(actionLoading = null)
        }
    }

    fun unblockUser(uid: String) {
        _state.value = _state.value.copy(actionLoading = uid)
        viewModelScope.launch {
            adminService.unblockUser(uid)
                .onSuccess { loadUsers() }
                .onFailure { e -> _state.value = _state.value.copy(error = e.toStringResId()) }
            _state.value = _state.value.copy(actionLoading = null)
        }
    }

    fun promoteUser(uid: String) {
        _state.value = _state.value.copy(actionLoading = uid)
        viewModelScope.launch {
            adminService.promoteToSuperAdmin(uid)
                .onSuccess { loadUsers() }
                .onFailure { e -> _state.value = _state.value.copy(error = e.toStringResId()) }
            _state.value = _state.value.copy(actionLoading = null)
        }
    }

    fun demoteUser(uid: String) {
        _state.value = _state.value.copy(actionLoading = uid)
        viewModelScope.launch {
            adminService.demoteToUser(uid)
                .onSuccess { loadUsers() }
                .onFailure { e -> _state.value = _state.value.copy(error = e.toStringResId()) }
            _state.value = _state.value.copy(actionLoading = null)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    navController: androidx.navigation.NavHostController,
    viewModel: AdminViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val tabs = listOf(
        stringResource(R.string.admin_users) to Icons.Default.People,
        stringResource(R.string.admin_broadcasts) to Icons.Default.Campaign,
        stringResource(R.string.admin_support) to Icons.Default.Support,
        stringResource(R.string.admin_reminders) to Icons.Default.Notifications
    )

    if (state.isCheckingAdmin) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (!state.isSuperadmin) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.admin_access_denied), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.admin_superadmin_only), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
            OutlinedButton(onClick = { navController.popBackStack() }) {
                Text(stringResource(R.string.admin_go_back))
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primary)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back), tint = Color.White)
                }
                Text(
                    stringResource(R.string.admin_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            TabRow(
                selectedTabIndex = state.selectedTab,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White
            ) {
                tabs.forEachIndexed { index, (label, icon) ->
                    Tab(
                        selected = state.selectedTab == index,
                        onClick = { viewModel.selectTab(index) },
                        text = { Text(label) },
                        icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }
            }
        }

        if (state.selectedTab == 1) {
            BroadcastsScreen()
        } else if (state.selectedTab == 2) {
            AdminSupportScreen()
        } else if (state.selectedTab == 3) {
            RemindersScreen()
        } else if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (state.error != null) {
            Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(state.error!!),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            var searchQuery by remember { mutableStateOf("") }
            val totalUsers = state.users.size
            val blockedUsers = state.users.count { it.blocked }
            val adminUsers = state.users.count { it.role == "superadmin" }
            val currentUid = state.currentUid

            val filteredUsers = remember(state.users, searchQuery) {
                if (searchQuery.isBlank()) state.users
                else state.users.filter {
                    it.displayName.contains(searchQuery, ignoreCase = true) ||
                    it.email.contains(searchQuery, ignoreCase = true) ||
                    (it.username?.contains(searchQuery, ignoreCase = true) ?: false)
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatCard(stringResource(R.string.admin_users), totalUsers.toString(), MaterialTheme.colorScheme.onSurface)
                        StatCard(stringResource(R.string.admin_blocked), blockedUsers.toString(), if (isSystemInDarkTheme()) BalanceNegativeDark else BalanceNegative)
                        StatCard(stringResource(R.string.admin_superadmin), adminUsers.toString(), MaterialTheme.colorScheme.primary)
                    }
                }

                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text(stringResource(R.string.admin_search_placeholder)) },
                        leadingIcon = { Icon(Icons.Default.Search, stringResource(R.string.common_search)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                items(filteredUsers, key = { it.uid }) { user ->
                    UserRow(
                        user = user,
                        isCurrentUser = user.uid == currentUid,
                        actionLoading = state.actionLoading == user.uid,
                        onBlock = { viewModel.blockUser(user.uid) },
                        onUnblock = { viewModel.unblockUser(user.uid) },
                        onPromote = { viewModel.promoteUser(user.uid) },
                        onDemote = { viewModel.demoteUser(user.uid) }
                    )
                }
                if (state.usersHasMore) {
                    item {
                        LaunchedEffect(state.users.lastOrNull()?.uid) {
                            viewModel.loadMoreUsers()
                        }
                        if (state.usersLoadingMore) {
                            LoadingIndicator(modifier = Modifier.fillMaxWidth().padding(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.StatCard(label: String, value: String, valueColor: Color) {
    TrevioCard(modifier = Modifier.weight(1f)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = valueColor)
        }
    }
}

@Composable
private fun UserRow(
    user: User,
    isCurrentUser: Boolean,
    actionLoading: Boolean,
    onBlock: () -> Unit,
    onUnblock: () -> Unit,
    onPromote: () -> Unit,
    onDemote: () -> Unit
) {
    var pendingAction by remember { mutableStateOf<String?>(null) }

    pendingAction?.let { action ->
        val (title, message) = when (action) {
            "block" -> stringResource(R.string.admin_block) to stringResource(R.string.admin_block_confirm, user.displayName)
            "unblock" -> stringResource(R.string.admin_unblock) to stringResource(R.string.admin_unblock_confirm, user.displayName)
            "promote" -> stringResource(R.string.admin_promote) to stringResource(R.string.admin_promote_confirm, user.displayName)
            "demote" -> stringResource(R.string.admin_demote) to stringResource(R.string.admin_demote_confirm, user.displayName)
            else -> "" to ""
        }
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = { Text(title) },
            text = { Text(message) },
            confirmButton = {
                Button(onClick = {
                    when (action) {
                        "block" -> onBlock()
                        "unblock" -> onUnblock()
                        "promote" -> onPromote()
                        "demote" -> onDemote()
                    }
                    pendingAction = null
                }) { Text(stringResource(R.string.common_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingAction = null }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    TrevioCard {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (user.photoURL.isNotEmpty()) {
                    coil.compose.AsyncImage(
                        model = user.photoURL,
                        contentDescription = user.displayName,
                        modifier = Modifier.size(40.dp).clip(CircleShape)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            user.displayName.firstOrNull()?.uppercase() ?: "?",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            user.displayName + if (isCurrentUser) stringResource(R.string.group_detail_you_suffix) else "",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (user.role == "superadmin") {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    stringResource(R.string.admin_superadmin),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        if (user.blocked) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = if (isSystemInDarkTheme()) BalanceNegativeDark else BalanceNegative.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    stringResource(R.string.admin_blocked),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSystemInDarkTheme()) BalanceNegativeDark else BalanceNegative,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        user.email,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (user.blocked) {
                    OutlinedButton(
                        onClick = { pendingAction = "unblock" },
                        enabled = !actionLoading,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.admin_unblock))
                    }
                } else {
                    OutlinedButton(
                        onClick = { pendingAction = "block" },
                        enabled = !actionLoading && !isCurrentUser,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.admin_block))
                    }
                }
                if (user.role == "superadmin") {
                    OutlinedButton(
                        onClick = { pendingAction = "demote" },
                        enabled = !actionLoading && !isCurrentUser,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.admin_demote))
                    }
                } else {
                    Button(
                        onClick = { pendingAction = "promote" },
                        enabled = !actionLoading,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.admin_promote))
                    }
                }
            }
        }
    }
}
