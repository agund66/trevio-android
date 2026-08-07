package com.trevio.android.util

import com.trevio.android.domain.model.CategoryBreakdown
import com.trevio.android.domain.model.Expense
import com.trevio.android.domain.model.GroupAnalytics
import com.trevio.android.domain.model.HighestExpense
import com.trevio.android.domain.model.Member
import com.trevio.android.domain.model.MemberSpending
import com.trevio.android.domain.model.MonthlyTrend
import com.trevio.android.domain.model.TopGroupSpending
import com.trevio.android.domain.model.UserAnalytics
import java.util.Calendar

private val MONTH_LABELS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

fun computeCategoryBreakdown(expenses: List<Expense>): List<CategoryBreakdown> {
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
            totalAmount = Math.round(pair.first * 100) / 100.0,
            expenseCount = pair.second,
            percentage = if (grandTotal > 0) Math.round((pair.first / grandTotal) * 10000) / 100.0 else 0.0
        )
    }.sortedByDescending { it.totalAmount }
}

fun computeMonthlyTrends(expenses: List<Expense>, months: Int = 6): List<MonthlyTrend> {
    val now = Calendar.getInstance()
    val trends = mutableListOf<MonthlyTrend>()
    for (i in (months - 1) downTo 0) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, now.get(Calendar.YEAR))
            set(Calendar.MONTH, now.get(Calendar.MONTH) - i)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val key = "${cal.get(Calendar.YEAR)}-${String.format("%02d", cal.get(Calendar.MONTH) + 1)}"
        trends.add(MonthlyTrend(
            month = key,
            label = "${MONTH_LABELS[cal.get(Calendar.MONTH)]} ${cal.get(Calendar.YEAR).toString().takeLast(2)}",
            totalAmount = 0.0,
            expenseCount = 0
        ))
    }
    val trendMap = trends.associateBy { it.month }.toMutableMap()
    for (e in expenses) {
        val cal = Calendar.getInstance().apply {
            timeInMillis = if (e.date > 0) e.date else System.currentTimeMillis()
        }
        val key = "${cal.get(Calendar.YEAR)}-${String.format("%02d", cal.get(Calendar.MONTH) + 1)}"
        val trend = trendMap[key] ?: continue
        trendMap[key] = trend.copy(
            totalAmount = trend.totalAmount + e.amount,
            expenseCount = trend.expenseCount + 1
        )
    }
    return trends.map { t ->
        trendMap[t.month] ?: t
    }.map { t ->
        t.copy(totalAmount = Math.round(t.totalAmount * 100) / 100.0)
    }
}

fun computeMemberSpending(
    expenses: List<Expense>,
    members: List<Member>
): List<MemberSpending> {
    val map = mutableMapOf<String, MemberSpending>()
    for (m in members) {
        map[m.uid] = MemberSpending(
            uid = m.uid,
            displayName = m.displayName,
            photoURL = m.photoURL,
            totalPaid = 0.0,
            totalShare = 0.0,
            expenseCount = 0,
            netBalance = m.balance
        )
    }
    for (e in expenses) {
        val payer = map[e.paidBy]
        if (payer != null) {
            map[e.paidBy] = payer.copy(
                totalPaid = payer.totalPaid + e.amount,
                expenseCount = payer.expenseCount + 1
            )
        }
        for ((uid, split) in e.splits) {
            val member = map[uid]
            if (member != null) {
                map[uid] = member.copy(totalShare = member.totalShare + split.amount)
            }
        }
    }
    return map.values.map { m ->
        m.copy(
            totalPaid = Math.round(m.totalPaid * 100) / 100.0,
            totalShare = Math.round(m.totalShare * 100) / 100.0
        )
    }.sortedByDescending { it.totalPaid }
}

fun computeGroupAnalytics(
    groupId: String,
    groupName: String,
    expenses: List<Expense>,
    members: List<Member>
): GroupAnalytics {
    val totalExpenses = Math.round(expenses.sumOf { it.amount } * 100) / 100.0
    val expenseCount = expenses.size
    val avgExpenseAmount = if (expenseCount > 0) Math.round((totalExpenses / expenseCount) * 100) / 100.0 else 0.0

    val highestExpense = if (expenses.isNotEmpty()) {
        val highest = expenses.maxByOrNull { it.amount }!!
        HighestExpense(
            description = highest.description,
            amount = highest.amount,
            date = highest.date
        )
    } else null

    val now = System.currentTimeMillis()
    val thirtyDaysAgo = now - 30L * 24 * 60 * 60 * 1000
    val recentExpenses = expenses.count { it.date >= thirtyDaysAgo }
    val recentActivityRate = if (expenseCount > 0) Math.round((recentExpenses.toDouble() / expenseCount) * 10000) / 100.0 else 0.0

    return GroupAnalytics(
        groupId = groupId,
        groupName = groupName,
        totalExpenses = totalExpenses,
        expenseCount = expenseCount,
        categoryBreakdown = computeCategoryBreakdown(expenses),
        monthlyTrends = computeMonthlyTrends(expenses),
        memberSpending = computeMemberSpending(expenses, members),
        avgExpenseAmount = avgExpenseAmount,
        highestExpense = highestExpense,
        recentActivityRate = recentActivityRate
    )
}

fun computeUserAnalytics(
    groups: List<Triple<String, String, Double>>,
    allExpensesByGroup: Map<String, List<Expense>>,
    currentUserId: String
): UserAnalytics {
    var totalSpent = 0.0
    var totalPaid = 0.0
    var expenseCount = 0
    val allExpenses = mutableListOf<Expense>()
    val groupSpendingMap = mutableMapOf<String, TopGroupSpending>()

    for ((groupId, groupName, _) in groups) {
        val expenses = allExpensesByGroup[groupId] ?: emptyList()
        for (e in expenses) {
            allExpenses.add(e)
            totalSpent += e.amount
            expenseCount++
            if (e.paidBy == currentUserId) {
                totalPaid += e.amount
            }
        }
        val groupTotal = expenses.sumOf { it.amount }
        groupSpendingMap[groupId] = TopGroupSpending(
            groupId = groupId,
            groupName = groupName,
            totalSpent = Math.round(groupTotal * 100) / 100.0,
            expenseCount = expenses.size
        )
    }

    val totalOwed = groups.filter { it.third > 0 }.sumOf { it.third }
    val totalOwing = groups.filter { it.third < 0 }.sumOf { -it.third }

    val topGroups = groupSpendingMap.values.sortedByDescending { it.totalSpent }.take(5)

    return UserAnalytics(
        totalSpent = Math.round(totalSpent * 100) / 100.0,
        totalPaid = Math.round(totalPaid * 100) / 100.0,
        totalOwed = Math.round(totalOwed * 100) / 100.0,
        totalOwing = Math.round(totalOwing * 100) / 100.0,
        netBalance = Math.round((totalOwed - totalOwing) * 100) / 100.0,
        groupCount = groups.size,
        expenseCount = expenseCount,
        categoryBreakdown = computeCategoryBreakdown(allExpenses),
        monthlyTrends = computeMonthlyTrends(allExpenses),
        topGroups = topGroups
    )
}
