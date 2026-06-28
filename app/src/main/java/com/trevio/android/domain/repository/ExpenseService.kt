package com.trevio.android.domain.repository

import com.trevio.android.domain.model.Expense
import com.trevio.android.domain.model.SplitEntry
import com.trevio.android.domain.model.SplitType

interface ExpenseService {
    suspend fun addExpense(
        groupId: String,
        description: String,
        amount: Double,
        currency: String,
        paidBy: String,
        splitType: SplitType,
        splits: Map<String, SplitEntry>,
        memberUids: List<String>,
        category: String,
        date: Long,
        isRecurring: Boolean,
        recurringFrequency: String?
    ): Result<String>

    suspend fun updateExpense(
        groupId: String,
        expenseId: String,
        description: String,
        amount: Double,
        currency: String,
        paidBy: String,
        splitType: SplitType,
        splits: Map<String, SplitEntry>,
        memberUids: List<String>,
        category: String,
        date: Long
    ): Result<Unit>

    suspend fun deleteExpense(groupId: String, expenseId: String): Result<Unit>
    suspend fun getGroupExpenses(groupId: String, pageSize: Int, lastExpenseId: String?): Result<List<Expense>>
}
