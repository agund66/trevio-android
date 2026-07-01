package com.trevio.android.ui.group

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trevio.android.core.designsystem.components.LoadingIndicator
import com.trevio.android.core.designsystem.components.MemberAvatar
import com.trevio.android.core.designsystem.components.TrevioCard
import com.trevio.android.core.designsystem.components.TrevioHeader
import com.trevio.android.core.designsystem.theme.TrevioBorder
import com.trevio.android.core.navigation.TrevioRoute
import com.trevio.android.domain.model.Activity
import com.trevio.android.domain.model.Expense
import com.trevio.android.domain.model.Member
import com.trevio.android.domain.model.UserSearchResult
import com.trevio.android.domain.model.SimplifiedDebt
import com.trevio.android.domain.repository.AuthService
import com.trevio.android.domain.repository.ExpenseService
import com.trevio.android.domain.repository.GroupInfo
import com.trevio.android.domain.repository.GroupService
import com.trevio.android.domain.repository.SettlementService
import com.trevio.android.domain.repository.UserService
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

    fun toggleArchive() {
        val isArchived = _state.value.groupInfo?.archived ?: false
        viewModelScope.launch {
            if (isArchived) {
                groupService.unarchiveGroup(groupId)
            } else {
                groupService.archiveGroup(groupId)
            }
            loadData()
        }
    }

    fun loadActivities() {
        _state.value = _state.value.copy(activitiesLoading = true)
        viewModelScope.launch {
            groupService.getGroupActivities(groupId)
                .onSuccess { activities ->
                    _state.value = _state.value.copy(activities = activities, activitiesLoading = false)
                }
                .onFailure {
                    _state.value = _state.value.copy(activitiesLoading = false)
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
            ).onSuccess { loadData() }
        }
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
    var searchQuery by remember { mutableStateOf("") }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.loadData()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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

        LazyColumn(modifier = Modifier.padding(padding).background(MaterialTheme.colorScheme.background)) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primary)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                        Row {
                            IconButton(onClick = { viewModel.toggleArchive() }) {
                                Icon(
                                    if (groupInfo?.archived == true) Icons.Default.Unarchive else Icons.Default.Archive,
                                    contentDescription = if (groupInfo?.archived == true) "Unarchive" else "Archive",
                                    tint = Color.White
                                )
                            }
                            if (!state.groupInfo?.inviteCode.isNullOrBlank()) {
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
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Text(
                            groupInfo?.name ?: "Group",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        if (groupInfo?.archived == true) {
                            Surface(
                                color = Color.White.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    "Archived",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
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
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Expenses") })
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Balances") })
                    Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Members") })
                    Tab(selected = selectedTab == 3, onClick = {
                        selectedTab = 3
                        if (state.activities.isEmpty() && !state.activitiesLoading) {
                            viewModel.loadActivities()
                        }
                    }, text = { Text("Activity") })
                }
            }

            when (selectedTab) {
                0 -> item {
                    ExpensesTab(
                        expenses = state.expenses,
                        members = state.members,
                        currentUserId = state.currentUserId,
                        formatOriginal = currencyFormatter.formatOriginal,
                        onSettleUp = { navController.navigate(TrevioRoute.SettleUp.createRoute(state.groupInfo?.groupId ?: "")) }
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
                    MembersTab(
                        members = state.members,
                        onInvite = { showInviteDialog = true },
                        onMemberClick = { uid -> navController.navigate(TrevioRoute.PublicProfile.createRoute(uid)) }
                    )
                }
                3 -> item {
                    ActivityTab(
                        activities = state.activities,
                        isLoading = state.activitiesLoading
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
private fun ExpensesTab(expenses: List<Expense>, members: List<Member>, currentUserId: String?, formatOriginal: (Double, String) -> String, onSettleUp: () -> Unit) {
    if (expenses.isEmpty()) {
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
            expenses.forEach { expense ->
                ExpenseCard(expense, members, currentUserId, formatOriginal)
            }
        }
    }
}

@Composable
private fun ExpenseCard(expense: Expense, members: List<Member>, currentUserId: String?, formatOriginal: (Double, String) -> String) {
    val payer = members.find { it.uid == expense.paidBy }
    val payerName = payer?.displayName?.split(" ")?.firstOrNull() ?: "Someone"
    val myShare = currentUserId?.let { expense.splits[it]?.amount }
    val categoryIcon = when (expense.category) {
        "food" -> Icons.Default.Restaurant
        "transport" -> Icons.Default.DirectionsCar
        "shopping" -> Icons.Default.ShoppingBag
        "turf" -> Icons.Default.Sports
        "accommodation" -> Icons.Default.Hotel
        else -> Icons.Default.Receipt
    }
    val categoryColor = when (expense.category) {
        "food" -> Color(0xFFF59E0B)
        "transport" -> Color(0xFF6366F1)
        "shopping" -> Color(0xFFEC4899)
        "turf" -> Color(0xFF22C55E)
        "accommodation" -> Color(0xFF0D9488)
        else -> Color(0xFF6B7280)
    }
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
                    .background(categoryColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(categoryIcon, contentDescription = null, tint = categoryColor, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(expense.description, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    buildString {
                        append("$payerName paid · ${expense.category}")
                        if (myShare != null && kotlin.math.abs(myShare) > 0.01) {
                            append(" · your share: ")
                            append(formatOriginal(myShare, expense.currency))
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = formatOriginal(expense.amount, expense.currency),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
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
                            member.displayName + if (isMe) " (you)" else "",
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
private fun MembersTab(members: List<Member>, onInvite: () -> Unit, onMemberClick: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Members (${members.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Button(onClick = onInvite, shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Invite")
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
                        Text(member.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
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

@Composable
private fun ActivityTab(activities: List<Activity>, isLoading: Boolean) {
    if (isLoading) {
        LoadingIndicator(modifier = Modifier.fillMaxWidth().padding(40.dp))
        return
    }

    if (activities.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 80.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                Spacer(modifier = Modifier.height(16.dp))
                Text("No activity yet", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
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
                            Text(activity.userName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("·", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(formatRelativeTime(activity.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

private fun activityIcon(type: String): Pair<androidx.compose.ui.graphics.vector.ImageVector, Color> {
    return when (type) {
        "expense_added", "expense_updated", "expense_deleted" -> Icons.Default.Receipt to Color(0xFFF59E0B)
        "settlement_added" -> Icons.Default.AccountBalanceWallet to Color(0xFF22C55E)
        "member_joined" -> Icons.Default.PersonAdd to Color(0xFF6366F1)
        "member_left" -> Icons.Default.PersonRemove to Color(0xFFEF4444)
        "group_created" -> Icons.Default.Group to Color(0xFF0D9488)
        else -> Icons.Default.Info to Color(0xFF6B7280)
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

private fun formatRelativeTime(timestamp: Long): String {
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
        else -> java.text.SimpleDateFormat("MMM dd", java.util.Locale.getDefault()).format(java.util.Date(timestamp))
    }
}
