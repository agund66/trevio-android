package com.trevio.android.core.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import com.trevio.android.core.designsystem.theme.TrevioPrimary
import com.trevio.android.core.designsystem.theme.TrevioSecondary
import com.trevio.android.core.designsystem.theme.TrevioAccent
import com.trevio.android.core.designsystem.theme.BalanceNegative
import com.trevio.android.core.designsystem.theme.TrevioSuccess
import kotlin.random.Random

/**
 * A lightweight confetti animation rendered on a [Canvas].
 *
 * Spawns ~30 confetti particles with random horizontal positions, colors
 * (drawn from a Trevio palette), rotations, and fall speeds. Particles
 * accelerate downward (gravity-like) and rotate as they fall. The animation
 * auto-stops after ~3 seconds and invokes [onComplete].
 *
 * No external libraries are used — everything is drawn with Compose Canvas.
 *
 * @param visible When true, the confetti plays. When false, nothing is drawn.
 * @param onComplete Callback invoked once the confetti finishes (~3s).
 * @param modifier Modifier for the composable. Defaults to fill max size.
 * @param particleCount Number of confetti particles to spawn.
 */
@Composable
fun ConfettiView(
    visible: Boolean,
    onComplete: () -> Unit = {},
    modifier: Modifier = Modifier,
    particleCount: Int = 30
) {
    if (!visible) return

    // Progress 0f → 1f over the animation lifetime.
    val progress = remember { Animatable(0f) }

    // Generate a stable set of particles per visibility trigger.
    val particles = remember(visible) {
        List(particleCount) {
            ConfettiParticle(
                xPercent = Random.nextFloat(),
                color = ConfettiPalette.random(),
                rotationSpeed = Random.nextFloat() * 720f - 360f,
                fallSpeed = 0.6f + Random.nextFloat() * 0.8f,
                horizontalDrift = Random.nextFloat() * 0.2f - 0.1f,
                sizeFraction = 0.012f + Random.nextFloat() * 0.018f,
                shapeIsCircle = Random.nextBoolean()
            )
        }
    }

    LaunchedEffect(visible) {
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 3000, easing = LinearEasing)
        )
        onComplete()
    }

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = this.size.width
            val height = this.size.height
            val p = progress.value

            particles.forEach { particle ->
                // Gravity-like acceleration: position follows p^2 for downward curve.
                val t = p * particle.fallSpeed
                val y = (t * t) * height * 1.2f - height * 0.1f
                val x = particle.xPercent * width + particle.horizontalDrift * width * p
                val rotation = particle.rotationSpeed * p

                if (y > height + 50f) return@forEach

                val pieceSize = this.size.minDimension * particle.sizeFraction

                rotate(degrees = rotation, pivot = Offset(x, y)) {
                    if (particle.shapeIsCircle) {
                        drawCircle(
                            color = particle.color,
                            radius = pieceSize / 2f,
                            center = Offset(x, y)
                        )
                    } else {
                        drawRect(
                            color = particle.color,
                            topLeft = Offset(x - pieceSize / 2f, y - pieceSize / 4f),
                            size = Size(pieceSize, pieceSize / 2f)
                        )
                    }
                }
            }
        }
    }
}

/** Palette of Trevio brand colors used for confetti pieces. */
private object ConfettiPalette {
    private val colors = listOf(
        TrevioPrimary,    // teal
        TrevioSecondary,  // indigo
        TrevioAccent,     // amber
        BalanceNegative,  // rose
        TrevioSuccess     // emerald
    )
    fun random(): Color = colors[Random.nextInt(colors.size)]
}

/** A single confetti particle's static configuration. */
private data class ConfettiParticle(
    val xPercent: Float,
    val color: Color,
    val rotationSpeed: Float,
    val fallSpeed: Float,
    val horizontalDrift: Float,
    val sizeFraction: Float,
    val shapeIsCircle: Boolean
)
