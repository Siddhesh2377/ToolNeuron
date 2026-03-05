package com.dark.tool_neuron.ui.components

import androidx.compose.animation.core.animateFloatAsState
import com.dark.tool_neuron.ui.theme.Motion
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

// ════════════════════════════════════════════
//  CELEBRATION PROGRESS — particle burst
// ════════════════════════════════════════════

private data class CelebrationParticle(
    var x: Float, var y: Float,
    var vx: Float, var vy: Float,
    var alpha: Float, var length: Float, var angle: Float,
    var life: Float, val maxLife: Float
)

private val ProgressHeight = 4.dp

@Composable
fun ActionCelebrationProgress(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    celebrating: Boolean = false
) {
    val shape = MaterialTheme.shapes.extraSmall
    val trackColor = MaterialTheme.colorScheme.primary.copy(0.06f)
    val activeColor = MaterialTheme.colorScheme.primary
    val h = ProgressHeight
    val density = LocalDensity.current

    val animP by animateFloatAsState(
        progress().coerceIn(0f, 1f),
        Motion.interactive(), label = "cpProg"
    )

    val particles = remember { mutableStateListOf<CelebrationParticle>() }
    var lastSpawnTime by remember { mutableLongStateOf(0L) }
    var prevProgress by remember { mutableStateOf(0f) }
    var containerWidth by remember { mutableStateOf(0f) }
    val heightPx = with(density) { h.toPx() }
    val gravity = with(density) { 120.dp.toPx() } // px/s^2

    LaunchedEffect(Unit) {
        var lastNanos = System.nanoTime()
        while (isActive) {
            kotlinx.coroutines.delay(16) // ~60fps
            val now = System.nanoTime()
            val dt = (now - lastNanos) / 1_000_000_000f
            lastNanos = now

            val p = animP
            val fillX = p * containerWidth

            // spawn particles
            val nowMs = now / 1_000_000
            val justCompleted = p >= 0.99f && prevProgress < 0.99f
            val spawnInterval = if (celebrating || justCompleted) 30L else 120L
            val spawnCount = if (celebrating || justCompleted) 6 else 2

            if ((p > 0f && p < 1f) || celebrating || justCompleted) {
                if (nowMs - lastSpawnTime > spawnInterval) {
                    lastSpawnTime = nowMs
                    val spread = if (celebrating || justCompleted) 2.2f else 1.2f
                    val speed = if (celebrating || justCompleted) 80f else 45f
                    repeat(spawnCount) {
                        val angle = (-spread / 2 + Math.random().toFloat() * spread)
                        val spd = (speed * 0.6f + Math.random().toFloat() * speed * 0.8f) * with(density) { 1.dp.toPx() }
                        val life = 0.5f + Math.random().toFloat() * 0.4f
                        particles.add(
                            CelebrationParticle(
                                x = fillX, y = heightPx / 2f + (Math.random().toFloat() - 0.5f) * heightPx,
                                vx = cos(angle) * spd, vy = sin(angle) * spd - spd * 0.3f,
                                alpha = 0.8f + Math.random().toFloat() * 0.2f,
                                length = with(density) { (3f + Math.random().toFloat() * 2f).dp.toPx() },
                                angle = angle,
                                life = 0f, maxLife = life
                            )
                        )
                    }
                }
            }
            prevProgress = p

            // update particles
            val iter = particles.listIterator()
            while (iter.hasNext()) {
                val part = iter.next()
                part.life += dt
                if (part.life >= part.maxLife) { iter.remove(); continue }
                part.vy += gravity * dt
                part.x += part.vx * dt
                part.y += part.vy * dt
                part.angle = atan2(part.vy, part.vx)
                part.alpha = (1f - part.life / part.maxLife).coerceIn(0f, 1f) * 0.9f
            }
        }
    }

    Box(
        modifier.fillMaxWidth().height(h + 20.dp) // extra space for particles
            .onSizeChanged { containerWidth = it.width.toFloat() }
    ) {
        // track
        Box(
            Modifier.fillMaxWidth().height(h)
                .align(Alignment.CenterStart)
                .clip(shape).background(trackColor)
        ) {
            Box(Modifier.fillMaxWidth(animP).fillMaxHeight().background(activeColor))
        }

        // particle overlay
        Canvas(Modifier.fillMaxSize()) {
            for (part in particles) {
                val dx = cos(part.angle) * part.length
                val dy = sin(part.angle) * part.length
                // offset y to center of track within the box
                val yOffset = (size.height - heightPx) / 2f
                drawLine(
                    color = activeColor.copy(alpha = part.alpha),
                    start = Offset(part.x - dx / 2, part.y + yOffset - dy / 2),
                    end = Offset(part.x + dx / 2, part.y + yOffset + dy / 2),
                    strokeWidth = with(density) { 1.5.dp.toPx() },
                    cap = StrokeCap.Round
                )
            }
        }
    }
}
