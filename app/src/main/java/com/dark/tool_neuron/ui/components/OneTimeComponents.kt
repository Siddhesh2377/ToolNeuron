package com.dark.tool_neuron.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowOutward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SubdirectoryArrowLeft
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.global.Standards
import com.dark.tool_neuron.models.state.AppState
import com.dark.tool_neuron.models.state.getBackgroundColor
import com.dark.tool_neuron.models.state.getColor
import com.dark.tool_neuron.models.state.getContentColor
import com.dark.tool_neuron.models.state.getDisplayText
import com.dark.tool_neuron.models.state.getIcon
import com.dark.tool_neuron.models.table_schema.Model
import com.dark.tool_neuron.state.AppStateManager
import com.dark.tool_neuron.ui.theme.rDp

@Composable
fun AnimatedTitle(
    modifier: Modifier = Modifier, onShowDynamicWindow: () -> Unit = {}
) {
    val appState by AppStateManager.appState.collectAsState()

    AnimatedContent(
        targetState = appState, transitionSpec = {
            fadeIn(animationSpec = tween(400)) togetherWith fadeOut(animationSpec = tween(400))
        }, label = "AppStateTitleAnim"
    ) { state ->
        TitleRow(
            text = state.getDisplayText(),
            icon = state.getIcon(),
            state = state,
            modifier = modifier.clickable {
                onShowDynamicWindow()
            })
    }
}

@Composable
fun TitleRow(
    modifier: Modifier = Modifier, text: String, icon: Int, state: AppState
) {
    val iconColor = state.getColor()
    val backgroundColor = state.getBackgroundColor()
    val contentColor = state.getContentColor()

    val isLoading = state is AppState.LoadingModel

    Box(modifier = modifier) {
        Surface(
            color = backgroundColor,
            shape = RoundedCornerShape(rDp(26.dp)),
            modifier = Modifier.height(rDp(Standards.ActionIconSize))
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(rDp(6.dp)),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = rDp(12.dp))
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(rDp(16.dp)),
                        color = iconColor,
                        strokeWidth = rDp(2.dp)
                    )
                } else {
                    Icon(
                        painter = painterResource(icon),
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(rDp(18.dp))
                    )
                }

                Text(
                    text = text,
                    color = contentColor,
                    maxLines = 1,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun ModelListItem(
    modifier: Modifier,
    model: Model,
    isLoaded: Boolean,
    onClickListener: (Model) -> Unit,
    onDeleteListener: ((Model) -> Unit)? = null
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Model") },
            text = { Text("Delete \"${model.modelName}\"? This will remove the model file from storage.") },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteListener?.invoke(model)
                    },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Delete") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = { showDeleteConfirm = false }
                ) { Text("Cancel") }
            }
        )
    }

    Card(
        modifier = modifier, colors = CardDefaults.cardColors(
            containerColor = if (isLoaded) MaterialTheme.colorScheme.primary.copy(0.12f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(0.5f)
        ), shape = RoundedCornerShape(rDp(10.dp))
    ) {
        Row(
            modifier = Modifier.padding(rDp(12.dp)),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(rDp(10.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(
                        if (model.providerType.name == "GGUF") com.dark.tool_neuron.R.drawable.smart_temp_message
                        else com.dark.tool_neuron.R.drawable.vl_models
                    ),
                    contentDescription = null,
                    modifier = Modifier.size(rDp(20.dp)),
                    tint = if (isLoaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )

                androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = model.modelName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isLoaded) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isLoaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = model.providerType.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(rDp(4.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onDeleteListener != null && !isLoaded) {
                    ActionButton(
                        onClickListener = { showDeleteConfirm = true },
                        icon = Icons.Default.Delete,
                        contentDescription = "Delete",
                        shape = RoundedCornerShape(rDp(8.dp)),
                        colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.error.copy(0.12f),
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    )
                }

                androidx.compose.animation.Crossfade(
                    targetState = isLoaded,
                    label = "button_state"
                ) { loaded ->
                    if (loaded) {
                        ActionTextButton(
                            onClickListener = { onClickListener(model) },
                            icon = Icons.Default.SubdirectoryArrowLeft,
                            text = "Unload",
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error.copy(0.12f),
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            shape = RoundedCornerShape(rDp(8.dp))
                        )
                    } else {
                        ActionButton(
                            onClickListener = { onClickListener(model) },
                            icon = Icons.Default.ArrowOutward,
                            contentDescription = "Load",
                            shape = RoundedCornerShape(rDp(8.dp)),
                            colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(0.12f),
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }
            }
        }
    }
}