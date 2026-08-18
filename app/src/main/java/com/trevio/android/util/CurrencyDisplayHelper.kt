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
        val userCurrency: String = AppConstants.BASE_CURRENCY,
        val userTimezone: String = AppConstants.DEFAULT_TIMEZONE,
        val rates: Map<String, Double> = emptyMap(),
        val isLoading: Boolean = true
    )

    private val _state = MutableStateFlow(CurrencyState())
    val state: StateFlow<CurrencyState> = _state

    init { loadRates() }

    fun loadRates() {
        viewModelScope.launch {
            val user = authService.getCurrentUser()
            val userCurrency = user?.defaultCurrency ?: AppConstants.BASE_CURRENCY
            val userTimezone = user?.timezone ?: AppConstants.DEFAULT_TIMEZONE
            exchangeRateService.getRates()
                .onSuccess { exchangeRates ->
                    _state.value = CurrencyState(
                        userCurrency = userCurrency,
                        userTimezone = userTimezone,
                        rates = exchangeRates.rates,
                        isLoading = false
                    )
                }
                .onFailure {
                    _state.value = CurrencyState(
                        userCurrency = userCurrency,
                        userTimezone = userTimezone,
                        isLoading = false
                    )
                }
        }
    }

    fun formatAmount(amount: Double, sourceCurrency: String): String {
        val currentState = _state.value
        if (sourceCurrency == currentState.userCurrency) {
            return CurrencyConverter.formatCurrency(amount, sourceCurrency)
        }
        if (currentState.rates.isEmpty()) {
            return CurrencyConverter.formatCurrency(amount, sourceCurrency)
        }
        val converted = CurrencyConverter.convertCurrency(amount, sourceCurrency, currentState.userCurrency, currentState.rates)
        return CurrencyConverter.formatCurrency(converted, currentState.userCurrency)
    }

    fun formatOriginal(amount: Double, currency: String): String {
        return CurrencyConverter.formatCurrency(amount, currency)
    }

    fun formatDate(timestamp: Long, includeTime: Boolean = false): String {
        return CurrencyConverter.formatDate(timestamp, _state.value.userCurrency, includeTime, _state.value.userTimezone)
    }
}

@Composable
fun rememberCurrencyFormatter(): CurrencyFormatter {
    val viewModel: CurrencyViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()
    return remember(state) {
        CurrencyFormatter(
            userCurrency = state.userCurrency,
            userTimezone = state.userTimezone,
            rates = state.rates,
            isLoading = state.isLoading,
            formatAmount = { amount, currency -> viewModel.formatAmount(amount, currency) },
            formatOriginal = { amount, currency -> viewModel.formatOriginal(amount, currency) },
            formatDate = { timestamp, includeTime -> viewModel.formatDate(timestamp, includeTime) }
        )
    }
}

data class CurrencyFormatter(
    val userCurrency: String,
    val userTimezone: String,
    val rates: Map<String, Double>,
    val isLoading: Boolean,
    val formatAmount: (Double, String) -> String,
    val formatOriginal: (Double, String) -> String,
    val formatDate: (Long, Boolean) -> String
)
