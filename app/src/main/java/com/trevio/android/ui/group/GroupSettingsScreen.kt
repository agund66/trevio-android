package com.trevio.android.ui.group

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trevio.android.core.designsystem.components.MemberAvatar
import com.trevio.android.core.designsystem.components.TrevioHeader
import com.trevio.android.core.navigation.TrevioRoute
import com.trevio.android.domain.repository.AuthService
import com.trevio.android.domain.repository.GroupInfo
import com.trevio.android.domain.repository.GroupService
import com.trevio.android.domain.repository.SettlementService
import com.trevio.android.domain.model.Member
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GroupSettingsViewModel @Inject constructor(
    private val groupService: GroupService,
    private val settlementService: SettlementService,
    private val authService: AuthService,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val groupId: String = savedStateHandle.get<String>("groupId") ?: ""

    data class SettingsState(
        val isLoading: Boolean = true,
        val groupInfo: GroupInfo? = null,
        val members: List<Member> = emptyList(),
        val currentUserId: String? = null,
        val name: String = "",
        val description: String = "",
        val error: String? = null,
        val success: String? = null,
        val transferTargetUid: String? = null,
        val showDeleteConfirm: Boolean = false,
        val showLeaveConfirm: Boolean = false,
        val isSaving: Boolean = false
    )

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state

    init { loadData() }

    fun loadData() {
        _state.value = _state.value.copy(isLoading = true)
        viewModelScope.launch {
            val uid = authService.getCurrentUserId()
            val info = groupService.getGroupInfo(groupId).getOrNull()
            val members = settlementService.getGroupBalances(groupId).getOrDefault(emptyList())
            _state.value = _state.value.copy(
                isLoading = false,
                groupInfo = info,
                members = members,
                currentUserId = uid,
                name = info?.name ?: "",
                description = info?.description ?: ""
            )
        }
    }

    fun refreshData() {
        viewModelScope.launch {
            val uid = authService.getCurrentUserId()
            val info = groupService.getGroupInfo(groupId).getOrNull()
            val members = settlementService.getGroupBalances(groupId).getOrDefault(emptyList())
            _state.value = _state.value.copy(
                groupInfo = info,
                members = members,
                currentUserId = uid,
                name = info?.name ?: "",
                description = info?.description ?: ""
            )
        }
    }

    fun updateName(v: String) { _state.value = _state.value.copy(name = v) }
    fun updateDescription(v: String) { _state.value = _state.value.copy(description = v) }
    fun setTransferTarget(uid: String?) { _state.value = _state.value.copy(transferTargetUid = uid) }
    fun setShowDeleteConfirm(v: Boolean) { _state.value = _state.value.copy(showDeleteConfirm = v) }
    fun setShowLeaveConfirm(v: Boolean) { _state.value = _state.value.copy(showLeaveConfirm = v) }

    val isAdmin: Boolean get() = _state.value.currentUserId == _state.value.groupInfo?.createdBy ||
        _state.value.members.find { it.uid == _state.value.currentUserId }?.role == "admin"

    fun saveGroupSettings() {
        val s = _state.value
        _state.value = s.copy(isSaving = true, error = null, success = null)
        viewModelScope.launch {
            groupService.updateGroup(groupId, s.name, s.description)
                .onSuccess {
                    _state.value = s.copy(isSaving = false, success = "Group settings updated")
                    refreshData()
                }
                .onFailure { e ->
                    _state.value = s.copy(isSaving = false, error = e.message)
                }
        }
    }

    fun transferAdmin() {
        val s = _state.value
        val targetUid = s.transferTargetUid ?: return
        _state.value = s.copy(isSaving = true, error = null, success = null)
        viewModelScope.launch {
            groupService.transferAdminRole(groupId, targetUid)
                .onSuccess {
                    _state.value = s.copy(isSaving = false, success = "Admin role transferred", transferTargetUid = null)
                    refreshData()
                }
                .onFailure { e ->
                    _state.value = s.copy(isSaving = false, error = e.message)
                }
        }
    }

    fun deleteGroup() {
        val s = _state.value
        _state.value = s.copy(isSaving = true, error = null)
        viewModelScope.launch {
            groupService.deleteGroup(groupId)
                .onSuccess {
                    _state.value = s.copy(isSaving = false, showDeleteConfirm = false, groupInfo = null)
                }
                .onFailure { e ->
                    _state.value = s.copy(isSaving = false, error = e.message, showDeleteConfirm = false)
                }
        }
    }

    fun leaveGroup() {
        val s = _state.value
        _state.value = s.copy(isSaving = true, error = null)
        viewModelScope.launch {
            groupService.leaveGroup(groupId)
                .onSuccess {
                    _state.value = s.copy(isSaving = false, showLeaveConfirm = false, groupInfo = null)
                }
                .onFailure { e ->
                    _state.value = s.copy(isSaving = false, error = e.message, showLeaveConfirm = false)
                }
        }
    }
}

