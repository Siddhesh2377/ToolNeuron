package com.mp.plugin_api.theme

import android.annotation.SuppressLint
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = White,
    onPrimary = Black,
    secondary = Grey,
    background = Black,
    onBackground = White,
    surface = LightBlack,
    onSurface = White
)

private val LightColorScheme = lightColorScheme(
    primary = Black,
    onPrimary = White,
    secondary = Grey,
    background = SoftWhite,
    onBackground = Black,
    surface = White,
    onSurface = Black
)

@SuppressLint("NewApi")
@Composable
fun PluginTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme, typography = Typography(), content = content
    )
}
