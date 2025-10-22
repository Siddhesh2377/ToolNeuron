package com.dark.neuroverse.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animate
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dark.neuroverse.ui.theme.rDP
import com.dark.neuroverse.viewModel.chatViewModel.TTSViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TTSPlaybackBarCompact(
    ttsViewModel: TTSViewModel, modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val audioProgress by ttsViewModel.audioProgress.collectAsStateWithLifecycle()
    val generationStatus by ttsViewModel.generationStatus.collectAsStateWithLifecycle()
    val isSeekable by ttsViewModel.isInitialized.collectAsStateWithLifecycle()

    var sliderPosition by remember { mutableFloatStateOf(audioProgress) }
    var isUserDragging by remember { mutableStateOf(false) }

    // Smooth slider animation
    LaunchedEffect(audioProgress, isUserDragging) {
        if (!isUserDragging) {
            animate(
                initialValue = sliderPosition,
                targetValue = audioProgress,
                animationSpec = androidx.compose.animation.core.tween(durationMillis = 50)
            ) { value, _ ->
                sliderPosition = value
            }
        }
    }

    AnimatedVisibility(
        visible = generationStatus != null,
        enter = slideInHorizontally(initialOffsetX = { -it }),
        exit = slideOutHorizontally(targetOffsetX = { -it })
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = rDP(8.dp), vertical = rDP(4.dp)),
            shape = MaterialTheme.shapes.small,
            tonalElevation = rDP(4.dp),
            shadowElevation = rDP(2.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(modifier = Modifier.padding(horizontal = rDP(10.dp), vertical = rDP(6.dp))) {

                // Top text info
                Text(
                    text = generationStatus ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = rDP(4.dp))
                )

                // Slider / progress + close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(rDP(8.dp))
                ) {
                    Crossfade(targetState = generationStatus, Modifier.weight(1f)) { status ->
                        if (status == "Generating...") {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(rDP(4.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        } else if (isSeekable) {
                            NeuroSlider(
                                sliderPosition = sliderPosition, onValueChange = {
                                sliderPosition = it
                                isUserDragging = true
                            }, onValueChangeFinished = {
                                scope.launch {
                                    ttsViewModel.seekTo(sliderPosition)
                                    isUserDragging = false
                                }
                            }, modifier = Modifier, isUserDragging = isUserDragging
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            // Stop immediately (non-suspend)
                            ttsViewModel.stopPlayback()

                            // Reset TTS in IO
                            scope.launch(Dispatchers.IO) {
                                ttsViewModel.resetTTS()
                            }
                        },
                        modifier = Modifier.size(rDP(24.dp))
                    )
 {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Stop TTS",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NeuroSlider(
    sliderPosition: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
    isUserDragging: Boolean = false
) {
    val scope = rememberCoroutineScope()

    Slider(
        value = sliderPosition,
        onValueChange = {
            onValueChange(it)
        },
        onValueChangeFinished = {
            scope.launch { onValueChangeFinished() }
        },
        valueRange = 0f..1f,
        modifier = modifier
            .fillMaxWidth()
            .height(rDP(32.dp))
            .graphicsLayer {
                alpha = if (isUserDragging) 1f else 0.95f
                scaleY = if (isUserDragging) 1.05f else 1f
            },
        thumb = {
            Box(
                modifier = Modifier
                    .size(rDP(18.dp))
                    .background(
                        brush = Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.secondary
                            )
                        ), shape = MaterialShapes.Cookie6Sided.toShape()
                    )
                    .shadow(
                        elevation = if (isUserDragging) rDP(4.dp) else rDP(2.dp),
                        shape = MaterialShapes.Cookie6Sided.toShape(),
                        clip = false
                    )
                    .border(
                        width = rDP(1.dp),
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.4f),
                        shape = MaterialShapes.Cookie6Sided.toShape()
                    )
            )
        },
        track = { sliderState ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rDP(4.dp))
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(sliderState.value)
                        .matchParentSize()
                        .background(
                            brush = Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.tertiary
                                )
                            )
                        )
                )
            }
        })
}

