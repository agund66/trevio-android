package com.trevio.android.ui.group

import android.content.Intent
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trevio.android.core.UserRefreshNotifier
import com.trevio.android.core.designsystem.components.ListItemSkeleton
import com.trevio.android.core.designsystem.components.LoadingIndicator
import com.trevio.android.core.designsystem.components.MemberAvatar
import com.trevio.android.core.designsystem.components.TrevioCard
import com.trevio.android.core.designsystem.components.TrevioHeader
import com.trevio.android.core.designsystem.components.formatRelativeTimeText
import com.trevio.android.core.designsystem.theme.TrevioBorder
import com.trevio.android.core.designsystem.theme.TemplateTrip
import com.trevio.android.core.designsystem.theme.TemplateTripDark
import com.trevio.android.core.designsystem.theme.TemplateTurf
import com.trevio.android.core.designsystem.theme.TemplateTurfDark
import com.trevio.android.core.designsystem.theme.TemplateCasual
import com.trevio.android.core.designsystem.theme.TemplateCasualDark
import com.trevio.android.core.designsystem.theme.TemplateHousehold
import com.trevio.android.core.designsystem.theme.TemplateHouseholdDark
import com.trevio.android.core.designsystem.theme.BalancePositive
import com.trevio.android.core.designsystem.theme.BalancePositiveDark
import com.trevio.android.core.designsystem.theme.BalanceNegative
import com.trevio.android.core.designsystem.theme.BalanceNegativeDark
import com.trevio.android.core.designsystem.theme.CategoryFood
import com.trevio.android.core.designsystem.theme.CategoryFoodDark
import com.trevio.android.core.designsystem.theme.CategoryTransport
import com.trevio.android.core.designsystem.theme.CategoryTransportDark
import com.trevio.android.core.designsystem.theme.CategoryShopping
import com.trevio.android.core.designsystem.theme.CategoryShoppingDark
import com.trevio.android.core.designsystem.theme.CategoryTurf
import com.trevio.android.core.designsystem.theme.CategoryTurfDark
import com.trevio.android.core.designsystem.theme.CategoryAccommodation
import com.trevio.android.core.designsystem.theme.CategoryAccommodationDark
import com.trevio.android.core.designsystem.theme.CategoryOther
import com.trevio.android.core.designsystem.theme.CategoryOtherDark
import com.trevio.android.R
import com.trevio.android.core.navigation.TrevioRoute
import com.trevio.android.domain.model.Activity
import com.trevio.android.domain.model.Expense
import com.trevio.android.domain.model.Member
import com.trevio.android.domain.model.PaginatedResult
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
import com.trevio.android.util.AppConstants
import com.trevio.android.util.CurrencyConverter
import com.trevio.android.data.remote.FirestoreObservers
import com.trevio.android.util.DateUtils
import com.trevio.android.util.MemberRole
import com.trevio.android.util.rememberCurrencyFormatter
import com.trevio.android.util.toStringResId
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
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
    private val firestoreObservers: FirestoreObservers,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val groupId: String = savedStateHandle.get<String>("groupId") ?: ""

    data class GroupState(
        val isLoading: Boolean = true,
        val groupInfo: GroupInfo? = null,
        val expenses: List<Expense> = emptyList(),
        val expensesHasMore: Boolean = false,
        val expensesLoadingMore: Boolean = false,
        val members: List<Member> = emptyList(),
        val debts: List<SimplifiedDebt> = emptyList(),
        val currentUserId: String? = null,
        val activities: List<Activity> = emptyList(),
        val activitiesLoading: Boolean = false,
        @StringRes val activitiesError: Int? = null,
        val activitiesHasMore: Boolean = false,
        val activitiesLoadingMore: Boolean = false,
        val searchResults: List<UserSearchResult> = emptyList(),
        @StringRes val inviteError: Int? = null,
        @StringRes val actionError: Int? = null,
        @StringRes val error: Int? = null,
        val settlements: List<Settlement> = emptyList(),
        val settlementsLoading: Boolean = false,
        @StringRes val settlementsError: Int? = null,
        val settlementsHasMore: Boolean = false,
        val settlementsLoadingMore: Boolean = false,
        val deleteExpenseId: String? = null,
        @StringRes val deleteError: Int? = null,
        @StringRes val loadMoreError: Int? = null
    )

    private val _state = MutableStateFlow(GroupState())
    val state: StateFlow<GroupState> = _state

    /// Tracks the current activity listener coroutine so repeated
    /// loadActivities() calls don't create multiple Firestore listeners.
    private var activitiesListenerJob: kotlinx.coroutines.Job? = null

    /// Tracks the main data listener (combine of groupInfo/expenses/balances)
    /// so repeated loadData() calls don't create multiple Firestore listeners.
    private var dataListenerJob: kotlinx.coroutines.Job? = null

    init {
        loadData()
        viewModelScope.launch {
            userRefreshNotifier.userRefreshed.collect {
                refreshData()
            }
        }
    }

    /**
     * Starts real-time Firestore listeners for groupInfo, expenses, and
     * members.  With offline persistence enabled, the first emission
     * comes from cache (instant) and subsequent emissions arrive from
     * the server silently — no full-screen loader on repeat visits.
     *
     * Simplified debts are fetched once on load and on refresh because
     * they require client-side computation over all expenses + settlements.
     */
    fun loadData() {
        _state.value = _state.value.copy(isLoading = true)

        // Cancel any existing listener before starting a new one
        // to prevent duplicate Firestore listeners.
        dataListenerJob?.cancel()
        dataListenerJob = viewModelScope.launch {
            val currentUid = authService.getCurrentUserId()
            _state.value = _state.value.copy(currentUserId = currentUid)

            // Combine the three real-time flows into a single state update.
            // Each flow emits independently; combine fires whenever any one
            // emits, so the UI updates incrementally as cache → server data
            // arrives.
            try {
                combine(
                    firestoreObservers.observeGroupInfo(groupId),
                    firestoreObservers.observeGroupExpenses(groupId, AppConstants.DEFAULT_PAGE_SIZE),
                    firestoreObservers.observeGroupBalances(groupId)
                ) { info, expensesResult, members ->
                    Triple(info, expensesResult, members)
                }.collect { (info, expensesResult, members) ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        groupInfo = info,
                        expenses = expensesResult.items,
                        expensesHasMore = expensesResult.hasMore,
                        members = members,
                        currentUserId = currentUid
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.toStringResId()
                )
            }
        }

        // Fetch debts separately — heavy computation, not a simple listener.
        // Done in parallel with the listeners so it doesn't block them.
        refreshDebts()
    }

    /**
     * Re-fetches data that is NOT covered by real-time listeners
     * (simplified debts + member balances with latest user profiles).
     * The listener-managed data (groupInfo, expenses) updates
     * automatically, but member display names/photos come from the
     * users collection which the listener doesn't watch — so we
     * re-fetch balances here to pick up profile changes.
     */
    fun refreshData() {
        refreshDebts()
        viewModelScope.launch {
            val members = settlementService.getGroupBalances(groupId).getOrDefault(emptyList())
            _state.value = _state.value.copy(members = members)
        }
    }

    private fun refreshDebts() {
        viewModelScope.launch {
            val debts = settlementService.getSimplifiedDebts(groupId).getOrDefault(emptyList())
            _state.value = _state.value.copy(debts = debts)
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
                    _state.value = _state.value.copy(inviteError = e.toStringResId())
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
                    _state.value = _state.value.copy(inviteError = e.toStringResId())
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
                _state.value = _state.value.copy(actionError = e.toStringResId())
            }
        }
    }

    fun loadActivities() {
        _state.value = _state.value.copy(activitiesLoading = true, activitiesError = null)
        // Cancel any existing listener before starting a new one
        // to prevent duplicate Firestore listeners.
        activitiesListenerJob?.cancel()
        activitiesListenerJob = viewModelScope.launch {
            try {
                firestoreObservers.observeGroupActivities(groupId, AppConstants.DEFAULT_PAGE_SIZE)
                    .collect { result ->
                        _state.value = _state.value.copy(
                            activities = result.items,
                            activitiesLoading = false,
                            activitiesHasMore = result.hasMore,
                            activitiesError = null
                        )
                    }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    activitiesLoading = false,
                    activitiesError = e.toStringResId()
                )
            }
        }
    }

    fun loadMoreActivities() {
        if (!_state.value.activitiesHasMore || _state.value.activitiesLoadingMore) return
        _state.value = _state.value.copy(activitiesLoadingMore = true)
        val lastId = _state.value.activities.lastOrNull()?.activityId
        viewModelScope.launch {
            groupService.getGroupActivities(groupId, AppConstants.DEFAULT_PAGE_SIZE, lastId)
                .onSuccess { result ->
                    _state.value = _state.value.copy(
                        activities = _state.value.activities + result.items,
                        activitiesLoadingMore = false,
                        activitiesHasMore = result.hasMore
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(activitiesLoadingMore = false)
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
                currency = debt.currency,
                method = com.trevio.android.domain.model.SettlementMethod.CASH,
                upiRefId = null
            ).onSuccess {
                _state.value = _state.value.copy(actionError = null)
                refreshData()
            }.onFailure { e ->
                _state.value = _state.value.copy(actionError = e.toStringResId())
            }
        }
    }

    fun loadSettlements() {
        _state.value = _state.value.copy(settlementsLoading = true, settlementsError = null)
        viewModelScope.launch {
            settlementService.getSettlementHistory(groupId, AppConstants.DEFAULT_PAGE_SIZE, null)
                .onSuccess { result ->
                    _state.value = _state.value.copy(settlements = result.items, settlementsLoading = false, settlementsHasMore = result.hasMore)
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(settlementsLoading = false, settlementsError = e.toStringResId())
                }
        }
    }

    fun loadMoreSettlements() {
        if (!_state.value.settlementsHasMore || _state.value.settlementsLoadingMore) return
        _state.value = _state.value.copy(settlementsLoadingMore = true)
        val lastId = _state.value.settlements.lastOrNull()?.settlementId
        viewModelScope.launch {
            settlementService.getSettlementHistory(groupId, AppConstants.DEFAULT_PAGE_SIZE, lastId)
                .onSuccess { result ->
                    _state.value = _state.value.copy(
                        settlements = _state.value.settlements + result.items,
                        settlementsLoadingMore = false,
                        settlementsHasMore = result.hasMore
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(settlementsLoadingMore = false)
                }
        }
    }

    fun loadMoreExpenses() {
        if (!_state.value.expensesHasMore || _state.value.expensesLoadingMore) return
        _state.value = _state.value.copy(expensesLoadingMore = true, loadMoreError = null)
        val lastId = _state.value.expenses.lastOrNull()?.expenseId
        viewModelScope.launch {
            expenseService.getGroupExpenses(groupId, AppConstants.DEFAULT_PAGE_SIZE, lastId)
                .onSuccess { result ->
                    _state.value = _state.value.copy(
                        expenses = _state.value.expenses + result.items,
                        expensesLoadingMore = false,
                        expensesHasMore = result.hasMore,
                        loadMoreError = null
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        expensesLoadingMore = false,
                        loadMoreError = R.string.load_more_error
                    )
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
                    _state.value = _state.value.copy(deleteError = e.toStringResId())
                }
        }
    }

    fun setDeleteExpenseId(expenseId: String?) {
        _state.value = _state.value.copy(deleteExpenseId = expenseId, deleteError = null)
    }

    fun removeMember(memberUid: String) {
        viewModelScope.launch {
            groupService.removeMember(groupId, memberUid)
                .onSuccess {
                    refreshData()
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(error = e.toStringResId())
                }
        }
    }
}

private fun getUpiVpa(debt: SimplifiedDebt): String = com.trevio.android.util.PaymentUtils.getUpiVpa(debt)

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
    var debouncedQuery by remember { mutableStateOf("") }
    var offlineName by remember { mutableStateOf("") }
    var expenseSearch by remember { mutableStateOf("") }
    var categoryFilter by remember { mutableStateOf("all") }
    val context = LocalContext.current

    val needsRefresh by navController.currentBackStackEntry
        ?.savedStateHandle?.getStateFlow<Boolean>("needsRefresh", false)
        ?.collectAsState() ?: remember { mutableStateOf(false) }

    LaunchedEffect(needsRefresh) {
        if (needsRefresh) {
            viewModel.refreshData()
            navController.currentBackStackEntry?.savedStateHandle?.set("needsRefresh", false)
            navController.previousBackStackEntry?.savedStateHandle?.set("needsRefresh", true)
        }
    }

    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotBlank()) {
            delay(AppConstants.DEBOUNCE_DELAY_MS)
            debouncedQuery = searchQuery
            viewModel.searchUsers(debouncedQuery)
        } else {
            debouncedQuery = ""
            viewModel.clearSearch()
        }
    }

    val shareInviteLink = {
        val inviteCode = state.groupInfo?.inviteCode
        if (!inviteCode.isNullOrBlank()) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.group_detail_share_subject, state.groupInfo?.name ?: ""))
                putExtra(Intent.EXTRA_TEXT, context.getString(R.string.group_detail_share_text, state.groupInfo?.name ?: "", inviteCode))
            }
            context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.group_detail_share_chooser)))
        }
    }

    val isHouseholdPreLoad = state.groupInfo?.template == com.trevio.android.domain.model.GroupTemplate.HOUSEHOLD

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    navController.navigate(TrevioRoute.AddExpense.createRoute(state.groupInfo?.groupId ?: ""))
                },
                shape = RoundedCornerShape(16.dp),
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = if (isHouseholdPreLoad) stringResource(R.string.group_detail_add_entry) else stringResource(R.string.group_detail_add_expense), tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    ) { padding ->
        // Only show the full-screen spinner on a true cold start (no
        // cached data at all).  With real-time listeners + offline
        // persistence, repeat visits emit cached data instantly, so
        // the spinner is barely visible.  If we have groupInfo from
        // cache, skip the spinner and render content directly.
        if (state.isLoading && state.groupInfo == null) {
            Column(modifier = Modifier.padding(padding).background(MaterialTheme.colorScheme.background)) {
                TrevioHeader(
                    title = stringResource(R.string.group_detail_title),
                    onBack = { navController.popBackStack() }
                )
                // Skeleton placeholders instead of a spinner
                repeat(4) {
                    ListItemSkeleton()
                    Spacer(Modifier.height(8.dp))
                }
            }
            return@Scaffold
        }

        if (state.groupInfo == null) {
            Column(modifier = Modifier.padding(padding).background(MaterialTheme.colorScheme.background)) {
                TrevioHeader(
                    title = stringResource(R.string.group_detail_title),
                    onBack = { navController.popBackStack() }
                )
                Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            stringResource(state.error ?: R.string.group_detail_failed_load),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(16.dp))
                        OutlinedButton(onClick = { viewModel.loadData() }) {
                            Text(stringResource(R.string.group_detail_retry))
                        }
                    }
                }
            }
            return@Scaffold
        }

        val groupInfo = state.groupInfo
        val isAdmin = state.currentUserId == groupInfo?.createdBy ||
            state.members.find { it.uid == state.currentUserId }?.role == MemberRole.ADMIN

        val isTrip = groupInfo?.template == com.trevio.android.domain.model.GroupTemplate.TRIP
        val isHousehold = groupInfo?.template == com.trevio.android.domain.model.GroupTemplate.HOUSEHOLD

        // For household groups, use the HouseholdViewModel
        val householdViewModel: com.trevio.android.ui.household.HouseholdViewModel = hiltViewModel()
        LaunchedEffect(state.members, isHousehold) {
            if (isHousehold) {
                householdViewModel.updateMembers(state.members)
            }
        }
        val householdState by householdViewModel.state.collectAsState()
        var editingEntry by remember { mutableStateOf<Expense?>(null) }
        var viewingEntry by remember { mutableStateOf<Expense?>(null) }

        LaunchedEffect(isHousehold) {
            selectedTab = 0
        }

        Column(modifier = Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background)) {
            // ── Fixed Header (does not scroll) ──
            TrevioHeader(
                title = groupInfo?.name ?: stringResource(R.string.group_detail_title),
                onBack = { navController.popBackStack() },
                actions = {
                    if (isAdmin) {
                        IconButton(onClick = { navController.navigate(TrevioRoute.GroupSettings.createRoute(state.groupInfo?.groupId ?: "")) }) {
                            Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.group_detail_settings), tint = Color.White)
                        }
                        IconButton(onClick = { viewModel.toggleArchive() }) {
                            Icon(
                                if (groupInfo?.archived == true) Icons.Default.Unarchive else Icons.Default.Archive,
                                contentDescription = if (groupInfo?.archived == true) stringResource(R.string.group_detail_unarchive) else stringResource(R.string.group_detail_archive),
                                tint = Color.White
                            )
                        }
                    }
                    if (!state.groupInfo?.inviteCode.isNullOrBlank()) {
                        IconButton(onClick = { showQrDialog = true }) {
                            Icon(Icons.Default.QrCode2, contentDescription = stringResource(R.string.group_detail_qr_code), tint = Color.White)
                        }
                        IconButton(onClick = {
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText(context.getString(R.string.group_detail_invite_code_label), state.groupInfo?.inviteCode))
                            Toast.makeText(context, context.getString(R.string.group_detail_invite_code_copied), Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.group_detail_copy_code), tint = Color.White)
                        }
                        IconButton(onClick = { shareInviteLink() }) {
                            Icon(Icons.Default.Share, contentDescription = stringResource(R.string.group_detail_share_invite), tint = Color.White)
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
                            stringResource(R.string.group_detail_archived),
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
                    InfoChip(stringResource(R.string.group_detail_members_count, groupInfo?.memberCount ?: 0))
                    InfoChip(currencyFormatter.formatAmount(groupInfo?.totalExpenses ?: 0.0, groupInfo?.currency ?: AppConstants.BASE_CURRENCY))
                    if (!groupInfo?.inviteCode.isNullOrBlank()) {
                        InfoChip(stringResource(R.string.group_detail_code, groupInfo?.inviteCode ?: ""))
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // ── Fixed Tab Bar (does not scroll) ──
            val householdMembersTabIndex = 3
            val householdActivityTabIndex = 4
            val regularMembersTabIndex = if (isTrip) 4 else 3
            val regularActivityTabIndex = if (isTrip) 5 else 4
            val membersTabIndex = if (isHousehold) householdMembersTabIndex else regularMembersTabIndex
            val activityTabIndex = if (isHousehold) householdActivityTabIndex else regularActivityTabIndex

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
                if (isHousehold) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text(stringResource(R.string.group_detail_today), maxLines = 1) },
                        icon = { Icon(Icons.Default.Today, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text(stringResource(R.string.group_detail_monthly), maxLines = 1) },
                        icon = { Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text(stringResource(R.string.group_detail_insights), maxLines = 1) },
                        icon = { Icon(Icons.Default.BarChart, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = selectedTab == householdMembersTabIndex,
                        onClick = { selectedTab = householdMembersTabIndex },
                        text = { Text(stringResource(R.string.group_detail_members), maxLines = 1) },
                        icon = { Icon(Icons.Default.Group, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = selectedTab == householdActivityTabIndex,
                        onClick = {
                            selectedTab = householdActivityTabIndex
                            if (state.activities.isEmpty() && !state.activitiesLoading) {
                                viewModel.loadActivities()
                            }
                        },
                        text = { Text(stringResource(R.string.group_detail_activity), maxLines = 1) },
                        icon = { Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                } else {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text(stringResource(R.string.group_detail_balances), maxLines = 1) },
                        icon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text(stringResource(R.string.group_detail_expenses), maxLines = 1) },
                        icon = { Icon(Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text(stringResource(R.string.group_detail_insights), maxLines = 1) },
                        icon = { Icon(Icons.Default.BarChart, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    if (isTrip) {
                        Tab(
                            selected = selectedTab == 3,
                            onClick = { selectedTab = 3 },
                            text = { Text(stringResource(R.string.group_detail_trip), maxLines = 1) },
                            icon = { Icon(Icons.Default.Place, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                    }
                    Tab(
                        selected = selectedTab == regularMembersTabIndex,
                        onClick = { selectedTab = regularMembersTabIndex },
                        text = { Text(stringResource(R.string.group_detail_members), maxLines = 1) },
                        icon = { Icon(Icons.Default.Group, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = selectedTab == regularActivityTabIndex,
                        onClick = {
                            selectedTab = regularActivityTabIndex
                            if (state.activities.isEmpty() && !state.activitiesLoading) {
                                viewModel.loadActivities()
                            }
                        },
                        text = { Text(stringResource(R.string.group_detail_activity), maxLines = 1) },
                        icon = { Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }
            }

            // ── Scrollable Tab Content ──
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (selectedTab) {
                0 -> if (isHousehold) item {
                    com.trevio.android.ui.household.DailyTab(
                        state = householdState,
                        onFullFormClick = {
                            navController.navigate(TrevioRoute.AddExpense.createRoute(state.groupInfo?.groupId ?: ""))
                        },
                        onViewEntry = { entry -> viewingEntry = entry },
                        onEditEntry = { entry -> editingEntry = entry },
                        onDeleteEntry = { entry -> householdViewModel.deleteEntry(entry.expenseId) },
                        onPreviousDay = {
                            val cal = java.util.Calendar.getInstance().apply {
                                timeInMillis = householdState.selectedDate
                                add(java.util.Calendar.DAY_OF_YEAR, -1)
                            }
                            householdViewModel.selectDate(cal.timeInMillis)
                            viewingEntry = null
                            editingEntry = null
                        },
                        onNextDay = {
                            val cal = java.util.Calendar.getInstance().apply {
                                timeInMillis = householdState.selectedDate
                                add(java.util.Calendar.DAY_OF_YEAR, 1)
                            }
                            householdViewModel.selectDate(cal.timeInMillis)
                            viewingEntry = null
                            editingEntry = null
                        }
                    )
                } else item {
                    BalancesTab(
                        members = state.members,
                        debts = state.debts,
                        currentUserId = state.currentUserId,
                        isAdmin = isAdmin,
                        formatGroupCurrency = { amount -> currencyFormatter.formatAmount(amount, state.groupInfo?.currency ?: AppConstants.BASE_CURRENCY) },
                        onSettleUp = { navController.navigate(TrevioRoute.SettleUp.createRoute(state.groupInfo?.groupId ?: "")) },
                        onMemberClick = { uid -> navController.navigate(TrevioRoute.PublicProfile.createRoute(uid)) },
                        onSettleDebt = { debt -> viewModel.settleDebt(debt) },
                        onPayViaUpi = { debt ->
                            val vpa = getUpiVpa(debt)
                            if (vpa.isNotEmpty()) {
                                val amountInInr = if (debt.currency == AppConstants.BASE_CURRENCY) {
                                    debt.amount
                                } else if (currencyFormatter.rates.isNotEmpty()) {
                                    CurrencyConverter.convertCurrency(debt.amount, debt.currency, AppConstants.BASE_CURRENCY, currencyFormatter.rates)
                                } else {
                                    Toast.makeText(context, context.getString(R.string.group_detail_rates_loading), Toast.LENGTH_SHORT).show()
                                    null
                                }
                                if (amountInInr != null) {
                                    val upiUri = "upi://pay?pa=${android.net.Uri.encode(vpa)}&pn=${android.net.Uri.encode(debt.toName)}&am=$amountInInr&cu=${AppConstants.BASE_CURRENCY}&tn=${android.net.Uri.encode("Trevio")}"
                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(upiUri))
                                    context.startActivity(android.content.Intent.createChooser(intent, context.getString(R.string.pay_with)))
                                }
                            }
                        }
                    )
                }
                1 -> if (isHousehold) item {
                    com.trevio.android.ui.household.MonthlyReportTab(
                        state = householdState,
                        onPreviousMonth = {
                            val cal = java.util.Calendar.getInstance().apply {
                                set(householdState.selectedYear, householdState.selectedMonth, 1)
                                add(java.util.Calendar.MONTH, -1)
                            }
                            householdViewModel.selectMonth(cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH))
                        },
                        onNextMonth = {
                            val cal = java.util.Calendar.getInstance().apply {
                                set(householdState.selectedYear, householdState.selectedMonth, 1)
                                add(java.util.Calendar.MONTH, 1)
                            }
                            householdViewModel.selectMonth(cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH))
                        }
                    )
                } else item {
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
                        },
                        hasMore = state.expensesHasMore && expenseSearch.isBlank() && categoryFilter == "all",
                        loadingMore = state.expensesLoadingMore,
                        loadMoreError = state.loadMoreError,
                        onLoadMore = { viewModel.loadMoreExpenses() }
                    )
                }
                2 -> item {
                    AnalyticsTab(
                        groupId = state.groupInfo?.groupId ?: "",
                        groupName = state.groupInfo?.name ?: stringResource(R.string.group_detail_title),
                        expenses = state.expenses,
                        members = state.members,
                        groupCurrency = state.groupInfo?.currency ?: AppConstants.BASE_CURRENCY
                    )
                }
                3 -> if (isTrip && !isHousehold) item {
                    TripTab(
                        groupId = state.groupInfo?.groupId ?: "",
                        groupCurrency = state.groupInfo?.currency ?: AppConstants.BASE_CURRENCY
                    )
                } else if (isHousehold) item {
                    MembersTab(
                        members = state.members,
                        currentUserId = state.currentUserId,
                        onInvite = { showInviteDialog = true },
                        onMemberClick = { uid ->
                            if (state.members.find { it.uid == uid }?.isOffline != true) {
                                navController.navigate(TrevioRoute.PublicProfile.createRoute(uid))
                            }
                        },
                        onAddOffline = { showAddOfflineDialog = true },
                        onRemoveMember = { uid -> viewModel.removeMember(uid) }
                    )
                }
                membersTabIndex -> if (!isHousehold) item {
                    MembersTab(
                        members = state.members,
                        currentUserId = state.currentUserId,
                        onInvite = { showInviteDialog = true },
                        onMemberClick = { uid ->
                            if (state.members.find { it.uid == uid }?.isOffline != true) {
                                navController.navigate(TrevioRoute.PublicProfile.createRoute(uid))
                            }
                        },
                        onAddOffline = { showAddOfflineDialog = true },
                        onRemoveMember = { uid -> viewModel.removeMember(uid) }
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
                        formatGroupCurrency = { amount -> currencyFormatter.formatAmount(amount, state.groupInfo?.currency ?: AppConstants.BASE_CURRENCY) },
                        formatDate = currencyFormatter.formatDate,
                        onLoadSettlements = { viewModel.loadSettlements() },
                        activitiesHasMore = state.activitiesHasMore,
                        activitiesLoadingMore = state.activitiesLoadingMore,
                        onLoadMoreActivities = { viewModel.loadMoreActivities() },
                        settlementsHasMore = state.settlementsHasMore,
                        settlementsLoadingMore = state.settlementsLoadingMore,
                        onLoadMoreSettlements = { viewModel.loadMoreSettlements() }
                    )
                }
            }
        }
        } // end Column

        if (showInviteDialog) {
            AlertDialog(
                onDismissRequest = {
                    showInviteDialog = false
                    searchQuery = ""
                    debouncedQuery = ""
                    viewModel.clearSearch()
                },
                title = { Text(stringResource(R.string.group_detail_invite_member)) },
                text = {
                    Column {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            label = { Text(stringResource(R.string.group_detail_search_by_username)) },
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
                                        Text(user.displayName + if (user.uid == state.currentUserId) stringResource(R.string.group_detail_you_suffix) else "", style = MaterialTheme.typography.bodyMedium)
                                        Text("@${user.username}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    IconButton(onClick = {
                                        viewModel.inviteMember(user.username)
                                        searchQuery = ""
                                    }) {
                                        Icon(Icons.Default.PersonAdd, contentDescription = stringResource(R.string.group_detail_invite_btn), tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                        if (state.inviteError != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(stringResource(state.inviteError!!), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        showInviteDialog = false
                        searchQuery = ""
                        viewModel.clearSearch()
                    }) { Text(stringResource(R.string.common_done)) }
                }
            )
        }

        if (showAddOfflineDialog) {
            AlertDialog(
                onDismissRequest = {
                    showAddOfflineDialog = false
                    offlineName = ""
                },
                title = { Text(stringResource(R.string.group_detail_add_offline)) },
                text = {
                    Column {
                        Text(
                            stringResource(R.string.group_detail_add_offline_msg),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = offlineName,
                            onValueChange = { offlineName = it },
                            label = { Text(stringResource(R.string.create_group_offline_name)) },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        if (state.inviteError != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(stringResource(state.inviteError!!), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
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
                    ) { Text(stringResource(R.string.group_detail_add)) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showAddOfflineDialog = false
                        offlineName = ""
                    }) { Text(stringResource(R.string.group_detail_cancel)) }
                }
            )
        }

        if (showQrDialog && state.groupInfo?.inviteCode != null) {
            GroupQrCodeDialog(
                groupName = state.groupInfo?.name ?: stringResource(R.string.group_detail_title),
                inviteCode = state.groupInfo?.inviteCode ?: "",
                onDismiss = { showQrDialog = false }
            )
        }

        if (state.deleteExpenseId != null) {
            AlertDialog(
                onDismissRequest = { viewModel.setDeleteExpenseId(null) },
                title = { Text(stringResource(R.string.group_detail_delete_expense_title)) },
                text = {
                    Column {
                        Text(stringResource(R.string.group_detail_delete_expense_msg))
                        if (state.deleteError != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(stringResource(state.deleteError!!), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = { viewModel.deleteExpense(state.deleteExpenseId!!) },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text(stringResource(R.string.group_detail_delete)) }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.setDeleteExpenseId(null) }) { Text(stringResource(R.string.group_detail_cancel)) }
                }
            )
        }

        // Household edit entry bottom sheet
        editingEntry?.let { entry ->
            if (isHousehold) {
                com.trevio.android.ui.household.EditEntrySheet(
                    entry = entry,
                    members = state.members,
                    isSaving = householdState.isSaving,
                    canEdit = entry.createdBy == state.currentUserId || isAdmin,
                    currencySymbol = householdState.currencySymbol,
                    onUpdate = { expenseId, amount, description, category, paidBy, date, note, transactionType ->
                        householdViewModel.updateEntry(
                            expenseId, amount, description, category, paidBy, date, note, transactionType
                        )
                        editingEntry = null
                    },
                    onDelete = { expenseId ->
                        householdViewModel.deleteEntry(expenseId)
                        editingEntry = null
                    },
                    onDismiss = { editingEntry = null }
                )
            }
        }

        // Household entry detail bottom sheet
        viewingEntry?.let { entry ->
            if (isHousehold) {
                com.trevio.android.ui.household.EntryDetailSheet(
                    entry = entry,
                    members = state.members,
                    onEdit = {
                        val e = entry
                        viewingEntry = null
                        editingEntry = e
                    },
                    onDelete = {
                        val id = entry.expenseId
                        viewingEntry = null
                        householdViewModel.deleteEntry(id)
                    },
                    onDismiss = { viewingEntry = null },
                    currencySymbol = householdState.currencySymbol
                )
            }
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
    onDeleteExpense: (String) -> Unit = {},
    hasMore: Boolean = false,
    loadingMore: Boolean = false,
    @StringRes loadMoreError: Int? = null,
    onLoadMore: () -> Unit = {}
) {
    val categories = listOf("all", "food", "transport", "shopping", "turf", "accommodation", "other")
    val hasExpenses = expenses.isNotEmpty() || expenseSearch.isNotBlank() || categoryFilter != "all"

    // Load more when all current expenses are shown and there are more available.
    // (Previously used scroll position detection, but this tab is rendered inside
    // a LazyColumn so verticalScroll is not allowed — we trigger load more
    // immediately when we have expenses and hasMore is true.)
    LaunchedEffect(hasMore, loadingMore, expenses.size, expenseSearch, categoryFilter) {
        if (hasMore && !loadingMore && expenseSearch.isBlank() && categoryFilter == "all" && expenses.isNotEmpty()) {
            onLoadMore()
        }
    }

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
                Text(stringResource(R.string.group_detail_no_expenses), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                Text(stringResource(R.string.group_detail_no_expenses_tap), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
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
                    placeholder = { Text(stringResource(R.string.group_detail_search_expenses)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categories, key = { it }) { cat ->
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
                        Text(stringResource(R.string.group_detail_no_expenses_filters), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        TextButton(onClick = { onExpenseSearchChange(""); onCategoryFilterChange("all") }) { Text(stringResource(R.string.group_detail_clear_filters)) }
                    }
                }
            } else {
                expenses.forEach { expense ->
                    ExpenseCard(expense, members, currentUserId, formatOriginal, onEditExpense, onDeleteExpense)
                }
                if (loadingMore) {
                    LoadingIndicator(modifier = Modifier.fillMaxWidth().padding(16.dp))
                }
                if (loadMoreError != null) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            stringResource(loadMoreError),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        TextButton(onClick = onLoadMore) {
                            Text(stringResource(R.string.group_detail_retry))
                        }
                    }
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
    val payerName = payer?.displayName?.split(" ")?.firstOrNull() ?: stringResource(R.string.someone)
    val isPayerMe = payer?.uid == currentUserId
    val displayPayerName = if (isPayerMe) stringResource(R.string.you) else payerName
    val myShare = currentUserId?.let { expense.splits[it]?.amount }
    val canEdit = expense.createdBy == currentUserId || members.find { it.uid == currentUserId }?.role == MemberRole.ADMIN
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
        "food" -> if (isDark) CategoryFoodDark else CategoryFood
        "transport" -> if (isDark) CategoryTransportDark else CategoryTransport
        "shopping" -> if (isDark) CategoryShoppingDark else CategoryShopping
        "turf" -> if (isDark) CategoryTurfDark else CategoryTurf
        "accommodation" -> if (isDark) CategoryAccommodationDark else CategoryAccommodation
        else -> if (isDark) CategoryOtherDark else CategoryOther
    }
    val iconBgAlpha = if (isDark) 0.15f else 0.12f
    val categoryLabelResId = when (expense.category) {
        "food" -> R.string.cat_food
        "transport" -> R.string.cat_transport
        "shopping" -> R.string.cat_shopping
        "turf" -> R.string.cat_turf
        "accommodation" -> R.string.cat_accommodation
        else -> R.string.cat_other
    }
    val displayDescription = expense.description.ifBlank { stringResource(categoryLabelResId) }
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
                    Text(displayDescription, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    if (expense.recurring != null) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.Repeat, contentDescription = stringResource(R.string.group_detail_recurring), modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
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
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.common_edit), modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { onDeleteExpense(expense.expenseId) }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.group_detail_delete), modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
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
    isAdmin: Boolean,
    formatGroupCurrency: (Double) -> String,
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

    fun formatNames(names: List<String>): String {
        if (names.isEmpty()) return ""
        if (names.size == 1) return names[0]
        if (names.size == 2) return "${names[0]} & ${names[1]}"
        return "${names.dropLast(1).joinToString(", ")} & ${names.last()}"
    }
    val creditorNames = myDebts.map { it.toName.split(" ").firstOrNull() ?: it.toName }
    val debtorNames = myCredits.map { it.fromName.split(" ").firstOrNull() ?: it.fromName }

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
                    Text(stringResource(R.string.group_detail_your_balance), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formatGroupCurrency(myBalance),
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
                            stringResource(R.string.group_detail_you_will_pay_names, formatNames(creditorNames), formatGroupCurrency(totalOwed)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    if (myCredits.isNotEmpty()) {
                        Text(
                            stringResource(R.string.group_detail_names_will_pay_you, formatNames(debtorNames), formatGroupCurrency(totalOwing)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (myDebts.isEmpty() && myCredits.isEmpty()) {
                        Text(
                            stringResource(R.string.group_detail_all_settled_group),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

        // ── Section 1: You will pay ──
        val myDebtsList = debts.filter { it.fromUid == currentUserId }
        val myCreditsList = debts.filter { it.toUid == currentUserId }
        val otherDebts = debts.filter { it.fromUid != currentUserId && it.toUid != currentUserId }
        val sortedMembers = remember(members) { members.sortedByDescending { Math.abs(it.balance) } }
        val unsettledCount = sortedMembers.count { Math.abs(it.balance) > 0.01 }
        val settledCount = sortedMembers.size - unsettledCount

        if (myDebtsList.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(8.dp).background(MaterialTheme.colorScheme.error, CircleShape))
                Text(stringResource(R.string.group_detail_you_will_pay), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
                Text(formatGroupCurrency(myDebtsList.sumOf { it.amount }), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
            }
            myDebtsList.forEach { debt ->
                val toMember = members.find { it.uid == debt.toUid }
                val paymentVpa = getUpiVpa(debt)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.05f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MemberAvatar(name = debt.toName ?: "", photoURL = toMember?.photoURL ?: "", size = 40)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.group_detail_you_will_pay_name, (debt.toName ?: "").split(" ").firstOrNull() ?: (debt.toName ?: "")),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                formatGroupCurrency(debt.amount),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            if (paymentVpa.isNotEmpty()) {
                                Text(
                                    stringResource(R.string.group_detail_pay_to, paymentVpa),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (paymentVpa.isNotEmpty()) {
                                OutlinedButton(
                                    onClick = { onPayViaUpi(debt) },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(stringResource(R.string.group_detail_pay_via_upi), style = MaterialTheme.typography.labelMedium)
                                }
                            }
                            Button(
                                onClick = { onSettleDebt(debt) },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text(stringResource(R.string.group_detail_paid_by_you), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }

        // ── Section 2: You will get ──
        if (myCreditsList.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                Text(stringResource(R.string.group_detail_you_will_get), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                Text(formatGroupCurrency(myCreditsList.sumOf { it.amount }), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
            }
            myCreditsList.forEach { debt ->
                val fromMember = members.find { it.uid == debt.fromUid }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MemberAvatar(name = debt.fromName ?: "", photoURL = fromMember?.photoURL ?: "", size = 40)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.group_detail_name_will_pay_you, (debt.fromName ?: "").split(" ").firstOrNull() ?: (debt.fromName ?: "")),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                formatGroupCurrency(debt.amount),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        OutlinedButton(
                            onClick = { onSettleDebt(debt) },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(stringResource(R.string.group_detail_received_from_name, (debt.fromName ?: "").split(" ").firstOrNull() ?: (debt.fromName ?: "")), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }

        // ── Section 3: Admin — settle between others ──
        if (isAdmin && otherDebts.isNotEmpty()) {
            Text(stringResource(R.string.group_detail_settle_between_members, otherDebts.size), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
            otherDebts.forEach { debt ->
                val fromFirst = (debt.fromName ?: "").split(" ").firstOrNull() ?: (debt.fromName ?: "")
                val toFirst = (debt.toName ?: "").split(" ").firstOrNull() ?: (debt.toName ?: "")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "$fromFirst ${stringResource(R.string.group_detail_will_pay)} $toFirst",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                formatGroupCurrency(debt.amount),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(
                            onClick = { onSettleDebt(debt) },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(stringResource(R.string.group_detail_paid_by_name, fromFirst), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MembersTab(members: List<Member>, currentUserId: String?, onInvite: () -> Unit, onMemberClick: (String) -> Unit, onAddOffline: () -> Unit, onRemoveMember: (String) -> Unit = {}) {
    var memberToRemove by remember { mutableStateOf<Pair<String, String>?>(null) }

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.group_detail_members_with_count, members.size), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAddOffline, shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.group_detail_add))
                }
                Button(onClick = onInvite, shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.group_detail_invite_btn))
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
                        Text(member.displayName + if (member.uid == currentUserId) stringResource(R.string.group_detail_you_suffix) else "", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                        if (member.isOffline) {
                            Text(stringResource(R.string.group_detail_offline_member), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Text("@${member.username}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (member.isOffline) {
                        AssistChip(
                            onClick = {},
                            leadingIcon = { Icon(Icons.Default.CloudOff, contentDescription = null, modifier = Modifier.size(14.dp)) },
                            label = { Text(stringResource(R.string.group_detail_offline_label), style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                    if (member.status == "pending") {
                        AssistChip(
                            onClick = {},
                            leadingIcon = { Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(14.dp)) },
                            label = { Text(stringResource(R.string.group_detail_pending_label), style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                    if (member.role == MemberRole.ADMIN) {
                        Spacer(modifier = Modifier.width(4.dp))
                        AssistChip(onClick = {}, label = { Text(stringResource(R.string.group_detail_admin_label), style = MaterialTheme.typography.labelSmall) })
                    }
                    val isCurrentUserAdmin = members.find { it.uid == currentUserId }?.role == MemberRole.ADMIN
                    if (isCurrentUserAdmin && !member.isOffline && member.uid != currentUserId && member.role != MemberRole.ADMIN) {
                        IconButton(
                            onClick = { memberToRemove = member.uid to member.displayName },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.PersonRemove, contentDescription = stringResource(R.string.group_detail_remove_member), modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
                        }
                    }
                }
            }
        }
    }

    memberToRemove?.let { (uid, name) ->
        AlertDialog(
            onDismissRequest = { memberToRemove = null },
            title = { Text(stringResource(R.string.group_detail_remove_confirm, name)) },
            text = {
                Text(stringResource(R.string.group_detail_remove_msg))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemoveMember(uid)
                        memberToRemove = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.common_remove)) }
            },
            dismissButton = {
                TextButton(onClick = { memberToRemove = null }) { Text(stringResource(R.string.group_detail_cancel)) }
            }
        )
    }
}

@Composable
private fun ActivityTab(
    activities: List<Activity>,
    settlements: List<Settlement>,
    settlementsLoading: Boolean,
    @StringRes settlementsError: Int?,
    currentUserId: String?,
    isLoading: Boolean,
    @StringRes errorMessage: Int? = null,
    formatGroupCurrency: (Double) -> String,
    formatDate: (Long, Boolean) -> String,
    onLoadSettlements: () -> Unit,
    activitiesHasMore: Boolean = false,
    activitiesLoadingMore: Boolean = false,
    onLoadMoreActivities: () -> Unit = {},
    settlementsHasMore: Boolean = false,
    settlementsLoadingMore: Boolean = false,
    onLoadMoreSettlements: () -> Unit = {}
) {
    var activityFilter by remember { mutableStateOf("all") }

    LaunchedEffect(activityFilter) {
        if (activityFilter == "settlements" && settlements.isEmpty() && !settlementsLoading) {
            onLoadSettlements()
        }
    }

    // Load more when there are more items available. Previously used scroll
    // position detection, but this tab is inside a LazyColumn so verticalScroll
    // is not allowed — we trigger load more immediately when items exist and
    // hasMore is true.
    LaunchedEffect(activityFilter, settlementsHasMore, settlementsLoadingMore, settlements.size, activitiesHasMore, activitiesLoadingMore, activities.size) {
        if (activityFilter == "settlements") {
            if (settlementsHasMore && !settlementsLoadingMore && settlements.isNotEmpty()) {
                onLoadMoreSettlements()
            }
        } else {
            if (activitiesHasMore && !activitiesLoadingMore && activities.isNotEmpty()) {
                onLoadMoreActivities()
            }
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
                label = { Text(stringResource(R.string.group_detail_all)) }
            )
            FilterChip(
                selected = activityFilter == "settlements",
                onClick = { activityFilter = "settlements" },
                label = { Text(stringResource(R.string.group_detail_settlements)) }
            )
        }

        if (activityFilter == "settlements") {
            if (settlementsLoading) {
                LoadingIndicator(modifier = Modifier.fillMaxWidth().padding(40.dp))
            } else if (settlementsError != null) {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(settlementsError), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            } else if (settlements.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(stringResource(R.string.group_detail_no_settlements), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    }
                }
            } else {
                val isDark = isSystemInDarkTheme()
                val settlementColor = if (isDark) BalancePositiveDark else BalancePositive
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    settlements.forEach { s ->
                        val isFromMe = currentUserId == s.fromUid
                        val isToMe = currentUserId == s.toUid
                        val fromName = if (isFromMe) stringResource(R.string.you) else s.fromName.split(" ").firstOrNull() ?: s.fromName
                        val toName = if (isToMe) stringResource(R.string.you_lowercase) else s.toName.split(" ").firstOrNull() ?: s.toName
                        val paidText = stringResource(R.string.settlement_paid, fromName, toName)
                        val refText = if (s.upiRefId.isNotBlank()) stringResource(R.string.settlement_ref, s.upiRefId) else null
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
                                    Text(paidText, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        buildString {
                                            if (s.date > 0) append(formatDate(s.date, false))
                                            append(" · ${s.method.name.lowercase()}")
                                            if (refText != null) append(" · $refText")
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(formatGroupCurrency(s.amount), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = settlementColor)
                            }
                        }
                    }
                    if (settlementsLoadingMore) {
                        LoadingIndicator(modifier = Modifier.fillMaxWidth().padding(16.dp))
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
                        Text(stringResource(R.string.group_detail_failed_load_activity), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(stringResource(errorMessage), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else if (activities.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(stringResource(R.string.group_detail_no_recent_activity), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
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
                                        Text(activity.userName + if (activity.userId == currentUserId) stringResource(R.string.group_detail_you_suffix) else "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("·", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(formatRelativeTimeText(activity.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                    if (activitiesLoadingMore) {
                        LoadingIndicator(modifier = Modifier.fillMaxWidth().padding(16.dp))
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
        "expense_added", "expense_updated", "expense_deleted" -> Icons.Default.Receipt to if (isDark) TemplateCasualDark else TemplateCasual
        "income_added", "income_updated", "income_deleted" -> Icons.Default.TrendingUp to if (isDark) BalancePositiveDark else BalancePositive
        "settlement_added" -> Icons.Default.AccountBalanceWallet to if (isDark) BalancePositiveDark else BalancePositive
        "member_joined" -> Icons.Default.PersonAdd to if (isDark) TemplateTripDark else TemplateTrip
        "member_left" -> Icons.Default.PersonRemove to if (isDark) BalanceNegativeDark else BalanceNegative
        "member_removed" -> Icons.Default.PersonRemove to if (isDark) BalanceNegativeDark else BalanceNegative
        "group_created" -> Icons.Default.Group to if (isDark) TemplateHouseholdDark else TemplateHousehold
        else -> Icons.Default.Info to if (isDark) CategoryOtherDark else CategoryOther
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

