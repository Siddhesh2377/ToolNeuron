package com.dark.tool_neuron.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.dark.tool_neuron.ui.screens.theme_preview.ColorShowcaseScreen
import com.dark.tool_neuron.ui.theme.ColorPalette
import com.dark.tool_neuron.ui.theme.ToolNeuronTheme

class ColorShowcaseActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val paletteRaw = intent.getStringExtra(EXTRA_PALETTE) ?: ColorPalette.DYNAMIC.name
        val isDark = intent.getBooleanExtra(EXTRA_DARK, true)
        val palette = runCatching { ColorPalette.valueOf(paletteRaw) }.getOrDefault(ColorPalette.DYNAMIC)

        setContent {
            ToolNeuronTheme(darkTheme = isDark, palette = palette) {
                ColorShowcaseScreen(paletteName = palette.displayName, isDark = isDark)
            }
        }
    }

    companion object {
        const val EXTRA_PALETTE = "palette"
        const val EXTRA_DARK = "dark"
    }
}
