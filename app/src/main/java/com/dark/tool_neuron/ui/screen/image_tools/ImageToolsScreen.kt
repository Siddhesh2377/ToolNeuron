package com.dark.tool_neuron.ui.screen.image_tools

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import com.dark.tool_neuron.ui.theme.Motion
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dark.tool_neuron.service.ModelDownloadService
import com.dark.tool_neuron.ui.components.ActionButton
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.viewmodel.ImageToolsViewModel
import com.dark.tool_neuron.viewmodel.ImageToolsViewModel.ImageTool
import com.dark.tool_neuron.viewmodel.ImageToolsViewModel.ProcessingState
import com.dark.tool_neuron.global.Standards

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ImageToolsScreen(
    onNavigateBack: () -> Unit,
    viewModel: ImageToolsViewModel = hiltViewModel()
) {
    val selectedTool by viewModel.selectedTool.collectAsStateWithLifecycle()
    val inputImage by viewModel.inputImage.collectAsStateWithLifecycle()
    val styleImage by viewModel.styleImage.collectAsStateWithLifecycle()
    val resultImage by viewModel.resultImage.collectAsStateWithLifecycle()
    val processingState by viewModel.processingState.collectAsStateWithLifecycle()
    val styleStrength by viewModel.styleStrength.collectAsStateWithLifecycle()
    val toolModelReady by viewModel.toolModelReady.collectAsStateWithLifecycle()
    val downloadStates by ModelDownloadService.downloadStates.collectAsStateWithLifecycle()

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.setInputImage(it) }
    }

    val stylePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.setStyleImage(it) }
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.releaseAll() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Image Tools", style = MaterialTheme.typography.titleLarge)
                },
                navigationIcon = {
                    ActionButton(
                        onClickListener = onNavigateBack,
                        icon = TnIcons.ArrowLeft,
                        modifier = Modifier.padding(start = Standards.SpacingXs)
                    )
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // ── Tool Selector Chips ──
            ToolSelector(
                selectedTool = selectedTool,
                onToolSelected = { viewModel.selectTool(it) }
            )

            Spacer(modifier = Modifier.height(Standards.SpacingMd))

            // ── Input Image ──
            ImagePickerCard(
                label = "Input Image",
                bitmap = inputImage,
                onPick = { imagePicker.launch("image/*") },
                modifier = Modifier.padding(horizontal = Standards.SpacingLg)
            )

            // ── Style Image (only for Style Transfer) ──
            AnimatedVisibility(
                visible = selectedTool == ImageTool.STYLE_TRANSFER,
                enter = Motion.Enter,
                exit = Motion.Exit
            ) {
                Column {
                    Spacer(modifier = Modifier.height(Standards.SpacingMd))
                    ImagePickerCard(
                        label = "Style Image",
                        bitmap = styleImage,
                        onPick = { stylePicker.launch("image/*") },
                        modifier = Modifier.padding(horizontal = Standards.SpacingLg)
                    )
                    Spacer(modifier = Modifier.height(Standards.SpacingSm))
                    StyleStrengthSlider(
                        strength = styleStrength,
                        onStrengthChange = { viewModel.setStyleStrength(it) },
                        modifier = Modifier.padding(horizontal = Standards.SpacingLg)
                    )
                }
            }

            Spacer(modifier = Modifier.height(Standards.SpacingLg))

            // ── Result Image ──
            AnimatedVisibility(
                visible = resultImage != null,
                enter = Motion.Enter,
                exit = Motion.Exit
            ) {
                resultImage?.let { bitmap ->
                    ResultImageCard(
                        bitmap = bitmap,
                        modifier = Modifier.padding(horizontal = Standards.SpacingLg)
                    )
                }
            }

            // ── Processing Indicator ──
            AnimatedVisibility(
                visible = processingState is ProcessingState.Processing ||
                        processingState is ProcessingState.Loading,
                enter = fadeIn(Motion.entrance()),
                exit = fadeOut(Motion.exit())
            ) {
                ProcessingIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Standards.SpacingLg)
                )
            }

            // ── Error Message ──
            AnimatedVisibility(
                visible = processingState is ProcessingState.Error,
                enter = Motion.Enter,
                exit = Motion.Exit
            ) {
                if (processingState is ProcessingState.Error) {
                    ErrorCard(
                        message = (processingState as ProcessingState.Error).message,
                        modifier = Modifier.padding(horizontal = Standards.SpacingLg)
                    )
                }
            }

            // ── Completion Info ──
            AnimatedVisibility(
                visible = processingState is ProcessingState.Complete,
                enter = fadeIn(Motion.entrance()),
                exit = fadeOut(Motion.exit())
            ) {
                if (processingState is ProcessingState.Complete) {
                    val elapsed = (processingState as ProcessingState.Complete).timeMs
                    Text(
                        text = "Done in ${elapsed}ms",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Standards.SpacingLg, vertical = Standards.SpacingSm),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(Standards.SpacingSm))

            // ── Action Button ──
            val isModelReady = toolModelReady[selectedTool] == true
            val isProcessing = processingState is ProcessingState.Processing ||
                    processingState is ProcessingState.Loading
            val hasInput = inputImage != null

            // Check download state for current tool
            val spec = viewModel.getToolModelSpec(selectedTool)
            val hasDownloadUrl = spec != null && spec.downloadUrl.isNotEmpty()
            val isDownloading = spec != null && downloadStates.containsKey(spec.id)

            if (isModelReady) {
                ProcessButton(
                    enabled = hasInput && !isProcessing,
                    isProcessing = isProcessing,
                    onClick = { viewModel.process() },
                    modifier = Modifier.padding(horizontal = Standards.SpacingLg, vertical = Standards.SpacingSm)
                )
            } else {
                DownloadButton(
                    toolName = selectedTool.label,
                    sizeMB = spec?.sizeMB ?: 0,
                    isDownloading = isDownloading,
                    isAvailable = hasDownloadUrl,
                    downloadProgress = if (isDownloading) {
                        val state = spec.let { downloadStates[it.id] }
                        if (state is ModelDownloadService.DownloadState.Downloading) state.progress else null
                    } else null,
                    onClick = { viewModel.downloadToolModel(selectedTool) },
                    modifier = Modifier.padding(horizontal = Standards.SpacingLg, vertical = Standards.SpacingSm)
                )
            }

            Spacer(modifier = Modifier.height(Standards.SpacingXl))
        }
    }
}

