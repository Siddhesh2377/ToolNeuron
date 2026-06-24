package com.dark.tool_neuron.ui.screens.hf_explorer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dark.download_manager.formatBytes
import com.dark.tool_neuron.repo.HfApiError
import com.dark.tool_neuron.repo.hf.HfCardData
import com.dark.tool_neuron.repo.hf.HfGated
import com.dark.tool_neuron.repo.hf.HfGgufMeta
import com.dark.tool_neuron.repo.hf.HfModelDetail
import com.dark.tool_neuron.repo.hf.HfSibling
import com.dark.tool_neuron.model.VlmFileGroup
import com.dark.tool_neuron.ui.components.ActionButton
import com.dark.tool_neuron.ui.components.ActionTextButton
import com.dark.tool_neuron.ui.components.CaptionText
import com.dark.tool_neuron.ui.components.SectionHeader
import com.dark.tool_neuron.ui.components.StandardCard
import com.dark.tool_neuron.ui.components.markdown.LocalMarkdownColors
import com.dark.tool_neuron.ui.components.markdown.lazyMarkdownItems
import com.dark.tool_neuron.ui.components.markdown.rememberMarkdownColors
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.LocalTnShapes
import com.dark.tool_neuron.viewmodel.HfExplorerViewModel
import com.dark.tool_neuron.viewmodel.HfFileFilter
import com.dark.tool_neuron.viewmodel.HfFileSizeBucket
import com.dark.tool_neuron.viewmodel.HfRepoDetailState

@Composable
fun HfRepoDetailScreen(
    innerPadding: PaddingValues,
    repoPath: String,
    vm: HfExplorerViewModel = hiltViewModel(),
) {
    val dimens = LocalDimens.current
    val state by vm.detailState.collectAsStateWithLifecycle()
    val filter by vm.fileFilter.collectAsStateWithLifecycle()
    val bucket by vm.fileSizeBucket.collectAsStateWithLifecycle()
    val existing by vm.existingRepoPaths.collectAsStateWithLifecycle()

    LaunchedEffect(repoPath) { vm.loadRepoDetail(repoPath) }

    when (val s = state) {
        HfRepoDetailState.Idle, HfRepoDetailState.Loading -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(dimens.spacingSm),
                ) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                    CaptionText(text = "Loading $repoPath…")
                }
            }
        }
        is HfRepoDetailState.Failed -> FailureView(
            innerPadding = innerPadding,
            error = s.error,
            onRetry = { vm.loadRepoDetail(repoPath) },
        )
        is HfRepoDetailState.Success -> DetailContent(
            innerPadding = innerPadding,
            detail = s.detail,
            readme = s.readme,
            readmeError = s.readmeError,
            filter = filter,
            bucket = bucket,
            isAdded = existing.contains(s.detail.summary.id.lowercase()),
            visibleFiles = vm.visibleFiles(s.detail),
            visibleFileGroups = vm.visibleFileGroups(s.detail),
            onAdd = { vm.addRepository(s.detail.summary.id, s.detail.summary.id.substringAfter("/")) },
            onFilterChange = vm::setFileFilter,
            onBucketChange = vm::setFileSizeBucket,
        )
    }
}

