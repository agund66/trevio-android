package com.trevio.android.ui.group

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trevio.android.core.UserRefreshNotifier
import com.trevio.android.core.designsystem.components.LoadingIndicator
import com.trevio.android.core.designsystem.components.MemberAvatar
import com.trevio.android.core.designsystem.components.TrevioCard
import com.trevio.android.core.designsystem.components.TrevioHeader
import com.trevio.android.core.designsystem.theme.TrevioBorder
import com.trevio.android.core.navigation.TrevioRoute
import com.trevio.android.domain.model.Activity
import com.trevio.android.domain.model.Expense
import com.trevio.android.domain.model.Member
import com.trevio.android.domain.model.Settlement
import com.trevio.android.domain.model.UserSearchResult
import com.trevio.android.domain.model.SimplifiedDebt
import com.trevio.android.domain.repository.AuthService
import com.trevio.android.domain.repository.ExpenseService
import com.trevio.android.domain.repository.GroupInfo
import com.trevio.android.domain.repository.GroupService
import com.trevio.android.domain.repository.SettlementService
import com.trevio.android.domain.repository.UserService
import com.trevio.android.ui.analytics.AnalyticsTab
import com.trevio.android.ui.trip.TripTab
import com.trevio.android.util.rememberCurrencyFormatter
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
    private val authService: AuthService,
    private val userRefreshNotifier: UserRefreshNotifier,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val groupId: String = savedStateHandle.get<String>("groupId") ?: ""

    data class GroupState(
        val isLoading: Boolean = true,
        val groupInfo: GroupInfo? = null,
        val expenses: List<Expense> = emptyList(),
        val members: List<Member> = emptyList(),
        val debts: List<SimplifiedDebt> = emptyList(),
        val currentUserId: String? = null,
        val activities: List<Activity> = emptyList(),
        val activitiesLoading: Boolean = false,
        val activitiesError: String? = null,
        val searchResults: List<UserSearchResult> = emptyList(),
        val inviteError: String? = null,
        val actionError: String? = null,
        val error: String? = null,
        val settlements: List<Settlement> = emptyList(),
        val settlementsLoading: Boolean = false,
        val settlementsError: String? = null,
        val deleteExpenseId: String? = null,
        val deleteError: String? = null
    )

    private val _state = MutableStateFlow(GroupState())
    val state: StateFlow<GroupState> = _state

    init { loadData() }

    init {
        viewModelScope.launch {
            userRefreshNotifier.userRefreshed.collect {
                refreshData()
            }
        }
    }

    fun loadData() {
        _state.value = _state.value.copy(isLoading = true)
        viewModelScope.launch {
            val currentUid = authService.getCurrentUserId()
            val info = groupService.getGroupInfo(groupId).getOrNull()
            val expenses = expenseService.getGroupExpenses(groupId, 50, null).getOrDefault(emptyList())
            val members = settlementService.getGroupBalances(groupId).getOrDefault(emptyList())
            val debts = settlementService.getSimplifiedDebts(groupId).getOrDefault(emptyList())
            _state.value = _state.value.copy(
                isLoading = false,
                groupInfo = info,
                expenses = expenses,
                members = members,
                debts = debts,
                currentUserId = currentUid
            )
        }
    }

    fun refreshData() {
        viewModelScope.launch {
            val currentUid = authService.getCurrentUserId()
            val info = groupService.getGroupInfo(groupId).getOrNull()
            val expenses = expenseService.getGroupExpenses(groupId, 50, null).getOrDefault(emptyList())
            val members = settlementService.getGroupBalances(groupId).getOrDefault(emptyList())
            val debts = settlementService.getSimplifiedDebts(groupId).getOrDefault(emptyList())
            _state.value = _state.value.copy(
                groupInfo = info,
                expenses = expenses,
                members = members,
                debts = debts,
                currentUserId = currentUid
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
                    refreshData()
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(inviteError = e.message)
                }
        }
    }

    fun addOfflineMember(name: String) {
        viewModelScope.launch {
            groupService.addOfflineMember(groupId, name)
                .onSuccess {
                    _state.value = _state.value.copy(
                        searchResults = emptyList(),
                        inviteError = null
                    )
                    refreshData()
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(inviteError = e.message)
                }
        }
    }

    fun clearSearch() {
        _state.value = _state.value.copy(searchResults = emptyList(), inviteError = null)
    }

    fun toggleArchive() {
        val isArchived = _state.value.groupInfo?.archived ?: false
        viewModelScope.launch {
            val result = if (isArchived) {
                groupService.unarchiveGroup(groupId)
            } else {
                groupService.archiveGroup(groupId)
            }
            result.onSuccess {
                _state.value = _state.value.copy(actionError = null)
                refreshData()
            }.onFailure { e ->
                _state.value = _state.value.copy(actionError = e.message)
            }
        }
    }

    fun loadActivities() {
        _state.value = _state.value.copy(activitiesLoading = true, activitiesError = null)
        viewModelScope.launch {
            groupService.getGroupActivities(groupId)
                .onSuccess { activities ->
                    _state.value = _state.value.copy(activities = activities, activitiesLoading = false)
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(activitiesLoading = false, activitiesError = e.message)
                }
        }
    }

    fun settleDebt(debt: SimplifiedDebt) {
        viewModelScope.launch {
            settlementService.addSettlement(
                groupId = groupId,
                fromUid = debt.fromUid,
                toUid = debt.toUid,
                amount = debt.amount,
                currency = "INR",
                method = com.trevio.android.domain.model.SettlementMethod.CASH,
                upiRefId = null
            ).onSuccess {
                _state.value = _state.value.copy(actionError = null)
                refreshData()
            }.onFailure { e ->
                _state.value = _state.value.copy(actionError = e.message)
            }
        }
    }

    fun loadSettlements() {
        _state.value = _state.value.copy(settlementsLoading = true, settlementsError = null)
        viewModelScope.launch {
            settlementService.getSettlementHistory(groupId)
                .onSuccess { settlements ->
                    _state.value = _state.value.copy(settlements = settlements, settlementsLoading = false)
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(settlementsLoading = false, settlementsError = e.message)
                }
        }
    }

    fun deleteExpense(expenseId: String) {
        viewModelScope.launch {
            expenseService.deleteExpense(groupId, expenseId)
                .onSuccess {
                    _state.value = _state.value.copy(deleteExpenseId = null, deleteError = null)
                    refreshData()
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(deleteError = e.message)
                }
        }
    }

    fun setDeleteExpenseId(expenseId: String?) {
        _state.value = _state.value.copy(deleteExpenseId = expenseId, deleteError = null)
    }
}

