package com.dark.tool_neuron.ui.screen.home

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.models.table_schema.Model
import com.dark.tool_neuron.ui.components.ActionButton
import com.dark.tool_neuron.ui.components.ActionTextButton
import com.dark.tool_neuron.ui.components.ModelListItem
import com.dark.tool_neuron.viewmodel.ChatViewModel
import com.dark.tool_neuron.viewmodel.LLMModelViewModel
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.global.Standards

// ── Models Tab ──

@Composable
internal fun ModelsTabContent(
    installedModels: List<Model>,
    currentModelID: String,
    modelViewModel: LLMModelViewModel,
    chatViewModel: ChatViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 240.dp)
    ) {
        if (installedModels.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Standards.SpacingXs)
                ) {
                    Icon(
                        imageVector = TnIcons.Photo,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Text(
                        "No models installed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(Standards.SpacingSm),
                verticalArrangement = Arrangement.spacedBy(Standards.SpacingXs)
            ) {
                items(installedModels, key = { it.id }) { model ->
                    ModelListItem(
                        modifier = Modifier.fillMaxWidth(),
                        model = model,
                        isLoaded = currentModelID == model.id,
                        onClickListener = { selectedModel ->
                            if (currentModelID == model.id) {
                                modelViewModel.unloadModel()
                            } else {
                                modelViewModel.loadModel(selectedModel)
                            }
                            chatViewModel.hideDynamicWindow()
                        },
                        onDeleteListener = { modelToDelete ->
                            modelViewModel.deleteModel(modelToDelete)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun DawModelListItem(
    modifier: Modifier,
    model: Model,
    isLoaded: Boolean,
    onClickListener: (Model) -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(Standards.RadiusMd),
        color = if (isLoaded) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        },
        tonalElevation = if (isLoaded) 1.dp else 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = Standards.SpacingSm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(Standards.SpacingSm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(Standards.RadiusSm),
                    color = if (isLoaded) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    Icon(
                        imageVector = if (model.providerType == com.dark.tool_neuron.models.enums.ProviderType.GGUF) TnIcons.Sparkles
                            else TnIcons.Photo,
                        contentDescription = null,
                        modifier = Modifier
                            .size(28.dp)
                            .padding(6.dp),
                        tint = if (isLoaded) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = model.modelName,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (isLoaded) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (isLoaded) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Standards.SpacingXs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isLoaded) {
                            Box(
                                modifier = Modifier
                                    .size(4.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                            )
                        }
                        Text(
                            text = model.providerType.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                alpha = if (isLoaded) 0.8f else 0.6f
                            )
                        )
                    }
                }
            }

            Crossfade(targetState = isLoaded, label = "button_state") { loaded ->
                if (loaded) {
                    ActionTextButton(
                        onClickListener = { onClickListener(model) },
                        icon = TnIcons.CornerDownLeft,
                        text = "Unload",
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error.copy(0.1f),
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        shape = RoundedCornerShape(Standards.RadiusSm)
                    )
                } else {
                    ActionButton(
                        onClickListener = { onClickListener(model) },
                        icon = TnIcons.ExternalLink,
                        contentDescription = "Load",
                        shape = RoundedCornerShape(Standards.RadiusSm),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(0.1f),
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
        }
    }
}
