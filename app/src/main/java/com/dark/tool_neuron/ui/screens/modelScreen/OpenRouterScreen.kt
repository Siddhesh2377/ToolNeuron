package com.dark.tool_neuron.ui.screens.modelScreen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.twotone.CheckCircle
import androidx.compose.material.icons.twotone.CloudOff
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.Key
import androidx.compose.material.icons.twotone.Search
import androidx.compose.material.icons.twotone.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dark.ai_module.model.OpenRouterModel
import com.dark.ai_module.model.toModelData
import com.dark.tool_neuron.R
import com.dark.tool_neuron.ui.theme.rDP
import com.dark.tool_neuron.ui.theme.rSp
import com.dark.tool_neuron.viewModel.ModelScreenViewModel
import kotlinx.coroutines.delay

@Composable
fun OpenRouterTab(viewModel: ModelScreenViewModel) {
    val context = LocalContext.current
    val apiKey by viewModel.openRouterApiKey.collectAsStateWithLifecycle()
    val installed by viewModel.openRouterInstalledModels.collectAsStateWithLifecycle()
    val available by viewModel.availableModels.collectAsStateWithLifecycle()

    var showPicker by remember { mutableStateOf(false) }
    var showKey by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.initOpenRouter(context)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(rDP(16.dp)),
        verticalArrangement = Arrangement.spacedBy(rDP(16.dp))
    ) {
        // API Settings Card
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                shape = RoundedCornerShape(rDP(20.dp)),
                elevation = CardDefaults.cardElevation(defaultElevation = rDP(2.dp))
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(rDP(20.dp)),
                    verticalArrangement = Arrangement.spacedBy(rDP(16.dp))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(rDP(12.dp))
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(rDP(40.dp))
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.TwoTone.Key,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(rDP(20.dp))
                                )
                            }
                        }
                        Column {
                            Text(
                                "API Configuration",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = rSp(16.sp, maxSp = 18.sp)
                                )
                            )
                            Text(
                                "Connect your OpenRouter account",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = rSp(12.sp, maxSp = 14.sp)
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { viewModel.saveOpenRouterApiKey(context, it) },
                        label = { Text("API Key", fontSize = rSp(14.sp, maxSp = 16.sp)) },
                        placeholder = { Text("sk-or-...", fontSize = rSp(14.sp, maxSp = 16.sp)) },
                        trailingIcon = {
                            IconButton(onClick = { showKey = !showKey }) {
                                Icon(
                                    painterResource(if (showKey) R.drawable.show else R.drawable.hide),
                                    "Toggle visibility",
                                    modifier = Modifier.size(rDP(20.dp))
                                )
                            }
                        },
                        visualTransformation = if (showKey) VisualTransformation.None
                        else PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(rDP(12.dp)),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        ),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = rSp(14.sp, maxSp = 16.sp)
                        )
                    )

                    Button(
                        onClick = { viewModel.fetchAvailableModels() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(rDP(48.dp)),
                        enabled = apiKey.isNotBlank(),
                        shape = RoundedCornerShape(rDP(12.dp))
                    ) {
                        Icon(
                            Icons.TwoTone.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(rDP(20.dp))
                        )
                        Spacer(Modifier.width(rDP(8.dp)))
                        Text(
                            "Fetch Available Models",
                            fontWeight = FontWeight.Bold,
                            fontSize = rSp(14.sp, maxSp = 16.sp)
                        )
                    }
                }
            }
        }

        // Selected Models Section
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                shape = RoundedCornerShape(rDP(20.dp)),
                elevation = CardDefaults.cardElevation(defaultElevation = rDP(2.dp))
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(rDP(20.dp)),
                    verticalArrangement = Arrangement.spacedBy(rDP(16.dp))
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "Selected Models",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = rSp(16.sp, maxSp = 18.sp)
                                )
                            )
                            Text(
                                if (installed.isEmpty()) "No models added yet"
                                else "${installed.size} model${if (installed.size != 1) "s" else ""} active",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = rSp(12.sp, maxSp = 14.sp)
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        AnimatedVisibility(
                            visible = installed.isNotEmpty(),
                            enter = scaleIn() + fadeIn(),
                            exit = scaleOut() + fadeOut()
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    "${installed.size}", modifier = Modifier.padding(
                                        horizontal = rDP(12.dp), vertical = rDP(6.dp)
                                    ), style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = rSp(14.sp, maxSp = 16.sp)
                                    ), color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }

                    if (installed.isEmpty()) {
                        EmptyState(
                            icon = Icons.TwoTone.CloudOff,
                            title = "No models selected",
                            subtitle = "Add models from the available list below",
                            compact = true
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(rDP(8.dp))) {
                            installed.forEach { model ->
                                OpenRouterModelItem(
                                    model = model, onRemove = { viewModel.removeModel(model.name) })
                            }
                        }
                    }

                    FilledTonalButton(
                        onClick = { showPicker = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(rDP(48.dp)),
                        enabled = available.isNotEmpty(),
                        shape = RoundedCornerShape(rDP(12.dp))
                    ) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = null,
                            modifier = Modifier.size(rDP(20.dp))
                        )
                        Spacer(Modifier.width(rDP(8.dp)))
                        Text(
                            "Add Model",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = rSp(14.sp, maxSp = 16.sp)
                        )
                    }
                }
            }
        }
    }

    if (showPicker) {
        ModelPickerDialog(
            available = available,
            selected = installed,
            onDismiss = { showPicker = false },
            onSelect = { model ->
                viewModel.addOpenRouterModel(model)
                viewModel.addModel(model.toModelData())
                showPicker = false
            })
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
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(rDP(if (compact) 64.dp else 96.dp))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(rDP(if (compact) 32.dp else 48.dp)),
                    tint = MaterialTheme.colorScheme.onSurface.copy(0.4f)
                )
            }
        }
        Text(
            title,
            style = if (compact) MaterialTheme.typography.titleMedium
            else MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            fontSize = rSp(if (compact) 16.sp else 20.sp, maxSp = if (compact) 18.sp else 22.sp)
        )
        Text(
            subtitle, style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = rSp(14.sp, maxSp = 16.sp)
            ), color = MaterialTheme.colorScheme.onSurface.copy(0.6f), textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun OpenRouterModelItem(model: OpenRouterModel, onRemove: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(rDP(16.dp)),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = rDP(1.dp),
        shadowElevation = rDP(1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(rDP(16.dp)),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(rDP(6.dp))
            ) {
                Text(
                    model.name, style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.SemiBold, fontSize = rSp(15.sp, maxSp = 17.sp)
                    ), maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(rDP(12.dp)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(rDP(6.dp)),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            "${model.ctxSize} ctx",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = rSp(11.sp, maxSp = 13.sp), fontWeight = FontWeight.Medium
                            ),
                            modifier = Modifier.padding(
                                horizontal = rDP(8.dp),
                                vertical = rDP(4.dp)
                            ),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Text(
                        "Temp ${model.temperature}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = rSp(11.sp, maxSp = 13.sp)
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.width(rDP(12.dp)))

            IconButton(
                onClick = onRemove, colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.error
                ), modifier = Modifier.size(rDP(40.dp))
            ) {
                Icon(
                    Icons.TwoTone.Delete,
                    contentDescription = "Remove model",
                    modifier = Modifier.size(rDP(20.dp))
                )
            }
        }
    }
}

