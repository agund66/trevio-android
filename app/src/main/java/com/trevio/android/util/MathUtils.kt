package com.trevio.android.util

import kotlin.math.round

object MathUtils {
    /** Rounds a number to 2 decimal places. */
    fun round2(value: Double): Double = round(value * 100) / 100.0

    /** Rounds a number to the specified decimal places. */
    fun roundTo(value: Double, decimals: Int): Double {
        val factor = Math.pow(10.0, decimals.toDouble())
        return round(value * factor) / factor
    }
}
