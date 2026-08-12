package com.trevio.android.ui.household

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import com.trevio.android.R
import com.trevio.android.domain.model.DailySummary
import com.trevio.android.domain.model.Expense
import com.trevio.android.domain.model.HouseholdGamification
import com.trevio.android.domain.model.Member
import com.trevio.android.domain.model.MonthlyReport
import com.trevio.android.domain.model.LocalizedString
import com.trevio.android.domain.model.SplitType
import com.trevio.android.domain.model.TransactionType
import com.trevio.android.domain.repository.AuthService
import com.trevio.android.domain.repository.ExpenseService
import com.trevio.android.domain.repository.GroupInfo
import com.trevio.android.domain.repository.GroupService
import com.trevio.android.domain.repository.ExchangeRateService
import com.trevio.android.domain.repository.SettlementService
import com.trevio.android.util.AppConstants
import com.trevio.android.util.CurrencyConverter
import com.trevio.android.util.FormatUtils
import com.trevio.android.util.HouseholdCategories
import com.trevio.android.util.computeDailySummary
import com.trevio.android.util.computeGamification
import com.trevio.android.util.computeMonthlyReport
import com.trevio.android.util.computeCategoryUsageCount
import com.trevio.android.util.suggestDescriptions
import com.trevio.android.util.toStringResId
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class HouseholdState(
    val isLoading: Boolean = true,
    val groupInfo: GroupInfo? = null,
    val expenses: List<Expense> = emptyList(),
    val members: List<Member> = emptyList(),
    val dailySummary: DailySummary? = null,
    val monthlyReport: MonthlyReport? = null,
    val gamification: HouseholdGamification? = null,
    val categoryUsage: Map<String, Int> = emptyMap(),
    val currentUserId: String? = null,
    val userCurrency: String = com.trevio.android.util.AppConstants.BASE_CURRENCY,
    val currencySymbol: String = "₹",
    val convertedBudget: Double? = null,
    val exchangeRates: Map<String, Double> = emptyMap(),
    val selectedDate: Long = System.currentTimeMillis(),
    val selectedYear: Int = Calendar.getInstance().get(Calendar.YEAR),
    val selectedMonth: Int = Calendar.getInstance().get(Calendar.MONTH),
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val lastSavedMessage: LocalizedString? = null,
    @StringRes val error: Int? = null
)

