package com.dark.neuroverse.compose.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay

@Composable
fun GlitchTypingText(
    finalText: String,
    delayPerChar: Long = 60L,
    modifier: Modifier
) {
    var displayText by remember { mutableStateOf("") }

    val randomChars = "!@#$%&*ABCDEFGHIJKLMNOPQRSTUVWXYZ"

    LaunchedEffect(finalText) {
        displayText = ""
        for (i in finalText.indices) {
            repeat(2) {
                displayText = finalText.substring(0, i) + randomChars.random()
                delay(20)
            }
            displayText = finalText.substring(0, i + 1)
            delay(delayPerChar)
        }
    }

    RichText(
        text = "$displayText▌",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}
