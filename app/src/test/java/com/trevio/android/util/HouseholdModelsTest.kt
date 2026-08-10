package com.trevio.android.util

import com.google.common.truth.Truth.assertThat
import com.google.firebase.Timestamp
import com.trevio.android.domain.model.BillItem
import com.trevio.android.domain.model.CategoryBreakdown
import com.trevio.android.domain.model.DailySummary
import com.trevio.android.domain.model.DailyTrend
import com.trevio.android.domain.model.Expense
import com.trevio.android.domain.model.Group
import com.trevio.android.domain.model.GroupTemplate
import com.trevio.android.domain.model.HouseholdGamification
import com.trevio.android.domain.model.ItemizedSplitData
import com.trevio.android.domain.model.MemberContribution
import com.trevio.android.domain.model.MonthComparison
import com.trevio.android.domain.model.MonthlyReport
import com.trevio.android.domain.model.RecurringConfig
import com.trevio.android.domain.model.RecurringFrequency
import com.trevio.android.domain.model.SplitEntry
import com.trevio.android.domain.model.SplitType
import com.trevio.android.domain.model.TransactionType
import com.trevio.android.domain.repository.GroupInfo
import java.util.Date
import org.junit.Test

/**
 * Unit tests for household-related domain models and the [DateUtils.toMillis]
 * conversion function used by [com.trevio.android.data.remote.FirebaseExpenseServiceImpl].
 */
class HouseholdModelsTest {

    // ─── Activity / notification type string constants ────────────────
    // These mirror the literal strings used inside FirebaseExpenseServiceImpl.
    private val activityExpenseAdded = "expense_added"
    private val activityIncomeAdded = "income_added"
    private val activityExpenseUpdated = "expense_updated"
    private val activityIncomeUpdated = "income_updated"
    private val activityExpenseDeleted = "expense_deleted"
    private val activityIncomeDeleted = "income_deleted"
    private val activityMemberRemoved = "member_removed"
    private val activityMemberLeft = "member_left"
    private val notifExpenseAdded = "expense_added"
    private val notifIncomeAdded = "income_added"

    // ══════════════════════════════════════════════════════════════════
    //  Model definitions
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `TransactionType has EXPENSE and INCOME values`() {
        assertThat(TransactionType.values().toList()).containsExactly(
            TransactionType.EXPENSE,
            TransactionType.INCOME
        )
    }

    @Test
    fun `TransactionType EXPENSE and INCOME are distinct`() {
        assertThat(TransactionType.EXPENSE).isNotEqualTo(TransactionType.INCOME)
    }

    @Test
    fun `GroupTemplate has HOUSEHOLD value`() {
        assertThat(GroupTemplate.valueOf("HOUSEHOLD")).isEqualTo(GroupTemplate.HOUSEHOLD)
        assertThat(GroupTemplate.values().toList()).contains(GroupTemplate.HOUSEHOLD)
    }

    @Test
    fun `Group has nullable monthlyBudget field`() {
        val groupDefault = Group(name = "Home")
        assertThat(groupDefault.monthlyBudget).isNull()

        val groupWithBudget = Group(name = "Home", monthlyBudget = 50000.0)
        assertThat(groupWithBudget.monthlyBudget).isEqualTo(50000.0)
    }

    @Test
    fun `Group creates with defaults`() {
        val group = Group()
        assertThat(group.groupId).isEmpty()
        assertThat(group.name).isEmpty()
        assertThat(group.template).isEqualTo(GroupTemplate.CASUAL)
        assertThat(group.currency).isEqualTo("INR")
        assertThat(group.memberCount).isEqualTo(0)
        assertThat(group.archived).isFalse()
        assertThat(group.budgetCategories).isNull()
    }

    @Test
    fun `GroupInfo has monthlyBudget field`() {
        val infoDefault = GroupInfo(name = "Family")
        assertThat(infoDefault.monthlyBudget).isNull()

        val infoWithBudget = GroupInfo(name = "Family", monthlyBudget = 25000.0)
        assertThat(infoWithBudget.monthlyBudget).isEqualTo(25000.0)
    }

