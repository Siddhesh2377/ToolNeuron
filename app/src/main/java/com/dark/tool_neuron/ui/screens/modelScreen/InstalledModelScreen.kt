package com.dark.tool_neuron.ui.screens.modelScreen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.Inventory
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dark.ai_module.model.ModelData
import com.dark.ai_module.model.ModelProvider
import com.dark.tool_neuron.ui.theme.rDP
import com.dark.tool_neuron.ui.theme.rSp
import com.dark.tool_neuron.viewModel.ModelScreenViewModel

@Composable
fun InstalledModelsTab(viewModel: ModelScreenViewModel) {
    val models by viewModel.models.collectAsState()

    if (models.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(
                icon = Icons.TwoTone.Inventory,
                title = "No installed models",
                subtitle = "Download or import models to get started"
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(rDP(16.dp)),
            verticalArrangement = Arrangement.spacedBy(rDP(12.dp))
        ) {
            items(models, key = { it.id }) { model ->
                InstalledModelCard(
                    model = model, onRemove = { viewModel.removeModel(model.modelName) })
            }
        }
    }
}

@Composable
private fun InstalledModelCard(model: ModelData, onRemove: () -> Unit) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(rDP(16.dp)),
        elevation = CardDefaults.cardElevation(defaultElevation = rDP(0.dp))
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(rDP(20.dp)),
            verticalArrangement = Arrangement.spacedBy(rDP(12.dp))
        ) {
            // Header Section
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        model.modelName,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = rSp(MaterialTheme.typography.titleLarge.fontSize),
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(Modifier.height(rDP(8.dp)))

                    // Provider & Architecture Badges
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(rDP(8.dp)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ProviderChip(
                            provider = model.providerName,
                            isLocal = model.providerName == ModelProvider.LocalGGUF.toString()
                        )
                    }
                }

                // Action Buttons
                Row(horizontalArrangement = Arrangement.spacedBy(rDP(6.dp))) {
                    IconButton(
                        onClick = { isExpanded = !isExpanded },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        )
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(rDP(22.dp))
                        )
                    }

                    IconButton(
                        onClick = onRemove, colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                        )
                    ) {
                        Icon(
                            Icons.TwoTone.Delete,
                            contentDescription = "Remove",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(rDP(22.dp))
                        )
                    }
                }
            }

            // Quick Stats (Always Visible)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(rDP(8.dp))
            ) {
                QuickStatPill(
                    label = "Context",
                    value = formatNumber(model.ctxSize),
                    modifier = Modifier.weight(1f)
                )
                QuickStatPill(
                    label = "Temp", value = model.temp.toString(), modifier = Modifier.weight(1f)
                )
                QuickStatPill(
                    label = "GPU",
                    value = model.gpuLayers.toString(),
                    modifier = Modifier.weight(1f)
                )
            }

            // Feature Indicators
            Row(
                horizontalArrangement = Arrangement.spacedBy(rDP(8.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (model.isToolCalling) {
                    FeatureBadge(
                        icon = Icons.Default.Build, text = "Function Calling"
                    )
                }
                if (model.useMMAP) {
                    FeatureBadge(
                        icon = Icons.Default.Memory, text = "MMAP"
                    )
                }
                if (model.useMLOCK) {
                    FeatureBadge(
                        icon = Icons.Default.Lock, text = "MLOCK"
                    )
                }
            }

            // Expanded Details Section
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(rDP(12.dp))) {
                    HorizontalDivider(
                        modifier = Modifier.alpha(0.3f),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    // Sampling Parameters
                    Text(
                        text = "Sampling Configuration",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontSize = rSp(MaterialTheme.typography.titleSmall.fontSize)
                        ),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(rDP(8.dp))) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(rDP(8.dp))
                        ) {
                            DetailParameterCard("Top-K", model.topK.toString(), Modifier.weight(1f))
                            DetailParameterCard("Top-P", model.topP.toString(), Modifier.weight(1f))
                            DetailParameterCard("Min-P", model.minP.toString(), Modifier.weight(1f))
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(rDP(8.dp))
                        ) {
                            DetailParameterCard(
                                "Max Tokens", formatNumber(model.maxTokens), Modifier.weight(1f)
                            )
                            DetailParameterCard(
                                "Mirostat", model.mirostat.toString(), Modifier.weight(1f)
                            )
                            DetailParameterCard(
                                "Seed",
                                if (model.seed == -1) "Random" else model.seed.toString(),
                                Modifier.weight(1f)
                            )
                        }
                    }

                    // Performance Settings
                    Text(
                        text = "Performance Settings",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontSize = rSp(MaterialTheme.typography.titleSmall.fontSize)
                        ),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(rDP(8.dp))
                    ) {
                        DetailParameterCard(
                            "Threads", model.threads.toString(), Modifier.weight(1f)
                        )
                        DetailParameterCard(
                            "GPU Layers", model.gpuLayers.toString(), Modifier.weight(1f)
                        )
                        DetailParameterCard(
                            "Context", formatNumber(model.ctxSize), Modifier.weight(1f)
                        )
                    }

                    // Advanced Mirostat Settings
                    if (model.mirostat > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(rDP(8.dp))
                        ) {
                            DetailParameterCard(
                                "Mirostat τ", model.mirostatTau.toString(), Modifier.weight(1f)
                            )
                            DetailParameterCard(
                                "Mirostat η", model.mirostatEta.toString(), Modifier.weight(1f)
                            )
                            Spacer(Modifier.weight(1f))
                        }
                    }

                    // Model Path Info
                    if (model.modelPath.isNotEmpty() && model.providerName == ModelProvider.LocalGGUF.toString()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(rDP(8.dp)))
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(rDP(12.dp)),
                            verticalArrangement = Arrangement.spacedBy(rDP(4.dp))
                        ) {
                            Text(
                                text = "Model Path",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = rSp(MaterialTheme.typography.labelSmall.fontSize)
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = model.modelPath,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = rSp(MaterialTheme.typography.bodySmall.fontSize)
                                ),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderChip(provider: String, isLocal: Boolean) {
    val backgroundColor = if (isLocal) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)

    val textColor = if (isLocal) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.secondary

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(rDP(6.dp)))
            .background(backgroundColor)
            .padding(horizontal = rDP(10.dp), vertical = rDP(5.dp)),
        horizontalArrangement = Arrangement.spacedBy(rDP(4.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isLocal) Icons.Default.Storage else Icons.Default.Cloud,
            contentDescription = null,
            modifier = Modifier.size(rDP(14.dp)),
            tint = textColor
        )
        Text(
            text = if (isLocal) "Local Model" else provider,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = rSp(MaterialTheme.typography.labelSmall.fontSize)
            ),
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

