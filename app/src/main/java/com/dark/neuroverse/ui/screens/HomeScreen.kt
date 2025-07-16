package com.dark.neuroverse.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import com.dark.ai_module.ai.Neuron
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun HomeScreen() {
    var text by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        try {
            CoroutineScope(Dispatchers.IO).launch {
                Neuron.generateResponseStreaming("Greet The User") { token ->
                    text += token
                }
            }
        } catch (e: Exception) {
            text = "Error: ${e.message}"
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        Text(
            text = "Welcome to NeuroV..! \n$text",
            style = MaterialTheme.typography.headlineMedium.copy(fontFamily = FontFamily.Serif)
        )
    }
}
