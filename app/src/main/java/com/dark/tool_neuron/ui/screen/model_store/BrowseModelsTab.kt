package com.dark.tool_neuron.ui.screen.model_store

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.ScrollableDefaults
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dark.tool_neuron.models.data.HuggingFaceModel
import com.dark.tool_neuron.service.ModelDownloadService
import com.dark.tool_neuron.global.Standards
import com.dark.tool_neuron.ui.components.ActionButton
import com.dark.tool_neuron.ui.components.CaptionText
import com.dark.tool_neuron.ui.theme.Motion
import com.dark.tool_neuron.viewmodel.ModelStoreViewModel
import com.dark.tool_neuron.viewmodel.RepoGroupInfo
import com.dark.tool_neuron.ui.icons.TnIcons

// ── ModelsTab ──

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ModelsTab(
    models: List<HuggingFaceModel>,
    isLoading: Boolean,
    error: String?,
    downloadStates: Map<String, ModelDownloadService.DownloadState>,
    installedModelIds: Set<String>,
    viewModel: ModelStoreViewModel,
    onDownload: (HuggingFaceModel) -> Unit,
    onCancelDownload: (String) -> Unit,
    onRetry: () -> Unit
) {
    val selectedRepo by viewModel.selectedRepository.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        ModelFiltersSection(viewModel = viewModel)

        when {
            isLoading && models.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator()
                }
            }

            error != null && models.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Standards.SpacingLg)
                    ) {
                        Icon(
                            imageVector = TnIcons.AlertTriangle,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "Error loading models",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(onClick = onRetry) {
                            Text("Retry")
                        }
                    }
                }
            }

            models.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Standards.SpacingMd)
                    ) {
                        Icon(
                            imageVector = TnIcons.SearchOff,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "No models found",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            else -> {
                AnimatedContent(
                    targetState = selectedRepo,
                    transitionSpec = {
                        fadeIn(Motion.state()) togetherWith
                                fadeOut(Motion.state())
                    },
                    label = "repo_nav"
                ) { repoKey ->
                    if (repoKey == null) {
                        // Repo card list view
                        RepoCardListView(
                            viewModel = viewModel,
                            isLoading = isLoading,
                            downloadStates = downloadStates
                        )
                    } else {
                        // Model detail view inside a repo
                        RepoDetailView(
                            repoKey = repoKey,
                            viewModel = viewModel,
                            isLoading = isLoading,
                            downloadStates = downloadStates,
                            installedModelIds = installedModelIds,
                            onDownload = onDownload,
                            onCancelDownload = onCancelDownload
                        )
                    }
                }
            }
        }
    }

    // Handle back press to return from detail to repo list
    if (selectedRepo != null) {
        BackHandler {
            viewModel.selectRepository(null)
        }
    }
}

// ── RepoCardListView ──

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun RepoCardListView(
    viewModel: ModelStoreViewModel,
    isLoading: Boolean,
    downloadStates: Map<String, ModelDownloadService.DownloadState>
) {
    val groupedRepos = remember(viewModel.filteredModels.collectAsStateWithLifecycle().value) {
        viewModel.getGroupedRepos()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .then(if (isLoading) Modifier.blur(4.dp) else Modifier),
            contentPadding = PaddingValues(horizontal = Standards.SpacingMd, vertical = Standards.SpacingSm),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            flingBehavior = ScrollableDefaults.flingBehavior()
        ) {
            items(
                items = groupedRepos.entries.toList(),
                key = { it.key }
            ) { (repoKey, info) ->
                val repoModels = remember(groupedRepos, repoKey) { viewModel.getModelsForRepo(repoKey) }
                val hasActiveDownload = repoModels.any { model ->
                    val state = downloadStates[model.id]
                    state is ModelDownloadService.DownloadState.Downloading ||
                            state is ModelDownloadService.DownloadState.Extracting ||
                            state is ModelDownloadService.DownloadState.Processing
                }

                StoreRepoCard(
                    info = info,
                    hasActiveDownload = hasActiveDownload,
                    onClick = { viewModel.selectRepository(repoKey) }
                )
            }
        }

        if (isLoading) {
            LoadingIndicator(modifier = Modifier.align(Alignment.Center))
        }
    }
}

// ── StoreRepoCard ──

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun StoreRepoCard(
    info: RepoGroupInfo,
    hasActiveDownload: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(Standards.CardSmallCornerRadius),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(Standards.CardPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
        ) {
            ModelTypeBadge(info.modelType)

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = info.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Standards.SpacingXs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (info.author.isNotEmpty()) {
                        CaptionText(text = info.author)
                        CaptionText(text = "·")
                    }
                    CaptionText(
                        text = "${info.modelCount} ${if (info.modelCount == 1) "model" else "models"}"
                    )
                    if (hasActiveDownload) {
                        CaptionText(text = "·")
                        LoadingIndicator(
                            modifier = Modifier.size(10.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Icon(
                imageVector = TnIcons.ArrowRight,
                contentDescription = "View models",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── RepoDetailView ──

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun RepoDetailView(
    repoKey: String,
    viewModel: ModelStoreViewModel,
    isLoading: Boolean,
    downloadStates: Map<String, ModelDownloadService.DownloadState>,
    installedModelIds: Set<String>,
    onDownload: (HuggingFaceModel) -> Unit,
    onCancelDownload: (String) -> Unit
) {
    val repoModels = remember(viewModel.filteredModels.collectAsStateWithLifecycle().value, repoKey) {
        viewModel.getModelsForRepo(repoKey)
    }
    val groupedRepos = remember(viewModel.filteredModels.collectAsStateWithLifecycle().value) {
        viewModel.getGroupedRepos()
    }
    val repoInfo = groupedRepos[repoKey]

    Column(modifier = Modifier.fillMaxSize()) {
        // Back header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Standards.SpacingSm, vertical = Standards.SpacingXs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Standards.SpacingXs)
        ) {
            ActionButton(
                onClickListener = { viewModel.selectRepository(null) },
                icon = TnIcons.ArrowLeft,
                contentDescription = "Back to repos"
            )
            repoInfo?.let { info ->
                ModelTypeBadge(info.modelType)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = info.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (info.author.isNotEmpty()) {
                        CaptionText(text = info.author)
                    }
                }
                CaptionText(text = "${info.modelCount} models")
            }
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        )

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (isLoading) Modifier.blur(4.dp) else Modifier),
                contentPadding = PaddingValues(horizontal = Standards.SpacingMd, vertical = Standards.SpacingSm),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                flingBehavior = ScrollableDefaults.flingBehavior()
            ) {
                items(
                    items = repoModels,
                    key = { model -> model.id }
                ) { model ->
                    ModelCard(
                        model = model,
                        isInstalled = installedModelIds.contains(model.id),
                        downloadState = downloadStates[model.id],
                        onDownload = { onDownload(model) },
                        onCancelDownload = { onCancelDownload(model.id) }
                    )
                }
            }

            if (isLoading) {
                LoadingIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}