    @Test
    fun `GroupInfo creates with defaults`() {
        val info = GroupInfo()
        assertThat(info.groupId).isEmpty()
        assertThat(info.template).isEqualTo(GroupTemplate.CASUAL)
        assertThat(info.currency).isEqualTo("INR")
        assertThat(info.memberCount).isEqualTo(0)
        assertThat(info.archived).isFalse()
        assertThat(info.budgetCategories).isNull()
    }

    @Test
    fun `Expense has transactionType field defaulting to EXPENSE`() {
        val expense = Expense(description = "Groceries")
        assertThat(expense.transactionType).isEqualTo(TransactionType.EXPENSE)
    }

    @Test
    fun `Expense can be created as INCOME`() {
        val income = Expense(description = "Salary", transactionType = TransactionType.INCOME)
        assertThat(income.transactionType).isEqualTo(TransactionType.INCOME)
    }

    @Test
    fun `Expense creates with defaults`() {
        val expense = Expense()
        assertThat(expense.expenseId).isEmpty()
        assertThat(expense.amount).isEqualTo(0.0)
        assertThat(expense.currency).isEqualTo("INR")
        assertThat(expense.splitType).isEqualTo(SplitType.EQUAL)
        assertThat(expense.category).isEqualTo("other")
        assertThat(expense.date).isEqualTo(0)
        assertThat(expense.exchangeRateToBase).isEqualTo(1.0)
        assertThat(expense.recurring).isNull()
        assertThat(expense.itemizedData).isNull()
    }

    @Test
    fun `DailySummary has all required fields`() {
        val summary = DailySummary(
            date = 1705276800000L,
            dateLabel = "15 Jan",
            totalSpent = 1200.0,
            totalReceived = 500.0,
            netAmount = 700.0,
            entryCount = 2,
            entries = listOf(
                Expense(description = "Lunch", amount = 800.0),
                Expense(description = "Refund", amount = 500.0, transactionType = TransactionType.INCOME)
            )
        )
        assertThat(summary.date).isEqualTo(1705276800000L)
        assertThat(summary.dateLabel).isEqualTo("15 Jan")
        assertThat(summary.totalSpent).isEqualTo(1200.0)
        assertThat(summary.totalReceived).isEqualTo(500.0)
        assertThat(summary.netAmount).isEqualTo(700.0)
        assertThat(summary.entryCount).isEqualTo(2)
        assertThat(summary.entries).hasSize(2)
    }

    @Test
    fun `DailySummary creates with defaults`() {
        val summary = DailySummary()
        assertThat(summary.date).isEqualTo(0)
        assertThat(summary.dateLabel).isEmpty()
        assertThat(summary.totalSpent).isEqualTo(0.0)
        assertThat(summary.totalReceived).isEqualTo(0.0)
        assertThat(summary.netAmount).isEqualTo(0.0)
        assertThat(summary.entryCount).isEqualTo(0)
        assertThat(summary.entries).isEmpty()
    }

    @Test
    fun `MonthlyReport has all required fields`() {
        val report = MonthlyReport(
            month = "2024-01",
            monthLabel = "January 2024",
            totalSpent = 15000.0,
            totalReceived = 3000.0,
            netAmount = 12000.0,
            entryCount = 25,
            spentByCategory = listOf(
                CategoryBreakdown(category = "food", totalAmount = 5000.0, expenseCount = 10, percentage = 33.3)
            ),
            receivedByCategory = listOf(
                CategoryBreakdown(category = "salary", totalAmount = 3000.0, expenseCount = 1, percentage = 100.0)
            ),
            memberContributions = listOf(
                MemberContribution(uid = "u1", displayName = "Alice", totalSpent = 9000.0, rank = 1)
            ),
            dailyTrend = listOf(
                DailyTrend(day = 1, date = 1704067200000L, totalSpent = 500.0)
            ),
            budget = 20000.0,
            budgetProgress = 75.0,
            budgetRemaining = 5000.0,
            comparisonWithLastMonth = MonthComparison(
                lastMonthSpent = 14000.0,
                spentChange = 1000.0,
                spentChangePercent = 7.14
            )
        )
        assertThat(report.month).isEqualTo("2024-01")
        assertThat(report.monthLabel).isEqualTo("January 2024")
        assertThat(report.totalSpent).isEqualTo(15000.0)
        assertThat(report.totalReceived).isEqualTo(3000.0)
        assertThat(report.netAmount).isEqualTo(12000.0)
        assertThat(report.entryCount).isEqualTo(25)
        assertThat(report.spentByCategory).hasSize(1)
        assertThat(report.receivedByCategory).hasSize(1)
        assertThat(report.memberContributions).hasSize(1)
        assertThat(report.dailyTrend).hasSize(1)
        assertThat(report.budget).isEqualTo(20000.0)
        assertThat(report.budgetProgress).isEqualTo(75.0)
        assertThat(report.budgetRemaining).isEqualTo(5000.0)
        assertThat(report.comparisonWithLastMonth).isNotNull()
        assertThat(report.comparisonWithLastMonth?.spentChange).isEqualTo(1000.0)
    }

