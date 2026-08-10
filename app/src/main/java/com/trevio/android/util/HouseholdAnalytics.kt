package com.trevio.android.util

import com.trevio.android.domain.model.CategoryBreakdown
import com.trevio.android.domain.model.DailySummary
import com.trevio.android.domain.model.DailyTrend
import com.trevio.android.domain.model.Expense
import com.trevio.android.domain.model.HouseholdGamification
import com.trevio.android.domain.model.Member
import com.trevio.android.domain.model.MemberContribution
import com.trevio.android.domain.model.MonthComparison
import com.trevio.android.domain.model.MonthlyReport
import com.trevio.android.domain.model.TransactionType
import java.util.Calendar
import java.util.Locale

private fun formatDateLabel(timestamp: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
    val now = Calendar.getInstance()
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }

    return when {
        DateUtils.isSameDay(timestamp, now.timeInMillis) -> {
            val day = cal.get(Calendar.DAY_OF_MONTH)
            val month = DateUtils.MONTH_LABELS[cal.get(Calendar.MONTH)]
            "Today, $day $month"
        }
        DateUtils.isSameDay(timestamp, yesterday.timeInMillis) -> {
            val day = cal.get(Calendar.DAY_OF_MONTH)
            val month = DateUtils.MONTH_LABELS[cal.get(Calendar.MONTH)]
            "Yesterday, $day $month"
        }
        else -> {
            val day = cal.get(Calendar.DAY_OF_MONTH)
            val month = DateUtils.MONTH_LABELS[cal.get(Calendar.MONTH)]
            val weekday = cal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.getDefault())
            "$weekday, $day $month"
        }
    }
}

private fun formatTime(timestamp: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
    val hour = cal.get(Calendar.HOUR_OF_DAY)
    val minute = cal.get(Calendar.MINUTE)
    val amPm = if (hour < 12) "AM" else "PM"
    val hour12 = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
    return String.format("%d:%02d %s", hour12, minute, amPm)
}

// ─── Daily Summary ──────────────────────────────────────────────

fun computeDailySummary(
    allExpenses: List<Expense>,
    date: Long = System.currentTimeMillis()
): DailySummary {
    val dayExpenses = allExpenses.filter { DateUtils.isSameDay(it.date, date) }
        .sortedByDescending { it.date }

    val totalSpent = dayExpenses
        .filter { it.transactionType == TransactionType.EXPENSE }
        .sumOf { it.amount }

    val totalReceived = dayExpenses
        .filter { it.transactionType == TransactionType.INCOME }
        .sumOf { it.amount }

    return DailySummary(
        date = date,
        dateLabel = formatDateLabel(date),
        totalSpent = MathUtils.round2(totalSpent),
        totalReceived = MathUtils.round2(totalReceived),
        netAmount = MathUtils.round2(totalReceived - totalSpent),
        entryCount = dayExpenses.size,
        entries = dayExpenses
    )
}

// ─── Monthly Report ─────────────────────────────────────────────

fun computeMonthlyReport(
    allExpenses: List<Expense>,
    members: List<Member>,
    year: Int,
    month: Int,
    monthlyBudget: Double? = null
): MonthlyReport {
    val monthExpenses = allExpenses.filter { DateUtils.isSameMonth(it.date, year, month) }

    val expenseEntries = monthExpenses.filter { it.transactionType == TransactionType.EXPENSE }
    val incomeEntries = monthExpenses.filter { it.transactionType == TransactionType.INCOME }

    val totalSpent = expenseEntries.sumOf { it.amount }
    val totalReceived = incomeEntries.sumOf { it.amount }

    val spentByCategory = computeHouseholdCategoryBreakdown(expenseEntries)
    val receivedByCategory = computeHouseholdCategoryBreakdown(incomeEntries)
    val memberContributions = computeMemberContributions(monthExpenses, members)
    val dailyTrend = computeDailyTrend(allExpenses, year, month)

    val budgetProgress = if (monthlyBudget != null && monthlyBudget > 0) {
        MathUtils.round2((totalSpent / monthlyBudget) * 100)
    } else 0.0

    val budgetRemaining = if (monthlyBudget != null) {
        MathUtils.round2(monthlyBudget - totalSpent)
    } else 0.0

    val comparison = computeMonthComparison(allExpenses, year, month)

    val monthLabel = "${DateUtils.FULL_MONTH_LABELS[month]} $year"

    return MonthlyReport(
        month = String.format("%04d-%02d", year, month + 1),
        monthLabel = monthLabel,
        totalSpent = MathUtils.round2(totalSpent),
        totalReceived = MathUtils.round2(totalReceived),
        netAmount = MathUtils.round2(totalReceived - totalSpent),
        entryCount = monthExpenses.size,
        spentByCategory = spentByCategory,
        receivedByCategory = receivedByCategory,
        memberContributions = memberContributions,
        dailyTrend = dailyTrend,
        budget = monthlyBudget,
        budgetProgress = budgetProgress,
        budgetRemaining = budgetRemaining,
        comparisonWithLastMonth = comparison
    )
}

