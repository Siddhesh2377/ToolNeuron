package com.dark.tool_neuron.ui.screen.model_store

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import com.dark.tool_neuron.ui.theme.Motion
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dark.tool_neuron.global.Standards
import com.dark.tool_neuron.models.data.HFModelRepository
import com.dark.tool_neuron.models.data.ModelCategory
import com.dark.tool_neuron.models.data.ModelType
import com.dark.tool_neuron.models.ui.ActionIcon
import com.dark.tool_neuron.models.ui.ActionItem
import com.dark.tool_neuron.repo.HuggingFaceExplorerRepo
import com.dark.tool_neuron.repo.ValidationResult
import com.dark.tool_neuron.ui.components.ActionButton
import com.dark.tool_neuron.ui.components.ActionSwitch
import com.dark.tool_neuron.ui.components.CaptionText
import com.dark.tool_neuron.ui.components.MultiActionButton
import com.dark.tool_neuron.ui.components.SectionHeader
import com.dark.tool_neuron.ui.components.StandardCard
import com.dark.tool_neuron.ui.components.StatusBadge
import com.dark.tool_neuron.ui.theme.maple
import com.dark.tool_neuron.viewmodel.ModelStoreViewModel
import com.dark.tool_neuron.ui.icons.TnIcons
import kotlinx.coroutines.delay

// ── SettingsTab ──

@Composable
internal fun SettingsTab(
    deviceInfo: Map<String, String>, viewModel: ModelStoreViewModel
) {
    val repositories by viewModel.repositories.collectAsStateWithLifecycle(emptyList())
    val validationResults by viewModel.validationResults.collectAsStateWithLifecycle()
    val explorerQuery by viewModel.explorerQuery.collectAsStateWithLifecycle()
    val explorerResults by viewModel.explorerResults.collectAsStateWithLifecycle()
    val isExplorerLoading by viewModel.isExplorerLoading.collectAsStateWithLifecycle()
    val explorerError by viewModel.explorerError.collectAsStateWithLifecycle()
    val existingRepoPaths = repositories.map { it.repoPath.lowercase() }.toSet()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingRepository by remember { mutableStateOf<HFModelRepository?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Standards.SpacingLg, vertical = Standards.SpacingSm),
        verticalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
    ) {
        // Device Info Section
        item {
            DeviceInfoCard(deviceInfo)
        }

        item {
            ExplorerRepositoriesCard(
                query = explorerQuery,
                results = explorerResults,
                isLoading = isExplorerLoading,
                error = explorerError,
                existingRepoPaths = existingRepoPaths,
                onQueryChange = viewModel::setExplorerQuery,
                onSearch = viewModel::searchExplorerRepositories,
                onAdd = viewModel::addExplorerRepository
            )
        }

        // Repositories Section
        item {
            SectionHeader(
                title = "Model Repositories",
                action = {
                    ActionButton(
                        onClickListener = { showAddDialog = true },
                        icon = TnIcons.Plus,
                        contentDescription = "Add Repository"
                    )
                }
            )
        }

        items(repositories, key = { it.id }) { repo ->
            RepositoryCard(
                repository = repo,
                validationResult = validationResults[repo.id],
                onToggle = { viewModel.toggleRepository(repo.id) },
                onEdit = { editingRepository = repo },
                onValidate = { viewModel.validateRepository(repo) },
                onDelete = { viewModel.removeRepository(repo.id) }
            )
        }
    }

    if (showAddDialog) {
        AddRepositoryDialog(onDismiss = { showAddDialog = false }, onAdd = { repo ->
            viewModel.addRepository(repo)
            showAddDialog = false
        })
    }

    editingRepository?.let { repo ->
        EditRepositoryDialog(
            repository = repo,
            onDismiss = { editingRepository = null },
            onSave = { updatedRepo ->
                viewModel.updateRepository(updatedRepo)
                editingRepository = null
            }
        )
    }
}

// ── ExplorerRepositoriesCard ──

