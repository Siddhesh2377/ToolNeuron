package com.dark.tool_neuron.ui.screens.home_screen

import android.Manifest
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.dark.tool_neuron.model.ChatDocument
import com.dark.tool_neuron.model.NavScreens
import com.dark.tool_neuron.ui.components.ContextStatsDialog
import com.dark.tool_neuron.viewmodel.ImageEncodeStatus
import com.dark.tool_neuron.service.inference.InferenceClient
import com.dark.tool_neuron.ui.components.ActionButton
import com.dark.tool_neuron.ui.components.ActionProgressButton
import com.dark.tool_neuron.ui.components.TnTextField
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.LocalTnShapes
import com.dark.tool_neuron.ui.theme.Motion
import com.dark.tool_neuron.viewmodel.HomeViewModel
import java.util.UUID

@Composable
fun HomeScreenBottomBar(
    navController: NavHostController,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val dimens = LocalDimens.current
    val focusManager = LocalFocusManager.current
    var text by remember { mutableStateOf("") }

    val thinkingEnabled by viewModel.thinkingEnabled.collectAsStateWithLifecycle()
    val researchEnabled by viewModel.researchEnabled.collectAsStateWithLifecycle()
    val supportsThinking by viewModel.supportsThinking.collectAsStateWithLifecycle()
    val isModelLoaded by InferenceClient.isModelLoaded.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val chatDocuments by viewModel.chatDocuments.collectAsStateWithLifecycle()
    val currentChatId by viewModel.currentChatId.collectAsStateWithLifecycle()
    val loadModelWindow by viewModel.loadModelWindows.collectAsStateWithLifecycle()
    val installedModels by viewModel.chatModels.collectAsStateWithLifecycle()
    val modelLoadState by viewModel.modelLoadState.collectAsStateWithLifecycle()
    val contextUsage by viewModel.contextUsage.collectAsStateWithLifecycle()

    val isIngesting by viewModel.isIngestingDocument.collectAsStateWithLifecycle()
    val documentError by viewModel.documentError.collectAsStateWithLifecycle()
    val ragReady by viewModel.ragReady.collectAsStateWithLifecycle()
    val activeEmbeddingName by viewModel.activeEmbeddingName.collectAsStateWithLifecycle()

    val pendingImages by viewModel.pendingImages.collectAsStateWithLifecycle()
    val imageEncodeStatus by viewModel.imageEncodeStatus.collectAsStateWithLifecycle()
    val isVlmLoaded by viewModel.isVlmLoaded.collectAsStateWithLifecycle()

    val imagesEncoding by remember {
        derivedStateOf {
            pendingImages.any { uri ->
                val s = imageEncodeStatus[uri] ?: ImageEncodeStatus.Pending
                s == ImageEncodeStatus.Pending || s == ImageEncodeStatus.Encoding
            }
        }
    }

    val canSend by remember {
        derivedStateOf {
            text.isNotBlank() && !isGenerating && !imagesEncoding && (isModelLoaded || installedModels.isNotEmpty())
        }
    }
    val vlmError by viewModel.vlmError.collectAsStateWithLifecycle()

    val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()
    val isTranscribing by viewModel.isTranscribing.collectAsStateWithLifecycle()
    val transcribedText by viewModel.transcribedText.collectAsStateWithLifecycle()
    val voiceError by viewModel.voiceError.collectAsStateWithLifecycle()
    val recordingAmplitude by viewModel.recordingAmplitude.collectAsStateWithLifecycle()
    val voiceSttAvailable = viewModel.voiceSttAvailable()

    LaunchedEffect(transcribedText) {
        val t = transcribedText ?: return@LaunchedEffect
        text = if (text.isBlank()) t else "$text $t"
        viewModel.consumeTranscribedText()
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.startRecording()
    }

    val onMicClick: () -> Unit = {
        if (!voiceSttAvailable) {
            navController.navigate(NavScreens.ModelStore.route)
        } else if (viewModel.voiceMicGranted()) {
            viewModel.startRecording()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris -> uris.forEach(viewModel::addImage) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = dimens.spacingMd)
            .padding(bottom = dimens.spacingXs),
    ) {
        VlmErrorBanner(
            error = vlmError,
            onDismiss = viewModel::clearVlmError,
        )

        VlmErrorBanner(
            error = voiceError,
            onDismiss = viewModel::clearVoiceError,
        )

        PendingImageRow(
            images = pendingImages,
            statusMap = imageEncodeStatus,
            onRemove = viewModel::removeImage,
        )

        DocumentChipsRow(
            documents = chatDocuments,
            onRemove = viewModel::removeDocument,
        )

        RagStatusBanner(
            isIngesting = isIngesting,
            error = documentError,
            ragReady = ragReady,
            embeddingName = activeEmbeddingName,
            docCount = chatDocuments.size,
            onDismissError = viewModel::clearDocumentError,
        )

        AnimatedVisibility(
            visible = loadModelWindow,
            enter = fadeIn(Motion.state()) + expandVertically(Motion.content()),
            exit = fadeOut(Motion.state()) + shrinkVertically(Motion.content()),
        ) {
            LoadModelWindow(
                models = installedModels,
                loadState = modelLoadState,
                onLoad = viewModel::loadModel,
                onUnload = { viewModel.unloadModel() },
                onBrowseStore = {
                    viewModel.toggleLoadModelWindow()
                    navController.navigate(NavScreens.ModelStore.route)
                },
            )
        }

        AnimatedContent(
            targetState = isRecording || isTranscribing,
            transitionSpec = {
                (fadeIn(Motion.state()) togetherWith fadeOut(Motion.state()))
            },
            label = "bottom_bar_state",
        ) { showEqualizer ->
            if (showEqualizer) {
                RecordingEqualizer(
                    amplitude = recordingAmplitude,
                    isTranscribing = isTranscribing,
                    onCancel = viewModel::cancelRecording,
                    onSubmit = viewModel::stopRecordingAndTranscribe,
                )
            } else {
                InputBar(
                    text = text,
                    onTextChange = { text = it },
                    canSend = canSend,
                    isGenerating = isGenerating,
                    isModelLoaded = isModelLoaded,
                    contextUsage = contextUsage,
                    supportsThinking = supportsThinking,
                    thinkingEnabled = thinkingEnabled,
                    researchEnabled = researchEnabled,
                    onResearchToggle = viewModel::toggleResearch,
                    onAttachImage = {
                        if (isVlmLoaded) {
                            imagePicker.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly,
                                ),
                            )
                        }
                    },
                    canAttachImage = isVlmLoaded,
                    onMicClick = onMicClick,
                    onThinkingToggle = viewModel::toggleThinking,
                    onLoadModelClick = viewModel::toggleLoadModelWindow,
                    onSend = {
                        if (canSend) {
                            focusManager.clearFocus()
                            val toSend = text
                            text = ""
                            viewModel.sendMessage(toSend)
                        } else if (!isGenerating && !isModelLoaded && text.isNotBlank() && !loadModelWindow) {
                            viewModel.toggleLoadModelWindow()
                        }
                    },
                    onStop = viewModel::stopGeneration,
                    onContextClick = viewModel::openContextStats,
                )
            }
        }
    }

    val contextStatsReport by viewModel.contextStatsReport.collectAsStateWithLifecycle()
    contextStatsReport?.let { report ->
        ContextStatsDialog(report = report, onDismiss = viewModel::dismissContextStats)
    }
}

