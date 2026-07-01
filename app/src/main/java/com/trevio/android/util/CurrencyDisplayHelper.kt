package com.trevio.android.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trevio.android.domain.repository.AuthService
import com.trevio.android.domain.repository.ExchangeRateService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CurrencyViewModel @Inject constructor(
    private val exchangeRateService: ExchangeRateService,
    private val authService: AuthService
) : ViewModel() {

    data class CurrencyState(
        val userCurrency: String = "INR",
        val rates: Map<String, Double> = emptyMap(),
        val isLoading: Boolean = true
    )

    private val _state = MutableStateFlow(CurrencyState())
    val state: StateFlow<CurrencyState> = _state

    init { loadRates() }

    fun loadRates() {
        viewModelScope.launch {
            val user = authService.getCurrentUser()
            val userCurrency = user?.defaultCurrency ?: "INR"
            exchangeRateService.getRates()
                .onSuccess { exchangeRates ->
                    _state.value = CurrencyState(
                        userCurrency = userCurrency,
                        rates = exchangeRates.rates,
                        isLoading = false
                    )
                }
                .onFailure {
                    _state.value = CurrencyState(
                        userCurrency = userCurrency,
                        isLoading = false
                    )
                }
        }
    }

    fun formatBase(amountInBase: Double): String {
        val currentState = _state.value
        if (currentState.rates.isEmpty()) return CurrencyConverter.formatCurrency(amountInBase, "INR")
        val converted = CurrencyConverter.convertFromBase(amountInBase, currentState.userCurrency, currentState.rates)
        return CurrencyConverter.formatCurrency(converted, currentState.userCurrency)
    }

    fun formatOriginal(amount: Double, currency: String): String {
        return CurrencyConverter.formatCurrency(amount, currency)
    }
}

@Composable
fun rememberCurrencyFormatter(): CurrencyFormatter {
    val viewModel: CurrencyViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()
    return remember(state) {
        CurrencyFormatter(
            userCurrency = state.userCurrency,
            rates = state.rates,
            isLoading = state.isLoading,
            formatBase = { amountInBase -> viewModel.formatBase(amountInBase) },
            formatOriginal = { amount, currency -> viewModel.formatOriginal(amount, currency) }
        )
    }
}

data class CurrencyFormatter(
    val userCurrency: String,
    val rates: Map<String, Double>,
    val isLoading: Boolean,
    val formatBase: (Double) -> String,
    val formatOriginal: (Double, String) -> String
)
