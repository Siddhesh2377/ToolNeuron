package com.dark.neuroverse.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.dark.neuroverse.ui.theme.Coral
import com.dark.neuroverse.ui.theme.rDP
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.random.Random

@Composable
fun RobotDecodePlaceholder(
    active: Boolean, base: String = "Decoding response", modifier: Modifier = Modifier
) {
    val charset = "!@#${'$'}%&*+/:;?=<>[]{}ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789░▒▓█"
    var shown by remember { mutableStateOf(base) }
    var step by remember { mutableStateOf(0) }

    // caret blink
    val blink by rememberInfiniteTransition(label = "caret").animateFloat(
        initialValue = 1f, targetValue = 0.25f, animationSpec = infiniteRepeatable(
            animation = tween(500), repeatMode = RepeatMode.Reverse
        ), label = "caretFloat"
    )

    // shimmer sweep
    val shimmerX by rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Restart),
        label = "shimmerFloat"
    )

    // loop while active
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        val phrases = listOf(
            "$base …",
            "Tokenizing …",
            "Loading KV cache …",
            "Neurons waking up …",
            "Planning …",
            "Reasoning …"
        )
        while (active && coroutineContext.isActive) {
            val seed = phrases[step % phrases.size]
            val noisy = seed.map { c ->
                when {
                    c.isWhitespace() || c == '…' -> c
                    Random.nextFloat() < 0.20f -> charset.random()
                    else -> c
                }
            }.joinToString("")
            shown = noisy
            step++
            delay(66L) // ~15 fps
        }
    }

    // render
    val caret = "▌"
    val gradient = Brush.linearGradient(
        colors = listOf(
            Coral.copy(alpha = 0.25f), Coral, Coral.copy(alpha = 0.25f)
        ), start = Offset.Zero, end = Offset(1000f * shimmerX + 1f, 0f)
    )

    Box(
        modifier
            .clip(RoundedCornerShape(rDP(8.dp)))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(rDP(8.dp)))
            .padding(rDP(9.dp))
    ) {
        Text(
            text = shown,
            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.drawWithContent {
                drawContent()
                drawRect(brush = gradient, alpha = 0.25f, blendMode = BlendMode.SrcOver)
            })
        Text(
            text = caret,
            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = blink),
            modifier = Modifier.align(Alignment.BottomEnd)
        )
    }
}