@Composable
private fun FailureView(
    innerPadding: PaddingValues,
    error: HfApiError,
    onRetry: () -> Unit,
) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current
    val (text, retryable) = when (error) {
        is HfApiError.RateLimited -> {
            val sec = error.retryAfterSeconds
            val msg = if (sec != null) "Rate limited — try again in ${formatRetry(sec)}"
            else "Rate limited — try again in a few minutes"
            msg to false
        }
        is HfApiError.NotFound -> "Repository not found" to false
        is HfApiError.Forbidden -> "Access denied" to false
        is HfApiError.Network -> "Network error — check your connection" to true
        is HfApiError.Parse -> "Couldn't read response" to true
        is HfApiError.Http -> "HTTP ${error.status}" to true
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = tnShapes.cardSmall,
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(dimens.spacingSm),
                modifier = Modifier.padding(dimens.cardPadding),
            ) {
                Icon(
                    TnIcons.AlertTriangle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp),
                )
                CaptionText(text = text, color = MaterialTheme.colorScheme.error)
                if (retryable) {
                    ActionTextButton(
                        onClickListener = onRetry,
                        icon = TnIcons.Refresh,
                        text = "Retry",
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailContent(
    innerPadding: PaddingValues,
    detail: HfModelDetail,
    readme: String?,
    readmeError: String?,
    filter: HfFileFilter,
    bucket: HfFileSizeBucket,
    isAdded: Boolean,
    visibleFiles: List<HfSibling>,
    visibleFileGroups: List<VlmFileGroup>,
    onAdd: () -> Unit,
    onFilterChange: (HfFileFilter) -> Unit,
    onBucketChange: (HfFileSizeBucket) -> Unit,
) {
    val dimens = LocalDimens.current
    val markdownColors = rememberMarkdownColors()
    CompositionLocalProvider(LocalMarkdownColors provides markdownColors) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                horizontal = dimens.screenPadding,
                vertical = dimens.spacingMd,
            ),
            verticalArrangement = Arrangement.spacedBy(dimens.spacingSm),
        ) {
            item { HeaderCard(detail = detail, isAdded = isAdded, onAdd = onAdd) }
            if (detail.summary.gated != HfGated.OPEN) {
                item { GatedNotice(detail) }
            }
            detail.ggufMeta?.let { meta -> item { GgufCard(meta) } }
            detail.cardData?.let { card -> item { CardDataView(card) } }
            item {
                FilterCard(
                    filter = filter,
                    bucket = bucket,
                    onFilterChange = onFilterChange,
                    onBucketChange = onBucketChange,
                )
            }
            item {
                SectionHeader(
                    title = "Files · ${visibleFiles.size}",
                    action = {
                        CaptionText(text = "Total " + formatBytes(visibleFiles.sumOf { it.sizeBytes }))
                    },
                )
            }
            if (visibleFileGroups.isNotEmpty()) {
                items(visibleFileGroups, key = { it.key }) { group ->
                    VlmFileGroupRow(group = group)
                }
                val groupedPaths = visibleFileGroups.flatMap { group ->
                    group.baseFiles.map { it.path } + group.projectorFiles.map { it.path }
                }.toSet()
                val remaining = visibleFiles.filterNot { it.path in groupedPaths }
                items(remaining, key = { it.path }) { file ->
                    FileRow(file = file)
                }
            } else {
                items(visibleFiles, key = { it.path }) { file ->
                    FileRow(file = file)
                }
            }
            if (visibleFiles.isEmpty()) {
                item { CaptionText("No files match the current filter.") }
            }
            item { SectionHeader(title = "Model card") }
            when {
                readme != null -> {
                    lazyMarkdownItems(text = readme, keyPrefix = "hf_readme_${detail.summary.id}")
                }
                readmeError != null -> {
                    item { CaptionText("Couldn't load README — $readmeError") }
                }
                else -> {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                            )
                            CaptionText(text = "Loading model card…")
                        }
                    }
                }
            }
            item { Spacer(Modifier.size(dimens.spacingLg)) }
        }
    }
}

@Composable
private fun VlmFileGroupRow(group: VlmFileGroup) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current
    var expanded by remember(group.key) { mutableStateOf(false) }
    val baseCount = group.baseFiles.size
    val projectorCount = group.projectorFiles.size
    Surface(
        shape = tnShapes.cardSmall,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.07f),
        modifier = Modifier.fillMaxWidth(),
        onClick = { expanded = !expanded },
    ) {
        Column(
            modifier = Modifier.padding(dimens.spacingSm),
            verticalArrangement = Arrangement.spacedBy(dimens.spacingXs),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
            ) {
                Icon(
                    imageVector = TnIcons.Eye,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = group.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    CaptionText(
                        text = buildList {
                            add("$baseCount model ${if (baseCount == 1) "file" else "files"}")
                            if (projectorCount > 0) add("$projectorCount projector ${if (projectorCount == 1) "file" else "files"}")
                            if (group.quantizations.isNotEmpty()) add(group.quantizations.joinToString(", "))
                        }.joinToString(" · "),
                    )
                }
                CaptionText(text = formatBytes(group.totalSizeBytes))
                Icon(
                    imageVector = if (expanded) TnIcons.ChevronUp else TnIcons.ChevronDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(dimens.spacingXs)) {
                    group.baseFiles.forEach { file -> FileRow(file) }
                    group.projectorFiles.forEach { file -> FileRow(file) }
                }
            }
        }
    }
}

@Composable
private fun HeaderCard(
    detail: HfModelDetail,
    isAdded: Boolean,
    onAdd: () -> Unit,
) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current
    StandardCard(
        title = detail.summary.id,
        description = detail.summary.author,
        icon = TnIcons.Server,
        trailing = {
            if (isAdded) {
                Box(
                    modifier = Modifier
                        .size(dimens.actionIconSize)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            RoundedCornerShape(50),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        TnIcons.Check,
                        contentDescription = "Already added",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            } else {
                ActionButton(
                    onClickListener = onAdd,
                    icon = TnIcons.Plus,
                    contentDescription = "Add repository",
                )
            }
        },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingMd),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        ) {
            Stat(label = "Downloads", value = formatCount(detail.summary.downloads))
            Stat(label = "Likes", value = formatCount(detail.summary.likes))
            if (detail.totalGgufBytes > 0) {
                Stat(label = "GGUF total", value = formatBytes(detail.totalGgufBytes))
            }
            detail.summary.lastModified?.let { Stat(label = "Updated", value = isoDate(it)) }
            if (detail.summary.gated != HfGated.OPEN) {
                Surface(
                    shape = tnShapes.full,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                ) {
                    Text(
                        if (detail.summary.gated == HfGated.AUTO) "Gated · auto" else "Gated",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = dimens.spacingSm, vertical = dimens.spacingXxs),
                    )
                }
            }
        }
    }
}