private fun getUpiVpa(debt: SimplifiedDebt): String {
    if (debt.toUpiId.isNotEmpty()) return debt.toUpiId
    if (debt.toPhoneNumber.isNotEmpty() && (debt.toCountryCode.isEmpty() || debt.toCountryCode == "IN")) {
        return "${debt.toPhoneNumber}@paytm"
    }
    return ""
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(
    navController: androidx.navigation.NavHostController,
    viewModel: GroupViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val currencyFormatter = rememberCurrencyFormatter()
    var selectedTab by remember { mutableStateOf(0) }
    var showInviteDialog by remember { mutableStateOf(false) }
    var showAddOfflineDialog by remember { mutableStateOf(false) }
    var showQrDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var offlineName by remember { mutableStateOf("") }
    var expenseSearch by remember { mutableStateOf("") }
    var categoryFilter by remember { mutableStateOf("all") }
    val context = LocalContext.current

    val needsRefresh by navController.currentBackStackEntry
        ?.savedStateHandle?.getStateFlow<Boolean>("needsRefresh", false)
        ?.collectAsState() ?: mutableStateOf(false)

    LaunchedEffect(needsRefresh) {
        if (needsRefresh) {
            viewModel.refreshData()
            navController.currentBackStackEntry?.savedStateHandle?.set("needsRefresh", false)
            navController.previousBackStackEntry?.savedStateHandle?.set("needsRefresh", true)
        }
    }

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
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    navController.navigate(TrevioRoute.AddExpense.createRoute(state.groupInfo?.groupId ?: ""))
                },
                shape = RoundedCornerShape(16.dp),
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Expense", tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    ) { padding ->
        if (state.isLoading) {
            Column(modifier = Modifier.padding(padding).background(MaterialTheme.colorScheme.background)) {
                TrevioHeader(
                    title = "Group",
                    onBack = { navController.popBackStack() }
                )
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            return@Scaffold
        }

        val groupInfo = state.groupInfo
        val isAdmin = state.currentUserId == groupInfo?.createdBy ||
            state.members.find { it.uid == state.currentUserId }?.role == "admin"

        val isTrip = groupInfo?.template == com.trevio.android.domain.model.GroupTemplate.TRIP

        LazyColumn(modifier = Modifier.padding(padding).background(MaterialTheme.colorScheme.background)) {
            item {
                TrevioHeader(
                    title = groupInfo?.name ?: "Group",
                    onBack = { navController.popBackStack() },
                    actions = {
                        if (isAdmin) {
                            IconButton(onClick = { navController.navigate(TrevioRoute.GroupSettings.createRoute(state.groupInfo?.groupId ?: "")) }) {
                                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
                            }
                            IconButton(onClick = { viewModel.toggleArchive() }) {
                                Icon(
                                    if (groupInfo?.archived == true) Icons.Default.Unarchive else Icons.Default.Archive,
                                    contentDescription = if (groupInfo?.archived == true) "Unarchive" else "Archive",
                                    tint = Color.White
                                )
                            }
                        }
                        if (!state.groupInfo?.inviteCode.isNullOrBlank()) {
                            IconButton(onClick = { showQrDialog = true }) {
                                Icon(Icons.Default.QrCode2, contentDescription = "QR Code", tint = Color.White)
                            }
                            IconButton(onClick = {
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Invite Code", state.groupInfo?.inviteCode))
                                Toast.makeText(context, "Invite code copied", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy Code", tint = Color.White)
                            }
                            IconButton(onClick = { shareInviteLink() }) {
                                Icon(Icons.Default.Share, contentDescription = "Share Invite", tint = Color.White)
                            }
                        }
                    }
                ) {
                    if (groupInfo?.archived == true) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            color = Color.White.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        ) {
                            Text(
                                "Archived",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (groupInfo?.description?.isNotEmpty() == true) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            groupInfo.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        InfoChip("${groupInfo?.memberCount ?: 0} members")
                        InfoChip(currencyFormatter.formatBase(groupInfo?.totalExpenses ?: 0.0))
                        if (!groupInfo?.inviteCode.isNullOrBlank()) {
                            InfoChip("Code: ${groupInfo?.inviteCode}")
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            item {
                val membersTabIndex = if (isTrip) 4 else 3
                val activityTabIndex = if (isTrip) 5 else 4
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    edgePadding = 0.dp,
                    divider = {},
                    indicator = {
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(it[selectedTab]),
                            color = MaterialTheme.colorScheme.primary,
                            height = 3.dp
                        )
                    }
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Expenses", maxLines = 1) },
                        icon = { Icon(Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Balances", maxLines = 1) },
                        icon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Insights", maxLines = 1) },
                        icon = { Icon(Icons.Default.BarChart, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    if (isTrip) {
                        Tab(
                            selected = selectedTab == 3,
                            onClick = { selectedTab = 3 },
                            text = { Text("Trip", maxLines = 1) },
                            icon = { Icon(Icons.Default.Place, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                    }
                    Tab(
                        selected = selectedTab == membersTabIndex,
                        onClick = { selectedTab = membersTabIndex },
                        text = { Text("Members", maxLines = 1) },
                        icon = { Icon(Icons.Default.Group, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = selectedTab == activityTabIndex,
                        onClick = {
                            selectedTab = activityTabIndex
                            if (state.activities.isEmpty() && !state.activitiesLoading) {
                                viewModel.loadActivities()
                            }
                        },
                        text = { Text("Activity", maxLines = 1) },
                        icon = { Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }
            }

            val membersTabIndex = if (isTrip) 4 else 3
            val activityTabIndex = if (isTrip) 5 else 4

            when (selectedTab) {
                0 -> item {
                    val filtered = state.expenses.filter { e ->
                        (expenseSearch.isBlank() || e.description.contains(expenseSearch, ignoreCase = true)) &&
                        (categoryFilter == "all" || e.category == categoryFilter)
                    }
                    ExpensesTab(
                        expenses = filtered,
                        members = state.members,
                        currentUserId = state.currentUserId,
                        formatOriginal = currencyFormatter.formatOriginal,
                        expenseSearch = expenseSearch,
                        onExpenseSearchChange = { expenseSearch = it },
                        categoryFilter = categoryFilter,
                        onCategoryFilterChange = { categoryFilter = it },
                        onEditExpense = { expenseId ->
                            navController.navigate(TrevioRoute.EditExpense.createRoute(state.groupInfo?.groupId ?: "", expenseId))
                        },
                        onDeleteExpense = { expenseId ->
                            viewModel.setDeleteExpenseId(expenseId)
                        }
                    )
                }
                1 -> item {
                    BalancesTab(
                        members = state.members,
                        debts = state.debts,
                        currentUserId = state.currentUserId,
                        formatBase = currencyFormatter.formatBase,
                        onSettleUp = { navController.navigate(TrevioRoute.SettleUp.createRoute(state.groupInfo?.groupId ?: "")) },
                        onMemberClick = { uid -> navController.navigate(TrevioRoute.PublicProfile.createRoute(uid)) },
                        onSettleDebt = { debt -> viewModel.settleDebt(debt) },
                        onPayViaUpi = { debt ->
                            val vpa = getUpiVpa(debt)
                            if (vpa.isNotEmpty()) {
                                val upiUri = "upi://pay?pa=${android.net.Uri.encode(vpa)}&pn=${android.net.Uri.encode(debt.toName)}&am=${debt.amount}&cu=INR&tn=${android.net.Uri.encode("Trevio")}"
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(upiUri))
                                context.startActivity(android.content.Intent.createChooser(intent, "Pay with..."))
                            }
                        }
                    )
                }
                2 -> item {
                    AnalyticsTab(
                        groupId = state.groupInfo?.groupId ?: "",
                        groupName = state.groupInfo?.name ?: "Group",
                        expenses = state.expenses,
                        members = state.members
                    )
                }
                3 -> if (isTrip) item {
                    TripTab(groupId = state.groupInfo?.groupId ?: "")
                }
                membersTabIndex -> item {
                    MembersTab(
                        members = state.members,
                        currentUserId = state.currentUserId,
                        onInvite = { showInviteDialog = true },
                        onMemberClick = { uid ->
                            if (state.members.find { it.uid == uid }?.isOffline != true) {
                                navController.navigate(TrevioRoute.PublicProfile.createRoute(uid))
                            }
                        },
                        onAddOffline = { showAddOfflineDialog = true }
                    )
                }
                activityTabIndex -> item {
                    ActivityTab(
                        activities = state.activities,
                        settlements = state.settlements,
                        settlementsLoading = state.settlementsLoading,
                        settlementsError = state.settlementsError,
                        currentUserId = state.currentUserId,
                        isLoading = state.activitiesLoading,
                        errorMessage = state.activitiesError,
                        formatBase = currencyFormatter.formatBase,
                        formatDate = currencyFormatter.formatDate,
                        onLoadSettlements = { viewModel.loadSettlements() }
                    )
                }
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
                                        Text(user.displayName + if (user.uid == state.currentUserId) " (You)" else "", style = MaterialTheme.typography.bodyMedium)
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

        if (showAddOfflineDialog) {
            AlertDialog(
                onDismissRequest = {
                    showAddOfflineDialog = false
                    offlineName = ""
                },
                title = { Text("Add Offline Member") },
                text = {
                    Column {
                        Text(
                            "Add someone who isn't on the app yet. They can claim their profile later.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = offlineName,
                            onValueChange = { offlineName = it },
                            label = { Text("Name") },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        if (state.inviteError != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(state.inviteError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (offlineName.isNotBlank()) {
                                viewModel.addOfflineMember(offlineName)
                                offlineName = ""
                                showAddOfflineDialog = false
                            }
                        },
                        enabled = offlineName.isNotBlank()
                    ) { Text("Add") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showAddOfflineDialog = false
                        offlineName = ""
                    }) { Text("Cancel") }
                }
            )
        }

        if (showQrDialog && state.groupInfo?.inviteCode != null) {
            GroupQrCodeDialog(
                groupName = state.groupInfo?.name ?: "Group",
                inviteCode = state.groupInfo?.inviteCode ?: "",
                onDismiss = { showQrDialog = false }
            )
        }

        if (state.deleteExpenseId != null) {
            AlertDialog(
                onDismissRequest = { viewModel.setDeleteExpenseId(null) },
                title = { Text("Delete Expense?") },
                text = {
                    Column {
                        Text("This action cannot be undone. Balances will be recalculated.")
                        if (state.deleteError != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(state.deleteError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = { viewModel.deleteExpense(state.deleteExpenseId!!) },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text("Delete") }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.setDeleteExpenseId(null) }) { Text("Cancel") }
                }
            )
        }
    }
}

@Composable
private fun ExpensesTab(
    expenses: List<Expense>,
    members: List<Member>,
    currentUserId: String?,
    formatOriginal: (Double, String) -> String,
    expenseSearch: String = "",
    onExpenseSearchChange: (String) -> Unit = {},
    categoryFilter: String = "all",
    onCategoryFilterChange: (String) -> Unit = {},
    onEditExpense: (String) -> Unit = {},
    onDeleteExpense: (String) -> Unit = {}
) {
    val categories = listOf("all", "food", "transport", "shopping", "turf", "accommodation", "other")
    val hasExpenses = expenses.isNotEmpty() || expenseSearch.isNotBlank() || categoryFilter != "all"

    if (expenses.isEmpty() && expenseSearch.isBlank() && categoryFilter == "all") {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 80.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                Spacer(modifier = Modifier.height(16.dp))
                Text("No expenses yet", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Tap + to add your first expense", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            }
        }
    } else {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (hasExpenses) {
                OutlinedTextField(
                    value = expenseSearch,
                    onValueChange = onExpenseSearchChange,
                    placeholder = { Text("Search expenses...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categories) { cat ->
                        FilterChip(
                            selected = categoryFilter == cat,
                            onClick = { onCategoryFilterChange(cat) },
                            label = { Text(cat.replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
            }
            if (expenses.isEmpty() && (expenseSearch.isNotBlank() || categoryFilter != "all")) {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No expenses match your filters", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        TextButton(onClick = { onExpenseSearchChange(""); onCategoryFilterChange("all") }) { Text("Clear filters") }
                    }
                }
            } else {
                expenses.forEach { expense ->
                    ExpenseCard(expense, members, currentUserId, formatOriginal, onEditExpense, onDeleteExpense)
                }
            }
        }
    }
}

@Composable
private fun ExpenseCard(
    expense: Expense,
    members: List<Member>,
    currentUserId: String?,
    formatOriginal: (Double, String) -> String,
    onEditExpense: (String) -> Unit = {},
    onDeleteExpense: (String) -> Unit = {}
) {
    val payer = members.find { it.uid == expense.paidBy }
    val payerName = payer?.displayName?.split(" ")?.firstOrNull() ?: "Someone"
    val isPayerMe = payer?.uid == currentUserId
    val displayPayerName = if (isPayerMe) "You" else payerName
    val myShare = currentUserId?.let { expense.splits[it]?.amount }
    val canEdit = expense.createdBy == currentUserId || members.find { it.uid == currentUserId }?.role == "admin"
    val categoryIcon = when (expense.category) {
        "food" -> Icons.Default.Restaurant
        "transport" -> Icons.Default.DirectionsCar
        "shopping" -> Icons.Default.ShoppingBag
        "turf" -> Icons.Default.Sports
        "accommodation" -> Icons.Default.Hotel
        else -> Icons.Default.Receipt
    }
    val isDark = isSystemInDarkTheme()
    val categoryColor = when (expense.category) {
        "food" -> if (isDark) Color(0xFFFBBF24) else Color(0xFFF59E0B)
        "transport" -> if (isDark) Color(0xFF818CF8) else Color(0xFF6366F1)
        "shopping" -> if (isDark) Color(0xFFF472B6) else Color(0xFFEC4899)
        "turf" -> if (isDark) Color(0xFF4ADE80) else Color(0xFF22C55E)
        "accommodation" -> if (isDark) Color(0xFF2DD4BF) else Color(0xFF0D9488)
        else -> if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280)
    }
    val iconBgAlpha = if (isDark) 0.15f else 0.12f
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(categoryColor.copy(alpha = iconBgAlpha)),
                contentAlignment = Alignment.Center
            ) {
                Icon(categoryIcon, contentDescription = null, tint = categoryColor, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(expense.description, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    if (expense.recurring != null) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.Repeat, contentDescription = "Recurring", modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    buildString {
                        append("$displayPayerName paid · ${expense.category}")
                        if (myShare != null && kotlin.math.abs(myShare) > 0.01) {
                            append(" · your share: ")
                            append(formatOriginal(myShare, expense.currency))
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (expense.note.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(expense.note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                }
            }
            Text(
                text = formatOriginal(expense.amount, expense.currency),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            if (canEdit) {
                Spacer(modifier = Modifier.width(4.dp))
                Column {
                    IconButton(onClick = { onEditExpense(expense.expenseId) }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { onDeleteExpense(expense.expenseId) }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
                    }
                }
            }
        }
    }
}

@Composable
private fun BalancesTab(
    members: List<Member>,
    debts: List<SimplifiedDebt>,
    currentUserId: String?,
    formatBase: (Double) -> String,
    onSettleUp: () -> Unit,
    onMemberClick: (String) -> Unit,
    onSettleDebt: (SimplifiedDebt) -> Unit = {},
    onPayViaUpi: (SimplifiedDebt) -> Unit = {}
) {
    val myBalance = members.find { it.uid == currentUserId }?.balance ?: 0.0
    val myDebts = debts.filter { it.fromUid == currentUserId }
    val myCredits = debts.filter { it.toUid == currentUserId }
    val totalOwed = myDebts.sumOf { it.amount }
    val totalOwing = myCredits.sumOf { it.amount }

    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Your Balance summary card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        myBalance > 0.01 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                        myBalance < -0.01 -> MaterialTheme.colorScheme.error.copy(alpha = 0.08f)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Your balance", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formatBase(myBalance),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            myBalance > 0.01 -> MaterialTheme.colorScheme.primary
                            myBalance < -0.01 -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (myDebts.isNotEmpty()) {
                        Text(
                            "You owe ${myDebts.size} ${if (myDebts.size == 1) "person" else "people"} ${formatBase(totalOwed)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    if (myCredits.isNotEmpty()) {
                        Text(
                            "${myCredits.size} ${if (myCredits.size == 1) "person owes" else "people owe"} you ${formatBase(totalOwing)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (myDebts.isEmpty() && myCredits.isEmpty()) {
                        Text(
                            "All settled up in this group",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

        // Settle Up button
        if (members.isNotEmpty() && debts.isNotEmpty()) {
            Button(
                onClick = onSettleUp,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Settle Up")
            }
        }

        // Suggested settlements header
        if (debts.isNotEmpty()) {
            Text("Suggested Settlements", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))

            debts.forEach { debt ->
                val isMyDebt = debt.fromUid == currentUserId
                val isMyCredit = debt.toUid == currentUserId
                val fromFirstName = debt.fromName.split(" ").firstOrNull() ?: debt.fromName
                val toFirstName = debt.toName.split(" ").firstOrNull() ?: debt.toName
                val paymentVpa = getUpiVpa(debt)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            isMyDebt -> MaterialTheme.colorScheme.error.copy(alpha = 0.05f)
                            isMyCredit -> MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                            else -> MaterialTheme.colorScheme.surface
                        }
                    )
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = when {
                                        isMyDebt -> "You owe $toFirstName"
                                        isMyCredit -> "$fromFirstName owes you"
                                        else -> "$fromFirstName owes $toFirstName"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = when {
                                        isMyDebt -> MaterialTheme.colorScheme.error
                                        isMyCredit -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.onSurface
                                    }
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    formatBase(debt.amount),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = when {
                                        isMyDebt -> MaterialTheme.colorScheme.error
                                        isMyCredit -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.onSurface
                                    }
                                )
                                if (paymentVpa.isNotEmpty() && isMyDebt) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        "Pay to: $paymentVpa",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        if (isMyDebt) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (paymentVpa.isNotEmpty()) {
                                    OutlinedButton(
                                        onClick = { onPayViaUpi(debt) },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("Pay via UPI", style = MaterialTheme.typography.labelMedium)
                                    }
                                    Button(
                                        onClick = { onSettleDebt(debt) },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("Mark Settled", style = MaterialTheme.typography.labelMedium)
                                    }
                                } else {
                                    Button(
                                        onClick = { onSettleDebt(debt) },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("Mark Settled", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Member balances header
        Text("Member Balances", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))

        members.forEach { member ->
            val isMe = member.uid == currentUserId
            Card(
                onClick = { onMemberClick(member.uid) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isMe) MaterialTheme.colorScheme.primary.copy(alpha = 0.05f) else MaterialTheme.colorScheme.surface
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MemberAvatar(name = member.displayName, photoURL = member.photoURL, size = 40)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            member.displayName + if (isMe) " (You)" else "",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                        Text("@${member.username}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (member.status == "pending") {
                        AssistChip(
                            onClick = {},
                            leadingIcon = { Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(14.dp)) },
                            label = { Text("pending", style = MaterialTheme.typography.labelSmall) }
                        )
                    } else {
                        val color = when {
                            member.balance > 0.01 -> MaterialTheme.colorScheme.primary
                            member.balance < -0.01 -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        val text = when {
                            member.balance > 0.01 -> if (isMe) "you'll get ${formatBase(member.balance)}" else "gets ${formatBase(member.balance)}"
                            member.balance < -0.01 -> if (isMe) "you'll pay ${formatBase(-member.balance)}" else "owes ${formatBase(-member.balance)}"
                            else -> "settled"
                        }
                        Surface(
                            color = color.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = text,
                                color = color,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MembersTab(members: List<Member>, currentUserId: String?, onInvite: () -> Unit, onMemberClick: (String) -> Unit, onAddOffline: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Members (${members.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAddOffline, shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add")
                }
                Button(onClick = onInvite, shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Invite")
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        members.forEach { member ->
            Card(
                onClick = { onMemberClick(member.uid) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MemberAvatar(name = member.displayName, photoURL = member.photoURL, size = 40)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(member.displayName + if (member.uid == currentUserId) " (You)" else "", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                        if (member.isOffline) {
                            Text("Offline member", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Text("@${member.username}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (member.isOffline) {
                        AssistChip(
                            onClick = {},
                            leadingIcon = { Icon(Icons.Default.CloudOff, contentDescription = null, modifier = Modifier.size(14.dp)) },
                            label = { Text("offline", style = MaterialTheme.typography.labelSmall) }
                        )
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

@Composable
private fun ActivityTab(
    activities: List<Activity>,
    settlements: List<Settlement>,
    settlementsLoading: Boolean,
    settlementsError: String?,
    currentUserId: String?,
    isLoading: Boolean,
    errorMessage: String? = null,
    formatBase: (Double) -> String,
    formatDate: (Long, Boolean) -> String,
    onLoadSettlements: () -> Unit
) {
    var activityFilter by remember { mutableStateOf("all") }

    LaunchedEffect(activityFilter) {
        if (activityFilter == "settlements" && settlements.isEmpty() && !settlementsLoading) {
            onLoadSettlements()
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        // Filter toggle
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            FilterChip(
                selected = activityFilter == "all",
                onClick = { activityFilter = "all" },
                label = { Text("All") }
            )
            FilterChip(
                selected = activityFilter == "settlements",
                onClick = { activityFilter = "settlements" },
                label = { Text("Settlements") }
            )
        }

        if (activityFilter == "settlements") {
            if (settlementsLoading) {
                LoadingIndicator(modifier = Modifier.fillMaxWidth().padding(40.dp))
            } else if (settlementsError != null) {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                    Text(settlementsError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            } else if (settlements.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("No settlements yet", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                val isDark = isSystemInDarkTheme()
                val settlementColor = if (isDark) Color(0xFF4ADE80) else Color(0xFF22C55E)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    settlements.forEach { s ->
                        val isFromMe = currentUserId == s.fromUid
                        val isToMe = currentUserId == s.toUid
                        val fromName = if (isFromMe) "You" else s.fromName.split(" ").firstOrNull() ?: s.fromName
                        val toName = if (isToMe) "you" else s.toName.split(" ").firstOrNull() ?: s.toName
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier.size(40.dp).clip(CircleShape).background(settlementColor.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = settlementColor, modifier = Modifier.size(20.dp))
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("$fromName paid $toName", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        buildString {
                                            if (s.date > 0) append(formatDate(s.date, false))
                                            append(" · ${s.method.name.lowercase()}")
                                            if (s.upiRefId.isNotBlank()) append(" · Ref: ${s.upiRefId}")
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(formatBase(s.amount), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = settlementColor)
                            }
                        }
                    }
                }
            }
        } else {
            if (isLoading) {
                LoadingIndicator(modifier = Modifier.fillMaxWidth().padding(40.dp))
            } else if (errorMessage != null) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Failed to load activity", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(errorMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else if (activities.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("No activity yet", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    activities.forEach { activity ->
                        val (icon, iconColor) = activityIcon(activity.type)
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(iconColor.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(18.dp))
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(activity.description, style = MaterialTheme.typography.bodySmall)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (activity.userPhotoURL.isNotEmpty()) {
                                            MemberAvatar(name = activity.userName, photoURL = activity.userPhotoURL, size = 16)
                                            Spacer(modifier = Modifier.width(4.dp))
                                        }
                                        Text(activity.userName + if (activity.userId == currentUserId) " (You)" else "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("·", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(formatRelativeTime(activity.createdAt, formatDate), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun activityIcon(type: String): Pair<androidx.compose.ui.graphics.vector.ImageVector, Color> {
    val isDark = isSystemInDarkTheme()
    return when (type) {
        "expense_added", "expense_updated", "expense_deleted" -> Icons.Default.Receipt to if (isDark) Color(0xFFFBBF24) else Color(0xFFF59E0B)
        "settlement_added" -> Icons.Default.AccountBalanceWallet to if (isDark) Color(0xFF4ADE80) else Color(0xFF22C55E)
        "member_joined" -> Icons.Default.PersonAdd to if (isDark) Color(0xFF818CF8) else Color(0xFF6366F1)
        "member_left" -> Icons.Default.PersonRemove to if (isDark) Color(0xFFF87171) else Color(0xFFEF4444)
        "group_created" -> Icons.Default.Group to if (isDark) Color(0xFF2DD4BF) else Color(0xFF0D9488)
        else -> Icons.Default.Info to if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280)
    }
}

@Composable
private fun InfoChip(text: String) {
    Surface(
        color = Color.White.copy(alpha = 0.2f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

private fun formatRelativeTime(timestamp: Long, formatDate: (Long, Boolean) -> String): String {
    if (timestamp == 0L) return ""
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val minutes = diff / 60000
    val hours = diff / 3600000
    val days = diff / 86400000
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days < 7 -> "${days}d ago"
        else -> formatDate(timestamp, false)
    }
}
