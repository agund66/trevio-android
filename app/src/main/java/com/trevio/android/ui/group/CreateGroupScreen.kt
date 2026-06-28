package com.trevio.android.ui.group

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trevio.android.core.designsystem.components.MemberAvatar
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

    fun createGroup(name: String, description: String, template: GroupTemplate, currency: String) {
        _state.value = _state.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            groupService.createGroup(
                name = name,
                description = description,
                template = template,
                currency = currency,
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
    var currency by remember { mutableStateOf("INR") }
    var searchQuery by remember { mutableStateOf("") }
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.createdGroupId) {
        if (state.createdGroupId != null) {
            navController.navigate(TrevioRoute.Home.route) {
                popUpTo(TrevioRoute.Home.route) { inclusive = true }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create Group") },
                navigationIcon = {
                    TextButton(onClick = { navController.popBackStack() }) {
                        Text("Cancel")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text("Choose a template", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TemplateCard(
                    icon = Icons.Default.TravelExplore,
                    title = "Trip",
                    selected = selectedTemplate == GroupTemplate.TRIP,
                    onClick = { selectedTemplate = GroupTemplate.TRIP },
                    modifier = Modifier.weight(1f)
                )
                TemplateCard(
                    icon = Icons.Default.Sports,
                    title = "Turf",
                    selected = selectedTemplate == GroupTemplate.TURF,
                    onClick = { selectedTemplate = GroupTemplate.TURF },
                    modifier = Modifier.weight(1f)
                )
                TemplateCard(
                    icon = Icons.Default.Group,
                    title = "Casual",
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
                singleLine = true
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description (optional)") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2
            )
            Spacer(modifier = Modifier.height(12.dp))

            Text("Add members", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it; viewModel.searchUsers(it) },
                label = { Text("Search by username") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            if (state.searchResults.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
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
                                Icon(Icons.Default.PersonAdd, contentDescription = "Add", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }

            if (state.selectedMembers.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text("Selected (${state.selectedMembers.size})", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                state.selectedMembers.forEach { user ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MemberAvatar(name = user.displayName, photoURL = user.photoURL, size = 32)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(user.displayName, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { viewModel.removeMember(user) }) { Text("Remove") }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            if (state.error != null) {
                Text(state.error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))
            }

            Button(
                onClick = { viewModel.createGroup(name, description, selectedTemplate, currency) },
                enabled = name.isNotBlank() && !state.isLoading,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                } else {
                    Text("Create Group", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun TemplateCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
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
            containerColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
        }
    }
}
