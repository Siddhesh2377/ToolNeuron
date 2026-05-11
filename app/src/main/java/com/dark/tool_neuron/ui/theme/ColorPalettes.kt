package com.dark.tool_neuron.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

enum class ColorPalette(val displayName: String) {
    DYNAMIC("Dynamic (Material You)"),
    NEON_LIME("Neon Lime"),
    OCEAN_CYAN("Ocean Cyan"),
    VIOLET_DUSK("Violet Dusk"),
    AMBER_RUST("Amber Rust"),
    ROSE_PINK("Rose Pink"),
    MONO_SLATE("Mono Slate"),
}

fun colorSchemeFor(palette: ColorPalette, dark: Boolean, context: Context): ColorScheme {
    if (palette == ColorPalette.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        return try {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } catch (_: Exception) {
            if (dark) NeonLimeDark else NeonLimeLight
        }
    }
    return when (palette) {
        ColorPalette.DYNAMIC,
        ColorPalette.NEON_LIME -> if (dark) NeonLimeDark else NeonLimeLight
        ColorPalette.OCEAN_CYAN -> if (dark) OceanCyanDark else OceanCyanLight
        ColorPalette.VIOLET_DUSK -> if (dark) VioletDuskDark else VioletDuskLight
        ColorPalette.AMBER_RUST -> if (dark) AmberRustDark else AmberRustLight
        ColorPalette.ROSE_PINK -> if (dark) RosePinkDark else RosePinkLight
        ColorPalette.MONO_SLATE -> if (dark) MonoSlateDark else MonoSlateLight
    }
}

private val NeonLimeLight = lightColorScheme(
    primary = Color(0xFF556500),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6ED6C),
    onPrimaryContainer = Color(0xFF161F00),
    secondary = Color(0xFF5B6147),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDFE6C5),
    onSecondaryContainer = Color(0xFF191E09),
    tertiary = Color(0xFF3A665B),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBCECDE),
    onTertiaryContainer = Color(0xFF00201A),
    background = Color(0xFFFBFDF4),
    onBackground = Color(0xFF1B1C16),
    surface = Color(0xFFFBFDF4),
    onSurface = Color(0xFF1B1C16),
    surfaceVariant = Color(0xFFE1E4D0),
    onSurfaceVariant = Color(0xFF454838),
    outline = Color(0xFF757866),
    outlineVariant = Color(0xFFC5C8B4),
)

private val NeonLimeDark = darkColorScheme(
    primary = Color(0xFFBAD052),
    onPrimary = Color(0xFF2A3500),
    primaryContainer = Color(0xFF3F4B00),
    onPrimaryContainer = Color(0xFFD6ED6C),
    secondary = Color(0xFFC3CAAA),
    onSecondary = Color(0xFF2D321D),
    secondaryContainer = Color(0xFF434931),
    onSecondaryContainer = Color(0xFFDFE6C5),
    tertiary = Color(0xFFA1D0C2),
    onTertiary = Color(0xFF02372E),
    tertiaryContainer = Color(0xFF214E44),
    onTertiaryContainer = Color(0xFFBCECDE),
    background = Color(0xFF13140E),
    onBackground = Color(0xFFE4E3D9),
    surface = Color(0xFF13140E),
    onSurface = Color(0xFFE4E3D9),
    surfaceVariant = Color(0xFF454838),
    onSurfaceVariant = Color(0xFFC5C8B4),
    outline = Color(0xFF8F9280),
    outlineVariant = Color(0xFF454838),
)

private val OceanCyanLight = lightColorScheme(
    primary = Color(0xFF006493),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCCE5FF),
    onPrimaryContainer = Color(0xFF001E30),
    secondary = Color(0xFF50606F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD3E5F7),
    onSecondaryContainer = Color(0xFF0C1D29),
    tertiary = Color(0xFF655A7C),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFEBDDFF),
    onTertiaryContainer = Color(0xFF201735),
    background = Color(0xFFF7FAFD),
    onBackground = Color(0xFF191C1F),
    surface = Color(0xFFF7FAFD),
    onSurface = Color(0xFF191C1F),
    surfaceVariant = Color(0xFFDDE3EA),
    onSurfaceVariant = Color(0xFF41474D),
    outline = Color(0xFF71787E),
    outlineVariant = Color(0xFFC1C7CE),
)