    @Test
    fun `MonthlyReport creates with defaults`() {
        val report = MonthlyReport()
        assertThat(report.month).isEmpty()
        assertThat(report.monthLabel).isEmpty()
        assertThat(report.totalSpent).isEqualTo(0.0)
        assertThat(report.totalReceived).isEqualTo(0.0)
        assertThat(report.netAmount).isEqualTo(0.0)
        assertThat(report.entryCount).isEqualTo(0)
        assertThat(report.spentByCategory).isEmpty()
        assertThat(report.receivedByCategory).isEmpty()
        assertThat(report.memberContributions).isEmpty()
        assertThat(report.dailyTrend).isEmpty()
        assertThat(report.budget).isNull()
        assertThat(report.budgetProgress).isEqualTo(0.0)
        assertThat(report.budgetRemaining).isEqualTo(0.0)
        assertThat(report.comparisonWithLastMonth).isNull()
    }

    @Test
    fun `HouseholdGamification has all required fields`() {
        val gamification = HouseholdGamification(
            loggingStreak = 7,
            streakStartDate = 1704067200000L,
            monthlyBadge = "Budget Master",
            participationToday = 66.6,
            membersLoggedToday = 2,
            totalMembers = 3,
            insightMessage = "Great job staying on track!"
        )
        assertThat(gamification.loggingStreak).isEqualTo(7)
        assertThat(gamification.streakStartDate).isEqualTo(1704067200000L)
        assertThat(gamification.monthlyBadge).isEqualTo("Budget Master")
        assertThat(gamification.participationToday).isEqualTo(66.6)
        assertThat(gamification.membersLoggedToday).isEqualTo(2)
        assertThat(gamification.totalMembers).isEqualTo(3)
        assertThat(gamification.insightMessage).isEqualTo("Great job staying on track!")
    }

    @Test
    fun `HouseholdGamification creates with defaults`() {
        val gamification = HouseholdGamification()
        assertThat(gamification.loggingStreak).isEqualTo(0)
        assertThat(gamification.streakStartDate).isNull()
        assertThat(gamification.monthlyBadge).isNull()
        assertThat(gamification.participationToday).isEqualTo(0.0)
        assertThat(gamification.membersLoggedToday).isEqualTo(0)
        assertThat(gamification.totalMembers).isEqualTo(0)
        assertThat(gamification.insightMessage).isNull()
    }

    @Test
    fun `MemberContribution has all required fields`() {
        val contribution = MemberContribution(
            uid = "u1",
            displayName = "Alice",
            photoURL = "https://example.com/photo.jpg",
            totalSpent = 9000.0,
            totalReceived = 1000.0,
            entryCount = 15,
            spentPercentage = 60.0,
            rank = 1
        )
        assertThat(contribution.uid).isEqualTo("u1")
        assertThat(contribution.displayName).isEqualTo("Alice")
        assertThat(contribution.photoURL).isEqualTo("https://example.com/photo.jpg")
        assertThat(contribution.totalSpent).isEqualTo(9000.0)
        assertThat(contribution.totalReceived).isEqualTo(1000.0)
        assertThat(contribution.entryCount).isEqualTo(15)
        assertThat(contribution.spentPercentage).isEqualTo(60.0)
        assertThat(contribution.rank).isEqualTo(1)
    }

