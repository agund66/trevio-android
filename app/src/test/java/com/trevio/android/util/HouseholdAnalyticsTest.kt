package com.trevio.android.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.LocalGroceryStore
import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import com.trevio.android.domain.model.Expense
import com.trevio.android.domain.model.Member
import com.trevio.android.domain.model.TransactionType
import java.util.Calendar
import org.junit.Test

class HouseholdAnalyticsTest {

    // ─── Helpers ───────────────────────────────────────────────────

    private fun ts(year: Int, month: Int, day: Int, hour: Int = 12, minute: Int = 0): Long {
        return Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun todayTs(hour: Int = 12): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun dayOffsetTs(daysAgo: Int, hour: Int = 12): Long {
        return Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -daysAgo)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun expense(
        amount: Double = 0.0,
        date: Long = 0,
        category: String = "other",
        paidBy: String = "u1",
        description: String = "test",
        transactionType: TransactionType = TransactionType.EXPENSE
    ) = Expense(
        amount = amount,
        date = date,
        category = category,
        paidBy = paidBy,
        description = description,
        transactionType = transactionType
    )

    private fun member(
        uid: String = "u1",
        displayName: String = "User",
        status: String = "active"
    ) = Member(uid = uid, displayName = displayName, status = status)

    private fun consecutiveDayExpenses(count: Int, paidBy: String = "u1"): List<Expense> {
        return (0 until count).map { i ->
            expense(amount = 10.0, date = dayOffsetTs(i), paidBy = paidBy, description = "entry $i")
        }
    }

    // ─── computeDailySummary ───────────────────────────────────────

    @Test
    fun dailySummary_emptyExpenses_returnsZerosAndEmptyEntries() {
        val result = computeDailySummary(emptyList(), ts(2024, 0, 15))
        assertThat(result.totalSpent).isEqualTo(0.0)
        assertThat(result.totalReceived).isEqualTo(0.0)
        assertThat(result.netAmount).isEqualTo(0.0)
        assertThat(result.entryCount).isEqualTo(0)
        assertThat(result.entries).isEmpty()
    }

    @Test
    fun dailySummary_singleExpenseOnSelectedDate_correctTotals() {
        val date = ts(2024, 0, 15)
        val expenses = listOf(expense(amount = 50.0, date = date))
        val result = computeDailySummary(expenses, date)
        assertThat(result.totalSpent).isEqualTo(50.0)
        assertThat(result.totalReceived).isEqualTo(0.0)
        assertThat(result.netAmount).isEqualTo(-50.0)
        assertThat(result.entryCount).isEqualTo(1)
        assertThat(result.entries).hasSize(1)
    }

    @Test
    fun dailySummary_singleIncomeOnSelectedDate_totalReceivedCorrect() {
        val date = ts(2024, 0, 15)
        val expenses = listOf(
            expense(amount = 1000.0, date = date, transactionType = TransactionType.INCOME)
        )
        val result = computeDailySummary(expenses, date)
        assertThat(result.totalReceived).isEqualTo(1000.0)
        assertThat(result.totalSpent).isEqualTo(0.0)
        assertThat(result.netAmount).isEqualTo(1000.0)
    }

    @Test
    fun dailySummary_mixedExpensesAndIncome_correctNet() {
        val date = ts(2024, 0, 15)
        val expenses = listOf(
            expense(amount = 200.0, date = date, transactionType = TransactionType.EXPENSE),
            expense(amount = 500.0, date = date, transactionType = TransactionType.INCOME),
            expense(amount = 100.0, date = date, transactionType = TransactionType.EXPENSE)
        )
        val result = computeDailySummary(expenses, date)
        assertThat(result.totalSpent).isEqualTo(300.0)
        assertThat(result.totalReceived).isEqualTo(500.0)
        assertThat(result.netAmount).isEqualTo(200.0)
        assertThat(result.entryCount).isEqualTo(3)
    }

    @Test
    fun dailySummary_expensesOnDifferentDates_filteredOut() {
        val selectedDate = ts(2024, 0, 15)
        val expenses = listOf(
            expense(amount = 50.0, date = ts(2024, 0, 16)),
            expense(amount = 30.0, date = ts(2024, 0, 14)),
            expense(amount = 20.0, date = selectedDate)
        )
        val result = computeDailySummary(expenses, selectedDate)
        assertThat(result.entryCount).isEqualTo(1)
        assertThat(result.totalSpent).isEqualTo(20.0)
    }

    @Test
    fun dailySummary_expenseWithDateZero_filteredOut() {
        val date = ts(2024, 0, 15)
        val expenses = listOf(
            expense(amount = 50.0, date = 0),
            expense(amount = 20.0, date = date)
        )
        val result = computeDailySummary(expenses, date)
        assertThat(result.entryCount).isEqualTo(1)
        assertThat(result.totalSpent).isEqualTo(20.0)
    }

    @Test
    fun dailySummary_multipleExpensesSameDay_allIncludedSortedDesc() {
        val date = ts(2024, 0, 15)
        val expenses = listOf(
            expense(amount = 10.0, date = ts(2024, 0, 15, 8), description = "morning"),
            expense(amount = 20.0, date = ts(2024, 0, 15, 14), description = "afternoon"),
            expense(amount = 30.0, date = ts(2024, 0, 15, 18), description = "evening")
        )
        val result = computeDailySummary(expenses, date)
        assertThat(result.entries).hasSize(3)
        assertThat(result.entries[0].description).isEqualTo("evening")
        assertThat(result.entries[1].description).isEqualTo("afternoon")
        assertThat(result.entries[2].description).isEqualTo("morning")
        assertThat(result.totalSpent).isEqualTo(60.0)
    }

    @Test
    fun dailySummary_transactionTypeDefaultsToExpenseWhenNotSpecified() {
        val date = ts(2024, 0, 15)
        val e = Expense(amount = 75.0, date = date, paidBy = "u1")
        val result = computeDailySummary(listOf(e), date)
        assertThat(result.totalSpent).isEqualTo(75.0)
        assertThat(result.totalReceived).isEqualTo(0.0)
    }

    @Test
    fun dailySummary_roundingToTwoDecimalPlaces() {
        val date = ts(2024, 0, 15)
        val expenses = listOf(
            expense(amount = 10.567, date = date),
            expense(amount = 20.334, date = date)
        )
        val result = computeDailySummary(expenses, date)
        // 10.567 + 20.334 = 30.901 → round2 = 30.9
        assertThat(result.totalSpent).isEqualTo(30.9)
    }

    // ─── computeMonthlyReport ──────────────────────────────────────

    @Test
    fun monthlyReport_emptyExpenses_allZeros() {
        val result = computeMonthlyReport(emptyList(), emptyList(), 2024, 0)
        assertThat(result.totalSpent).isEqualTo(0.0)
        assertThat(result.totalReceived).isEqualTo(0.0)
        assertThat(result.netAmount).isEqualTo(0.0)
        assertThat(result.entryCount).isEqualTo(0)
        assertThat(result.budgetProgress).isEqualTo(0.0)
        assertThat(result.budgetRemaining).isEqualTo(0.0)
        assertThat(result.comparisonWithLastMonth).isNull()
    }

    @Test
    fun monthlyReport_singleMonthExpensesOnly() {
        val expenses = listOf(
            expense(amount = 100.0, date = ts(2024, 0, 10)),
            expense(amount = 200.0, date = ts(2024, 0, 20))
        )
        val result = computeMonthlyReport(expenses, emptyList(), 2024, 0)
        assertThat(result.totalSpent).isEqualTo(300.0)
        assertThat(result.totalReceived).isEqualTo(0.0)
        assertThat(result.netAmount).isEqualTo(-300.0)
        assertThat(result.entryCount).isEqualTo(2)
    }

    @Test
    fun monthlyReport_singleMonthIncomeOnly() {
        val expenses = listOf(
            expense(amount = 5000.0, date = ts(2024, 0, 5), transactionType = TransactionType.INCOME)
        )
        val result = computeMonthlyReport(expenses, emptyList(), 2024, 0)
        assertThat(result.totalReceived).isEqualTo(5000.0)
        assertThat(result.totalSpent).isEqualTo(0.0)
        assertThat(result.netAmount).isEqualTo(5000.0)
    }

    @Test
    fun monthlyReport_bothExpensesAndIncome() {
        val expenses = listOf(
            expense(amount = 300.0, date = ts(2024, 0, 10), transactionType = TransactionType.EXPENSE),
            expense(amount = 1000.0, date = ts(2024, 0, 15), transactionType = TransactionType.INCOME)
        )
        val result = computeMonthlyReport(expenses, emptyList(), 2024, 0)
        assertThat(result.totalSpent).isEqualTo(300.0)
        assertThat(result.totalReceived).isEqualTo(1000.0)
        assertThat(result.netAmount).isEqualTo(700.0)
    }

    @Test
    fun monthlyReport_expensesFromOtherMonthsFilteredOut() {
        val expenses = listOf(
            expense(amount = 100.0, date = ts(2024, 0, 10)),
            expense(amount = 200.0, date = ts(2024, 1, 10)),
            expense(amount = 50.0, date = ts(2023, 11, 10))
        )
        val result = computeMonthlyReport(expenses, emptyList(), 2024, 0)
        assertThat(result.totalSpent).isEqualTo(100.0)
        assertThat(result.entryCount).isEqualTo(1)
    }

    @Test
    fun monthlyReport_budgetProgressZeroPercent() {
        val expenses = listOf(
            expense(amount = 0.0, date = ts(2024, 0, 10))
        )
        val result = computeMonthlyReport(expenses, emptyList(), 2024, 0, monthlyBudget = 1000.0)
        assertThat(result.budgetProgress).isEqualTo(0.0)
        assertThat(result.budgetRemaining).isEqualTo(1000.0)
    }

    @Test
    fun monthlyReport_budgetProgressFiftyPercent() {
        val expenses = listOf(
            expense(amount = 500.0, date = ts(2024, 0, 10))
        )
        val result = computeMonthlyReport(expenses, emptyList(), 2024, 0, monthlyBudget = 1000.0)
        assertThat(result.budgetProgress).isEqualTo(50.0)
        assertThat(result.budgetRemaining).isEqualTo(500.0)
    }

    @Test
    fun monthlyReport_budgetProgressHundredPercent() {
        val expenses = listOf(
            expense(amount = 1000.0, date = ts(2024, 0, 10))
        )
        val result = computeMonthlyReport(expenses, emptyList(), 2024, 0, monthlyBudget = 1000.0)
        assertThat(result.budgetProgress).isEqualTo(100.0)
        assertThat(result.budgetRemaining).isEqualTo(0.0)
    }

    @Test
    fun monthlyReport_budgetProgressOverHundredPercent() {
        val expenses = listOf(
            expense(amount = 1500.0, date = ts(2024, 0, 10))
        )
        val result = computeMonthlyReport(expenses, emptyList(), 2024, 0, monthlyBudget = 1000.0)
        assertThat(result.budgetProgress).isEqualTo(150.0)
        assertThat(result.budgetRemaining).isEqualTo(-500.0)
    }

    @Test
    fun monthlyReport_budgetProgressNoBudget_returnsZero() {
        val expenses = listOf(
            expense(amount = 500.0, date = ts(2024, 0, 10))
        )
        val result = computeMonthlyReport(expenses, emptyList(), 2024, 0, monthlyBudget = null)
        assertThat(result.budgetProgress).isEqualTo(0.0)
        assertThat(result.budgetRemaining).isEqualTo(0.0)
        assertThat(result.budget).isNull()
    }

    @Test
    fun monthlyReport_dailyTrendData() {
        val expenses = listOf(
            expense(amount = 100.0, date = ts(2024, 0, 15, 10)),
            expense(amount = 50.0, date = ts(2024, 0, 15, 14), transactionType = TransactionType.INCOME),
            expense(amount = 200.0, date = ts(2024, 0, 20))
        )
        val result = computeMonthlyReport(expenses, emptyList(), 2024, 0)
        assertThat(result.dailyTrend).hasSize(31) // January has 31 days
        val day15 = result.dailyTrend.first { it.day == 15 }
        assertThat(day15.totalSpent).isEqualTo(100.0)
        assertThat(day15.totalReceived).isEqualTo(50.0)
        val day20 = result.dailyTrend.first { it.day == 20 }
        assertThat(day20.totalSpent).isEqualTo(200.0)
        val day1 = result.dailyTrend.first { it.day == 1 }
        assertThat(day1.totalSpent).isEqualTo(0.0)
    }

    @Test
    fun monthlyReport_categoryBreakdown() {
        val expenses = listOf(
            expense(amount = 300.0, date = ts(2024, 0, 10), category = "groceries"),
            expense(amount = 100.0, date = ts(2024, 0, 11), category = "transport"),
            expense(amount = 200.0, date = ts(2024, 0, 12), category = "groceries")
        )
        val result = computeMonthlyReport(expenses, emptyList(), 2024, 0)
        assertThat(result.spentByCategory).hasSize(2)
        assertThat(result.spentByCategory[0].category).isEqualTo("groceries")
        assertThat(result.spentByCategory[0].totalAmount).isEqualTo(500.0)
        assertThat(result.spentByCategory[0].expenseCount).isEqualTo(2)
        assertThat(result.spentByCategory[1].category).isEqualTo("transport")
        assertThat(result.spentByCategory[1].totalAmount).isEqualTo(100.0)
    }

    @Test
    fun monthlyReport_memberContributions() {
        val members = listOf(member("u1", "Alice"), member("u2", "Bob"))
        val expenses = listOf(
            expense(amount = 300.0, date = ts(2024, 0, 10), paidBy = "u1"),
            expense(amount = 100.0, date = ts(2024, 0, 11), paidBy = "u2")
        )
        val result = computeMonthlyReport(expenses, members, 2024, 0)
        assertThat(result.memberContributions).hasSize(2)
        assertThat(result.memberContributions[0].uid).isEqualTo("u1")
        assertThat(result.memberContributions[0].totalSpent).isEqualTo(300.0)
        assertThat(result.memberContributions[1].uid).isEqualTo("u2")
    }

    @Test
    fun monthlyReport_februaryHas28Days() {
        val result = computeMonthlyReport(emptyList(), emptyList(), 2023, 1)
        assertThat(result.dailyTrend).hasSize(28)
        assertThat(result.dailyTrend.last().day).isEqualTo(28)
    }

    @Test
    fun monthlyReport_monthLabelCorrect() {
        val result = computeMonthlyReport(emptyList(), emptyList(), 2024, 0)
        assertThat(result.monthLabel).isEqualTo("January 2024")
        assertThat(result.month).isEqualTo("2024-01")
    }

    // ─── computeGamification ───────────────────────────────────────

    @Test
    fun gamification_emptyExpensesAndMembers_streakZeroParticipationZero() {
        val result = computeGamification(emptyList(), emptyList())
        assertThat(result.loggingStreak).isEqualTo(0)
        assertThat(result.participationToday).isEqualTo(0.0)
        assertThat(result.membersLoggedToday).isEqualTo(0)
        assertThat(result.totalMembers).isEqualTo(0)
        assertThat(result.monthlyBadge).isNull()
        assertThat(result.insightMessage).isNull()
    }

    @Test
    fun gamification_streakOneDay() {
        val expenses = listOf(expense(amount = 10.0, date = todayTs()))
        val result = computeGamification(expenses, emptyList())
        assertThat(result.loggingStreak).isEqualTo(1)
    }

    @Test
    fun gamification_streakThreeDays() {
        val expenses = consecutiveDayExpenses(3)
        val result = computeGamification(expenses, emptyList())
        assertThat(result.loggingStreak).isEqualTo(3)
    }

    @Test
    fun gamification_streakSevenDays() {
        val expenses = consecutiveDayExpenses(7)
        val result = computeGamification(expenses, emptyList())
        assertThat(result.loggingStreak).isEqualTo(7)
    }

    @Test
    fun gamification_streakThirtyDays() {
        val expenses = consecutiveDayExpenses(30)
        val result = computeGamification(expenses, emptyList())
        assertThat(result.loggingStreak).isEqualTo(30)
    }

    @Test
    fun gamification_brokenStreak_resetsToZero() {
        // Most recent entry is 2 days ago → not today or yesterday → streak 0
        val expenses = listOf(
            expense(amount = 10.0, date = dayOffsetTs(2)),
            expense(amount = 10.0, date = dayOffsetTs(3))
        )
        val result = computeGamification(expenses, emptyList())
        assertThat(result.loggingStreak).isEqualTo(0)
        assertThat(result.streakStartDate).isNull()
    }

    @Test
    fun gamification_participationZeroLogged() {
        val members = listOf(member("u1", "Alice"), member("u2", "Bob"), member("u3", "Carol"))
        val expenses = listOf(expense(amount = 10.0, date = dayOffsetTs(1)))
        val result = computeGamification(expenses, members)
        assertThat(result.membersLoggedToday).isEqualTo(0)
        assertThat(result.participationToday).isEqualTo(0.0)
    }

    @Test
    fun gamification_participationOneOfThree() {
        val members = listOf(member("u1", "Alice"), member("u2", "Bob"), member("u3", "Carol"))
        val expenses = listOf(expense(amount = 10.0, date = todayTs(), paidBy = "u1"))
        val result = computeGamification(expenses, members)
        assertThat(result.membersLoggedToday).isEqualTo(1)
        assertThat(result.participationToday).isEqualTo(33.33)
    }

    @Test
    fun gamification_participationAllThree() {
        val members = listOf(member("u1", "Alice"), member("u2", "Bob"), member("u3", "Carol"))
        val expenses = listOf(
            expense(amount = 10.0, date = todayTs(10), paidBy = "u1"),
            expense(amount = 20.0, date = todayTs(11), paidBy = "u2"),
            expense(amount = 30.0, date = todayTs(12), paidBy = "u3")
        )
        val result = computeGamification(expenses, members)
        assertThat(result.membersLoggedToday).isEqualTo(3)
        assertThat(result.participationToday).isEqualTo(100.0)
    }

    @Test
    fun gamification_participationZeroTotalMembers() {
        val expenses = listOf(expense(amount = 10.0, date = todayTs(), paidBy = "u1"))
        val result = computeGamification(expenses, emptyList())
        assertThat(result.totalMembers).isEqualTo(0)
        assertThat(result.participationToday).isEqualTo(0.0)
    }

    @Test
    fun gamification_badgeStreakChampion() {
        val expenses = consecutiveDayExpenses(30)
        val result = computeGamification(expenses, emptyList(), monthlyBudget = null, monthlySpent = 0.0)
        assertThat(result.monthlyBadge).isEqualTo("streak_champion")
    }

    @Test
    fun gamification_badgeBudgetMaster() {
        // No streak (empty), budget set, spent within budget
        val result = computeGamification(emptyList(), emptyList(), monthlyBudget = 1000.0, monthlySpent = 500.0)
        assertThat(result.monthlyBadge).isEqualTo("budget_master")
    }

    @Test
    fun gamification_badgeAllStars() {
        val members = listOf(member("u1", "Alice"), member("u2", "Bob"))
        val expenses = listOf(
            expense(amount = 10.0, date = todayTs(10), paidBy = "u1"),
            expense(amount = 20.0, date = todayTs(11), paidBy = "u2")
        )
        val result = computeGamification(expenses, members)
        assertThat(result.monthlyBadge).isEqualTo("all_stars")
    }

    @Test
    fun gamification_badgeNone() {
        val members = listOf(member("u1", "Alice"), member("u2", "Bob"))
        val expenses = listOf(expense(amount = 10.0, date = todayTs(), paidBy = "u1"))
        val result = computeGamification(expenses, members, monthlyBudget = null, monthlySpent = 0.0)
        // streak=1 (<30), no budget, 1 of 2 logged (not all) → no badge
        assertThat(result.monthlyBadge).isNull()
    }

    @Test
    fun gamification_insightBudgetExceeded() {
        val result = computeGamification(emptyList(), emptyList(), monthlyBudget = 100.0, monthlySpent = 120.0)
        assertThat(result.insightMessage).isEqualTo("You've exceeded your monthly budget by ₹20.00")
    }

    @Test
    fun gamification_insightWithinBudget() {
        val result = computeGamification(emptyList(), emptyList(), monthlyBudget = 100.0, monthlySpent = 85.0)
        assertThat(result.insightMessage).isEqualTo("You've used 85.0% of your budget. ₹15.00 left.")
    }

    @Test
    fun gamification_insightNoBudget_returnsNull() {
        val result = computeGamification(emptyList(), emptyList(), monthlyBudget = null, monthlySpent = 0.0)
        assertThat(result.insightMessage).isNull()
    }

    @Test
    fun gamification_insightTopCategory() {
        // No budget → falls to category insight; groceries is >40% of spending
        val expenses = listOf(
            expense(amount = 100.0, date = todayTs(), category = "groceries"),
            expense(amount = 50.0, date = todayTs(), category = "transport")
        )
        val result = computeGamification(expenses, emptyList(), monthlyBudget = null, monthlySpent = 0.0)
        assertThat(result.insightMessage).isEqualTo("Groceries is 66.67% of your spending this month")
    }

    // ─── computeMemberContributions ────────────────────────────────

    @Test
    fun memberContributions_emptyExpensesAndMembers_returnsEmptyList() {
        val result = computeMemberContributions(emptyList(), emptyList())
        assertThat(result).isEmpty()
    }

    @Test
    fun memberContributions_singleMemberPaidEverything() {
        val members = listOf(member("u1", "Alice"))
        val expenses = listOf(
            expense(amount = 100.0, date = ts(2024, 0, 10), paidBy = "u1"),
            expense(amount = 200.0, date = ts(2024, 0, 11), paidBy = "u1")
        )
        val result = computeMemberContributions(expenses, members)
        assertThat(result).hasSize(1)
        assertThat(result[0].uid).isEqualTo("u1")
        assertThat(result[0].totalSpent).isEqualTo(300.0)
        assertThat(result[0].entryCount).isEqualTo(2)
        assertThat(result[0].spentPercentage).isEqualTo(100.0)
        assertThat(result[0].rank).isEqualTo(1)
    }

    @Test
    fun memberContributions_multipleMembersDifferentAmounts() {
        val members = listOf(member("u1", "Alice"), member("u2", "Bob"))
        val expenses = listOf(
            expense(amount = 300.0, date = ts(2024, 0, 10), paidBy = "u1"),
            expense(amount = 100.0, date = ts(2024, 0, 11), paidBy = "u2")
        )
        val result = computeMemberContributions(expenses, members)
        assertThat(result).hasSize(2)
        assertThat(result[0].uid).isEqualTo("u1")
        assertThat(result[0].totalSpent).isEqualTo(300.0)
        assertThat(result[1].uid).isEqualTo("u2")
        assertThat(result[1].totalSpent).isEqualTo(100.0)
    }

    @Test
    fun memberContributions_sortedByTotalPaidDescending() {
        val members = listOf(member("u1", "Alice"), member("u2", "Bob"), member("u3", "Carol"))
        val expenses = listOf(
            expense(amount = 100.0, date = ts(2024, 0, 10), paidBy = "u1"),
            expense(amount = 500.0, date = ts(2024, 0, 11), paidBy = "u2"),
            expense(amount = 200.0, date = ts(2024, 0, 12), paidBy = "u3")
        )
        val result = computeMemberContributions(expenses, members)
        assertThat(result[0].uid).isEqualTo("u2")
        assertThat(result[0].totalSpent).isEqualTo(500.0)
        assertThat(result[0].rank).isEqualTo(1)
        assertThat(result[1].uid).isEqualTo("u3")
        assertThat(result[1].totalSpent).isEqualTo(200.0)
        assertThat(result[1].rank).isEqualTo(2)
        assertThat(result[2].uid).isEqualTo("u1")
        assertThat(result[2].totalSpent).isEqualTo(100.0)
        assertThat(result[2].rank).isEqualTo(3)
    }

    @Test
    fun memberContributions_inactiveMembersFilteredOut() {
        val members = listOf(member("u1", "Alice"), member("u2", "Bob", status = "left"))
        val expenses = listOf(
            expense(amount = 100.0, date = ts(2024, 0, 10), paidBy = "u1"),
            expense(amount = 200.0, date = ts(2024, 0, 11), paidBy = "u2")
        )
        val result = computeMemberContributions(expenses, members)
        assertThat(result).hasSize(1)
        assertThat(result[0].uid).isEqualTo("u1")
    }

    @Test
    fun memberContributions_incomeRecordedAsTotalReceived() {
        val members = listOf(member("u1", "Alice"))
        val expenses = listOf(
            expense(amount = 500.0, date = ts(2024, 0, 10), paidBy = "u1", transactionType = TransactionType.INCOME)
        )
        val result = computeMemberContributions(expenses, members)
        assertThat(result[0].totalReceived).isEqualTo(500.0)
        assertThat(result[0].totalSpent).isEqualTo(0.0)
        assertThat(result[0].entryCount).isEqualTo(1)
    }

    @Test
    fun memberContributions_expenseByUnknownMemberIgnored() {
        val members = listOf(member("u1", "Alice"))
        val expenses = listOf(
            expense(amount = 100.0, date = ts(2024, 0, 10), paidBy = "u1"),
            expense(amount = 200.0, date = ts(2024, 0, 11), paidBy = "unknown")
        )
        val result = computeMemberContributions(expenses, members)
        assertThat(result).hasSize(1)
        assertThat(result[0].totalSpent).isEqualTo(100.0)
    }

    // ─── HouseholdCategories ───────────────────────────────────────

    @Test
    fun getCategory_returnsCorrectCategoryForKnownKey() {
        val cat = HouseholdCategories.getCategory("groceries")
        assertThat(cat).isNotNull()
        assertThat(cat!!.key).isEqualTo("groceries")
        assertThat(cat.label).isEqualTo("Groceries")
    }

    @Test
    fun getCategory_returnsCorrectCategoryForIncomeKey() {
        val cat = HouseholdCategories.getCategory("salary")
        assertThat(cat).isNotNull()
        assertThat(cat!!.key).isEqualTo("salary")
        assertThat(cat.label).isEqualTo("Salary")
        assertThat(cat.isIncome).isTrue()
    }

    @Test
    fun getCategory_returnsNullForUnknownKey() {
        assertThat(HouseholdCategories.getCategory("nonexistent")).isNull()
    }

    @Test
    fun getCategoryLabel_returnsCorrectLabel() {
        assertThat(HouseholdCategories.getCategoryLabel("groceries")).isEqualTo("Groceries")
        assertThat(HouseholdCategories.getCategoryLabel("rent")).isEqualTo("Rent")
        assertThat(HouseholdCategories.getCategoryLabel("salary")).isEqualTo("Salary")
    }

    @Test
    fun getCategoryLabel_returnsCapitalizedKeyForUnknown() {
        assertThat(HouseholdCategories.getCategoryLabel("foobar")).isEqualTo("Foobar")
    }

    @Test
    fun getCategoryColor_returnsCorrectColor() {
        assertThat(HouseholdCategories.getCategoryColor("groceries")).isEqualTo(Color(0xFFF97316))
        assertThat(HouseholdCategories.getCategoryColor("utilities")).isEqualTo(Color(0xFF3B82F6))
    }

    @Test
    fun getCategoryColor_returnsDefaultForUnknown() {
        assertThat(HouseholdCategories.getCategoryColor("nonexistent")).isEqualTo(Color(0xFF94A3B8))
    }

    @Test
    fun getCategoryIcon_returnsCorrectIcon() {
        assertThat(HouseholdCategories.getCategoryIcon("groceries")).isEqualTo(Icons.Filled.LocalGroceryStore)
    }

    @Test
    fun getCategoryIcon_returnsDefaultForUnknown() {
        assertThat(HouseholdCategories.getCategoryIcon("nonexistent")).isEqualTo(Icons.Filled.Category)
    }

    @Test
    fun expenseCategories_has13Items() {
        assertThat(HouseholdCategories.EXPENSE_CATEGORIES).hasSize(13)
    }

    @Test
    fun incomeCategories_has8Items() {
        assertThat(HouseholdCategories.INCOME_CATEGORIES).hasSize(8)
    }

    @Test
    fun getCategories_trueReturnsIncomeCategories() {
        val result = HouseholdCategories.getCategories(true)
        assertThat(result).hasSize(8)
        assertThat(result.all { it.isIncome }).isTrue()
    }

    @Test
    fun getCategories_falseReturnsExpenseCategories() {
        val result = HouseholdCategories.getCategories(false)
        assertThat(result).hasSize(13)
        assertThat(result.none { it.isIncome }).isTrue()
    }

    @Test
    fun suggestCategory_matchesKeywords() {
        assertThat(HouseholdCategories.suggestCategory("Bought petrol for car")).isEqualTo("transport")
        assertThat(HouseholdCategories.suggestCategory("Electricity bill payment")).isEqualTo("utilities")
        assertThat(HouseholdCategories.suggestCategory("Netflix subscription")).isEqualTo("entertainment")
        assertThat(HouseholdCategories.suggestCategory("Monthly salary")).isEqualTo("salary")
    }

    @Test
    fun suggestCategory_returnsNullForNoMatch() {
        assertThat(HouseholdCategories.suggestCategory("xyzqwerty")).isNull()
    }

    @Test
    fun suggestCategory_returnsNullForBlank() {
        assertThat(HouseholdCategories.suggestCategory("")).isNull()
        assertThat(HouseholdCategories.suggestCategory("   ")).isNull()
    }

    @Test
    fun suggestCategory_multiWordPhrasePriority() {
        // "tata power" is a multi-word phrase that should match "utilities"
        assertThat(HouseholdCategories.suggestCategory("tata power bill")).isEqualTo("utilities")
        // "health insurance" should match "insurance" not "medical"
        assertThat(HouseholdCategories.suggestCategory("health insurance premium")).isEqualTo("insurance")
    }

    @Test
    fun suggestDescriptions_returnsMatches() {
        val expenses = listOf(
            expense(description = "Cafe lunch"),
            expense(description = "Carrot"),
            expense(description = "Groceries"),
            expense(description = "Coffee shop")
        )
        val result = suggestDescriptions(expenses, "ca")
        assertThat(result).containsExactly("Cafe lunch", "Carrot")
    }

    @Test
    fun suggestDescriptions_returnsEmptyForNoHistory() {
        val result = suggestDescriptions(emptyList(), "ca")
        assertThat(result).isEmpty()
    }

    @Test
    fun suggestDescriptions_returnsEmptyForBlankPrefix() {
        val expenses = listOf(expense(description = "Cafe lunch"))
        assertThat(suggestDescriptions(expenses, "")).isEmpty()
        assertThat(suggestDescriptions(expenses, "   ")).isEmpty()
    }

    @Test
    fun suggestDescriptions_respectsLimit() {
        val expenses = (1..10).map { expense(description = "item$it") }
        val result = suggestDescriptions(expenses, "item", limit = 3)
        assertThat(result).hasSize(3)
    }

    @Test
    fun suggestDescriptions_removesDuplicates() {
        val expenses = listOf(
            expense(description = "Coffee"),
            expense(description = "Coffee"),
            expense(description = "Coffee shop")
        )
        val result = suggestDescriptions(expenses, "co")
        assertThat(result).containsExactly("Coffee", "Coffee shop")
    }

    @Test
    fun suggestDescriptions_ignoresBlankDescriptions() {
        val expenses = listOf(
            expense(description = ""),
            expense(description = "   "),
            expense(description = "Carrot")
        )
        val result = suggestDescriptions(expenses, "ca")
        assertThat(result).containsExactly("Carrot")
    }

    @Test
    fun computeCategoryUsageCount_countsCorrectly() {
        val expenses = listOf(
            expense(category = "groceries"),
            expense(category = "groceries"),
            expense(category = "transport"),
            expense(category = "")
        )
        val result = computeCategoryUsageCount(expenses)
        assertThat(result["groceries"]).isEqualTo(2)
        assertThat(result["transport"]).isEqualTo(1)
        assertThat(result["other"]).isEqualTo(1)
    }

    @Test
    fun computeCategoryUsageCount_emptyExpenses_returnsEmptyMap() {
        val result = computeCategoryUsageCount(emptyList())
        assertThat(result).isEmpty()
    }

    // ─── Edge Cases ────────────────────────────────────────────────

    @Test
    fun dailySummary_veryLargeAmounts() {
        val date = ts(2024, 0, 15)
        val expenses = listOf(expense(amount = 1_000_000.0, date = date))
        val result = computeDailySummary(expenses, date)
        assertThat(result.totalSpent).isEqualTo(1_000_000.0)
    }

    @Test
    fun dailySummary_verySmallAmounts() {
        val date = ts(2024, 0, 15)
        val expenses = listOf(expense(amount = 0.01, date = date))
        val result = computeDailySummary(expenses, date)
        assertThat(result.totalSpent).isEqualTo(0.01)
    }

    @Test
    fun dailySummary_manyDecimalPlacesRounded() {
        val date = ts(2024, 0, 15)
        val expenses = listOf(expense(amount = 10.123456789, date = date))
        val result = computeDailySummary(expenses, date)
        assertThat(result.totalSpent).isEqualTo(10.12)
    }

    @Test
    fun dailySummary_emptyDescriptionsStillIncluded() {
        val date = ts(2024, 0, 15)
        val expenses = listOf(
            expense(amount = 50.0, date = date, description = "")
        )
        val result = computeDailySummary(expenses, date)
        assertThat(result.entries).hasSize(1)
        assertThat(result.entries[0].description).isEmpty()
    }

    @Test
    fun categoryBreakdown_multipleExpensesSameCategorySameDay() {
        val date = ts(2024, 0, 15)
        val expenses = listOf(
            expense(amount = 100.0, date = date, category = "groceries"),
            expense(amount = 200.0, date = date, category = "groceries")
        )
        val result = computeHouseholdCategoryBreakdown(expenses)
        assertThat(result).hasSize(1)
        assertThat(result[0].category).isEqualTo("groceries")
        assertThat(result[0].totalAmount).isEqualTo(300.0)
        assertThat(result[0].expenseCount).isEqualTo(2)
        assertThat(result[0].percentage).isEqualTo(100.0)
    }

    @Test
    fun categoryBreakdown_emptyExpenses_returnsEmptyList() {
        val result = computeHouseholdCategoryBreakdown(emptyList())
        assertThat(result).isEmpty()
    }

    @Test
    fun categoryBreakdown_emptyCategoryMapsToOther() {
        val expenses = listOf(
            expense(amount = 50.0, category = ""),
            expense(amount = 100.0, category = "groceries")
        )
        val result = computeHouseholdCategoryBreakdown(expenses)
        assertThat(result).hasSize(2)
        val other = result.first { it.category == "other" }
        assertThat(other.totalAmount).isEqualTo(50.0)
        assertThat(other.expenseCount).isEqualTo(1)
    }

    @Test
    fun categoryBreakdown_percentagesCalculatedCorrectly() {
        val expenses = listOf(
            expense(amount = 300.0, category = "groceries"),
            expense(amount = 100.0, category = "transport")
        )
        val result = computeHouseholdCategoryBreakdown(expenses)
        assertThat(result[0].percentage).isEqualTo(75.0)
        assertThat(result[1].percentage).isEqualTo(25.0)
    }

    @Test
    fun dailyTrend_emptyExpenses_allDaysZero() {
        val result = computeDailyTrend(emptyList(), 2024, 0)
        assertThat(result).hasSize(31)
        assertThat(result.all { it.totalSpent == 0.0 && it.totalReceived == 0.0 }).isTrue()
    }

    @Test
    fun dailyTrend_incomeRecordedSeparately() {
        val expenses = listOf(
            expense(amount = 200.0, date = ts(2024, 0, 10), transactionType = TransactionType.INCOME)
        )
        val result = computeDailyTrend(expenses, 2024, 0)
        val day10 = result.first { it.day == 10 }
        assertThat(day10.totalReceived).isEqualTo(200.0)
        assertThat(day10.totalSpent).isEqualTo(0.0)
    }

    @Test
    fun monthComparison_noLastMonthData_returnsNull() {
        val expenses = listOf(expense(amount = 100.0, date = ts(2024, 0, 10)))
        val result = computeMonthComparison(expenses, 2024, 0)
        assertThat(result).isNull()
    }

    @Test
    fun monthComparison_withLastMonthData_returnsComparison() {
        val expenses = listOf(
            expense(amount = 100.0, date = ts(2024, 0, 10)),
            expense(amount = 200.0, date = ts(2023, 11, 10))
        )
        val result = computeMonthComparison(expenses, 2024, 0)
        assertThat(result).isNotNull()
        assertThat(result!!.lastMonthSpent).isEqualTo(200.0)
        assertThat(result.spentChange).isEqualTo(-100.0)
    }

    @Test
    fun detectRecurringPattern_notRecurring_returnsFalse() {
        val expenses = listOf(
            expense(amount = 100.0, date = ts(2024, 0, 10), description = "Rent")
        )
        assertThat(detectRecurringPattern(expenses, "Rent", 100.0)).isFalse()
    }

    @Test
    fun detectRecurringPattern_recurring_returnsTrue() {
        val expenses = listOf(
            expense(amount = 100.0, date = ts(2024, 0, 10), description = "Rent"),
            expense(amount = 100.0, date = ts(2024, 1, 10), description = "Rent"),
            expense(amount = 100.0, date = ts(2024, 2, 10), description = "Rent")
        )
        assertThat(detectRecurringPattern(expenses, "Rent", 100.0)).isTrue()
    }

    @Test
    fun detectRecurringPattern_blankDescription_returnsFalse() {
        assertThat(detectRecurringPattern(emptyList(), "", 100.0)).isFalse()
    }
}
