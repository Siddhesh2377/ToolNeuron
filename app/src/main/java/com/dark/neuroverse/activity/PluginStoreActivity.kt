package com.dark.neuroverse.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.dark.neuroverse.ui.screens.PluginStoreScreen
import com.dark.neuroverse.ui.theme.NeuroVerseTheme

class PluginStoreActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NeuroVerseTheme {
                PluginStoreScreen()
            }
        }
    }
}



