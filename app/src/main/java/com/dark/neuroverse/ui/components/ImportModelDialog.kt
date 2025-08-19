package com.dark.neuroverse.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dark.ai_module.data.ModelsList
import com.dark.ai_module.model.ModelsData
import java.io.File

@Composable
fun ModelDialog(
    modelInfo: File, onDismiss: () -> Unit, onSave: (ModelsData) -> Unit
) {
    var modelsData by remember {
        mutableStateOf(
            ModelsList.CUSTOM_MODEL
        )
    }

    val isSystemTemplateSelected = modelsData.chatTemplate.isNotEmpty()

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            modifier = Modifier.padding(horizontal = 50.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    "Model Setup", style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif
                    ), color = MaterialTheme.colorScheme.primary
                )

                Spacer(Modifier.height(16.dp))

                // Model path preview
                Text("Selected Path: ${modelsData.modelPath}", maxLines = 1)

                Spacer(Modifier.height(20.dp))

                // Settings (checkboxes + template)
                ModelSettingsCard(modelsData) { updated ->
                    modelsData = updated
                }

                Spacer(Modifier.height(20.dp))

                // Actions
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            modelsData = modelsData.copy(
                                modelPath = modelInfo.absolutePath,
                                modeName = modelInfo.name,
                                modelSize = ((modelInfo.length() / 1024 / 1024).toInt())
                            )
                            onSave(modelsData)
                        }, enabled = isSystemTemplateSelected
                    ) {
                        Text("Import The Model")
                    }
                }
            }
        }
    }
}


@Composable
fun ModelSettingsCard(
    modelsData: ModelsData, onUpdate: (ModelsData) -> Unit
) {
    var toolCalling by remember { mutableStateOf(true) }
    var systemChatTemplate by remember { mutableStateOf(true) }
    var customTemplate by remember { mutableStateOf("") }

    OutlinedCard(
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary)
    ) {
        Column(
            Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .heightIn(min = 200.dp) // allow flexible height
                .fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Switch(
                    checked = toolCalling, colors = SwitchDefaults.colors(
                        uncheckedThumbColor = MaterialTheme.colorScheme.primary,
                        uncheckedTrackColor = MaterialTheme.colorScheme.onPrimary
                    ), onCheckedChange = {
                        toolCalling = it
                        onUpdate(modelsData.copy(toolUse = it.toString()))
                    })
                Text(
                    "Set Tool Calling", style = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold
                    )
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Switch(
                    checked = systemChatTemplate, colors = SwitchDefaults.colors(
                        uncheckedThumbColor = MaterialTheme.colorScheme.primary,
                        uncheckedTrackColor = MaterialTheme.colorScheme.onPrimary
                    ), onCheckedChange = {
                        systemChatTemplate = it
                        customTemplate = if (!it) ""
                        else ModelsList.chatTemplate
                        onUpdate(modelsData.copy(chatTemplate = if (it) customTemplate else ""))
                    })
                Text(
                    "Use System Chat Template", style = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold
                    )
                )
            }

            if (!systemChatTemplate) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = customTemplate, onValueChange = {
                    customTemplate = it
                    onUpdate(modelsData.copy(chatTemplate = it))
                }, label = { Text("Custom Chat Template") }, modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}


@Composable
fun ReadOnlyModelPathField(modelPath: String) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
    ) {
        DisableSelection {
            Text(
                text = modelPath,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}