@Composable
private fun QuickStatPill(
    label: String, value: String, modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(rDP(10.dp)))
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = rDP(10.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(rDP(4.dp))
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = rSp(MaterialTheme.typography.titleMedium.fontSize)
            ),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = rSp(MaterialTheme.typography.labelSmall.fontSize)
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun FeatureBadge(
    icon: ImageVector, text: String
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(rDP(6.dp)))
            .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f))
            .padding(horizontal = rDP(8.dp), vertical = rDP(5.dp)),
        horizontalArrangement = Arrangement.spacedBy(rDP(4.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(rDP(14.dp)),
            tint = MaterialTheme.colorScheme.tertiary
        )
        Text(
            text = text, style = MaterialTheme.typography.labelSmall.copy(
                fontSize = rSp(MaterialTheme.typography.labelSmall.fontSize)
            ), color = MaterialTheme.colorScheme.onTertiaryContainer, fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun DetailParameterCard(
    label: String, value: String, modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(rDP(8.dp)))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(rDP(10.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(rDP(4.dp))
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = rSp(MaterialTheme.typography.bodyLarge.fontSize)
            ),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = rSp(MaterialTheme.typography.labelSmall.fontSize)
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun EmptyState(
    icon: ImageVector, title: String, subtitle: String, compact: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(rDP(if (compact) 24.dp else 48.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(rDP(12.dp))
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(rDP(if (compact) 56.dp else 80.dp)),
            shadowElevation = rDP(0.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(rDP(if (compact) 28.dp else 40.dp)),
                    tint = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                )
            }
        }
        Text(
            title,
            style = if (compact) MaterialTheme.typography.titleMedium.copy(
                fontSize = rSp(MaterialTheme.typography.titleMedium.fontSize)
            )
            else MaterialTheme.typography.titleLarge.copy(
                fontSize = rSp(MaterialTheme.typography.titleLarge.fontSize)
            ),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            subtitle, style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = rSp(MaterialTheme.typography.bodyMedium.fontSize)
            ), color = MaterialTheme.colorScheme.onSurface.copy(0.6f), textAlign = TextAlign.Center
        )
    }
}