// ─── Category Breakdown for Household ───────────────────────────

fun computeHouseholdCategoryBreakdown(expenses: List<Expense>): List<CategoryBreakdown> {
    if (expenses.isEmpty()) return emptyList()
    val map = mutableMapOf<String, Pair<Double, Int>>()
    for (e in expenses) {
        val cat = e.category.ifEmpty { "other" }
        val existing = map[cat] ?: (0.0 to 0)
        map[cat] = (existing.first + e.amount) to (existing.second + 1)
    }
    val grandTotal = expenses.sumOf { it.amount }
    return map.map { (category, pair) ->
        CategoryBreakdown(
            category = category,
            totalAmount = MathUtils.round2(pair.first),
            expenseCount = pair.second,
            percentage = if (grandTotal > 0) MathUtils.round2((pair.first / grandTotal) * 100) else 0.0
        )
    }.sortedByDescending { it.totalAmount }
}

// ─── Member Contributions ───────────────────────────────────────

fun computeMemberContributions(
    expenses: List<Expense>,
    members: List<Member>
): List<MemberContribution> {
    val map = mutableMapOf<String, MemberContribution>()
    val activeMembers = members.filter { it.status == MemberStatus.ACTIVE }

    for (m in activeMembers) {
        map[m.uid] = MemberContribution(
            uid = m.uid,
            displayName = m.displayName,
            photoURL = m.photoURL,
            totalSpent = 0.0,
            totalReceived = 0.0,
            entryCount = 0
        )
    }

    val totalSpentAll = expenses.filter { it.transactionType == TransactionType.EXPENSE }.sumOf { it.amount }

    for (e in expenses) {
        val member = map[e.paidBy] ?: continue
        when (e.transactionType) {
            TransactionType.EXPENSE -> {
                map[e.paidBy] = member.copy(
                    totalSpent = member.totalSpent + e.amount,
                    entryCount = member.entryCount + 1
                )
            }
            TransactionType.INCOME -> {
                map[e.paidBy] = member.copy(
                    totalReceived = member.totalReceived + e.amount,
                    entryCount = member.entryCount + 1
                )
            }
        }
    }

    return map.values.map { m ->
        m.copy(
            totalSpent = MathUtils.round2(m.totalSpent),
            totalReceived = MathUtils.round2(m.totalReceived),
            spentPercentage = if (totalSpentAll > 0) MathUtils.round2((m.totalSpent / totalSpentAll) * 100) else 0.0
        )
    }.sortedByDescending { it.totalSpent }
        .mapIndexed { index, m -> m.copy(rank = index + 1) }
}

// ─── Daily Trend (for monthly bar chart) ────────────────────────

fun computeDailyTrend(
    allExpenses: List<Expense>,
    year: Int,
    month: Int
): List<DailyTrend> {
    val cal = Calendar.getInstance().apply {
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, month)
        set(Calendar.DAY_OF_MONTH, 1)
    }
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

    val trends = mutableListOf<DailyTrend>()
    for (day in 1..daysInMonth) {
        val dayCal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        trends.add(DailyTrend(
            day = day,
            date = dayCal.timeInMillis,
            totalSpent = 0.0,
            totalReceived = 0.0
        ))
    }

    val trendMap = trends.associateBy { it.day }.toMutableMap()

    for (e in allExpenses) {
        if (!DateUtils.isSameMonth(e.date, year, month)) continue
        val expCal = Calendar.getInstance().apply { timeInMillis = e.date }
        val day = expCal.get(Calendar.DAY_OF_MONTH)
        val trend = trendMap[day] ?: continue

        when (e.transactionType) {
            TransactionType.EXPENSE -> {
                trendMap[day] = trend.copy(totalSpent = trend.totalSpent + e.amount)
            }
            TransactionType.INCOME -> {
                trendMap[day] = trend.copy(totalReceived = trend.totalReceived + e.amount)
            }
        }
    }

    return trends.map { trendMap[it.day] ?: it }
        .map { it.copy(totalSpent = MathUtils.round2(it.totalSpent), totalReceived = MathUtils.round2(it.totalReceived)) }
}

