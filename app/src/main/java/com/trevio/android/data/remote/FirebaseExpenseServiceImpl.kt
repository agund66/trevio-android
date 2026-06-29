package com.trevio.android.data.remote

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.trevio.android.domain.model.Expense
import com.trevio.android.domain.model.SplitEntry
import com.trevio.android.domain.model.SplitType
import com.trevio.android.domain.repository.ExpenseService
import com.trevio.android.util.Calculations
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseExpenseServiceImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
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
            val uid = auth.currentUser?.uid ?: return Result.failure(Exception("User not authenticated"))
            if (groupId.isBlank() || description.isBlank() || amount <= 0.0 || paidBy.isBlank()) {
                return Result.failure(Exception("Missing required fields"))
            }

            val groupRef = firestore.collection("groups").document(groupId)
            val groupDoc = groupRef.get().await()
            if (!groupDoc.exists()) return Result.failure(Exception("Group not found"))

            val memberDoc = groupRef.collection("members").document(uid).get().await()
            if (!memberDoc.exists()) return Result.failure(Exception("You are not a member of this group"))

            val calculatedSplits = Calculations.calculateSplits(amount, splitType, memberUids, splits)
            val now = System.currentTimeMillis()
            val expenseRef = groupRef.collection("expenses").document()

            val expenseData = mutableMapOf<String, Any>(
                "description" to description,
                "amount" to amount,
                "currency" to currency,
                "paidBy" to paidBy,
                "splitType" to splitType.name.lowercase(),
                "splits" to calculatedSplits.mapValues { (_, v) ->
                    mapOf("amount" to v.amount, "shareValue" to v.shareValue)
                },
                "category" to (category.ifEmpty { "other" }),
                "date" to now,
                "isRecurring" to isRecurring,
                "createdBy" to uid,
                "createdAt" to now
            )

            if (isRecurring && recurringFrequency != null) {
                expenseData["recurringConfig"] = mapOf(
                    "frequency" to recurringFrequency,
                    "startDate" to now,
                    "endDate" to null,
                    "lastTriggered" to now
                )
            }

            val batch = firestore.runBatch { b ->
                b.set(expenseRef, expenseData)
                b.set(groupRef.collection("activities").document(), mapOf(
                    "type" to "expense_added",
                    "description" to "Added expense: $description ($currency $amount)",
                    "userId" to uid,
                    "data" to mapOf("expenseId" to expenseRef.id, "amount" to amount, "description" to description),
                    "createdAt" to now
                ))
                b.update(groupRef, mapOf(
                    "totalExpenses" to ((groupDoc.data?.get("totalExpenses") as? Double ?: 0.0) + amount),
                    "updatedAt" to now
                ))
            }
            batch.await()

            recalculateBalances(groupId)

            Result.success(expenseRef.id)
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
            val uid = auth.currentUser?.uid ?: return Result.failure(Exception("User not authenticated"))
            if (groupId.isBlank() || expenseId.isBlank()) return Result.failure(Exception("Group ID and Expense ID are required"))

            val groupRef = firestore.collection("groups").document(groupId)
            val expenseRef = groupRef.collection("expenses").document(expenseId)
            val expenseDoc = expenseRef.get().await()
            if (!expenseDoc.exists()) return Result.failure(Exception("Expense not found"))

            val oldExpense = expenseDoc.data ?: return Result.failure(Exception("Invalid expense data"))
            val memberDoc = groupRef.collection("members").document(uid).get().await()
            if (!memberDoc.exists()) return Result.failure(Exception("You are not a member of this group"))

            val now = System.currentTimeMillis()
            val updateData = mutableMapOf<String, Any>("updatedAt" to now)

            if (description.isNotBlank()) updateData["description"] = description
            if (amount > 0) updateData["amount"] = amount
            if (currency.isNotBlank()) updateData["currency"] = currency
            if (paidBy.isNotBlank()) updateData["paidBy"] = paidBy
            if (category.isNotBlank()) updateData["category"] = category

            val effectiveAmount = if (amount > 0) amount else (oldExpense["amount"] as? Double ?: 0.0)

            if (memberUids.isNotEmpty()) {
                updateData["splitType"] = splitType.name.lowercase()
                val calculatedSplits = Calculations.calculateSplits(effectiveAmount, splitType, memberUids, splits)
                updateData["splits"] = calculatedSplits.mapValues { (_, v) ->
                    mapOf("amount" to v.amount, "shareValue" to v.shareValue)
                }
            }

            val oldAmount = oldExpense["amount"] as? Double ?: 0.0
            val amountDiff = effectiveAmount - oldAmount

            val currentTotalExpenses = if (amountDiff != 0.0) {
                (groupRef.get().await().data?.get("totalExpenses") as? Double ?: 0.0)
            } else 0.0

            val batch = firestore.runBatch { b ->
                b.update(expenseRef, updateData)
                if (amountDiff != 0.0) {
                    b.update(groupRef, mapOf(
                        "totalExpenses" to (currentTotalExpenses + amountDiff),
                        "updatedAt" to now
                    ))
                }
                b.set(groupRef.collection("activities").document(), mapOf(
                    "type" to "expense_updated",
                    "description" to "Updated expense: ${if (description.isNotBlank()) description else oldExpense["description"]}",
                    "userId" to uid,
                    "data" to mapOf("expenseId" to expenseId, "groupId" to groupId),
                    "createdAt" to now
                ))
            }
            batch.await()

            recalculateBalances(groupId)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteExpense(groupId: String, expenseId: String): Result<Unit> {
        return try {
            val uid = auth.currentUser?.uid ?: return Result.failure(Exception("User not authenticated"))
            if (groupId.isBlank() || expenseId.isBlank()) return Result.failure(Exception("Group ID and Expense ID are required"))

            val groupRef = firestore.collection("groups").document(groupId)
            val expenseRef = groupRef.collection("expenses").document(expenseId)
            val expenseDoc = expenseRef.get().await()
            if (!expenseDoc.exists()) return Result.failure(Exception("Expense not found"))

            val expenseData = expenseDoc.data ?: return Result.failure(Exception("Invalid expense data"))
            val memberDoc = groupRef.collection("members").document(uid).get().await()
            if (!memberDoc.exists()) return Result.failure(Exception("You are not a member of this group"))

            val now = System.currentTimeMillis()
            val expenseAmount = expenseData["amount"] as? Double ?: 0.0
            val groupDoc = groupRef.get().await()

            val batch = firestore.runBatch { b ->
                b.delete(expenseRef)
                b.update(groupRef, mapOf(
                    "totalExpenses" to maxOf(0.0, (groupDoc.data?.get("totalExpenses") as? Double ?: 0.0) - expenseAmount),
                    "updatedAt" to now
                ))
                b.set(groupRef.collection("activities").document(), mapOf(
                    "type" to "expense_deleted",
                    "description" to "Deleted expense: ${expenseData["description"]}",
                    "userId" to uid,
                    "data" to mapOf("expenseId" to expenseId, "groupId" to groupId, "amount" to expenseAmount),
                    "createdAt" to now
                ))
            }
            batch.await()

            recalculateBalances(groupId)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getGroupExpenses(groupId: String, pageSize: Int, lastExpenseId: String?): Result<List<Expense>> {
        return try {
            val uid = auth.currentUser?.uid ?: return Result.failure(Exception("User not authenticated"))
            val groupRef = firestore.collection("groups").document(groupId)
            val memberDoc = groupRef.collection("members").document(uid).get().await()
            if (!memberDoc.exists()) return Result.failure(Exception("You are not a member of this group"))

            var query = groupRef.collection("expenses")
                .orderBy("date", Query.Direction.DESCENDING)
                .limit(pageSize.toLong())

            if (lastExpenseId != null) {
                val lastDoc = groupRef.collection("expenses").document(lastExpenseId).get().await()
                if (lastDoc.exists()) {
                    query = groupRef.collection("expenses")
                        .orderBy("date", Query.Direction.DESCENDING)
                        .startAfter(lastDoc)
                        .limit(pageSize.toLong())
                }
            }

            val snapshot = query.get().await()
            val expenses = snapshot.documents.map { doc ->
                val data = doc.data ?: emptyMap()
                @Suppress("UNCHECKED_CAST")
                val splitsRaw = data["splits"] as? Map<String, Map<String, Any>> ?: emptyMap()
                Expense(
                    expenseId = doc.id,
                    description = data["description"] as? String ?: "",
                    amount = (data["amount"] as? Double ?: 0.0),
                    currency = data["currency"] as? String ?: "INR",
                    paidBy = data["paidBy"] as? String ?: "",
                    splitType = SplitType.valueOf((data["splitType"] as? String ?: "equal").uppercase()),
                    splits = splitsRaw.mapValues { (_, v) ->
                        SplitEntry(
                            amount = (v["amount"] as? Double ?: 0.0),
                            shareValue = (v["shareValue"] as? Double ?: 0.0)
                        )
                    },
                    category = data["category"] as? String ?: "other",
                    isRecurring = data["isRecurring"] as? Boolean ?: false,
                    createdBy = data["createdBy"] as? String ?: ""
                )
            }
            Result.success(expenses)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun recalculateBalances(groupId: String) {
        val groupRef = firestore.collection("groups").document(groupId)

        val expensesSnapshot = groupRef.collection("expenses").get().await()
        val settlementsSnapshot = groupRef.collection("settlements").get().await()
        val membersSnapshot = groupRef.collection("members").whereEqualTo("status", "active").get().await()

        val memberUids = membersSnapshot.documents.map { it.id }

        val expenses = expensesSnapshot.documents.map { doc ->
            val data = doc.data ?: emptyMap()
            @Suppress("UNCHECKED_CAST")
            val splitsRaw = data["splits"] as? Map<String, Map<String, Any>> ?: emptyMap()
            Triple(
                data["paidBy"] as? String ?: "",
                splitsRaw.mapValues { (_, v) ->
                    SplitEntry(
                        amount = (v["amount"] as? Double ?: 0.0),
                        shareValue = (v["shareValue"] as? Double ?: 0.0)
                    )
                },
                data["amount"] as? Double ?: 0.0
            )
        }

        val settlements = settlementsSnapshot.documents.map { doc ->
            val data = doc.data ?: emptyMap()
            Triple(
                data["fromUid"] as? String ?: "",
                data["toUid"] as? String ?: "",
                data["amount"] as? Double ?: 0.0
            )
        }

        val balances = Calculations.calculateBalances(expenses, settlements, memberUids)

        val batch = firestore.runBatch { b ->
            balances.forEach { (memberUid, balance) ->
                b.update(groupRef.collection("members").document(memberUid), mapOf("balance" to balance))
            }
        }
        batch.await()
    }
}
