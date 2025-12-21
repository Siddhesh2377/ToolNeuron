package com.dark.tool_neuron.ui.screens.modelScreen

import kotlin.math.roundToInt

fun formatNumber(num: Int): String {
    return when {
        num >= 1000 -> "${(num / 1000.0).roundToInt()}K"
        else -> num.toString()
    }
}