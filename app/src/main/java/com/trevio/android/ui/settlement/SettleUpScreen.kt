package com.trevio.android.ui.settlement

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.*
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
import com.trevio.android.core.designsystem.components.TrevioCard
import com.trevio.android.core.designsystem.components.TrevioHeader
import com.trevio.android.core.designsystem.theme.TrevioBorder
import com.trevio.android.domain.model.SimplifiedDebt
import com.trevio.android.domain.repository.SettlementService
import com.trevio.android.util.rememberCurrencyFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private fun getUpiVpa(debt: SimplifiedDebt): String {
    if (debt.toUpiId.isNotEmpty()) return debt.toUpiId
    if (debt.toPhoneNumber.isNotEmpty() && (debt.toCountryCode.isEmpty() || debt.toCountryCode == "IN")) {
        return "${debt.toPhoneNumber}@paytm"
    }
    return ""
}

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

    fun settleDebt(debt: SimplifiedDebt, method: com.trevio.android.domain.model.SettlementMethod = com.trevio.android.domain.model.SettlementMethod.CASH) {
        viewModelScope.launch {
            settlementService.addSettlement(
                groupId = groupId,
                fromUid = debt.fromUid,
                toUid = debt.toUid,
                amount = debt.amount,
                currency = "INR",
                method = method,
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
    val currencyFormatter = rememberCurrencyFormatter()
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TrevioHeader(
            title = "Settle Up",
            onBack = { navController.popBackStack() }
        )
        if (state.isLoading) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return
        }

        if (state.debts.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Text("All settled up!", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("No pending settlements", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            return
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(state.debts) { debt ->
                DebtCard(
                    debt = debt,
                    formatBase = currencyFormatter.formatBase,
                    onSettle = { viewModel.settleDebt(debt) },
                    onPayViaUpi = {
                        val vpa = getUpiVpa(debt)
                        if (vpa.isNotEmpty()) {
                            val upiUri = "upi://pay?pa=${Uri.encode(vpa)}&pn=${Uri.encode(debt.toName)}&am=${debt.amount}&cu=INR&tn=${Uri.encode("Trevio")}"
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(upiUri))
                            context.startActivity(Intent.createChooser(intent, "Pay with..."))
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun DebtCard(
    debt: SimplifiedDebt,
    formatBase: (Double) -> String,
    onSettle: () -> Unit,
    onPayViaUpi: () -> Unit
) {
    TrevioCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF6366F1).copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Payments, contentDescription = null, tint = Color(0xFF6366F1), modifier = Modifier.size(22.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("${debt.fromName.split(" ").firstOrNull()} owes ${debt.toName.split(" ").firstOrNull()}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatBase(debt.amount), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
            if (debt.toUpiId.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text("Pay to: ${debt.toUpiId}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (debt.toPhoneNumber.isNotEmpty() && (debt.toCountryCode.isEmpty() || debt.toCountryCode == "IN")) {
                Spacer(modifier = Modifier.height(4.dp))
                Text("Pay to: ${debt.toPhoneNumber}@paytm", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val vpa = getUpiVpa(debt)
                if (vpa.isNotEmpty()) {
                    OutlinedButton(
                        onClick = onPayViaUpi,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Pay via UPI")
                    }
                    Button(
                        onClick = onSettle,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Mark Settled")
                    }
                } else {
                    Button(
                        onClick = onSettle,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Mark Settled")
                    }
                }
            }
        }
    }
}
