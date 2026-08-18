package com.trevio.android.data.remote

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.trevio.android.domain.model.MonthlyRecap
import com.trevio.android.domain.model.SplitEntry
import com.trevio.android.domain.model.WrappedSummary
import com.trevio.android.domain.repository.ExchangeRateService
import com.trevio.android.domain.repository.WrappedService
import com.trevio.android.util.AppConstants
import com.trevio.android.util.ErrorMessages
import com.trevio.android.util.Logger
import com.trevio.android.util.friendlyNetworkMessage
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseWrappedServiceImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val exchangeRateService: ExchangeRateService
) : WrappedService {

    companion object {
        private const val TAG = "WrappedService"
    }

    // ─── WrappedService implementation ───────────────────────────────

    override suspend fun getWrappedSummary(year: Int): Result<WrappedSummary> {
        return try {
            val uid = auth.currentUser?.uid
                ?: return Result.failure(Exception(ErrorMessages.USER_NOT_AUTHENTICATED))

            val doc = firestore.collection("users").document(uid)
                .collection("wrapped").document(year.toString()).get().await()

            if (doc.exists()) {
                val cached = parseWrappedSummary(doc.data, uid, year)
                if (cached != null) return Result.success(cached)
            }

            // No cached summary — generate a fresh one.
            generateWrappedSummary(year)
        } catch (e: Exception) {
            Logger.e(TAG, "getWrappedSummary failed", e)
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    override suspend fun generateWrappedSummary(year: Int): Result<WrappedSummary> {
        return try {
            val uid = auth.currentUser?.uid
                ?: return Result.failure(Exception(ErrorMessages.USER_NOT_AUTHENTICATED))

            // Compute the start and end timestamps for the given year.
            val (startOfYear, endOfYear) = yearBounds(year)

            // Fetch the user's currency for cross-group aggregation.
            val userDoc = firestore.collection("users").document(uid).get().await()
            val userCurrency = userDoc.getString("defaultCurrency") ?: AppConstants.BASE_CURRENCY

            // Find every group the user is an active member of.
            val memberSnapshot = firestore.collectionGroup("members")
                .whereEqualTo("uid", uid)
                .whereEqualTo("status", "active")
                .get().await()

            val groupIds = memberSnapshot.documents.mapNotNull { doc ->
                doc.reference.parent.parent?.id
            }.distinct()

            Logger.d(TAG, "Generating wrapped $year for $uid across ${groupIds.size} groups")

            // Accumulators
            var totalSpent = 0.0
            var totalPaid = 0.0
            var expenseCount = 0
            var largestExpense = 0.0
            var largestExpenseDesc = ""

            val categoryBreakdown = mutableMapOf<String, Double>()
            val monthlyBreakdown = mutableMapOf<Int, Double>()
            val groupBreakdown = mutableMapOf<String, Double>()

            for (groupId in groupIds) {
                val groupRef = firestore.collection("groups").document(groupId)

                // Read the group name and currency for breakdown tracking.
                val groupDoc = groupRef.get().await()
                val groupName = groupDoc.getString("name") ?: groupId
                val groupCurrency = groupDoc.getString("currency") ?: AppConstants.BASE_CURRENCY
                val groupToUserRate = exchangeRateService.getRate(groupCurrency, userCurrency).getOrDefault(1.0)

                // Read ALL expenses for the given year, ordered by date desc, limit 1000.
                val expenseSnapshot = groupRef.collection("expenses")
                    .whereGreaterThanOrEqualTo("date", startOfYear)
                    .whereLessThanOrEqualTo("date", endOfYear)
                    .orderBy("date", Query.Direction.DESCENDING)
                    .limit(1000)
                    .get().await()

                var groupTotal = 0.0

                for (doc in expenseSnapshot.documents) {
                    val data = doc.data ?: continue

                    // Only count "expense" types (not "income").
                    val transactionType = data["transactionType"] as? String ?: "expense"
                    if (transactionType != "expense") continue

                    val rawAmount = (data["amount"] as? Number)?.toDouble() ?: 0.0
                    val groupAmount = (data["amountInGroupCurrency"] as? Number)?.toDouble() ?: rawAmount
                    val amountInUserCurrency = groupAmount * groupToUserRate
                    val paidBy = data["paidBy"] as? String ?: ""
                    val date = (data["date"] as? Number)?.toLong() ?: 0L
                    val category = (data["category"] as? String)?.takeIf { it.isNotBlank() } ?: "uncategorized"
                    val description = data["description"] as? String ?: ""

                    @Suppress("UNCHECKED_CAST")
                    val splits = (data["splits"] as? Map<String, Map<String, Any>>)?.mapValues { (_, v) ->
                        SplitEntry(
                            amount = (v["amount"] as? Number)?.toDouble() ?: 0.0,
                            shareValue = (v["shareValue"] as? Number)?.toDouble() ?: 0.0
                        )
                    } ?: emptyMap()

                    // The user's split share for this expense, converted to user currency.
                    val splitRatio = if (rawAmount != 0.0) groupAmount / rawAmount else 1.0
                    val userShare = (splits[uid]?.amount ?: 0.0) * splitRatio * groupToUserRate

                    expenseCount++
                    totalSpent += userShare
                    groupTotal += userShare

                    if (paidBy == uid) {
                        totalPaid += amountInUserCurrency
                    }

                    // Track largest expense the user was involved in.
                    if (userShare > 0 && amountInUserCurrency > largestExpense) {
                        largestExpense = amountInUserCurrency
                        largestExpenseDesc = description
                    }

                    // Category breakdown.
                    categoryBreakdown[category] = (categoryBreakdown[category] ?: 0.0) + userShare

                    // Monthly breakdown.
                    val month = monthFromTimestamp(date)
                    monthlyBreakdown[month] = (monthlyBreakdown[month] ?: 0.0) + userShare
                }

                // Group breakdown.
                if (groupTotal > 0) {
                    groupBreakdown[groupName] = groupTotal
                }
            }

            // Derived metrics.
            val totalOwed = totalPaid - totalSpent
            val avgExpense = if (expenseCount > 0) totalSpent / expenseCount else 0.0
            val groupCount = groupIds.size

            // Top category.
            val topCategoryEntry = categoryBreakdown.maxByOrNull { it.value }
            val topCategory = topCategoryEntry?.key ?: "uncategorized"
            val topCategoryAmount = topCategoryEntry?.value ?: 0.0

            // Top group.
            val topGroupEntry = groupBreakdown.maxByOrNull { it.value }
            val topGroup = topGroupEntry?.key ?: ""
            val topGroupAmount = topGroupEntry?.value ?: 0.0

            // Busiest month.
            val busiestMonthEntry = monthlyBreakdown.maxByOrNull { it.value }
            val busiestMonth = busiestMonthEntry?.key ?: 0
            val busiestMonthAmount = busiestMonthEntry?.value ?: 0.0

            // Determine personality based on spending patterns.
            val (personality, personalityDesc) = determinePersonality(
                totalPaid = totalPaid,
                totalSpent = totalSpent,
                expenseCount = expenseCount,
                largestExpense = largestExpense,
                groupCount = groupCount
            )

            val summary = WrappedSummary(
                uid = uid,
                year = year,
                totalSpent = totalSpent,
                totalPaid = totalPaid,
                totalOwed = totalOwed,
                expenseCount = expenseCount,
                groupCount = groupCount,
                topCategory = topCategory,
                topCategoryAmount = topCategoryAmount,
                topGroup = topGroup,
                topGroupAmount = topGroupAmount,
                busiestMonth = busiestMonth,
                busiestMonthAmount = busiestMonthAmount,
                avgExpense = avgExpense,
                largestExpense = largestExpense,
                largestExpenseDesc = largestExpenseDesc,
                personality = personality,
                personalityDesc = personalityDesc,
                categoryBreakdown = categoryBreakdown.toMap(),
                monthlyBreakdown = monthlyBreakdown.toMap(),
                groupBreakdown = groupBreakdown.toMap(),
                generatedAt = System.currentTimeMillis()
            )

            // Persist the summary.
            val summaryData = mapOf(
                "uid" to uid,
                "year" to year,
                "totalSpent" to totalSpent,
                "totalPaid" to totalPaid,
                "totalOwed" to totalOwed,
                "expenseCount" to expenseCount,
                "groupCount" to groupCount,
                "topCategory" to topCategory,
                "topCategoryAmount" to topCategoryAmount,
                "topGroup" to topGroup,
                "topGroupAmount" to topGroupAmount,
                "busiestMonth" to busiestMonth,
                "busiestMonthAmount" to busiestMonthAmount,
                "avgExpense" to avgExpense,
                "largestExpense" to largestExpense,
                "largestExpenseDesc" to largestExpenseDesc,
                "personality" to personality,
                "personalityDesc" to personalityDesc,
                "categoryBreakdown" to categoryBreakdown,
                "monthlyBreakdown" to monthlyBreakdown,
                "groupBreakdown" to groupBreakdown,
                "generatedAt" to summary.generatedAt
            )

            firestore.collection("users").document(uid)
                .collection("wrapped").document(year.toString())
                .set(summaryData).await()

            Logger.i(TAG, "Wrapped $year generated for $uid: spent=$totalSpent expenses=$expenseCount")

            Result.success(summary)
        } catch (e: Exception) {
            Logger.e(TAG, "generateWrappedSummary failed", e)
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    override suspend fun getMonthlyRecap(year: Int, month: Int): Result<MonthlyRecap> {
        return try {
            val uid = auth.currentUser?.uid
                ?: return Result.failure(Exception(ErrorMessages.USER_NOT_AUTHENTICATED))

            val docId = "${year}_${month}"
            val doc = firestore.collection("users").document(uid)
                .collection("wrapped").document(docId).get().await()

            if (doc.exists()) {
                val cached = parseMonthlyRecap(doc.data, uid, year, month)
                if (cached != null) return Result.success(cached)
            }

            // No cached recap — generate a fresh one.
            generateMonthlyRecap(year, month)
        } catch (e: Exception) {
            Logger.e(TAG, "getMonthlyRecap failed", e)
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    override suspend fun generateMonthlyRecap(year: Int, month: Int): Result<MonthlyRecap> {
        return try {
            val uid = auth.currentUser?.uid
                ?: return Result.failure(Exception(ErrorMessages.USER_NOT_AUTHENTICATED))

            // Compute the start and end timestamps for the given month.
            val (startOfMonth, endOfMonth) = monthBounds(year, month)

            // Fetch the user's currency for cross-group aggregation.
            val userDoc = firestore.collection("users").document(uid).get().await()
            val userCurrency = userDoc.getString("defaultCurrency") ?: AppConstants.BASE_CURRENCY

            // Find every group the user is an active member of.
            val memberSnapshot = firestore.collectionGroup("members")
                .whereEqualTo("uid", uid)
                .whereEqualTo("status", "active")
                .get().await()

            val groupIds = memberSnapshot.documents.mapNotNull { doc ->
                doc.reference.parent.parent?.id
            }.distinct()

            Logger.d(TAG, "Generating monthly recap $year-$month for $uid across ${groupIds.size} groups")

            // Accumulators
            var totalSpent = 0.0
            var expenseCount = 0
            var largestExpense = 0.0

            val categoryBreakdown = mutableMapOf<String, Double>()
            val groupBreakdown = mutableMapOf<String, Double>()

            for (groupId in groupIds) {
                val groupRef = firestore.collection("groups").document(groupId)

                // Read the group name and currency for breakdown tracking.
                val groupDoc = groupRef.get().await()
                val groupName = groupDoc.getString("name") ?: groupId
                val groupCurrency = groupDoc.getString("currency") ?: AppConstants.BASE_CURRENCY
                val groupToUserRate = exchangeRateService.getRate(groupCurrency, userCurrency).getOrDefault(1.0)

                // Read ALL expenses for the given month, ordered by date desc, limit 1000.
                val expenseSnapshot = groupRef.collection("expenses")
                    .whereGreaterThanOrEqualTo("date", startOfMonth)
                    .whereLessThanOrEqualTo("date", endOfMonth)
                    .orderBy("date", Query.Direction.DESCENDING)
                    .limit(1000)
                    .get().await()

                var groupTotal = 0.0

                for (doc in expenseSnapshot.documents) {
                    val data = doc.data ?: continue

                    // Only count "expense" types (not "income").
                    val transactionType = data["transactionType"] as? String ?: "expense"
                    if (transactionType != "expense") continue

                    val rawAmount = (data["amount"] as? Number)?.toDouble() ?: 0.0
                    val groupAmount = (data["amountInGroupCurrency"] as? Number)?.toDouble() ?: rawAmount
                    val amountInUserCurrency = groupAmount * groupToUserRate
                    val category = (data["category"] as? String)?.takeIf { it.isNotBlank() } ?: "uncategorized"

                    @Suppress("UNCHECKED_CAST")
                    val splits = (data["splits"] as? Map<String, Map<String, Any>>)?.mapValues { (_, v) ->
                        SplitEntry(
                            amount = (v["amount"] as? Number)?.toDouble() ?: 0.0,
                            shareValue = (v["shareValue"] as? Number)?.toDouble() ?: 0.0
                        )
                    } ?: emptyMap()

                    // The user's split share for this expense, converted to user currency.
                    val splitRatio = if (rawAmount != 0.0) groupAmount / rawAmount else 1.0
                    val userShare = (splits[uid]?.amount ?: 0.0) * splitRatio * groupToUserRate

                    expenseCount++
                    totalSpent += userShare
                    groupTotal += userShare

                    // Track largest expense the user was involved in.
                    if (userShare > 0 && amountInUserCurrency > largestExpense) {
                        largestExpense = amountInUserCurrency
                    }

                    // Category breakdown.
                    categoryBreakdown[category] = (categoryBreakdown[category] ?: 0.0) + userShare
                }

                // Group breakdown.
                if (groupTotal > 0) {
                    groupBreakdown[groupName] = groupTotal
                }
            }

            // Top category.
            val topCategoryEntry = categoryBreakdown.maxByOrNull { it.value }
            val topCategory = topCategoryEntry?.key ?: "uncategorized"
            val topCategoryAmount = topCategoryEntry?.value ?: 0.0

            // Top group.
            val topGroupEntry = groupBreakdown.maxByOrNull { it.value }
            val topGroup = topGroupEntry?.key ?: ""
            val topGroupAmount = topGroupEntry?.value ?: 0.0

            val groupCount = groupIds.size

            // Determine personality based on spending patterns (scaled for monthly data).
            val (personality, personalityDesc) = determinePersonality(
                totalPaid = totalSpent, // Monthly recap doesn't track paid separately; use spent as proxy.
                totalSpent = totalSpent,
                expenseCount = expenseCount,
                largestExpense = largestExpense,
                groupCount = groupCount
            )

            val recap = MonthlyRecap(
                uid = uid,
                year = year,
                month = month,
                totalSpent = totalSpent,
                expenseCount = expenseCount,
                topCategory = topCategory,
                topCategoryAmount = topCategoryAmount,
                topGroup = topGroup,
                topGroupAmount = topGroupAmount,
                personality = personality,
                personalityDesc = personalityDesc,
                generatedAt = System.currentTimeMillis()
            )

            // Persist the recap.
            val recapData = mapOf(
                "uid" to uid,
                "year" to year,
                "month" to month,
                "totalSpent" to totalSpent,
                "expenseCount" to expenseCount,
                "topCategory" to topCategory,
                "topCategoryAmount" to topCategoryAmount,
                "topGroup" to topGroup,
                "topGroupAmount" to topGroupAmount,
                "personality" to personality,
                "personalityDesc" to personalityDesc,
                "generatedAt" to recap.generatedAt
            )

            firestore.collection("users").document(uid)
                .collection("wrapped").document("${year}_${month}")
                .set(recapData).await()

            Logger.i(TAG, "Monthly recap $year-$month generated for $uid: spent=$totalSpent expenses=$expenseCount")

            Result.success(recap)
        } catch (e: Exception) {
            Logger.e(TAG, "generateMonthlyRecap failed", e)
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────

    /** Returns the start and end timestamps (inclusive) for the given year. */
    private fun yearBounds(year: Int): Pair<Long, Long> {
        val start = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, Calendar.JANUARY)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val end = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, Calendar.DECEMBER)
            set(Calendar.DAY_OF_MONTH, 31)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis

        return Pair(start, end)
    }

    /** Returns the start and end timestamps (inclusive) for the given month. */
    private fun monthBounds(year: Int, month: Int): Pair<Long, Long> {
        val start = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1) // Calendar months are 0-indexed.
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val end = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis

        return Pair(start, end)
    }

    /** Extracts the 1-indexed month from a timestamp. */
    private fun monthFromTimestamp(timestamp: Long): Int {
        return Calendar.getInstance().apply {
            timeInMillis = timestamp
        }.get(Calendar.MONTH) + 1
    }

    /**
     * Determines the user's spending personality based on their patterns.
     * Returns a (personality, description) pair.
     */
    private fun determinePersonality(
        totalPaid: Double,
        totalSpent: Double,
        expenseCount: Int,
        largestExpense: Double,
        groupCount: Int
    ): Pair<String, String> {
        return when {
            totalPaid > totalSpent * 1.5 ->
                Pair("The Generous One", "You fronted the bill more often than not. Your friends appreciate you!")
            expenseCount > 100 ->
                Pair("The Active Splitter", "You're always splitting bills. Consistency is your superpower!")
            largestExpense > totalSpent * 0.3 ->
                Pair("The Big Spender", "You're not afraid of big expenses. You make things happen!")
            groupCount > 5 ->
                Pair("The Social Butterfly", "You're in many groups, splitting bills with everyone.")
            else ->
                Pair("The Steady Splitter", "You keep it balanced and steady. Reliable and fair.")
        }
    }

    /** Parses a Firestore document into a [WrappedSummary]. */
    @Suppress("UNCHECKED_CAST")
    private fun parseWrappedSummary(
        data: Map<String, Any>?,
        uid: String,
        year: Int
    ): WrappedSummary? {
        if (data == null) return null

        val categoryBreakdown = (data["categoryBreakdown"] as? Map<String, Any>)
            ?.mapValues { (_, v) -> (v as? Number)?.toDouble() ?: 0.0 }
            ?: emptyMap()

        val monthlyBreakdown = (data["monthlyBreakdown"] as? Map<String, Any>)
            ?.mapKeys { it.key.toIntOrNull() ?: 0 }
            ?.mapValues { (_, v) -> (v as? Number)?.toDouble() ?: 0.0 }
            ?: emptyMap()

        val groupBreakdown = (data["groupBreakdown"] as? Map<String, Any>)
            ?.mapValues { (_, v) -> (v as? Number)?.toDouble() ?: 0.0 }
            ?: emptyMap()

        return WrappedSummary(
            uid = data["uid"] as? String ?: uid,
            year = (data["year"] as? Number)?.toInt() ?: year,
            totalSpent = (data["totalSpent"] as? Number)?.toDouble() ?: 0.0,
            totalPaid = (data["totalPaid"] as? Number)?.toDouble() ?: 0.0,
            totalOwed = (data["totalOwed"] as? Number)?.toDouble() ?: 0.0,
            expenseCount = (data["expenseCount"] as? Number)?.toInt() ?: 0,
            groupCount = (data["groupCount"] as? Number)?.toInt() ?: 0,
            topCategory = data["topCategory"] as? String ?: "",
            topCategoryAmount = (data["topCategoryAmount"] as? Number)?.toDouble() ?: 0.0,
            topGroup = data["topGroup"] as? String ?: "",
            topGroupAmount = (data["topGroupAmount"] as? Number)?.toDouble() ?: 0.0,
            busiestMonth = (data["busiestMonth"] as? Number)?.toInt() ?: 0,
            busiestMonthAmount = (data["busiestMonthAmount"] as? Number)?.toDouble() ?: 0.0,
            avgExpense = (data["avgExpense"] as? Number)?.toDouble() ?: 0.0,
            largestExpense = (data["largestExpense"] as? Number)?.toDouble() ?: 0.0,
            largestExpenseDesc = data["largestExpenseDesc"] as? String ?: "",
            personality = data["personality"] as? String ?: "",
            personalityDesc = data["personalityDesc"] as? String ?: "",
            categoryBreakdown = categoryBreakdown,
            monthlyBreakdown = monthlyBreakdown,
            groupBreakdown = groupBreakdown,
            generatedAt = (data["generatedAt"] as? Number)?.toLong() ?: 0
        )
    }

    /** Parses a Firestore document into a [MonthlyRecap]. */
    private fun parseMonthlyRecap(
        data: Map<String, Any>?,
        uid: String,
        year: Int,
        month: Int
    ): MonthlyRecap? {
        if (data == null) return null

        return MonthlyRecap(
            uid = data["uid"] as? String ?: uid,
            year = (data["year"] as? Number)?.toInt() ?: year,
            month = (data["month"] as? Number)?.toInt() ?: month,
            totalSpent = (data["totalSpent"] as? Number)?.toDouble() ?: 0.0,
            expenseCount = (data["expenseCount"] as? Number)?.toInt() ?: 0,
            topCategory = data["topCategory"] as? String ?: "",
            topCategoryAmount = (data["topCategoryAmount"] as? Number)?.toDouble() ?: 0.0,
            topGroup = data["topGroup"] as? String ?: "",
            topGroupAmount = (data["topGroupAmount"] as? Number)?.toDouble() ?: 0.0,
            personality = data["personality"] as? String ?: "",
            personalityDesc = data["personalityDesc"] as? String ?: "",
            generatedAt = (data["generatedAt"] as? Number)?.toLong() ?: 0
        )
    }
}
