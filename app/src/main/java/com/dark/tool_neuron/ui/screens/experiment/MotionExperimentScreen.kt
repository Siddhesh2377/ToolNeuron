package com.dark.tool_neuron.ui.screens.experiment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberRangeSliderState
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.LocalTnShapes
import com.dark.tool_neuron.ui.theme.maple

@Composable
fun MotionExperimentScreen(innerPadding: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Header()
        SectionTitle("Sliders")
        SlidersSection()
        SectionTitle("Switches")
        SwitchesSection()
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Header() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Motion Lab",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "M3 Expressive sliders + switches. Drag, tap, observe motion.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 4.sp,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun ExperimentCard(
    label: String,
    valueText: String? = null,
    content: @Composable () -> Unit,
) {
    val tnShapes = LocalTnShapes.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        shape = tnShapes.card,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (valueText != null) {
                    Text(
                        text = valueText,
                        fontFamily = maple,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            content()
        }
    }
}

@Composable
private fun SlidersSection() {
    val standardState = rememberSliderState(value = 28f, valueRange = 1f..50f)
    val steppedState = rememberSliderState(value = 7f, valueRange = 1f..15f, steps = 13)
    val rangeState = rememberRangeSliderState(
        activeRangeStart = 0.2f,
        activeRangeEnd = 0.8f,
        valueRange = 0f..1f,
    )

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ExperimentCard(
            label = "Standard",
            valueText = "${standardState.value.toInt()}",
        ) {
            Slider(state = standardState)
        }

        ExperimentCard(
            label = "Stepped (1–15, 14 stops)",
            valueText = "${steppedState.value.toInt()}",
        ) {
            Slider(state = steppedState)
        }

        ExperimentCard(label = "Thick custom track") {
            ThickSlider(state = rememberSliderState(value = 512f, valueRange = 256f..1024f))
        }

        ExperimentCard(
            label = "Range",
            valueText = "${"%.2f".format(rangeState.activeRangeStart)} – ${"%.2f".format(rangeState.activeRangeEnd)}",
        ) {
            RangeSlider(state = rangeState)
        }
    }
}

@Composable
private fun ThickSlider(state: SliderState) {
    Slider(
        state = state,
        thumb = {
            Box(
                modifier = Modifier
                    .size(width = 6.dp, height = 36.dp)
                    .padding(vertical = 2.dp),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.fillMaxSize(),
                ) {}
            }
        },
        track = { sliderState ->
            val fraction = ((sliderState.value - sliderState.valueRange.start) /
                (sliderState.valueRange.endInclusive - sliderState.valueRange.start)).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = RoundedCornerShape(50),
                ) {}
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(20.dp),
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(50),
                ) {}
            }
        },
        colors = SliderDefaults.colors(),
    )
}

@Composable
private fun SwitchesSection() {
    var defaultSwitch by remember { mutableStateOf(true) }
    var iconSwitch by remember { mutableStateOf(false) }
    var unsizedSwitch by remember { mutableStateOf(true) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SwitchRowCard(
            label = "Default M3 Expressive",
            description = "Thumb morphs between dot, stadium, and pressed shapes.",
            checked = defaultSwitch,
            onCheckedChange = { defaultSwitch = it },
        )

        SwitchRowCard(
            label = "With check icon",
            description = "Icon appears in the thumb when checked.",
            checked = iconSwitch,
            onCheckedChange = { iconSwitch = it },
            thumbContent = if (iconSwitch) {
                {
                    Icon(
                        imageVector = TnIcons.Check,
                        contentDescription = null,
                        modifier = Modifier.size(SwitchDefaults.IconSize),
                    )
                }
            } else null,
        )

        SwitchRowCard(
            label = "Tinted track",
            description = "Custom checked colors using project palette.",
            checked = unsizedSwitch,
            onCheckedChange = { unsizedSwitch = it },
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedBorderColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
        )
    }
}

@Composable
private fun SwitchRowCard(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    thumbContent: (@Composable () -> Unit)? = null,
    colors: androidx.compose.material3.SwitchColors = SwitchDefaults.colors(),
) {
    val tnShapes = LocalTnShapes.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        shape = tnShapes.card,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                thumbContent = thumbContent,
                colors = colors,
            )
        }
    }
}
