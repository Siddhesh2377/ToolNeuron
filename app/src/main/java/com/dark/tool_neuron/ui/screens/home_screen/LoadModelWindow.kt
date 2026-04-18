package com.dark.tool_neuron.ui.screens.home_screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.model.ModelInfo
import com.dark.tool_neuron.ui.components.ActionTextButton
import com.dark.tool_neuron.ui.components.SectionHeader
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.LocalTnShapes
import com.dark.tool_neuron.viewmodel.ModelLoadState

private val WindowHeight = 240.dp

@Composable
fun LoadModelWindow(
    models: List<ModelInfo>,
    loadState: ModelLoadState,
    onLoad: (ModelInfo) -> Unit,
    onUnload: (ModelInfo) -> Unit,
    onBrowseStore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current

    val loadedId = when (loadState) {
        is ModelLoadState.Active -> loadState.modelId
        is ModelLoadState.Loading -> loadState.modelId
        else -> null
    }

    val sortedModels = remember(models, loadedId) {
        models.sortedWith(
            compareByDescending<ModelInfo> { it.id == loadedId }
                .thenBy { it.name.lowercase() }
        )
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(WindowHeight)
            .padding(bottom = dimens.spacingSm),
        shape = tnShapes.xl,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = dimens.spacingMd, vertical = dimens.spacingSm),
            verticalArrangement = Arrangement.spacedBy(dimens.spacingXs),
        ) {
            SectionHeader(title = "Models")

            if (sortedModels.isEmpty()) {
                EmptyState(onBrowseStore = onBrowseStore)
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(dimens.spacingXs),
                ) {
                    items(items = sortedModels, key = { it.id }) { model ->
                        ModelRow(
                            model = model,
                            rowState = rowStateFor(model, loadState),
                            onLoad = { onLoad(model) },
                            onUnload = { onUnload(model) },
                            onRetry = { onLoad(model) },
                        )
                    }
                }
            }
        }
    }
}

private sealed interface RowState {
    data object Idle : RowState
    data object Loading : RowState
    data object Active : RowState
    data class Error(val message: String) : RowState
}

private fun rowStateFor(model: ModelInfo, loadState: ModelLoadState): RowState = when (loadState) {
    is ModelLoadState.Loading -> if (loadState.modelId == model.id) RowState.Loading else RowState.Idle
    is ModelLoadState.Active -> if (loadState.modelId == model.id) RowState.Active else RowState.Idle
    is ModelLoadState.Error -> if (loadState.modelId == model.id) RowState.Error(loadState.message) else RowState.Idle
    ModelLoadState.Idle -> RowState.Idle
}

@Composable
private fun ModelRow(
    model: ModelInfo,
    rowState: RowState,
    onLoad: () -> Unit,
    onUnload: () -> Unit,
    onRetry: () -> Unit,
) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current
    val isActive = rowState is RowState.Active

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = tnShapes.lg,
        color = if (isActive)
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        else
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = dimens.spacingMd, vertical = dimens.spacingSm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = model.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (rowState is RowState.Error) {
                    Text(
                        text = rowState.message,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            when (rowState) {
                RowState.Idle -> ActionTextButton(
                    onClickListener = onLoad,
                    icon = TnIcons.Load,
                    text = "Load",
                )

                RowState.Loading -> Box(
                    modifier = Modifier.size(dimens.actionIconSize),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(dimens.iconMd),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    )
                }

                RowState.Active -> ActionTextButton(
                    onClickListener = onUnload,
                    icon = TnIcons.Check,
                    text = "Loaded",
                )

                is RowState.Error -> ActionTextButton(
                    onClickListener = onRetry,
                    icon = TnIcons.Refresh,
                    text = "Retry",
                )
            }
        }
    }
}

@Composable
private fun EmptyState(onBrowseStore: () -> Unit) {
    val dimens = LocalDimens.current
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(dimens.spacingSm),
        ) {
            Text(
                text = "No models installed",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            ActionTextButton(
                onClickListener = onBrowseStore,
                icon = TnIcons.Download,
                text = "Browse store",
            )
        }
    }
}
