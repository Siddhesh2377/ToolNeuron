package com.dark.tool_neuron.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.random.Random

private data class Particle(
    var x: Float,
    var y: Float,
    var velocityX: Float,
    var velocityY: Float,
    var alpha: Float,
    var radius: Float,
    var life: Float = 1f,
    var decay: Float = Random.nextFloat() * 0.02f + 0.01f
)

@Composable
fun ParticleProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    indicatorColor: Color = MaterialTheme.colorScheme.primary,
    particleColor: Color = MaterialTheme.colorScheme.primary,
    particleCount: Int = 6
) {
    var particles by remember { mutableStateOf(listOf<Particle>()) }
    val time = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        time.animateTo(
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            )
        )
    }

    // Spawn particles at progress edge
    LaunchedEffect(progress) {
        if (progress > 0.01f && progress < 0.99f) {
            val newParticles = List(particleCount) {
                Particle(
                    x = 0f,
                    y = 0f,
                    velocityX = (Random.nextFloat() - 0.5f) * 3f,
                    velocityY = -(Random.nextFloat() * 4f + 1f),
                    alpha = 1f,
                    radius = Random.nextFloat() * 2f + 1f
                )
            }
            particles = (particles.filter { it.life > 0f } + newParticles).takeLast(30)
        }
    }

    Box(
        modifier = modifier.fillMaxWidth().height(height + 24.dp),
        contentAlignment = Alignment.BottomStart
    ) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(height),
            color = indicatorColor,
            trackColor = trackColor,
        )

        Canvas(modifier = Modifier.matchParentSize()) {
            val progressX = size.width * progress
            val baseY = size.height - (height.toPx() / 2f)

            particles = particles.mapNotNull { p ->
                val updated = p.copy(
                    x = p.x + p.velocityX,
                    y = p.y + p.velocityY,
                    velocityY = p.velocityY + 0.15f,
                    life = p.life - p.decay,
                    alpha = (p.life - p.decay).coerceIn(0f, 1f)
                )
                if (updated.life > 0f) {
                    drawCircle(
                        color = particleColor.copy(alpha = updated.alpha * 0.7f),
                        radius = updated.radius,
                        center = Offset(
                            x = progressX + updated.x,
                            y = baseY + updated.y
                        )
                    )
                    updated
                } else null
            }
        }
    }
}
