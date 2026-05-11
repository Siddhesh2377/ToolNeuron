package com.dark.tool_neuron.ui.screens.storage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dark.download_manager.formatBytes
import com.dark.tool_neuron.repo.StorageCategoryId
import com.dark.tool_neuron.repo.StorageCategorySnapshot
import com.dark.tool_neuron.repo.StorageSnapshot
import com.dark.tool_neuron.ui.components.ActionTextButton
import com.dark.tool_neuron.ui.components.SectionHeader
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.LocalTnShapes
import com.dark.tool_neuron.viewmodel.StorageViewModel
import kotlinx.coroutines.delay

@Composable
fun StorageScreen(
    innerPadding: PaddingValues,
    viewModel: StorageViewModel,
    onNavigateToModelManager: () -> Unit,
    onNavigateToStore: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val dimens = LocalDimens.current

    LaunchedEffect(state.message) {
        if (state.message != null) {
            delay(2200)
            viewModel.consumeMessage()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                horizontal = dimens.screenPadding,
                vertical = dimens.spacingMd,
            ),
            verticalArrangement = Arrangement.spacedBy(dimens.spacingSm),
        ) {
            item(key = "hero") {
                HeroCard(snapshot = state.snapshot, isLoading = state.isLoading)
            }

            item(key = "warning") { DangerStrip() }

            item(key = "section-header") {
                Box(modifier = Modifier.padding(top = dimens.spacingSm)) {
                    SectionHeader(title = "By category")
                }
            }

            val deletable = listOf(
                StorageCategoryId.CHAT_MODELS,
                StorageCategoryId.VLM,
                StorageCategoryId.VOICE,
                StorageCategoryId.DOCUMENTS,
                StorageCategoryId.CHATS,
                StorageCategoryId.CACHE,
            )
            items(deletable, key = { "cat-${it.name}" }) { id ->
                CategoryCard(
                    id = id,
                    snapshot = state.snapshot?.categories?.get(id),
                    onManage = managerRouteFor(
                        id = id,
                        onModelManager = onNavigateToModelManager,
                        onStore = onNavigateToStore,
                    ),
                    onClear = { viewModel.requestClear(id) },
                )
            }

            item(key = "system-divider") {
                Box(modifier = Modifier.padding(top = dimens.spacingSm)) {
                    SectionHeader(title = "Protected")
                }
            }

            item(key = "system") {
                SystemCard(snapshot = state.snapshot?.categories?.get(StorageCategoryId.SYSTEM))
            }
        }

        if (state.message != null) {
            ToastPill(text = state.message!!)
        }
    }

    val pending = state.pendingClear
    if (pending != null) {
        ConfirmClearDialog(
            id = pending,
            sizeBytes = state.snapshot?.categories?.get(pending)?.sizeBytes ?: 0L,
            onConfirm = viewModel::confirmClear,
            onDismiss = viewModel::cancelClear,
        )
    }
}

@Composable
private fun HeroCard(snapshot: StorageSnapshot?, isLoading: Boolean) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current
    val total = snapshot?.totalBytes ?: 0L
    val palette = categoryPalette()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = tnShapes.card,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = dimens.spacingLg,
                vertical = dimens.spacingLg,
            ),
            verticalArrangement = Arrangement.spacedBy(dimens.spacingMd),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(dimens.spacingXxs)) {
                Text(
                    text = "Total used on this device",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                )
                Text(
                    text = if (isLoading && snapshot == null) "…" else formatBytes(total),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }

            if (snapshot != null && total > 0L) {
                SegmentedBar(snapshot = snapshot, palette = palette)
                LegendGrid(snapshot = snapshot, palette = palette)
            } else if (snapshot != null) {
                Text(
                    text = "Nothing stored yet. Install a model from the Store to get started.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
            }
        }
    }
}

@Composable
private fun SegmentedBar(snapshot: StorageSnapshot, palette: Map<StorageCategoryId, Color>) {
    val tnShapes = LocalTnShapes.current
    val total = snapshot.totalBytes.coerceAtLeast(1L)
    val ordered = listOf(
        StorageCategoryId.CHAT_MODELS,
        StorageCategoryId.VLM,
        StorageCategoryId.VOICE,
        StorageCategoryId.DOCUMENTS,
        StorageCategoryId.CHATS,
        StorageCategoryId.SYSTEM,
        StorageCategoryId.CACHE,
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(tnShapes.cardSmall)
            .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.10f)),
    ) {
        ordered.forEach { id ->
            val bytes = snapshot.categories[id]?.sizeBytes ?: 0L
            if (bytes <= 0L) return@forEach
            val weight = (bytes.toFloat() / total.toFloat()).coerceAtLeast(0.005f)
            Box(
                modifier = Modifier
                    .weight(weight)
                    .fillMaxWidth()
                    .background(palette[id] ?: MaterialTheme.colorScheme.outline),
            )
        }
    }
}

