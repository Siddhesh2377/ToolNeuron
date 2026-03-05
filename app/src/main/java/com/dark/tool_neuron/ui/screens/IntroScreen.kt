@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.dark.tool_neuron.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import com.dark.tool_neuron.ui.theme.Motion
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.ui.components.ActionCelebrationProgress
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun IntroScreen(onFinished: () -> Unit) {
    val titleAlpha = remember { Animatable(0f) }
    val taglineAlpha = remember { Animatable(0f) }
    var celebrating by remember { mutableStateOf(false) }

    // Permission handling
    var permissionDone by remember {
        mutableStateOf(Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)
    }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionDone = true }

    // Request runtime permissions immediately
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
        }
    }

    // Simulated runtime setup progress
    // TODO: Wire to InferenceClient.runtimeSetup when available
    var setupProgress by remember { mutableFloatStateOf(0f) }
    val animatedProgress by animateFloatAsState(
        targetValue = setupProgress,
        animationSpec = Motion.state(),
        label = "setup_progress"
    )

    // Main flow — starts after permission dialog is handled
    LaunchedEffect(permissionDone) {
        if (!permissionDone) return@LaunchedEffect

        // Fade in title
        launch { titleAlpha.animateTo(1f, tween(600)) }
        delay(300)

        // Fade in tagline + progress bar
        launch { taglineAlpha.animateTo(1f, tween(500)) }
        delay(200)

        // Simulate runtime initialization progress
        // Replace this block with InferenceClient.initializeRuntime() when available
        for (step in 1..10) {
            delay(80)
            setupProgress = step / 10f
        }

        // Let animation catch up to 1.0
        delay(400)

        // Celebrate for 1500ms then proceed
        celebrating = true
        delay(1500)
        onFinished()
    }

    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .padding(horizontal = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "ToolNeuron",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.alpha(titleAlpha.value)
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Privacy-First AI Assistant",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.alpha(taglineAlpha.value)
            )

            Spacer(Modifier.height(32.dp))

            ActionCelebrationProgress(
                progress = { animatedProgress },
                celebrating = celebrating,
                modifier = Modifier.alpha(taglineAlpha.value)
            )
        }
    }
}
