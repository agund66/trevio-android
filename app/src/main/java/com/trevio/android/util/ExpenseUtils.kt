package com.trevio.android.util

import com.trevio.android.domain.model.SimplifiedDebt
import com.trevio.android.domain.model.SplitEntry
import com.trevio.android.domain.model.SplitType
import com.trevio.android.domain.model.ItemizedSplitData

/**
 * Utility for parsing amount expressions (e.g. "100+50*2").
 */
object ExpressionParser {

    fun evaluate(expr: String): Double {
        val tokens = mutableListOf<String>()
        var currentNum = StringBuilder()
        for (c in expr) {
            if (c == '+' || c == '-' || c == '*' || c == '/') {
                if (currentNum.isNotEmpty()) {
                    tokens.add(currentNum.toString())
                    currentNum = StringBuilder()
                }
                tokens.add(c.toString())
            } else {
                currentNum.append(c)
            }
        }
        if (currentNum.isNotEmpty()) tokens.add(currentNum.toString())

        if (tokens.isEmpty()) return 0.0

        val parsed = mutableListOf<Any>()
        var i = 0
        while (i < tokens.size) {
            val t = tokens[i]
            if (t == "*" || t == "/") {
                val prev = parsed.removeAt(parsed.lastIndex) as Double
                val next = tokens[++i].toDoubleOrNull() ?: 0.0
                parsed.add(if (t == "*") prev * next else if (next != 0.0) prev / next else 0.0)
            } else if (t == "+" || t == "-") {
                parsed.add(t)
            } else {
                parsed.add(t.toDoubleOrNull() ?: 0.0)
            }
            i++
        }

        var result = parsed[0] as Double
        var j = 1
        while (j < parsed.size) {
            val op = parsed[j] as String
            val next = parsed[++j] as Double
            result = if (op == "+") result + next else result - next
            j++
        }
        return result
    }

    /**
     * Parse an amount string that may contain arithmetic expressions.
     * Returns 0.0 for empty or invalid input.
     */
    fun parseAmount(amountStr: String): Double {
        val cleaned = amountStr.replace(Regex("[^0-9.+\\-*/]"), "")
        if (cleaned.isEmpty()) return 0.0
        return if (!cleaned.any { it == '+' || it == '-' || it == '*' || it == '/' }) {
            cleaned.toDoubleOrNull() ?: 0.0
        } else {
            try {
                evaluate(cleaned)
            } catch (e: Exception) {
                cleaned.toDoubleOrNull() ?: 0.0
            }
        }
    }
}

/**
 * Utility for building split entries from UI input.
 */
object SplitBuilder {

    data class SplitSummary(val totalEntered: Double, val target: Double)

    fun computeSummary(
        splitType: SplitType,
        splitValues: Map<String, String>,
        memberUids: List<String>,
        amount: Double
    ): SplitSummary? {
        if (splitType == SplitType.EQUAL || amount <= 0.0) return null
        var totalEntered = 0.0
        for (uid in memberUids) {
            totalEntered += splitValues[uid]?.toDoubleOrNull() ?: 0.0
        }
        return when (splitType) {
            SplitType.PERCENT -> SplitSummary(totalEntered, 100.0)
            SplitType.EXACT -> SplitSummary(totalEntered, amount)
            SplitType.SHARES -> SplitSummary(totalEntered, 0.0)
            else -> null
        }
    }

    fun isValid(
        splitType: SplitType,
        splitValues: Map<String, String>,
        memberUids: List<String>,
        amount: Double,
        itemizedData: ItemizedSplitData
    ): Boolean {
        return when (splitType) {
            SplitType.EQUAL -> memberUids.isNotEmpty()
            SplitType.ITEMIZED -> {
                if (itemizedData.items.isEmpty()) false
                else itemizedData.items.all { it.name.isNotBlank() && it.amount > 0.0 && it.assignedTo.isNotEmpty() }
            }
            else -> {
                if (amount <= 0.0 || memberUids.isEmpty()) false
                else if (splitType == SplitType.SHARES) {
                    splitValues.values.any { (it.toDoubleOrNull() ?: 0.0) > 0.0 }
                } else {
                    val summary = computeSummary(splitType, splitValues, memberUids, amount)
                    summary != null && kotlin.math.abs(summary.totalEntered - summary.target) < 0.01
                }
            }
        }
    }

    fun buildSplits(
        splitType: SplitType,
        splitValues: Map<String, String>,
        memberUids: List<String>
    ): Map<String, SplitEntry> {
        if (splitType == SplitType.EQUAL || splitType == SplitType.ITEMIZED) return emptyMap()
        val result = mutableMapOf<String, SplitEntry>()
        for (uid in memberUids) {
            val v = splitValues[uid]?.toDoubleOrNull() ?: 0.0
            if (v > 0.0) {
                when (splitType) {
                    SplitType.SHARES -> result[uid] = SplitEntry(amount = 0.0, shareValue = v)
                    SplitType.PERCENT -> result[uid] = SplitEntry(amount = 0.0, shareValue = v)
                    SplitType.EXACT -> result[uid] = SplitEntry(amount = v)
                    else -> {}
                }
            }
        }
        return result
    }
}

/**
 * Utility for UPI payment address resolution.
 */
object PaymentUtils {

    fun getUpiVpa(debt: SimplifiedDebt): String {
        if (debt.toUpiId.isNotEmpty()) return debt.toUpiId
        if (debt.toPhoneNumber.isNotEmpty() && (debt.toCountryCode.isEmpty() || debt.toCountryCode == "IN")) {
            return "${debt.toPhoneNumber}@paytm"
        }
        return ""
    }
}