// ─── Month Comparison ───────────────────────────────────────────

fun computeMonthComparison(
    allExpenses: List<Expense>,
    year: Int,
    month: Int
): MonthComparison? {
    val currentMonthExpenses = allExpenses.filter { DateUtils.isSameMonth(it.date, year, month) }
    val currentSpent = currentMonthExpenses
        .filter { it.transactionType == TransactionType.EXPENSE }
        .sumOf { it.amount }
    val currentReceived = currentMonthExpenses
        .filter { it.transactionType == TransactionType.INCOME }
        .sumOf { it.amount }

    val lastMonthCal = Calendar.getInstance().apply {
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, month)
        add(Calendar.MONTH, -1)
    }
    val lastYear = lastMonthCal.get(Calendar.YEAR)
    val lastMonth = lastMonthCal.get(Calendar.MONTH)

    val lastMonthExpenses = allExpenses.filter { DateUtils.isSameMonth(it.date, lastYear, lastMonth) }
    val lastSpent = lastMonthExpenses
        .filter { it.transactionType == TransactionType.EXPENSE }
        .sumOf { it.amount }
    val lastReceived = lastMonthExpenses
        .filter { it.transactionType == TransactionType.INCOME }
        .sumOf { it.amount }

    if (lastSpent == 0.0 && lastReceived == 0.0) return null

    val spentChange = currentSpent - lastSpent
    val spentChangePercent = if (lastSpent > 0) MathUtils.round2((spentChange / lastSpent) * 100) else 0.0
    val receivedChange = currentReceived - lastReceived

    return MonthComparison(
        lastMonthSpent = MathUtils.round2(lastSpent),
        spentChange = MathUtils.round2(spentChange),
        spentChangePercent = spentChangePercent,
        lastMonthReceived = MathUtils.round2(lastReceived),
        receivedChange = MathUtils.round2(receivedChange)
    )
}

// ─── Gamification ───────────────────────────────────────────────

fun computeGamification(
    allExpenses: List<Expense>,
    members: List<Member>,
    monthlyBudget: Double? = null,
    monthlySpent: Double = 0.0,
    currency: String = "INR"
): HouseholdGamification {
    val activeMembers = members.filter { it.status == MemberStatus.ACTIVE }
    val totalMembers = activeMembers.size

    // Streak: consecutive days (ending today or yesterday) with >=1 entry
    val streak = computeLoggingStreak(allExpenses)

    // Participation today: how many members logged today
    val today = System.currentTimeMillis()
    val todayExpenses = allExpenses.filter { DateUtils.isSameDay(it.date, today) }
    val membersLoggedToday = todayExpenses.map { it.paidBy }.distinct().size
    val participationToday = if (totalMembers > 0) {
        MathUtils.round2((membersLoggedToday.toDouble() / totalMembers) * 100)
    } else 0.0

    // Monthly badge
    val monthlyBadge = computeMonthlyBadge(streak.count, monthlyBudget, monthlySpent, totalMembers, membersLoggedToday)

    // Insight message
    val insightMessage = computeInsightMessage(allExpenses, monthlyBudget, monthlySpent, currency)

    return HouseholdGamification(
        loggingStreak = streak.count,
        streakStartDate = streak.startDate,
        monthlyBadge = monthlyBadge,
        participationToday = participationToday,
        membersLoggedToday = membersLoggedToday,
        totalMembers = totalMembers,
        insightMessage = insightMessage
    )
}

private data class StreakResult(val count: Int, val startDate: Long?)

