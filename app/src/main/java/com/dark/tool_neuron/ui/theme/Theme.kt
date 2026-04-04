package com.dark.tool_neuron.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.Typography

private val DarkColorScheme = darkColorScheme()

private val LightColorScheme = lightColorScheme()

private val ManropeTypography: Typography by lazy {
    val base = Typography()
    base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = ManropeFontFamily),
        displayMedium = base.displayMedium.copy(fontFamily = ManropeFontFamily),
        displaySmall = base.displaySmall.copy(fontFamily = ManropeFontFamily),
        headlineLarge = base.headlineLarge.copy(fontFamily = ManropeFontFamily),
        headlineMedium = base.headlineMedium.copy(fontFamily = ManropeFontFamily),
        headlineSmall = base.headlineSmall.copy(fontFamily = ManropeFontFamily),
        titleLarge = base.titleLarge.copy(fontFamily = ManropeFontFamily),
        titleMedium = base.titleMedium.copy(fontFamily = ManropeFontFamily),
        titleSmall = base.titleSmall.copy(fontFamily = ManropeFontFamily),
        bodyLarge = base.bodyLarge.copy(fontFamily = ManropeFontFamily),
        bodyMedium = base.bodyMedium.copy(fontFamily = ManropeFontFamily),
        bodySmall = base.bodySmall.copy(fontFamily = ManropeFontFamily),
        labelLarge = base.labelLarge.copy(fontFamily = ManropeFontFamily),
        labelMedium = base.labelMedium.copy(fontFamily = ManropeFontFamily),
        labelSmall = base.labelSmall.copy(fontFamily = ManropeFontFamily),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ToolNeuronTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    // Dynamic color requires Android 12+ (API 31). Older devices get Material3 defaults.
    // Try-catch guards against OEM ROMs that report API 31+ but lack Monet resources —
    // dynamicDarkColorScheme() throws Resources$NotFoundException on these devices.
    val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        try {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } catch (_: Exception) {
            if (darkTheme) DarkColorScheme else LightColorScheme
        }
    } else {
        if (darkTheme) DarkColorScheme else LightColorScheme
    }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        typography = ManropeTypography,
        motionScheme = MotionScheme.expressive(),
        content = content
    )
}