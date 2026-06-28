package com.trevio.android.ui.settlement

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
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
import com.trevio.android.core.designsystem.components.MemberAvatar
import com.trevio.android.core.designsystem.components.formatCurrency
import com.trevio.android.domain.model.SimplifiedDebt
import com.trevio.android.domain.repository.SettlementService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettlementViewModel @Inject constructor(
    private val settlementService: SettlementService,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val groupId: String = savedStateHandle.get<String>("groupId") ?: ""

    data class SettlementState(
        val isLoading: Boolean = true,
        val debts: List<SimplifiedDebt> = emptyList(),
        val error: String? = null
    )

    private val _state = MutableStateFlow(SettlementState())
    val state: StateFlow<SettlementState> = _state

    init { loadDebts() }

    fun loadDebts() {
        _state.value = _state.value.copy(isLoading = true)
        viewModelScope.launch {
            settlementService.getSimplifiedDebts(groupId)
                .onSuccess { debts -> _state.value = SettlementState(isLoading = false, debts = debts) }
                .onFailure { e -> _state.value = SettlementState(isLoading = false, error = e.message) }
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
            ).onSuccess { loadDebts() }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettleUpScreen(
    navController: androidx.navigation.NavHostController,
    viewModel: SettlementViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settle Up") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (state.isLoading) {
            com.trevio.android.core.designsystem.components.LoadingIndicator(modifier = Modifier.padding(padding))
            return@Scaffold
        }

        if (state.debts.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("All settled up!", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("No pending settlements", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(state.debts) { debt ->
                DebtCard(
                    debt = debt,
                    onSettle = { viewModel.settleDebt(debt) },
                    onPayViaUpi = {
                        val upiUri = "upi://pay?pa=&pn=${Uri.encode(debt.toName)}&am=${debt.amount}&cu=INR&tn=Trevio"
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(upiUri))
                        context.startActivity(Intent.createChooser(intent, "Pay with..."))
                    }
                )
            }
        }
    }
}

@Composable
private fun DebtCard(
    debt: SimplifiedDebt,
    onSettle: () -> Unit,
    onPayViaUpi: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MemberAvatar(name = debt.fromName, photoURL = debt.fromPhotoURL, size = 36)
                Text(" → ", style = MaterialTheme.typography.titleMedium)
                MemberAvatar(name = debt.toName, photoURL = debt.toPhotoURL, size = 36)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("${debt.fromName.split(" ").firstOrNull()} owes ${debt.toName.split(" ").firstOrNull()}", style = MaterialTheme.typography.bodyMedium)
                    Text(formatCurrency(debt.amount), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onPayViaUpi, modifier = Modifier.weight(1f)) {
                    Text("Pay via UPI")
                }
                Button(onClick = onSettle, modifier = Modifier.weight(1f)) {
                    Text("Mark Settled")
                }
            }
        }
    }
}
