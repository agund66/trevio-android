package com.trevio.android.ui.expense

import androidx.compose.foundation.background
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
import com.trevio.android.domain.repository.AuthService
import com.trevio.android.domain.repository.ExpenseService
import com.trevio.android.domain.repository.SettlementService
import com.trevio.android.util.rememberCurrencyFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class ExpenseViewModel @Inject constructor(
    private val expenseService: ExpenseService,
    private val settlementService: SettlementService,
    private val authService: AuthService,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val groupId: String = savedStateHandle.get<String>("groupId") ?: ""
    val currentUserId: String? get() = runBlocking { authService.getCurrentUserId() }

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
                .onFailure { e -> _state.value = _state.value.copy(error = e.message) }
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
        date: Long = System.currentTimeMillis()
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
                date = date
            ).onSuccess {
                _state.value = _state.value.copy(isLoading = false, saved = true)
            }.onFailure { e ->
                _state.value = _state.value.copy(isLoading = false, error = e.message)
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
    val members = state.members
    var paidByUid by remember { mutableStateOf("") }
    val splitValues = remember { mutableStateMapOf<String, String>() }
    val currentUserId = remember { viewModel.currentUserId }
    var saveAndAddAnother by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    var isRecurring by remember { mutableStateOf(false) }

    val dateFormatter = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val todayStr = remember { dateFormatter.format(Date()) }
    var expenseDateStr by remember { mutableStateOf(todayStr) }
    var showDatePicker by remember { mutableStateOf(false) }
    val excludedMembers = remember { mutableStateMapOf<String, Boolean>() }

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
        if (state.saved) {
            navController.previousBackStackEntry?.savedStateHandle?.set("needsRefresh", true)
            if (saveAndAddAnother) {
                viewModel.resetSaved()
                description = ""
                amountStr = ""
                category = "other"
                splitType = SplitType.EQUAL
                splitValues.clear()
                excludedMembers.clear()
                expenseDateStr = todayStr
                note = ""
                isRecurring = false
                saveAndAddAnother = false
            } else {
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
        val cleaned = amountStr.replace(Regex("[^0-9.+\\-*/]"), "")
        if (cleaned.isEmpty()) 0.0
        else if (!cleaned.any { it == '+' || it == '-' || it == '*' || it == '/' }) {
            cleaned.toDoubleOrNull() ?: 0.0
        } else {
            try {
                evaluateExpression(cleaned)
            } catch (e: Exception) {
                cleaned.toDoubleOrNull() ?: 0.0
            }
        }
    }
    val activeMembers = members.filter { it.status == "active" }
    val includedMembers = activeMembers.filter { excludedMembers[it.uid] != true }
    val equalPerPerson = if (splitType == SplitType.EQUAL && amount > 0.0 && includedMembers.isNotEmpty()) amount / includedMembers.size else 0.0

    val splitSummary = remember(splitType, splitValues.toMap(), amount, includedMembers) {
        if (splitType == SplitType.EQUAL || amount <= 0.0) null
        else {
            var totalEntered = 0.0
            for (m in includedMembers) {
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

    val isSplitValid = remember(splitType, splitValues.toMap(), amount, includedMembers, splitSummary) {
        if (splitType == SplitType.EQUAL) includedMembers.isNotEmpty()
        else if (amount <= 0.0 || includedMembers.isEmpty()) false
        else if (splitType == SplitType.SHARES) {
            splitValues.values.any { (it.toDoubleOrNull() ?: 0.0) > 0.0 }
        }
        else splitSummary != null && kotlin.math.abs(splitSummary.first - splitSummary.second) < 0.01
    }

    val buildSplits: () -> Map<String, SplitEntry> = {
        if (splitType == SplitType.EQUAL) emptyMap()
        else {
            val result = mutableMapOf<String, SplitEntry>()
            for (m in includedMembers) {
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
                onValueChange = { amountStr = it.filter { c -> c.isDigit() || c == '.' || c == '+' || c == '-' || c == '*' || c == '/' } },
                label = { Text("Amount ($currencySymbol)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.headlineMedium,
                shape = RoundedCornerShape(12.dp)
            )
            if (amount > 0.0 && amountStr.any { it == '+' || it == '-' || it == '*' || it == '/' }) {
                Spacer(modifier = Modifier.height(4.dp))
                Text("= $currencySymbol${String.format("%,.2f", amount)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Quick calc:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                listOf("+" to "+", "−" to "-", "×" to "*", "÷" to "/").forEach { (label, op) ->
                    OutlinedButton(
                        onClick = { amountStr = amountStr + op },
                        modifier = Modifier.size(36.dp),
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
                        Text("Clear", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = expenseDateStr,
                onValueChange = { },
                label = { Text("Date") },
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                trailingIcon = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = "Pick date")
                    }
                }
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
            if (members.isEmpty()) {
                Text("Loading members...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    activeMembers.forEach { member ->
                        FilterChip(
                            selected = effectivePaidBy == member.uid,
                            onClick = { paidByUid = member.uid },
                            label = {
                                val name = member.displayName.split(" ").firstOrNull() ?: ""
                                Text(if (member.uid == currentUserId) "$name (You)" else name)
                            }
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
                                "$currencySymbol${String.format("%,.2f", equalPerPerson)} per person",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "${includedMembers.size} of ${activeMembers.size} members",
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
                                        Text(if (member.uid == currentUserId) "$name (You)" else name)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (splitType != SplitType.EQUAL && amount > 0.0 && includedMembers.isNotEmpty()) {
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
                        includedMembers.forEach { member ->
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
                                val name = member.displayName.split(" ").firstOrNull() ?: ""
                                Text(if (member.uid == currentUserId) "$name (You)" else name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
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
                            Text("Excluded members (tap to include):", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                activeMembers.filter { excludedMembers[it.uid] == true }.forEach { member ->
                                    FilterChip(
                                        selected = false,
                                        onClick = { excludedMembers.remove(member.uid) },
                                        label = { 
                                            val name = member.displayName.split(" ").firstOrNull() ?: ""
                                            Text(if (member.uid == currentUserId) "$name (You)" else name)
                                        }
                                    )
                                }
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

            // Note field
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note (optional)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                shape = RoundedCornerShape(12.dp)
            )

            // Recurring expense toggle
            Surface(
                shape = RoundedCornerShape(12.dp),
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = isRecurring, onCheckedChange = { isRecurring = it })
                    Icon(Icons.Default.Repeat, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Make this a recurring expense", style = MaterialTheme.typography.bodyMedium)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            if (state.error != null) {
                Text(state.error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        if (description.isNotBlank() && amount > 0 && effectivePaidBy.isNotEmpty()) {
                            viewModel.addExpense(
                                description = description,
                                amount = amount,
                                currency = currency,
                                paidBy = effectivePaidBy,
                                splitType = splitType,
                                splits = buildSplits(),
                                category = category,
                                date = expenseDateMillis
                            )
                        }
                    },
                    enabled = description.isNotBlank() && amountStr.isNotBlank() && isSplitValid && !state.isLoading,
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                    } else {
                        Text("Save", style = MaterialTheme.typography.titleMedium)
                    }
                }
                OutlinedButton(
                    onClick = {
                        saveAndAddAnother = true
                        if (description.isNotBlank() && amount > 0 && effectivePaidBy.isNotEmpty()) {
                            viewModel.addExpense(
                                description = description,
                                amount = amount,
                                currency = currency,
                                paidBy = effectivePaidBy,
                                splitType = splitType,
                                splits = buildSplits(),
                                category = category,
                                date = expenseDateMillis
                            )
                        }
                    },
                    enabled = description.isNotBlank() && amountStr.isNotBlank() && isSplitValid && !state.isLoading,
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Save & Add", style = MaterialTheme.typography.titleMedium)
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
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
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

private fun evaluateExpression(expr: String): Double {
    val tokens = mutableListOf<String>()
    var currentNum = StringBuilder()
    for (c in expr) {
        if (c == '+' || c == '-' || c == '*' || c == '/') {
            if (currentNum.isNotEmpty()) {
                tokens.add(currentNum.toString())
                currentNum = StringBuilder()
            }
            tokens.add(c.toString())
        } else {
            currentNum.append(c)
        }
    }
    if (currentNum.isNotEmpty()) tokens.add(currentNum.toString())

    if (tokens.isEmpty()) return 0.0

    val parsed = mutableListOf<Any>()
    var i = 0
    while (i < tokens.size) {
        val t = tokens[i]
        if (t == "*" || t == "/") {
            val prev = parsed.removeLast() as Double
            val next = tokens[++i].toDoubleOrNull() ?: 0.0
            parsed.add(if (t == "*") prev * next else if (next != 0.0) prev / next else 0.0)
        } else if (t == "+" || t == "-") {
            parsed.add(t)
        } else {
            parsed.add(t.toDoubleOrNull() ?: 0.0)
        }
        i++
    }

    var result = parsed[0] as Double
    var j = 1
    while (j < parsed.size) {
        val op = parsed[j] as String
        val next = parsed[++j] as Double
        result = if (op == "+") result + next else result - next
        j++
    }
    return result
}
