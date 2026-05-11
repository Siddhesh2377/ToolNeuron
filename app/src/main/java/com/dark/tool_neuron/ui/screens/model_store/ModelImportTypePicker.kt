package com.dark.tool_neuron.ui.screens.model_store

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.dark.tool_neuron.model.enums.ProviderType
import com.dark.tool_neuron.ui.theme.LocalDimens

@Composable
fun ModelImportTypePicker(
    fileName: String,
    onPick: (ProviderType) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import as…") },
        text = {
            Column {
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(LocalDimens.current.spacingMd))
                Text(
                    "Pick what kind of model this file is.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(ProviderType.GGUF) }) { Text("Chat (GGUF)") }
        },
        dismissButton = {
            TextButton(onClick = { onPick(ProviderType.EMBEDDING) }) { Text("Embedding (RAG)") }
        },
    )
}
