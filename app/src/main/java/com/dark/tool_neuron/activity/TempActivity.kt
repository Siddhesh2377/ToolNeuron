package com.dark.tool_neuron.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.dark.tool_neuron.ui.screen.guide.TermsAndConditionsScreen
import com.dark.tool_neuron.ui.theme.NeuroVerseTheme

class TempActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NeuroVerseTheme {
                TermsAndConditionsScreen{

                }
            }
        }
    }
}
