package com.trevio.android.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import com.trevio.android.domain.model.ExchangeRates
import com.trevio.android.domain.repository.ExchangeRateService
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseExchangeRateServiceImpl @Inject constructor(
    private val firestore: FirebaseFirestore
) : ExchangeRateService {

    companion object {
        private const val BASE_CURRENCY = "INR"
        private const val CACHE_DOC_PATH = "config/exchangeRates"
    }

    override suspend fun getRates(): Result<ExchangeRates> {
        return try {
            val todayStr = java.time.LocalDate.now().toString()

            val cachedDoc = firestore.document(CACHE_DOC_PATH).get().await()
            if (cachedDoc.exists()) {
                val data = cachedDoc.data
                if (data != null) {
                    val cachedDate = data["date"] as? String
                    @Suppress("UNCHECKED_CAST")
                    val cachedRates = data["rates"] as? Map<String, Double>
                    if (cachedDate == todayStr && cachedRates != null) {
                        return Result.success(ExchangeRates(
                            base = data["base"] as? String ?: BASE_CURRENCY,
                            date = cachedDate,
                            rates = cachedRates,
                            updatedAt = data["updatedAt"] as? Long ?: 0
                        ))
                    }
                }
            }

            fetchAndCacheRates(todayStr)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getRateToBase(currency: String): Result<Double> {
        return try {
            if (currency == BASE_CURRENCY) return Result.success(1.0)
            val rates = getRates().getOrNull()
                ?: return Result.failure(Exception("Failed to get exchange rates"))
            val rate = rates.rates[currency]
                ?: return Result.success(1.0)
            Result.success(1.0 / rate)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun fetchAndCacheRates(dateStr: String): Result<ExchangeRates> {
        return try {
            val response = URL("https://open.er-api.com/v6/latest/$BASE_CURRENCY").readText()
            val json = JSONObject(response)
            val ratesJson = json.getJSONObject("rates")
            val rates = mutableMapOf<String, Double>()
            val keys = ratesJson.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                rates[key] = ratesJson.getDouble(key)
            }

            val now = System.currentTimeMillis()
            firestore.document(CACHE_DOC_PATH).set(mapOf(
                "base" to BASE_CURRENCY,
                "date" to dateStr,
                "rates" to rates,
                "updatedAt" to now
            )).await()

            Result.success(ExchangeRates(
                base = BASE_CURRENCY,
                date = dateStr,
                rates = rates,
                updatedAt = now
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
