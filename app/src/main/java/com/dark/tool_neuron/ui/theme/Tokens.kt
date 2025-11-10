package com.dark.tool_neuron.ui.theme

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object NVSpacing {
    @get:Composable
    val xs get() = rDP(4.dp)

    @get:Composable
    val sm get() = rDP(8.dp)

    @get:Composable
    val md get() = rDP(12.dp)

    @get:Composable
    val lg get() = rDP(16.dp)
}

object NVRadius {
    @get:Composable
    val card get() = rDP(14.dp)

    @get:Composable
    val chip get() = rDP(10.dp)
}

fun dpToPx(context: Context, dp: Dp): Int {
    val scale = context.resources.displayMetrics.density
    return (dp.value * scale + 0.5f).toInt()
}

data class ActionColors(val bg: Color, val fg: Color, val outline: Color)

@Composable
fun actionColorsPrimary() =
    with(MaterialTheme.colorScheme) { ActionColors(primary, onPrimary, outline) }

@Composable
fun actionColorsDanger() = with(MaterialTheme.colorScheme) { ActionColors(error, onError, outline) }

@Composable
fun actionColorsNeutral() =
    with(MaterialTheme.colorScheme) { ActionColors(surface, onSurface, outline) }
