package com.trevio.android.ui.expense

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trevio.android.core.designsystem.components.TrevioHeader
import com.trevio.android.core.designsystem.theme.*
import com.trevio.android.domain.model.ItemizedSplitData
import com.trevio.android.domain.model.SplitEntry
import com.trevio.android.domain.model.SplitType
import com.trevio.android.domain.model.TransactionType
import com.trevio.android.domain.repository.AuthService
import com.trevio.android.domain.repository.ExpenseService
import com.trevio.android.domain.repository.SettlementService
import com.trevio.android.domain.model.Member
import com.trevio.android.util.AppConstants
import com.trevio.android.util.CurrencyConverter
import com.trevio.android.util.HouseholdCategories
import com.trevio.android.util.MemberRole
import com.trevio.android.util.MemberStatus
import com.trevio.android.util.toStringResId
import com.trevio.android.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EditExpenseViewModel @Inject constructor(
    private val expenseService: ExpenseService,
    private val settlementService: SettlementService,
    private val authService: AuthService,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val groupId: String = savedStateHandle.get<String>("groupId") ?: ""
    private val expenseId: String = savedStateHandle.get<String>("expenseId") ?: ""

    data class EditState(
        val isLoading: Boolean = true,
        val members: List<Member> = emptyList(),
        val currentUserId: String? = null,
        val canEdit: Boolean = false,
        val notFound: Boolean = false,
        val description: String = "",
        val amount: String = "",
        val currency: String = AppConstants.BASE_CURRENCY,
        val category: String = AppConstants.DEFAULT_CATEGORY,
        val splitType: SplitType = SplitType.EQUAL,
        val paidByUid: String = "",
        val note: String = "",
        val splitValues: Map<String, String> = emptyMap(),
        val itemizedData: ItemizedSplitData = ItemizedSplitData(),
        val transactionType: TransactionType = TransactionType.EXPENSE,
        val isSaving: Boolean = false,
        val isDeleting: Boolean = false,
        @StringRes val error: Int? = null,
        val loaded: Boolean = false
    )

    private val _state = MutableStateFlow(EditState())
    val state: StateFlow<EditState> = _state

    init { loadData() }

    fun loadData() {
        _state.value = _state.value.copy(isLoading = true)
        viewModelScope.launch {
            val uid = authService.getCurrentUserId()
            val members = settlementService.getGroupBalances(groupId).getOrDefault(emptyList())
            val expensesResult = expenseService.getGroupExpenses(groupId, AppConstants.LARGE_PAGE_SIZE, null).getOrNull()
            val expenses = expensesResult?.items ?: emptyList()
            val expense = expenses.find { it.expenseId == expenseId }

            if (expense == null) {
                _state.value = _state.value.copy(isLoading = false, notFound = true)
                return@launch
            }

            val member = members.find { it.uid == uid }
            val canEdit = expense.createdBy == uid || member?.role == MemberRole.ADMIN

            val splitVals = mutableMapOf<String, String>()
            if (expense.splitType != SplitType.EQUAL && expense.splitType != SplitType.ITEMIZED) {
                for ((uid2, split) in expense.splits) {
                    splitVals[uid2] = if (split.shareValue > 0) split.shareValue.toString() else split.amount.toString()
                }
            }

            _state.value = EditState(
                isLoading = false,
                members = members,
                currentUserId = uid,
                canEdit = canEdit,
                description = expense.description,
                amount = expense.amount.toString(),
                currency = expense.currency,
                category = expense.category,
                splitType = expense.splitType,
                paidByUid = expense.paidBy,
                note = expense.note,
                splitValues = splitVals,
                itemizedData = expense.itemizedData ?: ItemizedSplitData(),
                transactionType = expense.transactionType,
                loaded = true
            )
        }
    }

    fun updateDescription(v: String) { if (v.length <= 500) _state.value = _state.value.copy(description = v) }
    fun updateAmount(v: String) { _state.value = _state.value.copy(amount = v.filter { it.isDigit() || it == '.' }) }
    fun updateCategory(v: String) { _state.value = _state.value.copy(category = v) }
    fun updateSplitType(v: SplitType) { _state.value = _state.value.copy(splitType = v) }
    fun updatePaidBy(v: String) { _state.value = _state.value.copy(paidByUid = v) }
    fun updateNote(v: String) { if (v.length <= 500) _state.value = _state.value.copy(note = v) }
    fun updateSplitValue(uid: String, v: String) { _state.value = _state.value.copy(splitValues = _state.value.splitValues + (uid to v.filter { it.isDigit() || it == '.' })) }
    fun updateItemizedData(v: ItemizedSplitData) { _state.value = _state.value.copy(itemizedData = v) }

    fun save(onDone: () -> Unit) {
        val s = _state.value
        val numericAmount = s.amount.toDoubleOrNull() ?: 0.0
        if (s.description.isBlank() || numericAmount <= 0.0) {
            _state.value = s.copy(error = R.string.edit_expense_error)
            return
        }
        if (s.splitType == SplitType.ITEMIZED) {
            if (s.itemizedData.items.isEmpty() || s.itemizedData.items.any { it.name.isBlank() || it.amount <= 0.0 || it.assignedTo.isEmpty() }) {
                _state.value = s.copy(error = R.string.edit_expense_itemized_error)
                return
            }
            val itemsTotal = s.itemizedData.items.filter { it.assignedTo.isNotEmpty() }.sumOf { it.amount }
            val grandTotal = if (itemsTotal > 0) itemsTotal + s.itemizedData.taxAmount + s.itemizedData.tipAmount else 0.0
            if (kotlin.math.abs(grandTotal - numericAmount) > 0.01) {
                _state.value = s.copy(error = R.string.edit_expense_itemized_total_error)
                return
            }
        }
        if (s.splitType != SplitType.EQUAL && s.splitType != SplitType.ITEMIZED) {
            val activeMembers = s.members.filter { it.status == MemberStatus.ACTIVE }
            val splitSum = activeMembers.sumOf { s.splitValues[it.uid]?.toDoubleOrNull() ?: 0.0 }
            when (s.splitType) {
                SplitType.EXACT -> {
                    if (kotlin.math.abs(splitSum - numericAmount) > 0.01) {
                        _state.value = s.copy(error = R.string.edit_expense_exact_total_error)
                        return
                    }
                }
                SplitType.PERCENT -> {
                    if (kotlin.math.abs(splitSum - 100.0) > 0.01) {
                        _state.value = s.copy(error = R.string.edit_expense_percent_total_error)
                        return
                    }
                }
                SplitType.SHARES -> {
                    if (splitSum <= 0.0) {
                        _state.value = s.copy(error = R.string.edit_expense_shares_error)
                        return
                    }
                }
                else -> {}
            }
        }
        _state.value = s.copy(isSaving = true, error = null)
        viewModelScope.launch {
            val activeMembers = s.members.filter { it.status == MemberStatus.ACTIVE }
            val splits = mutableMapOf<String, SplitEntry>()
            if (s.splitType != SplitType.EQUAL && s.splitType != SplitType.ITEMIZED) {
                for (m in activeMembers) {
                    val v = s.splitValues[m.uid]?.toDoubleOrNull() ?: 0.0
                    if (v > 0) {
                        splits[m.uid] = if (s.splitType == SplitType.EXACT) SplitEntry(amount = v, shareValue = 0.0)
                                        else SplitEntry(amount = 0.0, shareValue = v)
                    }
                }
            }
            expenseService.updateExpense(
                groupId = groupId,
                expenseId = expenseId,
                description = s.description,
                amount = numericAmount,
                currency = s.currency,
                paidBy = s.paidByUid.ifBlank { activeMembers.find { it.uid == s.currentUserId }?.uid ?: activeMembers.firstOrNull()?.uid ?: "" },
                splitType = s.splitType,
                splits = splits,
                memberUids = activeMembers.map { it.uid },
                category = s.category,
                date = 0,
                note = s.note,
                itemizedData = if (s.splitType == SplitType.ITEMIZED) s.itemizedData else null,
                transactionType = s.transactionType
            ).onSuccess {
                _state.value = s.copy(isSaving = false)
                onDone()
            }.onFailure { e ->
                _state.value = s.copy(isSaving = false, error = e.toStringResId())
            }
        }
    }

    fun delete(onDone: () -> Unit) {
        _state.value = _state.value.copy(isDeleting = true, error = null)
        viewModelScope.launch {
            expenseService.deleteExpense(groupId, expenseId)
                .onSuccess {
                    _state.value = _state.value.copy(isDeleting = false)
                    onDone()
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(isDeleting = false, error = e.toStringResId())
                }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditExpenseScreen(
    navController: androidx.navigation.NavHostController,
    viewModel: EditExpenseViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TrevioHeader(
            title = stringResource(R.string.edit_expense_title),
            onBack = { navController.popBackStack() },
            actions = {
                if (state.canEdit && state.loaded) {
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.edit_expense_delete), tint = Color.White)
                    }
                }
            }
        )

        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (state.notFound) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.edit_expense_not_found), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else if (!state.canEdit) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.edit_expense_cannot_edit), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(R.string.edit_expense_admin_can_edit), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (state.error != null) {
                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp)) {
                        Text(stringResource(state.error!!), modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }

                val activeMembers = state.members.filter { it.status == MemberStatus.ACTIVE }

                val isSplitValid = when (state.splitType) {
                    SplitType.EQUAL -> activeMembers.isNotEmpty()
                    SplitType.ITEMIZED -> {
                        state.itemizedData.items.isNotEmpty() &&
                            state.itemizedData.items.all { it.name.isNotBlank() && it.amount > 0.0 && it.assignedTo.isNotEmpty() }
                    }
                    else -> state.amount.isNotBlank() && activeMembers.isNotEmpty()
                }

                val currencySymbol = remember(state.currency) {
                    CurrencyConverter.getCurrencySymbol(state.currency)
                }

                OutlinedTextField(
                    value = state.amount,
                    onValueChange = { viewModel.updateAmount(it) },
                    label = { Text(stringResource(R.string.edit_expense_amount_label, currencySymbol)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = state.description,
                    onValueChange = { viewModel.updateDescription(it) },
                    label = { Text(stringResource(R.string.edit_expense_description)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Text(stringResource(R.string.edit_expense_category), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HouseholdCategories.DEFAULT_CATEGORIES.forEach { cat ->
                        FilterChip(
                            selected = state.category == cat,
                            onClick = { viewModel.updateCategory(cat) },
                            label = { Text(stringResource(HouseholdCategories.DEFAULT_CATEGORY_LABELS[cat] ?: R.string.cat_other)) }
                        )
                    }
                }

                OutlinedTextField(
                    value = state.note,
                    onValueChange = { viewModel.updateNote(it) },
                    label = { Text(stringResource(R.string.edit_expense_note)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 3,
                    shape = RoundedCornerShape(12.dp)
                )

                Text(stringResource(R.string.edit_expense_paid_by), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    activeMembers.forEach { m ->
                        FilterChip(
                            selected = (state.paidByUid.ifBlank { activeMembers.find { it.uid == state.currentUserId }?.uid ?: "" }) == m.uid,
                            onClick = { viewModel.updatePaidBy(m.uid) },
                            label = { Text(m.displayName.split(" ").firstOrNull() ?: m.displayName + if (m.uid == state.currentUserId) " (${stringResource(R.string.edit_expense_you)})" else "") }
                        )
                    }
                }

                Text(stringResource(R.string.edit_expense_split_method), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SplitType.entries.forEach { st ->
                        FilterChip(
                            selected = state.splitType == st,
                            onClick = { viewModel.updateSplitType(st) },
                            label = {
                                Text(
                                    when (st) {
                                        SplitType.EQUAL -> stringResource(R.string.edit_expense_equal)
                                        SplitType.EXACT -> stringResource(R.string.edit_expense_exact)
                                        SplitType.PERCENT -> stringResource(R.string.edit_expense_percent)
                                        SplitType.SHARES -> stringResource(R.string.edit_expense_shares)
                                        SplitType.ITEMIZED -> stringResource(R.string.edit_expense_itemized)
                                    }
                                )
                            }
                        )
                    }
                }

                if (state.splitType == SplitType.ITEMIZED && activeMembers.isNotEmpty()) {
                    ItemizedSplitEditor(
                        members = activeMembers,
                        currencySymbol = currencySymbol,
                        itemizedData = state.itemizedData,
                        onItemizedDataChange = { viewModel.updateItemizedData(it) },
                        expenseAmount = state.amount.toDoubleOrNull() ?: 0.0
                    )
                }

                if (state.splitType != SplitType.EQUAL && state.splitType != SplitType.ITEMIZED && activeMembers.isNotEmpty()) {
                    Surface(shape = RoundedCornerShape(12.dp), tonalElevation = 1.dp) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            activeMembers.forEach { m ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        m.displayName + if (m.uid == state.currentUserId) " (${stringResource(R.string.edit_expense_you)})" else "",
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = state.splitValues[m.uid] ?: "",
                                        onValueChange = { viewModel.updateSplitValue(m.uid, it) },
                                        modifier = Modifier.width(80.dp),
                                        singleLine = true,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        viewModel.save {
                            navController.previousBackStackEntry?.savedStateHandle?.set("needsRefresh", true)
                            navController.popBackStack()
                        }
                    },
                    enabled = !state.isSaving && state.description.isNotBlank() && state.amount.isNotBlank() && isSplitValid,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (state.isSaving) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    else Text(stringResource(R.string.edit_expense_save))
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.edit_expense_delete_confirm)) },
            text = { Text(stringResource(R.string.edit_expense_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.delete { navController.popBackStack() }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.edit_expense_delete)) }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.common_cancel)) } }
        )
    }
}
