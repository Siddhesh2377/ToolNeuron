package com.dark.tool_neuron.ui.screen.home_screen

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.dark.tool_neuron.models.state.AppState
import com.dark.tool_neuron.models.state.getBackgroundColor
import com.dark.tool_neuron.models.state.getColor
import com.dark.tool_neuron.models.state.getContentColor
import com.dark.tool_neuron.state.AppStateManager
import com.dark.tool_neuron.ui.theme.rDp
import com.dark.tool_neuron.viewmodel.ChatViewModel
import androidx.compose.runtime.collectAsState
import com.dark.tool_neuron.R

@Composable
fun DynamicActionWindow(chatViewModel: ChatViewModel) {
    val appState by AppStateManager.appState.collectAsState()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ),

        elevation = CardDefaults.cardElevation(rDp(8.dp)),
        colors = CardDefaults.cardColors(
            containerColor = appState.getBackgroundColor()
        )
    ) {
        // Crossfade animation between different states
        Crossfade(
            targetState = appState, animationSpec = tween(300), label = "state_transition"
        ) { state ->
            when (state) {
                is AppState.Welcome -> WelcomeContent()
                is AppState.NoModelLoaded -> NoModelLoadedContent(chatViewModel)
                is AppState.ModelLoaded -> ModelLoadedContent(state.modelName)
                is AppState.LoadingModel -> LoadingModelContent(
                    modelName = state.modelName, progress = state.progress
                )

                is AppState.GeneratingText -> GeneratingTextContent(state.modelName)
                is AppState.GeneratingImage -> GeneratingImageContent(state.modelName)
                is AppState.GeneratingAudio -> GeneratingAudioContent(state.modelName)
                is AppState.Error -> ErrorContent(
                    message = state.message, modelName = state.modelName
                )
            }
        }
    }
}

// Individual state composables

@Composable
private fun WelcomeContent() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(rDp(24.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(rDp(16.dp))
    ) {
        Icon(
            painter = painterResource(R.drawable.user),
            contentDescription = null,
            modifier = Modifier.size(rDp(48.dp)),
            tint = AppStateManager.appState.collectAsState().value.getColor()
        )
        Text(
            text = "Welcome!",
            style = MaterialTheme.typography.headlineSmall,
            color = AppStateManager.appState.collectAsState().value.getContentColor()
        )
        Text(
            text = "Get started by loading a model",
            style = MaterialTheme.typography.bodyMedium,
            color = AppStateManager.appState.collectAsState().value.getContentColor()
        )
    }
}

@Composable
private fun NoModelLoadedContent(chatViewModel: ChatViewModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(rDp(24.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(rDp(16.dp))
    ) {
        Icon(
            painter = painterResource(R.drawable.vl_models),
            contentDescription = null,
            modifier = Modifier.size(rDp(48.dp)),
            tint = AppStateManager.appState.collectAsState().value.getColor()
        )
        Text(
            text = "No Model Loaded",
            style = MaterialTheme.typography.headlineSmall,
            color = AppStateManager.appState.collectAsState().value.getContentColor()
        )
        Text(
            text = "Please select a model to continue",
            style = MaterialTheme.typography.bodyMedium,
            color = AppStateManager.appState.collectAsState().value.getContentColor(),
            textAlign = TextAlign.Center
        )

        // Add your model selection button here
        Button(
            onClick = { chatViewModel.showModelList() }, colors = ButtonDefaults.buttonColors(
                containerColor = AppStateManager.appState.collectAsState().value.getColor()
            )
        ) {
            Text("Select Model")
        }
    }
}

@Composable
private fun ModelLoadedContent(modelName: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(rDp(24.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(rDp(16.dp))
    ) {
        Icon(
            painter = painterResource(R.drawable.smart_temp_message),
            contentDescription = null,
            modifier = Modifier.size(rDp(48.dp)),
            tint = AppStateManager.appState.collectAsState().value.getColor()
        )
        Text(
            text = "Model Ready",
            style = MaterialTheme.typography.headlineSmall,
            color = AppStateManager.appState.collectAsState().value.getContentColor()
        )
        Text(
            text = modelName,
            style = MaterialTheme.typography.titleMedium,
            color = AppStateManager.appState.collectAsState().value.getColor(),
            fontWeight = FontWeight.Bold
        )

        // Model info or actions
        Row(
            modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            OutlinedButton(
                onClick = { /* Model info */ }) {
                Text("Info")
            }
            OutlinedButton(
                onClick = { /* Change model */ }) {
                Text("Change")
            }
        }
    }
}

@Composable
private fun LoadingModelContent(modelName: String, progress: Float) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(rDp(24.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(rDp(16.dp))
    ) {
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.size(rDp(48.dp)),
            color = AppStateManager.appState.collectAsState().value.getColor(),
        )
        Text(
            text = "Loading Model",
            style = MaterialTheme.typography.headlineSmall,
            color = AppStateManager.appState.collectAsState().value.getContentColor()
        )
        Text(
            text = modelName,
            style = MaterialTheme.typography.bodyMedium,
            color = AppStateManager.appState.collectAsState().value.getContentColor()
        )
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(),
            color = AppStateManager.appState.collectAsState().value.getColor(),
        )
        Text(
            text = "${(progress * 100).toInt()}%",
            style = MaterialTheme.typography.bodySmall,
            color = AppStateManager.appState.collectAsState().value.getContentColor()
        )
    }
}

