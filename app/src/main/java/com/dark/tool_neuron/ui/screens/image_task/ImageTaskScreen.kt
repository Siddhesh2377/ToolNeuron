package com.dark.tool_neuron.ui.screens.image_task

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dark.download_manager.formatBytes
import com.dark.tool_neuron.model.ModelInfo
import com.dark.tool_neuron.ui.components.ActionTextButton
import com.dark.tool_neuron.ui.components.ActionToggleGroup
import com.dark.tool_neuron.ui.components.BodyLabel
import com.dark.tool_neuron.ui.components.CaptionText
import com.dark.tool_neuron.ui.components.StandardCard
import com.dark.tool_neuron.ui.components.SwitchRow
import com.dark.tool_neuron.ui.components.TnTextField
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.LocalTnShapes
import com.dark.tool_neuron.viewmodel.ImageTaskMode
import com.dark.tool_neuron.viewmodel.ImageTaskUi
import com.dark.tool_neuron.viewmodel.ImageTaskViewModel
import com.dark.tool_neuron.viewmodel.ImageOutputAction
import com.dark.tool_neuron.viewmodel.ModelLoadPhase
import com.dark.tool_neuron.viewmodel.RuntimePhase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ImageTaskScreen(
    innerPadding: PaddingValues,
    viewModel: ImageTaskViewModel,
    onOpenStore: () -> Unit,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val dimens = LocalDimens.current
    val context = LocalContext.current

    Log.d(
        "ImageTaskScreen",
        "render mode=${ui.mode} models=${ui.installedDiffusionModels.size} " +
            "upscalers=${ui.installedUpscalers.size} active=${ui.activeModelId} " +
            "busy=${ui.isBusy} status='${ui.statusText}' err='${ui.errorText}'",
    )

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::setInputImage) }
    var pendingSaveElsewhereBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var handledSaveElsewhereToken by remember { mutableStateOf("") }
    val saveElsewhere = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/png"),
    ) { uri ->
        val bitmap = pendingSaveElsewhereBitmap
        pendingSaveElsewhereBitmap = null
        if (uri != null && bitmap != null) {
            val result = writeBitmapToUri(context, bitmap, uri)
            Toast.makeText(
                context,
                if (result.isSuccess) "Saved image" else "Save failed: ${result.exceptionOrNull()?.message}",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    var showMaskPainter by remember { mutableStateOf(false) }
    var painterBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var painterLoading by remember { mutableStateOf(false) }

    LaunchedEffect(showMaskPainter, ui.inputImagePath) {
        if (showMaskPainter && painterBitmap == null && !painterLoading) {
            painterLoading = true
            painterBitmap = viewModel.loadInputBitmap()
            painterLoading = false
            if (painterBitmap == null) showMaskPainter = false
        }
        if (!showMaskPainter) {
            painterBitmap = null
        }
    }

    LaunchedEffect(ui.outputToken, ui.outputAction, ui.diffusionResultImage, ui.upscaleResultImage) {
        val image = ui.upscaleResultImage ?: ui.diffusionResultImage
        if (ui.outputAction == ImageOutputAction.SAVE_ELSEWHERE &&
            ui.outputToken.isNotBlank() &&
            ui.outputToken != handledSaveElsewhereToken &&
            image != null
        ) {
            handledSaveElsewhereToken = ui.outputToken
            pendingSaveElsewhereBitmap = image
            val prefix = when (ui.mode) {
                ImageTaskMode.GENERATE -> "tn_generate"
                ImageTaskMode.INPAINT -> "tn_inpaint"
                ImageTaskMode.UPSCALE -> "tn_upscale"
            }
            saveElsewhere.launch("${prefix}_${System.currentTimeMillis()}.png")
        }
    }

    val painterImage = painterBitmap
    if (showMaskPainter && painterImage != null) {
        MaskPainterDialog(
            inputBitmap = painterImage,
            cacheDir = context.cacheDir,
            onDismiss = { showMaskPainter = false },
            onConfirm = { path ->
                viewModel.setMaskPath(path)
                showMaskPainter = false
            },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = dimens.screenPadding,
            end = dimens.screenPadding,
            top = innerPadding.calculateTopPadding() + dimens.spacingSm,
            bottom = innerPadding.calculateBottomPadding() + dimens.spacingSm,
        ),
        verticalArrangement = Arrangement.spacedBy(dimens.spacingMd),
    ) {
        item("runtime") { RuntimeCard(ui = ui, viewModel = viewModel) }
        item("mode") { ModeCard(ui = ui, onSelect = viewModel::setMode) }
        item("model") {
            ModelPickCard(
                ui = ui,
                onPickModel = viewModel::setActiveModel,
                onPickUpscaler = viewModel::setActiveUpscaler,
                onOpenStore = onOpenStore,
            )
        }
        item("gpu") {
            ImageGpuCard(
                ui = ui,
                onToggle = viewModel::setGpuAcceleration,
            )
        }
        if (ui.modelLoadPhase !is ModelLoadPhase.Idle && ui.modelLoadPhase !is ModelLoadPhase.Loaded) {
            item("model-load") {
                ModelLoadCard(
                    phase = ui.modelLoadPhase,
                    onRetry = viewModel::retryLoadActiveModel,
                    onDismiss = viewModel::dismissLoadFailure,
                )
            }
        }

        when (ui.mode) {
            ImageTaskMode.GENERATE -> generateBody(ui, viewModel)
            ImageTaskMode.INPAINT -> inpaintBody(
                ui, viewModel,
                onPickImage = { pickImage.launch(arrayOf("image/*")) },
                onPaintMask = { showMaskPainter = true },
            )
            ImageTaskMode.UPSCALE -> upscaleBody(
                ui, viewModel,
                onPickImage = { pickImage.launch(arrayOf("image/*")) },
            )
        }
    }
}

private fun LazyListScope.generateBody(
    ui: ImageTaskUi,
    viewModel: ImageTaskViewModel,
) {
    item("g-prompt") { PromptCard(ui = ui, viewModel = viewModel) }
    item("g-knobs") { KnobsCard(ui = ui, viewModel = viewModel, showDenoise = false) }
    item("g-output-policy") { OutputPolicyCard(ui = ui, viewModel = viewModel) }
    item("g-run") { RunCard(ui = ui, viewModel = viewModel) }
    item("g-output") {
        DiffusionOutputCard(
            ui = ui,
            sectionTitle = "Generated image",
            saveNamePrefix = "tn_generate",
            onUpscale = { bitmap -> viewModel.upscaleResultBitmap(bitmap) },
        )
    }
}

private fun LazyListScope.inpaintBody(
    ui: ImageTaskUi,
    viewModel: ImageTaskViewModel,
    onPickImage: () -> Unit,
    onPaintMask: () -> Unit,
) {
    item("i-input") {
        InputImageCard(
            ui = ui,
            mode = ImageTaskMode.INPAINT,
            onPick = onPickImage,
            onPaintMask = onPaintMask,
            onClearMask = { viewModel.setMaskPath(null) },
        )
    }
    item("i-prompt") { PromptCard(ui = ui, viewModel = viewModel) }
    item("i-knobs") { KnobsCard(ui = ui, viewModel = viewModel, showDenoise = true) }
    item("i-output-policy") { OutputPolicyCard(ui = ui, viewModel = viewModel) }
    item("i-run") { RunCard(ui = ui, viewModel = viewModel) }
    item("i-output") {
        DiffusionOutputCard(
            ui = ui,
            sectionTitle = "Inpainted image",
            saveNamePrefix = "tn_inpaint",
            onUpscale = { bitmap -> viewModel.upscaleResultBitmap(bitmap) },
        )
    }
}

private fun LazyListScope.upscaleBody(
    ui: ImageTaskUi,
    @Suppress("UNUSED_PARAMETER") viewModel: ImageTaskViewModel,
    onPickImage: () -> Unit,
) {
    item("u-input") {
        InputImageCard(
            ui = ui,
            mode = ImageTaskMode.UPSCALE,
            onPick = onPickImage,
            onPaintMask = {},
            onClearMask = {},
        )
    }
    item("u-output-policy") { OutputPolicyCard(ui = ui, viewModel = viewModel) }
    item("u-run") { RunCard(ui = ui, viewModel = viewModel) }
    item("u-output") { UpscaleOutputCard(ui = ui) }
}

@Composable
private fun OutputPolicyCard(ui: ImageTaskUi, viewModel: ImageTaskViewModel) {
    val dimens = LocalDimens.current
    StandardCard(
        title = "After completion",
        icon = TnIcons.Download,
        description = "Choose where the new image goes when the task finishes.",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(dimens.spacingSm)) {
            ActionToggleGroup(
                items = listOf(
                    ImageOutputAction.KEEP,
                    ImageOutputAction.REPLACE_INPUT,
                    ImageOutputAction.SAVE_PHOTOS,
                    ImageOutputAction.SAVE_ELSEWHERE,
                ),
                selectedItem = ui.outputAction,
                onItemSelected = viewModel::setOutputAction,
                itemLabel = { action ->
                    when (action) {
                        ImageOutputAction.KEEP -> "Keep"
                        ImageOutputAction.REPLACE_INPUT -> "Replace"
                        ImageOutputAction.SAVE_PHOTOS -> "Photos"
                        ImageOutputAction.SAVE_ELSEWHERE -> "Save as"
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            CaptionText(
                text = when (ui.outputAction) {
                    ImageOutputAction.KEEP -> "Keep the result in this screen. You can still Save Photos or Save as from the result card."
                    ImageOutputAction.REPLACE_INPUT -> "Use the new image as the next input and clear the mask."
                    ImageOutputAction.SAVE_PHOTOS -> "Auto-save to Android Photos: Pictures/ToolNeuron."
                    ImageOutputAction.SAVE_ELSEWHERE -> "Open Android's save dialog when the new image is ready."
                },
            )
            if (ui.outputStatus.isNotBlank()) {
                BodyLabel(text = ui.outputStatus)
            }
        }
    }
}

@Composable
private fun ModelLoadCard(
    phase: ModelLoadPhase,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current
    val (title, description, isError) = when (phase) {
        is ModelLoadPhase.Loading -> Triple(
            "Loading ${phase.modelName}",
            "First load can take 30–90 s. Holding native sessions in memory.",
            false,
        )
        is ModelLoadPhase.Failed -> Triple(
            "Couldn't load ${phase.modelName}",
            "The native engine refused this model. Reason below.",
            true,
        )
        else -> Triple("", "", false)
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (isError) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
        shape = tnShapes.card,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimens.cardPadding),
            verticalArrangement = Arrangement.spacedBy(dimens.spacingSm),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
            ) {
                Icon(
                    imageVector = if (isError) TnIcons.AlertTriangle else TnIcons.Sparkles,
                    contentDescription = null,
                    tint = if (isError) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(dimens.iconMd),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isError) MaterialTheme.colorScheme.onErrorContainer
                            else MaterialTheme.colorScheme.onSurface,
                    )
                    if (description.isNotBlank()) {
                        Text(
                            text = description,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            when (phase) {
                is ModelLoadPhase.Loading -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                is ModelLoadPhase.Failed -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surface,
                        shape = tnShapes.cardSmall,
                    ) {
                        Text(
                            text = phase.reason,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(dimens.cardPadding),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
                    ) {
                        ActionTextButton(
                            onClickListener = onRetry,
                            icon = TnIcons.Refresh,
                            text = "Retry",
                            modifier = Modifier.weight(1f),
                        )
                        ActionTextButton(
                            onClickListener = onDismiss,
                            icon = TnIcons.X,
                            text = "Dismiss",
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                else -> Unit
            }
        }
    }
}

@Composable
private fun RuntimeCard(ui: ImageTaskUi, viewModel: ImageTaskViewModel) {
    val dimens = LocalDimens.current
    val title = when (ui.runtimePhase) {
        RuntimePhase.NEEDS_DOWNLOAD -> "Runtime — not downloaded"
        RuntimePhase.DOWNLOADING -> "Runtime — downloading"
        RuntimePhase.READY_TO_INITIALIZE -> "Runtime — ready to initialize"
        RuntimePhase.INITIALIZING -> "Runtime — initializing"
        RuntimePhase.READY -> "Runtime — ready"
    }
    val description = when (ui.runtimePhase) {
        RuntimePhase.NEEDS_DOWNLOAD -> "One-time ~30 MB download. Required to run any image model."
        RuntimePhase.DOWNLOADING -> {
            val total = ui.runtimeDownloadTotal
            val got = ui.runtimeDownloadBytes
            if (total > 0) "${formatBytes(got)} / ${formatBytes(total)}"
            else "Downloading…"
        }
        RuntimePhase.READY_TO_INITIALIZE -> "Initializing automatically…"
        RuntimePhase.INITIALIZING -> ui.statusText.ifBlank { "Extracting native libs…" }
        RuntimePhase.READY -> "Image gen + upscale are ready."
    }
    StandardCard(
        title = title,
        icon = TnIcons.Download,
        description = description,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(dimens.spacingSm)) {
            if (ui.runtimePhase == RuntimePhase.DOWNLOADING) {
                LinearProgressIndicator(
                    progress = { ui.runtimeDownloadProgress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            when (ui.runtimePhase) {
                RuntimePhase.NEEDS_DOWNLOAD -> {
                    ActionTextButton(
                        onClickListener = viewModel::startRuntimeDownload,
                        icon = TnIcons.Download,
                        text = "Download runtime",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                RuntimePhase.DOWNLOADING -> {
                    ActionTextButton(
                        onClickListener = viewModel::cancelRuntimeDownload,
                        icon = TnIcons.X,
                        text = "Cancel",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                RuntimePhase.READY_TO_INITIALIZE,
                RuntimePhase.INITIALIZING -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                RuntimePhase.READY -> Unit
            }
        }
    }
}

@Composable
private fun ImageGpuCard(
    ui: ImageTaskUi,
    onToggle: (Boolean) -> Unit,
) {
    StandardCard(
        title = "Acceleration",
        icon = TnIcons.Zap,
    ) {
        SwitchRow(
            title = "Use GPU / OpenCL",
            description = when (ui.mode) {
                ImageTaskMode.UPSCALE ->
                    "Applies to non-MNN upscalers. MNN upscalers keep their own mobile backend."
                else ->
                    "Applies to image generation and inpaint when the runtime/model backend supports OpenCL."
            },
            checked = ui.useGpuAcceleration,
            onCheckedChange = onToggle,
            icon = TnIcons.Cpu,
            enabled = !ui.isBusy,
        )
        CaptionText(
            text = "If a model fails to load or the device gets unstable, turn this off and retry.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ModeCard(
    ui: ImageTaskUi,
    onSelect: (ImageTaskMode) -> Unit,
) {
    val dimens = LocalDimens.current
    StandardCard(
        title = "Task",
        icon = TnIcons.Sparkles,
        description = "Pick what to do with image models",
    ) {
        ActionToggleGroup(
            items = listOf(
                ImageTaskMode.GENERATE,
                ImageTaskMode.INPAINT,
                ImageTaskMode.UPSCALE,
            ),
            selectedItem = ui.mode,
            onItemSelected = onSelect,
            itemLabel = { mode ->
                when (mode) {
                    ImageTaskMode.GENERATE -> "Generate"
                    ImageTaskMode.INPAINT -> "Inpaint"
                    ImageTaskMode.UPSCALE -> "Upscale"
                }
            },
            modifier = Modifier.fillMaxWidth(),
            height = dimens.toggleGroupHeight + 8.dp,
        )
    }
}

@Composable
private fun ModelPickCard(
    ui: ImageTaskUi,
    onPickModel: (String) -> Unit,
    onPickUpscaler: (String) -> Unit,
    onOpenStore: () -> Unit,
) {
    val dimens = LocalDimens.current
    val needsUpscaler = ui.mode == ImageTaskMode.UPSCALE
    val list: List<ModelInfo> = if (needsUpscaler) ui.installedUpscalers else ui.installedDiffusionModels
    val activeId = if (needsUpscaler) ui.activeUpscalerId else ui.activeModelId
    val pendingId = (ui.modelLoadPhase as? ModelLoadPhase.Loading)
        ?.let { activeId } ?: ""

    StandardCard(
        title = if (needsUpscaler) "Upscaler" else "Image model",
        icon = TnIcons.Photo,
        description = if (list.isEmpty())
            "Install one from the Store first"
        else "Tap to load — first load may take 30-90s",
    ) {
        if (list.isEmpty()) {
            ActionTextButton(
                onClickListener = onOpenStore,
                icon = TnIcons.Package,
                text = "Open Store",
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(dimens.spacingXs)) {
                list.forEach { model ->
                    ModelRow(
                        model = model,
                        selected = model.id == activeId,
                        loading = model.id == pendingId,
                        onClick = {
                            if (needsUpscaler) onPickUpscaler(model.id) else onPickModel(model.id)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelRow(
    model: ModelInfo,
    selected: Boolean,
    loading: Boolean,
    onClick: () -> Unit,
) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clickable(enabled = !loading) {
                Log.d("ImageTaskScreen", "ModelRow click id=${model.id}")
                onClick()
            },
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = tnShapes.cardSmall,
    ) {
        Row(
            modifier = Modifier.padding(dimens.cardPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(dimens.iconMd),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Icon(
                    imageVector = if (selected) TnIcons.Check else TnIcons.Photo,
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(dimens.iconMd),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                BodyLabel(text = model.name)
                CaptionText(text = model.id)
            }
        }
    }
}

@Composable
private fun PromptCard(ui: ImageTaskUi, viewModel: ImageTaskViewModel) {
    val dimens = LocalDimens.current
    val promptPlaceholder = when (ui.mode) {
        ImageTaskMode.INPAINT -> "Describe what to fill in the painted area…"
        else -> "Describe the image you want…"
    }
    StandardCard(title = "Prompt", icon = TnIcons.Edit) {
        Column(verticalArrangement = Arrangement.spacedBy(dimens.spacingSm)) {
            CaptionText(text = "Positive prompt")
            TnTextField(
                value = ui.prompt,
                onValueChange = viewModel::setPrompt,
                placeholder = promptPlaceholder,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 6,
            )
            CaptionText(text = "Negative prompt")
            TnTextField(
                value = ui.negativePrompt,
                onValueChange = viewModel::setNegativePrompt,
                placeholder = "Things to avoid",
                modifier = Modifier.fillMaxWidth(),
                maxLines = 4,
            )
        }
    }
}

@Composable
private fun KnobsCard(
    ui: ImageTaskUi,
    viewModel: ImageTaskViewModel,
    showDenoise: Boolean,
) {
    val dimens = LocalDimens.current
    StandardCard(title = "Settings", icon = TnIcons.Sliders) {
        Column(verticalArrangement = Arrangement.spacedBy(dimens.spacingSm)) {
            CaptionText(text = "Steps: ${ui.steps}")
            ActionToggleGroup(
                items = listOf(8, 14, 20, 28, 40),
                selectedItem = ui.steps,
                onItemSelected = viewModel::setSteps,
                itemLabel = { it.toString() },
                modifier = Modifier.fillMaxWidth(),
            )
            CaptionText(text = "CFG: ${"%.1f".format(ui.cfg)}")
            ActionToggleGroup(
                items = listOf(3f, 5f, 7f, 9f, 12f),
                selectedItem = ui.cfg,
                onItemSelected = viewModel::setCfg,
                itemLabel = { "%.0f".format(it) },
                modifier = Modifier.fillMaxWidth(),
            )
            CaptionText(text = "Scheduler")
            ActionToggleGroup(
                items = listOf("dpm", "euler_a", "lcm"),
                selectedItem = ui.schedulerKey,
                onItemSelected = viewModel::setScheduler,
                itemLabel = { it.uppercase() },
                modifier = Modifier.fillMaxWidth(),
            )
            CaptionText(text = "Resolution")
            if (ui.supportedResolutions.isEmpty()) {
                BodyLabel(
                    text = if (ui.activeModelId.isBlank())
                        "Pick an image model to see the resolutions it supports."
                    else
                        "Detecting supported resolutions…",
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                ActionToggleGroup(
                    items = ui.supportedResolutions,
                    selectedItem = ui.width to ui.height,
                    onItemSelected = { (w, h) -> viewModel.setResolution(w, h) },
                    itemLabel = { (w, h) -> if (w == h) "${w}²" else "${w}×${h}" },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (showDenoise) {
                CaptionText(text = "Denoise: ${"%.2f".format(ui.denoiseStrength)}")
                ActionToggleGroup(
                    items = listOf(0.2f, 0.4f, 0.6f, 0.8f),
                    selectedItem = ui.denoiseStrength,
                    onItemSelected = viewModel::setDenoiseStrength,
                    itemLabel = { "%.1f".format(it) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun InputImageCard(
    ui: ImageTaskUi,
    mode: ImageTaskMode,
    onPick: () -> Unit,
    onPaintMask: () -> Unit,
    onClearMask: () -> Unit,
) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current
    val context = LocalContext.current
    val path = ui.inputImagePath

    val title = when (mode) {
        ImageTaskMode.INPAINT -> "Source image + mask"
        ImageTaskMode.UPSCALE -> "Image to upscale"
        else -> "Input image"
    }

    StandardCard(title = title, icon = TnIcons.Photo) {
        Column(verticalArrangement = Arrangement.spacedBy(dimens.spacingSm)) {
            if (path == null) {
                CaptionText(text = "No image picked yet")
                ActionTextButton(
                    onClickListener = onPick,
                    icon = TnIcons.Photo,
                    text = "Pick image",
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                val bitmap by produceState<Bitmap?>(initialValue = null, path) {
                    value = withContext(Dispatchers.IO) { decodeBitmap(context, path) }
                }
                val image = bitmap
                if (image != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(image.width.toFloat() / image.height.toFloat())
                            .clip(tnShapes.cardSmall),
                    ) {
                        Image(
                            bitmap = image.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                    }
                } else {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        shape = tnShapes.cardSmall,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                    }
                }

                ActionTextButton(
                    onClickListener = onPick,
                    icon = TnIcons.Refresh,
                    text = "Replace image",
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (mode == ImageTaskMode.INPAINT && path != null) {
                if (ui.hasMask) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = TnIcons.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(dimens.iconMd),
                        )
                        BodyLabel(
                            text = "Mask painted",
                            modifier = Modifier.weight(1f),
                        )
                        ActionTextButton(
                            onClickListener = onPaintMask,
                            icon = TnIcons.Edit,
                            text = "Repaint",
                        )
                        ActionTextButton(
                            onClickListener = onClearMask,
                            icon = TnIcons.X,
                            text = "Clear",
                        )
                    }
                } else {
                    ActionTextButton(
                        onClickListener = onPaintMask,
                        icon = TnIcons.Edit,
                        text = "Paint mask",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    CaptionText(
                        text = "Inpaint regenerates only the painted area. " +
                            "Without a mask it falls back to img2img.",
                    )
                }
            }
        }
    }
}

@Composable
private fun RunCard(ui: ImageTaskUi, viewModel: ImageTaskViewModel) {
    val dimens = LocalDimens.current
    Column(verticalArrangement = Arrangement.spacedBy(dimens.spacingSm)) {
        ProgressMetricsCard(ui)
        if (ui.statusText.isNotBlank()) {
            CaptionText(text = ui.statusText)
        }
        if (ui.errorText != null) {
            Text(
                text = ui.errorText,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        if (ui.isBusy && ui.progress > 0f) {
            LinearProgressIndicator(
                progress = { ui.progress },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
        ) {
            ActionTextButton(
                onClickListener = viewModel::run,
                icon = TnIcons.Sparkles,
                text = when (ui.mode) {
                    ImageTaskMode.GENERATE -> "Generate"
                    ImageTaskMode.INPAINT -> "Inpaint"
                    ImageTaskMode.UPSCALE -> "Upscale 4×"
                },
                enabled = !ui.isBusy,
                modifier = Modifier.weight(1f),
            )
            if (ui.isBusy) {
                ActionTextButton(
                    onClickListener = viewModel::cancel,
                    icon = TnIcons.PlayerStop,
                    text = "Stop",
                )
            }
        }
    }
}

@Composable
private fun ProgressMetricsCard(ui: ImageTaskUi) {
    val metrics = ui.metrics
    if (!metrics.active && metrics.progress <= 0f) return
    val dimens = LocalDimens.current
    StandardCard(
        title = metrics.label.ifBlank { "Progress" },
        icon = TnIcons.Zap,
        description = metrics.detail,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(dimens.spacingSm)) {
            LinearProgressIndicator(
                progress = { metrics.progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
            ) {
                MetricPill("Elapsed", formatDuration(metrics.elapsedMs), Modifier.weight(1f))
                MetricPill("ETA", metrics.etaMs?.let(::formatDuration) ?: "--", Modifier.weight(1f))
                MetricPill("Done", "${(metrics.progress.coerceIn(0f, 1f) * 100).toInt()}%", Modifier.weight(1f))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
            ) {
                val step = if (metrics.currentStep != null && metrics.totalSteps != null)
                    "${metrics.currentStep}/${metrics.totalSteps}" else "--"
                MetricPill("Step", step, Modifier.weight(1f))
                MetricPill("Step time", metrics.stepMs?.let(::formatDuration) ?: "--", Modifier.weight(1f))
                val res = if (metrics.width > 0 && metrics.height > 0) "${metrics.width}×${metrics.height}" else "--"
                MetricPill("Output", res, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MetricPill(label: String, value: String, modifier: Modifier = Modifier) {
    val dimens = LocalDimens.current
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        shape = LocalTnShapes.current.cardSmall,
    ) {
        Column(modifier = Modifier.padding(dimens.spacingSm)) {
            CaptionText(text = label)
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun DiffusionOutputCard(
    ui: ImageTaskUi,
    sectionTitle: String,
    saveNamePrefix: String,
    onUpscale: (Bitmap) -> Unit,
) {
    val resultImage = ui.diffusionResultImage
    val previewImage = ui.previewImage
    val image = resultImage ?: previewImage ?: return
    val isResult = resultImage != null
    ImageViewerCard(
        bitmap = image,
        title = if (isResult) sectionTitle else "Live preview",
        saveNamePrefix = saveNamePrefix,
        showUpscale = isResult,
        onUpscaleClick = { onUpscale(image) },
    )
}

private fun formatDuration(ms: Long): String {
    val safe = ms.coerceAtLeast(0L)
    val totalSec = (safe + 999L) / 1000L
    val min = totalSec / 60L
    val sec = totalSec % 60L
    return if (min > 0) "${min}m ${sec}s" else "${sec}s"
}

@Composable
private fun UpscaleOutputCard(ui: ImageTaskUi) {
    val image = ui.upscaleResultImage ?: return
    ImageViewerCard(
        bitmap = image,
        title = "Upscaled 4×",
        saveNamePrefix = "tn_upscale",
        showUpscale = false,
        onUpscaleClick = null,
    )
}

private fun decodeBitmap(context: android.content.Context, path: String): Bitmap? = try {
    if (path.startsWith("content://") || path.startsWith("file://")) {
        context.contentResolver.openInputStream(Uri.parse(path))
            ?.use { BitmapFactory.decodeStream(it) }
    } else {
        BitmapFactory.decodeFile(path)
    }
} catch (_: Throwable) {
    null
}

private fun writeBitmapToUri(context: android.content.Context, bitmap: Bitmap, uri: Uri): Result<Unit> = runCatching {
    context.contentResolver.openOutputStream(uri).use { out ->
        requireNotNull(out) { "openOutputStream returned null" }
        if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
            throw IllegalStateException("Bitmap.compress returned false")
        }
    }
}
