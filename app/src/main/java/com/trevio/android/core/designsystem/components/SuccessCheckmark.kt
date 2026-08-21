package com.trevio.android.core.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.trevio.android.core.designsystem.theme.TrevioSuccess
import kotlinx.coroutines.launch

/**
 * An animated success checkmark composable.
 *
 * A green circle draws in first (arc sweep from 0→1), then a white checkmark
 * path draws on top with a spring "pop" for a satisfying confirmation effect.
 *
 * Use this after successful actions like settling up, saving an expense, or
 * completing a group creation flow.
 *
 * @param visible When true, the animation plays. When false, nothing is drawn.
 * @param onAnimationComplete Callback invoked once the checkmark finishes drawing.
 * @param modifier Modifier for the composable.
 * @param size The diameter of the checkmark circle.
 * @param circleColor Color of the background circle.
 * @param checkmarkColor Color of the checkmark stroke.
 */
@Composable
fun SuccessCheckmark(
    visible: Boolean,
    onAnimationComplete: () -> Unit = {},
    modifier: Modifier = Modifier,
    size: Dp = 80.dp,
    circleColor: Color = TrevioSuccess,
    checkmarkColor: Color = Color.White
) {
    // Circle draw progress: 0f → 1f
    val circleProgress = remember { Animatable(0f) }
    // Checkmark draw progress: 0f → 1f
    val checkmarkProgress = remember { Animatable(0f) }

    LaunchedEffect(visible) {
        if (visible) {
            // Reset before playing so re-triggering works.
            circleProgress.snapTo(0f)
            checkmarkProgress.snapTo(0f)

            // Draw the circle first.
            circleProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing)
            )
            // Then pop the checkmark with a spring for a satisfying finish.
            checkmarkProgress.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
            onAnimationComplete()
        }
    }

    if (!visible) return

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val diameter = this.size.minDimension
            val radius = diameter / 2f
            val center = Offset(this.size.width / 2f, this.size.height / 2f)

            // --- Circle ---
            if (circleProgress.value > 0f) {
                drawArc(
                    color = circleColor,
                    startAngle = -90f,
                    sweepAngle = 360f * circleProgress.value,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = androidx.compose.ui.geometry.Size(diameter, diameter),
                    style = Stroke(width = diameter * 0.12f, cap = StrokeCap.Round)
                )
                // Fill once the outline completes for a solid badge look.
                if (circleProgress.value >= 1f) {
                    drawCircle(
                        color = circleColor,
                        radius = radius,
                        center = center
                    )
                }
            }

            // --- Checkmark ---
            if (checkmarkProgress.value > 0f) {
                val stroke = diameter * 0.10f
                // Checkmark vertices relative to the circle.
                val p1 = Offset(center.x - radius * 0.38f, center.y + radius * 0.02f)
                val p2 = Offset(center.x - radius * 0.08f, center.y + radius * 0.32f)
                val p3 = Offset(center.x + radius * 0.42f, center.y - radius * 0.30f)

                val path = Path().apply {
                    moveTo(p1.x, p1.y)
                    // Draw only up to [checkmarkProgress] of the full path.
                    val t = checkmarkProgress.value.coerceIn(0f, 1f)
                    // Segment 1: p1 → p2 (first ~40% of the stroke)
                    val seg1End = if (t <= 0.4f) {
                        lerp(p1, p2, t / 0.4f)
                    } else {
                        p2
                    }
                    lineTo(seg1End.x, seg1End.y)
                    // Segment 2: p2 → p3 (remaining ~60%)
                    if (t > 0.4f) {
                        val seg2T = (t - 0.4f) / 0.6f
                        val seg2End = lerp(p2, p3, seg2T)
                        lineTo(seg2End.x, seg2End.y)
                    }
                }

                drawPath(
                    path = path,
                    color = checkmarkColor,
                    style = Stroke(width = stroke, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
                )
            }
        }
    }
}

/** Linear interpolation between two [Offset]s. */
private fun lerp(a: Offset, b: Offset, t: Float): Offset {
    return Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
}
