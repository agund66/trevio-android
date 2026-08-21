package com.trevio.android.core.designsystem.animation

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable

/**
 * Shared animation specs and transition helpers used across Trevio screens.
 *
 * Centralising these here keeps motion language consistent and makes it easy
 * to tweak the feel of the whole app from one place.
 */
object AnimationSpecs {

    /** Standard enter transition: fade + subtle slide in from the right. */
    val enterTransition =
        fadeIn(animationSpec = tween(300)) +
            slideInHorizontally(animationSpec = tween(300), initialOffsetX = { it / 8 })

    /** Standard exit transition: fade + subtle slide out to the left. */
    val exitTransition =
        fadeOut(animationSpec = tween(200)) +
            slideOutHorizontally(animationSpec = tween(200), targetOffsetX = { -it / 8 })

    /** Pop/scale enter transition for dialogs and modal sheets. */
    val popEnterTransition =
        fadeIn(animationSpec = tween(300)) +
            scaleIn(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy))

    /** Pop/scale exit transition for dialogs and modal sheets. */
    val popExitTransition =
        fadeOut(animationSpec = tween(200)) +
            scaleOut(animationSpec = tween(200))

    /** Lighter slide enter for tab content swaps (eighth width). */
    val tabSlideEnter =
        fadeIn(animationSpec = tween(250)) +
            slideInHorizontally(animationSpec = tween(250), initialOffsetX = { it / 8 })

    /** Lighter slide exit for tab content swaps (eighth width). */
    val tabSlideExit =
        fadeOut(animationSpec = tween(200)) +
            slideOutHorizontally(animationSpec = tween(200), targetOffsetX = { -it / 8 })
}

/**
 * Computes a staggered delay for the item at [index].
 *
 * Use this when animating lists of items in one-by-one so each subsequent
 * item starts [baseDelay] ms later than the previous one.
 *
 * Example:
 * ```
 * val delay = staggeredDelay(index)
 * LaunchedEffect(Unit) {
 *     delay(delay.toLong())
 *     visible = true
 * }
 * ```
 *
 * @param index Zero-based index of the item in the list.
 * @param baseDelay Milliseconds between each item. Defaults to 50ms.
 * @return The delay in milliseconds before this item should animate.
 */
@Composable
fun staggeredDelay(index: Int, baseDelay: Int = 50): Int {
    return baseDelay * index
}
