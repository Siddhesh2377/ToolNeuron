package com.dark.neuroverse.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.dark.ai_module.ai.Neuron
import com.dark.ai_module.helpers.JNILibHelper
import com.dark.ai_module.workers.ModelManager
import com.dark.neuroverse.model.HomeUiState
import com.dark.neuroverse.ui.screens.HomeScreen
import com.dark.neuroverse.ui.screens.IntroScreen
import com.dark.neuroverse.ui.screens.ModelsScreen
import com.dark.neuroverse.ui.theme.NeuroVerseTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            // UI states
            var currentScreen by remember { mutableStateOf(HomeUiState.INTRO) }
            var isJNIReady by remember { mutableStateOf(false) }
            var isJNIDownloading by remember { mutableStateOf(false) }

            // Launch once: check & load JNI
            LaunchedEffect(Unit) {
                // Optional delay for visual intro
                delay(3500)

                // Check if native lib exists
                isJNIDownloading = !JNILibHelper.checkIfJNILibExists(this@MainActivity)

                // Start downloading/loading
                JNILibHelper.loadJNILib(this@MainActivity) {
                    CoroutineScope(Dispatchers.IO).launch {
                        Neuron.loadModel(
                            File(ModelManager.getFirstModel()?.modelPath ?: ""),
                            context = this@MainActivity,
                            systemPrompt = "You are a helpful assistant."
                        ){
                            isJNIReady = true
                            isJNIDownloading = false
                        }
                    }
                }
            }

            // Once JNI is ready, go to next screen
            LaunchedEffect(isJNIReady) {
                if (isJNIReady) {
                    currentScreen = if (ModelManager.isAnyModelInstalled()) {
                        HomeUiState.MAIN
                    } else {
                        HomeUiState.MODELS
                    }
                }
            }

            // Composable Tree
            NeuroVerseTheme {
                Scaffold { innerPadding ->
                    Crossfade(
                        modifier = Modifier.padding(innerPadding),
                        targetState = currentScreen,
                        animationSpec = tween(durationMillis = 500, easing = FastOutLinearInEasing)
                    ) { screen ->
                        when (screen) {
                            HomeUiState.INTRO -> {
                                IntroScreen(isJNIDownloading)
                            }

                            HomeUiState.MODELS -> {
                                ModelsScreen {
                                    currentScreen = HomeUiState.MAIN
                                }
                            }

                            HomeUiState.MAIN -> {
                                HomeScreen()
                            }
                        }
                    }
                }
            }
        }
    }
}