@Composable
private fun ModelPickerDialog(
    available: List<OpenRouterModel>,
    selected: List<OpenRouterModel>,
    onDismiss: () -> Unit,
    onSelect: (OpenRouterModel) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val filtered = remember(searchQuery, available, selected) {
        available.filter { it.name.contains(searchQuery, ignoreCase = true) }
            .filter { it !in selected }
    }

    AlertDialog(
        onDismissRequest = onDismiss, confirmButton = {}, dismissButton = {
        TextButton(onClick = onDismiss) {
            Text(
                "Close", fontWeight = FontWeight.Bold, fontSize = rSp(14.sp, maxSp = 16.sp)
            )
        }
    }, title = {
        Text(
            "Select Model", style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold, fontSize = rSp(20.sp, maxSp = 22.sp)
            )
        )
    }, text = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = rDP(500.dp)),
            verticalArrangement = Arrangement.spacedBy(rDP(16.dp))
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        "Search models...", fontSize = rSp(14.sp, maxSp = 16.sp)
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.TwoTone.Search,
                        contentDescription = null,
                        modifier = Modifier.size(rDP(20.dp))
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(rDP(12.dp)),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = rSp(14.sp, maxSp = 16.sp)
                )
            )

            if (filtered.isEmpty()) {
                EmptyState(
                    icon = Icons.TwoTone.SearchOff,
                    title = "No models found",
                    subtitle = if (searchQuery.isBlank()) "Fetch models from API first"
                    else "Try different search terms",
                    compact = true
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(rDP(8.dp))
                ) {
                    items(filtered, key = { it.id }) { model ->
                        ModelPickerItem(
                            model = model, onClick = { onSelect(model) })
                    }
                }
            }
        }
    }, shape = RoundedCornerShape(rDP(24.dp))
    )
}

@Composable
private fun ModelPickerItem(model: OpenRouterModel, onClick: () -> Unit) {
    var isPressed by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        if (isPressed) 0.96f else 1f, animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium
        ), label = "scale"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(rDP(16.dp)))
            .clickable(
                interactionSource = remember { MutableInteractionSource() }, indication = null
            ) {
                isPressed = true
                onClick()
            },
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(rDP(16.dp)),
        tonalElevation = rDP(2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(rDP(16.dp)),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(rDP(8.dp))
            ) {
                Text(
                    model.name, style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.SemiBold, fontSize = rSp(15.sp, maxSp = 17.sp)
                    ), maxLines = 2, overflow = TextOverflow.Ellipsis
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(rDP(12.dp)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(rDP(6.dp)),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            "${model.ctxSize} ctx",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = rSp(11.sp, maxSp = 13.sp), fontWeight = FontWeight.Medium
                            ),
                            modifier = Modifier.padding(
                                horizontal = rDP(8.dp), vertical = rDP(4.dp)
                            ),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }

                    if (model.supportsTools) {
                        Surface(
                            shape = RoundedCornerShape(rDP(6.dp)),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                "Tools ✓", style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = rSp(11.sp, maxSp = 13.sp),
                                    fontWeight = FontWeight.Medium
                                ), modifier = Modifier.padding(
                                    horizontal = rDP(8.dp), vertical = rDP(4.dp)
                                ), color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.width(rDP(12.dp)))

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(rDP(40.dp))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "Add model",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(rDP(20.dp))
                    )
                }
            }
        }
    }

    LaunchedEffect(isPressed) {
        if (isPressed) {
            delay(100)
            isPressed = false
        }
    }
}