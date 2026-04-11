package com.dark.tool_neuron.ui.screens.home_screen

import android.content.Intent
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.dark.tool_neuron.model.ChatDocument
import com.dark.tool_neuron.service.inference.InferenceClient
import com.dark.tool_neuron.ui.components.ActionButton
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
    val tnShapes = LocalTnShapes.current
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }

    val plusMenuExpanded by viewModel.plusMenuExpanded.collectAsStateWithLifecycle()
    val webSearchEnabled by viewModel.webSearchEnabled.collectAsStateWithLifecycle()
    val thinkingEnabled by viewModel.thinkingEnabled.collectAsStateWithLifecycle()
    val isModelLoaded by InferenceClient.isModelLoaded.collectAsStateWithLifecycle()
    val chatDocuments by viewModel.chatDocuments.collectAsStateWithLifecycle()
    val currentChatId by viewModel.currentChatId.collectAsStateWithLifecycle()

    val contextUsage = if (isModelLoaded) InferenceClient.getContextUsage() else -1f
    val contextText = if (contextUsage >= 0f) "${(contextUsage * 100).toInt()}%" else "--"

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            var fileName = "Document"
            var fileSize = 0L
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIdx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = it.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) fileName = it.getString(nameIdx) ?: "Document"
                    if (sizeIdx >= 0) fileSize = it.getLong(sizeIdx)
                }
            }
            val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
            viewModel.addDocument(
                ChatDocument(
                    id = UUID.randomUUID().toString(),
                    chatId = currentChatId,
                    name = fileName,
                    mimeType = mimeType,
                    chunkCount = 0,
                    sizeBytes = fileSize,
                )
            )
        }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        if (plusMenuExpanded) {
            Popup(
                alignment = Alignment.BottomStart,
                onDismissRequest = { viewModel.dismissPlusMenu() },
                properties = PopupProperties(focusable = true)
            ) {
                PlusMenuCard(
                    webSearchEnabled = webSearchEnabled,
                    thinkingEnabled = thinkingEnabled,
                    documentCount = chatDocuments.size,
                    onWebSearchToggle = { viewModel.toggleWebSearch() },
                    onThinkingToggle = { viewModel.toggleThinking() },
                    onDocumentsClick = {
                        viewModel.dismissPlusMenu()
                        filePicker.launch(arrayOf("text/*", "application/pdf", "application/json"))
                    },
                    onImageClick = {},
                    onDismiss = { viewModel.dismissPlusMenu() }
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.03f)
                        .compositeOver(MaterialTheme.colorScheme.surface)
                )
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = dimens.spacingMd)
                .padding(top = dimens.spacingXs, bottom = dimens.spacingSm)
        ) {
            AnimatedVisibility(
                visible = chatDocuments.isNotEmpty(),
                enter = fadeIn(Motion.state()) + expandVertically(animationSpec = Motion.content()),
                exit = fadeOut(Motion.state()) + shrinkVertically(animationSpec = Motion.content())
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = dimens.spacingXs),
                    horizontalArrangement = Arrangement.spacedBy(dimens.spacingXs)
                ) {
                    chatDocuments.forEach { doc ->
                        DocumentChip(
                            name = doc.name,
                            onRemove = { viewModel.removeDocument(doc.id) }
                        )
                    }
                }
            }

            Surface(
                shape = tnShapes.xl,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    TnTextField(
                        value = text,
                        onValueChange = { text = it },
                        placeholder = "Send a message...",
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 5
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = dimens.spacingSm,
                                end = dimens.spacingSm,
                                bottom = dimens.spacingSm
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(dimens.spacingXs)
                    ) {
                        Box {
                            ActionButton(
                                onClickListener = { viewModel.togglePlusMenu() },
                                icon = TnIcons.Plus,
                                contentDescription = "More options",
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = if (plusMenuExpanded)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.primary.copy(0.08f),
                                    contentColor = if (plusMenuExpanded)
                                        MaterialTheme.colorScheme.onPrimary
                                    else
                                        MaterialTheme.colorScheme.primary
                                )
                            )
                            if (chatDocuments.isNotEmpty()) {
                                Surface(
                                    shape = tnShapes.full,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(16.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "${chatDocuments.size}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.85f
                                        )
                                    }
                                }
                            }
                        }

                        ActionButton(
                            onClickListener = {},
                            icon = TnIcons.Compass,
                            contentDescription = "Attach"
                        )

                        Spacer(Modifier.weight(1f))

                        ContextIndicator(
                            text = contextText,
                            progress = if (contextUsage >= 0f) contextUsage else 0f,
                            isActive = isModelLoaded
                        )

                        Spacer(Modifier.width(dimens.spacingXs))

                        ActionButton(
                            onClickListener = {},
                            icon = TnIcons.Send,
                            contentDescription = "Send",
                            shape = tnShapes.full,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = if (text.isNotBlank())
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.primary.copy(0.15f),
                                contentColor = if (text.isNotBlank())
                                    MaterialTheme.colorScheme.onPrimary
                                else
                                    MaterialTheme.colorScheme.primary.copy(0.4f)
                            )
                        )
                    }
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
            modifier = Modifier.padding(start = dimens.spacingSm, end = dimens.spacingXs, top = dimens.spacingXxs, bottom = dimens.spacingXxs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingXxs)
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(100.dp)
            )
            Icon(
                imageVector = TnIcons.X,
                contentDescription = "Remove",
                modifier = Modifier
                    .size(12.dp)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onRemove
                    ),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
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
    val color = if (isActive)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.spacingXxs)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(18.dp)) {
            CircularProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.size(16.dp),
                color = color,
                trackColor = color.copy(alpha = 0.15f),
                strokeWidth = 2.dp
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = color
        )
    }
}

