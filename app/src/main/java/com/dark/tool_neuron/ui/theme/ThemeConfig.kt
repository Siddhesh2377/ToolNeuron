package com.dark.tool_neuron.ui.theme

import kotlinx.serialization.Serializable

@Serializable
data class ThemeConfig(
    val id: String,
    val name: String,
    val author: String = "ToolNeuron",
    val version: Int = 1,
    val colors: ThemeColors,
    val fontFamily: String = "manrope",
    val useUppercaseLabels: Boolean = false,
    val letterSpacing: Float = 0f,
    val cornerRadius: Float = 12f,
    val accentBorderWidth: Float = 0f,
    val showCornerBrackets: Boolean = false,
    val showGridBackground: Boolean = false,
    val scrollbarWidth: Float = 4f,
    val squareStatusDots: Boolean = false
) {
    companion object {
        val DEFAULT = ThemeConfig(
            id = "default",
            name = "Default",
            colors = ThemeColors(
                background = "#121212",
                surface = "#1e1e1e",
                surfaceVariant = "#2d2d2d",
                onBackground = "#e0e0e0",
                onSurface = "#e0e0e0",
                onSurfaceVariant = "#a0a0a0",
                primary = "#bb86fc",
                onPrimary = "#000000"
            )
        )
    }
}

@Serializable
data class ThemeColors(
    val background: String,
    val surface: String,
    val surfaceVariant: String,
    val onBackground: String,
    val onSurface: String,
    val onSurfaceVariant: String,
    val primary: String,
    val onPrimary: String,
    val error: String = "#ef4444",
    val success: String = "#22c55e",
    val info: String = "#3b82f6",
    val primaryContainer: String? = null,
    val secondaryContainer: String? = null
)
