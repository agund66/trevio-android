package com.trevio.android.data.remote

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.trevio.android.domain.model.Expense
import com.trevio.android.domain.model.KarmaBreakdown
import com.trevio.android.domain.model.KarmaComponents
import com.trevio.android.domain.model.Settlement
import com.trevio.android.domain.model.SplitEntry
import com.trevio.android.domain.model.SplitType
import com.trevio.android.domain.repository.KarmaService
import com.trevio.android.util.AppConstants
import com.trevio.android.util.ErrorMessages
import com.trevio.android.util.Logger
import com.trevio.android.util.friendlyNetworkMessage
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

@Singleton
class FirebaseKarmaServiceImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : KarmaService {

    companion object {
        private const val TAG = "KarmaService"
        private const val STALE_DEBT_THRESHOLD_MS = 30L * 24 * 60 * 60 * 1000 // 30 days
        private const val MS_PER_DAY = 24.0 * 60 * 60 * 1000
    }

    // ─── KarmaService implementation ─────────────────────────────────

    override suspend fun getKarmaBreakdown(): Result<KarmaBreakdown> {
        return try {
            val uid = auth.currentUser?.uid
                ?: return Result.failure(Exception(ErrorMessages.USER_NOT_AUTHENTICATED))

            val breakdownDoc = firestore.collection("users").document(uid)
                .collection("karma").document("breakdown").get().await()

            if (breakdownDoc.exists()) {
                val cached = parseBreakdown(breakdownDoc.data, uid)
                if (cached != null) return Result.success(cached)
            }

            // No cached breakdown — compute and cache a fresh one.
            refreshKarma()
        } catch (e: Exception) {
            Logger.e(TAG, "getKarmaBreakdown failed", e)
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    override suspend fun refreshKarma(): Result<KarmaBreakdown> {
        return try {
            val uid = auth.currentUser?.uid
                ?: return Result.failure(Exception(ErrorMessages.USER_NOT_AUTHENTICATED))

            // Find every group the user is an active member of.
            val memberSnapshot = firestore.collectionGroup("members")
                .whereEqualTo("uid", uid)
                .whereEqualTo("status", "active")
                .get().await()

            val groupIds = memberSnapshot.documents.mapNotNull { doc ->
                // The parent path of a collectionGroup("members") doc is groups/{groupId}
                doc.reference.parent.parent?.id
            }.distinct()

            Logger.d(TAG, "Computing karma for $uid across ${groupIds.size} groups")

            // Accumulators for the five karma components.
            var settledDebts = 0.0          // sum of settlement amounts where user is payer
            var outstandingDebts = 0.0       // current debts the user owes (negative balances)
            var totalPaidForOthers = 0.0     // paid-by-user minus user's own share
            var totalSpent = 0.0             // sum of all expense amounts in user's groups
            var expenseCount = 0             // count of expense-type entries
            var settlementDayGaps = mutableListOf<Long>() // days between expense date & settlement date
            var staleDebtCount = 0           // groups with outstanding debt older than 30 days

            val now = System.currentTimeMillis()

            for (groupId in groupIds) {
                val groupRef = firestore.collection("groups").document(groupId)

                // ── Read expenses (last 500, ordered by date desc) ──
                val expenseSnapshot = groupRef.collection("expenses")
                    .orderBy("date", Query.Direction.DESCENDING)
                    .limit(500)
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
                        currency = data["currency"] as? String ?: AppConstants.BASE_CURRENCY,
                        paidBy = data["paidBy"] as? String ?: "",
                        splitType = SplitType.valueOf(
                            (data["splitType"] as? String ?: "EQUAL").uppercase()
                        ),
                        splits = splits,
                        category = data["category"] as? String ?: "other",
                        date = (data["date"] as? Number)?.toLong() ?: 0,
                        createdBy = data["createdBy"] as? String ?: "",
                        transactionType = com.trevio.android.domain.model.TransactionType.valueOf(
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

                // ── Read the user's member doc for current balance ──
                val memberDoc = groupRef.collection("members").document(uid).get().await()
                val memberData = memberDoc.data
                val balance = (memberData?.get("balance") as? Number)?.toDouble() ?: 0.0

                // ── Accumulate spending & generosity metrics ──
                for (exp in expenses) {
                    // Only count "expense" types for spending totals (not "income").
                    if (exp.transactionType != com.trevio.android.domain.model.TransactionType.EXPENSE) continue
                    expenseCount++
                    totalSpent += exp.amount

                    if (exp.paidBy == uid) {
                        // User's own share of this expense.
                        val ownShare = exp.splits[uid]?.amount ?: 0.0
                        totalPaidForOthers += max(0.0, exp.amount - ownShare)
                    }
                }

                // ── Accumulate reliability & settlement-speed metrics ──
                for (settle in settlements) {
                    if (settle.fromUid == uid) {
                        settledDebts += settle.amount

                        // Track how long it took to settle: gap between the settlement
                        // date and the earliest expense it likely covered. We use the
                        // settlement date minus the most recent prior expense date.
                        if (settle.date > 0) {
                            val priorExpenseDate = expenses
                                .filter { it.date in 1 until settle.date }
                                .maxOfOrNull { it.date } ?: 0
                            if (priorExpenseDate > 0) {
                                val gapDays = ((settle.date - priorExpenseDate) / MS_PER_DAY).toLong()
                                settlementDayGaps.add(gapDays)
                            }
                        }
                    }
                }

                // ── Outstanding debts & group health ──
                if (balance < 0) {
                    outstandingDebts += -balance // debt is a negative balance
                    // A debt is "stale" if the group's most recent expense is older than 30 days.
                    val latestExpenseDate = expenses.maxOfOrNull { it.date } ?: 0
                    if (latestExpenseDate > 0 && (now - latestExpenseDate) > STALE_DEBT_THRESHOLD_MS) {
                        staleDebtCount++
                    }
                }
            }

            // ─── Compute the five karma components ───────────────────────

            // 1. Reliability (0-300): settled debts vs total debts owed.
            val totalDebts = settledDebts + outstandingDebts
            val reliabilityScore = if (totalDebts > 0) {
                min(300, ((settledDebts / totalDebts) * 300).toInt())
            } else {
                300 // no debts at all — fully reliable
            }

            // 2. Generosity (0-250): paid-for-others ratio.
            val generosityScore = min(250, ((totalPaidForOthers / max(totalSpent, 1.0)) * 250).toInt())

            // 3. Consistency (0-200): expense count as a proxy for logging streak.
            val consistencyScore = min(200, (expenseCount / 50.0 * 200).toInt())

            // 4. SettlementSpeed (0-150): average days to settle.
            val settlementSpeedScore = if (settlementDayGaps.isEmpty()) {
                0
            } else {
                val avgSettlementDays = settlementDayGaps.average()
                if (avgSettlementDays > 30) 0 else min(150, max(0, (150 - avgSettlementDays * 5).toInt()))
            }

            // 5. GroupHealth (0-100): penalty for stale outstanding debts.
            val groupHealthScore = min(100, max(0, 100 - staleDebtCount * 10))

            val components = KarmaComponents(
                reliabilityScore = reliabilityScore,
                generosityScore = generosityScore,
                consistencyScore = consistencyScore,
                settlementSpeedScore = settlementSpeedScore,
                groupHealthScore = groupHealthScore
            )

            val totalScore = max(0, min(1000,
                reliabilityScore + generosityScore + consistencyScore +
                    settlementSpeedScore + groupHealthScore
            ))

            val tier = when (totalScore) {
                in 0..200 -> "bronze"
                in 201..450 -> "silver"
                in 451..700 -> "gold"
                else -> "platinum"
            }

            val updatedAt = System.currentTimeMillis()
            val breakdown = KarmaBreakdown(
                uid = uid,
                score = totalScore,
                tier = tier,
                components = components,
                updatedAt = updatedAt
            )

            // ─── Persist the breakdown & update the user doc ─────────────
            val breakdownData = mapOf(
                "uid" to uid,
                "score" to totalScore,
                "tier" to tier,
                "components" to mapOf(
                    "reliabilityScore" to components.reliabilityScore,
                    "generosityScore" to components.generosityScore,
                    "consistencyScore" to components.consistencyScore,
                    "settlementSpeedScore" to components.settlementSpeedScore,
                    "groupHealthScore" to components.groupHealthScore
                ),
                "updatedAt" to updatedAt
            )

            firestore.collection("users").document(uid)
                .collection("karma").document("breakdown")
                .set(breakdownData).await()

            firestore.collection("users").document(uid)
                .set(mapOf(
                    "karmaScore" to totalScore,
                    "karmaTier" to tier,
                    "karmaUpdatedAt" to updatedAt
                ), com.google.firebase.firestore.SetOptions.merge()).await()

            Logger.i(TAG, "Karma computed for $uid: score=$totalScore tier=$tier")

            Result.success(breakdown)
        } catch (e: Exception) {
            Logger.e(TAG, "refreshKarma failed", e)
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    override suspend fun setKarmaPublic(public: Boolean): Result<Unit> {
        return try {
            val uid = auth.currentUser?.uid
                ?: return Result.failure(Exception(ErrorMessages.USER_NOT_AUTHENTICATED))

            firestore.collection("users").document(uid)
                .set(mapOf(
                    "karmaPublic" to public,
                    "updatedAt" to System.currentTimeMillis()
                ), com.google.firebase.firestore.SetOptions.merge()).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Logger.e(TAG, "setKarmaPublic failed", e)
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    override suspend fun getPublicKarma(uid: String): Result<KarmaBreakdown?> {
        return try {
            if (uid.isBlank()) return Result.failure(Exception("User ID is required"))

            val userDoc = firestore.collection("users").document(uid).get().await()
            if (!userDoc.exists()) return Result.success(null)

            val isPublic = userDoc.getBoolean("karmaPublic") ?: false
            if (!isPublic) return Result.success(null)

            val breakdownDoc = firestore.collection("users").document(uid)
                .collection("karma").document("breakdown").get().await()
            if (!breakdownDoc.exists()) return Result.success(null)

            Result.success(parseBreakdown(breakdownDoc.data, uid))
        } catch (e: Exception) {
            Logger.e(TAG, "getPublicKarma failed", e)
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────

    /** Parses a Firestore breakdown document into a [KarmaBreakdown]. */
    @Suppress("UNCHECKED_CAST")
    private fun parseBreakdown(data: Map<String, Any>?, uid: String): KarmaBreakdown? {
        if (data == null) return null

        val componentsMap = data["components"] as? Map<String, Any>
        val components = if (componentsMap != null) {
            KarmaComponents(
                reliabilityScore = (componentsMap["reliabilityScore"] as? Number)?.toInt() ?: 0,
                generosityScore = (componentsMap["generosityScore"] as? Number)?.toInt() ?: 0,
                consistencyScore = (componentsMap["consistencyScore"] as? Number)?.toInt() ?: 0,
                settlementSpeedScore = (componentsMap["settlementSpeedScore"] as? Number)?.toInt() ?: 0,
                groupHealthScore = (componentsMap["groupHealthScore"] as? Number)?.toInt() ?: 0
            )
        } else {
            KarmaComponents()
        }

        return KarmaBreakdown(
            uid = data["uid"] as? String ?: uid,
            score = (data["score"] as? Number)?.toInt() ?: 0,
            tier = data["tier"] as? String ?: "bronze",
            components = components,
            updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: 0
        )
    }
}
