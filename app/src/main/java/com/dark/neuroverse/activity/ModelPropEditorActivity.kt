// ─────────────────────────────────────────────────────────────────────
package com.dark.neuroverse.activity

import android.app.Activity
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Tab
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.dark.ai_module.model.ModelData
import com.dark.ai_module.model.ModelProvider
import com.dark.ai_module.workers.ModelManager
import com.dark.neuroverse.ui.theme.NeuroVerseTheme
import com.dark.neuroverse.ui.theme.rDP
import kotlinx.coroutines.launch

class ModelPropEditorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        enableEdgeToEdge()
        setContent {
            NeuroVerseTheme {
                ChatSettingsScreen(
                    modelName = intent?.getStringExtra("modelName") ?: "defaultModel"
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatSettingsScreen(modelName: String) {
    // the model that the user is editing
    var currentModel by remember { mutableStateOf<ModelData?>(null) }

    // Load the model once
    LaunchedEffect(Unit) {
        currentModel = ModelManager.getModel(modelName)
    }

    // Until we have a model, just show nothing (or a progress indicator).
    if (currentModel == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Loading…")
        }
        return
    }

    val model = currentModel!!
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background, topBar = {
            ChatSettingsTopAppBar(onBack = {
                // Finish the activity
                (context as? Activity)?.finish()
            }, onSave = {
                coroutineScope.launch {
                    // Persist changes
                    ModelManager.updateModel(model).let {
                        ModelManager.loadModelAwait(model){
                            Log.d("ChatSettings", "Model updated")
                            (context as? Activity)?.finish()
                        }
                    }
                }
            })
        }) { padding ->
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
            AnimatedContent(
                targetState = switchTab, transitionSpec = {
                    fadeIn(tween(300)) + slideInHorizontally(
                        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)
                    ) { if (targetState > initialState) it else -it } togetherWith fadeOut(tween(200))
                }, label = "content"
            ) { tab ->
                when (tab) {
                    0 -> ContextContent(
                        systemPrompt = model.systemPrompt,
                        onPromptChange = { currentModel = currentModel!!.copy(systemPrompt = it) })

                    1 -> ModelContent(
                        model = model, onModelChange = { currentModel = it })
                }
            }
        }
    }
}

// State kept for the currently selected tab (0‑based).
private var _selectedTab: Int by mutableIntStateOf(0)
private val switchTab get() = _selectedTab

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatSettingsTopAppBar(
    onBack: () -> Unit, onSave: () -> Unit
) {
    Column {
        TopAppBar(
            navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }
        }, title = {
            Text(
                "Chat Settings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        }, actions = {
            IconButton(onClick = onSave) {
                Icon(
                    imageVector = Icons.Default.Check, contentDescription = "Save"
                )
            }
        }, colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.primary
        )
        )
        // Tabs
        SecondaryTabRow(
            _selectedTab,
            Modifier,
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.onSurface,
            {
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(_selectedTab),
                    color = MaterialTheme.colorScheme.primary
                )
            },
            @Composable { HorizontalDivider() },
            {
                listOf("Context", "Model").forEachIndexed { index, label ->
                    Tab(
                        selected = index == _selectedTab,
                        onClick = { _selectedTab = index },
                        text = { Text(label, style = MaterialTheme.typography.bodyMedium) })
                }
            })
    }
}

