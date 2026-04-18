package com.dark.tool_neuron.ui.screens.home_screen

import android.content.Intent
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dark.tool_neuron.model.ChatDocument
import com.dark.tool_neuron.ui.components.action_window.ActionWindowOverlay
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.viewmodel.HomeViewModel
import java.util.UUID

@Composable
fun HomeScreen(
    innerPadding: PaddingValues,
    actionWindowExpanded: Boolean,
    onActionWindowDismiss: () -> Unit,
    onStoreClick: () -> Unit = {},
    onGuideClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val dimens = LocalDimens.current
    val chatDocuments by viewModel.chatDocuments.collectAsStateWithLifecycle()
    val currentChatId by viewModel.currentChatId.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val streaming by viewModel.streamingFragment.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
    ) {
        if (messages.isEmpty() && streaming == null) {
            Text(
                text = "No model loaded.\nDownload one to get started.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center),
            )
        } else {
            ChatMessageList(
                messages = messages,
                streaming = streaming,
                isGenerating = isGenerating,
                onRegenerate = viewModel::regenerateLast,
                onDelete = viewModel::deleteMessage,
                contentPadding = PaddingValues(
                    horizontal = dimens.spacingLg,
                    vertical = dimens.spacingMd,
                ),
            )
        }

        ActionWindowOverlay(
            expanded = actionWindowExpanded,
            onDismiss = onActionWindowDismiss,
            onStoreClick = onStoreClick,
            onGuideClick = onGuideClick,
            onSettingsClick = onSettingsClick,
            onLoadDocument = {
                filePicker.launch(arrayOf("text/*", "application/pdf", "application/json"))
            },
            documents = chatDocuments,
            onRemoveDocument = { viewModel.removeDocument(it) },
        )
    }
}
