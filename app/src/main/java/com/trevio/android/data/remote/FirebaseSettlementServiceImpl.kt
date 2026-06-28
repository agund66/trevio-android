package com.trevio.android.data.remote

import com.google.firebase.functions.FirebaseFunctions
import com.trevio.android.domain.model.Member
import com.trevio.android.domain.model.Settlement
import com.trevio.android.domain.model.SettlementMethod
import com.trevio.android.domain.model.SimplifiedDebt
import com.trevio.android.domain.repository.SettlementService
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseSettlementServiceImpl @Inject constructor(
    private val functions: FirebaseFunctions
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
            val data = mutableMapOf<String, Any>(
                "groupId" to groupId,
                "fromUid" to fromUid,
                "toUid" to toUid,
                "amount" to amount,
                "currency" to currency,
                "method" to method.name.lowercase()
            )
            if (upiRefId != null) data["upiRefId"] = upiRefId

            val result = functions.getHttpsCallable("addSettlement").call(data).await()
            val res = result.getData() as Map<*, *>
            Result.success(res["settlementId"] as String)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getSimplifiedDebts(groupId: String): Result<List<SimplifiedDebt>> {
        return try {
            val result = functions.getHttpsCallable("getSimplifiedDebts")
                .call(mapOf("groupId" to groupId)).await()
            val data = result.getData() as Map<*, *>
            @Suppress("UNCHECKED_CAST")
            val debts = data["debts"] as List<Map<*, *>>
            Result.success(debts.map { d ->
                SimplifiedDebt(
                    fromUid = d["fromUid"] as String,
                    toUid = d["toUid"] as String,
                    fromName = d["fromName"] as String,
                    toName = d["toName"] as String,
                    fromPhotoURL = d["fromPhotoURL"] as? String ?: "",
                    toPhotoURL = d["toPhotoURL"] as? String ?: "",
                    amount = (d["amount"] as? Double ?: 0.0)
                )
            })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getGroupBalances(groupId: String): Result<List<Member>> {
        return try {
            val result = functions.getHttpsCallable("getGroupBalances")
                .call(mapOf("groupId" to groupId)).await()
            val data = result.getData() as Map<*, *>
            @Suppress("UNCHECKED_CAST")
            val members = data["members"] as List<Map<*, *>>
            Result.success(members.map { m ->
                Member(
                    uid = m["uid"] as String,
                    displayName = m["displayName"] as String,
                    username = m["username"] as? String ?: "",
                    photoURL = m["photoURL"] as? String ?: "",
                    balance = (m["balance"] as? Double ?: 0.0),
                    role = m["role"] as? String ?: "member",
                    status = m["status"] as? String ?: "active"
                )
            })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getSettlementHistory(groupId: String): Result<List<Settlement>> {
        return try {
            val result = functions.getHttpsCallable("getSettlementHistory")
                .call(mapOf("groupId" to groupId)).await()
            val data = result.getData() as Map<*, *>
            @Suppress("UNCHECKED_CAST")
            val settlements = data["settlements"] as List<Map<*, *>>
            Result.success(settlements.map { s ->
                Settlement(
                    settlementId = s["settlementId"] as String,
                    fromUid = s["fromUid"] as String,
                    toUid = s["toUid"] as String,
                    fromName = s["fromName"] as String,
                    toName = s["toName"] as String,
                    amount = (s["amount"] as? Double ?: 0.0),
                    currency = s["currency"] as? String ?: "INR",
                    method = SettlementMethod.valueOf((s["method"] as? String ?: "cash").uppercase()),
                    upiRefId = s["upiRefId"] as? String ?: ""
                )
            })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