@Composable
private fun GatedNotice(detail: HfModelDetail) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current
    val prompt = detail.cardData?.gatedPrompt
    Surface(
        shape = tnShapes.cardSmall,
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(dimens.cardPadding),
            verticalArrangement = Arrangement.spacedBy(dimens.spacingXs),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
            ) {
                Icon(
                    TnIcons.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    "Gated repository",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = "Files require a HuggingFace account and license acceptance. " +
                    "Sign in at huggingface.co, accept the terms, then re-attempt the download.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
            )
            if (!prompt.isNullOrBlank()) {
                CaptionText(text = prompt.take(300))
            }
        }
    }
}

@Composable
private fun GgufCard(meta: HfGgufMeta) {
    val dimens = LocalDimens.current
    StandardCard(
        title = "GGUF",
        icon = TnIcons.Cpu,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingMd),
        ) {
            meta.architecture?.let { Stat(label = "Arch", value = it) }
            meta.contextLength?.let { Stat(label = "Context", value = formatCount(it)) }
            meta.totalBytes?.let { Stat(label = "Total bytes", value = formatBytes(it)) }
            meta.bosToken?.let { Stat(label = "BOS", value = it.take(24)) }
            meta.eosToken?.let { Stat(label = "EOS", value = it.take(24)) }
        }
    }
}

@Composable
private fun CardDataView(card: HfCardData) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current
    StandardCard(
        title = "Metadata",
        icon = TnIcons.Info,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(dimens.spacingXs)) {
            card.license?.let { KvRow(label = "License", value = it) }
            if (card.baseModel.isNotEmpty()) {
                KvRow(label = "Base model", value = card.baseModel.joinToString(", "))
            }
            if (card.languages.isNotEmpty()) {
                KvRow(label = "Languages", value = card.languages.joinToString(", "))
            }
            card.pipelineTag?.let { KvRow(label = "Task", value = it) }
            if (card.tags.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(dimens.spacingXxs),
                ) {
                    card.tags.take(12).forEach { tag ->
                        Surface(
                            shape = tnShapes.full,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        ) {
                            Text(
                                tag,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KvRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(LocalDimens.current.spacingSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CaptionText(text = label, modifier = Modifier.padding(end = 4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        CaptionText(text = label)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun FilterCard(
    filter: HfFileFilter,
    bucket: HfFileSizeBucket,
    onFilterChange: (HfFileFilter) -> Unit,
    onBucketChange: (HfFileSizeBucket) -> Unit,
) {
    val dimens = LocalDimens.current
    StandardCard(
        title = "Filter files",
        icon = TnIcons.Sliders,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(dimens.spacingSm)) {
            CaptionText("File type")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(dimens.spacingXs),
            ) {
                HfFileFilter.entries.forEach { f ->
                    Pill(
                        text = when (f) {
                            HfFileFilter.ALL -> "All"
                            HfFileFilter.GGUF -> "GGUF"
                            HfFileFilter.MMPROJ -> "mmproj"
                        },
                        selected = filter == f,
                        onClick = { onFilterChange(f) },
                    )
                }
            }
            CaptionText("Max size")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(dimens.spacingXs),
            ) {
                HfFileSizeBucket.entries.forEach { b ->
                    Pill(
                        text = b.label,
                        selected = bucket == b,
                        onClick = { onBucketChange(b) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FileRow(file: HfSibling) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current
    val isGguf = file.path.endsWith(".gguf", ignoreCase = true)
    val isMmproj = file.path.contains("mmproj", ignoreCase = true)
    Surface(
        shape = tnShapes.cardSmall,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = dimens.spacingSm,
                vertical = dimens.spacingSm,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
        ) {
            Icon(
                imageVector = if (isMmproj) TnIcons.Eye else if (isGguf) TnIcons.Database else TnIcons.FileText,
                contentDescription = null,
                tint = if (isMmproj) MaterialTheme.colorScheme.tertiary
                else if (isGguf) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Text(
                file.path,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            CaptionText(
                text = if (file.sizeBytes > 0) formatBytes(file.sizeBytes) else "—",
            )
        }
    }
}

private fun isoDate(iso: String): String =
    iso.take(10).ifBlank { iso }

private fun formatRetry(sec: Int): String = when {
    sec >= 60 -> "${sec / 60}m"
    else -> "${sec}s"
}
