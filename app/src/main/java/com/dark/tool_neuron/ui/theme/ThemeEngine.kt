package com.dark.tool_neuron.ui.theme

import android.content.Context
import android.util.Log
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

val LocalThemeConfig = compositionLocalOf { ThemeConfig.DEFAULT }

object ThemeEngine {

    private const val TAG = "ThemeEngine"
    private const val THEMES_DIR = "themes"

    private val json = Json { ignoreUnknownKeys = true }

    private val _activeTheme = MutableStateFlow(ThemeConfig.DEFAULT)
    val activeTheme: StateFlow<ThemeConfig> = _activeTheme.asStateFlow()

    private val _availableThemes = MutableStateFlow(listOf(ThemeConfig.DEFAULT))
    val availableThemes: StateFlow<List<ThemeConfig>> = _availableThemes.asStateFlow()

    fun init(context: Context) {
        val themes = mutableListOf<ThemeConfig>()
        try {
            val themeFiles = context.assets.list(THEMES_DIR) ?: emptyArray()
            for (fileName in themeFiles) {
                if (!fileName.endsWith(".json")) continue
                try {
                    val jsonString = context.assets.open("$THEMES_DIR/$fileName")
                        .bufferedReader()
                        .use { it.readText() }
                    val config = json.decodeFromString<ThemeConfig>(jsonString)
                    themes.add(config)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse theme file: $fileName", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list theme files from assets", e)
        }

        if (themes.isEmpty()) {
            themes.add(ThemeConfig.DEFAULT)
        }

        _availableThemes.value = themes
        // Keep current active theme if it still exists, otherwise fall back to default
        val currentId = _activeTheme.value.id
        val resolved = themes.find { it.id == currentId } ?: themes.first()
        _activeTheme.value = resolved
    }

    fun applyTheme(themeId: String) {
        val theme = _availableThemes.value.find { it.id == themeId }
        if (theme != null) {
            _activeTheme.value = theme
        } else {
            Log.w(TAG, "Theme not found: $themeId")
        }
    }

    fun buildColorScheme(config: ThemeConfig, isDark: Boolean): ColorScheme {
        val c = config.colors
        val primary = parseHexColor(c.primary)
        val onPrimary = parseHexColor(c.onPrimary)
        val background = parseHexColor(c.background)
        val surface = parseHexColor(c.surface)
        val surfaceVariant = parseHexColor(c.surfaceVariant)
        val onBackground = parseHexColor(c.onBackground)
        val onSurface = parseHexColor(c.onSurface)
        val onSurfaceVariant = parseHexColor(c.onSurfaceVariant)
        val error = parseHexColor(c.error)
        val primaryContainer = if (c.primaryContainer != null) {
            parseHexColor(c.primaryContainer)
        } else {
            primary.copy(alpha = 0.3f)
        }
        val secondaryContainer = if (c.secondaryContainer != null) {
            parseHexColor(c.secondaryContainer)
        } else {
            surfaceVariant
        }

        return darkColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = primary,
            secondary = primary,
            onSecondary = onPrimary,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = onSurface,
            tertiary = primary,
            onTertiary = onPrimary,
            background = background,
            onBackground = onBackground,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant,
            error = error,
            onError = Color.White,
            errorContainer = error.copy(alpha = 0.3f),
            onErrorContainer = error,
            outline = onSurfaceVariant,
            outlineVariant = surfaceVariant,
            inverseSurface = onSurface,
            inverseOnSurface = surface,
            inversePrimary = primary,
            surfaceTint = primary
        )
    }

    fun buildShapes(config: ThemeConfig): Shapes {
        val radius = config.cornerRadius.dp
        return Shapes(
            extraSmall = RoundedCornerShape(radius * 0.25f),
            small = RoundedCornerShape(radius * 0.5f),
            medium = RoundedCornerShape(radius),
            large = RoundedCornerShape(radius * 1.5f),
            extraLarge = RoundedCornerShape(radius * 2f)
        )
    }

    fun getFontFamily(config: ThemeConfig): FontFamily {
        return when (config.fontFamily) {
            "manrope" -> ManropeFontFamily
            "jetbrains_mono", "maple_mono", "monospace" -> maple
            "system" -> FontFamily.Default
            else -> ManropeFontFamily
        }
    }
}

fun parseHexColor(hex: String): Color {
    val sanitized = hex.removePrefix("#")
    val colorLong = when (sanitized.length) {
        6 -> (0xFF000000 or sanitized.toLong(16))
        8 -> sanitized.toLong(16)
        else -> 0xFF000000 // fallback to black
    }
    return Color(colorLong.toInt())
}