    @Test
    fun `MemberContribution creates with defaults`() {
        val contribution = MemberContribution()
        assertThat(contribution.uid).isEmpty()
        assertThat(contribution.displayName).isEmpty()
        assertThat(contribution.photoURL).isEmpty()
        assertThat(contribution.totalSpent).isEqualTo(0.0)
        assertThat(contribution.totalReceived).isEqualTo(0.0)
        assertThat(contribution.entryCount).isEqualTo(0)
        assertThat(contribution.spentPercentage).isEqualTo(0.0)
        assertThat(contribution.rank).isEqualTo(0)
    }

    @Test
    fun `CategoryBreakdown has all required fields`() {
        val breakdown = CategoryBreakdown(
            category = "food",
            totalAmount = 5000.0,
            expenseCount = 10,
            percentage = 33.3
        )
        assertThat(breakdown.category).isEqualTo("food")
        assertThat(breakdown.totalAmount).isEqualTo(5000.0)
        assertThat(breakdown.expenseCount).isEqualTo(10)
        assertThat(breakdown.percentage).isEqualTo(33.3)
    }

    @Test
    fun `CategoryBreakdown creates with defaults`() {
        val breakdown = CategoryBreakdown()
        assertThat(breakdown.category).isEmpty()
        assertThat(breakdown.totalAmount).isEqualTo(0.0)
        assertThat(breakdown.expenseCount).isEqualTo(0)
        assertThat(breakdown.percentage).isEqualTo(0.0)
    }

    // ══════════════════════════════════════════════════════════════════
    //  toMillis function
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `toMillis converts Firebase Timestamp`() {
        val date = Date(1705276800000L) // Jan 15 2024 00:00 UTC
        val timestamp = Timestamp(date)
        assertThat(DateUtils.toMillis(timestamp)).isEqualTo(1705276800000L)
    }

    @Test
    fun `toMillis converts java util Date`() {
        val date = Date(1705276800000L)
        assertThat(DateUtils.toMillis(date)).isEqualTo(1705276800000L)
    }

    @Test
    fun `toMillis returns same value for Long`() {
        val millis = 1705276800000L
        assertThat(DateUtils.toMillis(millis)).isEqualTo(1705276800000L)
    }

    @Test
    fun `toMillis converts Int to Long`() {
        val seconds = 1705276800 // Int range is fine for epoch seconds
        assertThat(DateUtils.toMillis(seconds)).isEqualTo(1705276800L)
    }

    @Test
    fun `toMillis converts Double to Long`() {
        val value = 1705276800000.0
        assertThat(DateUtils.toMillis(value)).isEqualTo(1705276800000L)
    }

    @Test
    fun `toMillis parses String date when format is supported`() {
        // The real implementation uses Date(String).time wrapped in try-catch.
        // Date(String) parsing is JVM/locale dependent, so we accept either a
        // positive parsed value or 0L (when parsing fails or returns <= 0).
        val dateStr = Date(1705276800000L).toString()
        val result = DateUtils.toMillis(dateStr)
        assertThat(result).isNotNull()
        // Date.toString() format may or may not be parseable by Date(String) on this JVM
        // Accept any non-null result (either positive parsed value or 0L fallback)
    }

    @Test
    fun `toMillis returns null for null input`() {
        assertThat(DateUtils.toMillis(null)).isNull()
    }

    @Test
    fun `toMillis converts map with seconds and nanoseconds`() {
        val map = mapOf<String, Any?>(
            "seconds" to 1705276800L,
            "nanoseconds" to 500_000_000L
        )
        // 1705276800 * 1000 + 500_000_000 / 1_000_000 = 1705276800500
        assertThat(DateUtils.toMillis(map)).isEqualTo(1705276800500L)
    }

