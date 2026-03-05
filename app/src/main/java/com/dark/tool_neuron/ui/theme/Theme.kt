package com.dark.tool_neuron.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NeuroVerseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    val colorScheme = if (darkTheme) {
        dynamicDarkColorScheme(context)
    } else {
        dynamicLightColorScheme(context)
    }

    // Define typography with Manrope as the base/default family
    val typography = Typography().copy(
        displayLarge = Typography().displayLarge.copy(fontFamily = ManropeFontFamily),
        displayMedium = Typography().displayMedium.copy(fontFamily = ManropeFontFamily),
        displaySmall = Typography().displaySmall.copy(fontFamily = ManropeFontFamily),
        headlineLarge = Typography().headlineLarge.copy(fontFamily = ManropeFontFamily),
        headlineMedium = Typography().headlineMedium.copy(fontFamily = ManropeFontFamily),
        headlineSmall = Typography().headlineSmall.copy(fontFamily = ManropeFontFamily),
        titleLarge = Typography().titleLarge.copy(fontFamily = ManropeFontFamily),
        titleMedium = Typography().titleMedium.copy(fontFamily = ManropeFontFamily),
        titleSmall = Typography().titleSmall.copy(fontFamily = ManropeFontFamily),
        bodyLarge = Typography().bodyLarge.copy(fontFamily = ManropeFontFamily),
        bodyMedium = Typography().bodyMedium.copy(fontFamily = ManropeFontFamily),
        bodySmall = Typography().bodySmall.copy(fontFamily = ManropeFontFamily),
        labelLarge = Typography().labelLarge.copy(fontFamily = ManropeFontFamily),
        labelMedium = Typography().labelMedium.copy(fontFamily = ManropeFontFamily),
        labelSmall = Typography().labelSmall.copy(fontFamily = ManropeFontFamily),
    )

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        typography = typography,
        motionScheme = MotionScheme.expressive(),
        content = content
    )
}
