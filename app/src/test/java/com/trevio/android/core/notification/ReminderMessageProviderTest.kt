package com.trevio.android.core.notification

import android.content.Context
import android.content.res.AssetManager
import com.google.common.truth.Truth.assertThat
import com.trevio.android.domain.model.FeaturedMessage
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.io.ByteArrayInputStream

class ReminderMessageProviderTest {

    private val mockContext: Context = mock(Context::class.java)
    private val mockAssets: AssetManager = mock(AssetManager::class.java)

    private val sampleJson = """
    {
      "version": 1,
      "nothingLogged": {
        "household": {
          "playful": ["Fridge knows", "Wallet called", "Money ghosting"],
          "warm": ["Take two minutes", "Small habits"],
          "witty": ["Ignoring expenses", "Procrastination is free"],
          "budgetAware": ["You're {pctUsed}% into budget", "Budget check {pctUsed}%"]
        },
        "other": {
          "playful": ["Mystery spending", "Wallet crying"],
          "warm": ["Quick log tonight"],
          "witty": ["Unlogged expenses"],
          "budgetAware": ["{pctUsed}% of group budget"]
        }
      },
      "partiallyLogged": {
        "household": {
          "playful": ["Nice start", "Halfway there"],
          "warm": ["Good job"],
          "witty": ["Some is heavy lifting"],
          "budgetAware": ["{pctUsed}% budget, partial"]
        },
        "other": {
          "playful": ["Good start"],
          "warm": ["Making progress"],
          "witty": ["Partial log"],
          "budgetAware": ["{pctUsed}% partial"]
        }
      },
      "fullyLogged": {
        "household": {
          "playful": ["Books closed", "Perfect evening"],
          "warm": ["Beautifully done"],
          "witty": ["Wallet blushing"],
          "budgetAware": ["{pctUsed}% in control"]
        },
        "other": {
          "playful": ["Today's books closed"],
          "warm": ["Well done"],
          "witty": ["Organized and intimidated"],
          "budgetAware": ["{pctUsed}% controlled"]
        }
      },
      "milestones": ["One whole week!", "30 days legendary"],
      "recovery": ["Streak broke. No biggie.", "New beginning."],
      "seasonal": ["Weekend spending hits different."]
    }
    """.trimIndent()

    private fun createProvider(json: String = sampleJson): ReminderMessageProvider {
        val stream = ByteArrayInputStream(json.toByteArray())
        `when`(mockContext.assets).thenReturn(mockAssets)
        `when`(mockAssets.open("reminder_messages.json")).thenReturn(stream)
        return ReminderMessageProvider(mockContext)
    }

    @Test
    fun featuredMessage_overridesEverything() {
        val provider = createProvider()
        val featured = FeaturedMessage(body = "Admin custom message", startAt = 0, endAt = Long.MAX_VALUE)
        val result = provider.selectMessage(
            state = LoggingState.NOTHING_LOGGED,
            isHousehold = true,
            currentStreak = 5,
            streakBrokeYesterday = false,
            pctUsed = 50,
            featuredMessage = featured,
            lastMessageIdx = -1
        )
        assertThat(result).isEqualTo("Admin custom message")
    }

    @Test
    fun recoveryMessage_whenStreakBrokeYesterday() {
        val provider = createProvider()
        val result = provider.selectMessage(
            state = LoggingState.NOTHING_LOGGED,
            isHousehold = true,
            currentStreak = 0,
            streakBrokeYesterday = true,
            pctUsed = null,
            featuredMessage = null,
            lastMessageIdx = -1
        )
        // Should be from the recovery pool
        assertThat(result).isAnyOf("Streak broke. No biggie.", "New beginning.")
    }

    @Test
    fun milestoneMessage_onMilestoneDay() {
        val provider = createProvider()
        val result = provider.selectMessage(
            state = LoggingState.FULLY_LOGGED,
            isHousehold = true,
            currentStreak = 7,
            streakBrokeYesterday = false,
            pctUsed = null,
            featuredMessage = null,
            lastMessageIdx = -1
        )
        // Should be from the milestones pool
        assertThat(result).isAnyOf("One whole week!", "30 days legendary")
    }

    @Test
    fun placeholderInjection_streakAndPctUsed() {
        val provider = createProvider()
        val result = provider.selectMessage(
            state = LoggingState.NOTHING_LOGGED,
            isHousehold = true,
            currentStreak = 5,
            streakBrokeYesterday = false,
            pctUsed = 65,
            featuredMessage = null,
            lastMessageIdx = -1
        )
        // The result should not contain unreplaced placeholders
        assertThat(result).doesNotContain("{streak}")
        assertThat(result).doesNotContain("{pctUsed}")
    }

    @Test
    fun fallbackString_whenJsonFailsToLoad() {
        // Mock context that throws when loading asset
        `when`(mockContext.assets).thenReturn(mockAssets)
        `when`(mockAssets.open("reminder_messages.json"))
            .thenThrow(java.io.IOException("File not found"))
        `when`(mockContext.getString(com.trevio.android.R.string.reminder_fallback_nothing))
            .thenReturn("Fallback: log your spending")
        `when`(mockContext.getString(com.trevio.android.R.string.reminder_fallback_partial))
            .thenReturn("Fallback: complete your log")
        `when`(mockContext.getString(com.trevio.android.R.string.reminder_fallback_full))
            .thenReturn("Fallback: well done")

        val provider = ReminderMessageProvider(mockContext)
        val result = provider.selectMessage(
            state = LoggingState.NOTHING_LOGGED,
            isHousehold = true,
            currentStreak = 0,
            streakBrokeYesterday = false,
            pctUsed = null,
            featuredMessage = null,
            lastMessageIdx = -1
        )
        assertThat(result).isEqualTo("Fallback: log your spending")
    }

    @Test
    fun noRepeat_consecutiveCallsReturnDifferentMessages() {
        val provider = createProvider()
        val result1 = provider.selectMessage(
            state = LoggingState.NOTHING_LOGGED,
            isHousehold = true,
            currentStreak = 3,
            streakBrokeYesterday = false,
            pctUsed = null,
            featuredMessage = null,
            lastMessageIdx = -1
        )
        val lastIdx = provider.lastSelectedIndex()
        val result2 = provider.selectMessage(
            state = LoggingState.NOTHING_LOGGED,
            isHousehold = true,
            currentStreak = 3,
            streakBrokeYesterday = false,
            pctUsed = null,
            featuredMessage = null,
            lastMessageIdx = lastIdx
        )
        // If the pool has more than 1 message, the two results should differ
        if (lastIdx >= 0) {
            assertThat(provider.lastSelectedIndex()).isNotEqualTo(lastIdx)
        }
    }
}