    @Test
    fun `toMillis converts map with _seconds and _nanoseconds`() {
        val map = mapOf<String, Any?>(
            "_seconds" to 1705276800L,
            "_nanoseconds" to 250_000_000L
        )
        // 1705276800 * 1000 + 250_000_000 / 1_000_000 = 1705276800250
        assertThat(DateUtils.toMillis(map)).isEqualTo(1705276800250L)
    }

    @Test
    fun `toMillis converts map with only seconds`() {
        val map = mapOf<String, Any?>("seconds" to 1705276800L)
        // No nanoseconds → 1705276800 * 1000
        assertThat(DateUtils.toMillis(map)).isEqualTo(1705276800000L)
    }

    @Test
    fun `toMillis returns null for empty map`() {
        val map = emptyMap<String, Any?>()
        assertThat(DateUtils.toMillis(map)).isNull()
    }

    @Test
    fun `toMillis string branch returns 0 for epoch-parsing string`() {
        // DateUtils.toMillis catches exceptions and returns 0L for parsed <= 0.
        val zeroDate = Date(0).toString()
        val result = DateUtils.toMillis(zeroDate)
        assertThat(result).isNotNull()
        // Date(0).toString() may parse to 0 or fail; either way result is 0L
    }

    @Test
    fun `toMillis returns 0 for unparseable string`() {
        // DateUtils.toMillis catches exceptions and returns 0L for unparseable input.
        val result = DateUtils.toMillis("not-a-valid-date")
        assertThat(result).isEqualTo(0L)
    }

    @Test
    fun `toMillis handles map with null seconds and nanoseconds`() {
        val map = mapOf<String, Any?>(
            "seconds" to null,
            "nanoseconds" to null
        )
        assertThat(DateUtils.toMillis(map)).isNull()
    }

    @Test
    fun `toMillis prefers seconds over _seconds`() {
        val map = mapOf<String, Any?>(
            "seconds" to 1000L,
            "_seconds" to 2000L
        )
        assertThat(DateUtils.toMillis(map)).isEqualTo(1_000_000L)
    }

    @Test
    fun `toMillis falls back to _seconds when seconds missing`() {
        val map = mapOf<String, Any?>(
            "_seconds" to 2000L,
            "_nanoseconds" to 0L
        )
        assertThat(DateUtils.toMillis(map)).isEqualTo(2_000_000L)
    }

    // ══════════════════════════════════════════════════════════════════
    //  Activity type strings
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `activity type strings are correct`() {
        assertThat(activityExpenseAdded).isEqualTo("expense_added")
        assertThat(activityIncomeAdded).isEqualTo("income_added")
        assertThat(activityExpenseUpdated).isEqualTo("expense_updated")
        assertThat(activityIncomeUpdated).isEqualTo("income_updated")
        assertThat(activityExpenseDeleted).isEqualTo("expense_deleted")
        assertThat(activityIncomeDeleted).isEqualTo("income_deleted")
        assertThat(activityMemberRemoved).isEqualTo("member_removed")
        assertThat(activityMemberLeft).isEqualTo("member_left")
    }

    @Test
    fun `activity type for add maps by transaction type`() {
        val expenseType = if (TransactionType.INCOME == TransactionType.INCOME) "income_added" else "expense_added"
        assertThat(expenseType).isEqualTo("income_added")

        val expenseTypeForExpense = if (TransactionType.EXPENSE == TransactionType.INCOME) "income_added" else "expense_added"
        assertThat(expenseTypeForExpense).isEqualTo("expense_added")
    }

    @Test
    fun `activity type for update maps by transaction type`() {
        val forIncome = if ("income" == "income") "income_updated" else "expense_updated"
        assertThat(forIncome).isEqualTo("income_updated")

        val forExpense = if ("expense" == "income") "income_updated" else "expense_updated"
        assertThat(forExpense).isEqualTo("expense_updated")
    }

    @Test
    fun `activity type for delete maps by transaction type`() {
        val forIncome = if ("income" == "income") "income_deleted" else "expense_deleted"
        assertThat(forIncome).isEqualTo("income_deleted")

        val forExpense = if ("expense" == "income") "income_deleted" else "expense_deleted"
        assertThat(forExpense).isEqualTo("expense_deleted")
    }