@Composable
private fun PlusMenuCard(
    webSearchEnabled: Boolean,
    thinkingEnabled: Boolean,
    documentCount: Int,
    onWebSearchToggle: () -> Unit,
    onThinkingToggle: () -> Unit,
    onDocumentsClick: () -> Unit,
    onImageClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current

    Surface(
        shape = tnShapes.xl,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
        modifier = Modifier
            .padding(horizontal = dimens.screenPadding)
            .padding(bottom = dimens.spacingSm)
    ) {
        Column(
            modifier = Modifier.padding(dimens.spacingMd),
            verticalArrangement = Arrangement.spacedBy(dimens.spacingXs)
        ) {
            PlusMenuItem(
                icon = TnIcons.Search,
                label = "Web Search",
                isToggled = webSearchEnabled,
                onClick = onWebSearchToggle
            )
            PlusMenuItem(
                icon = TnIcons.Sparkles,
                label = "Thinking",
                isToggled = thinkingEnabled,
                onClick = onThinkingToggle
            )
            PlusMenuItem(
                icon = TnIcons.BookOpen,
                label = if (documentCount > 0) "Documents ($documentCount)" else "Documents",
                isToggled = documentCount > 0,
                onClick = onDocumentsClick
            )
            PlusMenuItem(
                icon = TnIcons.Photo,
                label = "Image",
                isToggled = false,
                onClick = onImageClick
            )
        }
    }
}

@Composable
private fun PlusMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isToggled: Boolean,
    onClick: () -> Unit,
) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current

    val bgColor by animateColorAsState(
        targetValue = if (isToggled)
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        else
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        animationSpec = Motion.state(),
        label = "plusItemBg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isToggled)
            MaterialTheme.colorScheme.primary
        else
            MaterialTheme.colorScheme.onSurface,
        animationSpec = Motion.state(),
        label = "plusItemContent"
    )

    Surface(
        shape = tnShapes.md,
        color = bgColor,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = dimens.spacingMd,
                vertical = dimens.spacingSm
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(dimens.iconMd),
                tint = contentColor
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = contentColor,
                modifier = Modifier.weight(1f)
            )
            if (isToggled) {
                Icon(
                    imageVector = TnIcons.Check,
                    contentDescription = null,
                    modifier = Modifier.size(dimens.iconSm),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
