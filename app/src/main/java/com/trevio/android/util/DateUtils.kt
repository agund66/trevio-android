package com.trevio.android.util

import com.google.firebase.Timestamp
import com.trevio.android.R
import com.trevio.android.domain.model.LocalizedString
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.text.SimpleDateFormat

object DateUtils {

    // ─── Time constants ──────────────────────────────────────────
    const val MS_PER_SECOND: Long = 1000
    const val MS_PER_MINUTE: Long = 60 * MS_PER_SECOND
    const val MS_PER_HOUR: Long = 60 * MS_PER_MINUTE
    const val MS_PER_DAY: Long = 24 * MS_PER_HOUR

    // ─── Month labels ────────────────────────────────────────────

    private val shortMonthResIds = intArrayOf(
        R.string.month_jan, R.string.month_feb, R.string.month_mar, R.string.month_apr,
        R.string.month_may, R.string.month_jun, R.string.month_jul, R.string.month_aug,
        R.string.month_sep, R.string.month_oct, R.string.month_nov, R.string.month_dec
    )

    private val fullMonthResIds = intArrayOf(
        R.string.month_january, R.string.month_february, R.string.month_march, R.string.month_april,
        R.string.month_may_full, R.string.month_june, R.string.month_july, R.string.month_august,
        R.string.month_september, R.string.month_october, R.string.month_november, R.string.month_december
    )

    fun getMonthLabelResId(month: Int): Int = shortMonthResIds.getOrElse(month) { R.string.month_jan }

    fun getFullMonthLabelResId(month: Int): Int = fullMonthResIds.getOrElse(month) { R.string.month_january }

    // ─── Core helpers ────────────────────────────────────────────

    /** Converts any Firestore date value to milliseconds. Handles Timestamp, Date, Number, String, and Map. */
    fun toMillis(value: Any?): Long? {
        return when (value) {
            is Timestamp -> value.toDate().time
            is Date -> value.time
            is Number -> value.toLong()
            is String -> {
                return try {
                    val parsed = Date(value).time
                    if (parsed > 0) parsed else 0L
                } catch (e: Exception) {
                    0L
                }
            }
            is Map<*, *> -> {
                val seconds = (value["seconds"] as? Number)?.toLong() ?: (value["_seconds"] as? Number)?.toLong()
                val nanoseconds = (value["nanoseconds"] as? Number)?.toLong() ?: (value["_nanoseconds"] as? Number)?.toLong()
                if (seconds != null) {
                    seconds * 1000 + (nanoseconds?.div(1_000_000) ?: 0L)
                } else null
            }
            null -> null
            else -> null
        }
    }

    /** Returns true if two timestamps fall on the same calendar day. */
    fun isSameDay(timestamp1: Long, timestamp2: Long): Boolean {
        if (timestamp1 <= 0 || timestamp2 <= 0) return false
        val cal1 = Calendar.getInstance().apply { timeInMillis = timestamp1 }
        val cal2 = Calendar.getInstance().apply { timeInMillis = timestamp2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
            cal1.get(Calendar.MONTH) == cal2.get(Calendar.MONTH) &&
            cal1.get(Calendar.DAY_OF_MONTH) == cal2.get(Calendar.DAY_OF_MONTH)
    }

    /** Returns true if the timestamp falls in the given year/month (0-indexed month). */
    fun isSameMonth(timestamp: Long, year: Int, month: Int): Boolean {
        if (timestamp <= 0) return false
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        return cal.get(Calendar.YEAR) == year && cal.get(Calendar.MONTH) == month
    }

    /** Returns a Calendar set to the start of the given timestamp's day (midnight). */
    fun startOfDay(timestamp: Long): Calendar {
        return Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    /** Formats a timestamp as YYYY-MM-DD for date inputs. */
    fun formatDateToISO(timestamp: Long): String {
        if (timestamp <= 0) return ""
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    // ─── Display formatters ──────────────────────────────────────

    /** Formats a timestamp as a short date (e.g., "Mon, Jan 15"). */
    fun formatShortDate(timestamp: Long): String {
        if (timestamp <= 0) return ""
        val sdf = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun formatWeekday(timestamp: Long): String {
        if (timestamp <= 0) return ""
        val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
        return calendar.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.getDefault()).orEmpty()
    }

    /** Formats a timestamp as a full date with time (e.g., "Mon, Jan 15, 2024 · 3:45 PM"). */
    fun formatFullDate(timestamp: Long): String {
        if (timestamp <= 0) return ""
        val sdf = SimpleDateFormat("EEE, MMM d, yyyy · h:mm a", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    /** Formats a timestamp as time only (e.g., "3:45 PM"). */
    fun formatTime(timestamp: Long): String {
        if (timestamp <= 0) return ""
        val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun formatRelativeTime(timestamp: Long): LocalizedString? {
        if (timestamp <= 0) return null
        val now = System.currentTimeMillis()
        val diffMs = now - timestamp
        val diffMins = diffMs / MS_PER_MINUTE
        val diffHours = diffMs / MS_PER_HOUR
        val diffDays = diffMs / MS_PER_DAY
        return when {
            diffMins < 1 -> LocalizedString(R.string.time_just_now)
            diffMins < 60 -> LocalizedString(R.string.time_minutes_ago, listOf(diffMins))
            diffHours < 24 -> LocalizedString(R.string.time_hours_ago, listOf(diffHours))
            diffDays < 7 -> LocalizedString(R.string.time_days_ago, listOf(diffDays))
            else -> null
        }
    }

    /** Returns current time in milliseconds. */
    fun now(): Long = System.currentTimeMillis()
}
