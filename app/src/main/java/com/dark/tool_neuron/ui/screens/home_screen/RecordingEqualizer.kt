package com.dark.tool_neuron.ui.screens.home_screen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.ui.components.ActionButton
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.LocalTnShapes
import kotlinx.coroutines.delay

private const val BAR_COUNT = 24
private const val BAR_WINDOW_MS = 80L

@Composable
fun RecordingEqualizer(
    amplitude: Float,
    isTranscribing: Boolean,
    onCancel: () -> Unit,
    onSubmit: () -> Unit,
) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current

    val bars = remember { mutableStateOf(FloatArray(BAR_COUNT)) }
    LaunchedEffect(Unit) {
        while (true) {
            val current = bars.value
            val next = FloatArray(BAR_COUNT)
            for (i in 0 until BAR_COUNT - 1) next[i] = current[i + 1]
            next[BAR_COUNT - 1] = amplitude.coerceIn(0f, 1f)
            bars.value = next
            delay(BAR_WINDOW_MS)
        }
    }

    Surface(
        shape = tnShapes.full,
        color = MaterialTheme.colorScheme.error.copy(alpha = 0.10f)
            .compositeOver(MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = dimens.spacingSm,
                    vertical = dimens.spacingSm,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
        ) {
            ActionButton(
                onClickListener = onCancel,
                icon = TnIcons.X,
                contentDescription = "Cancel recording",
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (isTranscribing) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(dimens.iconSm),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "Transcribing…",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                } else {
                    EqualizerBars(
                        bars = bars.value,
                        activeColor = MaterialTheme.colorScheme.error,
                    )
                }
            }

            ActionButton(
                onClickListener = onSubmit,
                icon = TnIcons.CircleCheck,
                contentDescription = "Stop and transcribe",
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    }
}

@Composable
private fun EqualizerBars(bars: FloatArray, activeColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().fillMaxHeight(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally),
    ) {
        for (i in bars.indices) {
            val raw = bars[i].coerceIn(0f, 1f)
            val target = (0.08f + raw * 0.92f).coerceIn(0.08f, 1f)
            val animated by animateFloatAsState(
                targetValue = target,
                animationSpec = tween(durationMillis = 120),
                label = "bar_$i",
            )
            Spacer(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight(animated)
                    .background(
                        color = activeColor.copy(alpha = 0.35f + animated * 0.65f),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(2.dp),
                    ),
            )
        }
    }
}
