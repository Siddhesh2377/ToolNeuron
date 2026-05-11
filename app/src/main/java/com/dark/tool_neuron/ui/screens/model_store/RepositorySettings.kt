package com.dark.tool_neuron.ui.screens.model_store

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import com.dark.tool_neuron.model.HFRepository
import com.dark.tool_neuron.model.ModelCategory
import com.dark.tool_neuron.model.ui.ActionIcon
import com.dark.tool_neuron.model.ui.ActionItem
import com.dark.tool_neuron.repo.ExplorerRepo
import com.dark.tool_neuron.repo.ValidationResult
import com.dark.tool_neuron.ui.components.ActionButton
import com.dark.tool_neuron.ui.components.ActionSwitch
import com.dark.tool_neuron.ui.components.CaptionText
import com.dark.tool_neuron.ui.components.MultiActionButton
import com.dark.tool_neuron.ui.components.SectionHeader
import com.dark.tool_neuron.ui.components.StandardCard
import com.dark.tool_neuron.ui.components.StatusBadge
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.LocalTnShapes
import com.dark.tool_neuron.ui.theme.Motion
import com.dark.tool_neuron.ui.theme.maple
import com.dark.tool_neuron.viewmodel.ModelStoreViewModel
import kotlinx.coroutines.delay

@Composable
internal fun SettingsTab(
    deviceInfo: Map<String, String>,
    viewModel: ModelStoreViewModel,
    onOpenHfExplorer: () -> Unit,
) {
    val dimens = LocalDimens.current
    val repositories by viewModel.repositories.collectAsStateWithLifecycle()
    val validationResults by viewModel.validationResults.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingRepository by remember { mutableStateOf<HFRepository?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = dimens.spacingLg, vertical = dimens.spacingSm),
        verticalArrangement = Arrangement.spacedBy(dimens.spacingSm)
    ) {
        item { DeviceInfoCard(deviceInfo) }

        item {
            HfExplorerLauncherCard(onOpen = onOpenHfExplorer)
        }

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
        AddRepositoryDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { viewModel.addRepository(it); showAddDialog = false }
        )
    }

    editingRepository?.let { repo ->
        EditRepositoryDialog(
            repository = repo,
            onDismiss = { editingRepository = null },
            onSave = { viewModel.updateRepository(it); editingRepository = null }
        )
    }
}

