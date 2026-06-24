package com.dark.tool_neuron.ui.screens.model_store

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.model.enums.ProviderType
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.LocalDimens

@Composable
fun ModelImportTypePicker(
    fileName: String,
    onPick: (ProviderType) -> Unit,
    onDismiss: () -> Unit,
) = ModelTypePickerDialog(
    title = "Import as...",
    fileName = fileName,
    selectedType = null,
    onPick = onPick,
    onDismiss = onDismiss,
)

@Composable
internal fun ModelTypePickerDialog(
    title: String,
    fileName: String,
    selectedType: ProviderType?,
    onPick: (ProviderType) -> Unit,
    onDismiss: () -> Unit,
) {
    val dimens = LocalDimens.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(dimens.spacingMd))
                Text(
                    "Pick what kind of model this file is.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(dimens.spacingSm))
                PROVIDER_OPTIONS.forEach { type ->
                    TextButton(
                        onClick = { onPick(type) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (selectedType == type) {
                                Icon(
                                    TnIcons.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            } else {
                                Spacer(Modifier.size(18.dp))
                            }
                            Column(modifier = Modifier.padding(start = dimens.spacingSm)) {
                                Text(
                                    text = providerTypeLabel(type),
                                    fontWeight = if (selectedType == type) FontWeight.SemiBold else FontWeight.Normal,
                                )
                                Text(
                                    text = providerTypeBlurb(type),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

internal fun providerTypeLabel(type: ProviderType): String = when (type) {
    ProviderType.GGUF -> "Chat (GGUF)"
    ProviderType.VISION_CHAT -> "Vision chat"
    ProviderType.TOOL_SEARCH -> "Chat"
    ProviderType.EMBEDDING -> "Embedding (RAG)"
    ProviderType.IMAGE_GEN -> "Image generation"
    ProviderType.IMAGE_UPSCALER -> "Image upscaler"
    ProviderType.TTS -> "Text-to-Speech"
    ProviderType.STT -> "Speech-to-Text"
}

private fun providerTypeBlurb(type: ProviderType): String = when (type) {
    ProviderType.GGUF -> "Conversation models used by chat and the remote server."
    ProviderType.VISION_CHAT -> "Chat models that can accept image input when a projector is available."
    ProviderType.TOOL_SEARCH -> "Chat model."
    ProviderType.EMBEDDING -> "Vector models for document search and RAG."
    ProviderType.IMAGE_GEN -> "Diffusion models used by the image workspace."
    ProviderType.IMAGE_UPSCALER -> "Upscaling models shown in image upscale mode."
    ProviderType.TTS -> "Voice models that read replies aloud."
    ProviderType.STT -> "Voice models that transcribe speech input."
}

private val PROVIDER_OPTIONS = listOf(
    ProviderType.GGUF,
    ProviderType.VISION_CHAT,
    ProviderType.EMBEDDING,
    ProviderType.IMAGE_GEN,
    ProviderType.IMAGE_UPSCALER,
    ProviderType.TTS,
    ProviderType.STT,
)
