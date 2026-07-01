package com.trevio.android.data.remote

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.trevio.android.domain.model.Member
import com.trevio.android.domain.model.Settlement
import com.trevio.android.domain.model.SettlementMethod
import com.trevio.android.domain.model.SimplifiedDebt
import com.trevio.android.domain.model.SplitEntry
import com.trevio.android.domain.repository.SettlementService
import com.trevio.android.domain.repository.ExchangeRateService
import com.trevio.android.util.Calculations
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseSettlementServiceImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val exchangeRateService: ExchangeRateService
) : SettlementService {

    override suspend fun addSettlement(
        groupId: String,
        fromUid: String,
        toUid: String,
        amount: Double,
        currency: String,
        method: SettlementMethod,
        upiRefId: String?
    ): Result<String> {
        return try {
            val uid = auth.currentUser?.uid ?: return Result.failure(Exception("User not authenticated"))
            if (groupId.isBlank() || fromUid.isBlank() || toUid.isBlank() || amount <= 0.0) {
                return Result.failure(Exception("Missing required fields"))
            }
            if (fromUid == toUid) return Result.failure(Exception("Cannot settle with yourself"))

            val groupRef = firestore.collection("groups").document(groupId)
            val groupDoc = groupRef.get().await()
            if (!groupDoc.exists()) return Result.failure(Exception("Group not found"))

            val memberDoc = groupRef.collection("members").document(uid).get().await()
            if (!memberDoc.exists()) return Result.failure(Exception("You are not a member of this group"))

            val rateToBase = exchangeRateService.getRateToBase(currency).getOrDefault(1.0)
            val amountInBase = kotlin.math.round(amount * rateToBase * 100) / 100

            val now = System.currentTimeMillis()
            val settlementRef = groupRef.collection("settlements").document()

            val settlementData = mutableMapOf<String, Any>(
                "fromUid" to fromUid,
                "toUid" to toUid,
                "amount" to amountInBase,
                "currency" to "INR",
                "originalAmount" to amount,
                "originalCurrency" to currency,
                "method" to method.name.lowercase(),
                "date" to now,
                "createdBy" to uid,
                "createdAt" to now
            )
            if (upiRefId != null) settlementData["upiRefId"] = upiRefId

            val fromUserDoc = firestore.collection("users").document(fromUid).get().await()
            val fromUserName = fromUserDoc.data?.get("displayName") as? String ?: "Someone"
            val toUserDoc = firestore.collection("users").document(toUid).get().await()
            val toUserName = toUserDoc.data?.get("displayName") as? String ?: "Someone"

            val batch = firestore.batch()
            batch.set(settlementRef, settlementData)
            batch.set(groupRef.collection("activities").document(), mapOf(
                "type" to "settlement_added",
                "description" to "$fromUserName settled ₹$amountInBase with $toUserName",
                "userId" to uid,
                "data" to mapOf(
                    "settlementId" to settlementRef.id,
                    "fromUid" to fromUid,
                    "toUid" to toUid,
                    "amount" to amountInBase
                ),
                "createdAt" to now
            ))
            batch.commit().await()

            recalculateBalances(groupId)

            firestore.collection("users").document(toUid).collection("notifications").document()
                .set(mapOf(
                    "type" to "settlement",
                    "title" to "Payment Received",
                    "body" to "$fromUserName recorded a payment of ₹$amountInBase to you",
                    "data" to mapOf(
                        "groupId" to groupId,
                        "groupName" to (groupDoc.data?.get("name") as? String ?: ""),
                        "settlementId" to settlementRef.id,
                        "type" to "settlement"
                    ),
                    "read" to false,
                    "createdAt" to now
                )).await()

            Result.success(settlementRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getSimplifiedDebts(groupId: String): Result<List<SimplifiedDebt>> {
        return try {
            val uid = auth.currentUser?.uid ?: return Result.failure(Exception("User not authenticated"))
            val groupRef = firestore.collection("groups").document(groupId)
            val memberDoc = groupRef.collection("members").document(uid).get().await()
            if (!memberDoc.exists()) return Result.failure(Exception("You are not a member of this group"))

            val debts = calculateSimplifiedDebts(groupId)

            val enrichedDebts = debts.map { debt ->
                val fromDoc = firestore.collection("users").document(debt.fromUid).get().await()
                val toDoc = firestore.collection("users").document(debt.toUid).get().await()
                val fromData = fromDoc.data
                val toData = toDoc.data
                SimplifiedDebt(
                    fromUid = debt.fromUid,
                    toUid = debt.toUid,
                    fromName = fromData?.get("displayName") as? String ?: "Unknown",
                    toName = toData?.get("displayName") as? String ?: "Unknown",
                    fromPhotoURL = fromData?.get("photoURL") as? String ?: "",
                    toPhotoURL = toData?.get("photoURL") as? String ?: "",
                    toUpiId = toData?.get("upiId") as? String ?: "",
                    fromUpiId = fromData?.get("upiId") as? String ?: "",
                    toPhoneNumber = toData?.get("phoneNumber") as? String ?: "",
                    toCountryCode = toData?.get("countryCode") as? String ?: "",
                    amount = debt.amount
                )
            }
            Result.success(enrichedDebts)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getGroupBalances(groupId: String): Result<List<Member>> {
        return try {
            val uid = auth.currentUser?.uid ?: return Result.failure(Exception("User not authenticated"))
            val groupRef = firestore.collection("groups").document(groupId)
            val memberDoc = groupRef.collection("members").document(uid).get().await()
            if (!memberDoc.exists()) return Result.failure(Exception("You are not a member of this group"))

            val membersSnapshot = groupRef.collection("members")
                .whereIn("status", listOf("active", "pending"))
                .get().await()

            val members = membersSnapshot.documents.mapNotNull { doc ->
                val data = doc.data ?: return@mapNotNull null
                val userDoc = firestore.collection("users").document(doc.id).get().await()
                val userData = userDoc.data
                Member(
                    uid = doc.id,
                    displayName = userData?.get("displayName") as? String ?: "Unknown",
                    username = userData?.get("username") as? String ?: "",
                    photoURL = userData?.get("photoURL") as? String ?: "",
                    balance = (data["balance"] as? Number)?.toDouble() ?: 0.0,
                    role = data["role"] as? String ?: "member",
                    status = data["status"] as? String ?: "active"
                )
            }
            Result.success(members)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getSettlementHistory(groupId: String): Result<List<Settlement>> {
        return try {
            val uid = auth.currentUser?.uid ?: return Result.failure(Exception("User not authenticated"))
            val groupRef = firestore.collection("groups").document(groupId)
            val memberDoc = groupRef.collection("members").document(uid).get().await()
            if (!memberDoc.exists()) return Result.failure(Exception("You are not a member of this group"))

            val snapshot = groupRef.collection("settlements")
                .orderBy("date", Query.Direction.DESCENDING)
                .limit(50)
                .get().await()

            val settlements = snapshot.documents.map { doc ->
                val data = doc.data ?: emptyMap()
                val fromDoc = firestore.collection("users").document(data["fromUid"] as? String ?: "").get().await()
                val toDoc = firestore.collection("users").document(data["toUid"] as? String ?: "").get().await()
                Settlement(
                    settlementId = doc.id,
                    fromUid = data["fromUid"] as? String ?: "",
                    toUid = data["toUid"] as? String ?: "",
                    fromName = fromDoc.data?.get("displayName") as? String ?: "Unknown",
                    toName = toDoc.data?.get("displayName") as? String ?: "Unknown",
                    amount = (data["amount"] as? Number)?.toDouble() ?: 0.0,
                    currency = data["currency"] as? String ?: "INR",
                    method = SettlementMethod.valueOf((data["method"] as? String ?: "cash").uppercase()),
                    upiRefId = data["upiRefId"] as? String ?: ""
                )
            }
            Result.success(settlements)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun calculateSimplifiedDebts(groupId: String): List<Calculations.SimplifiedDebtRaw> {
        val groupRef = firestore.collection("groups").document(groupId)

        val expensesSnapshot = groupRef.collection("expenses").get().await()
        val settlementsSnapshot = groupRef.collection("settlements").get().await()
        val membersSnapshot = groupRef.collection("members").whereEqualTo("status", "active").get().await()

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
        return Calculations.simplifyDebts(balances)
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

        val batch = firestore.batch()
        balances.forEach { (memberUid, balance) ->
            val roundedBalance = kotlin.math.round(balance * 100) / 100
            batch.update(groupRef.collection("members").document(memberUid), mapOf("balance" to roundedBalance))
        }
        batch.commit().await()
    }
}
