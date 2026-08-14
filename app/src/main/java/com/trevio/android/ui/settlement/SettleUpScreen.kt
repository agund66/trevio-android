package com.trevio.android.ui.settlement

import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trevio.android.R
import com.trevio.android.core.designsystem.components.TrevioCard
import com.trevio.android.core.designsystem.components.TrevioHeader
import com.trevio.android.core.designsystem.theme.SettlementBgStart
import com.trevio.android.core.designsystem.theme.SettlementGradientEnd
import com.trevio.android.core.designsystem.theme.SettlementGradientStart
import com.trevio.android.core.designsystem.theme.TemplateTrip
import com.trevio.android.core.designsystem.theme.TemplateTripDark
import com.trevio.android.core.designsystem.theme.TrevioBorder
import com.trevio.android.domain.model.SimplifiedDebt
import com.trevio.android.domain.repository.AuthService
import com.trevio.android.domain.repository.SettlementService
import com.trevio.android.util.AppConstants
import com.trevio.android.util.rememberCurrencyFormatter
import com.trevio.android.util.toStringResId
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private fun getUpiVpa(debt: SimplifiedDebt): String = com.trevio.android.util.PaymentUtils.getUpiVpa(debt)

@HiltViewModel
class SettlementViewModel @Inject constructor(
    private val settlementService: SettlementService,
    private val authService: AuthService,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val groupId: String = savedStateHandle.get<String>("groupId") ?: ""

    data class SettlementState(
        val isLoading: Boolean = true,
        val debts: List<SimplifiedDebt> = emptyList(),
        @StringRes val error: Int? = null,
        val currentUserId: String? = null
    )

    private val _state = MutableStateFlow(SettlementState())
    val state: StateFlow<SettlementState> = _state

    init {
        loadCurrentUserId()
        loadDebts()
    }

    private fun loadCurrentUserId() {
        viewModelScope.launch {
            _state.value = _state.value.copy(currentUserId = authService.getCurrentUserId())
        }
    }

    fun loadDebts() {
        _state.value = _state.value.copy(isLoading = true)
        viewModelScope.launch {
            settlementService.getSimplifiedDebts(groupId)
                .onSuccess { debts -> _state.value = _state.value.copy(isLoading = false, debts = debts) }
                .onFailure { e -> _state.value = _state.value.copy(isLoading = false, error = e.toStringResId()) }
        }
    }

    fun settleDebt(debt: SimplifiedDebt, method: com.trevio.android.domain.model.SettlementMethod = com.trevio.android.domain.model.SettlementMethod.CASH) {
        viewModelScope.launch {
            settlementService.addSettlement(
                groupId = groupId,
                fromUid = debt.fromUid,
                toUid = debt.toUid,
                amount = debt.amount,
                currency = AppConstants.BASE_CURRENCY,
                method = method,
                upiRefId = null
            ).onSuccess {
                _state.value = _state.value.copy(error = null)
                loadDebts()
            }.onFailure { e ->
                _state.value = _state.value.copy(error = e.toStringResId())
            }
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
    val currentUserId = state.currentUserId
    val currencyFormatter = rememberCurrencyFormatter()
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TrevioHeader(
            title = stringResource(R.string.settle_up_title),
            onBack = { navController.popBackStack() }
        )
        if (state.isLoading) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return
        }

        if (state.error != null && state.debts.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(stringResource(R.string.settle_up_failed), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(stringResource(state.error!!), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(onClick = { viewModel.loadDebts() }) {
                        Text(stringResource(R.string.settle_up_retry))
                    }
                }
            }
            return
        }

        if (state.error != null) {
            Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(state.error!!), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }

        if (state.debts.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                TrevioCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        SettlementBgStart.copy(alpha = 0.18f),
                                        SettlementGradientEnd.copy(alpha = 0.06f)
                                    )
                                )
                            )
                            .padding(vertical = 40.dp, horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(SettlementGradientStart, SettlementGradientEnd)
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = stringResource(R.string.settle_up_all_settled_desc),
                                modifier = Modifier.size(44.dp),
                                tint = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            stringResource(R.string.settle_up_all_settled),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.settle_up_no_debts),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            return
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(state.debts, key = { "${it.fromUid}_${it.toUid}" }) { debt ->
                DebtCard(
                    debt = debt,
                    currentUserId = currentUserId,
                    formatBase = currencyFormatter.formatBase,
                    onSettle = {
                        viewModel.settleDebt(debt)
                        navController.previousBackStackEntry?.savedStateHandle?.set("needsRefresh", true)
                    },
                    onPayViaUpi = {
                        val vpa = getUpiVpa(debt)
                        if (vpa.isNotEmpty()) {
                            val upiUri = "upi://pay?pa=${Uri.encode(vpa)}&pn=${Uri.encode(debt.toName)}&am=${debt.amount}&cu=${AppConstants.BASE_CURRENCY}&tn=${Uri.encode("Trevio")}"
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(upiUri))
                            context.startActivity(Intent.createChooser(intent, context.getString(R.string.pay_with)))
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
    currentUserId: String?,
    formatBase: (Double) -> String,
    onSettle: () -> Unit,
    onPayViaUpi: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val iconColor = if (isDark) TemplateTripDark else TemplateTrip
    TrevioCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(iconColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Payments, contentDescription = stringResource(R.string.settle_up_payment), tint = iconColor, modifier = Modifier.size(22.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    val fromName = if (debt.fromUid == currentUserId) stringResource(R.string.add_expense_you) else debt.fromName.split(" ").firstOrNull() ?: ""
                    val toName = if (debt.toUid == currentUserId) stringResource(R.string.add_expense_you).lowercase() else debt.toName.split(" ").firstOrNull() ?: ""
                    val debtText = if (debt.fromUid == currentUserId) {
                        stringResource(R.string.debt_you_owe_name, toName)
                    } else if (debt.toUid == currentUserId) {
                        stringResource(R.string.debt_name_owes_you, fromName)
                    } else {
                        stringResource(R.string.debt_name_owes_name, fromName, toName)
                    }
                    Text(debtText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatBase(debt.amount), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
            if (debt.toUpiId.isNotEmpty() && (debt.toCountryCode.isEmpty() || debt.toCountryCode == "IN")) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(stringResource(R.string.settle_up_pay_to, debt.toUpiId), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (debt.toPhoneNumber.isNotEmpty() && (debt.toCountryCode.isEmpty() || debt.toCountryCode == "IN")) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(stringResource(R.string.settle_up_pay_to_vpa, "${debt.toPhoneNumber}@paytm"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        Text(stringResource(R.string.settle_up_pay_upi))
                    }
                    Button(
                        onClick = onSettle,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.settle_up_settle))
                    }
                } else {
                    Button(
                        onClick = onSettle,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.settle_up_settle))
                    }
                }
            }
        }
    }
}
