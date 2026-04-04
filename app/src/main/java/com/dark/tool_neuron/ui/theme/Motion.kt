package com.dark.tool_neuron.ui.theme

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme.LocalMaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavBackStackEntry

object Motion {

    private val iosEasing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)

    // Interactive press/toggle feedback — snappy with slight bounce
    fun <T> interactive(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 0.7f, stiffness = 500f
    )

    // Content appear/disappear, expand/collapse
    fun <T> content(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 0.9f, stiffness = Spring.StiffnessMedium
    )

    // State changes — color, alpha, size
    fun <T> state(): FiniteAnimationSpec<T> = tween(
        durationMillis = 200, easing = iosEasing
    )

    // Page/modal entrance
    fun <T> entrance(): FiniteAnimationSpec<T> = tween(
        durationMillis = 350, easing = iosEasing
    )

    // Exit — faster than entrance
    fun <T> exit(): FiniteAnimationSpec<T> = tween(
        durationMillis = 200, easing = iosEasing
    )
}

// Returned by rememberVisibilityTransitions() for use in AnimatedVisibility.
data class VisibilityTransitions(
    val enter: EnterTransition,
    val exit: ExitTransition,
)

// Holds the four NavHost transition lambdas as a single reusable unit.
data class NavTransitions(
    val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition,
    val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition,
    val popEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition,
    val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition,
)

/*
 * Scheme-aware fade transitions for AnimatedVisibility.
 * Fade-only keeps it generic — callers can combine with their own
 * slide/expand if needed (e.g. enter + expandVertically(...)).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun rememberVisibilityTransitions(): VisibilityTransitions {
    val scheme = LocalMaterialTheme.current.motionScheme
    return remember(scheme) {
        VisibilityTransitions(
            enter = fadeIn(scheme.fastEffectsSpec()),
            exit  = fadeOut(scheme.fastEffectsSpec()),
        )
    }
}

/*
 * Scheme-aware fade + scale transitions for NavHost.
 * Scale (0.95 ↔ 1.0) gives a Z-axis depth feel without implying
 * left-right hierarchy — correct for non-hierarchical navigation.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun rememberNavTransitions(): NavTransitions {
    val scheme = LocalMaterialTheme.current.motionScheme
    return remember(scheme) {
        NavTransitions(
            enter = {
                scaleIn(scheme.defaultSpatialSpec(), initialScale = 0.95f) +
                        fadeIn(scheme.fastEffectsSpec())
            },
            exit = {
                scaleOut(scheme.defaultSpatialSpec(), targetScale = 0.95f) +
                        fadeOut(scheme.fastEffectsSpec())
            },
            popEnter = {
                scaleIn(scheme.defaultSpatialSpec(), initialScale = 0.95f) +
                        fadeIn(scheme.fastEffectsSpec())
            },
            popExit = {
                scaleOut(scheme.defaultSpatialSpec(), targetScale = 0.95f) +
                        fadeOut(scheme.fastEffectsSpec())
            },
        )
    }
}
