package com.trevio.android.ui.expense

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trevio.android.core.designsystem.components.TrevioHeader
import com.trevio.android.domain.model.SplitEntry
import com.trevio.android.domain.model.SplitType
import com.trevio.android.domain.repository.ExpenseService
import com.trevio.android.domain.repository.SettlementService
import com.trevio.android.util.rememberCurrencyFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExpenseViewModel @Inject constructor(
    private val expenseService: ExpenseService,
    private val settlementService: SettlementService,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val groupId: String = savedStateHandle.get<String>("groupId") ?: ""

    data class ExpenseFormState(
        val isLoading: Boolean = false,
        val error: String? = null,
        val saved: Boolean = false,
        val members: List<com.trevio.android.domain.model.Member> = emptyList()
    )

    private val _state = MutableStateFlow(ExpenseFormState())
    val state: StateFlow<ExpenseFormState> = _state

    init { loadMembers() }

    private fun loadMembers() {
        viewModelScope.launch {
            settlementService.getGroupBalances(groupId)
                .onSuccess { members -> _state.value = _state.value.copy(members = members) }
        }
    }

    fun addExpense(
        description: String,
        amount: Double,
        currency: String,
        paidBy: String,
        splitType: SplitType,
        splits: Map<String, SplitEntry>,
        category: String,
        isRecurring: Boolean,
        recurringFrequency: String?
    ) {
        _state.value = _state.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            val memberUids = _state.value.members.map { it.uid }
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
                date = System.currentTimeMillis(),
                isRecurring = isRecurring,
                recurringFrequency = recurringFrequency
            ).onSuccess {
                _state.value = _state.value.copy(isLoading = false, saved = true)
            }.onFailure { e ->
                _state.value = _state.value.copy(isLoading = false, error = e.message)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseScreen(
    navController: androidx.navigation.NavHostController,
    viewModel: ExpenseViewModel = hiltViewModel()
) {
    var description by remember { mutableStateOf("") }
    var amountStr by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("other") }
    var splitType by remember { mutableStateOf(SplitType.EQUAL) }
    var isRecurring by remember { mutableStateOf(false) }
    var recurringFrequency by remember { mutableStateOf("daily") }
    val state by viewModel.state.collectAsState()
    val currencyFormatter = rememberCurrencyFormatter()
    var currency by remember { mutableStateOf(currencyFormatter.userCurrency) }
    val members = state.members
    var paidByUid by remember { mutableStateOf("") }
    val splitValues = remember { mutableStateMapOf<String, String>() }

    val currencySymbol = remember(currency) {
        when (currency) {
            "INR" -> "₹"
            "USD" -> "$"
            "EUR" -> "€"
            "GBP" -> "£"
            "JPY" -> "¥"
            "AUD" -> "A$"
            "CAD" -> "C$"
            "SGD" -> "S$"
            "AED" -> "د.إ"
            else -> currency
        }
    }

    LaunchedEffect(state.saved) {
        if (state.saved) { navController.popBackStack() }
    }

    LaunchedEffect(members) {
        if (paidByUid.isEmpty() && members.isNotEmpty()) {
            paidByUid = members.first().uid
        }
    }

    val amount = amountStr.toDoubleOrNull() ?: 0.0
    val activeMembers = members.filter { it.status == "active" }

    val splitSummary = remember(splitType, splitValues.toMap(), amount, activeMembers) {
        if (splitType == SplitType.EQUAL || amount <= 0.0) null
        else {
            var totalEntered = 0.0
            for (m in activeMembers) {
                totalEntered += splitValues[m.uid]?.toDoubleOrNull() ?: 0.0
            }
            when (splitType) {
                SplitType.PERCENT -> Pair(totalEntered, 100.0)
                SplitType.EXACT -> Pair(totalEntered, amount)
                SplitType.SHARES -> Pair(totalEntered, 0.0)
                else -> null
            }
        }
    }

    val isSplitValid = remember(splitType, splitValues.toMap(), amount, activeMembers, splitSummary) {
        if (splitType == SplitType.EQUAL) true
        else if (amount <= 0.0 || activeMembers.isEmpty()) false
        else if (splitType == SplitType.SHARES) {
            splitValues.values.any { (it.toDoubleOrNull() ?: 0.0) > 0.0 }
        }
        else splitSummary != null && kotlin.math.abs(splitSummary.first - splitSummary.second) < 0.01
    }

    val buildSplits: () -> Map<String, SplitEntry> = {
        if (splitType == SplitType.EQUAL) emptyMap()
        else {
            val result = mutableMapOf<String, SplitEntry>()
            for (m in activeMembers) {
                val v = splitValues[m.uid]?.toDoubleOrNull() ?: 0.0
                if (v > 0.0) {
                    when (splitType) {
                        SplitType.SHARES -> result[m.uid] = SplitEntry(amount = 0.0, shareValue = v)
                        SplitType.PERCENT -> result[m.uid] = SplitEntry(amount = 0.0, shareValue = v)
                        SplitType.EXACT -> result[m.uid] = SplitEntry(amount = v)
                        else -> {}
                    }
                }
            }
            result
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TrevioHeader(
            title = "Add Expense",
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
                onValueChange = { amountStr = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("Amount ($currencySymbol)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.headlineMedium,
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))

            SectionLabel("Category")
            Spacer(modifier = Modifier.height(8.dp))
            val categories = listOf("food", "transport", "shopping", "turf", "accommodation", "other")
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.take(3).forEach { cat ->
                    FilterChip(
                        selected = category == cat,
                        onClick = { category = cat },
                        label = { Text(cat.replaceFirstChar { it.uppercase() }) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.drop(3).forEach { cat ->
                    FilterChip(
                        selected = category == cat,
                        onClick = { category = cat },
                        label = { Text(cat.replaceFirstChar { it.uppercase() }) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            SectionLabel("Paid by")
            Spacer(modifier = Modifier.height(8.dp))
            if (members.isNotEmpty()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    members.take(4).forEach { member ->
                        FilterChip(
                            selected = paidByUid == member.uid,
                            onClick = { paidByUid = member.uid },
                            label = { Text(member.displayName.split(" ").firstOrNull() ?: "") }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            SectionLabel("Split method")
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SplitType.values().forEach { st ->
                    FilterChip(
                        selected = splitType == st,
                        onClick = { splitType = st },
                        label = { Text(st.name.lowercase().replaceFirstChar { it.uppercase() }) }
                    )
                }
            }

            if (splitType != SplitType.EQUAL && amount > 0.0 && activeMembers.isNotEmpty()) {
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
                                    SplitType.EXACT -> "Enter exact amount per member"
                                    SplitType.PERCENT -> "Enter percentage per member"
                                    SplitType.SHARES -> "Enter shares per member"
                                    else -> ""
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (splitSummary != null && splitType != SplitType.SHARES) {
                                Text(
                                    "${splitSummary.first}/${splitSummary.second}" + if (splitType == SplitType.PERCENT) "%" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isSplitValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        activeMembers.forEach { member ->
                            val value = splitValues[member.uid] ?: ""
                            val totalShares = splitValues.values.sumOf { it.toDoubleOrNull() ?: 0.0 }
                            val displayAmount = when (splitType) {
                                SplitType.PERCENT -> if (value.isNotEmpty()) " = $currencySymbol${String.format("%,.2f", (value.toDoubleOrNull() ?: 0.0) / 100 * amount)}" else ""
                                SplitType.SHARES -> if (value.isNotEmpty() && totalShares > 0) " = $currencySymbol${String.format("%,.2f", (value.toDoubleOrNull() ?: 0.0) / totalShares * amount)}" else ""
                                else -> ""
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(member.displayName.split(" ").firstOrNull() ?: "", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
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
                        if (splitType == SplitType.SHARES) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Amounts are split proportionally based on share values.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Repeat, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(12.dp))
                Text("Recurring expense", modifier = Modifier.weight(1f))
                Switch(checked = isRecurring, onCheckedChange = { isRecurring = it })
            }

            if (isRecurring) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("daily", "weekly", "monthly").forEach { freq ->
                        FilterChip(
                            selected = recurringFrequency == freq,
                            onClick = { recurringFrequency = freq },
                            label = { Text(freq.replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            if (state.error != null) {
                Text(state.error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))
            }

            Button(
                onClick = {
                    val amount = amountStr.toDoubleOrNull() ?: 0.0
                    if (description.isNotBlank() && amount > 0 && paidByUid.isNotEmpty()) {
                        viewModel.addExpense(
                            description = description,
                            amount = amount,
                            currency = currency,
                            paidBy = paidByUid,
                            splitType = splitType,
                            splits = buildSplits(),
                            category = category,
                            isRecurring = isRecurring,
                            recurringFrequency = if (isRecurring) recurringFrequency else null
                        )
                    }
                },
                enabled = description.isNotBlank() && amountStr.isNotBlank() && isSplitValid && !state.isLoading,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                } else {
                    Text("Save Expense", style = MaterialTheme.typography.titleMedium)
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