private val OceanCyanDark = darkColorScheme(
    primary = Color(0xFF90CCFF),
    onPrimary = Color(0xFF00344E),
    primaryContainer = Color(0xFF004B70),
    onPrimaryContainer = Color(0xFFCCE5FF),
    secondary = Color(0xFFB7C9DA),
    onSecondary = Color(0xFF223240),
    secondaryContainer = Color(0xFF384857),
    onSecondaryContainer = Color(0xFFD3E5F7),
    tertiary = Color(0xFFCFC1EA),
    onTertiary = Color(0xFF362C4B),
    tertiaryContainer = Color(0xFF4D4263),
    onTertiaryContainer = Color(0xFFEBDDFF),
    background = Color(0xFF0F1418),
    onBackground = Color(0xFFE1E2E6),
    surface = Color(0xFF0F1418),
    onSurface = Color(0xFFE1E2E6),
    surfaceVariant = Color(0xFF41474D),
    onSurfaceVariant = Color(0xFFC1C7CE),
    outline = Color(0xFF8B9198),
    outlineVariant = Color(0xFF41474D),
)

private val VioletDuskLight = lightColorScheme(
    primary = Color(0xFF5B4AC2),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE3DFFF),
    onPrimaryContainer = Color(0xFF170351),
    secondary = Color(0xFF605D71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE6DFF8),
    onSecondaryContainer = Color(0xFF1C1A2C),
    tertiary = Color(0xFF7D5260),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD8E3),
    onTertiaryContainer = Color(0xFF31101D),
    background = Color(0xFFFBF8FF),
    onBackground = Color(0xFF1B1B22),
    surface = Color(0xFFFBF8FF),
    onSurface = Color(0xFF1B1B22),
    surfaceVariant = Color(0xFFE4E1EC),
    onSurfaceVariant = Color(0xFF46464F),
    outline = Color(0xFF777680),
    outlineVariant = Color(0xFFC7C5D0),
)

private val VioletDuskDark = darkColorScheme(
    primary = Color(0xFFC6C0FF),
    onPrimary = Color(0xFF2B1884),
    primaryContainer = Color(0xFF4332A3),
    onPrimaryContainer = Color(0xFFE3DFFF),
    secondary = Color(0xFFC8C4DC),
    onSecondary = Color(0xFF312F42),
    secondaryContainer = Color(0xFF484559),
    onSecondaryContainer = Color(0xFFE6DFF8),
    tertiary = Color(0xFFEFB8C8),
    onTertiary = Color(0xFF4A2532),
    tertiaryContainer = Color(0xFF633B48),
    onTertiaryContainer = Color(0xFFFFD8E3),
    background = Color(0xFF121218),
    onBackground = Color(0xFFE4E1E9),
    surface = Color(0xFF121218),
    onSurface = Color(0xFFE4E1E9),
    surfaceVariant = Color(0xFF46464F),
    onSurfaceVariant = Color(0xFFC7C5D0),
    outline = Color(0xFF918F9A),
    outlineVariant = Color(0xFF46464F),
)

private val AmberRustLight = lightColorScheme(
    primary = Color(0xFF8A5000),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDCBE),
    onPrimaryContainer = Color(0xFF2D1700),
    secondary = Color(0xFF735A41),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFEDDBD),
    onSecondaryContainer = Color(0xFF281806),
    tertiary = Color(0xFF5C6339),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE0E8B3),
    onTertiaryContainer = Color(0xFF191E00),
    background = Color(0xFFFFFBFF),
    onBackground = Color(0xFF1F1B16),
    surface = Color(0xFFFFFBFF),
    onSurface = Color(0xFF1F1B16),
    surfaceVariant = Color(0xFFF0DFCB),
    onSurfaceVariant = Color(0xFF504435),
    outline = Color(0xFF827464),
    outlineVariant = Color(0xFFD4C3B0),
)

