package com.trevio.android.core.notification

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.trevio.android.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId

private val Context.streakDataStore by preferencesDataStore(name = "streak_preferences")

/**
 * Tracks the user's daily logging streak using DataStore.
 *
 * A streak day is any day where the user logs at least one expense or income.
 * The streak increments by 1 for each consecutive day, and resets to 0 if a
 * day is missed.  The best streak is preserved for milestone detection.
 *
 * All date comparisons are done in the user's [ZoneId] so that "today" is
 * consistent regardless of where the user is.
 *
 * Stored values:
 * - [currentStreak]: consecutive days of logging (0 if last log was >1 day ago)
 * - [bestStreak]: highest streak ever achieved
 * - [lastLoggedDate]: epoch day of the last logged date (LocalDate.toEpochDay())
 * - [lastMessageIndex]: index of the last message sent (for no-repeat rotation)
 */
@Singleton
class StreakTracker @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "StreakTracker"
    }

    private val currentStreakKey = intPreferencesKey("current_streak")
    private val bestStreakKey = intPreferencesKey("best_streak")
    private val lastLoggedDateKey = longPreferencesKey("last_logged_date")
    private val lastMessageIndexKey = intPreferencesKey("last_message_index")

    data class StreakState(
        val currentStreak: Int = 0,
        val bestStreak: Int = 0,
        val lastLoggedDate: Long = 0,
        val lastMessageIndex: Int = -1
    )

    val streakState: Flow<StreakState> = context.streakDataStore.data.map { prefs ->
        StreakState(
            currentStreak = prefs[currentStreakKey] ?: 0,
            bestStreak = prefs[bestStreakKey] ?: 0,
            lastLoggedDate = prefs[lastLoggedDateKey] ?: 0,
            lastMessageIndex = prefs[lastMessageIndexKey] ?: -1
        )
    }

    /**
     * Called when the user logs an expense or income.  Updates the streak
     * based on whether this is the first log today, a continuation of an
     * existing streak, or a restart after a gap.
     *
     * @param timezone The user's IANA timezone ID (e.g. "Asia/Kolkata").
     */
    suspend fun onExpenseLogged(timezone: String) {
        val zone = runCatching { ZoneId.of(timezone) }.getOrElse { ZoneId.systemDefault() }
        val today = LocalDate.now(zone).toEpochDay()

        val state = streakState.first()
        val lastLogged = state.lastLoggedDate

        if (lastLogged == today) {
            // Already logged today — no streak change.
            return
        }

        val newStreak: Int
        if (lastLogged == today - 1) {
            // Consecutive day — increment streak.
            newStreak = state.currentStreak + 1
        } else {
            // Gap — restart streak at 1.
            newStreak = 1
        }

        val newBest = maxOf(state.bestStreak, newStreak)

        context.streakDataStore.edit { prefs ->
            prefs[currentStreakKey] = newStreak
            prefs[bestStreakKey] = newBest
            prefs[lastLoggedDateKey] = today
        }
        Logger.i(TAG, "Streak updated: current=$newStreak, best=$newBest")
    }

    /**
     * Returns the current streak state, computing whether the streak broke
     * yesterday (i.e. the last logged day was >1 day ago from today).
     */
    suspend fun getStreakInfo(timezone: String): StreakInfo {
        val zone = runCatching { ZoneId.of(timezone) }.getOrElse { ZoneId.systemDefault() }
        val today = LocalDate.now(zone).toEpochDay()
        val state = streakState.first()

        // If the last logged date is today, the streak is intact.
        // If it's yesterday, the streak is still active but today isn't logged yet.
        // If it's older than yesterday, the streak is broken.
        val effectiveStreak = when {
            state.lastLoggedDate == today -> state.currentStreak
            state.lastLoggedDate == today - 1 -> state.currentStreak
            else -> 0
        }

        val streakBrokeYesterday = state.lastLoggedDate < today - 1 && state.currentStreak > 0

        return StreakInfo(
            currentStreak = effectiveStreak,
            bestStreak = state.bestStreak,
            streakBrokeYesterday = streakBrokeYesterday,
            lastMessageIndex = state.lastMessageIndex
        )
    }

    /**
     * Persists the last selected message index for no-repeat rotation.
     */
    suspend fun setLastMessageIndex(index: Int) {
        context.streakDataStore.edit { prefs ->
            prefs[lastMessageIndexKey] = index
        }
    }

    data class StreakInfo(
        val currentStreak: Int,
        val bestStreak: Int,
        val streakBrokeYesterday: Boolean,
        val lastMessageIndex: Int
    )
}
