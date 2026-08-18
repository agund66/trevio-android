package com.trevio.android.data.remote

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.trevio.android.domain.model.Expense
import com.trevio.android.domain.model.Nudge
import com.trevio.android.domain.model.Settlement
import com.trevio.android.domain.model.SplitEntry
import com.trevio.android.domain.model.SplitType
import com.trevio.android.domain.model.TransactionType
import com.trevio.android.domain.repository.ExchangeRateService
import com.trevio.android.domain.repository.NudgeService
import com.trevio.android.util.AppConstants
import com.trevio.android.util.CurrencyConverter
import com.trevio.android.util.ErrorMessages
import com.trevio.android.util.Logger
import com.trevio.android.util.friendlyNetworkMessage
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class FirebaseNudgeServiceImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val exchangeRateService: ExchangeRateService
) : NudgeService {

    companion object {
        private const val TAG = "NudgeService"

        // Time thresholds (in milliseconds)
        private const val DAY_MS = 24L * 60 * 60 * 1000
        private const val STALE_DEBT_THRESHOLD_MS = 14L * DAY_MS          // 14 days
        private const val LARGE_EXPENSE_WINDOW_MS = 7L * DAY_MS           // last 7 days
        private const val INACTIVE_GROUP_THRESHOLD_MS = 30L * DAY_MS      // 30 days
        private const val SETTLEMENT_REMINDER_THRESHOLD_MS = 30L * DAY_MS // 30 days
        private const val GENEROSITY_WINDOW_MS = 30L * DAY_MS             // last 30 days
        private const val DEDUP_WINDOW_MS = 24L * DAY_MS                  // 24 hours

        // Detection threshold expressed in the group's calculation currency.
        private const val LARGE_EXPENSE_AMOUNT_GROUP = 120.0
        private const val GENEROSITY_PERCENT_THRESHOLD = 60.0
        private const val STREAK_MIN_DAYS = 3
    }

    // ─── NudgeService implementation ─────────────────────────────────

    override suspend fun getActiveNudges(): Result<List<Nudge>> {
        return try {
            val uid = auth.currentUser?.uid
                ?: return Result.failure(Exception(ErrorMessages.USER_NOT_AUTHENTICATED))

            val snapshot = firestore.collection("users").document(uid)
                .collection("nudges")
                .whereEqualTo("dismissedAt", 0L)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(20)
                .get().await()

            val nudges = snapshot.documents.mapNotNull { doc -> parseNudge(doc.id, doc.data) }
            Result.success(nudges)
        } catch (e: Exception) {
            Logger.e(TAG, "getActiveNudges failed", e)
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    override suspend fun generateNudges(): Result<List<Nudge>> {
        return try {
            val uid = auth.currentUser?.uid
                ?: return Result.failure(Exception(ErrorMessages.USER_NOT_AUTHENTICATED))

            val now = System.currentTimeMillis()

            // ── Fetch user's currency and exchange rates for correct nudge formatting ──
            val userDoc = firestore.collection("users").document(uid).get().await()
            val userCurrency = userDoc.getString("defaultCurrency") ?: AppConstants.BASE_CURRENCY
            var rates = emptyMap<String, Double>()

            // ── Find every group the user is an active member of ──
            val memberSnapshot = firestore.collectionGroup("members")
                .whereEqualTo("uid", uid)
                .whereEqualTo("status", "active")
                .get().await()

            val groupIds = memberSnapshot.documents.mapNotNull { doc ->
                doc.reference.parent.parent?.id
            }.distinct()

            Logger.d(TAG, "Generating nudges for $uid across ${groupIds.size} groups")

            // ── Load existing active nudges for deduplication ──
            val existingSnapshot = firestore.collection("users").document(uid)
                .collection("nudges")
                .whereGreaterThan("createdAt", now - DEDUP_WINDOW_MS)
                .get().await()

            // Key: "{type}|{groupId}" for the last 24h (non-dismissed only).
            val recentNudgeKeys = existingSnapshot.documents.mapNotNull { doc ->
                val data = doc.data ?: return@mapNotNull null
                val dismissedAt = (data["dismissedAt"] as? Number)?.toLong() ?: 0L
                if (dismissedAt != 0L) return@mapNotNull null
                val type = data["type"] as? String ?: ""
                val groupId = data["groupId"] as? String ?: ""
                "$type|$groupId"
            }.toMutableSet()

            val generatedNudges = mutableListOf<Nudge>()

            // Aggregates for streak detection (across all groups).
            val expenseDays = mutableSetOf<Long>()

            for (groupId in groupIds) {
                val groupRef = firestore.collection("groups").document(groupId)

                // ── Read group doc for name & currency ──
                val groupDoc = groupRef.get().await()
                val groupData = groupDoc.data ?: continue
                val groupName = groupData["name"] as? String ?: "this group"
                val groupCurrency = groupData["currency"] as? String ?: AppConstants.BASE_CURRENCY
                if (groupCurrency != userCurrency && rates.isEmpty()) {
                    rates = exchangeRateService.getRates().getOrNull()?.rates ?: emptyMap()
                }

                // ── Read expenses (last 100, ordered by date desc) ──
                val expenseSnapshot = groupRef.collection("expenses")
                    .orderBy("date", Query.Direction.DESCENDING)
                    .limit(100)
                    .get().await()

                val expenses = expenseSnapshot.documents.map { doc ->
                    val data = doc.data ?: emptyMap()
                    @Suppress("UNCHECKED_CAST")
                    val splits = (data["splits"] as? Map<String, Map<String, Any>>)?.mapValues { (_, v) ->
                        SplitEntry(
                            amount = (v["amount"] as? Number)?.toDouble() ?: 0.0,
                            shareValue = (v["shareValue"] as? Number)?.toDouble() ?: 0.0
                        )
                    } ?: emptyMap()

                    Expense(
                        expenseId = doc.id,
                        description = data["description"] as? String ?: "",
                        amount = (data["amount"] as? Number)?.toDouble() ?: 0.0,
                        currency = data["currency"] as? String ?: groupCurrency,
                        paidBy = data["paidBy"] as? String ?: "",
                        splitType = SplitType.valueOf(
                            (data["splitType"] as? String ?: "EQUAL").uppercase()
                        ),
                        splits = splits,
                        category = data["category"] as? String ?: "other",
                        date = (data["date"] as? Number)?.toLong() ?: 0,
                        createdBy = data["createdBy"] as? String ?: "",
                        exchangeRateToGroupCurrency = (data["exchangeRateToGroupCurrency"] as? Number)?.toDouble() ?: 1.0,
                        amountInGroupCurrency = (data["amountInGroupCurrency"] as? Number)?.toDouble() ?: ((data["amount"] as? Number)?.toDouble() ?: 0.0),
                        transactionType = TransactionType.valueOf(
                            (data["transactionType"] as? String ?: "expense").uppercase()
                        )
                    )
                }

                // ── Read settlements ──
                val settlementSnapshot = groupRef.collection("settlements").get().await()
                val settlements = settlementSnapshot.documents.map { doc ->
                    val data = doc.data ?: emptyMap()
                    Settlement(
                        settlementId = doc.id,
                        fromUid = data["fromUid"] as? String ?: "",
                        toUid = data["toUid"] as? String ?: "",
                        amount = (data["amount"] as? Number)?.toDouble() ?: 0.0,
                        date = (data["date"] as? Number)?.toLong() ?: 0
                    )
                }

                // ── Compute the user's balance for this group ──
                // balance = sum(expense.amount where paidBy==uid)
                //         - sum(user's split share)
                //         + sum(settlement.amount where fromUid==uid)
                //         - sum(settlement.amount where toUid==uid)
                val balance = computeBalance(uid, expenses, settlements)

                // Track distinct expense days for streak detection (only "expense" types).
                for (exp in expenses) {
                    if (exp.transactionType != TransactionType.EXPENSE) continue
                    if (exp.createdBy == uid || exp.paidBy == uid) {
                        expenseDays.add(exp.date / DAY_MS)
                    }
                }

                // ── Evaluate each nudge rule for this group ──

                // a) STALE_DEBT — user owes money and oldest unsettled expense is >14 days old.
                if (balance < 0) {
                    val unsettledExpenses = expenses.filter {
                        it.transactionType == TransactionType.EXPENSE &&
                            it.splits.containsKey(uid) &&
                            (it.splits[uid]?.amount ?: 0.0) > 0.0
                    }
                    val oldestUnsettled = unsettledExpenses.minOfOrNull { it.date } ?: 0L
                    if (oldestUnsettled > 0 && (now - oldestUnsettled) > STALE_DEBT_THRESHOLD_MS) {
                        val amount = formatAmount(abs(balance), groupCurrency, userCurrency, rates)
                        addNudge(
                            generatedNudges, recentNudgeKeys,
                            type = "STALE_DEBT",
                            groupId = groupId, groupName = groupName,
                            title = "You have a pending balance in $groupName",
                            body = "You owe $amount in $groupName. Settle up to keep things fair.",
                            severity = "warning",
                            actionType = "settle_up",
                            actionLabel = "Settle Up",
                            actionData = mapOf("groupId" to groupId)
                        )
                    }
                }

                // b) LARGE_EXPENSE — expense >10000 in the last 7 days paid by the user.
                val largeExpense = expenses.firstOrNull { exp ->
                    exp.transactionType == TransactionType.EXPENSE &&
                        exp.paidBy == uid &&
                        exp.amountInGroupCurrency > LARGE_EXPENSE_AMOUNT_GROUP &&
                        exp.date > 0 &&
                        (now - exp.date) <= LARGE_EXPENSE_WINDOW_MS
                }
                if (largeExpense != null) {
                    val amount = formatAmount(largeExpense.amountInGroupCurrency, groupCurrency, userCurrency, rates)
                    addNudge(
                        generatedNudges, recentNudgeKeys,
                        type = "LARGE_EXPENSE",
                        groupId = groupId, groupName = groupName,
                        title = "Big expense in $groupName",
                        body = "You fronted $amount for $groupName. Consider requesting settlements.",
                        severity = "info",
                        actionType = "view_group",
                        actionLabel = "View Group",
                        actionData = mapOf("groupId" to groupId)
                    )
                }

                // c) INACTIVE_GROUP — no expenses in the last 30 days but unsettled balances.
                val latestExpenseDate = expenses
                    .filter { it.transactionType == TransactionType.EXPENSE }
                    .maxOfOrNull { it.date } ?: 0L
                val isInactive = latestExpenseDate > 0 &&
                    (now - latestExpenseDate) > INACTIVE_GROUP_THRESHOLD_MS
                if (isInactive && abs(balance) > 0.01) {
                    addNudge(
                        generatedNudges, recentNudgeKeys,
                        type = "INACTIVE_GROUP",
                        groupId = groupId, groupName = groupName,
                        title = "$groupName has been quiet",
                        body = "No activity in 30 days but there are pending balances. Time to settle up?",
                        severity = "info",
                        actionType = "view_group",
                        actionLabel = "View Group",
                        actionData = mapOf("groupId" to groupId)
                    )
                }

                // d) SETTLEMENT_REMINDER — user is owed money for >30 days.
                if (balance > 0) {
                    // Find the oldest expense the user paid for that is still unsettled
                    // (i.e. the user is owed) and older than 30 days.
                    val owedExpenses = expenses.filter {
                        it.transactionType == TransactionType.EXPENSE &&
                            it.paidBy == uid &&
                            it.splits.any { (memberUid, entry) ->
                                memberUid != uid && entry.amount > 0.0
                            }
                    }
                    val oldestOwed = owedExpenses.minOfOrNull { it.date } ?: 0L
                    if (oldestOwed > 0 && (now - oldestOwed) > SETTLEMENT_REMINDER_THRESHOLD_MS) {
                        val amount = formatAmount(balance, groupCurrency, userCurrency, rates)
                        addNudge(
                            generatedNudges, recentNudgeKeys,
                            type = "SETTLEMENT_REMINDER",
                            groupId = groupId, groupName = groupName,
                            title = "You're owed money in $groupName",
                            body = "$amount has been pending for over 30 days. Send a friendly reminder?",
                            severity = "info",
                            actionType = "send_reminder",
                            actionLabel = "Remind",
                            actionData = mapOf("groupId" to groupId)
                        )
                    }
                }

                // f) GENEROSITY_BADGE — user paid for >60% of expenses in the last 30 days.
                val recentExpenses = expenses.filter {
                    it.transactionType == TransactionType.EXPENSE &&
                        it.date > 0 &&
                        (now - it.date) <= GENEROSITY_WINDOW_MS
                }
                if (recentExpenses.isNotEmpty()) {
                    val userPaidCount = recentExpenses.count { it.paidBy == uid }
                    val percent = (userPaidCount.toDouble() / recentExpenses.size) * 100.0
                    if (percent > GENEROSITY_PERCENT_THRESHOLD) {
                        addNudge(
                            generatedNudges, recentNudgeKeys,
                            type = "GENEROSITY_BADGE",
                            groupId = groupId, groupName = groupName,
                            title = "You're the generous one in $groupName",
                            body = "You've covered ${percent.toInt()}% of expenses this month. Your group appreciates you!",
                            severity = "positive",
                            actionType = "",
                            actionLabel = "",
                            actionData = emptyMap()
                        )
                    }
                }
            }

            // e) POSITIVE_STREAK — user has logged expenses in 3+ consecutive days.
            val streak = computeCurrentStreak(expenseDays, now)
            if (streak >= STREAK_MIN_DAYS) {
                addNudge(
                    generatedNudges, recentNudgeKeys,
                    type = "POSITIVE_STREAK",
                    groupId = "", groupName = "",
                    title = "You're on a $streak-day logging streak!",
                    body = "Keep it up! Consistent logging helps keep group finances transparent.",
                    severity = "positive",
                    actionType = "",
                    actionLabel = "",
                    actionData = emptyMap()
                )
            }

            // ── Persist newly generated nudges to Firestore ──
            val nudgesRef = firestore.collection("users").document(uid).collection("nudges")
            for (nudge in generatedNudges) {
                val docRef = nudgesRef.document()
                val nudgeWithId = nudge.copy(nudgeId = docRef.id)
                docRef.set(nudgeWithId.toMap()).await()
            }

            Logger.i(TAG, "Generated ${generatedNudges.size} nudges for $uid")
            Result.success(generatedNudges)
        } catch (e: Exception) {
            Logger.e(TAG, "generateNudges failed", e)
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    override suspend fun dismissNudge(nudgeId: String): Result<Unit> {
        return try {
            val uid = auth.currentUser?.uid
                ?: return Result.failure(Exception(ErrorMessages.USER_NOT_AUTHENTICATED))

            firestore.collection("users").document(uid)
                .collection("nudges").document(nudgeId)
                .set(
                    mapOf("dismissedAt" to System.currentTimeMillis()),
                    com.google.firebase.firestore.SetOptions.merge()
                ).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Logger.e(TAG, "dismissNudge failed", e)
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    override suspend fun markNudgeRead(nudgeId: String): Result<Unit> {
        return try {
            val uid = auth.currentUser?.uid
                ?: return Result.failure(Exception(ErrorMessages.USER_NOT_AUTHENTICATED))

            firestore.collection("users").document(uid)
                .collection("nudges").document(nudgeId)
                .set(
                    mapOf("readAt" to System.currentTimeMillis()),
                    com.google.firebase.firestore.SetOptions.merge()
                ).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Logger.e(TAG, "markNudgeRead failed", e)
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────

    /**
     * Adds a nudge to [generated] if an active nudge of the same type+group
     * hasn't already been created in the last 24 hours ([recentKeys]).
     */
    private fun addNudge(
        generated: MutableList<Nudge>,
        recentKeys: MutableSet<String>,
        type: String,
        groupId: String,
        groupName: String,
        title: String,
        body: String,
        severity: String,
        actionType: String,
        actionLabel: String,
        actionData: Map<String, String>
    ) {
        val key = "$type|$groupId"
        if (key in recentKeys) return

        val nudge = Nudge(
            type = type,
            title = title,
            body = body,
            groupId = groupId,
            groupName = groupName,
            severity = severity,
            actionType = actionType,
            actionLabel = actionLabel,
            actionData = actionData,
            createdAt = System.currentTimeMillis()
        )
        generated.add(nudge)
        recentKeys.add(key)
    }

    /**
     * Computes the user's balance for a group:
     *   balance = sum(expense.amount where paidBy==uid)
     *           - sum(user's split share)
     *           + sum(settlement.amount where fromUid==uid)
     *           - sum(settlement.amount where toUid==uid)
     *
     * A positive balance means the group owes the user; negative means the
     * user owes the group.
     */
    private fun computeBalance(
        uid: String,
        expenses: List<Expense>,
        settlements: List<Settlement>
    ): Double {
        var balance = 0.0

        for (exp in expenses) {
            if (exp.transactionType != TransactionType.EXPENSE) continue
            if (exp.paidBy == uid) {
                balance += exp.amountInGroupCurrency
            }
            val userShare = exp.splits[uid]?.amount ?: 0.0
            balance -= userShare * exp.exchangeRateToGroupCurrency
        }

        for (settle in settlements) {
            if (settle.fromUid == uid) {
                balance += settle.amount
            } else if (settle.toUid == uid) {
                balance -= settle.amount
            }
        }

        return balance
    }

    /**
     * Computes the length (in days) of the current consecutive logging streak
     * ending today (or yesterday, to tolerate timezone drift).
     */
    private fun computeCurrentStreak(expenseDays: Set<Long>, now: Long): Int {
        if (expenseDays.isEmpty()) return 0
        val today = now / DAY_MS
        var streak = 0
        var cursor = today
        // Allow the streak to start from today or yesterday.
        if (cursor !in expenseDays) cursor = today - 1
        while (cursor in expenseDays) {
            streak++
            cursor--
        }
        return streak
    }

    /**
     * Formats an amount with the appropriate currency symbol and grouping.
     * Falls back to a plain grouped number when the currency is unknown.
     */
    private fun formatAmount(amountInGroupCurrency: Double, groupCurrency: String, userCurrency: String, rates: Map<String, Double>): String {
        val converted = CurrencyConverter.convertCurrency(amountInGroupCurrency, groupCurrency, userCurrency, rates)
        return CurrencyConverter.formatCurrency(converted, userCurrency)
    }

    /** Parses a Firestore nudge document into a [Nudge]. */
    @Suppress("UNCHECKED_CAST")
    private fun parseNudge(nudgeId: String, data: Map<String, Any>?): Nudge? {
        if (data == null) return null
        return Nudge(
            nudgeId = nudgeId,
            uid = data["uid"] as? String ?: "",
            type = data["type"] as? String ?: "",
            title = data["title"] as? String ?: "",
            body = data["body"] as? String ?: "",
            groupId = data["groupId"] as? String ?: "",
            groupName = data["groupName"] as? String ?: "",
            severity = data["severity"] as? String ?: "info",
            actionLabel = data["actionLabel"] as? String ?: "",
            actionType = data["actionType"] as? String ?: "",
            actionData = (data["actionData"] as? Map<String, String>) ?: emptyMap(),
            createdAt = (data["createdAt"] as? Number)?.toLong() ?: 0,
            readAt = (data["readAt"] as? Number)?.toLong() ?: 0,
            dismissedAt = (data["dismissedAt"] as? Number)?.toLong() ?: 0
        )
    }
}

// ─── Nudge serialization extension ───────────────────────────────────

private fun Nudge.toMap(): Map<String, Any> = mapOf(
    "nudgeId" to nudgeId,
    "uid" to uid,
    "type" to type,
    "title" to title,
    "body" to body,
    "groupId" to groupId,
    "groupName" to groupName,
    "severity" to severity,
    "actionLabel" to actionLabel,
    "actionType" to actionType,
    "actionData" to actionData,
    "createdAt" to createdAt,
    "readAt" to readAt,
    "dismissedAt" to dismissedAt
)