@Composable
fun ContextContent(
    systemPrompt: String, onPromptChange: (String) -> Unit
) {
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
                value = systemPrompt,
                onValueChange = onPromptChange,
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
                text = "Token count: ${systemPrompt.split(" ").size}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

@Composable
fun ModelContent(
    model: ModelData, onModelChange: (ModelData) -> Unit
) {
    Log.d("ModelContent", "ModelContent called with model: $model")
    Column(verticalArrangement = Arrangement.spacedBy(rDP(12.dp))) {
        SettingsCard(title = "Generation Settings") {
            Column(verticalArrangement = Arrangement.spacedBy(rDP(20.dp))) {
                ModernSlider(
                    label = "Temperature",
                    description = "Controls randomness in responses",
                    value = model.temp,
                    onValueChange = { onModelChange(model.copy(temp = it)) },
                    valueRange = 0f..2f
                )

                ModernSlider(
                    label = "Top K",
                    description = "Limits vocabulary selection",
                    value = model.topK.toFloat(),
                    steps = 50,
                    onValueChange = { onModelChange(model.copy(topK = it.toInt())) },
                    valueRange = 1f..100f
                )

                ModernSlider(
                    label = "Top P",
                    description = "Nucleus sampling threshold",
                    value = model.topP,
                    onValueChange = { onModelChange(model.copy(topP = it)) },
                    valueRange = 0f..1f
                )

                ModernSlider(
                    label = "Min P",
                    description = "Minimum probability threshold",
                    value = model.minP,
                    onValueChange = { onModelChange(model.copy(minP = it)) },
                    valueRange = 0f..1f
                )
            }
        }

        SettingsCard(title = "Context Settings") {
            Column(verticalArrangement = Arrangement.spacedBy(rDP(16.dp))) {
                ModernSlider(
                    label = "Context Size",
                    description = "Maximum context window",
                    value = model.ctxSize.toFloat(),
                    onValueChange = { onModelChange(model.copy(ctxSize = it.toInt())) },
                    valueRange = 512f..16384f,
                    steps = 15
                )

                ModernSlider(
                    label = "Max Tokens",
                    description = "Maximum response length",
                    value = model.maxTokens.toFloat(),
                    onValueChange = { onModelChange(model.copy(maxTokens = it.toInt())) },
                    valueRange = if (model.providerName == ModelProvider.OpenRouter.toString()) 1f..80192f else 1f..16096f,
                    steps = if (model.providerName == ModelProvider.OpenRouter.toString()) 79 else 15
                )
            }
        }

        SettingsCard(title = "Advanced Options") {
            Column(verticalArrangement = Arrangement.spacedBy(rDP(4.dp))) {
                ModernSwitchRow(
                    label = "Use MMAP",
                    description = "Memory‑mapped file loading",
                    checked = model.useMMAP,
                    onCheckedChange = { onModelChange(model.copy(useMMAP = it)) })

                ModernSwitchRow(
                    label = "Use MLOCK",
                    description = "Lock model in RAM",
                    checked = model.useMLOCK,
                    onCheckedChange = { onModelChange(model.copy(useMLOCK = it)) })
            }
        }
    }
}

@Composable
fun SettingsCard(
    title: String? = null, content: @Composable ColumnScope.() -> Unit
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
                targetState = displayValue, transitionSpec = {
                    (slideInVertically { -it } + fadeIn()) togetherWith (slideOutVertically { it } + fadeOut())
                }, label = "value"
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
            value = value, onValueChange = {
            onValueChange(it)
            haptics.performHapticFeedback(HapticFeedbackType.LongPress) // tick
        }, onValueChangeFinished = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress) // final
        }, valueRange = valueRange, steps = steps
        )
    }
}

@Composable
fun ModernSwitchRow(
    label: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(rDP(12.dp)))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = rDP(12.dp)),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
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
            checked = checked, onCheckedChange = onCheckedChange
        )
    }
}

@Composable
fun ModernSwitch(
    checked: Boolean, onCheckedChange: (Boolean) -> Unit
) {
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) rDP(20.dp) else rDP(0.dp), animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium
        ), label = "thumbOffset"
    )

    val trackColor by animateColorAsState(
        targetValue = if (checked) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(300),
        label = "trackColor"
    )

    val thumbColor by animateColorAsState(
        targetValue = if (checked) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outline, animationSpec = tween(300), label = "thumbColor"
    )

    Box(
        modifier = Modifier
            .width(rDP(52.dp))
            .height(rDP(32.dp))
            .clip(RoundedCornerShape(rDP(16.dp)))
            .background(trackColor)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }) { onCheckedChange(!checked) }
            .padding(rDP(4.dp))) {
        Box(
            modifier = Modifier
                .size(rDP(24.dp))
                .offset(x = thumbOffset)
                .clip(RoundedCornerShape(rDP(12.dp)))
                .background(thumbColor)
        )
    }
}