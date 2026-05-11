package com.dark.plugins.expense

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
internal fun ExpenseTheme(content: @Composable () -> Unit) {
    val scheme = lightColorScheme(
        primary = Color(0xFF1B7D4F),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFB8EFCB),
        onPrimaryContainer = Color(0xFF002111),
        secondary = Color(0xFF4D6356),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFCFE9D8),
        onSecondaryContainer = Color(0xFF0A1F14),
        tertiary = Color(0xFF3B6471),
        tertiaryContainer = Color(0xFFBFE9F7),
        onTertiaryContainer = Color(0xFF001F28),
        background = Color(0xFFF5FBF6),
        onBackground = Color(0xFF161D18),
        surface = Color(0xFFF5FBF6),
        onSurface = Color(0xFF161D18),
        surfaceContainer = Color(0xFFE9F3EB),
        surfaceContainerHigh = Color(0xFFDFEDE2),
        surfaceContainerHighest = Color(0xFFD2E6D7),
        onSurfaceVariant = Color(0xFF45524A),
        outline = Color(0xFF75827A),
        outlineVariant = Color(0xFFC4D1C8),
        error = Color(0xFFB3261E),
        onError = Color.White,
        errorContainer = Color(0xFFF9DEDC),
        onErrorContainer = Color(0xFF410E0B),
    )
    MaterialTheme(colorScheme = scheme, content = content)
}
