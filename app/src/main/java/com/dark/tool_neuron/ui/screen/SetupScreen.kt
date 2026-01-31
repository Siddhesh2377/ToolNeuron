package com.dark.tool_neuron.ui.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dark.tool_neuron.global.Standards
import com.dark.tool_neuron.service.ModelDownloadService
import com.dark.tool_neuron.ui.theme.rDp
import com.dark.tool_neuron.viewmodel.SetupOption
import com.dark.tool_neuron.viewmodel.SetupPhase
import com.dark.tool_neuron.viewmodel.SetupViewModel
import kotlinx.coroutines.delay

@Composable
fun SetupScreen(
    onSetupComplete: () -> Unit
) {
    val viewModel: SetupViewModel = viewModel()
    val phase by viewModel.setupPhase.collectAsState()
    val selectedOption by viewModel.selectedOption.collectAsState()
    val downloadStates by viewModel.downloadStates.collectAsState()
    val setupComplete by viewModel.setupComplete.collectAsState()
    val downloadError by viewModel.downloadError.collectAsState()
    val primaryModelId by viewModel.primaryModelId.collectAsState()

    // Navigate when setup completes
    LaunchedEffect(setupComplete) {
        if (setupComplete) {
            delay(400)
            onSetupComplete()
        }
    }

    // Auto-advance from intro after 1.5s
    LaunchedEffect(phase) {
        if (phase == SetupPhase.INTRO) {
            delay(1500)
            viewModel.advanceFromIntro()
        }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        AnimatedContent(
            targetState = phase,
            transitionSpec = {
                fadeIn(tween(600)) togetherWith fadeOut(tween(400))
            },
            modifier = Modifier.padding(padding),
            label = "setup_phase"
        ) { currentPhase ->
            when (currentPhase) {
                SetupPhase.INTRO -> SetupIntroContent()
                SetupPhase.SETUP -> SetupOptionsContent(
                    selectedOption = selectedOption,
                    downloadStates = downloadStates,
                    primaryModelId = primaryModelId,
                    downloadError = downloadError,
                    onOptionSelected = { viewModel.selectOption(it) },
                    onRetry = { viewModel.retryDownload() }
                )
            }
        }
    }
}

// ==================== Intro Phase ====================

@Composable
private fun SetupIntroContent() {
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        alpha.animateTo(1f, animationSpec = tween(800))
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(horizontal = rDp(32.dp))
                .padding(bottom = rDp(80.dp))
                .alpha(alpha.value),
            verticalArrangement = Arrangement.spacedBy(rDp(8.dp))
        ) {
            Text(
                "Tool-Neuron",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "Where Your Privacy Matters!",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LinearProgressIndicator()
        }
    }
}

// ==================== Setup Phase ====================

@Composable
private fun SetupOptionsContent(
    selectedOption: SetupOption?,
    downloadStates: Map<String, ModelDownloadService.DownloadState>,
    primaryModelId: String?,
    downloadError: String?,
    onOptionSelected: (SetupOption) -> Unit,
    onRetry: () -> Unit
) {
    val isDownloading = selectedOption != null && selectedOption != SetupOption.POWER_MODE
    val downloadState = primaryModelId?.let { downloadStates[it] }

    val progress = when (downloadState) {
        is ModelDownloadService.DownloadState.Downloading -> downloadState.progress
        is ModelDownloadService.DownloadState.Extracting -> -1f
        is ModelDownloadService.DownloadState.Processing -> -1f
        is ModelDownloadService.DownloadState.Success -> 1f
        else -> 0f
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = rDp(24.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        // Header section
        AnimatedContent(
            targetState = isDownloading,
            transitionSpec = {
                (fadeIn(tween(300)) + slideInVertically(
                    initialOffsetY = { -it / 4 },
                    animationSpec = spring(dampingRatio = 0.8f)
                )) togetherWith fadeOut(tween(200))
            },
            label = "header"
        ) { downloading ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (downloading) {
                    Text(
                        "Downloading...",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(rDp(8.dp)))
                    Text(
                        "You can Minimize the app, Will Notify You",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(rDp(24.dp)))

                    // Progress bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (progress >= 0f) {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(rDp(6.dp))
                                    .clip(RoundedCornerShape(rDp(3.dp))),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                            Spacer(Modifier.width(rDp(12.dp)))
                            Text(
                                "${(progress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            // Indeterminate for extracting/processing
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(rDp(6.dp))
                                    .clip(RoundedCornerShape(rDp(3.dp))),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                        }
                    }
                } else {
                    Text(
                        "Welcome User",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(rDp(8.dp)))
                    Text(
                        "Choose Your Setup!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(rDp(32.dp)))

        // Options list with staggered animation
        val options = listOf(
            SetupOption.TEXT to "Text",
            SetupOption.TEXT_UNCENSORED to "Text Uncensored",
            SetupOption.TEXT_TTS to "Text + TTS",
            SetupOption.IMAGE_GEN to "Image Gen",
            SetupOption.POWER_MODE to "Power Mode ( SKIP )"
        )

        options.forEachIndexed { index, (option, label) ->
            key(option) {
                var visible by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    delay(index * 80L)
                    visible = true
                }

                AnimatedVisibility(
                    visible = visible,
                    enter = slideInVertically(
                        initialOffsetY = { it },
                        animationSpec = spring(
                            dampingRatio = 0.75f,
                            stiffness = 300f
                        )
                    ) + fadeIn(spring(stiffness = 300f))
                ) {
                    SetupOptionCard(
                        label = label,
                        isSelected = selectedOption == option,
                        isDownloading = isDownloading,
                        enabled = selectedOption == null,
                        onClick = { onOptionSelected(option) }
                    )
                }

                if (index < options.lastIndex) {
                    Spacer(Modifier.height(rDp(8.dp)))
                }
            }
        }

        // Error message
        AnimatedVisibility(
            visible = downloadError != null,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier.padding(top = rDp(16.dp)),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = downloadError ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(rDp(8.dp)))
                TextButton(onClick = onRetry) {
                    Text("Retry")
                }
            }
        }
    }
}

// ==================== Option Card ====================

@Composable
private fun SetupOptionCard(
    label: String,
    isSelected: Boolean,
    isDownloading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isSelected && isDownloading -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
            else -> MaterialTheme.colorScheme.surfaceContainerLow
        },
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "optionBg"
    )

    val contentColor by animateColorAsState(
        targetValue = when {
            isSelected && isDownloading -> MaterialTheme.colorScheme.onPrimaryContainer
            !enabled && !isSelected -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            else -> MaterialTheme.colorScheme.onSurface
        },
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "optionContent"
    )

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(rDp(Standards.CardCornerRadius)),
        color = backgroundColor,
        enabled = enabled && !isDownloading
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = rDp(16.dp),
                vertical = rDp(14.dp)
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = contentColor
            )

            if (isSelected && isDownloading) {
                Icon(
                    imageVector = Icons.Default.RadioButtonChecked,
                    contentDescription = "Downloading",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(rDp(20.dp))
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "Download",
                    tint = contentColor.copy(alpha = 0.7f),
                    modifier = Modifier.size(rDp(20.dp))
                )
            }
        }
    }
}
