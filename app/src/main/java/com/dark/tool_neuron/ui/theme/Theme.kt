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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min


@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun rDP(baseDp: Dp, designWidth: Float = 360f): Dp {
    val config = LocalConfiguration.current
    val screenWidthDp = config.screenWidthDp.toFloat()
    val scaleFactor = screenWidthDp / designWidth
    return (baseDp.value * scaleFactor).dp
}

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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NeuroVerseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    val colorScheme = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        motionScheme = MotionScheme.expressive(), // expressive motion = subtle, fluid animations
        content = content
    )
}

