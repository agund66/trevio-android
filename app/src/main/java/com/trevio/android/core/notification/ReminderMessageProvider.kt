package com.trevio.android.core.notification

import android.content.Context
import com.trevio.android.domain.model.FeaturedMessage
import com.trevio.android.util.Logger
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Activity state detected at fire time, based on how many expenses the user
 * has logged today.
 */
enum class LoggingState {
    NOTHING_LOGGED,
    PARTIALLY_LOGGED,
    FULLY_LOGGED
}

/**
 * Selects a daily reminder message from the local [reminder_messages.json]
 * asset library.
 *
 * Selection strategy (in priority order):
 * 1. **Featured message** — if the admin has an active featured message, it
 *    overrides everything.
 * 2. **Recovery** — if the streak broke yesterday, pick from the recovery pool.
 * 3. **Milestone** — if today is a milestone day (7, 14, 21, 30, …), pick from
 *    the milestone pool.
 * 4. **Seasonal** — on weekends, 30% chance to pick from the seasonal pool.
 * 5. **State pool** — otherwise pick from the pool matching the user's
 *    [LoggingState] and group type, using a weekly-bucket rotation that
 *    avoids repeating the last sent message.
 *
 * If the JSON asset fails to load, falls back to [R.string] defaults.
 */
class ReminderMessageProvider(private val context: Context) {

    companion object {
        private const val TAG = "ReminderMessageProvider"
        private const val ASSET_FILE = "reminder_messages.json"

        /** Days that count as milestones for special celebration messages. */
        private val MILESTONE_DAYS = setOf(7, 14, 21, 30, 45, 50, 60, 75, 100, 150, 180, 200, 250, 300, 365)

        private const val SEASONAL_CHANCE = 0.3
    }

    private val messages: JSONObject? by lazy { loadMessages() }

    /** Index of the last message sent (stored by caller in DataStore). */
    private var lastMessageIndex: Int = -1

    /**
     * Returns the message body for today, following the selection strategy.
     *
     * @param state           Today's logging state.
     * @param isHousehold     Whether the user's primary group is a household.
     * @param currentStreak   Current logging streak (days).
     * @param streakBrokeYesterday  True if the streak was broken yesterday.
     * @param pctUsed         Percentage of monthly budget used (0-100), or null.
     * @param featuredMessage Active admin featured message, or null.
     * @param lastMessageIdx  Index of the last sent message (for no-repeat), or -1.
     */
    fun selectMessage(
        state: LoggingState,
        isHousehold: Boolean,
        currentStreak: Int,
        streakBrokeYesterday: Boolean,
        pctUsed: Int?,
        featuredMessage: FeaturedMessage?,
        lastMessageIdx: Int
    ): String {
        // 1. Featured message overrides everything.
        featuredMessage?.let { return it.body }

        val json = messages ?: return fallbackMessage(state)

        this.lastMessageIndex = lastMessageIdx

        // 2. Recovery — streak broke yesterday.
        if (streakBrokeYesterday) {
            return pickFromPool(json, "recovery", currentStreak, pctUsed)
        }

        // 3. Milestone — today is a milestone day.
        if (currentStreak in MILESTONE_DAYS) {
            return pickFromPool(json, "milestones", currentStreak, pctUsed)
        }

        // 4. Seasonal — weekend with a small chance.
        val today = LocalDate.now()
        if ((today.dayOfWeek == DayOfWeek.SATURDAY || today.dayOfWeek == DayOfWeek.SUNDAY)
            && Math.random() < SEASONAL_CHANCE
        ) {
            return pickFromPool(json, "seasonal", currentStreak, pctUsed)
        }

        // 5. State pool — the main rotation.
        val stateKey = when (state) {
            LoggingState.NOTHING_LOGGED -> "nothingLogged"
            LoggingState.PARTIALLY_LOGGED -> "partiallyLogged"
            LoggingState.FULLY_LOGGED -> "fullyLogged"
        }
        val groupKey = if (isHousehold) "household" else "other"
        val statePool = json.optJSONObject(stateKey)?.optJSONObject(groupKey) ?: return fallbackMessage(state)

        // Pick a tone sub-pool using weekly rotation.
        val tones = listOf("playful", "warm", "witty", "budgetAware")
        val dayOfYear = today.dayOfYear
        val weekBucket = (dayOfYear / 7) % tones.size
        val toneKey = tones[weekBucket]

        val tonePool = statePool.optJSONArray(toneKey) ?: run {
            // Fallback to any available tone in this group.
            for (t in tones) {
                statePool.optJSONArray(t)?.let { return@run it }
            }
            return fallbackMessage(state)
        }

        if (tonePool.length() == 0) return fallbackMessage(state)

        // Daily shuffle with no-repeat: pick a different index than last time.
        var index = dayOfYear % tonePool.length()
        if (index == lastMessageIndex && tonePool.length() > 1) {
            index = (index + 1) % tonePool.length()
        }

        val raw = tonePool.optString(index)
        if (raw.isEmpty()) return fallbackMessage(state)
        this.lastMessageIndex = index
        return injectPlaceholders(raw, currentStreak, pctUsed)
    }

    /** Returns the index of the last selected message (for persistence by caller). */
    fun lastSelectedIndex(): Int = lastMessageIndex

    // ─── Internal helpers ────────────────────────────────────────────

    private fun pickFromPool(
        json: JSONObject,
        poolKey: String,
        streak: Int,
        pctUsed: Int?
    ): String {
        val pool = json.optJSONArray(poolKey) ?: return fallbackMessage(LoggingState.NOTHING_LOGGED)
        if (pool.length() == 0) return fallbackMessage(LoggingState.NOTHING_LOGGED)

        val today = LocalDate.now()
        val index = today.dayOfYear % pool.length()
        val raw = pool.optString(index)
        if (raw.isEmpty()) return fallbackMessage(LoggingState.NOTHING_LOGGED)
        // Update lastMessageIndex for no-repeat rotation across days.
        this.lastMessageIndex = index
        return injectPlaceholders(raw, streak, pctUsed)
    }

    private fun injectPlaceholders(message: String, streak: Int, pctUsed: Int?): String {
        var result = message.replace("{streak}", streak.toString())
        if (pctUsed != null) {
            result = result.replace("{pctUsed}", pctUsed.toString())
        }
        return result
    }

    private fun loadMessages(): JSONObject? {
        return try {
            context.assets.open(ASSET_FILE).use { stream ->
                val json = stream.bufferedReader().use { it.readText() }
                JSONObject(json)
            }
        } catch (e: Exception) {
            Logger.w(TAG, "Failed to load $ASSET_FILE, using fallback strings", e)
            null
        }
    }

    private fun fallbackMessage(state: LoggingState): String {
        return when (state) {
            LoggingState.NOTHING_LOGGED ->
                context.getString(com.trevio.android.R.string.reminder_fallback_nothing)
            LoggingState.PARTIALLY_LOGGED ->
                context.getString(com.trevio.android.R.string.reminder_fallback_partial)
            LoggingState.FULLY_LOGGED ->
                context.getString(com.trevio.android.R.string.reminder_fallback_full)
        }
    }
}
