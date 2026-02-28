package com.dark.tool_neuron.ui.theme

import android.annotation.SuppressLint
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun rDp(
    baseDp: Dp, designWidth: Float = 360f, minDp: Dp? = null, maxDp: Dp? = null
): Dp {
    val config = LocalConfiguration.current
    val screenWidthDp = config.screenWidthDp.toFloat()

    // Prevent division by zero and handle edge cases
    if (designWidth <= 0f || screenWidthDp <= 0f) return baseDp

    val scaleFactor = screenWidthDp / designWidth

    // Apply scaling with optional clamping
    var scaledValue = baseDp.value * scaleFactor

    // Clamp to min/max if provided
    minDp?.let { scaledValue = maxOf(scaledValue, it.value) }
    maxDp?.let { scaledValue = minOf(scaledValue, it.value) }

    return scaledValue.dp
}

@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun rSp(
    baseSp: TextUnit,
    designWidth: Float = 360f,
    minSp: TextUnit? = null,
    maxSp: TextUnit? = null,
    respectFontScale: Boolean = true
): TextUnit {
    // Handle non-SP units gracefully
    if (baseSp.type != TextUnitType.Sp) return baseSp

    val config = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthDp = config.screenWidthDp.toFloat()

    // Prevent division by zero and handle edge cases
    if (designWidth <= 0f || screenWidthDp <= 0f) return baseSp

    val scale = screenWidthDp / designWidth

    // Scaled value (still in sp units)
    var scaledValue = baseSp.value * scale

    // If you want to IGNORE accessibility fontScale (rare), neutralize it
    if (!respectFontScale) {
        val fontScale = density.fontScale.coerceAtLeast(0.001f)
        scaledValue /= fontScale
    }

    // Clamp if provided - fixed: use minOf instead of min for consistency
    minSp?.let {
        if (it.type == TextUnitType.Sp) {
            scaledValue = maxOf(scaledValue, it.value)
        }
    }
    maxSp?.let {
        if (it.type == TextUnitType.Sp) {
            scaledValue = minOf(scaledValue, it.value)
        }
    }

    return scaledValue.sp
}



@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NeuroVerseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val activeConfig by ThemeEngine.activeTheme.collectAsState()

    val isDefault = activeConfig.id == "default"

    val colorScheme = if (isDefault) {
        // Preserve original dynamic color behavior for the default theme
        if (darkTheme) {
            dynamicDarkColorScheme(context)
        } else {
            dynamicLightColorScheme(context)
        }
    } else {
        ThemeEngine.buildColorScheme(activeConfig, isDark = darkTheme)
    }

    val shapes = if (isDefault) {
        MaterialTheme.shapes
    } else {
        ThemeEngine.buildShapes(activeConfig)
    }

    val fontFamily = ThemeEngine.getFontFamily(activeConfig)

    // Build typography with the selected font family
    val baseTypography = Typography()
    val letterSpacing = activeConfig.letterSpacing.sp
    val typography = baseTypography.copy(
        displayLarge = baseTypography.displayLarge.copy(fontFamily = fontFamily, letterSpacing = letterSpacing),
        displayMedium = baseTypography.displayMedium.copy(fontFamily = fontFamily, letterSpacing = letterSpacing),
        displaySmall = baseTypography.displaySmall.copy(fontFamily = fontFamily, letterSpacing = letterSpacing),
        headlineLarge = baseTypography.headlineLarge.copy(fontFamily = fontFamily, letterSpacing = letterSpacing),
        headlineMedium = baseTypography.headlineMedium.copy(fontFamily = fontFamily, letterSpacing = letterSpacing),
        headlineSmall = baseTypography.headlineSmall.copy(fontFamily = fontFamily, letterSpacing = letterSpacing),
        titleLarge = baseTypography.titleLarge.copy(fontFamily = fontFamily, letterSpacing = letterSpacing),
        titleMedium = baseTypography.titleMedium.copy(fontFamily = fontFamily, letterSpacing = letterSpacing),
        titleSmall = baseTypography.titleSmall.copy(fontFamily = fontFamily, letterSpacing = letterSpacing),
        bodyLarge = baseTypography.bodyLarge.copy(fontFamily = fontFamily, letterSpacing = letterSpacing),
        bodyMedium = baseTypography.bodyMedium.copy(fontFamily = fontFamily, letterSpacing = letterSpacing),
        bodySmall = baseTypography.bodySmall.copy(fontFamily = fontFamily, letterSpacing = letterSpacing),
        labelLarge = baseTypography.labelLarge.copy(fontFamily = fontFamily, letterSpacing = letterSpacing),
        labelMedium = baseTypography.labelMedium.copy(fontFamily = fontFamily, letterSpacing = letterSpacing),
        labelSmall = baseTypography.labelSmall.copy(fontFamily = fontFamily, letterSpacing = letterSpacing),
    )

    CompositionLocalProvider(LocalThemeConfig provides activeConfig) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = shapes,
            typography = typography,
            motionScheme = MotionScheme.expressive(),
            content = content
        )
    }
}