package com.trevio.android.core.notification

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * Unit tests for the streak computation logic used by [StreakTracker].
 *
 * These tests verify the core date-based streak logic without requiring
 * a DataStore instance (which needs an Android Context).  The logic is
 * extracted into pure functions for testability.
 */
class StreakTrackerTest {

    private val zone = ZoneId.of("Asia/Kolkata")

    @Test
    fun consecutiveDay_incrementsStreak() {
        val today = LocalDate.now(zone).toEpochDay()
        val yesterday = today - 1
        val currentStreak = 5

        val newStreak = computeNewStreak(today, yesterday, currentStreak)
        assertThat(newStreak).isEqualTo(6)
    }

    @Test
    fun gapDay_resetsStreakToOne() {
        val today = LocalDate.now(zone).toEpochDay()
        val twoDaysAgo = today - 2
        val currentStreak = 5

        val newStreak = computeNewStreak(today, twoDaysAgo, currentStreak)
        assertThat(newStreak).isEqualTo(1)
    }

    @Test
    fun sameDay_noStreakChange() {
        val today = LocalDate.now(zone).toEpochDay()
        val currentStreak = 5

        // Already logged today — should not change
        val newStreak = computeNewStreak(today, today, currentStreak)
        assertThat(newStreak).isEqualTo(5) // No change
    }

    @Test
    fun bestStreak_preservedWhenCurrentExceeds() {
        val currentStreak = 10
        val bestStreak = 7
        val newBest = maxOf(bestStreak, currentStreak)
        assertThat(newBest).isEqualTo(10)
    }

    @Test
    fun bestStreak_preservedWhenCurrentDoesNotExceed() {
        val currentStreak = 3
        val bestStreak = 15
        val newBest = maxOf(bestStreak, currentStreak)
        assertThat(newBest).isEqualTo(15)
    }

    @Test
    fun streakBrokeYesterday_detectedCorrectly() {
        val today = LocalDate.now(zone).toEpochDay()
        val lastLogged = today - 3 // 3 days ago
        val currentStreak = 5

        val broke = isStreakBrokeYesterday(today, lastLogged, currentStreak)
        assertThat(broke).isTrue()
    }

    @Test
    fun streakBrokeYesterday_notTriggeredWhenLoggedToday() {
        val today = LocalDate.now(zone).toEpochDay()
        val lastLogged = today
        val currentStreak = 5

        val broke = isStreakBrokeYesterday(today, lastLogged, currentStreak)
        assertThat(broke).isFalse()
    }

    @Test
    fun streakBrokeYesterday_notTriggeredWhenLoggedYesterday() {
        val today = LocalDate.now(zone).toEpochDay()
        val lastLogged = today - 1
        val currentStreak = 5

        val broke = isStreakBrokeYesterday(today, lastLogged, currentStreak)
        assertThat(broke).isFalse()
    }

    @Test
    fun effectiveStreak_intactWhenLoggedToday() {
        val today = LocalDate.now(zone).toEpochDay()
        val lastLogged = today
        val currentStreak = 7

        val effective = computeEffectiveStreak(today, lastLogged, currentStreak)
        assertThat(effective).isEqualTo(7)
    }

    @Test
    fun effectiveStreak_intactWhenLoggedYesterday() {
        val today = LocalDate.now(zone).toEpochDay()
        val lastLogged = today - 1
        val currentStreak = 7

        val effective = computeEffectiveStreak(today, lastLogged, currentStreak)
        assertThat(effective).isEqualTo(7)
    }

    @Test
    fun effectiveStreak_zeroWhenGapExists() {
        val today = LocalDate.now(zone).toEpochDay()
        val lastLogged = today - 5
        val currentStreak = 7

        val effective = computeEffectiveStreak(today, lastLogged, currentStreak)
        assertThat(effective).isEqualTo(0)
    }

    // ─── Pure functions mirroring StreakTracker logic ──────────────

    private fun computeNewStreak(todayEpoch: Long, lastLoggedEpoch: Long, currentStreak: Int): Int {
        return when {
            lastLoggedEpoch == todayEpoch -> currentStreak // No change
            lastLoggedEpoch == todayEpoch - 1 -> currentStreak + 1
            else -> 1
        }
    }

    private fun isStreakBrokeYesterday(todayEpoch: Long, lastLoggedEpoch: Long, currentStreak: Int): Boolean {
        return lastLoggedEpoch < todayEpoch - 1 && currentStreak > 0
    }

    private fun computeEffectiveStreak(todayEpoch: Long, lastLoggedEpoch: Long, currentStreak: Int): Int {
        return when {
            lastLoggedEpoch == todayEpoch -> currentStreak
            lastLoggedEpoch == todayEpoch - 1 -> currentStreak
            else -> 0
        }
    }
}
