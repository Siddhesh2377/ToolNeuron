package com.dark.tool_neuron.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.dark.tool_neuron.data_packs.DataPackScreen
import com.dark.tool_neuron.neuron_example.GGUFLoaderDemo
import com.dark.tool_neuron.neuron_example.NeuronExampleScreen
import com.dark.tool_neuron.ui.screen.memory.VaultDashboard
import com.dark.tool_neuron.ui.theme.NeuroVerseTheme

class TempActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NeuroVerseTheme {
                //DataPackScreen()
                //NeuronExampleScreen()
                //VaultDashboard()
                GGUFLoaderDemo()
            }
        }
    }
}
