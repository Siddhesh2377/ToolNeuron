package com.dark.tool_neuron.ui.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.Alignment

// Centralized animation tokens — use these instead of inline spring()/tween() calls.
object Motion {

    // iOS ease curve
    private val iosEasing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)

    // Interactive press/toggle feedback — snappy with slight bounce
    fun <T> interactive(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 0.7f,
        stiffness = 500f
    )

    // Content appear/disappear, expand/collapse
    fun <T> content(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 0.9f,
        stiffness = Spring.StiffnessMedium
    )

    // State changes — color, alpha, size
    fun <T> state(): FiniteAnimationSpec<T> = tween(
        durationMillis = 200,
        easing = iosEasing
    )

    // Page/modal entrance — iOS-style curve
    fun <T> entrance(): FiniteAnimationSpec<T> = tween(
        durationMillis = 350,
        easing = iosEasing
    )

    // Exit — faster than entrance
    fun <T> exit(): FiniteAnimationSpec<T> = tween(
        durationMillis = 200,
        easing = iosEasing
    )

    // Standard enter transition for AnimatedVisibility
    val Enter: EnterTransition = fadeIn(tween(300)) +
        expandVertically(tween(300), expandFrom = Alignment.Top)

    // Standard exit transition for AnimatedVisibility
    val Exit: ExitTransition = fadeOut(tween(200)) +
        shrinkVertically(tween(200), shrinkTowards = Alignment.Top)
}