@HiltViewModel
class HouseholdViewModel @Inject constructor(
    private val expenseService: ExpenseService,
    private val groupService: GroupService,
    private val exchangeRateService: ExchangeRateService,
    private val settlementService: SettlementService,
    private val authService: AuthService,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val groupId: String = savedStateHandle.get<String>("groupId") ?: ""

    private val _state = MutableStateFlow(HouseholdState())
    val state: StateFlow<HouseholdState> = _state.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        if (groupId.isBlank()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val groupInfoResult = groupService.getGroupInfo(groupId)
                val groupInfo = groupInfoResult.getOrNull()

                // Load user's default currency
                val user = authService.getCurrentUser()
                val userCurrency = user?.defaultCurrency ?: com.trevio.android.util.AppConstants.BASE_CURRENCY
                val currencySymbol = CurrencyConverter.getCurrencySymbol(userCurrency)

                // Load exchange rates
                val ratesResult = exchangeRateService.getRates()
                val rates = ratesResult.getOrNull()?.rates ?: emptyMap()

                // Load all expenses (large page size to get everything for household)
                val expensesResult = expenseService.getGroupExpenses(groupId, pageSize = AppConstants.HOUSEHOLD_PAGE_SIZE, lastExpenseId = null)
                val rawExpenses = expensesResult.getOrNull()?.items ?: emptyList()

                // Convert each expense's amount to the viewer's currency for display.
                // Preserve the original amount and currency so edits can save back
                // in the original currency instead of overwriting with the display currency.
                val convertedExpenses = rawExpenses.map { expense ->
                    val convertedAmount = CurrencyConverter.convertCurrency(
                        expense.amount, expense.currency, userCurrency, rates
                    )
                    expense.copy(
                        amount = convertedAmount,
                        currency = userCurrency,
                        originalAmount = expense.amount,
                        originalCurrency = expense.currency
                    )
                }

                // Convert budget from INR base to user's currency for display/analytics
                val budgetInUserCurrency = groupInfo?.monthlyBudget?.let { budget ->
                    CurrencyConverter.convertFromBase(budget, userCurrency, rates)
                }

                // Load members from group info
                val members = loadMembers()

                val dailySummary = computeDailySummary(convertedExpenses, _state.value.selectedDate)
                val monthlyReport = computeMonthlyReport(
                    convertedExpenses, members,
                    _state.value.selectedYear,
                    _state.value.selectedMonth,
                    budgetInUserCurrency
                )
                val gamification = computeGamification(
                    convertedExpenses, members,
                    budgetInUserCurrency,
                    monthlyReport.totalSpent,
                    userCurrency
                )
                val categoryUsage = computeCategoryUsageCount(convertedExpenses)

                _state.value = HouseholdState(
                    isLoading = false,
                    groupInfo = groupInfo,
                    expenses = convertedExpenses,
                    members = members,
                    dailySummary = dailySummary,
                    monthlyReport = monthlyReport,
                    gamification = gamification,
                    categoryUsage = categoryUsage,
                    currentUserId = getCurrentUserId(),
                    userCurrency = userCurrency,
                    currencySymbol = currencySymbol,
                    convertedBudget = budgetInUserCurrency,
                    exchangeRates = rates,
                    selectedDate = _state.value.selectedDate,
                    selectedYear = _state.value.selectedYear,
                    selectedMonth = _state.value.selectedMonth
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.toStringResId())
            }
        }
    }

    private suspend fun loadMembers(): List<Member> {
        return try {
            settlementService.getGroupBalances(groupId).getOrDefault(emptyList())
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun getCurrentUserId(): String? {
        return try {
            com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Quick save: the core 2-action flow. Amount + category = saved.
     */
    fun quickSave(
        amount: Double,
        category: String,
        transactionType: TransactionType,
        description: String = "",
        paidBy: String? = null
    ) {
        if (amount <= 0 || groupId.isBlank()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true, saveSuccess = false, error = null)
            try {
                val effectivePaidBy = paidBy ?: _state.value.currentUserId ?: ""
                if (effectivePaidBy.isBlank()) {
                    _state.value = _state.value.copy(isSaving = false, error = R.string.error_authentication_required)
                    return@launch
                }
                val effectiveDescription = description.ifBlank {
                    HouseholdCategories.getCategoryLabel(category)
                }
                val currency = _state.value.userCurrency

                val result = expenseService.addExpense(
                    groupId = groupId,
                    description = effectiveDescription,
                    amount = amount,
                    currency = currency,
                    paidBy = effectivePaidBy,
                    splitType = SplitType.EQUAL,
                    splits = emptyMap(),
                    memberUids = emptyList(),
                    category = category,
                    date = System.currentTimeMillis(),
                    note = "",
                    recurring = null,
                    itemizedData = null,
                    transactionType = transactionType
                )

                if (result.isSuccess) {
                    val labelResId = HouseholdCategories.getCategoryLabelResId(category)
                    val typeResId = if (transactionType == TransactionType.INCOME) R.string.entry_type_received else R.string.entry_type_logged
                    val symbol = _state.value.currencySymbol
                    _state.value = _state.value.copy(
                        isSaving = false,
                        saveSuccess = true,
                        lastSavedMessage = LocalizedString(
                            R.string.entry_logged_msg,
                            listOf(
                                LocalizedString(typeResId),
                                symbol,
                                FormatUtils.formatAmount(amount),
                                LocalizedString(labelResId)
                            )
                        )
                    )
                    // Reload data to reflect the new entry
                    loadData()
                } else {
                    _state.value = _state.value.copy(
                        isSaving = false,
                        error = R.string.error_failed_to_save
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isSaving = false,
                    error = e.toStringResId()
                )
            }
        }
    }

    /**
     * Full save: with description, paidBy, date, note, recurring.
     */
    fun fullSave(
        amount: Double,
        description: String,
        category: String,
        paidBy: String,
        date: Long,
        note: String,
        transactionType: TransactionType,
        recurring: com.trevio.android.domain.model.RecurringConfig? = null
    ) {
        if (amount <= 0 || groupId.isBlank()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true, saveSuccess = false, error = null)
            try {
                if (paidBy.isBlank()) {
                    _state.value = _state.value.copy(isSaving = false, error = R.string.error_authentication_required)
                    return@launch
                }
                val currency = _state.value.userCurrency
                val effectiveDescription = description.ifBlank {
                    HouseholdCategories.getCategoryLabel(category)
                }

                val result = expenseService.addExpense(
                    groupId = groupId,
                    description = effectiveDescription,
                    amount = amount,
                    currency = currency,
                    paidBy = paidBy,
                    splitType = SplitType.EQUAL,
                    splits = emptyMap(),
                    memberUids = emptyList(),
                    category = category,
                    date = date,
                    note = note,
                    recurring = recurring,
                    itemizedData = null,
                    transactionType = transactionType
                )

                if (result.isSuccess) {
                    _state.value = _state.value.copy(
                        isSaving = false,
                        saveSuccess = true,
                        lastSavedMessage = LocalizedString(R.string.entry_saved_msg)
                    )
                    loadData()
                } else {
                    _state.value = _state.value.copy(
                        isSaving = false,
                        error = R.string.error_failed_to_save
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(isSaving = false, error = e.toStringResId())
            }
        }
    }

    /**
     * Update an existing entry.
     */
    fun updateEntry(
        expenseId: String,
        amount: Double,
        description: String,
        category: String,
        paidBy: String,
        date: Long,
        note: String,
        transactionType: TransactionType
    ) {
        if (amount <= 0 || groupId.isBlank() || expenseId.isBlank()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true, error = null)
            try {
                if (paidBy.isBlank()) {
                    _state.value = _state.value.copy(isSaving = false, error = R.string.error_authentication_required)
                    return@launch
                }
                val userCurrency = _state.value.userCurrency
                val effectiveDescription = description.ifBlank {
                    HouseholdCategories.getCategoryLabel(category)
                }

                // Find the original expense to preserve its currency.
                // The displayed amount was converted to the user's currency;
                // convert back to the original currency before saving so
                // we don't overwrite the stored currency or corrupt the
                // base amount.
                val originalExpense = _state.value.expenses.find { it.expenseId == expenseId }
                val originalCurrency = originalExpense?.originalCurrency?.takeIf { it.isNotBlank() }
                    ?: originalExpense?.currency?.takeIf { it.isNotBlank() }
                    ?: userCurrency
                val originalAmount = originalExpense?.originalAmount?.takeIf { it > 0 }
                    ?: originalExpense?.amount
                    ?: amount

                // Convert the edited display amount back to the original currency
                val rates = _state.value.exchangeRates
                val amountToSave = if (userCurrency != originalCurrency && rates.isNotEmpty()) {
                    CurrencyConverter.convertCurrency(amount, userCurrency, originalCurrency, rates)
                } else {
                    amount
                }

                val result = expenseService.updateExpense(
                    groupId = groupId,
                    expenseId = expenseId,
                    description = effectiveDescription,
                    amount = amountToSave,
                    currency = originalCurrency,
                    paidBy = paidBy,
                    splitType = SplitType.EQUAL,
                    splits = emptyMap(),
                    memberUids = emptyList(),
                    category = category,
                    date = date,
                    note = note,
                    itemizedData = null,
                    transactionType = transactionType
                )

                if (result.isSuccess) {
                    _state.value = _state.value.copy(isSaving = false, lastSavedMessage = LocalizedString(R.string.entry_updated_msg))
                    loadData()
                } else {
                    _state.value = _state.value.copy(
                        isSaving = false,
                        error = R.string.error_failed_to_update
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(isSaving = false, error = e.toStringResId())
            }
        }
    }

    /**
     * Delete an entry.
     */
    fun deleteEntry(expenseId: String) {
        if (groupId.isBlank() || expenseId.isBlank()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true, error = null)
            try {
                val result = expenseService.deleteExpense(groupId, expenseId)
                if (result.isSuccess) {
                    _state.value = _state.value.copy(isSaving = false, lastSavedMessage = LocalizedString(R.string.entry_deleted_msg))
                    loadData()
                } else {
                    _state.value = _state.value.copy(
                        isSaving = false,
                        error = R.string.error_failed_to_delete
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(isSaving = false, error = e.toStringResId())
            }
        }
    }

    /**
     * Navigate to a different date for the daily view.
     */
    fun selectDate(date: Long) {
        val summary = computeDailySummary(_state.value.expenses, date)
        _state.value = _state.value.copy(selectedDate = date, dailySummary = summary)
    }

    /**
     * Navigate to a different month for the monthly report.
     */
    fun selectMonth(year: Int, month: Int) {
        val report = computeMonthlyReport(
            _state.value.expenses,
            _state.value.members,
            year, month,
            _state.value.convertedBudget
        )
        _state.value = _state.value.copy(selectedYear = year, selectedMonth = month, monthlyReport = report)
    }

    /**
     * Clear the save success message.
     */
    fun clearSaveSuccess() {
        _state.value = _state.value.copy(saveSuccess = false, lastSavedMessage = null)
    }

    /**
     * Get auto-suggested category for a description.
     */
    fun suggestCategory(description: String): String? {
        return HouseholdCategories.suggestCategory(description)
    }

    /**
     * Get description autocomplete suggestions.
     */
    fun suggestDescriptions(prefix: String): List<String> {
        return suggestDescriptions(_state.value.expenses, prefix)
    }

    /**
     * Update members list (called from GroupDetailScreen which already loads members).
     */
    fun updateMembers(members: List<Member>) {
        _state.value = _state.value.copy(members = members)
        // Recompute analytics with new members
        val dailySummary = computeDailySummary(_state.value.expenses, _state.value.selectedDate)
        val monthlyReport = computeMonthlyReport(
            _state.value.expenses, members,
            _state.value.selectedYear, _state.value.selectedMonth,
            _state.value.convertedBudget
        )
        val gamification = computeGamification(
            _state.value.expenses, members,
            _state.value.convertedBudget,
            monthlyReport.totalSpent,
            _state.value.userCurrency
        )
        _state.value = _state.value.copy(
            dailySummary = dailySummary,
            monthlyReport = monthlyReport,
            gamification = gamification
        )
    }
}
