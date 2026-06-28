package com.trevio.android.ui.group

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trevio.android.core.designsystem.components.BalanceChip
import com.trevio.android.core.designsystem.components.LoadingIndicator
import com.trevio.android.core.designsystem.components.MemberAvatar
import com.trevio.android.core.designsystem.components.formatCurrency
import com.trevio.android.core.navigation.TrevioRoute
import com.trevio.android.domain.model.Expense
import com.trevio.android.domain.model.Member
import com.trevio.android.domain.model.UserSearchResult
import com.trevio.android.domain.repository.ExpenseService
import com.trevio.android.domain.repository.GroupInfo
import com.trevio.android.domain.repository.GroupService
import com.trevio.android.domain.repository.SettlementService
import com.trevio.android.domain.repository.UserService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GroupViewModel @Inject constructor(
    private val expenseService: ExpenseService,
    private val settlementService: SettlementService,
    private val groupService: GroupService,
    private val userService: UserService,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val groupId: String = savedStateHandle.get<String>("groupId") ?: ""

    data class GroupState(
        val isLoading: Boolean = true,
        val groupInfo: GroupInfo? = null,
        val expenses: List<Expense> = emptyList(),
        val members: List<Member> = emptyList(),
        val searchResults: List<UserSearchResult> = emptyList(),
        val inviteError: String? = null,
        val error: String? = null
    )

    private val _state = MutableStateFlow(GroupState())
    val state: StateFlow<GroupState> = _state

    init { loadData() }

    fun loadData() {
        _state.value = _state.value.copy(isLoading = true)
        viewModelScope.launch {
            val info = groupService.getGroupInfo(groupId).getOrNull()
            val expenses = expenseService.getGroupExpenses(groupId, 50, null).getOrDefault(emptyList())
            val members = settlementService.getGroupBalances(groupId).getOrDefault(emptyList())
            _state.value = GroupState(
                isLoading = false,
                groupInfo = info,
                expenses = expenses,
                members = members
            )
        }
    }

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
                            _state.value.members.none { it.uid == r.uid }
                        }
                    )
                }
        }
    }

    fun inviteMember(username: String) {
        viewModelScope.launch {
            groupService.sendGroupInvitation(groupId, username)
                .onSuccess {
                    _state.value = _state.value.copy(
                        searchResults = emptyList(),
                        inviteError = null
                    )
                    loadData()
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(inviteError = e.message)
                }
        }
    }

    fun clearSearch() {
        _state.value = _state.value.copy(searchResults = emptyList(), inviteError = null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(
    navController: androidx.navigation.NavHostController,
    viewModel: GroupViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var selectedTab by remember { mutableStateOf(0) }
    var showInviteDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val context = LocalContext.current

    val shareInviteLink = {
        val inviteCode = state.groupInfo?.inviteCode
        if (!inviteCode.isNullOrBlank()) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Join \"${state.groupInfo?.name}\" on Trevio")
                putExtra(Intent.EXTRA_TEXT, "You've been invited to join \"${state.groupInfo?.name}\" on Trevio. Tap to join and start splitting bills!\n\nhttps://trevio.app/join/$inviteCode")
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share Invite"))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.groupInfo?.name ?: "Group") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!state.groupInfo?.inviteCode.isNullOrBlank()) {
                        IconButton(onClick = { shareInviteLink() }) {
                            Icon(Icons.Default.Share, contentDescription = "Share Invite")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    navController.navigate(TrevioRoute.AddExpense.createRoute(state.groupInfo?.groupId ?: ""))
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Expense")
            }
        }
    ) { padding ->
        if (state.isLoading) {
            LoadingIndicator(modifier = Modifier.padding(padding))
            return@Scaffold
        }

        val groupInfo = state.groupInfo
        val currency = groupInfo?.currency ?: "INR"

        Column(modifier = Modifier.padding(padding)) {
            if (groupInfo?.description?.isNotEmpty() == true) {
                Text(
                    groupInfo.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(onClick = {}, label = { Text("${groupInfo?.memberCount ?: 0} members") })
                AssistChip(onClick = {}, label = { Text("${formatCurrency(groupInfo?.totalExpenses ?: 0.0, currency)} total") })
                if (!groupInfo?.inviteCode.isNullOrBlank()) {
                    AssistChip(
                        onClick = {},
                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text("Code: ${groupInfo?.inviteCode}") }
                    )
                }
            }

            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Expenses") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Balances") })
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Members") })
            }

            when (selectedTab) {
                0 -> ExpensesTab(
                    expenses = state.expenses,
                    onSettleUp = { navController.navigate(TrevioRoute.SettleUp.createRoute(state.groupInfo?.groupId ?: "")) }
                )
                1 -> BalancesTab(
                    members = state.members,
                    currency = currency,
                    onSettleUp = { navController.navigate(TrevioRoute.SettleUp.createRoute(state.groupInfo?.groupId ?: "")) }
                )
                2 -> MembersTab(
                    members = state.members,
                    onInvite = { showInviteDialog = true }
                )
            }
        }

        if (showInviteDialog) {
            AlertDialog(
                onDismissRequest = {
                    showInviteDialog = false
                    searchQuery = ""
                    viewModel.clearSearch()
                },
                title = { Text("Invite Member") },
                text = {
                    Column {
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
                            state.searchResults.forEach { user ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    MemberAvatar(name = user.displayName, photoURL = user.photoURL, size = 32)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(user.displayName, style = MaterialTheme.typography.bodyMedium)
                                        Text("@${user.username}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    IconButton(onClick = {
                                        viewModel.inviteMember(user.username)
                                        searchQuery = ""
                                    }) {
                                        Icon(Icons.Default.PersonAdd, contentDescription = "Invite", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                        if (state.inviteError != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(state.inviteError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        showInviteDialog = false
                        searchQuery = ""
                        viewModel.clearSearch()
                    }) { Text("Done") }
                }
            )
        }
    }
}

@Composable
private fun ExpensesTab(expenses: List<Expense>, onSettleUp: () -> Unit) {
    if (expenses.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(16.dp))
                Text("No expenses yet", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Tap + to add your first expense", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(expenses) { expense ->
                ExpenseCard(expense)
            }
        }
    }
}

