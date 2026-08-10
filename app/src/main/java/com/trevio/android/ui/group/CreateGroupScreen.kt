package com.trevio.android.ui.group

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trevio.android.core.designsystem.components.MemberAvatar
import com.trevio.android.core.designsystem.components.TrevioHeader
import com.trevio.android.core.navigation.TrevioRoute
import com.trevio.android.domain.model.GroupTemplate
import com.trevio.android.domain.model.UserSearchResult
import com.trevio.android.domain.repository.AuthService
import com.trevio.android.domain.repository.GroupService
import com.trevio.android.domain.repository.UserService
import com.trevio.android.util.CurrencyConverter
import com.trevio.android.util.rememberCurrencyFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CreateGroupViewModel @Inject constructor(
    private val groupService: GroupService,
    private val userService: UserService,
    private val authService: AuthService
) : ViewModel() {

    data class OfflineMember(
        val name: String
    )

    data class CreateState(
        val isLoading: Boolean = false,
        val error: String? = null,
        val searchResults: List<UserSearchResult> = emptyList(),
        val selectedMembers: List<UserSearchResult> = emptyList(),
        val offlineMembers: List<OfflineMember> = emptyList(),
        val inviteCode: String? = null,
        val createdGroupId: String? = null,
        val currentUserId: String? = null,
        val isSearching: Boolean = false
    )

    private val _state = MutableStateFlow(CreateState())
    val state: StateFlow<CreateState> = _state

    init {
        viewModelScope.launch {
            val uid = authService.getCurrentUserId()
            _state.value = _state.value.copy(currentUserId = uid)
        }
    }

    fun searchUsers(query: String) {
        if (query.isBlank()) {
            _state.value = _state.value.copy(searchResults = emptyList(), isSearching = false)
            return
        }
        _state.value = _state.value.copy(isSearching = true)
        viewModelScope.launch {
            userService.searchUsers(query)
                .onSuccess { results ->
                    _state.value = _state.value.copy(
                        searchResults = results.filter { r ->
                            _state.value.selectedMembers.none { it.uid == r.uid }
                        },
                        isSearching = false
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(isSearching = false)
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

    fun addOfflineMember(name: String) {
        if (name.isBlank()) return
        _state.value = _state.value.copy(
            offlineMembers = _state.value.offlineMembers + OfflineMember(name.trim())
        )
    }

    fun removeOfflineMember(member: OfflineMember) {
        _state.value = _state.value.copy(
            offlineMembers = _state.value.offlineMembers.filter { it != member }
        )
    }

    fun createGroup(name: String, description: String, template: GroupTemplate, monthlyBudget: Double? = null) {
        _state.value = _state.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            groupService.createGroup(
                name = name,
                description = description,
                template = template,
                memberUids = _state.value.selectedMembers.map { it.uid },
                monthlyBudget = monthlyBudget
            ).onSuccess { (groupId, inviteCode) ->
                for (offline in _state.value.offlineMembers) {
                    groupService.addOfflineMember(groupId, offline.name)
                }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateGroupScreen(
    navController: androidx.navigation.NavHostController,
    viewModel: CreateGroupViewModel = hiltViewModel()
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var monthlyBudget by remember { mutableStateOf("") }
    var selectedTemplate by remember { mutableStateOf(GroupTemplate.CASUAL) }
    var searchQuery by remember { mutableStateOf("") }
    var debouncedQuery by remember { mutableStateOf("") }
    var offlineName by remember { mutableStateOf("") }
    val state by viewModel.state.collectAsState()
    val currencyFormatter = rememberCurrencyFormatter()
    val currencySymbol = CurrencyConverter.getCurrencySymbol(currencyFormatter.userCurrency)
    val currentUserId = state.currentUserId
    val isDark = isSystemInDarkTheme()

    LaunchedEffect(state.createdGroupId) {
        if (state.createdGroupId != null) {
            navController.getBackStackEntry(TrevioRoute.Home.route)
                .savedStateHandle["needsRefresh"] = true
            navController.popBackStack(TrevioRoute.Home.route, inclusive = false)
        }
    }

    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotBlank()) {
            delay(300)
            debouncedQuery = searchQuery
            viewModel.searchUsers(debouncedQuery)
        } else {
            debouncedQuery = ""
            viewModel.searchUsers("")
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
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            // ── Template Selection ──
            SectionLabel("Choose a template")
            Spacer(modifier = Modifier.height(4.dp))
            SectionHint("Pick a category that best fits your group")
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TemplateCard(
                    icon = Icons.Default.TravelExplore,
                    title = "Trip",
                    subtitle = "Travel & trips",
                    iconColor = if (isDark) Color(0xFF818CF8) else Color(0xFF6366F1),
                    selected = selectedTemplate == GroupTemplate.TRIP,
                    onClick = { selectedTemplate = GroupTemplate.TRIP },
                    modifier = Modifier.weight(1f)
                )
                TemplateCard(
                    icon = Icons.Default.Sports,
                    title = "Turf",
                    subtitle = "Sports & turf",
                    iconColor = if (isDark) Color(0xFF4ADE80) else Color(0xFF22C55E),
                    selected = selectedTemplate == GroupTemplate.TURF,
                    onClick = { selectedTemplate = GroupTemplate.TURF },
                    modifier = Modifier.weight(1f)
                )
                TemplateCard(
                    icon = Icons.Default.Group,
                    title = "Casual",
                    subtitle = "Friends & casual",
                    iconColor = if (isDark) Color(0xFFFBBF24) else Color(0xFFF59E0B),
                    selected = selectedTemplate == GroupTemplate.CASUAL,
                    onClick = { selectedTemplate = GroupTemplate.CASUAL },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TemplateCard(
                    icon = Icons.Default.Home,
                    title = "Household",
                    subtitle = "Daily family expenses",
                    iconColor = if (isDark) Color(0xFF2DD4BF) else Color(0xFF0D9488),
                    selected = selectedTemplate == GroupTemplate.HOUSEHOLD,
                    onClick = { selectedTemplate = GroupTemplate.HOUSEHOLD },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.weight(1f))
            }
            if (selectedTemplate == GroupTemplate.HOUSEHOLD) {
                Spacer(modifier = Modifier.height(8.dp))
                SectionHint("Track daily family spending and income. No splitting — just log who paid what and see monthly reports.")
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ── Group Details ──
            SectionLabel("Group details")
            Spacer(modifier = Modifier.height(4.dp))
            SectionHint("Give your group a name so members can identify it")
            Spacer(modifier = Modifier.height(12.dp))

            StyledTextField(
                value = name,
                onValueChange = { name = it },
                label = "Group name",
                icon = Icons.Outlined.Label,
                singleLine = true
            )
            Spacer(modifier = Modifier.height(12.dp))
            StyledTextField(
                value = description,
                onValueChange = { description = it },
                label = "Description (optional)",
                icon = Icons.Outlined.Description,
                minLines = 2
            )
            if (selectedTemplate == GroupTemplate.HOUSEHOLD) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = monthlyBudget,
                    onValueChange = { monthlyBudget = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Monthly budget (optional)") },
                    prefix = { Text(currencySymbol) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ── Add Members ──
            SectionLabel("Add members")
            Spacer(modifier = Modifier.height(4.dp))
            SectionHint("Search by username or add someone not on the app")
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search by username", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = ""; debouncedQuery = "" }, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Outlined.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )

            // Loading indicator for search
            if (state.isSearching) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }

            // Search Results
            AnimatedVisibility(
                visible = state.searchResults.isNotEmpty(),
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        tonalElevation = 2.dp,
                        shadowElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            state.searchResults.forEachIndexed { index, user ->
                                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.addMember(user) }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    MemberAvatar(name = user.displayName, photoURL = user.photoURL, size = 40)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            user.displayName + if (user.uid == currentUserId) " (You)" else "",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            "@${user.username}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Outlined.PersonAdd,
                                            contentDescription = "Add",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // No results hint with add-offline option
            if (searchQuery.isNotEmpty() && state.searchResults.isEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.addOfflineMember(searchQuery)
                                searchQuery = ""
                                debouncedQuery = ""
                            }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.PersonAdd,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Add \"$searchQuery\" as offline member",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Divider between search and offline add
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(modifier = Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)))
                Text("or add by name", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                Box(modifier = Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)))
            }
            Spacer(modifier = Modifier.height(12.dp))

            // Add offline member by name
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = offlineName,
                    onValueChange = { offlineName = it },
                    placeholder = { Text("Name", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                    leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null, modifier = Modifier.size(20.dp)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )
                Button(
                    onClick = {
                        viewModel.addOfflineMember(offlineName)
                        offlineName = ""
                    },
                    enabled = offlineName.isNotBlank(),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add", modifier = Modifier.size(20.dp))
                }
            }

            // Selected Members (app users)
            AnimatedVisibility(
                visible = state.selectedMembers.isNotEmpty() || state.offlineMembers.isNotEmpty(),
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    val totalCount = state.selectedMembers.size + state.offlineMembers.size
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Group, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "$totalCount added",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        state.selectedMembers.forEach { user ->
                            SelectedMemberChip(
                                user = user,
                                isCurrentUser = user.uid == currentUserId,
                                onRemove = { viewModel.removeMember(user) }
                            )
                        }
                        state.offlineMembers.forEach { offline ->
                            OfflineMemberChip(
                                name = offline.name,
                                onRemove = { viewModel.removeOfflineMember(offline) }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Error
            if (state.error != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        state.error!!,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Create Button
            Button(
                onClick = {
                    val budgetInUserCurrency = monthlyBudget.toDoubleOrNull()
                    if (budgetInUserCurrency != null && budgetInUserCurrency < 0) {
                        return@Button
                    }
                    val budgetInBase = if (budgetInUserCurrency != null && budgetInUserCurrency > 0) {
                        CurrencyConverter.convertToBase(budgetInUserCurrency, currencyFormatter.userCurrency, currencyFormatter.rates)
                    } else null
                    viewModel.createGroup(name, description, selectedTemplate, budgetInBase)
                },
                enabled = name.isNotBlank() && !state.isLoading,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(22.dp)
                    )
                } else {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Create Group", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun SectionHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    )
}

@Composable
private fun StyledTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    singleLine: Boolean = false,
    minLines: Int = 1
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = singleLine,
        minLines = minLines,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
        )
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectedMemberChip(
    user: UserSearchResult,
    isCurrentUser: Boolean,
    onRemove: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
    ) {
        Row(
            modifier = Modifier.padding(start = 6.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MemberAvatar(name = user.displayName, photoURL = user.photoURL, size = 28)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = user.displayName + if (isCurrentUser) " (You)" else "",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(onClick = onRemove, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Outlined.Close, contentDescription = "Remove", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun OfflineMemberChip(
    name: String,
    onRemove: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(start = 6.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Person,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(onClick = onRemove, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Outlined.Close, contentDescription = "Remove", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun TemplateCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    iconColor: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val bgGradient = if (selected) {
        Brush.verticalGradient(
            listOf(iconColor.copy(alpha = 0.12f), iconColor.copy(alpha = 0.04f))
        )
    } else {
        Brush.verticalGradient(
            listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surface)
        )
    }

    Card(
        onClick = onClick,
        modifier = modifier,
        border = if (selected) BorderStroke(2.dp, iconColor) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 4.dp else 1.dp)
    ) {
        Box(
            modifier = Modifier
                .background(bgGradient)
                .padding(vertical = 20.dp, horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(iconColor.copy(alpha = if (selected) 0.18f else 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) iconColor else MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    fontSize = 10.sp,
                    maxLines = 1
                )
            }
        }
    }
}
