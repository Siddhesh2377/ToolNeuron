package com.dark.tool_neuron.ui.screens.model_store

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dark.download_manager.HxdState
import com.dark.download_manager.HxdStatus
import com.dark.tool_neuron.model.HuggingFaceModel
import com.dark.tool_neuron.model.ModelInstallProgress
import com.dark.tool_neuron.ui.components.ActionButton
import com.dark.tool_neuron.ui.components.CaptionText
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.LocalTnShapes
import com.dark.tool_neuron.ui.theme.Motion
import com.dark.tool_neuron.viewmodel.ModelStoreViewModel
import com.dark.tool_neuron.viewmodel.RepoGroupInfo

@Composable
internal fun BrowseModelsTab(
    models: List<HuggingFaceModel>,
    isLoading: Boolean,
    error: String?,
    downloadStates: Map<String, HxdState>,
    installStates: Map<String, ModelInstallProgress>,
    extractingIds: Set<String>,
    extractingFile: Map<String, String>,
    installedModelIds: Set<String>,
    viewModel: ModelStoreViewModel,
    onDownload: (HuggingFaceModel) -> Unit,
    onCancelDownload: (String) -> Unit,
    onRetry: () -> Unit
) {
    val dimens = LocalDimens.current
    val selectedRepo by viewModel.selectedRepository.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        ModelFiltersSection(viewModel = viewModel)

        when {
            isLoading && models.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(32.dp))
                }
            }
            error != null && models.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(dimens.spacingLg)
                    ) {
                        Icon(TnIcons.AlertTriangle, null, Modifier.size(48.dp), MaterialTheme.colorScheme.error)
                        Text("Error loading models", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                        Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = onRetry) { Text("Retry") }
                    }
                }
            }
            models.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(dimens.spacingMd)
                    ) {
                        Icon(TnIcons.SearchOff, null, Modifier.size(48.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("No models found", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            else -> {
                AnimatedContent(
                    targetState = selectedRepo,
                    transitionSpec = { fadeIn(Motion.state()) togetherWith fadeOut(Motion.state()) },
                    label = "repo_nav"
                ) { repoKey ->
                    if (repoKey == null) {
                        RepoCardListView(viewModel, isLoading, downloadStates, installStates)
                    } else {
                        RepoDetailView(repoKey, viewModel, isLoading, downloadStates, installStates, extractingIds, extractingFile, installedModelIds, onDownload, onCancelDownload)
                    }
                }
            }
        }
    }

    if (selectedRepo != null) {
        BackHandler { viewModel.selectRepository(null) }
    }
}

@Composable
internal fun RepoCardListView(
    viewModel: ModelStoreViewModel,
    isLoading: Boolean,
    downloadStates: Map<String, HxdState>,
    installStates: Map<String, ModelInstallProgress>,
) {
    val dimens = LocalDimens.current
    val filteredModels by viewModel.filteredModels.collectAsStateWithLifecycle()
    val groupedRepos = remember(filteredModels) { viewModel.getGroupedRepos() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = dimens.spacingMd, vertical = dimens.spacingSm),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(groupedRepos.entries.toList(), key = { it.key }) { (repoKey, info) ->
            val repoModels = remember(filteredModels, repoKey) { viewModel.getModelsForRepo(repoKey) }
            val hasActiveDownload = repoModels.any { model ->
                val state = downloadStates[model.id]
                val install = installStates[model.id]
                (state != null && state.status in listOf(HxdStatus.QUEUED, HxdStatus.CONNECTING, HxdStatus.DOWNLOADING)) ||
                    install?.isActive == true
            }

            StoreRepoCard(info, hasActiveDownload) { viewModel.selectRepository(repoKey) }
        }
    }
}

@Composable
internal fun StoreRepoCard(
    info: RepoGroupInfo,
    hasActiveDownload: Boolean,
    onClick: () -> Unit
) {
    val dimens = LocalDimens.current
    val shapes = LocalTnShapes.current

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = shapes.cardSmall,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(dimens.cardPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm)
        ) {
            Icon(TnIcons.Sparkles, null, Modifier.size(dimens.iconMd), MaterialTheme.colorScheme.primary)

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    info.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(dimens.spacingXs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (info.author.isNotEmpty()) {
                        CaptionText(text = info.author)
                        CaptionText(text = "\u00B7")
                    }
                    CaptionText(text = "${info.modelCount} ${if (info.modelCount == 1) "model" else "models"}")
                    if (hasActiveDownload) {
                        CaptionText(text = "\u00B7")
                        CircularProgressIndicator(Modifier.size(10.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 1.5.dp)
                    }
                }
            }

            Icon(TnIcons.ArrowRight, "View models", Modifier.size(20.dp), MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun RepoDetailView(
    repoKey: String,
    viewModel: ModelStoreViewModel,
    isLoading: Boolean,
    downloadStates: Map<String, HxdState>,
    installStates: Map<String, ModelInstallProgress>,
    extractingIds: Set<String>,
    extractingFile: Map<String, String>,
    installedModelIds: Set<String>,
    onDownload: (HuggingFaceModel) -> Unit,
    onCancelDownload: (String) -> Unit
) {
    val dimens = LocalDimens.current
    val filteredModels by viewModel.filteredModels.collectAsStateWithLifecycle()
    val taskGroups = remember(filteredModels, repoKey) { viewModel.getTaskGroupsForRepo(repoKey) }
    val groupedRepos = remember(filteredModels) { viewModel.getGroupedRepos() }
    val repoInfo = groupedRepos[repoKey]

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dimens.spacingSm, vertical = dimens.spacingXs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingXs)
        ) {
            ActionButton(
                onClickListener = { viewModel.selectRepository(null) },
                icon = TnIcons.ArrowLeft,
                contentDescription = "Back to repos"
            )
            repoInfo?.let { info ->
                Icon(TnIcons.Sparkles, null, Modifier.size(dimens.iconMd), MaterialTheme.colorScheme.primary)
                Column(modifier = Modifier.weight(1f)) {
                    Text(info.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (info.author.isNotEmpty()) CaptionText(text = info.author)
                }
                CaptionText(text = "${info.modelCount} models")
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = dimens.spacingMd, vertical = dimens.spacingSm),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            taskGroups.forEach { (taskName, modelsForTask) ->
                item(key = "task_$repoKey$taskName") {
                    TaskSectionHeader(
                        title = taskName,
                        count = modelsForTask.size,
                    )
                }
                items(modelsForTask, key = { it.id }) { model ->
                    CatalogModelCard(
                        model = model,
                        isInstalled = model.id in installedModelIds,
                        downloadState = downloadStates[model.id],
                        installProgress = installStates[model.id],
                        isExtracting = model.id in extractingIds,
                        extractingEntryName = extractingFile[model.id],
                        onDownload = { onDownload(model) },
                        onCancel = { onCancelDownload(model.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskSectionHeader(
    title: String,
    count: Int,
) {
    val dimens = LocalDimens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = dimens.spacingSm, bottom = dimens.spacingXs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.spacingXs),
    ) {
        Icon(
            imageVector = TnIcons.ArrowRight,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        CaptionText(text = "$count")
    }
}