@Composable
internal fun DeviceInfoCard(deviceInfo: Map<String, String>) {
    var expanded by remember { mutableStateOf(false) }
    val entries = deviceInfo.entries.toList()
    val previewEntries = entries.take(3)
    val remainingEntries = entries.drop(3)
    val dimens = LocalDimens.current

    StandardCard(
        title = "Device Information",
        icon = TnIcons.Prompt,
        trailing = {
            if (remainingEntries.isNotEmpty()) {
                ActionButton(
                    onClickListener = { expanded = !expanded },
                    icon = if (expanded) TnIcons.ChevronUp else TnIcons.ChevronDown,
                    contentDescription = if (expanded) "Collapse" else "Expand"
                )
            }
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(dimens.spacingXs)) {
            previewEntries.forEach { (key, value) ->
                DeviceInfoRow(
                    key.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
                    value
                )
            }
            AnimatedVisibility(visible = expanded, enter = Motion.Enter, exit = Motion.Exit) {
                Column(verticalArrangement = Arrangement.spacedBy(dimens.spacingXs)) {
                    remainingEntries.forEach { (key, value) ->
                        DeviceInfoRow(
                            key.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
                            value
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun DeviceInfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun HfExplorerLauncherCard(onOpen: () -> Unit) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
        shape = tnShapes.card,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = dimens.cardPadding,
                vertical = dimens.cardPadding,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingMd),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(50),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = TnIcons.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "HuggingFace Explorer",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Browse repos, inspect file sizes, add to your sources.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = TnIcons.ArrowRight,
                contentDescription = "Open HF Explorer",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
internal fun ExplorerRepositoriesCard(
    query: String,
    results: List<ExplorerRepo>,
    isLoading: Boolean,
    error: String?,
    existingRepoPaths: Set<String>,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onAdd: (ExplorerRepo) -> Unit
) {
    val dimens = LocalDimens.current
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
        AnimatedVisibility(visible = expanded, enter = Motion.Enter, exit = Motion.Exit) {
            Column(verticalArrangement = Arrangement.spacedBy(dimens.spacingSm)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Search GGUF repositories") },
                    placeholder = { Text("e.g. qwen, mistral, coder") },
                    singleLine = true,
                    trailingIcon = {
                        ActionButton(onClickListener = onSearch, icon = TnIcons.Search, contentDescription = "Search")
                    }
                )

                AnimatedContent(
                    targetState = Triple(isLoading, error, results.size),
                    transitionSpec = { fadeIn(Motion.entrance()) togetherWith fadeOut(Motion.exit()) },
                    label = "explorer_status"
                ) { (loading, err, count) ->
                    when {
                        loading -> {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dimens.spacingXs)) {
                                CircularProgressIndicator(Modifier.size(10.dp), strokeWidth = 1.5.dp, color = MaterialTheme.colorScheme.primary)
                                CaptionText(text = "Searching HuggingFace...")
                            }
                        }
                        !err.isNullOrBlank() -> CaptionText(text = err, color = MaterialTheme.colorScheme.error)
                        count > 0 -> CaptionText(text = "$count result${if (count != 1) "s" else ""} found")
                        else -> Spacer(Modifier.height(0.dp))
                    }
                }

                val displayedResults = results.take(8)
                displayedResults.forEachIndexed { index, repo ->
                    val isAdded = existingRepoPaths.contains(repo.id.lowercase())
                    var visible by remember(repo.id) { mutableStateOf(false) }
                    LaunchedEffect(repo.id) { delay(index * 60L); visible = true }

                    AnimatedVisibility(
                        visible = visible,
                        enter = slideInVertically(initialOffsetY = { it / 2 }, animationSpec = Motion.content()) + fadeIn(Motion.content())
                    ) {
                        Column {
                            ExplorerResultRow(repo, isAdded) { onAdd(repo) }
                            if (index < displayedResults.lastIndex) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ExplorerResultRow(
    repo: ExplorerRepo,
    isAdded: Boolean,
    onAdd: () -> Unit
) {
    val dimens = LocalDimens.current
    val shapes = LocalTnShapes.current

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = shapes.cardSmall
    ) {
        Row(
            modifier = Modifier.padding(dimens.cardPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(repo.id, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(dimens.spacingXs), verticalAlignment = Alignment.CenterVertically) {
                    CaptionText(text = "${repo.downloads} downloads")
                    CaptionText(text = "\u00B7")
                    CaptionText(text = "${repo.likes} likes")
                    if (repo.gated) {
                        CaptionText(text = "\u00B7")
                        StatusBadge(text = "Gated", isActive = true)
                    }
                }
            }

            if (isAdded) {
                Icon(TnIcons.CircleCheck, "Added", tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), modifier = Modifier.size(dimens.actionIconSize))
            } else {
                ActionButton(onClickListener = onAdd, icon = TnIcons.Plus, contentDescription = "Add repository")
            }
        }
    }
}

@Composable
internal fun RepositoryCard(
    repository: HFRepository,
    validationResult: ValidationResult?,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onValidate: () -> Unit,
    onDelete: () -> Unit
) {
    val dimens = LocalDimens.current
    val shapes = LocalTnShapes.current

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = shapes.cardSmall,
        onClick = onValidate
    ) {
        Column(
            modifier = Modifier.padding(dimens.cardPadding),
            verticalArrangement = Arrangement.spacedBy(dimens.spacingXs)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm)
            ) {
                val dotColor = when (validationResult) {
                    is ValidationResult.Valid -> MaterialTheme.colorScheme.primary
                    is ValidationResult.Invalid -> MaterialTheme.colorScheme.error
                    is ValidationResult.Checking -> MaterialTheme.colorScheme.tertiary
                    null -> MaterialTheme.colorScheme.outlineVariant
                }
                if (validationResult is ValidationResult.Checking) {
                    CircularProgressIndicator(Modifier.size(10.dp), color = dotColor, strokeWidth = 1.5.dp)
                } else {
                    Box(Modifier.size(6.dp).background(dotColor, RoundedCornerShape(50)))
                }

                Text(
                    repository.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (repository.isEnabled) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )

                MultiActionButton(
                    actions = listOf(
                        ActionItem(ActionIcon.Vector(TnIcons.Edit), onEdit, "Edit"),
                        ActionItem(ActionIcon.Vector(TnIcons.Trash), onDelete, "Delete")
                    )
                )

                ActionSwitch(checked = repository.isEnabled, onCheckedChange = { onToggle() })
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(dimens.spacingXs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    repository.repoPath,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = maple,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                CaptionText(text = "\u00B7")
                CaptionText(text = repository.category.displayName)
                if (validationResult is ValidationResult.Valid) {
                    CaptionText(text = "\u00B7")
                    CaptionText(text = "${validationResult.ggufFileCount} GGUF", color = MaterialTheme.colorScheme.primary)
                } else if (validationResult is ValidationResult.Invalid) {
                    CaptionText(text = "\u00B7")
                    CaptionText(text = validationResult.reason, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
internal fun AddRepositoryDialog(
    onDismiss: () -> Unit,
    onAdd: (HFRepository) -> Unit
) {
    val dimens = LocalDimens.current
    var repoName by remember { mutableStateOf("") }
    var repoPath by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Repository") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(dimens.spacingMd)) {
                OutlinedTextField(value = repoName, onValueChange = { repoName = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = repoPath, onValueChange = { repoPath = it }, label = { Text("Repository Path") }, placeholder = { Text("username/repo-name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (repoName.isNotBlank() && repoPath.isNotBlank()) {
                        onAdd(HFRepository(id = repoPath.replace("/", "-"), name = repoName, repoPath = repoPath))
                    }
                },
                enabled = repoName.isNotBlank() && repoPath.isNotBlank()
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
internal fun EditRepositoryDialog(
    repository: HFRepository,
    onDismiss: () -> Unit,
    onSave: (HFRepository) -> Unit
) {
    val dimens = LocalDimens.current
    var repoName by remember { mutableStateOf(repository.name) }
    var repoPath by remember { mutableStateOf(repository.repoPath) }
    var selectedCategory by remember { mutableStateOf(repository.category) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Repository") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(dimens.spacingMd)) {
                OutlinedTextField(value = repoName, onValueChange = { repoName = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = repoPath, onValueChange = { repoPath = it }, label = { Text("Repository Path") }, singleLine = true, modifier = Modifier.fillMaxWidth())

                Text("Category", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(
                    verticalArrangement = Arrangement.spacedBy(dimens.spacingSm),
                    modifier = Modifier.scrollable(rememberScrollState(), orientation = Orientation.Horizontal)
                ) {
                    val categories = ModelCategory.entries.toList()
                    categories.chunked(2).forEach { row ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm)) {
                            row.forEach { cat ->
                                FilterChip(
                                    selected = selectedCategory == cat,
                                    onClick = { selectedCategory = cat },
                                    label = { Text(cat.displayName) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (repoName.isNotBlank() && repoPath.isNotBlank()) {
                        onSave(repository.copy(name = repoName, repoPath = repoPath, category = selectedCategory))
                    }
                },
                enabled = repoName.isNotBlank() && repoPath.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

