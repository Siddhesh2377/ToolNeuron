package com.dark.tool_neuron

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dark.tool_neuron.ui.screens.model_store.InstalledModelsEmptyState
import com.dark.tool_neuron.ui.theme.ToolNeuronTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InstalledModelsEmptyStateTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyStateStillOffersLocalImport() {
        var imports = 0
        composeRule.setContent {
            ToolNeuronTheme(darkTheme = false) {
                InstalledModelsEmptyState(onImport = { imports++ })
            }
        }
        composeRule.onNodeWithText("No installed models").assertIsDisplayed()
        composeRule.onNodeWithText("Import local model").assertIsDisplayed().performClick()
        assertEquals(1, imports)
    }
}