@Composable
private fun LegendGrid(snapshot: StorageSnapshot, palette: Map<StorageCategoryId, Color>) {
    val dimens = LocalDimens.current
    val ordered = listOf(
        StorageCategoryId.CHAT_MODELS,
        StorageCategoryId.VLM,
        StorageCategoryId.VOICE,
        StorageCategoryId.DOCUMENTS,
        StorageCategoryId.CHATS,
        StorageCategoryId.SYSTEM,
        StorageCategoryId.CACHE,
    ).filter { (snapshot.categories[it]?.sizeBytes ?: 0L) > 0L }

    Column(verticalArrangement = Arrangement.spacedBy(dimens.spacingXs)) {
        ordered.chunked(2).forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
            ) {
                pair.forEach { id ->
                    LegendChip(
                        modifier = Modifier.weight(1f),
                        color = palette[id] ?: MaterialTheme.colorScheme.outline,
                        label = categoryMeta(id).title,
                        size = formatBytes(snapshot.categories[id]?.sizeBytes ?: 0L),
                    )
                }
                if (pair.size == 1) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun LegendChip(
    modifier: Modifier,
    color: Color,
    label: String,
    size: String,
) {
    val dimens = LocalDimens.current
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.spacingXs),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Text(
            text = size,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun DangerStrip() {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = tnShapes.cardSmall,
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = dimens.spacingMd,
                vertical = dimens.spacingSm,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
        ) {
            Icon(
                imageVector = TnIcons.AlertTriangle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(dimens.iconSm),
            )
            Text(
                text = "Deletions here are permanent. System data is locked.",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun CategoryCard(
    id: StorageCategoryId,
    snapshot: StorageCategorySnapshot?,
    onManage: (() -> Unit)?,
    onClear: () -> Unit,
) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current
    val meta = categoryMeta(id)
    val palette = categoryPalette()
    val tint = palette[id] ?: MaterialTheme.colorScheme.primary
    val sizeBytes = snapshot?.sizeBytes ?: 0L
    val itemCount = snapshot?.itemCount ?: 0
    val isEmpty = sizeBytes <= 0L

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = tnShapes.card,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f),
    ) {
        Column(modifier = Modifier.padding(dimens.cardPadding)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
            ) {
                Box(
                    modifier = Modifier
                        .size(dimens.actionIconSize + 6.dp)
                        .clip(tnShapes.cardSmall)
                        .background(tint.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = meta.icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(dimens.iconMd),
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = meta.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = subtitleFor(id, itemCount, isEmpty),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Text(
                    text = formatBytes(sizeBytes),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isEmpty) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                )
            }

            if (!isEmpty || onManage != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = dimens.spacingSm),
                    horizontalArrangement = Arrangement.spacedBy(dimens.spacingXs),
                ) {
                    Box(modifier = Modifier.weight(1f))

                    if (onManage != null) {
                        ActionTextButton(
                            onClickListener = onManage,
                            icon = if (isEmpty) TnIcons.Plus else TnIcons.Sliders,
                            text = if (isEmpty) "Browse" else "Manage",
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = tint.copy(alpha = 0.10f),
                                contentColor = tint,
                            ),
                        )
                    }
                    if (!isEmpty) {
                        ActionTextButton(
                            onClickListener = onClear,
                            icon = TnIcons.Trash,
                            text = "Clear",
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.10f),
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SystemCard(snapshot: StorageCategorySnapshot?) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current
    val sizeBytes = snapshot?.sizeBytes ?: 0L
    val palette = categoryPalette()
    val tint = palette[StorageCategoryId.SYSTEM] ?: MaterialTheme.colorScheme.error

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = tnShapes.card,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.20f),
    ) {
        Column(modifier = Modifier.padding(dimens.cardPadding)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
            ) {
                Box(
                    modifier = Modifier
                        .size(dimens.actionIconSize + 6.dp)
                        .clip(tnShapes.cardSmall)
                        .background(tint.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = TnIcons.Lock,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(dimens.iconMd),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "System data",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Encrypted vault, auth state, indexes",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = formatBytes(sizeBytes),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "Wipe these from Settings → Privacy or with the panic PIN.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = dimens.spacingSm),
            )
        }
    }
}

@Composable
private fun ConfirmClearDialog(
    id: StorageCategoryId,
    sizeBytes: Long,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val meta = categoryMeta(id)
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = TnIcons.AlertTriangle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(28.dp),
            )
        },
        title = { Text("Free ${formatBytes(sizeBytes)}?") },
        text = {
            Text(
                text = dialogBodyFor(id),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) { Text("Delete ${meta.title.lowercase()}") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun ToastPill(text: String) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(dimens.spacingLg),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            shape = tnShapes.full,
            color = MaterialTheme.colorScheme.inverseSurface,
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(
                    horizontal = dimens.spacingLg,
                    vertical = dimens.spacingSm,
                ),
                color = MaterialTheme.colorScheme.inverseOnSurface,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private data class CategoryMeta(
    val title: String,
    val icon: ImageVector,
)

@Composable
private fun categoryMeta(id: StorageCategoryId): CategoryMeta = when (id) {
    StorageCategoryId.CHAT_MODELS -> CategoryMeta("Chat models", TnIcons.Cpu)
    StorageCategoryId.VLM -> CategoryMeta("Vision models", TnIcons.Photo)
    StorageCategoryId.VOICE -> CategoryMeta("Voice models", TnIcons.Volume)
    StorageCategoryId.DOCUMENTS -> CategoryMeta("Documents", TnIcons.FileText)
    StorageCategoryId.CHATS -> CategoryMeta("Chat history", TnIcons.MessageCircle)
    StorageCategoryId.CACHE -> CategoryMeta("Cache", TnIcons.Broom)
    StorageCategoryId.SYSTEM -> CategoryMeta("System data", TnIcons.ShieldCheck)
}

@Composable
private fun categoryPalette(): Map<StorageCategoryId, Color> {
    val cs = MaterialTheme.colorScheme
    return mapOf(
        StorageCategoryId.CHAT_MODELS to cs.primary,
        StorageCategoryId.VLM to cs.tertiary,
        StorageCategoryId.VOICE to cs.secondary,
        StorageCategoryId.DOCUMENTS to cs.primary.copy(alpha = 0.55f),
        StorageCategoryId.CHATS to cs.tertiary.copy(alpha = 0.55f),
        StorageCategoryId.CACHE to cs.outline,
        StorageCategoryId.SYSTEM to cs.error,
    )
}

private fun managerRouteFor(
    id: StorageCategoryId,
    onModelManager: () -> Unit,
    onStore: () -> Unit,
): (() -> Unit)? = when (id) {
    StorageCategoryId.CHAT_MODELS,
    StorageCategoryId.VLM,
    StorageCategoryId.VOICE -> onModelManager
    StorageCategoryId.DOCUMENTS,
    StorageCategoryId.CHATS,
    StorageCategoryId.CACHE,
    StorageCategoryId.SYSTEM -> null
}

private fun subtitleFor(id: StorageCategoryId, count: Int, isEmpty: Boolean): String {
    if (isEmpty) {
        return when (id) {
            StorageCategoryId.CHAT_MODELS -> "No models installed yet"
            StorageCategoryId.VLM -> "No vision models yet"
            StorageCategoryId.VOICE -> "No voice models yet"
            StorageCategoryId.DOCUMENTS -> "No documents attached"
            StorageCategoryId.CHATS -> "No chats yet"
            StorageCategoryId.CACHE -> "Clean"
            StorageCategoryId.SYSTEM -> ""
        }
    }
    return when (id) {
        StorageCategoryId.CHAT_MODELS -> "$count installed · GGUF weights"
        StorageCategoryId.VLM -> "$count installed · base + projector"
        StorageCategoryId.VOICE -> "$count installed · TTS / STT"
        StorageCategoryId.DOCUMENTS -> "$count attached · raw bytes"
        StorageCategoryId.CHATS -> "$count chats · messages and drafts"
        StorageCategoryId.CACHE -> "Temporary files"
        StorageCategoryId.SYSTEM -> "Encrypted vault"
    }
}

private fun dialogBodyFor(id: StorageCategoryId): String = when (id) {
    StorageCategoryId.CHAT_MODELS -> "Installed chat models will be removed. Re-download them anytime from the Store."
    StorageCategoryId.VLM -> "Vision base + projector files will be removed. Re-download from the Store."
    StorageCategoryId.VOICE -> "TTS and STT models will be removed. Voice falls back to the next install."
    StorageCategoryId.DOCUMENTS -> "All chat-attached documents and their RAG index will be wiped. Chats stay; only the document side is cleared."
    StorageCategoryId.CHATS -> "Every chat and its messages will be deleted. This cannot be undone."
    StorageCategoryId.CACHE -> "Cached thumbnails and temp files will be cleared. Models, chats, and documents are untouched."
    StorageCategoryId.SYSTEM -> ""
}
