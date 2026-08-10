package com.trevio.android.data.remote

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.trevio.android.domain.model.BillItem
import com.trevio.android.domain.model.Expense
import com.trevio.android.domain.model.GroupTemplate
import com.trevio.android.domain.model.ItemizedSplitData
import com.trevio.android.domain.model.PaginatedResult
import com.trevio.android.domain.model.RecurringConfig
import com.trevio.android.domain.model.RecurringFrequency
import com.trevio.android.domain.model.SplitEntry
import com.trevio.android.domain.model.SplitType
import com.trevio.android.domain.model.TransactionType
import com.trevio.android.domain.repository.ExpenseService
import com.trevio.android.domain.repository.ExchangeRateService
import com.trevio.android.util.Calculations
import com.trevio.android.util.DateUtils
import com.trevio.android.util.ErrorMessages
import com.trevio.android.util.friendlyNetworkMessage
import com.trevio.android.util.Logger
import com.trevio.android.util.MemberRole
import com.trevio.android.util.MemberStatus
import com.trevio.android.util.toStorageString
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseExpenseServiceImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val exchangeRateService: ExchangeRateService
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
        note: String,
        recurring: RecurringConfig?,
        itemizedData: ItemizedSplitData?,
        transactionType: TransactionType
    ): Result<String> {
        return try {
            val uid = auth.currentUser?.uid ?: return Result.failure(Exception(ErrorMessages.USER_NOT_AUTHENTICATED))
            if (groupId.isBlank() || description.isBlank() || amount <= 0.0 || paidBy.isBlank()) {
                return Result.failure(Exception("Missing required fields"))
            }

            val groupRef = firestore.collection("groups").document(groupId)
            val groupDoc = groupRef.get().await()
            if (!groupDoc.exists()) return Result.failure(Exception(ErrorMessages.GROUP_NOT_FOUND))

            val memberDoc = groupRef.collection("members").document(uid).get().await()
            if (!memberDoc.exists()) return Result.failure(Exception("You are not a member of this group"))

            // Check if this is a household group (no splitting, no balance recalc)
            val templateStr = groupDoc.getString("template") ?: "casual"
            val isHousehold = templateStr.equals("household", ignoreCase = true)

            val calculatedSplits = if (isHousehold) {
                emptyMap<String, SplitEntry>()
            } else {
                Calculations.calculateSplits(amount, splitType, memberUids, splits, itemizedData)
            }
            val exchangeRateToBase = exchangeRateService.getRateToBase(currency).getOrDefault(1.0)
            val now = System.currentTimeMillis()
            val expenseDate = if (date > 0) date else now
            val expenseRef = groupRef.collection("expenses").document()

            val expenseData = mutableMapOf<String, Any>(
                "description" to description,
                "amount" to amount,
                "currency" to currency,
                "paidBy" to paidBy,
                "splitType" to splitType.toStorageString(),
                "splits" to calculatedSplits.mapValues { (_, v) ->
                    mapOf("amount" to v.amount, "shareValue" to v.shareValue)
                },
                "category" to (category.ifEmpty { "other" }),
                "date" to expenseDate,
                "createdBy" to uid,
                "createdAt" to now,
                "exchangeRateToBase" to exchangeRateToBase,
                "transactionType" to transactionType.toStorageString()
            )
            if (note.isNotBlank()) expenseData["note"] = note
            if (recurring != null) {
                expenseData["recurring"] = mapOf(
                    "frequency" to recurring.frequency.toStorageString(),
                    "endDate" to (recurring.endDate ?: 0L),
                    "nextDueDate" to (recurring.nextDueDate ?: 0L),
                    "parentExpenseId" to (recurring.parentExpenseId ?: "")
                )
            }
            if (itemizedData != null) {
                expenseData["itemizedData"] = mapOf(
                    "items" to itemizedData.items.map { item ->
                        mapOf(
                            "itemId" to item.itemId,
                            "name" to item.name,
                            "amount" to item.amount,
                            "assignedTo" to item.assignedTo
                        )
                    },
                    "taxAmount" to itemizedData.taxAmount,
                    "tipAmount" to itemizedData.tipAmount,
                    "taxSplitMode" to itemizedData.taxSplitMode,
                    "tipSplitMode" to itemizedData.tipSplitMode
                )
            }

            val amountInBase = amount * exchangeRateToBase

            val batch = firestore.batch()
            batch.set(expenseRef, expenseData)
            val activityType = if (transactionType == TransactionType.INCOME) "income_added" else "expense_added"
            val activityDesc = if (transactionType == TransactionType.INCOME) {
                "Added income: $description ($currency $amount)"
            } else {
                "Added expense: $description ($currency $amount)"
            }
            batch.set(groupRef.collection("activities").document(), mapOf(
                "type" to activityType,
                "description" to activityDesc,
                "userId" to uid,
                "data" to mapOf("expenseId" to expenseRef.id, "amount" to amount, "description" to description),
                "createdAt" to now
            ))
            // Only update totalExpenses for EXPENSE type (not INCOME)
            if (transactionType == TransactionType.EXPENSE) {
                batch.update(groupRef, mapOf(
                    "totalExpenses" to FieldValue.increment(amountInBase),
                    "updatedAt" to now
                ))
            } else {
                batch.update(groupRef, mapOf("updatedAt" to now))
            }
            batch.commit().await()

            // Skip balance recalculation for household groups (no splitting)
            if (!isHousehold) {
                recalculateBalances(groupId)
            }

            // Notify all active group members except the creator (non-blocking — don't fail expense creation if notifications fail)
            try {
                val groupName = groupDoc.getString("name") ?: ""
                val creatorDoc = firestore.collection("users").document(uid).get().await()
                val creatorName = creatorDoc.getString("displayName") ?: "Someone"
                val activeMembers = groupRef.collection("members")
                    .whereEqualTo("status", MemberStatus.ACTIVE).get().await()

                var notifyBatch = firestore.batch()
                var count = 0
                for (memberDoc in activeMembers.documents) {
                    val memberUid = memberDoc.id
                    if (memberUid == uid) continue
                    val notifRef = firestore.collection("users").document(memberUid)
                        .collection("notifications").document()
                    val notifType = if (transactionType == TransactionType.INCOME) "income_added" else "expense_added"
                    val notifTitle = if (transactionType == TransactionType.INCOME) "New Income Added" else "New Expense Added"
                    val notifBody = if (transactionType == TransactionType.INCOME) {
                        "$creatorName added income \"$description\" ($currency $amount) in \"$groupName\""
                    } else {
                        "$creatorName added \"$description\" ($currency $amount) in \"$groupName\""
                    }
                    notifyBatch.set(notifRef, mapOf(
                        "type" to notifType,
                        "title" to notifTitle,
                        "body" to notifBody,
                        "data" to mapOf(
                            "groupId" to groupId,
                            "groupName" to groupName,
                            "expenseId" to expenseRef.id,
                            "type" to notifType
                        ),
                        "read" to false,
                        "createdAt" to now
                    ))
                    count++
                    if (count % 400 == 0) {
                        notifyBatch.commit().await()
                        notifyBatch = firestore.batch()
                    }
                }
                if (count % 400 != 0) {
                    notifyBatch.commit().await()
                }
            } catch (notifError: Exception) {
                Logger.w("FirebaseExpenseService", "Failed to send expense notifications", notifError)
            }

            Result.success(expenseRef.id)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
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
        date: Long,
        note: String,
        itemizedData: ItemizedSplitData?,
        transactionType: TransactionType
    ): Result<Unit> {
        return try {
            val uid = auth.currentUser?.uid ?: return Result.failure(Exception(ErrorMessages.USER_NOT_AUTHENTICATED))
            if (groupId.isBlank() || expenseId.isBlank()) return Result.failure(Exception("Group ID and Expense ID are required"))

            val groupRef = firestore.collection("groups").document(groupId)
            val expenseRef = groupRef.collection("expenses").document(expenseId)
            val expenseDoc = expenseRef.get().await()
            if (!expenseDoc.exists()) return Result.failure(Exception(ErrorMessages.EXPENSE_NOT_FOUND))

            val oldExpense = expenseDoc.data ?: return Result.failure(Exception("Invalid expense data"))
            val memberDoc = groupRef.collection("members").document(uid).get().await()
            if (!memberDoc.exists()) return Result.failure(Exception("You are not a member of this group"))

            // Only creator or group admin can edit
            val isCreator = oldExpense["createdBy"] == uid
            val isAdmin = memberDoc.data?.get("role") == MemberRole.ADMIN
            if (!isCreator && !isAdmin) return Result.failure(Exception("Only the expense creator or group admin can edit this expense"))

            // Check if household group
            val groupDoc = groupRef.get().await()
            val templateStr = groupDoc.getString("template") ?: "casual"
            val isHousehold = templateStr.equals("household", ignoreCase = true)

            val now = System.currentTimeMillis()
            val updateData = mutableMapOf<String, Any>("updatedAt" to now)

            if (description.isNotBlank()) updateData["description"] = description
            if (amount > 0) updateData["amount"] = amount
            if (currency.isNotBlank()) updateData["currency"] = currency
            if (paidBy.isNotBlank()) updateData["paidBy"] = paidBy
            if (category.isNotBlank()) updateData["category"] = category
            updateData["note"] = note
            updateData["transactionType"] = transactionType.toStorageString()

            val oldCurrency = oldExpense["currency"] as? String ?: "INR"
            val newCurrency = if (currency.isNotBlank()) currency else oldCurrency
            if (newCurrency != oldCurrency) {
                val newRate = exchangeRateService.getRateToBase(newCurrency).getOrDefault(1.0)
                updateData["exchangeRateToBase"] = newRate
            }

            val effectiveAmount = if (amount > 0) amount else (oldExpense["amount"] as? Number)?.toDouble() ?: 0.0

            if (memberUids.isNotEmpty() && !isHousehold) {
                updateData["splitType"] = splitType.toStorageString()
                val calculatedSplits = Calculations.calculateSplits(effectiveAmount, splitType, memberUids, splits, itemizedData)
                updateData["splits"] = calculatedSplits.mapValues { (_, v) ->
                    mapOf("amount" to v.amount, "shareValue" to v.shareValue)
                }
                if (itemizedData != null) {
                    updateData["itemizedData"] = mapOf(
                        "items" to itemizedData.items.map { item ->
                            mapOf(
                                "itemId" to item.itemId,
                                "name" to item.name,
                                "amount" to item.amount,
                                "assignedTo" to item.assignedTo
                            )
                        },
                        "taxAmount" to itemizedData.taxAmount,
                        "tipAmount" to itemizedData.tipAmount,
                        "taxSplitMode" to itemizedData.taxSplitMode,
                        "tipSplitMode" to itemizedData.tipSplitMode
                    )
                } else if (splitType != SplitType.ITEMIZED) {
                    updateData["itemizedData"] = FieldValue.delete()
                }
            }

            val oldAmount = (oldExpense["amount"] as? Number)?.toDouble() ?: 0.0
            val oldRate = (oldExpense["exchangeRateToBase"] as? Number)?.toDouble() ?: 1.0
            val newRate = (updateData["exchangeRateToBase"] as? Number)?.toDouble() ?: oldRate
            val oldAmountInBase = oldAmount * oldRate
            val newAmountInBase = effectiveAmount * newRate
            val amountDiffInBase = newAmountInBase - oldAmountInBase

            // Check old transaction type to determine if totalExpenses should be adjusted
            val oldTransactionType = (oldExpense["transactionType"] as? String) ?: "expense"
            val newTransactionType = transactionType.toStorageString()

            val batch = firestore.batch()
            batch.update(expenseRef, updateData)
            // Handle totalExpenses based on transaction type changes
            when {
                oldTransactionType == "expense" && newTransactionType == "income" -> {
                    // Changed from expense to income: decrement by old amount
                    batch.update(groupRef, mapOf(
                        "totalExpenses" to FieldValue.increment(-oldAmountInBase),
                        "updatedAt" to now
                    ))
                }
                oldTransactionType == "income" && newTransactionType == "expense" -> {
                    // Changed from income to expense: increment by new amount
                    batch.update(groupRef, mapOf(
                        "totalExpenses" to FieldValue.increment(newAmountInBase),
                        "updatedAt" to now
                    ))
                }
                oldTransactionType == "expense" && newTransactionType == "expense" && amountDiffInBase != 0.0 -> {
                    // Both expenses, amount changed: adjust by difference
                    batch.update(groupRef, mapOf(
                        "totalExpenses" to FieldValue.increment(amountDiffInBase),
                        "updatedAt" to now
                    ))
                }
                else -> {
                    batch.update(groupRef, mapOf("updatedAt" to now))
                }
            }
            batch.set(groupRef.collection("activities").document(), mapOf(
                "type" to (if (newTransactionType == "income") "income_updated" else "expense_updated"),
                "description" to (if (newTransactionType == "income") "Updated income: ${if (description.isNotBlank()) description else oldExpense["description"]}" else "Updated expense: ${if (description.isNotBlank()) description else oldExpense["description"]}"),
                "userId" to uid,
                "data" to mapOf("expenseId" to expenseId, "groupId" to groupId),
                "createdAt" to now
            ))
            batch.commit().await()

            // Skip balance recalculation for household groups
            if (!isHousehold) {
                recalculateBalances(groupId)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    override suspend fun deleteExpense(groupId: String, expenseId: String): Result<Unit> {
        return try {
            val uid = auth.currentUser?.uid ?: return Result.failure(Exception(ErrorMessages.USER_NOT_AUTHENTICATED))
            if (groupId.isBlank() || expenseId.isBlank()) return Result.failure(Exception("Group ID and Expense ID are required"))

            val groupRef = firestore.collection("groups").document(groupId)
            val expenseRef = groupRef.collection("expenses").document(expenseId)
            val expenseDoc = expenseRef.get().await()
            if (!expenseDoc.exists()) return Result.failure(Exception(ErrorMessages.EXPENSE_NOT_FOUND))

            val expenseData = expenseDoc.data ?: return Result.failure(Exception("Invalid expense data"))
            val memberDoc = groupRef.collection("members").document(uid).get().await()
            if (!memberDoc.exists()) return Result.failure(Exception("You are not a member of this group"))

            // Only creator or group admin can delete
            val isCreator = expenseData["createdBy"] == uid
            val isAdmin = memberDoc.data?.get("role") == MemberRole.ADMIN
            if (!isCreator && !isAdmin) return Result.failure(Exception("Only the expense creator or group admin can delete this expense"))

            // Check if household group
            val groupDoc = groupRef.get().await()
            val templateStr = groupDoc.getString("template") ?: "casual"
            val isHousehold = templateStr.equals("household", ignoreCase = true)

            val now = System.currentTimeMillis()
            val expenseAmount = (expenseData["amount"] as? Number)?.toDouble() ?: 0.0
            val expenseRate = (expenseData["exchangeRateToBase"] as? Number)?.toDouble() ?: 1.0
            val amountInBase = expenseAmount * expenseRate
            val transactionTypeStr = (expenseData["transactionType"] as? String) ?: "expense"

            val batch = firestore.batch()
            batch.delete(expenseRef)
            // Only decrement totalExpenses if it was an expense (not income)
            if (transactionTypeStr == "expense") {
                batch.update(groupRef, mapOf(
                    "totalExpenses" to FieldValue.increment(-amountInBase),
                    "updatedAt" to now
                ))
            } else {
                batch.update(groupRef, mapOf("updatedAt" to now))
            }
            batch.set(groupRef.collection("activities").document(), mapOf(
                "type" to (if (transactionTypeStr == "income") "income_deleted" else "expense_deleted"),
                "description" to (if (transactionTypeStr == "income") "Deleted income: ${expenseData["description"]}" else "Deleted expense: ${expenseData["description"]}"),
                "userId" to uid,
                "data" to mapOf("expenseId" to expenseId, "groupId" to groupId, "amount" to expenseAmount),
                "createdAt" to now
            ))
            batch.commit().await()

            // Skip balance recalculation for household groups
            if (!isHousehold) {
                recalculateBalances(groupId)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    override suspend fun getGroupExpenses(groupId: String, pageSize: Int, lastExpenseId: String?): Result<PaginatedResult<Expense>> {
        return try {
            val uid = auth.currentUser?.uid ?: return Result.failure(Exception(ErrorMessages.USER_NOT_AUTHENTICATED))
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
                        @Suppress("UNCHECKED_CAST")
                        val itemsRaw = id["items"] as? List<Map<String, Any>> ?: emptyList()
                        ItemizedSplitData(
                            items = itemsRaw.map { item ->
                                @Suppress("UNCHECKED_CAST")
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
            Result.success(PaginatedResult(
                items = expenses,
                hasMore = snapshot.size() == pageSize,
                lastId = if (snapshot.size() > 0) snapshot.documents.last().id else null
            ))
        } catch (e: Exception) {
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    private suspend fun recalculateBalances(groupId: String) {
        val groupRef = firestore.collection("groups").document(groupId)

        val expensesSnapshot = groupRef.collection("expenses").get().await()
        val settlementsSnapshot = groupRef.collection("settlements").get().await()
        val membersSnapshot = groupRef.collection("members").whereEqualTo("status", MemberStatus.ACTIVE).get().await()

        val memberUids = membersSnapshot.documents.map { it.id }

        val expenses = expensesSnapshot.documents.map { doc ->
            val data = doc.data ?: emptyMap()
            @Suppress("UNCHECKED_CAST")
            val splitsRaw = data["splits"] as? Map<String, Map<String, Any>> ?: emptyMap()
            Calculations.ExpenseBalanceData(
                paidBy = data["paidBy"] as? String ?: "",
                splits = splitsRaw.mapValues { (_, v) ->
                    SplitEntry(
                        amount = (v["amount"] as? Number)?.toDouble() ?: 0.0,
                        shareValue = (v["shareValue"] as? Number)?.toDouble() ?: 0.0
                    )
                },
                amount = (data["amount"] as? Number)?.toDouble() ?: 0.0,
                exchangeRateToBase = (data["exchangeRateToBase"] as? Number)?.toDouble() ?: 1.0
            )
        }

        val settlements = settlementsSnapshot.documents.map { doc ->
            val data = doc.data ?: emptyMap()
            Triple(
                data["fromUid"] as? String ?: "",
                data["toUid"] as? String ?: "",
                (data["amount"] as? Number)?.toDouble() ?: 0.0
            )
        }

        val balances = Calculations.calculateBalances(expenses, settlements, memberUids)

        val balanceEntries = balances.entries.toList()
        val batchSize = 400
        for (i in balanceEntries.indices step batchSize) {
            val chunk = balanceEntries.subList(i, minOf(i + batchSize, balanceEntries.size))
            val batch = firestore.batch()
            for ((memberUid, balance) in chunk) {
                val roundedBalance = kotlin.math.round(balance * 100) / 100
                batch.update(groupRef.collection("members").document(memberUid), mapOf("balance" to roundedBalance))
            }
            batch.commit().await()
        }
    }
}
