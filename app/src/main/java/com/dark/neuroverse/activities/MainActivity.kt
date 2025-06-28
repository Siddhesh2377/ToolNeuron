package com.dark.neuroverse.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.core.view.WindowCompat
import com.dark.neuroverse.compose.screens.main.MainScreen
import com.dark.neuroverse.compose.screens.temp.NeuronTreeScreen
import com.dark.neuroverse.compose.screens.temp.TaskDemoScreen
import com.dark.neuroverse.ui.theme.NeuroVerseTheme


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        enableEdgeToEdge()
        setContent {
            NeuroVerseTheme {
                Scaffold(containerColor = MaterialTheme.colorScheme.surface) { it ->
                    MainScreen(it)
                }
            }
        }
    }
}



