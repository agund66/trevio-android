package com.trevio.android.ui.expense

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
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
import com.trevio.android.core.navigation.TrevioRoute
import com.trevio.android.domain.model.SplitType
import com.trevio.android.domain.repository.ExpenseService
import com.trevio.android.domain.repository.GroupService
import com.trevio.android.domain.repository.SettlementService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExpenseViewModel @Inject constructor(
    private val expenseService: ExpenseService,
    private val settlementService: SettlementService,
    private val groupService: GroupService,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val groupId: String = savedStateHandle.get<String>("groupId") ?: ""

    data class ExpenseFormState(
        val isLoading: Boolean = false,
        val error: String? = null,
        val saved: Boolean = false,
        val members: List<com.trevio.android.domain.model.Member> = emptyList(),
        val currency: String = "INR"
    )

    private val _state = MutableStateFlow(ExpenseFormState())
    val state: StateFlow<ExpenseFormState> = _state

    init { loadMembers() }

    private fun loadMembers() {
        viewModelScope.launch {
            groupService.getGroupInfo(groupId)
                .onSuccess { info ->
                    _state.value = _state.value.copy(currency = info.currency)
                }
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
                splits = emptyMap(),
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
    val members = state.members
    var paidByUid by remember { mutableStateOf("") }

    LaunchedEffect(state.saved) {
        if (state.saved) { navController.popBackStack() }
    }

    LaunchedEffect(members) {
        if (paidByUid.isEmpty() && members.isNotEmpty()) {
            paidByUid = members.first().uid
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Expense") },
                navigationIcon = {
                    TextButton(onClick = { navController.popBackStack() }) { Text("Cancel") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)
        ) {
            OutlinedTextField(
                value = amountStr,
                onValueChange = { amountStr = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("Amount") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(12.dp))

            Text("Category", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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
            Text("Paid by", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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
            Text("Split method", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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

            Spacer(modifier = Modifier.weight(1f))
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
                            currency = state.currency,
                            paidBy = paidByUid,
                            splitType = splitType,
                            category = category,
                            isRecurring = isRecurring,
                            recurringFrequency = if (isRecurring) recurringFrequency else null
                        )
                    }
                },
                enabled = description.isNotBlank() && amountStr.isNotBlank() && !state.isLoading,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                } else {
                    Text("Save Expense", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