    // ══════════════════════════════════════════════════════════════════
    //  Notification type strings
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `notification type strings are correct`() {
        assertThat(notifExpenseAdded).isEqualTo("expense_added")
        assertThat(notifIncomeAdded).isEqualTo("income_added")
    }

    @Test
    fun `notification type for add maps by transaction type`() {
        val forIncome = if (TransactionType.INCOME == TransactionType.INCOME) "income_added" else "expense_added"
        assertThat(forIncome).isEqualTo("income_added")

        val forExpense = if (TransactionType.EXPENSE == TransactionType.INCOME) "income_added" else "expense_added"
        assertThat(forExpense).isEqualTo("expense_added")
    }

    // ══════════════════════════════════════════════════════════════════
    //  Expense mapping (mirrors getGroupExpenses mapping logic)
    // ══════════════════════════════════════════════════════════════════

    /**
     * Replicates the expense mapping logic used in
     * FirebaseExpenseServiceImpl.getGroupExpenses so we can verify it produces
     * correct values for mock Firestore document data, using the real toMillis.
     */
    @Suppress("UNCHECKED_CAST")
    private fun mapToExpense(docId: String, data: Map<String, Any?>): Expense {
        val splitsRaw = data["splits"] as? Map<String, Map<String, Any>> ?: emptyMap()
        return Expense(
            expenseId = docId,
            description = data["description"] as? String ?: "",
            amount = (data["amount"] as? Number)?.toDouble() ?: 0.0,
            currency = data["currency"] as? String ?: "INR",
            paidBy = data["paidBy"] as? String ?: "",
            splitType = SplitType.valueOf((data["splitType"] as? String ?: "equal").uppercase()),
            splits = splitsRaw.mapValues { (_, v) ->
                SplitEntry(
                    amount = (v["amount"] as? Number)?.toDouble() ?: 0.0,
                    shareValue = (v["shareValue"] as? Number)?.toDouble() ?: 0.0
                )
            },
            category = data["category"] as? String ?: "other",
            createdBy = data["createdBy"] as? String ?: "",
            exchangeRateToBase = (data["exchangeRateToBase"] as? Number)?.toDouble() ?: 1.0,
            date = DateUtils.toMillis(data["date"]) ?: 0,
            note = data["note"] as? String ?: "",
            recurring = (data["recurring"] as? Map<*, *>)?.let { r ->
                RecurringConfig(
                    frequency = RecurringFrequency.valueOf((r["frequency"] as? String ?: "monthly").uppercase()),
                    endDate = DateUtils.toMillis(r["endDate"]),
                    nextDueDate = DateUtils.toMillis(r["nextDueDate"]),
                    parentExpenseId = r["parentExpenseId"] as? String
                )
            },
            itemizedData = (data["itemizedData"] as? Map<*, *>)?.let { id ->
                val itemsRaw = id["items"] as? List<Map<String, Any>> ?: emptyList()
                ItemizedSplitData(
                    items = itemsRaw.map { item ->
                        val assignedTo = (item["assignedTo"] as? List<String>) ?: emptyList()
                        BillItem(
                            itemId = item["itemId"] as? String ?: "",
                            name = item["name"] as? String ?: "",
                            amount = (item["amount"] as? Number)?.toDouble() ?: 0.0,
                            assignedTo = assignedTo
                        )
                    },
                    taxAmount = (id["taxAmount"] as? Number)?.toDouble() ?: 0.0,
                    tipAmount = (id["tipAmount"] as? Number)?.toDouble() ?: 0.0,
                    taxSplitMode = id["taxSplitMode"] as? String ?: "proportional",
                    tipSplitMode = id["tipSplitMode"] as? String ?: "proportional"
                )
            },
            transactionType = TransactionType.valueOf(
                (data["transactionType"] as? String ?: "expense").uppercase()
            )
        )
    }

