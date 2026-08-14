package com.trevio.android.core.notification

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.trevio.android.domain.model.ReminderConfig
import com.trevio.android.util.AppConstants
import com.trevio.android.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules the daily reminder [ReminderWorker] using WorkManager.
 *
 * The worker is scheduled as a [OneTimeWorkRequest] with a computed initial
 * delay targeting the user's local evening time.  After each execution, the
 * worker reschedules itself for the next day — this "self-perpetuating"
 * pattern avoids the 15-minute minimum interval of [PeriodicWorkRequest]
 * while still benefiting from WorkManager's OS-guaranteed execution, Doze
 * survival, and automatic reboot restoration.
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "ReminderScheduler"
        const val WORK_NAME = "daily_reminder"
    }

    /**
     * Schedules (or reschedules) the daily reminder.
     *
     * @param timezone  The user's IANA timezone ID (e.g. "Asia/Kolkata").
     * @param config    The current reminder configuration from Firestore.
     */
    fun schedule(timezone: String, config: ReminderConfig) {
        if (!config.enabled) {
            cancel()
            Logger.i(TAG, "Reminders disabled by admin — cancelled scheduled work")
            return
        }

        val zone = runCatching { ZoneId.of(timezone) }.getOrElse {
            Logger.w(TAG, "Invalid timezone '$timezone', falling back to default")
            ZoneId.of(AppConstants.DEFAULT_TIMEZONE)
        }

        val eveningTime = resolveEveningTime(timezone, config)
        val delayMillis = computeDelayToNextEvening(eveningTime, zone)

        if (delayMillis < 0) {
            Logger.w(TAG, "Computed negative delay, skipping schedule")
            return
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .addTag(WORK_NAME)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )

        Logger.i(TAG, "Scheduled reminder in ${delayMillis / 1000 / 60} minutes " +
                "(target: $eveningTime in $zone)")
    }

    /**
     * Cancels any scheduled daily reminder.
     */
    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    // ─── Internal helpers ────────────────────────────────────────────

    /**
     * Resolves the evening time for the given timezone, checking admin
     * overrides first, then falling back to the default.
     */
    private fun resolveEveningTime(timezone: String, config: ReminderConfig): LocalTime {
        val override = config.timezoneOverrides[timezone]
        val timeStr = override ?: config.defaultLocalTime
        return runCatching {
            LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HH:mm"))
        }.getOrElse {
            Logger.w(TAG, "Invalid time format '$timeStr', using default 20:00")
            LocalTime.of(20, 0)
        }
    }

    /**
     * Computes the delay in milliseconds from now until the next evening
     * time in the user's timezone.  If today's evening time has already
     * passed, schedules for tomorrow.
     *
     * DST-safe: uses [ZonedDateTime] with the user's [ZoneId], so the
     * computed instant correctly accounts for daylight saving transitions.
     */
    private fun computeDelayToNextEvening(eveningTime: LocalTime, zone: ZoneId): Long {
        val now = ZonedDateTime.now(zone)
        val today = now.toLocalDate()

        // Try today first.
        var target = ZonedDateTime.of(today, eveningTime, zone)

        // If the target time has already passed today, schedule for tomorrow.
        if (!target.isAfter(now)) {
            target = target.plusDays(1)
        }

        val delay = ChronoUnit.MILLIS.between(now, target)

        // Safety: ensure delay is at least 1 minute to avoid immediate re-fire loops.
        return maxOf(delay, TimeUnit.MINUTES.toMillis(1))
    }
}