// ════════════════════════════════════════════
//  TOOL SELECTOR
// ════════════════════════════════════════════

@Composable
private fun ToolSelector(
    selectedTool: ImageTool,
    onToolSelected: (ImageTool) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Standards.SpacingMd),
        horizontalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
    ) {
        ImageTool.entries.forEach { tool ->
            val isSelected = tool == selectedTool
            val containerColor by animateColorAsState(
                targetValue = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                animationSpec = Motion.content(),
                label = "chipColor"
            )

            FilterChip(
                selected = isSelected,
                onClick = { onToolSelected(tool) },
                label = {
                    Text(
                        text = tool.label,
                        style = MaterialTheme.typography.labelLarge
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = toolIcon(tool),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = containerColor,
                    containerColor = containerColor
                )
            )
        }
    }
}

@Composable
private fun toolIcon(tool: ImageTool) = when (tool) {
    ImageTool.UPSCALER -> TnIcons.ArrowUp
    ImageTool.SEGMENTER -> TnIcons.Wand
    ImageTool.LAMA_INPAINT -> TnIcons.Eraser
    ImageTool.DEPTH -> TnIcons.Eye
    ImageTool.STYLE_TRANSFER -> TnIcons.Palette
}

// ════════════════════════════════════════════
//  IMAGE CARDS
// ════════════════════════════════════════════

@Composable
private fun ImagePickerCard(
    label: String,
    bitmap: Bitmap?,
    onPick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(Standards.RadiusXl)

    Surface(
        onClick = onPick,
        modifier = modifier
            .fillMaxWidth()
            .clip(shape),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = label,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat().coerceAtLeast(1f)),
                contentScale = ContentScale.Fit
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .padding(Standards.SpacingXl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = TnIcons.Photo,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(Standards.SpacingMd))
                Text(
                    text = "Tap to pick $label",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ResultImageCard(
    bitmap: Bitmap,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(Standards.RadiusXl)

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Result",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = Standards.SpacingSm)
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape),
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Result",
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat().coerceAtLeast(1f)),
                contentScale = ContentScale.Fit
            )
        }
    }
}

// ════════════════════════════════════════════
//  CONTROLS
// ════════════════════════════════════════════

@Composable
private fun StyleStrengthSlider(
    strength: Float,
    onStrengthChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Style Strength",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "${(strength * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = strength,
            onValueChange = onStrengthChange,
            valueRange = 0f..1f
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ProcessingIndicator(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
        ) {
            LoadingIndicator()
            Text(
                "Processing...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ErrorCard(
    message: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Standards.RadiusLg),
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Row(
            modifier = Modifier.padding(Standards.SpacingMd),
            horizontalArrangement = Arrangement.spacedBy(Standards.SpacingSm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = TnIcons.AlertCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

// ════════════════════════════════════════════
//  ACTION BUTTONS
// ════════════════════════════════════════════

@Composable
private fun ProcessButton(
    enabled: Boolean,
    isProcessing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Standards.RadiusXl),
        color = if (enabled) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        enabled = enabled && !isProcessing
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Standards.SpacingLg),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = TnIcons.PlayerPlay,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (enabled) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Spacer(modifier = Modifier.width(Standards.SpacingSm))
            Text(
                text = "Process",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = if (enabled) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

@Composable
private fun DownloadButton(
    toolName: String,
    sizeMB: Int,
    isDownloading: Boolean,
    isAvailable: Boolean,
    downloadProgress: Float?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor by animateColorAsState(
        targetValue = if (isAvailable) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        animationSpec = Motion.content(),
        label = "dlBtnColor"
    )
    val contentColor = if (isAvailable) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Standards.RadiusXl),
        color = containerColor,
        enabled = isAvailable && !isDownloading
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Standards.SpacingLg),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = TnIcons.Download,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = contentColor
            )
            Spacer(modifier = Modifier.width(Standards.SpacingSm))
            Text(
                text = when {
                    isDownloading -> "Downloading... ${downloadProgress?.let { "${(it * 100).toInt()}%" } ?: ""}"
                    !isAvailable -> "$toolName Model — not yet available"
                    else -> "Download $toolName Model (~${sizeMB}MB)"
                },
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = contentColor
            )
        }
    }
}
