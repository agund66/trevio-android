package com.trevio.android.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.round

object CurrencyConverter {

    private val currencyLocales = mapOf(
        "INR" to Locale("en", "IN"),
        "USD" to Locale("en", "US"),
        "EUR" to Locale("en", "IE"),
        "GBP" to Locale("en", "GB"),
        "JPY" to Locale("ja", "JP"),
        "AUD" to Locale("en", "AU"),
        "CAD" to Locale("en", "CA"),
        "SGD" to Locale("en", "SG"),
        "AED" to Locale("ar", "AE"),
        "SAR" to Locale("ar", "SA"),
        "PKR" to Locale("ur", "PK"),
        "BDT" to Locale("bn", "BD"),
        "LKR" to Locale("en", "LK"),
        "NPR" to Locale("ne", "NP"),
        "ZAR" to Locale("en", "ZA"),
        "NGN" to Locale("en", "NG"),
        "KES" to Locale("sw", "KE")
    )

    fun getLocaleForCurrency(currency: String): Locale {
        return currencyLocales[currency] ?: Locale("en", "US")
    }

    fun formatDate(timestamp: Long, currency: String, includeTime: Boolean = false): String {
        if (timestamp <= 0L) return ""
        val locale = getLocaleForCurrency(currency)
        val pattern = if (includeTime) "dd MMM yyyy, HH:mm" else "dd MMM yyyy"
        return SimpleDateFormat(pattern, locale).format(Date(timestamp))
    }

    fun convertFromBase(
        amountInBase: Double,
        toCurrency: String,
        rates: Map<String, Double>
    ): Double {
        if (toCurrency == "INR") return amountInBase
        val rate = rates[toCurrency] ?: return amountInBase
        return round(amountInBase * rate * 100) / 100
    }

    fun convertToBase(
        amount: Double,
        fromCurrency: String,
        rates: Map<String, Double>
    ): Double {
        if (fromCurrency == "INR") return amount
        val rate = rates[fromCurrency] ?: return amount
        return round(amount / rate * 100) / 100
    }

    fun getRateToBase(
        currency: String,
        rates: Map<String, Double>
    ): Double {
        if (currency == "INR") return 1.0
        val rate = rates[currency] ?: return 1.0
        return 1.0 / rate
    }

    private val currencySymbols = mapOf(
        "INR" to "₹",
        "USD" to "$",
        "EUR" to "€",
        "GBP" to "£",
        "JPY" to "¥",
        "AUD" to "A$",
        "CAD" to "C$",
        "SGD" to "S$",
        "AED" to "د.إ",
        "SAR" to "﷼",
        "PKR" to "₨",
        "BDT" to "৳",
        "LKR" to "₨",
        "NPR" to "₨",
        "ZAR" to "R",
        "NGN" to "₦",
        "KES" to "KSh"
    )

    fun convertCurrency(amount: Double, fromCurrency: String, toCurrency: String, rates: Map<String, Double>): Double {
        if (fromCurrency == toCurrency) return amount
        val fromRate = rates[fromCurrency] ?: return amount
        val toRate = rates[toCurrency] ?: return amount
        return Math.round(amount * (toRate / fromRate) * 100) / 100.0
    }

    fun getCurrencySymbol(currency: String): String {
        return currencySymbols[currency] ?: currency
    }

    fun formatCurrency(amount: Double, currency: String): String {
        val symbol = currencySymbols[currency] ?: ""
        val formatted = String.format(Locale.getDefault(), "%,.2f", amount)
        return "$symbol$formatted"
    }

    fun formatConverted(
        amount: Double,
        fromCurrency: String,
        rates: Map<String, Double>?,
        userCurrency: String
    ): String {
        if (rates == null || fromCurrency == userCurrency) {
            return formatCurrency(amount, userCurrency)
        }
        val baseAmount = convertToBase(amount, fromCurrency, rates)
        val converted = convertFromBase(baseAmount, userCurrency, rates)
        return formatCurrency(converted, userCurrency)
    }
}
