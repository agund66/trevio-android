package com.trevio.android.util

import kotlin.math.round

object CurrencyConverter {

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

    fun formatCurrency(amount: Double, currency: String): String {
        val symbol = currencySymbols[currency] ?: ""
        val formatted = String.format("%,.2f", amount)
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
