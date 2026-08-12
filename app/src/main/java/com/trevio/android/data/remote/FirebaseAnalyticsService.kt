package com.trevio.android.data.remote

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.trevio.android.domain.model.Expense
import com.trevio.android.domain.model.GroupAnalytics
import com.trevio.android.domain.model.Member
import com.trevio.android.domain.model.UserAnalytics
import com.trevio.android.domain.repository.AnalyticsService
import com.trevio.android.util.AppConstants
import com.trevio.android.util.computeGroupAnalytics
import com.trevio.android.util.friendlyNetworkMessage
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseAnalyticsService @Inject constructor(
    private val auth: FirebaseAuth
) : AnalyticsService {

    private val db = FirebaseFirestore.getInstance()

    override suspend fun getGroupAnalytics(groupId: String): Result<GroupAnalytics> {
        return try {
            val uid = auth.currentUser?.uid ?: return Result.failure(Exception("User not authenticated"))
            if (groupId.isBlank()) return Result.failure(Exception("Group ID is required"))

            val memberDoc = db.collection("groups").document(groupId).collection("members").document(uid).get().await()
            if (!memberDoc.exists()) return Result.failure(Exception("You are not a member of this group"))

            val expensesRef = db.collection("groups").document(groupId).collection("expenses")
                .orderBy("date", Query.Direction.DESCENDING).limit(500)
            val expenseSnapshot = expensesRef.get().await()

            val expenses = expenseSnapshot.documents.map { doc ->
                val data = doc.data ?: emptyMap()
                @Suppress("UNCHECKED_CAST")
                val splits = (data["splits"] as? Map<String, Map<String, Any>>)?.mapValues { (_, v) ->
                    com.trevio.android.domain.model.SplitEntry(
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
                    splitType = com.trevio.android.domain.model.SplitType.valueOf(
                        (data["splitType"] as? String ?: "EQUAL").uppercase()
                    ),
                    splits = splits,
                    category = data["category"] as? String ?: "other",
                    date = (data["date"] as? Number)?.toLong() ?: 0,
                    createdBy = data["createdBy"] as? String ?: "",
                    note = data["note"] as? String ?: ""
                )
            }

            val membersRef = db.collection("groups").document(groupId).collection("members")
                .whereIn("status", listOf("active", "pending"))
            val membersSnapshot = membersRef.get().await()
            val members = membersSnapshot.documents.map { doc ->
                val data = doc.data ?: emptyMap()
                Member(
                    uid = doc.id,
                    displayName = data["displayName"] as? String ?: "",
                    username = data["username"] as? String ?: "",
                    photoURL = data["photoURL"] as? String ?: "",
                    balance = (data["balance"] as? Number)?.toDouble() ?: 0.0,
                    role = data["role"] as? String ?: "member",
                    status = data["status"] as? String ?: "active"
                )
            }

            val groupDoc = db.collection("groups").document(groupId).get().await()
            val groupName = groupDoc.getString("name") ?: groupId

            Result.success(computeGroupAnalytics(groupId, groupName, expenses, members))
        } catch (e: Exception) {
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    override suspend fun getUserAnalytics(): Result<UserAnalytics> {
        auth.currentUser?.uid ?: return Result.failure(Exception("User not authenticated"))
        return Result.success(UserAnalytics())
    }
}
