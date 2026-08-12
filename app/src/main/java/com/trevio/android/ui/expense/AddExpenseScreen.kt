package com.trevio.android.ui.expense

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trevio.android.core.designsystem.components.TrevioHeader
import com.trevio.android.core.designsystem.theme.*
import com.trevio.android.domain.model.BillItem
import com.trevio.android.domain.model.ItemizedSplitData
import com.trevio.android.domain.model.SplitEntry
import com.trevio.android.domain.model.SplitType
import com.trevio.android.domain.model.TransactionType
import com.trevio.android.domain.repository.AuthService
import com.trevio.android.domain.repository.ExpenseService
import com.trevio.android.domain.repository.GroupService
import com.trevio.android.domain.repository.SettlementService
import com.trevio.android.util.CurrencyConverter
import com.trevio.android.util.ExpressionParser
import com.trevio.android.util.AppConstants
import com.trevio.android.util.SplitBuilder
import com.trevio.android.util.HouseholdCategories
import com.trevio.android.util.MemberStatus
import com.trevio.android.util.toStringResId
import com.trevio.android.util.rememberCurrencyFormatter
import com.trevio.android.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class ExpenseViewModel @Inject constructor(
    private val expenseService: ExpenseService,
    private val settlementService: SettlementService,
    private val groupService: GroupService,
    private val authService: AuthService,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val groupId: String = savedStateHandle.get<String>("groupId") ?: ""

    data class ExpenseFormState(
        val isLoading: Boolean = false,
        @StringRes val error: Int? = null,
        val saved: Boolean = false,
        val members: List<com.trevio.android.domain.model.Member> = emptyList(),
        val isHousehold: Boolean = false,
        val currentUserId: String? = null
    )

    private val _state = MutableStateFlow(ExpenseFormState())
    val state: StateFlow<ExpenseFormState> = _state

    init {
        loadMembers()
        loadGroupInfo()
        loadCurrentUserId()
    }

    private fun loadCurrentUserId() {
        viewModelScope.launch {
            val uid = authService.getCurrentUserId()
            _state.value = _state.value.copy(currentUserId = uid)
        }
    }

    private fun loadMembers() {
        viewModelScope.launch {
            settlementService.getGroupBalances(groupId)
                .onSuccess { members -> _state.value = _state.value.copy(members = members) }
                .onFailure { e -> _state.value = _state.value.copy(error = e.toStringResId()) }
        }
    }

    private fun loadGroupInfo() {
        viewModelScope.launch {
            groupService.getGroupInfo(groupId)
                .onSuccess { info ->
                    _state.value = _state.value.copy(
                        isHousehold = info.template == com.trevio.android.domain.model.GroupTemplate.HOUSEHOLD
                    )
                }
        }
    }

    // ---- SplitBuilder wrappers (UI should call these, not SplitBuilder) ----

    /**
     * Compute a split summary (total entered vs. target) for the given split configuration.
     * Returns null for EQUAL splits or when there is nothing to summarize.
     */
    fun computeSplitSummary(
        splitType: SplitType,
        splitValues: Map<String, String>,
        amount: Double,
        excludedMemberUids: Set<String> = emptySet()
    ): SplitBuilder.SplitSummary? {
        val memberUids = includedMemberUids(excludedMemberUids)
        return SplitBuilder.computeSummary(splitType, splitValues, memberUids, amount)
    }

    /**
     * Validate the current split configuration.
     */
    fun isSplitValid(
        splitType: SplitType,
        splitValues: Map<String, String>,
        amount: Double,
        itemizedData: ItemizedSplitData,
        excludedMemberUids: Set<String> = emptySet()
    ): Boolean {
        val memberUids = includedMemberUids(excludedMemberUids)
        return SplitBuilder.isValid(splitType, splitValues, memberUids, amount, itemizedData)
    }

    /**
     * Build the [SplitEntry] map for the given split configuration.
     */
    fun buildSplits(
        splitType: SplitType,
        splitValues: Map<String, String>,
        excludedMemberUids: Set<String> = emptySet()
    ): Map<String, SplitEntry> {
        val memberUids = includedMemberUids(excludedMemberUids)
        return SplitBuilder.buildSplits(splitType, splitValues, memberUids)
    }

    /**
     * Validate the split, build the splits, and call [addExpense] in one step.
     * Sets [ExpenseFormState.error] and returns early when the split is invalid.
     */
    fun prepareAndSaveExpense(
        description: String,
        amount: Double,
        currency: String,
        paidBy: String,
        splitType: SplitType,
        splitValues: Map<String, String>,
        category: String,
        date: Long = System.currentTimeMillis(),
        note: String = "",
        recurring: com.trevio.android.domain.model.RecurringConfig? = null,
        itemizedData: ItemizedSplitData? = null,
        transactionType: TransactionType = TransactionType.EXPENSE,
        excludedMemberUids: Set<String> = emptySet()
    ) {
        val memberUids = includedMemberUids(excludedMemberUids)
        if (!SplitBuilder.isValid(splitType, splitValues, memberUids, amount, itemizedData ?: ItemizedSplitData())) {
            _state.value = _state.value.copy(isLoading = false, error = R.string.add_expense_split_incomplete)
            return
        }
        val splits = SplitBuilder.buildSplits(splitType, splitValues, memberUids)
        addExpense(
            description = description,
            amount = amount,
            currency = currency,
            paidBy = paidBy,
            splitType = splitType,
            splits = splits,
            memberUids = memberUids,
            category = category,
            date = date,
            note = note,
            recurring = recurring,
            itemizedData = itemizedData,
            transactionType = transactionType
        )
    }

    private fun includedMemberUids(excludedMemberUids: Set<String>): List<String> =
        _state.value.members
            .filter { it.status == MemberStatus.ACTIVE && it.uid !in excludedMemberUids }
            .map { it.uid }

    fun addExpense(
        description: String,
        amount: Double,
        currency: String,
        paidBy: String,
        splitType: SplitType,
        splits: Map<String, SplitEntry>,
        memberUids: List<String>,
        category: String,
        date: Long = System.currentTimeMillis(),
        note: String = "",
        recurring: com.trevio.android.domain.model.RecurringConfig? = null,
        itemizedData: ItemizedSplitData? = null,
        transactionType: TransactionType = TransactionType.EXPENSE
    ) {
        _state.value = _state.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            expenseService.addExpense(
                groupId = groupId,
                description = description,
                amount = amount,
                currency = currency,
                paidBy = paidBy,
                splitType = splitType,
                splits = splits,
                memberUids = memberUids,
                category = category,
                date = date,
                note = note,
                recurring = recurring,
                itemizedData = itemizedData,
                transactionType = transactionType
            ).onSuccess {
                _state.value = _state.value.copy(isLoading = false, saved = true)
            }.onFailure { e ->
                _state.value = _state.value.copy(isLoading = false, error = e.toStringResId())
            }
        }
    }

    fun resetSaved() {
        _state.value = _state.value.copy(saved = false)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddExpenseScreen(
    navController: androidx.navigation.NavHostController,
    viewModel: ExpenseViewModel = hiltViewModel()
) {
    var description by remember { mutableStateOf("") }
    var amountStr by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("other") }
    var splitType by remember { mutableStateOf(SplitType.EQUAL) }
    val state by viewModel.state.collectAsState()
    val currencyFormatter = rememberCurrencyFormatter()
    var currency by remember { mutableStateOf(currencyFormatter.userCurrency) }
    var userChangedCurrency by remember { mutableStateOf(false) }
    val members = state.members
    val isHousehold = state.isHousehold
    var isIncome by remember { mutableStateOf(false) }
    var paidByUid by remember { mutableStateOf("") }
    val splitValues = remember { mutableStateMapOf<String, String>() }
    val currentUserId = state.currentUserId
    var saveAndAddAnother by remember { mutableStateOf(false) }
    var showSuccess by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    var isRecurring by remember { mutableStateOf(false) }
    var recurringFrequency by remember { mutableStateOf(com.trevio.android.domain.model.RecurringFrequency.MONTHLY) }
    var itemizedData by remember { mutableStateOf(ItemizedSplitData()) }

    val dateFormatter = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val todayStr = remember { dateFormatter.format(Date()) }
    var expenseDateStr by remember { mutableStateOf(todayStr) }
    var showDatePicker by remember { mutableStateOf(false) }
    val excludedMembers = remember { mutableStateMapOf<String, Boolean>() }

    val currencySymbol = remember(currency) {
        CurrencyConverter.getCurrencySymbol(currency)
    }

    // Sync the form's currency with the formatter's user currency once it loads
    // asynchronously. Only update when the user hasn't manually chosen a currency,
    // so the expense is saved in the user's actual currency instead of the default.
    LaunchedEffect(currencyFormatter.userCurrency) {
        if (!userChangedCurrency && currency != currencyFormatter.userCurrency) {
            currency = currencyFormatter.userCurrency
        }
    }

    LaunchedEffect(state.saved) {
        if (state.saved) {
            navController.previousBackStackEntry?.savedStateHandle?.set("needsRefresh", true)
            showSuccess = true
            if (saveAndAddAnother) {
                delay(AppConstants.SAVE_AND_ADD_DELAY_MS)
                showSuccess = false
                viewModel.resetSaved()
                description = ""
                amountStr = ""
                category = AppConstants.DEFAULT_CATEGORY
                splitType = SplitType.EQUAL
                splitValues.clear()
                excludedMembers.clear()
                expenseDateStr = todayStr
                note = ""
                isRecurring = false
                isIncome = false
                itemizedData = ItemizedSplitData()
                saveAndAddAnother = false
            } else {
                delay(AppConstants.SAVE_AND_ADD_DELAY_MS)
                navController.popBackStack()
            }
        }
    }

    LaunchedEffect(members) {
        if (paidByUid.isEmpty() && members.isNotEmpty()) {
            val currentUserMember = members.find { it.uid == currentUserId }
            paidByUid = currentUserMember?.uid ?: members.first().uid
        }
    }

    val amount = remember(amountStr) {
        ExpressionParser.parseAmount(amountStr)
    }
    val activeMembers = members.filter { it.status == MemberStatus.ACTIVE }
    val includedMembers = activeMembers.filter { excludedMembers[it.uid] != true }
    val equalPerPerson = if (splitType == SplitType.EQUAL && amount > 0.0 && includedMembers.isNotEmpty()) amount / includedMembers.size else 0.0

    val excludedUids = remember(excludedMembers.toMap()) {
        excludedMembers.filterValues { it }.keys
    }

    val splitSummary = remember(splitType, splitValues.toMap(), amount, excludedUids) {
        viewModel.computeSplitSummary(splitType, splitValues.toMap(), amount, excludedUids)
    }

    val isSplitValid = remember(splitType, splitValues.toMap(), amount, excludedUids, itemizedData) {
        viewModel.isSplitValid(splitType, splitValues.toMap(), amount, itemizedData, excludedUids)
    }

    val effectivePaidBy = paidByUid.ifEmpty {
        activeMembers.find { it.uid == currentUserId }?.uid ?: activeMembers.firstOrNull()?.uid ?: ""
    }

    val expenseDateMillis = remember(expenseDateStr) {
        try {
            dateFormatter.parse(expenseDateStr)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TrevioHeader(
            title = if (isHousehold) stringResource(R.string.add_expense_title_household) else stringResource(R.string.add_expense_title),
            onBack = { navController.popBackStack() }
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            OutlinedTextField(
                value = amountStr,
                onValueChange = { amountStr = it.filter { c -> c.isDigit() || c == '.' || c == '+' || c == '-' || c == '*' || c == '/' } },
                label = { Text(stringResource(R.string.add_expense_amount_label, currencySymbol)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.headlineMedium,
                shape = RoundedCornerShape(12.dp)
            )
            if (amount > 0.0 && amountStr.any { it == '+' || it == '-' || it == '*' || it == '/' }) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(stringResource(R.string.add_expense_calc_result, currencySymbol, String.format(Locale.getDefault(), "%,.2f", amount)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.add_expense_quick_calc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                listOf("+" to "+", "−" to "-", "×" to "*", "÷" to "/").forEach { (label, op) ->
                    OutlinedButton(
                        onClick = { amountStr = amountStr + op },
                        modifier = Modifier.size(40.dp),
                        contentPadding = PaddingValues(0.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                if (amountStr.isNotEmpty()) {
                    TextButton(
                        onClick = { amountStr = "" },
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text(stringResource(R.string.add_expense_clear), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = description,
                onValueChange = { if (it.length <= 500) description = it },
                label = { Text(stringResource(R.string.add_expense_description)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = expenseDateStr,
                onValueChange = { },
                label = { Text(stringResource(R.string.add_expense_date)) },
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                trailingIcon = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = stringResource(R.string.add_expense_pick_date))
                    }
                }
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Spent/Received toggle for household groups
            if (isHousehold) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (!isIncome) MaterialTheme.colorScheme.error else Color.Transparent)
                            .clickable { isIncome = false }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            stringResource(R.string.add_expense_spent),
                            color = if (!isIncome) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (!isIncome) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isIncome) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { isIncome = true }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            stringResource(R.string.add_expense_received),
                            color = if (isIncome) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (isIncome) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            SectionLabel(stringResource(R.string.add_expense_category))
            Spacer(modifier = Modifier.height(8.dp))
            if (isHousehold) {
                // Household categories with icons
                val householdCats = if (isIncome) HouseholdCategories.INCOME_CATEGORIES else HouseholdCategories.EXPENSE_CATEGORIES
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    householdCats.forEach { cat ->
                        FilterChip(
                            selected = category == cat.key,
                            onClick = { category = cat.key },
                            label = { Text(stringResource(cat.labelResId)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = cat.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = cat.color
                                )
                            }
                        )
                    }
                }
            } else {
                val categories = listOf("food", "transport", "shopping", "turf", "accommodation", "other")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    categories.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat.replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            SectionLabel(stringResource(R.string.add_expense_paid_by))
            Spacer(modifier = Modifier.height(8.dp))
            if (members.isEmpty()) {
                Text(stringResource(R.string.add_expense_loading_members), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    activeMembers.forEach { member ->
                        FilterChip(
                            selected = effectivePaidBy == member.uid,
                            onClick = { paidByUid = member.uid },
                            label = {
                                val name = member.displayName.split(" ").firstOrNull() ?: ""
                                Text(if (member.uid == currentUserId) "$name (${stringResource(R.string.add_expense_you)})" else name)
                            }
                        )
                    }
                }
            }

            // Split section — hidden for household groups
            if (!isHousehold) {
            Spacer(modifier = Modifier.height(16.dp))
            SectionLabel(stringResource(R.string.add_expense_split_method))
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SplitType.values().forEach { st ->
                    FilterChip(
                        selected = splitType == st,
                        onClick = { splitType = st },
                        label = {
                            Text(
                                when (st) {
                                    SplitType.EQUAL -> stringResource(R.string.add_expense_equal)
                                    SplitType.EXACT -> stringResource(R.string.add_expense_exact)
                                    SplitType.PERCENT -> stringResource(R.string.add_expense_percent)
                                    SplitType.SHARES -> stringResource(R.string.add_expense_shares)
                                    SplitType.ITEMIZED -> stringResource(R.string.add_expense_itemized)
                                }
                            )
                        }
                    )
                }
            }

            if (splitType == SplitType.EQUAL && amount > 0.0 && activeMembers.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                stringResource(R.string.add_expense_per_person, "$currencySymbol${String.format(Locale.getDefault(), "%,.2f", equalPerPerson)}"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                stringResource(R.string.add_expense_members_count, includedMembers.size, activeMembers.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            activeMembers.forEach { member ->
                                val excluded = excludedMembers[member.uid] == true
                                FilterChip(
                                    selected = !excluded,
                                    onClick = {
                                        excludedMembers[member.uid] = !excluded
                                    },
                                    label = {
                                        val name = member.displayName.split(" ").firstOrNull() ?: ""
                                        Text(if (member.uid == currentUserId) "$name (${stringResource(R.string.add_expense_you)})" else name)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (splitType == SplitType.ITEMIZED && activeMembers.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                ItemizedSplitEditor(
                    members = activeMembers,
                    currencySymbol = currencySymbol,
                    itemizedData = itemizedData,
                    onItemizedDataChange = { itemizedData = it }
                )
            }

            if (splitType != SplitType.EQUAL && splitType != SplitType.ITEMIZED && amount > 0.0 && includedMembers.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                when (splitType) {
                                    SplitType.EXACT -> stringResource(R.string.add_expense_enter_exact)
                                    SplitType.PERCENT -> stringResource(R.string.add_expense_enter_percent)
                                    SplitType.SHARES -> stringResource(R.string.add_expense_enter_shares)
                                    else -> ""
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (splitSummary != null && splitType != SplitType.SHARES) {
                                Text(
                                    "${splitSummary.totalEntered}/${splitSummary.target}" + if (splitType == SplitType.PERCENT) "%" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isSplitValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        includedMembers.forEach { member ->
                            val value = splitValues[member.uid] ?: ""
                            val totalShares = splitValues.values.sumOf { it.toDoubleOrNull() ?: 0.0 }
                            val displayAmount = when (splitType) {
                                SplitType.PERCENT -> if (value.isNotEmpty()) " = $currencySymbol${String.format(Locale.getDefault(), "%,.2f", (value.toDoubleOrNull() ?: 0.0) / 100 * amount)}" else ""
                                SplitType.SHARES -> if (value.isNotEmpty() && totalShares > 0) " = $currencySymbol${String.format(Locale.getDefault(), "%,.2f", (value.toDoubleOrNull() ?: 0.0) / totalShares * amount)}" else ""
                                else -> ""
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val name = member.displayName.split(" ").firstOrNull() ?: ""
                                Text(if (member.uid == currentUserId) "$name (${stringResource(R.string.add_expense_you)})" else name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                if (displayAmount.isNotEmpty()) {
                                    Text(displayAmount, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                OutlinedTextField(
                                    value = value,
                                    onValueChange = { v -> splitValues[member.uid] = v.filter { c -> c.isDigit() || c == '.' } },
                                    modifier = Modifier.width(100.dp),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    textStyle = MaterialTheme.typography.bodyMedium,
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                        }
                        if (includedMembers.size < activeMembers.size) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(stringResource(R.string.add_expense_excluded), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                activeMembers.filter { excludedMembers[it.uid] == true }.forEach { member ->
                                    FilterChip(
                                        selected = false,
                                        onClick = { excludedMembers.remove(member.uid) },
                                        label = {
                                            val name = member.displayName.split(" ").firstOrNull() ?: ""
                                            Text(if (member.uid == currentUserId) "$name (${stringResource(R.string.add_expense_you)})" else name)
                                        }
                                    )
                                }
                            }
                        }
                        if (splitType == SplitType.SHARES) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(stringResource(R.string.add_expense_shares_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            } // end if (!isHousehold)

            Spacer(modifier = Modifier.height(16.dp))

            // Note field
            OutlinedTextField(
                value = note,
                onValueChange = { if (it.length <= 500) note = it },
                label = { Text(stringResource(R.string.add_expense_note)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 3,
                shape = RoundedCornerShape(12.dp)
            )

            // Recurring expense toggle
            Surface(
                shape = RoundedCornerShape(12.dp),
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp).fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = isRecurring, onCheckedChange = { isRecurring = it })
                        Icon(Icons.Default.Repeat, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.add_expense_recurring), style = MaterialTheme.typography.bodyMedium)
                    }
                    if (isRecurring) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = recurringFrequency == com.trevio.android.domain.model.RecurringFrequency.WEEKLY,
                                onClick = { recurringFrequency = com.trevio.android.domain.model.RecurringFrequency.WEEKLY },
                                label = { Text(stringResource(R.string.add_expense_weekly)) }
                            )
                            FilterChip(
                                selected = recurringFrequency == com.trevio.android.domain.model.RecurringFrequency.MONTHLY,
                                onClick = { recurringFrequency = com.trevio.android.domain.model.RecurringFrequency.MONTHLY },
                                label = { Text(stringResource(R.string.add_expense_monthly)) }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            if (state.error != null) {
                Text(stringResource(state.error!!), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        if (description.isNotBlank() && amount > 0 && effectivePaidBy.isNotEmpty()) {
                            viewModel.prepareAndSaveExpense(
                                description = description,
                                amount = amount,
                                currency = currency,
                                paidBy = effectivePaidBy,
                                splitType = splitType,
                                splitValues = splitValues.toMap(),
                                category = category,
                                date = expenseDateMillis,
                                note = note,
                                recurring = if (isRecurring) com.trevio.android.domain.model.RecurringConfig(frequency = recurringFrequency) else null,
                                itemizedData = if (splitType == SplitType.ITEMIZED) itemizedData else null,
                                transactionType = if (isIncome) TransactionType.INCOME else TransactionType.EXPENSE,
                                excludedMemberUids = excludedUids
                            )
                        }
                    },
                    enabled = description.isNotBlank() && amountStr.isNotBlank() && (isHousehold || isSplitValid) && !state.isLoading,
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                    } else {
                        Text(stringResource(R.string.add_expense_save), style = MaterialTheme.typography.titleMedium)
                    }
                }
                OutlinedButton(
                    onClick = {
                        saveAndAddAnother = true
                        if (description.isNotBlank() && amount > 0 && effectivePaidBy.isNotEmpty()) {
                            viewModel.prepareAndSaveExpense(
                                description = description,
                                amount = amount,
                                currency = currency,
                                paidBy = effectivePaidBy,
                                splitType = splitType,
                                splitValues = splitValues.toMap(),
                                category = category,
                                date = expenseDateMillis,
                                note = note,
                                recurring = if (isRecurring) com.trevio.android.domain.model.RecurringConfig(frequency = recurringFrequency) else null,
                                itemizedData = if (splitType == SplitType.ITEMIZED) itemizedData else null,
                                transactionType = if (isIncome) TransactionType.INCOME else TransactionType.EXPENSE,
                                excludedMemberUids = excludedUids
                            )
                        }
                    },
                    enabled = description.isNotBlank() && amountStr.isNotBlank() && (isHousehold || isSplitValid) && !state.isLoading,
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.add_expense_save_add), style = MaterialTheme.typography.titleMedium)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = expenseDateMillis,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(millis: Long): Boolean = millis <= System.currentTimeMillis()
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let {
                            expenseDateStr = dateFormatter.format(Date(it))
                        }
                        showDatePicker = false
                    }
                ) { Text(stringResource(R.string.common_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showSuccess) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.32f)),
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(visible = showSuccess) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(28.dp))
                                .background(SaveButtonGreen),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.add_expense_saved),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
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
