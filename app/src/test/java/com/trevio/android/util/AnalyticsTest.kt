package com.trevio.android.util

import com.google.common.truth.Truth.assertThat
import com.trevio.android.domain.model.Expense
import com.trevio.android.domain.model.Member
import com.trevio.android.domain.model.SplitEntry
import com.trevio.android.domain.model.SplitType
import com.trevio.android.domain.model.TopGroupSpending
import org.junit.Test

class AnalyticsTest {

    private fun makeExpense(
        id: String,
        amount: Double,
        category: String,
        paidBy: String,
        splits: Map<String, SplitEntry> = emptyMap(),
        date: Long = System.currentTimeMillis()
    ): Expense = Expense(
        expenseId = id,
        description = "Expense $id",
        amount = amount,
        paidBy = paidBy,
        splitType = SplitType.EQUAL,
        splits = splits,
        category = category,
        date = date
    )

    private fun makeMember(uid: String, name: String, balance: Double = 0.0): Member = Member(
        uid = uid,
        displayName = name,
        balance = balance
    )

    @Test
    fun `computeCategoryBreakdown returns empty for no expenses`() {
        val result = computeCategoryBreakdown(emptyList())
        assertThat(result).isEmpty()
    }

    @Test
    fun `computeCategoryBreakdown groups by category`() {
        val expenses = listOf(
            makeExpense("1", 100.0, "food", "u1"),
            makeExpense("2", 200.0, "food", "u1"),
            makeExpense("3", 300.0, "transport", "u2")
        )
        val result = computeCategoryBreakdown(expenses)
        assertThat(result).hasSize(2)
        assertThat(result[0].category).isEqualTo("food")
        assertThat(result[0].totalAmount).isEqualTo(300.0)
        assertThat(result[0].expenseCount).isEqualTo(2)
        assertThat(result[0].percentage).isWithin(0.1).of(50.0)
    }

    @Test
    fun `computeCategoryBreakdown sorts by total descending`() {
        val expenses = listOf(
            makeExpense("1", 100.0, "food", "u1"),
            makeExpense("2", 500.0, "transport", "u1"),
            makeExpense("3", 50.0, "other", "u1")
        )
        val result = computeCategoryBreakdown(expenses)
        assertThat(result[0].category).isEqualTo("transport")
        assertThat(result[1].category).isEqualTo("food")
        assertThat(result[2].category).isEqualTo("other")
    }

    @Test
    fun `computeMonthlyTrends returns 6 months by default`() {
        val result = computeMonthlyTrends(emptyList())
        assertThat(result).hasSize(6)
    }

    @Test
    fun `computeMonthlyTrends all zero for no expenses`() {
        val result = computeMonthlyTrends(emptyList())
        result.forEach {
            assertThat(it.totalAmount).isEqualTo(0.0)
            assertThat(it.expenseCount).isEqualTo(0)
        }
    }

