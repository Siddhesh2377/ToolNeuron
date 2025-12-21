package com.dark.tool_neuron.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dark.tool_neuron.ui.screens.ModelsScreen
import com.dark.tool_neuron.ui.screens.modelScreen.GGUFModelScreen
import com.dark.tool_neuron.ui.theme.NeuroVerseTheme

class ModelActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            NeuroVerseTheme {
                //ModelsScreen {  }
                Screen()
            }
        }
    }

}

@Composable
fun Screen(){
    Scaffold {
        Box(Modifier.padding(it)){
            GGUFModelScreen()
        }
    }
}