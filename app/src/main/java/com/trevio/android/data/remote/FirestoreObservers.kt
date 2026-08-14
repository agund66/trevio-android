package com.trevio.android.data.remote

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.trevio.android.domain.model.Activity
import com.trevio.android.domain.model.AppNotification
import com.trevio.android.domain.model.BillItem
import com.trevio.android.domain.model.Expense
import com.trevio.android.domain.model.FeaturedMessage
import com.trevio.android.domain.model.Group
import com.trevio.android.domain.model.GroupTemplate
import com.trevio.android.domain.model.ItemizedSplitData
import com.trevio.android.domain.model.Member
import com.trevio.android.domain.model.PaginatedResult
import com.trevio.android.domain.model.RecurringConfig
import com.trevio.android.domain.model.RecurringFrequency
import com.trevio.android.domain.model.ReminderConfig
import com.trevio.android.domain.model.SplitEntry
import com.trevio.android.domain.model.SplitType
import com.trevio.android.domain.model.TransactionType
import com.trevio.android.domain.repository.GroupInfo
import com.trevio.android.util.AppConstants
import com.trevio.android.util.DateUtils
import com.trevio.android.util.Logger
import com.trevio.android.util.MemberStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real-time Firestore listeners exposed as [Flow]s.
 *
 * Each method wraps a [com.google.firebase.firestore.addSnapshotListener]
 * in a [callbackFlow], so the caller gets instant emissions from the
 * offline cache (when persistence is enabled) followed by silent updates
 * whenever the server data changes.  This eliminates the full-screen
 * loader pattern where the user waits for a one-time `.get()` round-trip.
 *
 * The mapping logic mirrors the existing `Firebase*ServiceImpl` read
 * methods so the emitted data shapes are identical.  The one-time `.get()`
 * methods in the services remain untouched and serve as fallbacks for
 * pagination and explicit refreshes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class FirestoreObservers @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {

    // ─── Helpers ────────────────────────────────────────────────────

    /**
     * Safely parses an enum value from a Firestore string, returning the
     * default if the value is null or doesn't match any enum constant.
     * This prevents crashes when the database contains unexpected values.
     */
    private inline fun <reified T : Enum<T>> safeEnum(
        value: Any?,
        default: T
    ): T {
        if (value !is String) return default
        return try {
            enumValueOf(value.uppercase())
        } catch (e: IllegalArgumentException) {
            default
        }
    }

    private fun uid(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("User not authenticated")

    // ─── User Groups ────────────────────────────────────────────────

    /**
     * Emits the current user's groups in real-time.
     *
     * Uses a collectionGroup("members") listener filtered to the current
     * user's UID + active status, then batch-fetches the corresponding
     * group documents on each emission.  Re-emits whenever any membership
     * or group doc changes.
     */
    fun observeUserGroups(): Flow<List<Group>> = callbackFlow {
        val currentUid = try { uid() } catch (e: Exception) { close(e); return@callbackFlow }

        val registration = firestore.collectionGroup("members")
            .whereEqualTo("uid", currentUid)
            .whereEqualTo("status", MemberStatus.ACTIVE)
            .addSnapshotListener { membersSnapshot, error ->
                if (error != null) {
                    Logger.w("FirestoreObservers", "observeUserGroups error: ${error.message}", error)
                    close(error)
                    return@addSnapshotListener
                }
                if (membersSnapshot == null) return@addSnapshotListener

                val memberGroupPairs = membersSnapshot.documents.mapNotNull { memberDoc ->
                    val pathSegments = memberDoc.reference.path.split("/")
                    val groupId = pathSegments.getOrNull(1) ?: return@mapNotNull null
                    memberDoc to groupId
                }

                if (memberGroupPairs.isEmpty()) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                // Launch async group-doc fetches in the producer scope
                launch {
                    try {
                        val groupDocs = coroutineScope {
                            memberGroupPairs.map { (_, groupId) ->
                                async { firestore.collection("groups").document(groupId).get().await() }
                            }.map { it.await() }
                        }

                        val groups = memberGroupPairs.zip(groupDocs).mapNotNull { (pair, groupDoc) ->
                            val (memberDoc, _) = pair
                            if (!groupDoc.exists()) return@mapNotNull null
                            val data = groupDoc.data ?: return@mapNotNull null
                            val memberData = memberDoc.data ?: emptyMap()
                            Group(
                                groupId = groupDoc.id,
                                name = data["name"] as? String ?: "",
                                description = data["description"] as? String ?: "",
                                template = safeEnum(data["template"], GroupTemplate.CASUAL),
                                currency = data["currency"] as? String ?: AppConstants.BASE_CURRENCY,
                                createdBy = data["createdBy"] as? String ?: "",
                                inviteCode = data["inviteCode"] as? String ?: "",
                                memberCount = (data["memberCount"] as? Number)?.toInt() ?: 0,
                                totalExpenses = (data["totalExpenses"] as? Number)?.toDouble() ?: 0.0,
                                yourBalance = (memberData["balance"] as? Number)?.toDouble() ?: 0.0,
                                yourRole = memberData["role"] as? String ?: "member",
                                archived = data["archived"] as? Boolean ?: false,
                                monthlyBudget = (data["monthlyBudget"] as? Number)?.toDouble(),
                                budgetCategories = (data["budgetCategories"] as? Map<String, Any>)?.mapValues { (_, v) ->
                                    (v as? Number)?.toDouble() ?: 0.0
                                }
                            )
                        }
                        trySend(groups)
                    } catch (e: Exception) {
                        Logger.w("FirestoreObservers", "observeUserGroups fetch error: ${e.message}", e)
                    }
                }
            }
        awaitClose { registration.remove() }
    }

    // ─── Group Info ─────────────────────────────────────────────────

    /**
     * Emits a single group's info in real-time.
     */
    fun observeGroupInfo(groupId: String): Flow<GroupInfo?> = callbackFlow {
        val registration = firestore.collection("groups").document(groupId)
            .addSnapshotListener { docSnapshot, error ->
                if (error != null) {
                    Logger.w("FirestoreObservers", "observeGroupInfo error: ${error.message}", error)
                    close(error)
                    return@addSnapshotListener
                }
                if (docSnapshot == null || !docSnapshot.exists()) {
                    trySend(null)
                    return@addSnapshotListener
                }
                val data = docSnapshot.data ?: run {
                    trySend(null)
                    return@addSnapshotListener
                }
                @Suppress("UNCHECKED_CAST")
                val budgetCategoriesRaw = data["budgetCategories"] as? Map<String, Any>
                trySend(
                    GroupInfo(
                        groupId = groupId,
                        name = data["name"] as? String ?: "",
                        description = data["description"] as? String ?: "",
                        template = safeEnum(data["template"], GroupTemplate.CASUAL),
                        currency = data["currency"] as? String ?: AppConstants.BASE_CURRENCY,
                        inviteCode = data["inviteCode"] as? String ?: "",
                        createdBy = data["createdBy"] as? String ?: "",
                        memberCount = (data["memberCount"] as? Number)?.toInt() ?: 0,
                        totalExpenses = (data["totalExpenses"] as? Number)?.toDouble() ?: 0.0,
                        archived = data["archived"] as? Boolean ?: false,
                        monthlyBudget = (data["monthlyBudget"] as? Number)?.toDouble(),
                        budgetCategories = budgetCategoriesRaw?.mapValues { (_, v) ->
                            (v as? Number)?.toDouble() ?: 0.0
                        }
                    )
                )
            }
        awaitClose { registration.remove() }
    }

    // ─── Group Expenses ─────────────────────────────────────────────

    /**
     * Emits the first page of group expenses in real-time.
     * Pagination (load-more) should still use the one-time `.get()`
     * method in [com.trevio.android.domain.repository.ExpenseService.getGroupExpenses].
     */
    fun observeGroupExpenses(groupId: String, pageSize: Int): Flow<PaginatedResult<Expense>> = callbackFlow {
        val groupRef = firestore.collection("groups").document(groupId)
        val registration = groupRef.collection("expenses")
            .orderBy("date", Query.Direction.DESCENDING)
            .limit(pageSize.toLong())
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Logger.w("FirestoreObservers", "observeGroupExpenses error: ${error.message}", error)
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener

                val expenses = snapshot.documents.map { doc ->
                    val data = doc.data ?: emptyMap()
                    @Suppress("UNCHECKED_CAST")
                    val splitsRaw = data["splits"] as? Map<String, Map<String, Any>> ?: emptyMap()
                    Expense(
                        expenseId = doc.id,
                        description = data["description"] as? String ?: "",
                        amount = (data["amount"] as? Number)?.toDouble() ?: 0.0,
                        currency = data["currency"] as? String ?: AppConstants.BASE_CURRENCY,
                        paidBy = data["paidBy"] as? String ?: "",
                        paidByName = data["paidByName"] as? String ?: "",
                        splitType = safeEnum(data["splitType"], SplitType.EQUAL),
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
                                frequency = safeEnum(r["frequency"], RecurringFrequency.MONTHLY),
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
                        transactionType = safeEnum(data["transactionType"], TransactionType.EXPENSE)
                    )
                }
                trySend(PaginatedResult(
                    items = expenses,
                    hasMore = snapshot.size() == pageSize,
                    lastId = if (snapshot.size() > 0) snapshot.documents.last().id else null
                ))
            }
        awaitClose { registration.remove() }
    }

    // ─── Group Balances (Members) ───────────────────────────────────

    /**
     * Emits the group's members (with balances) in real-time.
     * Uses denormalized displayName/username/photoURL stored on the
     * member doc — no N+1 user doc fetches needed.
     */
    fun observeGroupBalances(groupId: String): Flow<List<Member>> = callbackFlow {
        val groupRef = firestore.collection("groups").document(groupId)
        val registration = groupRef.collection("members")
            .whereIn("status", listOf("active", "pending"))
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Logger.w("FirestoreObservers", "observeGroupBalances error: ${error.message}", error)
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener

                val members = snapshot.documents.mapNotNull { doc ->
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
                trySend(members)
            }
        awaitClose { registration.remove() }
    }

    // ─── Notifications ──────────────────────────────────────────────

    /**
     * Emits the first page of the current user's notifications in real-time.
     */
    fun observeNotifications(pageSize: Int): Flow<PaginatedResult<AppNotification>> = callbackFlow {
        val currentUid = try { uid() } catch (e: Exception) { close(e); return@callbackFlow }
        val registration = firestore.collection("users").document(currentUid)
            .collection("notifications")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(pageSize.toLong())
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Logger.w("FirestoreObservers", "observeNotifications error: ${error.message}", error)
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener

                val notifications = snapshot.documents.map { doc ->
                    val data = doc.data ?: emptyMap()
                    @Suppress("UNCHECKED_CAST")
                    val dataMap = (data["data"] as? Map<String, String>) ?: emptyMap()
                    AppNotification(
                        notificationId = doc.id,
                        type = data["type"] as? String ?: "",
                        title = data["title"] as? String ?: "",
                        body = data["body"] as? String ?: "",
                        read = data["read"] as? Boolean ?: false,
                        createdAt = DateUtils.toMillis(data["createdAt"]) ?: 0,
                        data = dataMap
                    )
                }
                trySend(PaginatedResult(
                    items = notifications,
                    hasMore = snapshot.size() == pageSize,
                    lastId = if (snapshot.size() > 0) snapshot.documents.last().id else null
                ))
            }
        awaitClose { registration.remove() }
    }

    // ─── Group Activities ───────────────────────────────────────────

    /**
     * Emits the first page of group activities in real-time.
     * Uses denormalized userName/userPhotoURL stored on each activity
     * doc — no N+1 user doc fetches needed.
     */
    fun observeGroupActivities(groupId: String, pageSize: Int): Flow<PaginatedResult<Activity>> = callbackFlow {
        val groupRef = firestore.collection("groups").document(groupId)
        val registration = groupRef.collection("activities")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(pageSize.toLong())
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Logger.w("FirestoreObservers", "observeGroupActivities error: ${error.message}", error)
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener

                val activities = snapshot.documents.map { doc ->
                    val data = doc.data ?: emptyMap()
                    @Suppress("UNCHECKED_CAST")
                    val activityData = data["data"] as? Map<String, Any>
                    Activity(
                        activityId = doc.id,
                        type = data["type"] as? String ?: "unknown",
                        description = data["description"] as? String ?: "",
                        userId = data["userId"] as? String ?: "",
                        userName = data["userName"] as? String ?: "Someone",
                        userPhotoURL = data["userPhotoURL"] as? String ?: "",
                        createdAt = DateUtils.toMillis(data["createdAt"]) ?: 0,
                        data = activityData
                    )
                }
                trySend(PaginatedResult(
                    items = activities,
                    hasMore = snapshot.size() == pageSize,
                    lastId = if (snapshot.size() > 0) snapshot.documents.last().id else null
                ))
            }
        awaitClose { registration.remove() }
    }

    // ─── Daily Reminder Config ──────────────────────────────────────

    /**
     * Emits the daily reminder configuration from `config/dailyReminder` in
     * real-time.  Returns `null` if the document does not exist yet (admin
     * has not configured reminders).  The caller should treat `null` as
     * "reminders disabled" and cancel any scheduled work.
     */
    fun observeReminderConfig(): Flow<ReminderConfig?> = callbackFlow {
        val registration = firestore.collection("config").document("dailyReminder")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Logger.w("FirestoreObservers", "observeReminderConfig error: ${error.message}", error)
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot == null || !snapshot.exists()) {
                    trySend(null)
                    return@addSnapshotListener
                }

                val data = snapshot.data ?: emptyMap()
                @Suppress("UNCHECKED_CAST")
                val overrides = data["timezoneOverrides"] as? Map<String, String> ?: emptyMap()
                val featured = (data["featuredMessage"] as? Map<String, Any>)?.let { fm ->
                    FeaturedMessage(
                        title = fm["title"] as? String,
                        body = fm["body"] as? String ?: return@let null,
                        startAt = (fm["startAt"] as? Number)?.toLong() ?: 0,
                        endAt = (fm["endAt"] as? Number)?.toLong() ?: 0
                    )
                }
                trySend(ReminderConfig(
                    enabled = data["enabled"] as? Boolean ?: true,
                    featuredMessage = featured,
                    defaultLocalTime = data["defaultLocalTime"] as? String ?: "20:00",
                    timezoneOverrides = overrides,
                    updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: 0
                ))
            }
        awaitClose { registration.remove() }
    }
}
