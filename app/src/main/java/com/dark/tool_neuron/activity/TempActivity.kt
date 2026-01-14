package com.dark.tool_neuron.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.dark.tool_neuron.data_packs.DataPackScreen
import com.dark.tool_neuron.ui.theme.NeuroVerseTheme

class TempActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NeuroVerseTheme {
                DataPackScreen()
            }
        }
    }
}
