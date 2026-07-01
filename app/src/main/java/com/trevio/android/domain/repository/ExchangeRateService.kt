package com.trevio.android.domain.repository

import com.trevio.android.domain.model.ExchangeRates

interface ExchangeRateService {
    suspend fun getRates(): Result<ExchangeRates>
    suspend fun getRateToBase(currency: String): Result<Double>
}