@Composable
private fun ExpenseCard(expense: Expense) {
    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(expense.description, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "${expense.paidBy.ifEmpty { "Someone" }} paid · ${expense.category}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = formatCurrency(expense.amount, expense.currency),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun BalancesTab(members: List<Member>, currency: String, onSettleUp: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (members.isNotEmpty()) {
            Button(
                onClick = onSettleUp,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Settle Up")
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(members) { member ->
                Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MemberAvatar(name = member.displayName, photoURL = member.photoURL, size = 40)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(member.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                            Text("@${member.username}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (member.status == "pending") {
                            AssistChip(
                                onClick = {},
                                leadingIcon = { Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(14.dp)) },
                                label = { Text("pending", style = MaterialTheme.typography.labelSmall) }
                            )
                        } else {
                            BalanceChip(balance = member.balance, currency = currency)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MembersTab(members: List<Member>, onInvite: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Members (${members.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Button(onClick = onInvite, shape = MaterialTheme.shapes.medium) {
                Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Invite")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(members) { member ->
                Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MemberAvatar(name = member.displayName, photoURL = member.photoURL, size = 40)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(member.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                            Text("@${member.username}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (member.status == "pending") {
                            AssistChip(
                                onClick = {},
                                leadingIcon = { Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(14.dp)) },
                                label = { Text("pending", style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                        if (member.role == "admin") {
                            Spacer(modifier = Modifier.width(4.dp))
                            AssistChip(onClick = {}, label = { Text("admin", style = MaterialTheme.typography.labelSmall) })
                        }
                    }
                }
            }
        }
    }
}
