package com.dark.tool_neuron.ui.screen.model_config

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import com.dark.tool_neuron.ui.theme.Motion
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dark.tool_neuron.R
import com.dark.tool_neuron.data.AppSettingsDataStore
import com.dark.tool_neuron.global.Standards
import com.dark.tool_neuron.models.enums.ProviderType
import com.dark.tool_neuron.models.table_schema.Model
import com.dark.tool_neuron.ui.components.ActionButton
import com.dark.tool_neuron.ui.components.ActionTextButton
import com.dark.tool_neuron.ui.components.CuteSwitch
import com.dark.tool_neuron.viewmodel.ModelConfigEditorViewModel
import com.dark.tool_neuron.ui.icons.TnIcons

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ModelConfigEditorScreen(
    onBackClick: () -> Unit,
    viewModel: ModelConfigEditorViewModel = hiltViewModel()
) {
    val models by viewModel.models.collectAsStateWithLifecycle(emptyList())
    val selectedModel by viewModel.selectedModel.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val saveSuccess by viewModel.saveSuccess.collectAsStateWithLifecycle()

    // Track which panel is showing (list or editor)
    var showingEditor by remember { mutableStateOf(false) }

    // Auto-show editor when model is selected
    LaunchedEffect(selectedModel) {
        if (selectedModel != null) {
            showingEditor = true
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        if (showingEditor && selectedModel != null) {
                            selectedModel!!.modelName
                        } else {
                            "Model Configuration"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    ActionTextButton(
                        onClickListener = {
                            if (showingEditor && selectedModel != null) {
                                // Go back to list
                                showingEditor = false
                            } else {
                                // Exit screen
                                onBackClick()
                            }
                        },
                        icon = TnIcons.ChevronLeft,
                        text = if (showingEditor && selectedModel != null) "Models" else "Back",
                        modifier = Modifier.padding(start = 6.dp)
                    )
                },
                actions = {
                    if (showingEditor && selectedModel != null) {
                        IconButton(
                            onClick = { viewModel.saveConfiguration() },
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            Icon(
                                imageVector = TnIcons.DeviceFloppy,
                                contentDescription = "Save",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (models.isEmpty()) {
                EmptyModelsState()
            } else {
                // Animated content switching
                AnimatedContent(
                    targetState = showingEditor && selectedModel != null,
                    transitionSpec = {
                        if (targetState) {
                            // Going to editor
                            slideInHorizontally(
                                initialOffsetX = { it },
                                animationSpec = Motion.content()
                            ) togetherWith slideOutHorizontally(
                                targetOffsetX = { -it / 3 },
                                animationSpec = Motion.content()
                            )
                        } else {
                            // Going back to list
                            slideInHorizontally(
                                initialOffsetX = { -it / 3 },
                                animationSpec = Motion.content()
                            ) togetherWith slideOutHorizontally(
                                targetOffsetX = { it },
                                animationSpec = Motion.content()
                            )
                        }.using(SizeTransform(clip = false))
                    },
                    label = "panelSwitch"
                ) { isShowingEditor ->
                    if (isShowingEditor && selectedModel != null) {
                        // Show Editor Panel
                        ConfigEditorPanel(
                            model = selectedModel!!,
                            viewModel = viewModel,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        // Show Model List Panel
                        ModelListPanel(
                            models = models,
                            selectedModel = selectedModel,
                            onModelSelected = { model ->
                                viewModel.selectModel(model)
                                showingEditor = true
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            // Loading overlay
            AnimatedVisibility(
                visible = isLoading,
                enter = fadeIn(Motion.entrance()),
                exit = fadeOut(Motion.exit())
            ) {
                LoadingOverlay()
            }

            // Save success message
            AnimatedVisibility(
                visible = saveSuccess,
                enter = fadeIn(Motion.entrance()) + slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = Motion.interactive()
                ),
                exit = fadeOut(Motion.exit()) + slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = Motion.exit()
                ),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = Standards.SpacingXxl)
            ) {
                SuccessMessage()
            }
        }
    }
}

@Composable
private fun ModelListPanel(
    models: List<Model>,
    selectedModel: Model?,
    onModelSelected: (Model) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    ) {
        Column {
            Text(
                text = "Models (${models.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(Standards.SpacingLg)
            )

            LazyColumn(
                contentPadding = PaddingValues(horizontal = Standards.SpacingSm, vertical = Standards.SpacingSm),
                verticalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
            ) {
                items(models, key = { it.id }) { model ->
                    ModelListItem(
                        model = model,
                        isSelected = selectedModel?.id == model.id,
                        onClick = { onModelSelected(model) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelListItem(
    model: Model,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        animationSpec = Motion.state(),
        label = "itemBg"
    )

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(Standards.RadiusLg),
        color = backgroundColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(Standards.SpacingMd)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Standards.SpacingMd),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when (model.providerType) {
                    ProviderType.GGUF -> TnIcons.FileText
                    ProviderType.DIFFUSION -> TnIcons.Photo
                    else -> TnIcons.Database
                },
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = model.modelName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1
                )
                Text(
                    text = model.providerType.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (model.isActive) {
                Icon(
                    imageVector = TnIcons.CircleCheck,
                    contentDescription = "Active",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun ConfigEditorPanel(
    model: Model,
    viewModel: ModelConfigEditorViewModel,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(Standards.SpacingLg)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = model.modelName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${model.providerType.name} Model Configuration",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(Standards.SpacingLg))

        // Config content based on model type
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(Standards.SpacingLg)
        ) {
            item {
                when (model.providerType) {
                    ProviderType.GGUF -> GgufConfigEditor(viewModel)
                    ProviderType.DIFFUSION -> DiffusionConfigEditor(viewModel)
                    else -> {
                        Text(
                            "Unsupported model type",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GgufConfigEditor(viewModel: ModelConfigEditorViewModel) {
    val ggufConfig by viewModel.ggufConfig.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val hardwareTuningEnabled by remember {
        AppSettingsDataStore(context).hardwareTuningEnabled
    }.collectAsStateWithLifecycle(initialValue = true)

    val loadingLocked = hardwareTuningEnabled

    val physicalCores = remember {
        try {
            val text = java.io.File("/sys/devices/system/cpu/present").readText().trim()
            val parts = text.split("-")
            if (parts.size == 2) parts[1].toInt() + 1 else Runtime.getRuntime().availableProcessors()
        } catch (_: Exception) {
            Runtime.getRuntime().availableProcessors()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(Standards.SpacingLg)) {
        ConfigSection("Loading Parameters") {
            AnimatedVisibility(
                visible = loadingLocked,
                enter = Motion.Enter,
                exit = Motion.Exit
            ) {
                Text(
                    text = "Managed by Performance Mode \u2014 disable Hardware Tuning in Settings to edit",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Standards.SpacingSm)
                )
            }

            IntField(
                label = "Threads",
                value = ggufConfig.loadingParams.threads,
                onValueChange = { viewModel.updateGgufThreads(it) },
                range = 1..physicalCores,
                enabled = !loadingLocked
            )

            IntField(
                label = "Context Size",
                value = ggufConfig.loadingParams.ctxSize,
                onValueChange = { viewModel.updateGgufContextSize(it) },
                range = 512..32768,
                step = 512,
                enabled = !loadingLocked
            )

            SwitchField(
                label = "Use Memory Mapping (mmap)",
                checked = ggufConfig.loadingParams.useMmap,
                onCheckedChange = { viewModel.updateGgufUseMmap(it) },
                enabled = !loadingLocked
            )

            SwitchField(
                label = "Use Memory Lock (mlock)",
                checked = ggufConfig.loadingParams.useMlock,
                onCheckedChange = { viewModel.updateGgufUseMlock(it) },
                enabled = !loadingLocked
            )
        }

        ConfigSection("Inference Parameters") {
            FloatField(
                label = "Temperature",
                value = ggufConfig.inferenceParams.temperature,
                onValueChange = { viewModel.updateGgufTemperature(it) },
                range = 0f..2f,
                step = 0.1f
            )

            IntField(
                label = "Top K",
                value = ggufConfig.inferenceParams.topK,
                onValueChange = { viewModel.updateGgufTopK(it) },
                range = 1..100
            )

            FloatField(
                label = "Top P",
                value = ggufConfig.inferenceParams.topP,
                onValueChange = { viewModel.updateGgufTopP(it) },
                range = 0f..1f,
                step = 0.05f
            )

            FloatField(
                label = "Min P",
                value = ggufConfig.inferenceParams.minP,
                onValueChange = { viewModel.updateGgufMinP(it) },
                range = 0f..1f,
                step = 0.05f
            )

            IntField(
                label = "Max Tokens",
                value = ggufConfig.inferenceParams.maxTokens,
                onValueChange = { viewModel.updateGgufMaxTokens(it) },
                range = 1..4096,
                step = 128
            )

            IntField(
                label = "Mirostat Mode",
                value = ggufConfig.inferenceParams.mirostat,
                onValueChange = { viewModel.updateGgufMirostat(it) },
                range = 0..2
            )

            if (ggufConfig.inferenceParams.mirostat > 0) {
                FloatField(
                    label = "Mirostat Tau",
                    value = ggufConfig.inferenceParams.mirostatTau,
                    onValueChange = { viewModel.updateGgufMirostatTau(it) },
                    range = 0f..10f,
                    step = 0.5f
                )

                FloatField(
                    label = "Mirostat Eta",
                    value = ggufConfig.inferenceParams.mirostatEta,
                    onValueChange = { viewModel.updateGgufMirostatEta(it) },
                    range = 0f..1f,
                    step = 0.05f
                )
            }

            TextField(
                label = "System Prompt (Optional)",
                value = ggufConfig.inferenceParams.systemPrompt,
                onValueChange = { viewModel.updateGgufSystemPrompt(it) },
                multiline = true,
                minLines = 3
            )
        }
    }
}

@Composable
private fun DiffusionConfigEditor(viewModel: ModelConfigEditorViewModel) {
    val diffusionConfig by viewModel.diffusionConfig.collectAsStateWithLifecycle()
    val inferenceParams by viewModel.diffusionInferenceParams.collectAsStateWithLifecycle()

    Column(verticalArrangement = Arrangement.spacedBy(Standards.SpacingLg)) {
        ConfigSection("Model Configuration") {
            IntField(
                label = "Text Embedding Size",
                value = diffusionConfig.textEmbeddingSize,
                onValueChange = { viewModel.updateDiffusionEmbeddingSize(it) },
                range = 512..2048,
                step = 256
            )

            SwitchField(
                label = "Run on CPU",
                description = "Use CPU instead of NPU/GPU",
                checked = diffusionConfig.runOnCpu,
                onCheckedChange = { viewModel.updateDiffusionRunOnCpu(it) }
            )

            SwitchField(
                label = "Use CPU CLIP",
                description = "Process CLIP on CPU (MNN)",
                checked = diffusionConfig.useCpuClip,
                onCheckedChange = { viewModel.updateDiffusionUseCpuClip(it) }
            )

            SwitchField(
                label = "Pony v6 Model",
                description = "Enable for Pony Diffusion models",
                checked = diffusionConfig.isPony,
                onCheckedChange = { viewModel.updateDiffusionIsPony(it) }
            )

            SwitchField(
                label = "Safety Mode",
                description = "Enable content filtering",
                checked = diffusionConfig.safetyMode,
                onCheckedChange = { viewModel.updateDiffusionSafetyMode(it) }
            )
        }

        ConfigSection("Inference Parameters") {
            TextField(
                label = "Negative Prompt",
                value = inferenceParams.negativePrompt,
                onValueChange = { viewModel.updateDiffusionNegativePrompt(it) },
                multiline = true,
                minLines = 2
            )

            IntField(
                label = "Steps",
                value = inferenceParams.steps,
                onValueChange = { viewModel.updateDiffusionSteps(it) },
                range = 1..50,
                step = 1,
                description = "Number of denoising steps"
            )

            FloatField(
                label = "CFG Scale",
                value = inferenceParams.cfgScale,
                onValueChange = { viewModel.updateDiffusionCfgScale(it) },
                range = 1f..20f,
                step = 0.5f,
                description = "Classifier-free guidance scale"
            )

            FloatField(
                label = "Denoise Strength",
                value = inferenceParams.denoiseStrength,
                onValueChange = { viewModel.updateDiffusionDenoiseStrength(it) },
                range = 0f..1f,
                step = 0.05f,
                description = "Strength for img2img (0 = original, 1 = full denoise)"
            )

            // Scheduler dropdown
            Column(verticalArrangement = Arrangement.spacedBy(Standards.SpacingSm)) {
                Text(
                    text = "Scheduler",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )

                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
                ) {
                    listOf("dpm", "euler", "euler_a", "ddim", "pndm").forEach { scheduler ->
                        FilterChip(
                            selected = inferenceParams.scheduler == scheduler,
                            onClick = { viewModel.updateDiffusionScheduler(scheduler) },
                            label = {
                                Text(
                                    scheduler.uppercase(),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        )
                    }
                }
            }

            SwitchField(
                label = "Use OpenCL",
                description = "Enable OpenCL acceleration",
                checked = inferenceParams.useOpenCL,
                onCheckedChange = { viewModel.updateDiffusionUseOpenCL(it) }
            )

            SwitchField(
                label = "Show Diffusion Process",
                description = "Display intermediate images during generation",
                checked = inferenceParams.showDiffusionProcess,
                onCheckedChange = { viewModel.updateDiffusionShowProcess(it) }
            )

            if (inferenceParams.showDiffusionProcess) {
                IntField(
                    label = "Show Stride",
                    value = inferenceParams.showDiffusionStride,
                    onValueChange = { viewModel.updateDiffusionShowStride(it) },
                    range = 1..10,
                    step = 1,
                    description = "Show intermediate image every N steps"
                )
            }
        }
    }
}

@Composable
private fun ConfigSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(Standards.SpacingMd)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )

        Surface(
            shape = RoundedCornerShape(Standards.RadiusLg),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ) {
            Column(
                modifier = Modifier.padding(Standards.SpacingLg),
                verticalArrangement = Arrangement.spacedBy(Standards.SpacingMd)
            ) {
                content()
            }
        }
    }
}

@Composable
private fun IntField(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    step: Int = 1,
    description: String? = null,
    enabled: Boolean = true
) {
    val alpha = if (enabled) 1f else 0.5f
    Column(
        modifier = Modifier.alpha(alpha),
        verticalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                description?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(Standards.RadiusMd),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            ) {
                Text(
                    text = value.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = Standards.SpacingMd, vertical = 6.dp)
                )
            }
        }

        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first) / step,
            enabled = enabled
        )
    }
}

@Composable
private fun FloatField(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    description: String? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(Standards.SpacingSm)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                description?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(Standards.RadiusMd),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            ) {
                Text(
                    text = "%.2f".format(value),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = Standards.SpacingMd, vertical = 6.dp)
                )
            }
        }

        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = ((range.endInclusive - range.start) / step).toInt() - 1
        )
    }
}

@Composable
private fun SwitchField(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    description: String? = null,
    enabled: Boolean = true
) {
    val alpha = if (enabled) 1f else 0.5f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        CuteSwitch(
            checked = checked,
            onCheckedChange = { if (enabled) onCheckedChange(it) }
        )
    }
}

@Composable
private fun TextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    multiline: Boolean = false,
    minLines: Int = 1
) {
    Column(verticalArrangement = Arrangement.spacedBy(Standards.SpacingSm)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )

        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            minLines = if (multiline) minLines else 1,
            maxLines = if (multiline) 6 else 1,
            shape = RoundedCornerShape(Standards.RadiusMd)
        )
    }
}

@Composable
private fun EmptyModelsState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Standards.SpacingLg)
        ) {
            Icon(
                imageVector = TnIcons.Sparkles,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Text(
                "No Models Found",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Add models to configure them",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptySelectionState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Standards.SpacingMd)
        ) {
            Icon(
                imageVector = TnIcons.Settings,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Text(
                "Select a model to configure",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LoadingOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)),
        contentAlignment = Alignment.Center
    ) {
        LoadingIndicator()
    }
}

@Composable
private fun SuccessMessage() {
    Surface(
        shape = RoundedCornerShape(Standards.RadiusLg),
        color = MaterialTheme.colorScheme.primaryContainer,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = Standards.SpacingMd),
            horizontalArrangement = Arrangement.spacedBy(Standards.SpacingMd),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = TnIcons.CircleCheck,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                "Configuration saved successfully",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}