private val AmberRustDark = darkColorScheme(
    primary = Color(0xFFFFB77C),
    onPrimary = Color(0xFF4B2700),
    primaryContainer = Color(0xFF6A3A00),
    onPrimaryContainer = Color(0xFFFFDCBE),
    secondary = Color(0xFFE2C1A3),
    onSecondary = Color(0xFF412C17),
    secondaryContainer = Color(0xFF59422B),
    onSecondaryContainer = Color(0xFFFEDDBD),
    tertiary = Color(0xFFC4CC99),
    onTertiary = Color(0xFF2E3410),
    tertiaryContainer = Color(0xFF444B23),
    onTertiaryContainer = Color(0xFFE0E8B3),
    background = Color(0xFF17130E),
    onBackground = Color(0xFFEAE1D9),
    surface = Color(0xFF17130E),
    onSurface = Color(0xFFEAE1D9),
    surfaceVariant = Color(0xFF504435),
    onSurfaceVariant = Color(0xFFD4C3B0),
    outline = Color(0xFF9C8E7D),
    outlineVariant = Color(0xFF504435),
)

private val RosePinkLight = lightColorScheme(
    primary = Color(0xFFB31D56),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFD9E1),
    onPrimaryContainer = Color(0xFF3F001A),
    secondary = Color(0xFF755762),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFD9E2),
    onSecondaryContainer = Color(0xFF2B151E),
    tertiary = Color(0xFF7C5738),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDCBE),
    onTertiaryContainer = Color(0xFF2C1700),
    background = Color(0xFFFFFBFF),
    onBackground = Color(0xFF21191B),
    surface = Color(0xFFFFFBFF),
    onSurface = Color(0xFF21191B),
    surfaceVariant = Color(0xFFF3DCE1),
    onSurfaceVariant = Color(0xFF514247),
    outline = Color(0xFF837177),
    outlineVariant = Color(0xFFD6C1C6),
)

private val RosePinkDark = darkColorScheme(
    primary = Color(0xFFFFB0C3),
    onPrimary = Color(0xFF66002B),
    primaryContainer = Color(0xFF8D003F),
    onPrimaryContainer = Color(0xFFFFD9E1),
    secondary = Color(0xFFE4BDC9),
    onSecondary = Color(0xFF432A33),
    secondaryContainer = Color(0xFF5C4049),
    onSecondaryContainer = Color(0xFFFFD9E2),
    tertiary = Color(0xFFEFBE98),
    onTertiary = Color(0xFF482A0E),
    tertiaryContainer = Color(0xFF624022),
    onTertiaryContainer = Color(0xFFFFDCBE),
    background = Color(0xFF191113),
    onBackground = Color(0xFFECE0E2),
    surface = Color(0xFF191113),
    onSurface = Color(0xFFECE0E2),
    surfaceVariant = Color(0xFF514247),
    onSurfaceVariant = Color(0xFFD6C1C6),
    outline = Color(0xFF9E8C90),
    outlineVariant = Color(0xFF514247),
)

private val MonoSlateLight = lightColorScheme(
    primary = Color(0xFF435668),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC8DBEE),
    onPrimaryContainer = Color(0xFF001023),
    secondary = Color(0xFF536069),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD6E4EE),
    onSecondaryContainer = Color(0xFF101C24),
    tertiary = Color(0xFF68597C),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFEFDCFF),
    onTertiaryContainer = Color(0xFF221735),
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFF8FAFC),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFDDE3EA),
    onSurfaceVariant = Color(0xFF41474D),
    outline = Color(0xFF71787E),
    outlineVariant = Color(0xFFC1C7CE),
)

private val MonoSlateDark = darkColorScheme(
    primary = Color(0xFFADC4DB),
    onPrimary = Color(0xFF152B3F),
    primaryContainer = Color(0xFF2D4255),
    onPrimaryContainer = Color(0xFFC8DBEE),
    secondary = Color(0xFFBAC8D2),
    onSecondary = Color(0xFF253239),
    secondaryContainer = Color(0xFF3B4850),
    onSecondaryContainer = Color(0xFFD6E4EE),
    tertiary = Color(0xFFD2C0E6),
    onTertiary = Color(0xFF382B4B),
    tertiaryContainer = Color(0xFF4F4263),
    onTertiaryContainer = Color(0xFFEFDCFF),
    background = Color(0xFF11141A),
    onBackground = Color(0xFFE2E2E6),
    surface = Color(0xFF11141A),
    onSurface = Color(0xFFE2E2E6),
    surfaceVariant = Color(0xFF41474D),
    onSurfaceVariant = Color(0xFFC1C7CE),
    outline = Color(0xFF8B9198),
    outlineVariant = Color(0xFF41474D),
)