@Composable
private fun InputBar(
    text: String,
    onTextChange: (String) -> Unit,
    canSend: Boolean,
    isGenerating: Boolean,
    isModelLoaded: Boolean,
    contextUsage: Float,
    supportsThinking: Boolean,
    thinkingEnabled: Boolean,
    researchEnabled: Boolean,
    onResearchToggle: () -> Unit,
    onAttachImage: () -> Unit,
    canAttachImage: Boolean,
    onMicClick: () -> Unit,
    onThinkingToggle: () -> Unit,
    onLoadModelClick: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onContextClick: () -> Unit,
) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current
    val contextText = if (isModelLoaded) "${(contextUsage * 100).toInt()}%" else "--"
    val contextProgress = if (isModelLoaded) contextUsage else 0f

    Surface(
        shape = tnShapes.xl,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.09f)
            .compositeOver(MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            TnTextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = "Send a message...",
                modifier = Modifier.fillMaxWidth(),
                maxLines = 5,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = dimens.spacingSm,
                        end = dimens.spacingSm,
                        bottom = dimens.spacingSm,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.spacingXs),
            ) {
                ActionButton(
                    onClickListener = onAttachImage,
                    icon = TnIcons.Photo,
                    contentDescription = if (canAttachImage) "Attach image" else "Attach image (load a VLM model first)",
                    enabled = canAttachImage,
                )
                ActionButton(
                    onClickListener = onLoadModelClick,
                    icon = TnIcons.Leaf,
                    contentDescription = "Load Model",
                    enabled = !isGenerating,
                )
                ThinkingToggleButton(
                    supported = supportsThinking,
                    enabled = thinkingEnabled,
                    onClick = onThinkingToggle,
                )
                ResearchToggleButton(
                    enabled = researchEnabled,
                    onClick = onResearchToggle,
                )
                Spacer(Modifier.weight(1f))
                ContextIndicator(
                    text = contextText,
                    progress = contextProgress,
                    isActive = isModelLoaded,
                    onClick = { if (isModelLoaded) onContextClick() },
                )
                Spacer(Modifier.width(dimens.spacingXs))
                ActionButton(
                    onClickListener = onMicClick,
                    icon = TnIcons.Mic,
                    contentDescription = "Voice input",
                )
                Spacer(Modifier.width(dimens.spacingXs))
                SendOrStopButton(
                    canSend = canSend,
                    isGenerating = isGenerating,
                    onSend = onSend,
                    onStop = onStop,
                )
            }
        }
    }
}