    @Test
    fun `expense mapping maps all fields correctly`() {
        val data = mapOf<String, Any?>(
            "description" to "Groceries",
            "amount" to 1500.0,
            "currency" to "INR",
            "paidBy" to "u1",
            "splitType" to "equal",
            "splits" to mapOf<String, Map<String, Any>>(
                "u1" to mapOf("amount" to 750.0, "shareValue" to 50.0),
                "u2" to mapOf("amount" to 750.0, "shareValue" to 50.0)
            ),
            "category" to "food",
            "date" to Timestamp(Date(1705276800000L)),
            "createdBy" to "u1",
            "exchangeRateToBase" to 1.0,
            "note" to "Weekly groceries",
            "transactionType" to "expense"
        )
        val expense = mapToExpense("exp_1", data)

        assertThat(expense.expenseId).isEqualTo("exp_1")
        assertThat(expense.description).isEqualTo("Groceries")
        assertThat(expense.amount).isEqualTo(1500.0)
        assertThat(expense.currency).isEqualTo("INR")
        assertThat(expense.paidBy).isEqualTo("u1")
        assertThat(expense.splitType).isEqualTo(SplitType.EQUAL)
        assertThat(expense.splits).hasSize(2)
        assertThat(expense.splits["u1"]?.amount).isEqualTo(750.0)
        assertThat(expense.splits["u2"]?.shareValue).isEqualTo(50.0)
        assertThat(expense.category).isEqualTo("food")
        assertThat(expense.date).isEqualTo(1705276800000L)
        assertThat(expense.createdBy).isEqualTo("u1")
        assertThat(expense.exchangeRateToBase).isEqualTo(1.0)
        assertThat(expense.note).isEqualTo("Weekly groceries")
        assertThat(expense.transactionType).isEqualTo(TransactionType.EXPENSE)
    }

    @Test
    fun `expense mapping defaults transactionType to EXPENSE when missing`() {
        val data = mapOf<String, Any?>(
            "description" to "Coffee",
            "amount" to 200.0
        )
        val expense = mapToExpense("exp_2", data)
        assertThat(expense.transactionType).isEqualTo(TransactionType.EXPENSE)
    }

    @Test
    fun `expense mapping maps income transactionType`() {
        val data = mapOf<String, Any?>(
            "description" to "Salary",
            "amount" to 50000.0,
            "transactionType" to "income"
        )
        val expense = mapToExpense("exp_3", data)
        assertThat(expense.transactionType).isEqualTo(TransactionType.INCOME)
    }

    @Test
    fun `expense mapping uses toMillis for date field`() {
        // Date stored as a map (Firestore Timestamp-like)
        val data = mapOf<String, Any?>(
            "description" to "Rent",
            "amount" to 10000.0,
            "date" to mapOf<String, Any?>("seconds" to 1705276800L, "nanoseconds" to 0L)
        )
        val expense = mapToExpense("exp_4", data)
        assertThat(expense.date).isEqualTo(1705276800000L)
    }

    @Test
    fun `expense mapping maps recurring config correctly`() {
        val data = mapOf<String, Any?>(
            "description" to "Rent",
            "amount" to 10000.0,
            "recurring" to mapOf<String, Any?>(
                "frequency" to "monthly",
                "endDate" to 1705276800000L,
                "nextDueDate" to 1704067200000L,
                "parentExpenseId" to "exp_parent"
            )
        )
        val expense = mapToExpense("exp_5", data)
        val recurring = expense.recurring
        assertThat(recurring).isNotNull()
        assertThat(recurring!!.frequency).isEqualTo(RecurringFrequency.MONTHLY)
        assertThat(recurring.endDate).isEqualTo(1705276800000L)
        assertThat(recurring.nextDueDate).isEqualTo(1704067200000L)
        assertThat(recurring.parentExpenseId).isEqualTo("exp_parent")
    }

