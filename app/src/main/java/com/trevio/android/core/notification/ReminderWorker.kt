package com.trevio.android.core.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.trevio.android.MainActivity
import com.trevio.android.R
import com.trevio.android.domain.model.FeaturedMessage
import com.trevio.android.domain.model.GroupTemplate
import com.trevio.android.domain.model.ReminderConfig
import com.trevio.android.util.AppConstants
import com.trevio.android.util.Logger
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.ZoneId

/**
 * The WorkManager worker that fires the daily reminder notification.
 *
 * On each execution:
 * 1. Fetches `config/dailyReminder` from Firestore (one-time `.get()`).
 * 2. If disabled, returns success without rescheduling.
 * 3. Detects today's logging activity by querying the user's group expenses.
 * 4. Selects a message from the local library (or admin's featured message).
 * 5. Appends a gamification footer (streak / milestone / recovery).
 * 6. Posts the notification on the [NotificationChannels.REMINDER] channel.
 * 7. Reschedules itself for tomorrow via [ReminderScheduler].
 */
@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val streakTracker: StreakTracker,
    private val scheduler: ReminderScheduler
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "ReminderWorker"
        private const val NOTIFICATION_ID = 2001
    }

    override suspend fun doWork(): Result {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            Logger.i(TAG, "User not authenticated — skipping reminder")
            return Result.success()
        }

        // 1. Fetch reminder config from Firestore.
        val config = fetchReminderConfig()
        if (config == null) {
            // Network error — retry with backoff.
            Logger.w(TAG, "Failed to fetch config — will retry")
            return Result.retry()
        }
        if (!config.enabled) {
            Logger.i(TAG, "Reminders disabled by admin — not rescheduling")
            return Result.success()
        }

        // 2. Fetch user's timezone.
        val timezone = fetchUserTimezone(uid)

        // Wrap the main work body so that ANY unexpected exception (DataStore
        // IOException, asset read failure, etc.) does not break the
        // self-perpetuating rescheduling chain.  The reschedule call in the
        // finally block guarantees tomorrow's reminder is always enqueued.
        var posted = false
        try {
            // 3. Fetch the user's group IDs (shared by activity detection + primary group).
            val groupIds = fetchUserGroupIds(uid)

            // 4. Detect today's logging activity.
            val activityState = detectActivityState(uid, timezone, groupIds)

            // 5. Fetch user's primary group info (for household detection + deep-link).
            val primaryGroup = fetchPrimaryGroup(groupIds, timezone)

            // 6. Get streak info (wrapped — DataStore IOException is non-fatal).
            val streakInfo = try {
                streakTracker.getStreakInfo(timezone)
            } catch (e: Exception) {
                Logger.w(TAG, "Failed to read streak info, using defaults", e)
                StreakTracker.StreakInfo(0, 0, false, -1)
            }

            // 7. Select message.
            val messageProvider = ReminderMessageProvider(applicationContext)
            val featured = getActiveFeaturedMessage(config)
            val messageBody = messageProvider.selectMessage(
                state = activityState,
                isHousehold = primaryGroup?.isHousehold == true,
                currentStreak = streakInfo.currentStreak,
                streakBrokeYesterday = streakInfo.streakBrokeYesterday,
                pctUsed = primaryGroup?.pctUsed,
                featuredMessage = featured,
                lastMessageIdx = streakInfo.lastMessageIndex
            )

            // 8. Persist the last selected message index for no-repeat rotation.
            try {
                messageProvider.lastSelectedIndex().let { idx ->
                    if (idx >= 0) streakTracker.setLastMessageIndex(idx)
                }
            } catch (e: Exception) {
                Logger.w(TAG, "Failed to persist last message index", e)
            }

            // 9. Build gamification footer.
            // When the message body already conveys recovery (streakBrokeYesterday
            // → recovery pool), skip the footer to avoid double recovery messaging.
            val footer = if (streakInfo.streakBrokeYesterday) "" else buildGamificationFooter(streakInfo)
            val fullBody = if (footer.isNotEmpty()) "$messageBody $footer" else messageBody

            // 10. Post notification.
            val title = featured?.title ?: applicationContext.getString(R.string.reminder_title)
            postNotification(title, fullBody, primaryGroup?.groupId)
            posted = true

            Logger.i(TAG, "Reminder posted: state=$activityState, streak=${streakInfo.currentStreak}")
        } catch (e: Exception) {
            Logger.e(TAG, "Unexpected error in doWork — notification not posted, but will reschedule", e)
        } finally {
            // 11. Always reschedule for tomorrow, even if the main body threw.
            // This is the critical self-perpetuating step — without it, a
            // single exception would permanently kill all future reminders.
            try {
                scheduler.schedule(timezone, config)
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to reschedule — reminder chain broken!", e)
            }
        }

        return Result.success()
    }

    // ─── Firestore fetches ───────────────────────────────────────────

    /**
     * Fetches the reminder config. Returns null only on network/Firestore
     * errors (triggering a retry). If the document doesn't exist, returns a
     * default config with enabled=true — this is the "first run" case where
     * the admin hasn't configured anything yet.
     */
    private suspend fun fetchReminderConfig(): ReminderConfig? {
        return try {
            val doc = firestore.collection("config").document("dailyReminder").get().await()
            if (!doc.exists()) {
                // Doc doesn't exist yet — use defaults (enabled, 20:00).
                return ReminderConfig()
            }

            val data = doc.data ?: return ReminderConfig()
            @Suppress("UNCHECKED_CAST")
            val overrides = data["timezoneOverrides"] as? Map<String, String> ?: emptyMap()
            val featured = (data["featuredMessage"] as? Map<String, Any>)?.let { fm ->
                val body = fm["body"] as? String ?: return@let null
                FeaturedMessage(
                    title = fm["title"] as? String,
                    body = body,
                    startAt = (fm["startAt"] as? Number)?.toLong() ?: 0,
                    endAt = (fm["endAt"] as? Number)?.toLong() ?: 0
                )
            }
            ReminderConfig(
                enabled = data["enabled"] as? Boolean ?: true,
                featuredMessage = featured,
                defaultLocalTime = data["defaultLocalTime"] as? String ?: "20:00",
                timezoneOverrides = overrides,
                updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: 0
            )
        } catch (e: Exception) {
            Logger.w(TAG, "Failed to fetch reminder config: ${e.message}", e)
            null
        }
    }

    private suspend fun fetchUserTimezone(uid: String): String {
        return try {
            val doc = firestore.collection("users").document(uid).get().await()
            doc.getString("timezone") ?: AppConstants.DEFAULT_TIMEZONE
        } catch (e: Exception) {
            Logger.w(TAG, "Failed to fetch user timezone, using default", e)
            AppConstants.DEFAULT_TIMEZONE
        }
    }

    /**
     * Fetches the user's group IDs by querying the members collection group.
     */
    private suspend fun fetchUserGroupIds(uid: String): List<String> {
        return try {
            val memberSnapshot = firestore.collectionGroup("members")
                .whereEqualTo("uid", uid)
                .whereEqualTo("status", "active")
                .get()
                .await()

            memberSnapshot.documents.mapNotNull { doc ->
                val segments = doc.reference.path.split("/")
                segments.getOrNull(1)
            }
        } catch (e: Exception) {
            Logger.w(TAG, "Failed to fetch user groups: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Detects today's logging activity by querying expenses across all the
     * user's groups.  Counts entries dated today and checks whether both
     * expense and income types are present.
     */
    private suspend fun detectActivityState(
        uid: String,
        timezone: String,
        groupIds: List<String>
    ): LoggingState {
        val zone = runCatching { ZoneId.of(timezone) }.getOrElse { ZoneId.systemDefault() }
        val today = LocalDate.now(zone)
        val startOfDay = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val endOfDay = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        return try {
            if (groupIds.isEmpty()) return LoggingState.NOTHING_LOGGED

            var totalToday = 0
            var hasExpense = false
            var hasIncome = false

            for (groupId in groupIds) {
                val expensesSnapshot = firestore.collection("groups")
                    .document(groupId)
                    .collection("expenses")
                    .whereGreaterThanOrEqualTo("date", startOfDay)
                    .whereLessThan("date", endOfDay)
                    .limit(20)
                    .get()
                    .await()

                for (doc in expensesSnapshot.documents) {
                    val createdBy = doc.getString("createdBy")
                    // Only count entries created by this user.
                    if (createdBy == uid) {
                        totalToday++
                        val type = doc.getString("transactionType")
                        if (type == "expense") hasExpense = true
                        if (type == "income") hasIncome = true
                    }
                }
            }

            when {
                totalToday >= 3 && hasExpense && hasIncome -> LoggingState.FULLY_LOGGED
                totalToday > 0 -> LoggingState.PARTIALLY_LOGGED
                else -> LoggingState.NOTHING_LOGGED
            }
        } catch (e: Exception) {
            Logger.w(TAG, "Failed to detect activity state: ${e.message}", e)
            LoggingState.NOTHING_LOGGED
        }
    }

    /**
     * Fetches the user's primary group (household takes priority) for
     * deep-linking and budget percentage calculation.
     */
    private suspend fun fetchPrimaryGroup(
        groupIds: List<String>,
        timezone: String
    ): PrimaryGroupInfo? {
        return try {
            if (groupIds.isEmpty()) return null

            // Fetch all group docs.
            val groupDocs = groupIds.map { groupId ->
                firestore.collection("groups").document(groupId).get().await()
            }

            // Prefer household groups, then any group.
            val sorted = groupDocs.filter { it.exists() }.sortedByDescending { doc ->
                doc.getString("template") == GroupTemplate.HOUSEHOLD.name
            }

            val groupDoc = sorted.firstOrNull() ?: return null
            val groupId = groupDoc.id
            val isHousehold = groupDoc.getString("template") == GroupTemplate.HOUSEHOLD.name

            // Calculate budget percentage if monthly budget is set.
            val monthlyBudget = (groupDoc.getDouble("monthlyBudget") ?: groupDoc.get("monthlyBudget") as? Number)?.toDouble()
            val pctUsed = if (monthlyBudget != null && monthlyBudget > 0) {
                calculateMonthlyBudgetUsed(groupId, monthlyBudget, timezone)
            } else null

            PrimaryGroupInfo(groupId = groupId, isHousehold = isHousehold, pctUsed = pctUsed)
        } catch (e: Exception) {
            Logger.w(TAG, "Failed to fetch primary group: ${e.message}", e)
            null
        }
    }

    /**
     * Calculates the percentage of monthly budget used by summing this
     * month's expenses in the group.
     */
    private suspend fun calculateMonthlyBudgetUsed(groupId: String, monthlyBudget: Double, timezone: String): Int {
        return try {
            val zone = runCatching { ZoneId.of(timezone) }.getOrElse { ZoneId.systemDefault() }
            val monthStart = LocalDate.now(zone).withDayOfMonth(1).atStartOfDay(zone)
                .toInstant().toEpochMilli()

            val snapshot = firestore.collection("groups")
                .document(groupId)
                .collection("expenses")
                .whereGreaterThanOrEqualTo("date", monthStart)
                .whereEqualTo("transactionType", "expense")
                .get()
                .await()

            val total = snapshot.documents.sumOf { doc ->
                (doc.getDouble("amount") ?: (doc.get("amount") as? Number)?.toDouble() ?: 0.0)
            }

            ((total / monthlyBudget) * 100).toInt().coerceIn(0, 999)
        } catch (e: Exception) {
            Logger.w(TAG, "Failed to calculate budget used: ${e.message}", e)
            0
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────

    private fun getActiveFeaturedMessage(config: ReminderConfig): FeaturedMessage? {
        val featured = config.featuredMessage ?: return null
        val now = System.currentTimeMillis()
        // Active if now is within [startAt, endAt].
        return if (now >= featured.startAt && now <= featured.endAt) featured else null
    }

    private fun buildGamificationFooter(streakInfo: StreakTracker.StreakInfo): String {
        return when {
            streakInfo.streakBrokeYesterday ->
                applicationContext.getString(R.string.reminder_streak_recovery)
            streakInfo.currentStreak == 0 ->
                applicationContext.getString(R.string.reminder_streak_start)
            streakInfo.currentStreak >= 100 ->
                applicationContext.getString(R.string.reminder_streak_milestone, "100-day streak")
            streakInfo.currentStreak >= 2 ->
                applicationContext.getString(R.string.reminder_streak_format, streakInfo.currentStreak)
            streakInfo.currentStreak == 1 ->
                applicationContext.getString(R.string.reminder_streak_format, 1)
            else -> ""
        }
    }

    private fun postNotification(title: String, body: String, groupId: String?) {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            // Deep-link to the group detail if we have a group ID.
            if (groupId != null) {
                putExtra("nav_route", "group/$groupId")
            }
        }

        // Use a fixed notification ID so today's reminder replaces yesterday's
        // undirected notification instead of stacking up in the tray.
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(applicationContext, NotificationChannels.REMINDER)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        try {
            NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            Logger.w(TAG, "Cannot post notification: POST_NOTIFICATIONS not granted")
        }
    }

    private data class PrimaryGroupInfo(
        val groupId: String,
        val isHousehold: Boolean,
        val pctUsed: Int?
    )
}
