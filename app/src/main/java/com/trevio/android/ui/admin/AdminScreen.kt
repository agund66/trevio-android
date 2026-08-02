package com.trevio.android.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trevio.android.core.designsystem.components.TrevioCard
import com.trevio.android.core.designsystem.theme.TrevioBorder
import com.trevio.android.domain.model.User
import com.trevio.android.domain.repository.AdminService
import com.trevio.android.domain.repository.AuthService
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
        val users: List<User> = emptyList(),
        val error: String? = null,
        val actionLoading: String? = null,
        val currentUid: String? = null,
        val selectedTab: Int = 0
    )

    private val _state = MutableStateFlow(AdminState())
    val state: StateFlow<AdminState> = _state

    init {
        viewModelScope.launch {
            _state.value = _state.value.copy(currentUid = authService.getCurrentUserId())
            loadUsers()
        }
    }

    fun selectTab(index: Int) {
        _state.value = _state.value.copy(selectedTab = index)
    }

    fun loadUsers() {
        _state.value = _state.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            adminService.getAllUsers()
                .onSuccess { users -> _state.value = _state.value.copy(isLoading = false, users = users) }
                .onFailure { e -> _state.value = _state.value.copy(isLoading = false, error = e.message) }
        }
    }

    fun blockUser(uid: String) {
        _state.value = _state.value.copy(actionLoading = uid)
        viewModelScope.launch {
            adminService.blockUser(uid)
                .onSuccess { loadUsers() }
                .onFailure { e -> _state.value = _state.value.copy(error = e.message) }
            _state.value = _state.value.copy(actionLoading = null)
        }
    }

    fun unblockUser(uid: String) {
        _state.value = _state.value.copy(actionLoading = uid)
        viewModelScope.launch {
            adminService.unblockUser(uid)
                .onSuccess { loadUsers() }
                .onFailure { e -> _state.value = _state.value.copy(error = e.message) }
            _state.value = _state.value.copy(actionLoading = null)
        }
    }

    fun promoteUser(uid: String) {
        _state.value = _state.value.copy(actionLoading = uid)
        viewModelScope.launch {
            adminService.promoteToSuperAdmin(uid)
                .onSuccess { loadUsers() }
                .onFailure { e -> _state.value = _state.value.copy(error = e.message) }
            _state.value = _state.value.copy(actionLoading = null)
        }
    }

    fun demoteUser(uid: String) {
        _state.value = _state.value.copy(actionLoading = uid)
        viewModelScope.launch {
            adminService.demoteToUser(uid)
                .onSuccess { loadUsers() }
                .onFailure { e -> _state.value = _state.value.copy(error = e.message) }
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
    val tabs = listOf("Users" to Icons.Default.People, "Broadcasts" to Icons.Default.Campaign)

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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Text(
                    "Admin Dashboard",
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
        } else if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (state.error != null) {
            Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                Text(
                    state.error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            val totalUsers = state.users.size
            val blockedUsers = state.users.count { it.blocked }
            val adminUsers = state.users.count { it.role == "superadmin" }
            val currentUid = state.currentUid

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
                        StatCard("Total Users", totalUsers.toString(), MaterialTheme.colorScheme.onSurface)
                        StatCard("Blocked", blockedUsers.toString(), Color(0xFFEF4444))
                        StatCard("Admins", adminUsers.toString(), MaterialTheme.colorScheme.primary)
                    }
                }

                items(state.users) { user ->
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
                            user.displayName + if (isCurrentUser) " (You)" else "",
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
                                    "Admin",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        if (user.blocked) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = Color(0xFFEF4444).copy(alpha = 0.1f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    "Blocked",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFEF4444),
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
                        onClick = onUnblock,
                        enabled = !actionLoading,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Unblock")
                    }
                } else {
                    OutlinedButton(
                        onClick = onBlock,
                        enabled = !actionLoading && !isCurrentUser,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Block")
                    }
                }
                if (user.role == "superadmin") {
                    OutlinedButton(
                        onClick = onDemote,
                        enabled = !actionLoading && !isCurrentUser,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Demote")
                    }
                } else {
                    Button(
                        onClick = onPromote,
                        enabled = !actionLoading,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Promote")
                    }
                }
            }
        }
    }
}
