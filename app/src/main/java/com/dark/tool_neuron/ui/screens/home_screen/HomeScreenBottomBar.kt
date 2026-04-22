package com.dark.tool_neuron.ui.screens.home_screen

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
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

    val plusMenuExpanded by viewModel.plusMenuExpanded.collectAsStateWithLifecycle()
    val thinkingEnabled by viewModel.thinkingEnabled.collectAsStateWithLifecycle()
    val supportsThinking by viewModel.supportsThinking.collectAsStateWithLifecycle()
    val isModelLoaded by InferenceClient.isModelLoaded.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val chatDocuments by viewModel.chatDocuments.collectAsStateWithLifecycle()
    val currentChatId by viewModel.currentChatId.collectAsStateWithLifecycle()
    val loadModelWindow by viewModel.loadModelWindows.collectAsStateWithLifecycle()
    val installedModels by viewModel.installedModels.collectAsStateWithLifecycle()
    val modelLoadState by viewModel.modelLoadState.collectAsStateWithLifecycle()
    val contextUsage by viewModel.contextUsage.collectAsStateWithLifecycle()

    val canSend by remember {
        derivedStateOf { text.isNotBlank() && isModelLoaded && !isGenerating }
    }

    val filePicker = rememberDocumentPicker(currentChatId, viewModel::addDocument)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = dimens.spacingMd)
            .padding(bottom = dimens.spacingXs),
    ) {
        AnimatedVisibility(
            visible = plusMenuExpanded,
            enter = fadeIn(Motion.state()) + expandVertically(Motion.content()),
            exit = fadeOut(Motion.state()) + shrinkVertically(Motion.content()),
        ) {
            PlusMenuCard(
                thinkingEnabled = thinkingEnabled,
                showThinking = supportsThinking,
                documentCount = chatDocuments.size,
                onThinkingToggle = viewModel::toggleThinking,
                onDocumentsClick = {
                    viewModel.dismissPlusMenu()
                    filePicker.launch(arrayOf("text/*", "application/pdf", "application/json"))
                },
            )
        }

        DocumentChipsRow(
            documents = chatDocuments,
            onRemove = viewModel::removeDocument,
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

        InputBar(
            text = text,
            onTextChange = { text = it },
            canSend = canSend,
            isGenerating = isGenerating,
            isModelLoaded = isModelLoaded,
            contextUsage = contextUsage,
            plusMenuExpanded = plusMenuExpanded,
            documentCount = chatDocuments.size,
            onPlusClick = viewModel::togglePlusMenu,
            onLoadModelClick = viewModel::toggleLoadModelWindow,
            onSend = {
                focusManager.clearFocus()
                val toSend = text
                text = ""
                viewModel.sendMessage(toSend)
            },
            onStop = viewModel::stopGeneration,
        )
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
    plusMenuExpanded: Boolean,
    documentCount: Int,
    onPlusClick: () -> Unit,
    onLoadModelClick: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
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
                PlusButton(
                    expanded = plusMenuExpanded,
                    badgeCount = documentCount,
                    onClick = onPlusClick,
                )
                ActionButton(
                    onClickListener = onLoadModelClick,
                    icon = TnIcons.Leaf,
                    contentDescription = "Load Model",
                )
                Spacer(Modifier.weight(1f))
                ContextIndicator(
                    text = contextText,
                    progress = contextProgress,
                    isActive = isModelLoaded,
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
private fun PlusButton(
    expanded: Boolean,
    badgeCount: Int,
    onClick: () -> Unit,
) {
    val tnShapes = LocalTnShapes.current
    Box {
        ActionButton(
            onClickListener = onClick,
            icon = TnIcons.Plus,
            contentDescription = "More options",
            colors = toggleIconColors(expanded),
        )
        if (badgeCount > 0) {
            Surface(
                shape = tnShapes.full,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(16.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "$badgeCount",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.85f,
                    )
                }
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
private fun rememberDocumentPicker(
    currentChatId: String?,
    onPicked: (ChatDocument) -> Unit,
): ManagedActivityResultLauncher<Array<String>, Uri?> {
    val context = LocalContext.current
    return rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        context.contentResolver.takePersistableUriPermission(
            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
        var name = "Document"
        var size = 0L
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = c.getColumnIndex(OpenableColumns.SIZE)
                if (nameIdx >= 0) name = c.getString(nameIdx) ?: name
                if (sizeIdx >= 0) size = c.getLong(sizeIdx)
            }
        }
        val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
        onPicked(
            ChatDocument(
                id = UUID.randomUUID().toString(),
                chatId = currentChatId,
                name = name,
                mimeType = mime,
                chunkCount = 0,
                sizeBytes = size,
            )
        )
    }
}

@Composable
private fun toggleIconColors(active: Boolean): IconButtonColors =
    IconButtonDefaults.filledIconButtonColors(
        containerColor = if (active)
            MaterialTheme.colorScheme.primary
        else
            MaterialTheme.colorScheme.primary.copy(0.08f),
        contentColor = if (active)
            MaterialTheme.colorScheme.onPrimary
        else
            MaterialTheme.colorScheme.primary,
    )

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

