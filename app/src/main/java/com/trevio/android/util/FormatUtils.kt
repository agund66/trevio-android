package com.trevio.android.util

import java.util.Locale

object FormatUtils {
    fun formatAmount(amount: Double): String {
        return if (amount == amount.toLong().toDouble()) {
            amount.toLong().toString()
        } else {
            String.format(Locale.getDefault(), "%.2f", amount)
        }
    }
}
