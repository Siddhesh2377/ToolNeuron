package com.dark.tool_neuron.ui.screens.intro_screen

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.ui.components.TnProgressBar
import com.dark.tool_neuron.ui.icons.TnIcons
import kotlinx.coroutines.delay
import kotlin.math.sqrt

@Composable
fun IntroScreen(
    innerPadding: PaddingValues,
    onFinish: () -> Unit = {},
) {
    var progressTarget by remember { mutableFloatStateOf(0f) }
    val progress by animateFloatAsState(
        targetValue = progressTarget,
        animationSpec = tween(durationMillis = PROGRESS_DURATION_MS, easing = LinearEasing),
        label = "introProgress",
    )

    var revealTarget by remember { mutableFloatStateOf(0f) }
    val reveal by animateFloatAsState(
        targetValue = revealTarget,
        animationSpec = tween(durationMillis = REVEAL_DURATION_MS, easing = FastOutSlowInEasing),
        label = "introReveal",
    )

    LaunchedEffect(Unit) { progressTarget = 1f }

    LaunchedEffect(progress) {
        if (progress >= 1f && revealTarget < 1f) {
            delay(PROGRESS_TO_REVEAL_PAUSE_MS)
            revealTarget = 1f
            delay(REVEAL_DURATION_MS.toLong() + REVEAL_HOLD_MS)
            onFinish()
        }
    }

    val accent = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawWithContent {
                drawContent()
                if (reveal > 0f) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val maxRadius = sqrt(cx * cx + cy * cy)
                    drawCircle(
                        color = accent,
                        radius = maxRadius * reveal,
                        center = Offset(cx, cy),
                    )
                }
            }
            .padding(innerPadding),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.width(IntrinsicSize.Max),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(14.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = TnIcons.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = "Privacy Is Priority",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(Modifier.height(2.dp))

            TnProgressBar(
                progress = progress,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private const val PROGRESS_DURATION_MS = 1800
private const val PROGRESS_TO_REVEAL_PAUSE_MS = 150L
private const val REVEAL_DURATION_MS = 600
private const val REVEAL_HOLD_MS = 350L
