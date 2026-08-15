package com.trevio.android.data.remote

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.WriteBatch
import com.trevio.android.domain.model.Member
import com.trevio.android.domain.model.PaginatedResult
import com.trevio.android.domain.model.Settlement
import com.trevio.android.domain.model.SettlementMethod
import com.trevio.android.domain.model.SimplifiedDebt
import com.trevio.android.domain.model.SplitEntry
import com.trevio.android.domain.repository.SettlementService
import com.trevio.android.domain.repository.ExchangeRateService
import com.trevio.android.util.AppConstants
import com.trevio.android.util.Calculations
import com.trevio.android.util.ErrorMessages
import com.trevio.android.util.Logger
import com.trevio.android.util.friendlyNetworkMessage
import com.trevio.android.util.toStorageString
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
            val uid = auth.currentUser?.uid ?: return Result.failure(Exception(ErrorMessages.USER_NOT_AUTHENTICATED))
            if (groupId.isBlank() || fromUid.isBlank() || toUid.isBlank() || amount <= 0.0) {
                return Result.failure(Exception("Missing required fields"))
            }
            if (fromUid == toUid) return Result.failure(Exception("Cannot settle with yourself"))
            if (uid != fromUid && uid != toUid) {
                return Result.failure(Exception("You can only record settlements involving yourself"))
            }

            val groupRef = firestore.collection("groups").document(groupId)
            val groupDoc = groupRef.get().await()
            if (!groupDoc.exists()) return Result.failure(Exception(ErrorMessages.GROUP_NOT_FOUND))

            val memberDoc = groupRef.collection("members").document(uid).get().await()
            if (!memberDoc.exists()) return Result.failure(Exception("You are not a member of this group"))

            val fromMember = groupRef.collection("members").document(fromUid).get().await()
            val toMember = groupRef.collection("members").document(toUid).get().await()
            if (!fromMember.exists() || !toMember.exists()) {
                return Result.failure(Exception("Both parties must be group members"))
            }

            val rateToBase = exchangeRateService.getRateToBase(currency).getOrDefault(1.0)
            val amountInBase = kotlin.math.round(amount * rateToBase * 100) / 100

            val now = System.currentTimeMillis()
            val settlementRef = groupRef.collection("settlements").document()

            // Fetch user/member docs for denormalized fromName/toName on settlement doc
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
            val fromUserPhotoURL = if (fromIsOffline) {
                fromMemberDoc.data?.get("photoURL") as? String ?: ""
            } else {
                fromUserDoc.data?.get("photoURL") as? String ?: ""
            }

            val settlementData = mutableMapOf<String, Any>(
                "fromUid" to fromUid,
                "toUid" to toUid,
                "fromName" to fromUserName,
                "toName" to toUserName,
                "amount" to amountInBase,
                "currency" to AppConstants.BASE_CURRENCY,
                "originalAmount" to amount,
                "originalCurrency" to currency,
                "method" to method.toStorageString(),
                "date" to now,
                "createdBy" to uid,
                "createdAt" to now
            )
            if (upiRefId != null) settlementData["upiRefId"] = upiRefId

            val batch = firestore.batch()
            batch.set(settlementRef, settlementData)
            batch.set(groupRef.collection("activities").document(), mapOf(
                "type" to "settlement_added",
                "description" to "$fromUserName settled $currency $amount with $toUserName",
                "userId" to uid,
                "userName" to (if (uid == fromUid) fromUserName else toUserName),
                "userPhotoURL" to (if (uid == fromUid) fromUserPhotoURL else (if (toIsOffline) (toMemberDoc.data?.get("photoURL") as? String ?: "") else (toUserDoc.data?.get("photoURL") as? String ?: ""))),
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

                val notifBatch = firestore.batch()
                for (notifyUid in notifyUids) {
                    val isReceiver = notifyUid == toUid
                    notifBatch.set(
                        firestore.collection("users").document(notifyUid).collection("notifications").document(),
                        mapOf(
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
                        )
                    )
                }
                notifBatch.commit().await()
            } catch (notifError: Exception) {
                Logger.w("FirebaseSettlementService", "Failed to send settlement notification", notifError)
            }

            Result.success(settlementRef.id)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    override suspend fun getSimplifiedDebts(groupId: String): Result<List<SimplifiedDebt>> {
        return try {
            val uid = auth.currentUser?.uid ?: return Result.failure(Exception(ErrorMessages.USER_NOT_AUTHENTICATED))
            val groupRef = firestore.collection("groups").document(groupId)
            val memberDoc = groupRef.collection("members").document(uid).get().await()
            if (!memberDoc.exists()) return Result.failure(Exception("You are not a member of this group"))

            // Read pre-computed simplifiedDebts from group doc (stored by recalculateBalances).
            // Fall back to client-side computation for older docs without this field.
            val groupDoc = groupRef.get().await()
            @Suppress("UNCHECKED_CAST")
            val storedDebts = groupDoc.get("simplifiedDebts") as? List<Map<String, Any>>
            val debts = if (storedDebts != null) {
                storedDebts.map { d ->
                    Calculations.SimplifiedDebtRaw(
                        fromUid = d["fromUid"] as? String ?: "",
                        toUid = d["toUid"] as? String ?: "",
                        amount = (d["amount"] as? Number)?.toDouble() ?: 0.0
                    )
                }
            } else {
                calculateSimplifiedDebts(groupId)
            }

            val allUids = debts.flatMap { listOf(it.fromUid, it.toUid) }.filter { it.isNotEmpty() }.distinct()

            // Fetch member docs (for denormalized displayName/photoURL) and
            // user docs (for upiId/phoneNumber/countryCode — not denormalized).
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

                // Use denormalized displayName/photoURL from member docs
                val fromName = fromMemberData?.get("displayName") as? String ?: "Unknown"
                val fromPhotoURL = fromMemberData?.get("photoURL") as? String ?: ""
                val toName = toMemberData?.get("displayName") as? String ?: "Unknown"
                val toPhotoURL = toMemberData?.get("photoURL") as? String ?: ""

                // upiId/phoneNumber/countryCode only available from user docs
                // (not denormalized — sensitive payment info)
                val fromUpiId = if (fromIsOffline) "" else userMap[debt.fromUid]?.get("upiId") as? String ?: ""
                val toUpiId = if (toIsOffline) "" else userMap[debt.toUid]?.get("upiId") as? String ?: ""
                val toPhoneNumber = if (toIsOffline) "" else userMap[debt.toUid]?.get("phoneNumber") as? String ?: ""
                val toCountryCode = if (toIsOffline) "" else userMap[debt.toUid]?.get("countryCode") as? String ?: ""

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
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    override suspend fun getGroupBalances(groupId: String): Result<List<Member>> {
        return try {
            val uid = auth.currentUser?.uid ?: return Result.failure(Exception(ErrorMessages.USER_NOT_AUTHENTICATED))
            val groupRef = firestore.collection("groups").document(groupId)
            val memberDoc = groupRef.collection("members").document(uid).get().await()
            if (!memberDoc.exists()) return Result.failure(Exception("You are not a member of this group"))

            val membersSnapshot = groupRef.collection("members")
                .whereIn("status", listOf("active", "pending"))
                .get().await()

            // Use denormalized fields from member docs — no user doc fetches
            val members = membersSnapshot.documents.mapNotNull { doc ->
                val data = doc.data ?: return@mapNotNull null
                Member(
                    uid = doc.id,
                    displayName = data["displayName"] as? String ?: "Unknown",
                    username = data["username"] as? String ?: "",
                    photoURL = data["photoURL"] as? String ?: "",
                    balance = (data["balance"] as? Number)?.toDouble() ?: 0.0,
                    role = data["role"] as? String ?: "member",
                    status = data["status"] as? String ?: "active",
                    isOffline = data["isOffline"] as? Boolean ?: false
                )
            }
            Result.success(members)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    override suspend fun getSettlementHistory(groupId: String, pageSize: Int, lastSettlementId: String?): Result<PaginatedResult<Settlement>> {
        return try {
            val uid = auth.currentUser?.uid ?: return Result.failure(Exception(ErrorMessages.USER_NOT_AUTHENTICATED))
            val groupRef = firestore.collection("groups").document(groupId)
            val memberDoc = groupRef.collection("members").document(uid).get().await()
            if (!memberDoc.exists()) return Result.failure(Exception("You are not a member of this group"))

            var query = groupRef.collection("settlements")
                .orderBy("date", Query.Direction.DESCENDING)
                .limit(pageSize.toLong())

            if (lastSettlementId != null) {
                val lastDoc = groupRef.collection("settlements").document(lastSettlementId).get().await()
                if (lastDoc.exists()) {
                    query = groupRef.collection("settlements")
                        .orderBy("date", Query.Direction.DESCENDING)
                        .startAfter(lastDoc)
                        .limit(pageSize.toLong())
                }
            }

            val snapshot = query.get().await()

            // Collect UIDs for fallback name resolution (older docs may not have fromName/toName)
            val allUids = snapshot.documents.flatMap { doc ->
                val data = doc.data ?: emptyMap()
                listOf(data["fromUid"] as? String ?: "", data["toUid"] as? String ?: "")
            }.filter { it.isNotEmpty() }.distinct()

            // Only fetch member docs for fallback — prefer denormalized fromName/toName
            val memberDocs = coroutineScope {
                allUids.associateWith { async { groupRef.collection("members").document(it).get().await() } }
                    .mapValues { it.value.await() }
            }
            val memberMap = mutableMapOf<String, Map<String, Any>?>()
            memberDocs.forEach { (uid, doc) -> memberMap[uid] = doc.data }

            val settlements = snapshot.documents.map { doc ->
                val data = doc.data ?: emptyMap()
                val fromUid = data["fromUid"] as? String ?: ""
                val toUid = data["toUid"] as? String ?: ""

                // Prefer denormalized fromName/toName from settlement doc,
                // fall back to member doc displayName for older docs
                val fromName = (data["fromName"] as? String)
                    ?: memberMap[fromUid]?.get("displayName") as? String
                    ?: "Unknown"
                val toName = (data["toName"] as? String)
                    ?: memberMap[toUid]?.get("displayName") as? String
                    ?: "Unknown"

                Settlement(
                    settlementId = doc.id,
                    fromUid = fromUid,
                    toUid = toUid,
                    fromName = fromName,
                    toName = toName,
                    amount = (data["amount"] as? Number)?.toDouble() ?: 0.0,
                    currency = data["currency"] as? String ?: AppConstants.BASE_CURRENCY,
                    method = SettlementMethod.valueOf((data["method"] as? String ?: "cash").uppercase()),
                    upiRefId = data["upiRefId"] as? String ?: "",
                    date = (data["date"] as? Number)?.toLong() ?: 0,
                    createdBy = data["createdBy"] as? String ?: ""
                )
            }
            Result.success(PaginatedResult(
                items = settlements,
                hasMore = snapshot.size() == pageSize,
                lastId = if (snapshot.size() > 0) snapshot.documents.last().id else null
            ))
        } catch (e: Exception) {
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
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

        // Compute simplified debts in the same pass and store on group doc
        val simplifiedDebts = Calculations.simplifyDebts(balances)
        val debtsForStorage = simplifiedDebts.map { d ->
            mapOf(
                "fromUid" to d.fromUid,
                "toUid" to d.toUid,
                "amount" to (kotlin.math.round(d.amount * 100) / 100)
            )
        }

        val balanceEntries = balances.entries.toList()
        // If there are no balance entries, still store simplifiedDebts on the group doc
        if (balanceEntries.isEmpty()) {
            val batch = firestore.batch()
            batch.update(groupRef, mapOf(
                "simplifiedDebts" to debtsForStorage,
                "updatedAt" to System.currentTimeMillis()
            ))
            batch.commit().await()
            return
        }
        val batchSize = 400
        for (i in balanceEntries.indices step batchSize) {
            val chunk = balanceEntries.subList(i, minOf(i + batchSize, balanceEntries.size))
            val batch = firestore.batch()
            for ((memberUid, balance) in chunk) {
                val roundedBalance = kotlin.math.round(balance * 100) / 100
                batch.update(groupRef.collection("members").document(memberUid), mapOf("balance" to roundedBalance))
            }
            if (i == 0) {
                batch.update(groupRef, mapOf(
                    "simplifiedDebts" to debtsForStorage,
                    "updatedAt" to System.currentTimeMillis()
                ))
            }
            batch.commit().await()
        }
    }
}
