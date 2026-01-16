package com.dark.tool_neuron.ui.screen

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.R
import com.dark.tool_neuron.ui.theme.rDp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun IntroScreen() {
    val context = LocalContext.current

    var progress by remember { mutableFloatStateOf(0f) }
    var delayTime by remember { mutableLongStateOf(8L) }
    var loadingStatus by remember { mutableStateOf("Initializing App...") }

    val pulseScale = remember { Animatable(1f) }
    val shimmerAlpha = remember { Animatable(0.3f) }


    // Pulsing animation for the icon
    LaunchedEffect(Unit) {
        pulseScale.animateTo(
            targetValue = 1.15f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            )
        )
    }

    // Shimmer effect for offline indicator
    LaunchedEffect(Unit) {
        shimmerAlpha.animateTo(
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            )
        )
    }

    // Progress delay control
    LaunchedEffect(Unit) {
        delayTime = 8
        delay(2000)
        delayTime = 3
        delay(2000)
        delayTime = 8
    }

    // Progress animation
    LaunchedEffect(Unit) {
        for (i in 1..1000) {
            delay(delayTime)
            progress = i / 1000f
        }
    }

    Scaffold(Modifier.fillMaxSize()) { _ ->
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            MaterialTheme.colorScheme.surfaceContainerLowest
                        )
                    )
                )
        ) {
            Column(
                Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = rDp(32.dp)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Icon with pulsing animation and background
                Box(contentAlignment = Alignment.Center) {
                    // Background circle with gradient
                    Surface(
                        modifier = Modifier
                            .size(rDp(140.dp))
                            .scale(pulseScale.value),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    ) {}

                    // Inner circle
                    Surface(
                        modifier = Modifier.size(rDp(110.dp)),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ) {}

                    // Icon
                    Icon(
                        painterResource(R.drawable.ai_model),
                        contentDescription = "App Icon",
                        Modifier.size(rDp(70.dp)),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(Modifier.height(rDp(32.dp)))

                // App name
                Text(
                    "Tool-Neuron",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    fontFamily = FontFamily.SansSerif,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(Modifier.height(rDp(8.dp)))

                // Tagline
                Text(
                    "Where Your Privacy Matters :)",
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.SansSerif,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(rDp(24.dp)))

                // Offline indicator with shimmer
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.padding(horizontal = rDp(16.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(
                            horizontal = rDp(16.dp),
                            vertical = rDp(8.dp)
                        ),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Offline dot indicator
                        Box(
                            modifier = Modifier
                                .size(rDp(8.dp))
                                .background(
                                    MaterialTheme.colorScheme.primary,
                                    CircleShape
                                )
                                .alpha(shimmerAlpha.value)
                        )

                        Spacer(Modifier.width(rDp(8.dp)))

                        Text(
                            "100% Offline AI",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            // Bottom section with progress and info
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = rDp(40.dp))
                    .padding(horizontal = rDp(32.dp)),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Loading text
                Text(
                    loadingStatus,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = rDp(12.dp))
                )

                // Progress indicator
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(rDp(6.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )

                Spacer(Modifier.height(rDp(16.dp)))

                // Privacy message
                Text(
                    "No data leaves your device",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