@Composable
private fun PendingImageRow(
    images: List<Uri>,
    statusMap: Map<Uri, ImageEncodeStatus>,
    onRemove: (Uri) -> Unit,
) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current
    val context = LocalContext.current
    AnimatedVisibility(
        visible = images.isNotEmpty(),
        enter = fadeIn(Motion.state()) + expandVertically(Motion.content()),
        exit = fadeOut(Motion.state()) + shrinkVertically(Motion.content()),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(bottom = dimens.spacingXs),
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingXs),
        ) {
            images.forEach { uri ->
                val bitmap = remember(uri) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use {
                            BitmapFactory.decodeStream(it)
                        }
                    }.getOrNull()
                }
                val status = statusMap[uri] ?: ImageEncodeStatus.Pending
                Box(
                    modifier = Modifier.size(56.dp),
                ) {
                    Surface(
                        shape = tnShapes.md,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        modifier = Modifier.size(56.dp),
                    ) {
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Attached image",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            Icon(
                                imageVector = TnIcons.Photo,
                                contentDescription = null,
                                modifier = Modifier.padding(dimens.spacingSm),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    EncodeStatusOverlay(status = status)
                    Surface(
                        shape = tnShapes.full,
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(18.dp)
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                                onClick = { onRemove(uri) },
                            ),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = TnIcons.X,
                                contentDescription = "Remove image",
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxScope.EncodeStatusOverlay(status: ImageEncodeStatus) {
    when (status) {
        ImageEncodeStatus.Encoding -> {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        MaterialTheme.colorScheme.scrim.copy(alpha = 0.35f),
                        LocalTnShapes.current.md,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
        ImageEncodeStatus.Cached -> {
            Surface(
                shape = LocalTnShapes.current.full,
                color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .size(16.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = TnIcons.Check,
                        contentDescription = "Vision tokens cached",
                        modifier = Modifier.size(11.dp),
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
        }
        ImageEncodeStatus.Error -> {
            Surface(
                shape = LocalTnShapes.current.full,
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .size(16.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = TnIcons.AlertTriangle,
                        contentDescription = "Vision encode failed",
                        modifier = Modifier.size(11.dp),
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
        ImageEncodeStatus.Pending -> Unit
    }
}

@Composable
private fun VlmErrorBanner(
    error: String?,
    onDismiss: () -> Unit,
) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current
    AnimatedVisibility(
        visible = error != null,
        enter = fadeIn(Motion.state()) + expandVertically(Motion.content()),
        exit = fadeOut(Motion.state()) + shrinkVertically(Motion.content()),
    ) {
        Surface(
            shape = tnShapes.lg,
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = dimens.spacingXs),
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = dimens.spacingMd,
                    vertical = dimens.spacingSm,
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
            ) {
                Text(
                    text = error.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = TnIcons.X,
                    contentDescription = "Dismiss",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .size(dimens.iconSm)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = onDismiss,
                        ),
                )
            }
        }
    }
}

@Composable
private fun DocumentChipsRow(
    documents: List<ChatDocument>,
    onRemove: (String) -> Unit,
) {
    val dimens = LocalDimens.current
    AnimatedVisibility(
        visible = documents.isNotEmpty(),
        enter = fadeIn(Motion.state()) + expandVertically(Motion.content()),
        exit = fadeOut(Motion.state()) + shrinkVertically(Motion.content()),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(bottom = dimens.spacingXs),
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingXs),
        ) {
            documents.forEach { doc ->
                DocumentChip(
                    name = doc.name,
                    onRemove = { onRemove(doc.id) },
                )
            }
        }
    }
}

@Composable
private fun ResearchToggleButton(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = if (enabled) IconButtonDefaults.filledIconButtonColors(
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) else IconButtonDefaults.filledIconButtonColors(
        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        contentColor = MaterialTheme.colorScheme.primary,
    )
    ActionButton(
        onClickListener = onClick,
        icon = TnIcons.Compass,
        contentDescription = if (enabled) "Research mode on" else "Research mode off",
        colors = colors,
    )
}

@Composable
private fun ThinkingToggleButton(
    supported: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = when {
        !supported -> IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
            contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
        )
        enabled -> IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        )
        else -> IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
            contentColor = MaterialTheme.colorScheme.primary,
        )
    }
    ActionButton(
        onClickListener = { if (supported) onClick() },
        icon = TnIcons.Sparkles,
        contentDescription = if (supported) "Toggle thinking" else "Thinking not supported by this model",
        colors = colors,
    )
}

