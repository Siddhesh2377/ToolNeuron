package com.dark.neuroverse.compose.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun GlitchTypingText(
    finalText: String,
    delayPerChar: Long = 60L,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    modifier: Modifier
) {
    var displayText by remember { mutableStateOf("") }

    val randomChars = "!@#$%&*ABCDEFGHIJKLMNOPQRSTUVWXYZ"

    LaunchedEffect(finalText) {
        displayText = ""
        for (i in finalText.indices) {
            repeat(2) {
                displayText = finalText.substring(0, i) + randomChars.random()
                delay(20)
            }
            displayText = finalText.substring(0, i + 1)
            delay(delayPerChar)
        }
    }

    RichText(
        text = "$displayText▌",
        style = style,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

@Composable
fun ShimmerText(text: String) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val shimmerTranslate by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer-anim"
    )

    val brush = Brush.linearGradient(
        colors = listOf(
            Color.LightGray,
            Color.White,
            Color.DarkGray
        ),
        start = Offset.Zero,
        end = Offset(x = shimmerTranslate, y = shimmerTranslate)
    )

    Text(
        text = text,
        fontSize = 18.sp,
        fontWeight = FontWeight.Medium,
        style = TextStyle(brush = brush),
        modifier = Modifier
            .padding(top = 8.dp)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

