package com.trevio.android.data.remote

import com.google.firebase.functions.FirebaseFunctions
import com.trevio.android.domain.model.Expense
import com.trevio.android.domain.model.SplitEntry
import com.trevio.android.domain.model.SplitType
import com.trevio.android.domain.repository.ExpenseService
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseExpenseServiceImpl @Inject constructor(
    private val functions: FirebaseFunctions
) : ExpenseService {

    override suspend fun addExpense(
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
    ): Result<String> {
        return try {
            val data = mutableMapOf<String, Any>(
                "groupId" to groupId,
                "description" to description,
                "amount" to amount,
                "currency" to currency,
                "paidBy" to paidBy,
                "splitType" to splitType.name.lowercase(),
                "memberUids" to memberUids,
                "category" to category,
                "isRecurring" to isRecurring
            )

            if (splits.isNotEmpty()) {
                data["splits"] = splits.mapValues { (_, v) ->
                    mapOf("amount" to v.amount, "shareValue" to v.shareValue)
                }
            }

            if (isRecurring && recurringFrequency != null) {
                data["recurringConfig"] = mapOf(
                    "frequency" to recurringFrequency,
                    "startDate" to date.toString()
                )
            }

            val result = functions.getHttpsCallable("addExpense").call(data).await()
            val res = result.getData() as Map<*, *>
            Result.success(res["expenseId"] as String)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateExpense(
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
    ): Result<Unit> {
        return try {
            val data = mutableMapOf<String, Any>(
                "groupId" to groupId,
                "expenseId" to expenseId,
                "description" to description,
                "amount" to amount,
                "currency" to currency,
                "paidBy" to paidBy,
                "splitType" to splitType.name.lowercase(),
                "memberUids" to memberUids,
                "category" to category
            )

            if (splits.isNotEmpty()) {
                data["splits"] = splits.mapValues { (_, v) ->
                    mapOf("amount" to v.amount, "shareValue" to v.shareValue)
                }
            }

            functions.getHttpsCallable("updateExpense").call(data).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteExpense(groupId: String, expenseId: String): Result<Unit> {
        return try {
            functions.getHttpsCallable("deleteExpense")
                .call(mapOf("groupId" to groupId, "expenseId" to expenseId)).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getGroupExpenses(groupId: String, pageSize: Int, lastExpenseId: String?): Result<List<Expense>> {
        return try {
            val data = mutableMapOf<String, Any>(
                "groupId" to groupId,
                "pageSize" to pageSize
            )
            if (lastExpenseId != null) data["lastExpenseId"] = lastExpenseId

            val result = functions.getHttpsCallable("getGroupExpenses").call(data).await()
            val res = result.getData() as Map<*, *>
            @Suppress("UNCHECKED_CAST")
            val expenses = res["expenses"] as List<Map<*, *>>
            Result.success(expenses.map { e ->
                @Suppress("UNCHECKED_CAST")
                val splitsRaw = e["splits"] as? Map<String, Map<*, *>> ?: emptyMap()
                Expense(
                    expenseId = e["expenseId"] as String,
                    description = e["description"] as String,
                    amount = (e["amount"] as? Double ?: 0.0),
                    currency = e["currency"] as? String ?: "INR",
                    paidBy = e["paidBy"] as String,
                    splitType = SplitType.valueOf((e["splitType"] as? String ?: "equal").uppercase()),
                    splits = splitsRaw.mapValues { (_, v) ->
                        SplitEntry(
                            amount = (v["amount"] as? Double ?: 0.0),
                            shareValue = (v["shareValue"] as? Double ?: 0.0)
                        )
                    },
                    category = e["category"] as? String ?: "other",
                    isRecurring = e["isRecurring"] as? Boolean ?: false,
                    createdBy = e["createdBy"] as? String ?: ""
                )
            })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
