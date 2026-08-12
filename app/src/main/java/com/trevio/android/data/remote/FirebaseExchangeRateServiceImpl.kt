package com.trevio.android.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import com.trevio.android.domain.model.ExchangeRates
import com.trevio.android.domain.repository.ExchangeRateService
import com.trevio.android.util.AppConstants
import com.trevio.android.util.Logger
import com.trevio.android.util.friendlyNetworkMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseExchangeRateServiceImpl @Inject constructor(
    private val firestore: FirebaseFirestore
) : ExchangeRateService {

    companion object {
        private const val CACHE_DOC_PATH = "config/exchangeRates"
        private const val API_URL = "https://open.er-api.com/v6/latest/"
        private const val TAG = "ExchangeRateService"
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
                            base = data["base"] as? String ?: AppConstants.BASE_CURRENCY,
                            date = cachedDate,
                            rates = cachedRates,
                            updatedAt = data["updatedAt"] as? Long ?: 0
                        ))
                    }
                }
            }

            fetchAndCacheRates(todayStr)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    override suspend fun getRateToBase(currency: String): Result<Double> {
        return try {
            if (currency == AppConstants.BASE_CURRENCY) return Result.success(1.0)
            val rates = getRates().getOrNull()
                ?: return Result.failure(Exception("Failed to get exchange rates"))
            val rate = rates.rates[currency]
                ?: return Result.failure(Exception("Exchange rate not available for currency: $currency"))
            Result.success(1.0 / rate)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    private suspend fun fetchAndCacheRates(dateStr: String): Result<ExchangeRates> {
        return try {
            // Perform network I/O on the IO dispatcher to avoid blocking coroutines
            val rates = withContext(Dispatchers.IO) {
                val url = URL("$API_URL${AppConstants.BASE_CURRENCY}")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                conn.requestMethod = "GET"
                try {
                    val response = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(response)
                    val ratesJson = json.getJSONObject("rates")
                    val ratesMap = mutableMapOf<String, Double>()
                    val keys = ratesJson.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        ratesMap[key] = ratesJson.getDouble(key)
                    }
                    ratesMap
                } finally {
                    conn.disconnect()
                }
            }

            val now = System.currentTimeMillis()

            // Best-effort cache write: the Firestore rules restrict writes to
            // superadmins, so normal users will get a permission-denied error.
            // The rates are still valid in-memory for this session.
            try {
                firestore.document(CACHE_DOC_PATH).set(mapOf(
                    "base" to AppConstants.BASE_CURRENCY,
                    "date" to dateStr,
                    "rates" to rates,
                    "updatedAt" to now
                )).await()
            } catch (e: Exception) {
                Logger.w(tag = TAG, message = "Could not cache exchange rates: ${e.message}")
            }

            Result.success(ExchangeRates(
                base = AppConstants.BASE_CURRENCY,
                date = dateStr,
                rates = rates,
                updatedAt = now
            ))
        } catch (e: Exception) {
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }
}