@Composable
internal fun ExplorerRepositoriesCard(
    query: String,
    results: List<HuggingFaceExplorerRepo>,
    isLoading: Boolean,
    error: String?,
    existingRepoPaths: Set<String>,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onAdd: (HuggingFaceExplorerRepo) -> Unit
) {
    var expanded by remember { mutableStateOf(true) }

    StandardCard(
        title = "HuggingFace GGUF Explorer",
        icon = TnIcons.Search,
        trailing = {
            ActionButton(
                onClickListener = { expanded = !expanded },
                icon = if (expanded) TnIcons.ChevronUp else TnIcons.ChevronDown,
                contentDescription = if (expanded) "Collapse" else "Expand"
            )
        }
    ) {
        AnimatedVisibility(
            visible = expanded,
            enter = Motion.Enter,
            exit = Motion.Exit
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Standards.SpacingSm)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Search GGUF repositories") },
                    placeholder = { Text("e.g. qwen, mistral, coder") },
                    singleLine = true,
                    trailingIcon = {
                        ActionButton(
                            onClickListener = onSearch,
                            icon = TnIcons.Search,
                            contentDescription = "Search"
                        )
                    }
                )

                // ── Status Row ──
                AnimatedContent(
                    targetState = Triple(isLoading, error, results.size),
                    transitionSpec = {
                        fadeIn(Motion.entrance()) togetherWith fadeOut(Motion.exit())
                    },
                    label = "explorer_status"
                ) { (loading, err, count) ->
                    when {
                        loading -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Standards.SpacingXs)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(10.dp),
                                    strokeWidth = 1.5.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                CaptionText(text = "Searching HuggingFace...")
                            }
                        }
                        !err.isNullOrBlank() -> {
                            CaptionText(
                                text = err,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        count > 0 -> {
                            CaptionText(text = "$count result${if (count != 1) "s" else ""} found")
                        }
                        else -> Spacer(modifier = Modifier.height(0.dp))
                    }
                }

                // ── Results ──
                val displayedResults = results.take(8)
                displayedResults.forEachIndexed { index, repo ->
                    val isAdded = existingRepoPaths.contains(repo.id.lowercase())

                    var visible by remember(repo.id) { mutableStateOf(false) }
                    LaunchedEffect(repo.id) {
                        delay(index * 60L)
                        visible = true
                    }

                    AnimatedVisibility(
                        visible = visible,
                        enter = slideInVertically(
                            initialOffsetY = { it / 2 },
                            animationSpec = Motion.content()
                        ) + fadeIn(Motion.content())
                    ) {
                        Column {
                            ExplorerResultRow(
                                repo = repo,
                                isAdded = isAdded,
                                onAdd = { onAdd(repo) }
                            )
                            if (index < displayedResults.lastIndex) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── ExplorerResultRow ──

@Composable
internal fun ExplorerResultRow(
    repo: HuggingFaceExplorerRepo,
    isAdded: Boolean,
    onAdd: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(Standards.CardSmallCornerRadius)
    ) {
        Row(
            modifier = Modifier.padding(Standards.CardPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = repo.id,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Standards.SpacingXs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CaptionText(text = "${repo.downloads} downloads")
                    CaptionText(text = "·")
                    CaptionText(text = "${repo.likes} likes")
                    if (repo.gated) {
                        CaptionText(text = "·")
                        StatusBadge(text = "Gated", isActive = true)
                    }
                }
            }

            if (isAdded) {
                Icon(
                    imageVector = TnIcons.CircleCheck,
                    contentDescription = "Added",
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                    modifier = Modifier.size(Standards.ActionIconSize)
                )
            } else {
                ActionButton(
                    onClickListener = onAdd,
                    icon = TnIcons.Plus,
                    contentDescription = "Add repository"
                )
            }
        }
    }
}

// ── RepositoryCard ──

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun RepositoryCard(
    repository: HFModelRepository,
    validationResult: ValidationResult?,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onValidate: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(Standards.CardSmallCornerRadius),
        onClick = onValidate
    ) {
        Column(
            modifier = Modifier.padding(Standards.CardPadding),
            verticalArrangement = Arrangement.spacedBy(Standards.SpacingXs)
        ) {
            // Row 1: validation dot + name + actions + switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
            ) {
                // Validation status dot
                val dotColor = when (validationResult) {
                    is ValidationResult.Valid -> MaterialTheme.colorScheme.primary
                    is ValidationResult.Invalid -> MaterialTheme.colorScheme.error
                    is ValidationResult.Checking -> MaterialTheme.colorScheme.tertiary
                    null -> MaterialTheme.colorScheme.outlineVariant
                }
                if (validationResult is ValidationResult.Checking) {
                    LoadingIndicator(
                        modifier = Modifier.size(10.dp),
                        color = dotColor
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(dotColor, RoundedCornerShape(50))
                    )
                }

                // Repo name
                Text(
                    text = repository.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (repository.isEnabled) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )

                // Grouped Edit + Delete
                MultiActionButton(
                    actions = listOf(
                        ActionItem(
                            icon = ActionIcon.Vector(TnIcons.Edit),
                            onClick = onEdit,
                            contentDescription = "Edit"
                        ),
                        ActionItem(
                            icon = ActionIcon.Vector(TnIcons.Trash),
                            onClick = onDelete,
                            contentDescription = "Delete"
                        )
                    )
                )

                // Toggle
                ActionSwitch(
                    checked = repository.isEnabled,
                    onCheckedChange = { onToggle() }
                )
            }

            // Row 2: repo path + category + GGUF count (inline)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Standards.SpacingXs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = repository.repoPath,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = maple,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                CaptionText(text = "·")
                CaptionText(text = repository.category.displayName)

                if (validationResult is ValidationResult.Valid) {
                    CaptionText(text = "·")
                    CaptionText(
                        text = "${validationResult.ggufFileCount} ${validationResult.label}",
                        color = MaterialTheme.colorScheme.primary
                    )
                } else if (validationResult is ValidationResult.Invalid) {
                    CaptionText(text = "·")
                    CaptionText(
                        text = validationResult.reason,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

// ── AddRepositoryDialog ──

@Composable
internal fun AddRepositoryDialog(
    onDismiss: () -> Unit, onAdd: (HFModelRepository) -> Unit
) {
    var repoName by remember { mutableStateOf("") }
    var repoPath by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(ModelType.GGUF) }

    AlertDialog(onDismissRequest = onDismiss, title = { Text("Add Repository") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(Standards.SpacingMd)) {
            OutlinedTextField(
                value = repoName,
                onValueChange = { repoName = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = repoPath,
                onValueChange = { repoPath = it },
                label = { Text("Repository Path") },
                placeholder = { Text("username/repo-name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
            ) {
                FilterChip(
                    selected = selectedType == ModelType.GGUF,
                    onClick = { selectedType = ModelType.GGUF },
                    label = { Text("GGUF") })
                FilterChip(
                    selected = selectedType == ModelType.SD,
                    onClick = { selectedType = ModelType.SD },
                    label = { Text("Stable Diffusion") })
            }
        }
    }, confirmButton = {
        Button(
            onClick = {
                if (repoName.isNotBlank() && repoPath.isNotBlank()) {
                    onAdd(
                        HFModelRepository(
                            id = repoPath.replace("/", "-"),
                            name = repoName,
                            repoPath = repoPath,
                            modelType = selectedType
                        )
                    )
                }
            }, enabled = repoName.isNotBlank() && repoPath.isNotBlank()
        ) {
            Text("Add")
        }
    }, dismissButton = {
        TextButton(onClick = onDismiss) {
            Text("Cancel")
        }
    })
}

// ── EditRepositoryDialog ──

@Composable
internal fun EditRepositoryDialog(
    repository: HFModelRepository,
    onDismiss: () -> Unit,
    onSave: (HFModelRepository) -> Unit
) {
    var repoName by remember { mutableStateOf(repository.name) }
    var repoPath by remember { mutableStateOf(repository.repoPath) }
    var selectedType by remember { mutableStateOf(repository.modelType) }
    var selectedCategory by remember { mutableStateOf(repository.category) }
    var showCategoryDropdown by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Repository") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Standards.SpacingMd)) {
                OutlinedTextField(
                    value = repoName,
                    onValueChange = { repoName = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = repoPath,
                    onValueChange = { repoPath = it },
                    label = { Text("Repository Path") },
                    placeholder = { Text("username/repo-name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Model Type
                Text(
                    text = "Model Type",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
                ) {
                    FilterChip(
                        selected = selectedType == ModelType.GGUF,
                        onClick = { selectedType = ModelType.GGUF },
                        label = { Text("GGUF") }
                    )
                    FilterChip(
                        selected = selectedType == ModelType.SD,
                        onClick = { selectedType = ModelType.SD },
                        label = { Text("Stable Diffusion") }
                    )
                }

                // Category Dropdown
                Text(
                    text = "Category",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Category chips displayed as a grid
                Column(verticalArrangement = Arrangement.spacedBy(Standards.SpacingSm), modifier = Modifier.scrollable(rememberScrollState(), orientation = Orientation.Horizontal)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
                    ) {
                        FilterChip(
                            selected = selectedCategory == ModelCategory.GENERAL,
                            onClick = { selectedCategory = ModelCategory.GENERAL },
                            label = { Text("General") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = selectedCategory == ModelCategory.MEDICAL,
                            onClick = { selectedCategory = ModelCategory.MEDICAL },
                            label = { Text("Medical") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
                    ) {
                        FilterChip(
                            selected = selectedCategory == ModelCategory.RESEARCH,
                            onClick = { selectedCategory = ModelCategory.RESEARCH },
                            label = { Text("Research") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = selectedCategory == ModelCategory.CODING,
                            onClick = { selectedCategory = ModelCategory.CODING },
                            label = { Text("Coding") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
                    ) {
                        FilterChip(
                            selected = selectedCategory == ModelCategory.UNCENSORED,
                            onClick = { selectedCategory = ModelCategory.UNCENSORED },
                            label = { Text("Uncensored") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = selectedCategory == ModelCategory.BUSINESS,
                            onClick = { selectedCategory = ModelCategory.BUSINESS },
                            label = { Text("Business") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
                    ) {
                        FilterChip(
                            selected = selectedCategory == ModelCategory.CYBERSECURITY,
                            onClick = { selectedCategory = ModelCategory.CYBERSECURITY },
                            label = { Text("Cybersecurity") },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (repoName.isNotBlank() && repoPath.isNotBlank()) {
                        onSave(
                            repository.copy(
                                name = repoName,
                                repoPath = repoPath,
                                modelType = selectedType,
                                category = selectedCategory
                            )
                        )
                    }
                },
                enabled = repoName.isNotBlank() && repoPath.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
