package com.trevio.android.core.designsystem.components

import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * A wrapper that adds a scale-down press animation and optional haptic feedback
 * to any content.
 *
 * While the user presses the content, it scales down to [scaleDown] (default 0.96)
 * with a spring for a tactile, responsive feel. On click, a [HapticFeedbackType]
 * pulse is triggered (unless [hapticEnabled] is false).
 *
 * Example:
 * ```
 * PressableScale(onClick = { viewModel.save() }) {
 *     Text("Save")
 * }
 * ```
 *
 * @param onClick Called when the content is tapped.
 * @param modifier Modifier for the row container.
 * @param scaleDown The scale factor applied while pressed (0 < scaleDown < 1).
 * @param hapticEnabled Whether to emit haptic feedback on click.
 * @param enabled When false, the content is not clickable and no animation or
 *   haptic feedback is triggered. Defaults to true.
 * @param content The content to render, in a [RowScope].
 */
@Composable
fun PressableScale(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    scaleDown: Float = 0.96f,
    hapticEnabled: Boolean = true,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val hapticFeedback = LocalHapticFeedback.current

    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) scaleDown else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "pressableScale"
    )

    Row(
        modifier = modifier
            .scale(scale)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    if (hapticEnabled) {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    onClick()
                }
            ),
        content = content
    )
}
