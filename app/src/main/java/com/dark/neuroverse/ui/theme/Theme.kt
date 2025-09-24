package com.dark.neuroverse.ui.theme

import android.annotation.SuppressLint
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isUnspecified
import androidx.compose.ui.unit.sp
import kotlin.math.min


private val DarkColorScheme = darkColorScheme(
    primary = White,
    onPrimary = Black,
    secondary = Grey,
    background = Black,
    onBackground = White,
    surface = LightBlack,
    onSurface = White,
    primaryContainer = PrimaryContainer
)

private val LightColorScheme = lightColorScheme(
    primary = Black,
    onPrimary = White,
    secondary = Grey,
    background = SoftWhite,
    onBackground = Black,
    surface = White,
    onSurface = Black,
    primaryContainer = PrimaryContainer
)

/**
 * Scales a base dp size based on current screen width (responsive).
 *
 * @param baseDp The original size you designed for (e.g., 360dp width screen).
 * @param designWidth The screen width your design is based on (default 360dp).
 */
@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun rDP(baseDp: Dp, designWidth: Float = 360f): Dp {
    val config = LocalConfiguration.current
    val screenWidthDp = config.screenWidthDp.toFloat()
    val scaleFactor = screenWidthDp / designWidth
    return (baseDp.value * scaleFactor).dp
}

// responsive sp (width-based), keeps system fontScale by default
@Composable
fun rSp(
    baseSp: TextUnit,
    designWidth: Float = 360f,
    minSp: TextUnit? = null,
    maxSp: TextUnit? = null,
    respectFontScale: Boolean = true
): TextUnit {
    val config = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthDp = config.screenWidthDp.toFloat()
    val scale = screenWidthDp / designWidth

    // scaled value (still in sp units)
    var v = baseSp.value * scale

    // if you want to IGNORE accessibility fontScale (rare), neutralize it:
    if (!respectFontScale) {
        v /= density.fontScale.coerceAtLeast(0.001f)
    }

    // clamp if provided
    minSp?.let { v = maxOf(v, it.value) }
    maxSp?.let { v = min(v, maxSp.value) }

    return v.sp
}

// optional: shortest-dimension scaling (better for orientation changes)
@Composable
fun rSpShortest(baseSp: TextUnit, designShortSide: Float = 360f): TextUnit {
    val cfg = LocalConfiguration.current
    val shortSide = min(cfg.screenWidthDp, cfg.screenHeightDp).toFloat()
    val scale = shortSide / designShortSide
    return (baseSp.value * scale).sp
}

// convenience to scale an existing TextStyle’s fontSize if set
@Composable
fun TextStyle.scaled(designWidth: Float = 360f): TextStyle =
    if (fontSize.isUnspecified) this else copy(fontSize = rSp(fontSize, designWidth))


@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NeuroVerseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        motionScheme = MotionScheme.expressive(),
        content = content
    )
}
