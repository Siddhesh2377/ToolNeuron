package com.dark.neuroverse.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.dark.neuroverse.ui.components.M3TabSwitch
import com.dark.neuroverse.ui.theme.NeuroVerseTheme
import com.dark.neuroverse.ui.theme.rDP

class ModelPropEditorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        enableEdgeToEdge()
        setContent {
            NeuroVerseTheme {
                ChatSettingsScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatSettingsScreen() {
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = rDP(16.dp))
                .padding(top = rDP(16.dp), bottom = rDP(24.dp)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            M3TabSwitch(
                selectedIndex = selectedTab,
                options = listOf("Context", "Model"),
                onTabSelected = { selectedTab = it },
                modifier = Modifier.padding(bottom = rDP(20.dp))
            )

            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    fadeIn(tween(300)) + slideInHorizontally(
                        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)
                    ) { if (targetState > initialState) it else -it } togetherWith
                            fadeOut(tween(200))
                },
                label = "content"
            ) { tab ->
                when (tab) {
                    0 -> ContextContent()
                    1 -> ModelContent()
                }
            }
        }
    }
}

@Composable
fun ContextContent() {
    SystemPromptSection()
}

@Composable
fun SystemPromptSection() {
    SettingsCard {
        Column(verticalArrangement = Arrangement.spacedBy(rDP(12.dp))) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "System Prompt",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            OutlinedTextField(
                value = "",
                onValueChange = {},
                placeholder = { Text("You are a helpful assistant.") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rDP(180.dp)),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                shape = RoundedCornerShape(rDP(12.dp))
            )

            Text(
                text = "Token count: 0",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

@Composable
fun ModelContent() {
    Column(verticalArrangement = Arrangement.spacedBy(rDP(12.dp))) {
        // Generation Settings
        SettingsCard(title = "Generation Settings") {
            Column(verticalArrangement = Arrangement.spacedBy(rDP(20.dp))) {
                var temperature by remember { mutableFloatStateOf(0.7f) }
                ModernSlider(
                    label = "Temperature",
                    description = "Controls randomness in responses",
                    value = temperature,
                    onValueChange = { temperature = it },
                    valueRange = 0f..2f
                )

                var topK by remember { mutableIntStateOf(40) }
                ModernSlider(
                    label = "Top K",
                    description = "Limits vocabulary selection",
                    value = topK.toFloat(),
                    steps = 50,
                    onValueChange = { topK = it.toInt() },
                    valueRange = 1f..100f
                )

                var topP by remember { mutableFloatStateOf(0.9f) }
                ModernSlider(
                    label = "Top P",
                    description = "Nucleus sampling threshold",
                    value = topP,
                    onValueChange = { topP = it },
                    valueRange = 0f..1f
                )

                var minP by remember { mutableFloatStateOf(0.05f) }
                ModernSlider(
                    label = "Min P",
                    description = "Minimum probability threshold",
                    value = minP,
                    onValueChange = { minP = it },
                    valueRange = 0f..1f
                )
            }
        }

        // Context Settings
        SettingsCard(title = "Context Settings") {
            Column(verticalArrangement = Arrangement.spacedBy(rDP(16.dp))) {
                var ctxSize by remember { mutableFloatStateOf(4048f) }
                ModernSlider(
                    label = "Context Size",
                    description = "Maximum context window",
                    value = ctxSize,
                    onValueChange = { ctxSize = it },
                    valueRange = 512f..16384f,
                    steps = 15
                )

                var maxTokens by remember { mutableFloatStateOf(2048f) }
                ModernSlider(
                    label = "Max Tokens",
                    description = "Maximum response length",
                    value = maxTokens,
                    onValueChange = { maxTokens = it },
                    valueRange = 128f..8192f,
                    steps = 15
                )
            }
        }

        // Advanced Options
        SettingsCard(title = "Advanced Options") {
            Column(verticalArrangement = Arrangement.spacedBy(rDP(4.dp))) {
                var useMMAP by remember { mutableStateOf(true) }
                ModernSwitchRow(
                    label = "Use MMAP",
                    description = "Memory-mapped file loading",
                    checked = useMMAP,
                    onCheckedChange = { useMMAP = it }
                )

                var useMLOCK by remember { mutableStateOf(false) }
                ModernSwitchRow(
                    label = "Use MLOCK",
                    description = "Lock model in RAM",
                    checked = useMLOCK,
                    onCheckedChange = { useMLOCK = it }
                )
            }
        }
    }
}

@Composable
fun SettingsCard(
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(rDP(16.dp)))
            .background(MaterialTheme.colorScheme.surface)
            .padding(rDP(16.dp)),
        verticalArrangement = Arrangement.spacedBy(rDP(16.dp))
    ) {
        title?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        content()
    }
}

@Composable
fun ModernSlider(
    label: String,
    description: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0
) {
    var isDragging by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    Column(verticalArrangement = Arrangement.spacedBy(rDP(8.dp))) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val displayValue = when {
                valueRange.endInclusive > 100f -> "%.0f".format(value)
                valueRange.endInclusive > 2f -> "%.1f".format(value)
                else -> "%.2f".format(value)
            }

            AnimatedContent(
                targetState = displayValue,
                transitionSpec = {
                    (slideInVertically { -it } + fadeIn()) togetherWith
                            (slideOutVertically { it } + fadeOut())
                },
                label = "value"
            ) { displayVal ->
                Text(
                    text = displayVal,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Slider(
            value = value,
            onValueChange = {
                onValueChange(it)
                isDragging = true

                // Trigger subtle tick feedback as user drags
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            },
            onValueChangeFinished = {
                isDragging = false
                // Stronger vibration when drag finishes
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            },
            valueRange = valueRange,
            steps = steps
        )
    }
}

@Composable
fun ModernSwitchRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(rDP(12.dp)))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = rDP(12.dp)),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        ModernSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
fun ModernSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) rDP(20.dp) else rDP(0.dp),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "thumbOffset"
    )

    val trackColor by animateColorAsState(
        targetValue = if (checked) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(300),
        label = "trackColor"
    )

    val thumbColor by animateColorAsState(
        targetValue = if (checked) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outline,
        animationSpec = tween(300),
        label = "thumbColor"
    )

    Box(
        modifier = Modifier
            .width(rDP(52.dp))
            .height(rDP(32.dp))
            .clip(RoundedCornerShape(rDP(16.dp)))
            .background(trackColor)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onCheckedChange(!checked) }
            .padding(rDP(4.dp))
    ) {
        Box(
            modifier = Modifier
                .size(rDP(24.dp))
                .offset(x = thumbOffset)
                .clip(RoundedCornerShape(rDP(12.dp)))
                .background(thumbColor)
        )
    }
}