private fun computeLoggingStreak(allExpenses: List<Expense>): StreakResult {
    if (allExpenses.isEmpty()) return StreakResult(0, null)

    // Get unique dates with entries (as day timestamps)
    val entryDates = allExpenses
        .filter { it.date > 0 }
        .map { exp ->
            Calendar.getInstance().apply {
                timeInMillis = exp.date
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }
        .toSet()
        .sortedDescending()

    if (entryDates.isEmpty()) return StreakResult(0, null)

    // Check if the most recent entry is today or yesterday
    val today = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    val yesterday = today - 24L * 60 * 60 * 1000

    if (entryDates.first() != today && entryDates.first() != yesterday) {
        return StreakResult(0, null)
    }

    // Count consecutive days
    var streak = 0
    var streakStart: Long? = null
    var checkDate = entryDates.first()

    for (entryDate in entryDates) {
        if (entryDate == checkDate) {
            streak++
            streakStart = entryDate
            checkDate -= 24L * 60 * 60 * 1000
        } else if (entryDate < checkDate) {
            break
        }
    }

    return StreakResult(streak, streakStart)
}

private fun computeMonthlyBadge(
    streak: Int,
    monthlyBudget: Double?,
    monthlySpent: Double,
    totalMembers: Int,
    membersLoggedToday: Int
): String? {
    val badges = mutableListOf<String>()

    if (streak >= 30) badges.add("streak_champion")
    if (monthlyBudget != null && monthlyBudget > 0 && monthlySpent <= monthlyBudget) {
        badges.add("budget_master")
    }
    if (totalMembers > 0 && membersLoggedToday == totalMembers) {
        badges.add("all_stars")
    }

    return badges.firstOrNull()
}

private fun computeInsightMessage(
    allExpenses: List<Expense>,
    monthlyBudget: Double?,
    monthlySpent: Double,
    currency: String = "INR"
): String? {
    // Budget insight
    if (monthlyBudget != null && monthlyBudget > 0) {
        val progress = (monthlySpent / monthlyBudget) * 100
        when {
            progress >= 100 -> return "You've exceeded your monthly budget by ${CurrencyConverter.formatCurrency(MathUtils.round2(monthlySpent - monthlyBudget), currency)}"
            progress >= 80 -> return "You've used ${MathUtils.round2(progress)}% of your budget. ${CurrencyConverter.formatCurrency(MathUtils.round2(monthlyBudget - monthlySpent), currency)} left."
        }
    }

    // Category insight
    val now = Calendar.getInstance()
    val monthExpenses = allExpenses.filter {
        DateUtils.isSameMonth(it.date, now.get(Calendar.YEAR), now.get(Calendar.MONTH)) &&
        it.transactionType == TransactionType.EXPENSE
    }
    if (monthExpenses.isNotEmpty()) {
        val breakdown = computeHouseholdCategoryBreakdown(monthExpenses)
        val topCategory = breakdown.firstOrNull()
        if (topCategory != null && topCategory.percentage >= 40) {
            val label = HouseholdCategories.getCategoryLabel(topCategory.category)
            return "$label is ${MathUtils.round2(topCategory.percentage)}% of your spending this month"
        }
    }

    return null
}

// ─── Category Usage Count (for smart ordering) ──────────────────

fun computeCategoryUsageCount(expenses: List<Expense>): Map<String, Int> {
    val map = mutableMapOf<String, Int>()
    for (e in expenses) {
        val cat = e.category.ifEmpty { "other" }
        map[cat] = (map[cat] ?: 0) + 1
    }
    return map
}

// ─── Description Autocomplete ───────────────────────────────────

fun suggestDescriptions(
    expenses: List<Expense>,
    prefix: String,
    limit: Int = 5
): List<String> {
    if (prefix.isBlank()) return emptyList()
    val lower = prefix.trim().lowercase()
    return expenses
        .map { it.description }
        .filter { it.isNotBlank() && it.lowercase().startsWith(lower) }
        .distinct()
        .take(limit)
}

// ─── Recurring Pattern Detection ────────────────────────────────

data class RecurringPattern(
    val description: String,
    val category: String,
    val avgAmount: Double,
    val monthCount: Int
)

fun detectRecurringPattern(
    expenses: List<Expense>,
    description: String,
    amount: Double
): Boolean {
    if (description.isBlank()) return false
    val lower = description.trim().lowercase()

    // Check if same description appeared in at least 3 different months with similar amounts (±20%)
    val matching = expenses.filter {
        it.description.lowercase() == lower &&
        it.transactionType == TransactionType.EXPENSE
    }

    if (matching.size < 2) return false

    val monthKeys = matching.map { exp ->
        val cal = Calendar.getInstance().apply { timeInMillis = exp.date }
        "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.MONTH)}"
    }.distinct()

    if (monthKeys.size < 2) return false

    // Check amount similarity (within 20% of the new amount)
    val minAmount = amount * 0.8
    val maxAmount = amount * 1.2
    val similarAmounts = matching.count { it.amount in minAmount..maxAmount }

    return similarAmounts >= 2
}
