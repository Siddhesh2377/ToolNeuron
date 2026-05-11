package com.dark.tool_neuron.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration

private const val EXPANDED_WIDTH_THRESHOLD_DP = 840

val LocalIsExpandedLayout = staticCompositionLocalOf { false }

@Composable
@ReadOnlyComposable
fun rememberIsExpandedScreen(): Boolean {
    val config = LocalConfiguration.current
    return config.screenWidthDp >= EXPANDED_WIDTH_THRESHOLD_DP
}

@Composable
fun ProvideWindowMetrics(content: @Composable () -> Unit) {
    val isExpanded = rememberIsExpandedScreen()
    CompositionLocalProvider(LocalIsExpandedLayout provides isExpanded) {
        content()
    }
}