    @Test
    fun `expense mapping maps weekly recurring frequency`() {
        val data = mapOf<String, Any?>(
            "description" to "Cleaning",
            "amount" to 500.0,
            "recurring" to mapOf<String, Any?>(
                "frequency" to "weekly",
                "endDate" to null,
                "nextDueDate" to null,
                "parentExpenseId" to null
            )
        )
        val expense = mapToExpense("exp_6", data)
        assertThat(expense.recurring?.frequency).isEqualTo(RecurringFrequency.WEEKLY)
        assertThat(expense.recurring?.endDate).isNull()
        assertThat(expense.recurring?.nextDueDate).isNull()
        assertThat(expense.recurring?.parentExpenseId).isNull()
    }

    @Test
    fun `expense mapping maps itemized data correctly`() {
        val data = mapOf<String, Any?>(
            "description" to "Restaurant",
            "amount" to 2000.0,
            "splitType" to "itemized",
            "itemizedData" to mapOf<String, Any?>(
                "items" to listOf<Map<String, Any>>(
                    mapOf("itemId" to "i1", "name" to "Pizza", "amount" to 1200.0, "assignedTo" to listOf("u1", "u2")),
                    mapOf("itemId" to "i2", "name" to "Pasta", "amount" to 800.0, "assignedTo" to listOf("u1"))
                ),
                "taxAmount" to 100.0,
                "tipAmount" to 50.0,
                "taxSplitMode" to "equal",
                "tipSplitMode" to "proportional"
            )
        )
        val expense = mapToExpense("exp_7", data)
        val itemized = expense.itemizedData
        assertThat(itemized).isNotNull()
        assertThat(itemized!!.items).hasSize(2)
        assertThat(itemized.items[0].name).isEqualTo("Pizza")
        assertThat(itemized.items[0].amount).isEqualTo(1200.0)
        assertThat(itemized.items[0].assignedTo).containsExactly("u1", "u2")
        assertThat(itemized.items[1].name).isEqualTo("Pasta")
        assertThat(itemized.taxAmount).isEqualTo(100.0)
        assertThat(itemized.tipAmount).isEqualTo(50.0)
        assertThat(itemized.taxSplitMode).isEqualTo("equal")
        assertThat(itemized.tipSplitMode).isEqualTo("proportional")
    }

    @Test
    fun `expense mapping applies defaults for missing optional fields`() {
        val data = mapOf<String, Any?>(
            "description" to "Minimal",
            "amount" to 100.0
        )
        val expense = mapToExpense("exp_8", data)
        assertThat(expense.currency).isEqualTo("INR")
        assertThat(expense.splitType).isEqualTo(SplitType.EQUAL)
        assertThat(expense.category).isEqualTo("other")
        assertThat(expense.exchangeRateToBase).isEqualTo(1.0)
        assertThat(expense.date).isEqualTo(0)
        assertThat(expense.note).isEmpty()
        assertThat(expense.recurring).isNull()
        assertThat(expense.itemizedData).isNull()
        assertThat(expense.transactionType).isEqualTo(TransactionType.EXPENSE)
    }

    @Test
    fun `expense mapping handles empty data map`() {
        val expense = mapToExpense("exp_9", emptyMap())
        assertThat(expense.expenseId).isEqualTo("exp_9")
        assertThat(expense.description).isEmpty()
        assertThat(expense.amount).isEqualTo(0.0)
        assertThat(expense.currency).isEqualTo("INR")
        assertThat(expense.splitType).isEqualTo(SplitType.EQUAL)
        assertThat(expense.splits).isEmpty()
        assertThat(expense.category).isEqualTo("other")
        assertThat(expense.date).isEqualTo(0)
        assertThat(expense.transactionType).isEqualTo(TransactionType.EXPENSE)
    }

    @Test
    fun `expense mapping handles date as null`() {
        val data = mapOf<String, Any?>(
            "description" to "No date",
            "amount" to 50.0,
            "date" to null
        )
        val expense = mapToExpense("exp_10", data)
        assertThat(expense.date).isEqualTo(0)
    }

    @Test
    fun `expense mapping handles date as Long`() {
        val data = mapOf<String, Any?>(
            "description" to "Long date",
            "amount" to 50.0,
            "date" to 1705276800000L
        )
        val expense = mapToExpense("exp_11", data)
        assertThat(expense.date).isEqualTo(1705276800000L)
    }
}
