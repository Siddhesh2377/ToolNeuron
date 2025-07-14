package com.dark.neuroverse.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.PathEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.dark.ai_module.workers.ModelManager
import com.dark.neuroverse.model.HomeUiState
import com.dark.neuroverse.ui.screens.HomeScreen
import com.dark.neuroverse.ui.screens.IntroScreen
import com.dark.neuroverse.ui.screens.ModelsScreen
import com.dark.neuroverse.ui.theme.NeuroVerseTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var currentScreen by remember { mutableStateOf(HomeUiState.INTRO) }

            LaunchedEffect(Unit) {
                delay(4000)
                currentScreen = if (ModelManager.isAnyModelInstalled()) {
                    HomeUiState.MAIN
                }else{
                    HomeUiState.MODELS
                }
            }

            NeuroVerseTheme {
                Scaffold {
                    Crossfade(
                        modifier = Modifier.padding(it), targetState = currentScreen,
                        animationSpec = tween(easing = FastOutLinearInEasing)
                    ) { screen ->
                        when (screen) {
                            HomeUiState.INTRO -> {
                                IntroScreen()
                            }
                            HomeUiState.MODELS -> ModelsScreen {
                                currentScreen = HomeUiState.MAIN
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