    @Test
    fun `computeMonthlyTrends assigns expenses to correct month`() {
        val cal = java.util.Calendar.getInstance()
        val thisMonth = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.DAY_OF_MONTH, 15)
        }.timeInMillis
        val expenses = listOf(
            makeExpense("1", 100.0, "food", "u1", date = thisMonth),
            makeExpense("2", 200.0, "food", "u1", date = thisMonth)
        )
        val result = computeMonthlyTrends(expenses)
        val lastTrend = result.last()
        assertThat(lastTrend.totalAmount).isEqualTo(300.0)
        assertThat(lastTrend.expenseCount).isEqualTo(2)
    }

    @Test
    fun `computeMemberSpending tracks totalPaid`() {
        val members = listOf(makeMember("u1", "Alice"), makeMember("u2", "Bob"))
        val expenses = listOf(
            makeExpense("1", 100.0, "food", "u1", mapOf("u1" to SplitEntry(50.0), "u2" to SplitEntry(50.0))),
            makeExpense("2", 200.0, "food", "u2", mapOf("u1" to SplitEntry(100.0), "u2" to SplitEntry(100.0)))
        )
        val result = computeMemberSpending(expenses, members)
        val alice = result.find { it.uid == "u1" }!!
        val bob = result.find { it.uid == "u2" }!!
        assertThat(alice.totalPaid).isEqualTo(100.0)
        assertThat(alice.expenseCount).isEqualTo(1)
        assertThat(bob.totalPaid).isEqualTo(200.0)
        assertThat(bob.expenseCount).isEqualTo(1)
    }

    @Test
    fun `computeMemberSpending tracks totalShare`() {
        val members = listOf(makeMember("u1", "Alice"), makeMember("u2", "Bob"))
        val expenses = listOf(
            makeExpense("1", 100.0, "food", "u1", mapOf("u1" to SplitEntry(60.0), "u2" to SplitEntry(40.0)))
        )
        val result = computeMemberSpending(expenses, members)
        val alice = result.find { it.uid == "u1" }!!
        val bob = result.find { it.uid == "u2" }!!
        assertThat(alice.totalShare).isEqualTo(60.0)
        assertThat(bob.totalShare).isEqualTo(40.0)
    }

    @Test
    fun `computeMemberSpending sorts by totalPaid descending`() {
        val members = listOf(makeMember("u1", "Alice"), makeMember("u2", "Bob"))
        val expenses = listOf(
            makeExpense("1", 100.0, "food", "u1"),
            makeExpense("2", 500.0, "food", "u2")
        )
        val result = computeMemberSpending(expenses, members)
        assertThat(result[0].uid).isEqualTo("u2")
        assertThat(result[1].uid).isEqualTo("u1")
    }

    @Test
    fun `computeGroupAnalytics returns correct totals`() {
        val members = listOf(makeMember("u1", "Alice"), makeMember("u2", "Bob"))
        val expenses = listOf(
            makeExpense("1", 100.0, "food", "u1", mapOf("u1" to SplitEntry(50.0), "u2" to SplitEntry(50.0))),
            makeExpense("2", 200.0, "transport", "u2", mapOf("u1" to SplitEntry(100.0), "u2" to SplitEntry(100.0)))
        )
        val result = computeGroupAnalytics("g1", "Test Group", expenses, members)
        assertThat(result.groupId).isEqualTo("g1")
        assertThat(result.groupName).isEqualTo("Test Group")
        assertThat(result.totalExpenses).isEqualTo(300.0)
        assertThat(result.expenseCount).isEqualTo(2)
        assertThat(result.avgExpenseAmount).isEqualTo(150.0)
    }

    @Test
    fun `computeGroupAnalytics handles empty expenses`() {
        val result = computeGroupAnalytics("g1", "Empty", emptyList(), emptyList())
        assertThat(result.totalExpenses).isEqualTo(0.0)
        assertThat(result.expenseCount).isEqualTo(0)
        assertThat(result.avgExpenseAmount).isEqualTo(0.0)
        assertThat(result.highestExpense).isNull()
        assertThat(result.categoryBreakdown).isEmpty()
    }

    @Test
    fun `computeGroupAnalytics finds highest expense`() {
        val expenses = listOf(
            makeExpense("1", 100.0, "food", "u1"),
            makeExpense("2", 500.0, "transport", "u1"),
            makeExpense("3", 50.0, "food", "u1")
        )
        val result = computeGroupAnalytics("g1", "Test", expenses, emptyList())
        assertThat(result.highestExpense).isNotNull()
        assertThat(result.highestExpense!!.amount).isEqualTo(500.0)
        assertThat(result.highestExpense!!.description).isEqualTo("Expense 2")
    }

    @Test
    fun `computeGroupAnalytics computes recentActivityRate`() {
        val now = System.currentTimeMillis()
        val oldDate = now - 60L * 24 * 60 * 60 * 1000
        val recentDate = now - 5L * 24 * 60 * 60 * 1000
        val expenses = listOf(
            makeExpense("1", 100.0, "food", "u1", date = oldDate),
            makeExpense("2", 200.0, "food", "u1", date = recentDate),
            makeExpense("3", 300.0, "food", "u1", date = recentDate)
        )
        val result = computeGroupAnalytics("g1", "Test", expenses, emptyList())
        assertThat(result.recentActivityRate).isWithin(0.2).of(66.67)
    }

    @Test
    fun `computeUserAnalytics aggregates across groups`() {
        val groups = listOf(
            Triple("g1", "Group 1", 50.0),
            Triple("g2", "Group 2", -30.0)
        )
        val allExpenses = mapOf(
            "g1" to listOf(makeExpense("1", 300.0, "food", "u1")),
            "g2" to listOf(makeExpense("2", 200.0, "transport", "u2"))
        )
        val result = computeUserAnalytics(groups, allExpenses, "u1")
        assertThat(result.totalSpent).isEqualTo(500.0)
        assertThat(result.totalPaid).isEqualTo(300.0)
        assertThat(result.totalOwed).isEqualTo(50.0)
        assertThat(result.totalOwing).isEqualTo(30.0)
        assertThat(result.netBalance).isEqualTo(20.0)
        assertThat(result.groupCount).isEqualTo(2)
        assertThat(result.expenseCount).isEqualTo(2)
    }

    @Test
    fun `computeUserAnalytics returns top 5 groups sorted by spending`() {
        val groups = (0..6).map { i ->
            Triple("g$i", "Group $i", 0.0)
        }
        val allExpenses = groups.associate { (gid, _, _) ->
            val idx = gid.removePrefix("g").toInt()
            gid to listOf(makeExpense("e$idx", (idx + 1) * 100.0, "food", "u1"))
        }
        val result = computeUserAnalytics(groups, allExpenses, "u1")
        assertThat(result.topGroups).hasSize(5)
        assertThat(result.topGroups[0].totalSpent).isEqualTo(700.0)
        assertThat(result.topGroups[4].totalSpent).isEqualTo(300.0)
    }

    @Test
    fun `computeUserAnalytics handles empty groups`() {
        val result = computeUserAnalytics(emptyList(), emptyMap(), "u1")
        assertThat(result.totalSpent).isEqualTo(0.0)
        assertThat(result.groupCount).isEqualTo(0)
        assertThat(result.expenseCount).isEqualTo(0)
        assertThat(result.topGroups).isEmpty()
    }

    // ─── Edge cases ──────────────────────────────────────────────

    @Test
    fun `computeCategoryBreakdown handles empty category as other`() {
        val expenses = listOf(makeExpense("1", 100.0, "", "u1"))
        val result = computeCategoryBreakdown(expenses)
        assertThat(result).hasSize(1)
        assertThat(result[0].category).isEqualTo("other")
    }

    @Test
    fun `computeMemberSpending handles expense paid by non-member`() {
        val members = listOf(makeMember("u1", "Alice"))
        val expenses = listOf(makeExpense("1", 100.0, "food", "u2", mapOf("u1" to SplitEntry(100.0))))
        val result = computeMemberSpending(expenses, members)
        val alice = result.find { it.uid == "u1" }!!
        assertThat(alice.totalPaid).isEqualTo(0.0)
        assertThat(alice.totalShare).isEqualTo(100.0)
    }

    @Test
    fun `computeMonthlyTrends ignores expenses older than 6 months`() {
        val oldCal = java.util.Calendar.getInstance().apply { add(java.util.Calendar.MONTH, -8) }
        val expenses = listOf(makeExpense("1", 500.0, "food", "u1", date = oldCal.timeInMillis))
        val result = computeMonthlyTrends(expenses)
        val totalAcrossMonths = result.sumOf { it.totalAmount }
        assertThat(totalAcrossMonths).isEqualTo(0.0)
    }

    @Test
    fun `computeCategoryBreakdown all same category returns 100 percent`() {
        val expenses = listOf(
            makeExpense("1", 100.0, "food", "u1"),
            makeExpense("2", 200.0, "food", "u1")
        )
        val result = computeCategoryBreakdown(expenses)
        assertThat(result).hasSize(1)
        assertThat(result[0].percentage).isEqualTo(100.0)
    }

    @Test
    fun `computeGroupAnalytics with single expense`() {
        val expenses = listOf(makeExpense("1", 250.0, "food", "u1"))
        val result = computeGroupAnalytics("g1", "Solo", expenses, emptyList())
        assertThat(result.expenseCount).isEqualTo(1)
        assertThat(result.avgExpenseAmount).isEqualTo(250.0)
        assertThat(result.highestExpense!!.amount).isEqualTo(250.0)
        assertThat(result.recentActivityRate).isEqualTo(100.0)
    }

    @Test
    fun `computeMemberSpending preserves member balance from input`() {
        val members = listOf(makeMember("u1", "Alice", balance = 75.5))
        val result = computeMemberSpending(emptyList(), members)
        assertThat(result[0].netBalance).isEqualTo(75.5)
    }
}
