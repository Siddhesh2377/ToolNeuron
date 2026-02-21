package com.dark.tool_neuron.ui.screen

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.R
import com.dark.tool_neuron.global.Standards
import com.dark.tool_neuron.ui.components.ActionTextButton
import com.dark.tool_neuron.ui.theme.rDp

@Composable
fun WelcomeScreen(
    onContinue: () -> Unit
) {
    val pulseScale = remember { Animatable(1f) }
    val shimmerAlpha = remember { Animatable(0.3f) }
    val contentAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        contentAlpha.animateTo(1f, animationSpec = tween(800))
    }

    LaunchedEffect(Unit) {
        pulseScale.animateTo(
            targetValue = 1.15f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            )
        )
    }

    LaunchedEffect(Unit) {
        shimmerAlpha.animateTo(
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            )
        )
    }

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
                .padding(horizontal = rDp(32.dp))
                .alpha(contentAlpha.value),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Pulsing icon
            Box(contentAlignment = Alignment.Center) {
                Surface(
                    modifier = Modifier
                        .size(rDp(140.dp))
                        .scale(pulseScale.value),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                ) {}

                Surface(
                    modifier = Modifier.size(rDp(110.dp)),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ) {}

                Icon(
                    painterResource(R.drawable.ai_model),
                    contentDescription = "ToolNeuron",
                    Modifier.size(rDp(70.dp)),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(rDp(32.dp)))

            Text(
                "Tool-Neuron",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(rDp(8.dp)))

            Text(
                "Privacy-First AI on Your Device",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(rDp(32.dp)))

            // Feature highlights
            Column(
                verticalArrangement = Arrangement.spacedBy(rDp(12.dp))
            ) {
                FeatureHighlight(
                    icon = Icons.Default.Shield,
                    text = "Complete Privacy"
                )
                FeatureHighlight(
                    icon = Icons.Default.Memory,
                    text = "On-Device AI Processing"
                )
                FeatureHighlight(
                    icon = Icons.Default.Lock,
                    text = "AES-256 Encrypted Storage"
                )
                FeatureHighlight(
                    icon = Icons.Default.WifiOff,
                    text = "Works Fully Offline"
                )
            }
        }

        // Bottom section
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = rDp(40.dp))
                .padding(horizontal = rDp(32.dp))
                .alpha(contentAlpha.value),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ActionTextButton(
                onClickListener = onContinue,
                icon = Icons.AutoMirrored.Filled.ArrowForward,
                text = "Get Started",
                contentDescription = "Continue to setup",
                modifier = Modifier.fillMaxWidth(0.8f)
            )

            Spacer(Modifier.height(rDp(16.dp)))

            Text(
                "No data leaves your device",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun FeatureHighlight(
    icon: ImageVector,
    text: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            shape = RoundedCornerShape(rDp(Standards.CardSmallCornerRadius)),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            modifier = Modifier.size(rDp(36.dp))
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(rDp(18.dp))
                )
            }
        }

        Spacer(Modifier.width(rDp(12.dp)))

        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
