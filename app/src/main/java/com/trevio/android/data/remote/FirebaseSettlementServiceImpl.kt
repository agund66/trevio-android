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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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

            val (fromUserDoc, fromMemberDoc, toUserDoc, toMemberDoc) = coroutineScope {
                val fromUserAsync = async { firestore.collection("users").document(fromUid).get().await() }
                val fromMemberAsync = async { groupRef.collection("members").document(fromUid).get().await() }
                val toUserAsync = async { firestore.collection("users").document(toUid).get().await() }
                val toMemberAsync = async { groupRef.collection("members").document(toUid).get().await() }
                listOf(fromUserAsync.await(), fromMemberAsync.await(), toUserAsync.await(), toMemberAsync.await())
            }
            val fromIsOffline = fromMemberDoc.data?.get("isOffline") as? Boolean ?: false
            val fromUserName = if (fromIsOffline) {
                fromMemberDoc.data?.get("displayName") as? String ?: "Someone"
            } else {
                fromUserDoc.data?.get("displayName") as? String ?: "Someone"
            }
            val toIsOffline = toMemberDoc.data?.get("isOffline") as? Boolean ?: false
            val toUserName = if (toIsOffline) {
                toMemberDoc.data?.get("displayName") as? String ?: "Someone"
            } else {
                toUserDoc.data?.get("displayName") as? String ?: "Someone"
            }

            val batch = firestore.batch()
            batch.set(settlementRef, settlementData)
            batch.set(groupRef.collection("activities").document(), mapOf(
                "type" to "settlement_added",
                "description" to "$fromUserName settled $currency $amount with $toUserName",
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

            // Notify the receiver and/or payer (non-blocking — don't fail settlement creation if notification fails)
            // Skip self-notification: don't notify the user who is recording the settlement
            try {
                val groupName = groupDoc.data?.get("name") as? String ?: ""
                val notifyUids = mutableListOf<String>()
                if (toUid != uid) notifyUids.add(toUid)
                if (fromUid != uid && toUid !in notifyUids) notifyUids.add(fromUid)

                for (notifyUid in notifyUids) {
                    val isReceiver = notifyUid == toUid
                    firestore.collection("users").document(notifyUid).collection("notifications").document()
                        .set(mapOf(
                            "type" to "settlement",
                            "title" to if (isReceiver) "Payment Received" else "Payment Recorded",
                            "body" to if (isReceiver)
                                "$fromUserName recorded a payment of $currency $amount to you"
                            else
                                "You paid $toUserName $currency $amount (recorded by $fromUserName)",
                            "data" to mapOf(
                                "groupId" to groupId,
                                "groupName" to groupName,
                                "settlementId" to settlementRef.id,
                                "type" to "settlement"
                            ),
                            "read" to false,
                            "createdAt" to now
                        )).await()
                }
            } catch (notifError: Exception) {
                android.util.Log.w("FirebaseSettlementService", "Failed to send settlement notification", notifError)
            }

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

            val allUids = debts.flatMap { listOf(it.fromUid, it.toUid) }.filter { it.isNotEmpty() }.distinct()

            val (memberDocs, userDocs) = coroutineScope {
                val members = allUids.associateWith { async { groupRef.collection("members").document(it).get().await() } }
                    .mapValues { it.value.await() }
                val users = allUids.associateWith { async { firestore.collection("users").document(it).get().await() } }
                    .mapValues { it.value.await() }
                members to users
            }

            val memberMap = mutableMapOf<String, Map<String, Any>?>()
            memberDocs.forEach { (uid, doc) -> memberMap[uid] = doc.data }
            val userMap = mutableMapOf<String, Map<String, Any>?>()
            userDocs.forEach { (uid, doc) -> userMap[uid] = doc.data }

            val enrichedDebts = debts.map { debt ->
                val fromMemberData = memberMap[debt.fromUid]
                val toMemberData = memberMap[debt.toUid]
                val fromIsOffline = fromMemberData?.get("isOffline") as? Boolean ?: false
                val toIsOffline = toMemberData?.get("isOffline") as? Boolean ?: false

                val fromName: String
                val fromPhotoURL: String
                val fromUpiId: String
                if (fromIsOffline) {
                    fromName = fromMemberData?.get("displayName") as? String ?: "Unknown"
                    fromPhotoURL = ""
                    fromUpiId = ""
                } else {
                    val fromData = userMap[debt.fromUid]
                    fromName = fromData?.get("displayName") as? String ?: "Unknown"
                    fromPhotoURL = fromData?.get("photoURL") as? String ?: ""
                    fromUpiId = fromData?.get("upiId") as? String ?: ""
                }

                val toName: String
                val toPhotoURL: String
                val toUpiId: String
                val toPhoneNumber: String
                val toCountryCode: String
                if (toIsOffline) {
                    toName = toMemberData?.get("displayName") as? String ?: "Unknown"
                    toPhotoURL = ""
                    toUpiId = ""
                    toPhoneNumber = ""
                    toCountryCode = ""
                } else {
                    val toData = userMap[debt.toUid]
                    toName = toData?.get("displayName") as? String ?: "Unknown"
                    toPhotoURL = toData?.get("photoURL") as? String ?: ""
                    toUpiId = toData?.get("upiId") as? String ?: ""
                    toPhoneNumber = toData?.get("phoneNumber") as? String ?: ""
                    toCountryCode = toData?.get("countryCode") as? String ?: ""
                }

                SimplifiedDebt(
                    fromUid = debt.fromUid,
                    toUid = debt.toUid,
                    fromName = fromName,
                    toName = toName,
                    fromPhotoURL = fromPhotoURL,
                    toPhotoURL = toPhotoURL,
                    toUpiId = toUpiId,
                    fromUpiId = fromUpiId,
                    toPhoneNumber = toPhoneNumber,
                    toCountryCode = toCountryCode,
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

            val onlineMemberIds = membersSnapshot.documents
                .filter { (it.data ?: emptyMap())["isOffline"] as? Boolean != true }
                .map { it.id }

            val userDocs = coroutineScope {
                onlineMemberIds.associateWith { uid ->
                    async { firestore.collection("users").document(uid).get().await() }
                }.mapValues { it.value.await() }
            }
            val userMap = mutableMapOf<String, Map<String, Any>?>()
            userDocs.forEach { (uid, doc) -> userMap[uid] = doc.data }

            val members = membersSnapshot.documents.mapNotNull { doc ->
                val data = doc.data ?: return@mapNotNull null
                val isOffline = data["isOffline"] as? Boolean ?: false
                if (isOffline) {
                    Member(
                        uid = doc.id,
                        displayName = data["displayName"] as? String ?: "Unknown",
                        username = "",
                        photoURL = "",
                        balance = (data["balance"] as? Number)?.toDouble() ?: 0.0,
                        role = data["role"] as? String ?: "member",
                        status = data["status"] as? String ?: "active",
                        isOffline = true
                    )
                } else {
                    val userData = userMap[doc.id]
                    Member(
                        uid = doc.id,
                        displayName = userData?.get("displayName") as? String ?: "Unknown",
                        username = userData?.get("username") as? String ?: "",
                        photoURL = userData?.get("photoURL") as? String ?: "",
                        balance = (data["balance"] as? Number)?.toDouble() ?: 0.0,
                        role = data["role"] as? String ?: "member",
                        status = data["status"] as? String ?: "active",
                        isOffline = false
                    )
                }
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

            val allUids = snapshot.documents.flatMap { doc ->
                val data = doc.data ?: emptyMap()
                listOf(data["fromUid"] as? String ?: "", data["toUid"] as? String ?: "")
            }.filter { it.isNotEmpty() }.distinct()

            val (memberDocs, userDocs) = coroutineScope {
                val members = allUids.associateWith { async { groupRef.collection("members").document(it).get().await() } }
                    .mapValues { it.value.await() }
                val users = allUids.associateWith { async { firestore.collection("users").document(it).get().await() } }
                    .mapValues { it.value.await() }
                members to users
            }

            val memberMap = mutableMapOf<String, Map<String, Any>?>()
            memberDocs.forEach { (uid, doc) -> memberMap[uid] = doc.data }
            val userMap = mutableMapOf<String, Map<String, Any>?>()
            userDocs.forEach { (uid, doc) -> userMap[uid] = doc.data }

            val settlements = snapshot.documents.map { doc ->
                val data = doc.data ?: emptyMap()
                val fromUid = data["fromUid"] as? String ?: ""
                val toUid = data["toUid"] as? String ?: ""

                val fromMemberData = memberMap[fromUid]
                val toMemberData = memberMap[toUid]
                val fromIsOffline = fromMemberData?.get("isOffline") as? Boolean ?: false
                val toIsOffline = toMemberData?.get("isOffline") as? Boolean ?: false

                val fromName = if (fromIsOffline) {
                    fromMemberData?.get("displayName") as? String ?: "Unknown"
                } else {
                    userMap[fromUid]?.get("displayName") as? String ?: "Unknown"
                }
                val toName = if (toIsOffline) {
                    toMemberData?.get("displayName") as? String ?: "Unknown"
                } else {
                    userMap[toUid]?.get("displayName") as? String ?: "Unknown"
                }

                Settlement(
                    settlementId = doc.id,
                    fromUid = fromUid,
                    toUid = toUid,
                    fromName = fromName,
                    toName = toName,
                    amount = (data["amount"] as? Number)?.toDouble() ?: 0.0,
                    currency = data["currency"] as? String ?: "INR",
                    method = SettlementMethod.valueOf((data["method"] as? String ?: "cash").uppercase()),
                    upiRefId = data["upiRefId"] as? String ?: "",
                    date = (data["date"] as? Number)?.toLong() ?: 0,
                    createdBy = data["createdBy"] as? String ?: ""
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
