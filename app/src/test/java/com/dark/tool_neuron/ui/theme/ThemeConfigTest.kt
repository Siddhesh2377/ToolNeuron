package com.dark.tool_neuron.ui.theme

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeConfigTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `parse default theme JSON`() {
        val jsonString = """
        {
            "id": "default",
            "name": "Default",
            "author": "ToolNeuron",
            "version": 1,
            "colors": {
                "background": "#121212",
                "surface": "#1e1e1e",
                "surfaceVariant": "#2d2d2d",
                "onBackground": "#e0e0e0",
                "onSurface": "#e0e0e0",
                "onSurfaceVariant": "#a0a0a0",
                "primary": "#bb86fc",
                "onPrimary": "#000000",
                "error": "#ef4444",
                "success": "#22c55e",
                "info": "#3b82f6"
            },
            "fontFamily": "manrope",
            "useUppercaseLabels": false,
            "letterSpacing": 0,
            "cornerRadius": 12,
            "accentBorderWidth": 0,
            "showCornerBrackets": false,
            "showGridBackground": false,
            "scrollbarWidth": 4,
            "squareStatusDots": false
        }
        """.trimIndent()

        val config = json.decodeFromString<ThemeConfig>(jsonString)

        assertEquals("default", config.id)
        assertEquals("Default", config.name)
        assertEquals("manrope", config.fontFamily)
        assertFalse(config.useUppercaseLabels)
        assertEquals(0f, config.letterSpacing, 0.001f)
        assertEquals(12f, config.cornerRadius, 0.001f)
        assertEquals(0f, config.accentBorderWidth, 0.001f)
        assertFalse(config.showCornerBrackets)
        assertFalse(config.showGridBackground)
        assertEquals(4f, config.scrollbarWidth, 0.001f)
        assertFalse(config.squareStatusDots)

        assertEquals("#121212", config.colors.background)
        assertEquals("#bb86fc", config.colors.primary)
        assertEquals("#ef4444", config.colors.error)
        assertNull(config.colors.primaryContainer)
        assertNull(config.colors.secondaryContainer)
    }

    @Test
    fun `parse industrial-label theme JSON`() {
        val jsonString = """
        {
            "id": "industrial-label",
            "name": "Industrial Label",
            "author": "ToolNeuron",
            "version": 1,
            "colors": {
                "background": "#0b0b0b",
                "surface": "#111111",
                "surfaceVariant": "#1a1a1a",
                "onBackground": "#d4d4d4",
                "onSurface": "#d4d4d4",
                "onSurfaceVariant": "#666666",
                "primary": "#f59e0b",
                "onPrimary": "#000000",
                "error": "#ef4444",
                "success": "#22c55e",
                "info": "#3b82f6"
            },
            "fontFamily": "jetbrains_mono",
            "useUppercaseLabels": true,
            "letterSpacing": 1.2,
            "cornerRadius": 0,
            "accentBorderWidth": 1.5,
            "showCornerBrackets": true,
            "showGridBackground": true,
            "scrollbarWidth": 6,
            "squareStatusDots": true
        }
        """.trimIndent()

        val config = json.decodeFromString<ThemeConfig>(jsonString)

        assertEquals("industrial-label", config.id)
        assertEquals("Industrial Label", config.name)
        assertEquals("jetbrains_mono", config.fontFamily)
        assertTrue(config.useUppercaseLabels)
        assertEquals(1.2f, config.letterSpacing, 0.001f)
        assertEquals(0f, config.cornerRadius, 0.001f)
        assertEquals(1.5f, config.accentBorderWidth, 0.001f)
        assertTrue(config.showCornerBrackets)
        assertTrue(config.showGridBackground)
        assertEquals(6f, config.scrollbarWidth, 0.001f)
        assertTrue(config.squareStatusDots)

        assertEquals("#0b0b0b", config.colors.background)
        assertEquals("#f59e0b", config.colors.primary)
    }

    @Test
    fun `parse hex color`() {
        val white = parseHexColor("#ffffff")
        assertEquals(1f, white.red, 0.01f)
        assertEquals(1f, white.green, 0.01f)
        assertEquals(1f, white.blue, 0.01f)
        assertEquals(1f, white.alpha, 0.01f)

        val black = parseHexColor("#000000")
        assertEquals(0f, black.red, 0.01f)
        assertEquals(0f, black.green, 0.01f)
        assertEquals(0f, black.blue, 0.01f)

        val red = parseHexColor("#ff0000")
        assertEquals(1f, red.red, 0.01f)
        assertEquals(0f, red.green, 0.01f)
        assertEquals(0f, red.blue, 0.01f)

        val amber = parseHexColor("#f59e0b")
        assertEquals(0.96f, amber.red, 0.01f)
        assertEquals(0.62f, amber.green, 0.01f)
        assertEquals(0.04f, amber.blue, 0.01f)
    }

    @Test
    fun `parse hex color with 8-digit ARGB`() {
        val semiTransparent = parseHexColor("#80ff0000")
        assertEquals(0.50f, semiTransparent.alpha, 0.02f)
        assertEquals(1f, semiTransparent.red, 0.01f)
    }

    @Test
    fun `default ThemeConfig companion has correct values`() {
        val default = ThemeConfig.DEFAULT
        assertEquals("default", default.id)
        assertEquals("Default", default.name)
        assertEquals("ToolNeuron", default.author)
        assertEquals(1, default.version)
        assertEquals("manrope", default.fontFamily)
        assertFalse(default.useUppercaseLabels)
        assertEquals(12f, default.cornerRadius, 0.001f)
    }

    @Test
    fun `JSON with missing optional fields uses defaults`() {
        val jsonString = """
        {
            "id": "minimal",
            "name": "Minimal",
            "colors": {
                "background": "#000000",
                "surface": "#111111",
                "surfaceVariant": "#222222",
                "onBackground": "#ffffff",
                "onSurface": "#ffffff",
                "onSurfaceVariant": "#aaaaaa",
                "primary": "#00ff00",
                "onPrimary": "#000000"
            }
        }
        """.trimIndent()

        val config = json.decodeFromString<ThemeConfig>(jsonString)

        assertEquals("minimal", config.id)
        assertEquals("ToolNeuron", config.author)
        assertEquals(1, config.version)
        assertEquals("manrope", config.fontFamily)
        assertFalse(config.useUppercaseLabels)
        assertEquals(0f, config.letterSpacing, 0.001f)
        assertEquals(12f, config.cornerRadius, 0.001f)
        assertEquals("#ef4444", config.colors.error)
        assertEquals("#22c55e", config.colors.success)
        assertEquals("#3b82f6", config.colors.info)
        assertNull(config.colors.primaryContainer)
    }
}
