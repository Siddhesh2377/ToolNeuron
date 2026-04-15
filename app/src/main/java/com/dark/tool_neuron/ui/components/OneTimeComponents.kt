package com.dark.tool_neuron.ui.components

import androidx.compose.animation.AnimatedContent
import com.dark.tool_neuron.ui.theme.Motion
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.global.Standards
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dark.tool_neuron.models.state.AppState
import com.dark.tool_neuron.models.state.getBackgroundColor
import com.dark.tool_neuron.models.state.getColor
import com.dark.tool_neuron.models.state.getContentColor
import com.dark.tool_neuron.models.state.getDisplayText
import com.dark.tool_neuron.models.state.getIcon
import com.dark.tool_neuron.models.table_schema.Model
import com.dark.tool_neuron.state.AppStateManager
import com.dark.tool_neuron.ui.icons.TnIcons

@Composable
fun AnimatedTitle(
    modifier: Modifier = Modifier, onShowDynamicWindow: () -> Unit = {}
) {
    val appState by AppStateManager.appState.collectAsStateWithLifecycle()

    AnimatedContent(
        targetState = appState, transitionSpec = {
            fadeIn(Motion.entrance()) togetherWith fadeOut(Motion.entrance())
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TitleRow(
    modifier: Modifier = Modifier, text: String, icon: ImageVector, state: AppState
) {
    val iconColor = state.getColor()
    val backgroundColor = state.getBackgroundColor()
    val contentColor = state.getContentColor()

    val isLoading = state is AppState.LoadingModel

    Box(modifier = modifier) {
        Surface(
            color = backgroundColor,
            shape = RoundedCornerShape(26.dp),
            modifier = Modifier.height(Standards.ActionIconSize)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = Standards.SpacingMd)
            ) {
                if (isLoading) {
                    LoadingIndicator(
                        modifier = Modifier.size(16.dp),
                        color = iconColor
                    )
                } else {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(18.dp)
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
        ), shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.padding(Standards.SpacingMd),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (model.providerType.name == "GGUF") TnIcons.Sparkles
                        else TnIcons.Photo,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
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
                horizontalArrangement = Arrangement.spacedBy(Standards.SpacingXs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onDeleteListener != null && !isLoaded) {
                    ActionButton(
                        onClickListener = { showDeleteConfirm = true },
                        icon = TnIcons.Trash,
                        contentDescription = "Delete",
                        shape = RoundedCornerShape(Standards.RadiusMd),
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
                            icon = TnIcons.CornerDownLeft,
                            text = "Unload",
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error.copy(0.12f),
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            shape = RoundedCornerShape(Standards.RadiusMd)
                        )
                    } else {
                        ActionButton(
                            onClickListener = { onClickListener(model) },
                            icon = TnIcons.ExternalLink,
                            contentDescription = "Load",
                            shape = RoundedCornerShape(Standards.RadiusMd),
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