@Composable
private fun GeneratingTextContent(modelName: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(rDp(24.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(rDp(16.dp))
    ) {
        // Animated indicator
        val infiniteTransition = rememberInfiniteTransition(label = "generating")
        val rotation by infiniteTransition.animateFloat(
            initialValue = 0f, targetValue = 360f, animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = LinearEasing), repeatMode = RepeatMode.Restart
            ), label = "rotation"
        )

        Icon(
            painter = painterResource(R.drawable.tool),
            contentDescription = null,
            modifier = Modifier
                .size(rDp(48.dp))
                .rotate(rotation),
            tint = AppStateManager.appState.collectAsState().value.getColor()
        )
        Text(
            text = "Generating Text",
            style = MaterialTheme.typography.headlineSmall,
            color = AppStateManager.appState.collectAsState().value.getContentColor()
        )
        Text(
            text = modelName,
            style = MaterialTheme.typography.bodyMedium,
            color = AppStateManager.appState.collectAsState().value.getContentColor()
        )
    }
}

@Composable
private fun GeneratingImageContent(modelName: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(rDp(24.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(rDp(16.dp))
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "generating_image")
        val scale by infiniteTransition.animateFloat(
            initialValue = 1f, targetValue = 1.2f, animationSpec = infiniteRepeatable(
                animation = tween(800), repeatMode = RepeatMode.Reverse
            ), label = "scale"
        )

        Icon(
            painter = painterResource(R.drawable.tool),
            contentDescription = null,
            modifier = Modifier
                .size(rDp(48.dp))
                .scale(scale),
            tint = AppStateManager.appState.collectAsState().value.getColor()
        )
        Text(
            text = "Creating Image",
            style = MaterialTheme.typography.headlineSmall,
            color = AppStateManager.appState.collectAsState().value.getContentColor()
        )
        Text(
            text = modelName,
            style = MaterialTheme.typography.bodyMedium,
            color = AppStateManager.appState.collectAsState().value.getContentColor()
        )
    }
}

@Composable
private fun GeneratingAudioContent(modelName: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(rDp(24.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(rDp(16.dp))
    ) {
        // Audio wave animation
        Row(
            horizontalArrangement = Arrangement.spacedBy(rDp(4.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {

        }

        Text(
            text = "Generating Audio",
            style = MaterialTheme.typography.headlineSmall,
            color = AppStateManager.appState.collectAsState().value.getContentColor()
        )
        Text(
            text = modelName,
            style = MaterialTheme.typography.bodyMedium,
            color = AppStateManager.appState.collectAsState().value.getContentColor()
        )
    }
}

@Composable
private fun ErrorContent(message: String, modelName: String?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(rDp(24.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(rDp(16.dp))
    ) {
        Icon(
            painter = painterResource(R.drawable.error),
            contentDescription = null,
            modifier = Modifier.size(rDp(48.dp)),
            tint = AppStateManager.appState.collectAsState().value.getColor()
        )
        Text(
            text = "Error",
            style = MaterialTheme.typography.headlineSmall,
            color = AppStateManager.appState.collectAsState().value.getContentColor()
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = AppStateManager.appState.collectAsState().value.getContentColor(),
            textAlign = TextAlign.Center
        )

        if (modelName != null) {
            Text(
                text = "Model: $modelName",
                style = MaterialTheme.typography.bodySmall,
                color = AppStateManager.appState.collectAsState().value.getContentColor()
            )
        }

        Button(
            onClick = { /* Retry action */ }, colors = ButtonDefaults.buttonColors(
                containerColor = AppStateManager.appState.collectAsState().value.getColor()
            )
        ) {
            Text("Retry")
        }
    }
}