@Composable
fun GroupSettingsScreen(
    navController: androidx.navigation.NavHostController,
    viewModel: GroupSettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    if (state.isLoading) {
        Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            TrevioHeader(
                title = "Group Settings",
                onBack = { navController.popBackStack() }
            )
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        return
    }

    if (!viewModel.isAdmin) {
        Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            TrevioHeader(
                title = "Group Settings",
                onBack = { navController.popBackStack() }
            )
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Only group admins can access group settings.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        return
    }

    val activeMembers = state.members.filter { it.status == "active" && it.uid != state.currentUserId }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TrevioHeader(
            title = "Group Settings",
            onBack = { navController.popBackStack() }
        )
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (state.error != null) {
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(state.error!!, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
            if (state.success != null) {
                val isDark = isSystemInDarkTheme()
                val successColor = if (isDark) Color(0xFF4ADE80) else Color(0xFF22C55E)
                val successTextColor = if (isDark) Color(0xFF86EFAC) else Color(0xFF16A34A)
                Surface(color = successColor.copy(alpha = 0.12f), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(state.success!!, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, color = successTextColor)
                }
            }

            // Group Details Section
            Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Group Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = state.name,
                        onValueChange = { viewModel.updateName(it) },
                        label = { Text("Group Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = state.description,
                        onValueChange = { viewModel.updateDescription(it) },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                    Button(
                        onClick = { viewModel.saveGroupSettings() },
                        enabled = state.name.isNotBlank() && !state.isSaving,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (state.isSaving) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        else Text("Save Changes")
                    }
                }
            }

            // Transfer Admin Section
            Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Transfer Admin Role", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Transfer admin rights to another member. You will become a regular member.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (activeMembers.isNotEmpty()) {
                        activeMembers.forEach { m ->
                            Surface(
                                onClick = { viewModel.setTransferTarget(m.uid) },
                                shape = RoundedCornerShape(12.dp),
                                color = if (state.transferTargetUid == m.uid) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    MemberAvatar(name = m.displayName, photoURL = m.photoURL, size = 32)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(m.displayName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                    if (state.transferTargetUid == m.uid) Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                        if (state.transferTargetUid != null) {
                            OutlinedButton(
                                onClick = { viewModel.transferAdmin() },
                                enabled = !state.isSaving,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.AdminPanelSettings, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                if (state.isSaving) Text("Transferring...") else Text("Transfer Admin Role")
                            }
                        }
                    } else {
                        Text("No other active members to transfer admin role to.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    }
                }
            }

            // Danger Zone
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Danger Zone", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
                    Text("Delete this group permanently. All expenses, settlements, and activity will be removed.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f))
                    Text("Only works if you are the sole active member. Remove other members first.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
                    if (!state.showDeleteConfirm) {
                        OutlinedButton(
                            onClick = { viewModel.setShowDeleteConfirm(true) },
                            border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.error)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Delete Group")
                        }
                    } else {
                        Text("Are you absolutely sure? This cannot be undone.", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.error)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { viewModel.deleteGroup() },
                                enabled = !state.isSaving,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                if (state.isSaving) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                                else Text("Yes, Delete")
                            }
                            OutlinedButton(onClick = { viewModel.setShowDeleteConfirm(false) }) { Text("Cancel") }
                        }
                    }
                }
            }

            // Leave Group (non-admins only)
            if (!viewModel.isAdmin) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Leave Group", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.tertiary)
                        Text("You will no longer have access to this group's expenses and activity.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.8f))
                        if (!state.showLeaveConfirm) {
                            OutlinedButton(
                                onClick = { viewModel.setShowLeaveConfirm(true) },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.tertiary)
                            ) {
                                Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Leave Group")
                            }
                        } else {
                            Text("Are you sure you want to leave this group?", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.tertiary)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { viewModel.leaveGroup() },
                                    enabled = !state.isSaving,
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                                ) {
                                    if (state.isSaving) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                                    else Text("Yes, Leave")
                                }
                                OutlinedButton(onClick = { viewModel.setShowLeaveConfirm(false) }) { Text("Cancel") }
                            }
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(state.groupInfo) {
        if (state.groupInfo == null && !state.isLoading) {
            navController.popBackStack(TrevioRoute.Groups.route, inclusive = false)
        }
    }
}