@Composable
private fun SendOrStopButton(
    canSend: Boolean,
    isGenerating: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    val tnShapes = LocalTnShapes.current
    if (isGenerating) {
        ActionProgressButton(
            onClickListener = onStop,
            icon = TnIcons.PlayerStop,
            contentDescription = "Stop generation",
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        )
    } else {
        ActionButton(
            onClickListener = onSend,
            icon = TnIcons.Send,
            contentDescription = "Send",
            enabled = canSend,
            shape = tnShapes.full,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (canSend)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.primary.copy(0.15f),
                contentColor = if (canSend)
                    MaterialTheme.colorScheme.onPrimary
                else
                    MaterialTheme.colorScheme.primary.copy(0.4f),
            ),
        )
    }
}

@Composable
private fun RagStatusBanner(
    isIngesting: Boolean,
    error: String?,
    ragReady: Boolean,
    embeddingName: String?,
    docCount: Int,
    onDismissError: () -> Unit,
) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current
    val visible = isIngesting || error != null || (ragReady && docCount > 0 && embeddingName != null)

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(Motion.state()) + expandVertically(Motion.content()),
        exit = fadeOut(Motion.state()) + shrinkVertically(Motion.content()),
    ) {
        val bg = when {
            error != null -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
            isIngesting   -> MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
            else          -> MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
        }
        val fg = when {
            error != null -> MaterialTheme.colorScheme.error
            else          -> MaterialTheme.colorScheme.primary
        }
        Surface(
            shape = tnShapes.lg,
            color = bg,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = dimens.spacingXs),
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = dimens.spacingMd,
                    vertical = dimens.spacingSm,
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
            ) {
                if (isIngesting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(dimens.iconSm),
                        strokeWidth = 2.dp,
                        color = fg,
                    )
                }
                val msg = when {
                    error != null -> error
                    isIngesting   -> "Parsing and indexing document..."
                    else          -> "RAG active via $embeddingName · $docCount doc${if (docCount == 1) "" else "s"}"
                }
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = fg,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (error != null) {
                    Icon(
                        imageVector = TnIcons.X,
                        contentDescription = "Dismiss",
                        tint = fg,
                        modifier = Modifier
                            .size(dimens.iconSm)
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                                onClick = onDismissError,
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun DocumentChip(
    name: String,
    onRemove: () -> Unit,
) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current

    Surface(
        shape = tnShapes.full,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
    ) {
        Row(
            modifier = Modifier.padding(
                start = dimens.spacingSm,
                end = dimens.spacingXs,
                top = dimens.spacingXxs,
                bottom = dimens.spacingXxs,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingXxs),
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(100.dp),
            )
            Icon(
                imageVector = TnIcons.X,
                contentDescription = "Remove",
                modifier = Modifier
                    .size(12.dp)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onRemove,
                    ),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun ContextIndicator(
    text: String,
    progress: Float,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current

    val contentColor = if (isActive)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)

    Surface(
        shape = tnShapes.full,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        modifier = Modifier.clickable(enabled = isActive, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = dimens.spacingMd,
                vertical = dimens.spacingSm,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingXs),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(dimens.iconSm)) {
                CircularProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.size(dimens.iconSm),
                    color = contentColor,
                    trackColor = contentColor.copy(alpha = 0.15f),
                    strokeWidth = 2.dp,
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
            )
        }
    }
}

