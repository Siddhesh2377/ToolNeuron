package com.dark.tool_neuron.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.dark.tool_neuron.ui.screen.HomeScreen
import com.dark.tool_neuron.ui.screen.IntroScreen
import com.dark.tool_neuron.ui.screen.ProgressDemoScreen
import com.dark.tool_neuron.ui.theme.NeuroVerseTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NeuroVerseTheme {
                HomeScreen()
            }
        }
    }
}