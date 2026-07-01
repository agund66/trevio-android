package com.trevio.android.ui.group

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sports
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trevio.android.core.designsystem.components.MemberAvatar
import com.trevio.android.core.designsystem.components.TrevioHeader
import com.trevio.android.core.navigation.TrevioRoute
import com.trevio.android.domain.model.GroupTemplate
import com.trevio.android.domain.model.UserSearchResult
import com.trevio.android.domain.repository.GroupService
import com.trevio.android.domain.repository.UserService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CreateGroupViewModel @Inject constructor(
    private val groupService: GroupService,
    private val userService: UserService
) : ViewModel() {

    data class CreateState(
        val isLoading: Boolean = false,
        val error: String? = null,
        val searchResults: List<UserSearchResult> = emptyList(),
        val selectedMembers: List<UserSearchResult> = emptyList(),
        val inviteCode: String? = null,
        val createdGroupId: String? = null
    )

    private val _state = MutableStateFlow(CreateState())
    val state: StateFlow<CreateState> = _state

    fun searchUsers(query: String) {
        if (query.isBlank()) {
            _state.value = _state.value.copy(searchResults = emptyList())
            return
        }
        viewModelScope.launch {
            userService.searchUsers(query)
                .onSuccess { results ->
                    _state.value = _state.value.copy(
                        searchResults = results.filter { r ->
                            _state.value.selectedMembers.none { it.uid == r.uid }
                        }
                    )
                }
        }
    }

    fun addMember(user: UserSearchResult) {
        _state.value = _state.value.copy(
            selectedMembers = _state.value.selectedMembers + user,
            searchResults = _state.value.searchResults.filter { it.uid != user.uid }
        )
    }

    fun removeMember(user: UserSearchResult) {
        _state.value = _state.value.copy(
            selectedMembers = _state.value.selectedMembers.filter { it.uid != user.uid }
        )
    }

    fun createGroup(name: String, description: String, template: GroupTemplate) {
        _state.value = _state.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            groupService.createGroup(
                name = name,
                description = description,
                template = template,
                memberUids = _state.value.selectedMembers.map { it.uid }
            ).onSuccess { (groupId, inviteCode) ->
                _state.value = _state.value.copy(
                    isLoading = false,
                    createdGroupId = groupId,
                    inviteCode = inviteCode
                )
            }.onFailure { e ->
                _state.value = _state.value.copy(isLoading = false, error = e.message)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGroupScreen(
    navController: androidx.navigation.NavHostController,
    viewModel: CreateGroupViewModel = hiltViewModel()
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedTemplate by remember { mutableStateOf(GroupTemplate.CASUAL) }
    var searchQuery by remember { mutableStateOf("") }
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.createdGroupId) {
        if (state.createdGroupId != null) {
            navController.popBackStack(TrevioRoute.Home.route, inclusive = false)
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TrevioHeader(
            title = "Create Group",
            onBack = { navController.popBackStack() }
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            SectionLabel("Choose a template")
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TemplateCard(
                    icon = Icons.Default.TravelExplore,
                    title = "Trip",
                    iconColor = Color(0xFF6366F1),
                    selected = selectedTemplate == GroupTemplate.TRIP,
                    onClick = { selectedTemplate = GroupTemplate.TRIP },
                    modifier = Modifier.weight(1f)
                )
                TemplateCard(
                    icon = Icons.Default.Sports,
                    title = "Turf",
                    iconColor = Color(0xFF22C55E),
                    selected = selectedTemplate == GroupTemplate.TURF,
                    onClick = { selectedTemplate = GroupTemplate.TURF },
                    modifier = Modifier.weight(1f)
                )
                TemplateCard(
                    icon = Icons.Default.Group,
                    title = "Casual",
                    iconColor = Color(0xFFF59E0B),
                    selected = selectedTemplate == GroupTemplate.CASUAL,
                    onClick = { selectedTemplate = GroupTemplate.CASUAL },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Group name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description (optional)") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2,
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            SectionLabel("Add members")
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it; viewModel.searchUsers(it) },
                label = { Text("Search by username") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            if (state.searchResults.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column {
                        state.searchResults.forEach { user ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                                    .selectable(selected = false, onClick = { viewModel.addMember(user) }),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                MemberAvatar(name = user.displayName, photoURL = user.photoURL, size = 36)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(user.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    Text("@${user.username}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.PersonAdd, contentDescription = "Add", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }

            if (state.selectedMembers.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text("Selected (${state.selectedMembers.size})", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                state.selectedMembers.forEach { user ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        shape = RoundedCornerShape(10.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MemberAvatar(name = user.displayName, photoURL = user.photoURL, size = 32)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(user.displayName, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            TextButton(onClick = { viewModel.removeMember(user) }) { Text("Remove") }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (state.error != null) {
                Text(state.error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))
            }

            Button(
                onClick = { viewModel.createGroup(name, description, selectedTemplate) },
                enabled = name.isNotBlank() && !state.isLoading,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                } else {
                    Text("Create Group", style = MaterialTheme.typography.titleMedium)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun TemplateCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    iconColor: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Card(
        onClick = onClick,
        modifier = modifier,
        border = CardDefaults.outlinedCardBorder(true),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 2.dp else 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = if (selected) 0.15f else 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
        }
    }
}
