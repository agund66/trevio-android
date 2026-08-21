package com.trevio.android.core.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.material3.Text
import java.util.Locale

/**
 * An animated numeric counter that tweens from the previous value to the target [Double] value.
 *
 * Designed for currency displays (e.g. settlement amounts, balances). The value
 * is animated over 1 second using [FastOutSlowInEasing] for a natural deceleration.
 *
 * On first appearance the counter animates from 0 (controlled by [animateOnFirstAppearance]).
 * On subsequent recompositions with the same value, no animation runs. When the value
 * changes while on screen, it animates from the old value to the new one.
 *
 * @param targetValue The final value to animate towards.
 * @param prefix A string prepended to the formatted number (e.g. "₹").
 * @param decimals Number of decimal places to render.
 * @param fontSize Font size of the rendered text.
 * @param fontWeight Font weight of the rendered text.
 * @param color Color of the rendered text.
 * @param animateOnFirstAppearance Whether to animate from 0 on first composition (default true).
 */
@Composable
fun AnimatedCounter(
    targetValue: Double,
    prefix: String = "₹",
    decimals: Int = 0,
    fontSize: TextUnit,
    fontWeight: FontWeight,
    color: Color,
    animateOnFirstAppearance: Boolean = true
) {
    var previousValue by remember { mutableStateOf<Double?>(null) }
    val animatable = remember { Animatable(if (animateOnFirstAppearance) 0f else targetValue.toFloat()) }

    LaunchedEffect(targetValue) {
        val startValue = previousValue ?: if (animateOnFirstAppearance) 0.0 else targetValue
        if (startValue != targetValue) {
            animatable.snapTo(startValue.toFloat())
            animatable.animateTo(
                targetValue.toFloat(),
                animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
            )
        } else {
            animatable.snapTo(targetValue.toFloat())
        }
        previousValue = targetValue
    }

    val formatted = String.format(Locale.getDefault(), "%,.${decimals}f", animatable.value)
    Text(
        text = "$prefix$formatted",
        fontSize = fontSize,
        fontWeight = fontWeight,
        color = color,
        style = TextStyle(fontFeatureSettings = "tnum")
    )
}

/**
 * An animated numeric counter that tweens from the previous value to the target [Int] value.
 *
 * Designed for count displays (e.g. number of members, transactions, streaks).
 * The value is animated over 1 second using [FastOutSlowInEasing].
 *
 * On first appearance the counter animates from 0 (controlled by [animateOnFirstAppearance]).
 * On subsequent recompositions with the same value, no animation runs. When the value
 * changes while on screen, it animates from the old value to the new one.
 *
 * @param targetValue The final integer value to animate towards.
 * @param suffix A string appended to the formatted number (e.g. "x", " pts").
 * @param fontSize Font size of the rendered text.
 * @param fontWeight Font weight of the rendered text.
 * @param color Color of the rendered text.
 * @param animateOnFirstAppearance Whether to animate from 0 on first composition (default true).
 */
@Composable
fun AnimatedCounter(
    targetValue: Int,
    suffix: String = "",
    fontSize: TextUnit,
    fontWeight: FontWeight,
    color: Color,
    animateOnFirstAppearance: Boolean = true
) {
    var previousValue by remember { mutableStateOf<Int?>(null) }
    val animatable = remember { Animatable(if (animateOnFirstAppearance) 0f else targetValue.toFloat()) }

    LaunchedEffect(targetValue) {
        val startValue = previousValue ?: if (animateOnFirstAppearance) 0 else targetValue
        if (startValue != targetValue) {
            animatable.snapTo(startValue.toFloat())
            animatable.animateTo(
                targetValue.toFloat(),
                animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
            )
        } else {
            animatable.snapTo(targetValue.toFloat())
        }
        previousValue = targetValue
    }

    val formatted = String.format(Locale.getDefault(), "%,.0f", animatable.value)
    Text(
        text = "$formatted$suffix",
        fontSize = fontSize,
        fontWeight = fontWeight,
        color = color,
        style = TextStyle(fontFeatureSettings = "tnum")
    )
}
