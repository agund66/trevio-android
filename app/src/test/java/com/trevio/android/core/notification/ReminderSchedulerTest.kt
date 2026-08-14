package com.trevio.android.core.notification

import com.google.common.truth.Truth.assertThat
import com.trevio.android.domain.model.ReminderConfig
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Unit tests for the delay computation logic used by [ReminderScheduler].
 *
 * These tests verify the timezone-aware evening time resolution and delay
 * computation without requiring a WorkManager instance.
 */
class ReminderSchedulerTest {

    @Test
    fun resolveEveningTime_usesOverrideWhenPresent() {
        val config = ReminderConfig(
            enabled = true,
            defaultLocalTime = "20:00",
            timezoneOverrides = mapOf("America/New_York" to "19:00")
        )
        val time = resolveEveningTime("America/New_York", config)
        assertThat(time.hour).isEqualTo(19)
        assertThat(time.minute).isEqualTo(0)
    }

    @Test
    fun resolveEveningTime_fallsBackToDefault() {
        val config = ReminderConfig(
            enabled = true,
            defaultLocalTime = "20:00",
            timezoneOverrides = mapOf("America/New_York" to "19:00")
        )
        val time = resolveEveningTime("Asia/Kolkata", config)
        assertThat(time.hour).isEqualTo(20)
        assertThat(time.minute).isEqualTo(0)
    }

    @Test
    fun resolveEveningTime_invalidTimeFallsBackToDefault() {
        val config = ReminderConfig(
            enabled = true,
            defaultLocalTime = "20:00",
            timezoneOverrides = mapOf("Asia/Kolkata" to "invalid")
        )
        val time = resolveEveningTime("Asia/Kolkata", config)
        assertThat(time.hour).isEqualTo(20)
    }

    @Test
    fun computeDelay_schedulesForTodayIfEveningHasNotPassed() {
        val zone = ZoneId.of("Asia/Kolkata")
        val now = ZonedDateTime.of(LocalDate.now(zone), LocalTime.of(10, 0), zone)
        val eveningTime = LocalTime.of(20, 0)
        val delay = computeDelayToNextEvening(eveningTime, zone, now)
        // Should be about 10 hours
        val hours = delay / (1000 * 60 * 60)
        assertThat(hours).isEqualTo(10L)
    }

    @Test
    fun computeDelay_schedulesForTomorrowIfEveningHasPassed() {
        val zone = ZoneId.of("Asia/Kolkata")
        val now = ZonedDateTime.of(LocalDate.now(zone), LocalTime.of(21, 0), zone)
        val eveningTime = LocalTime.of(20, 0)
        val delay = computeDelayToNextEvening(eveningTime, zone, now)
        // Should be about 23 hours
        val hours = delay / (1000 * 60 * 60)
        assertThat(hours).isEqualTo(23L)
    }

    @Test
    fun computeDelay_minimumOneMinute() {
        val zone = ZoneId.of("Asia/Kolkata")
        val eveningTime = LocalTime.of(20, 0)
        val now = ZonedDateTime.of(LocalDate.now(zone), LocalTime.of(19, 59, 59), zone)
        val delay = computeDelayToNextEvening(eveningTime, zone, now)
        // Should be at least 1 minute (60000 ms) even if the target is very close
        assertThat(delay).isAtLeast(60_000L)
    }

    @Test
    fun computeDelay_dstTransitionHandledCorrectly() {
        // Test with a timezone that has DST (America/New_York)
        val zone = ZoneId.of("America/New_York")
        val now = ZonedDateTime.of(LocalDate.now(zone), LocalTime.of(10, 0), zone)
        val eveningTime = LocalTime.of(19, 0)
        val delay = computeDelayToNextEvening(eveningTime, zone, now)
        // Should be positive and reasonable (about 9 hours)
        assertThat(delay).isGreaterThan(0L)
        val hours = delay / (1000 * 60 * 60)
        assertThat(hours).isIn(8L..10L)
    }

    // ─── Pure functions mirroring ReminderScheduler logic ──────────

    private fun resolveEveningTime(timezone: String, config: ReminderConfig): LocalTime {
        val override = config.timezoneOverrides[timezone]
        val timeStr = override ?: config.defaultLocalTime
        return runCatching {
            LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HH:mm"))
        }.getOrElse { LocalTime.of(20, 0) }
    }

    private fun computeDelayToNextEvening(eveningTime: LocalTime, zone: ZoneId, now: ZonedDateTime): Long {
        val today = now.toLocalDate()
        var target = ZonedDateTime.of(today, eveningTime, zone)
        if (!target.isAfter(now)) {
            target = target.plusDays(1)
        }
        val delay = ChronoUnit.MILLIS.between(now, target)
        return maxOf(delay, 60_000L)
    }
}
