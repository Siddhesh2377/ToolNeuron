package com.dark.tool_neuron.ui.screen.model_store

import androidx.compose.animation.AnimatedVisibility
import com.dark.tool_neuron.ui.theme.Motion
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dark.tool_neuron.models.data.ModelCategory
import com.dark.tool_neuron.models.data.ModelType
import com.dark.tool_neuron.ui.components.ActionSwitch
import com.dark.tool_neuron.ui.components.ExpandCollapseIcon
import com.dark.tool_neuron.utils.SizeCategory
import com.dark.tool_neuron.viewmodel.ModelStoreViewModel
import com.dark.tool_neuron.viewmodel.SortOption
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.global.Standards

// ── SearchAppBar ──

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchAppBar(
    searchQuery: String, onSearchQueryChange: (String) -> Unit, onCloseSearch: () -> Unit
) {
    TopAppBar(title = {
        TextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("Search models...") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }, navigationIcon = {
        IconButton(onClick = onCloseSearch) {
            Icon(TnIcons.ArrowLeft, "Close search")
        }
    })
}

// ── ModelFiltersSection ──

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelFiltersSection(
    viewModel: ModelStoreViewModel
) {
    val selectedModelType by viewModel.selectedModelType.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val selectedParameters by viewModel.selectedParameters.collectAsStateWithLifecycle()
    val selectedQuantizations by viewModel.selectedQuantizations.collectAsStateWithLifecycle()
    val selectedSizeCategory by viewModel.selectedSizeCategory.collectAsStateWithLifecycle()
    val selectedTags by viewModel.selectedTags.collectAsStateWithLifecycle()
    val showNsfw by viewModel.showNsfw.collectAsStateWithLifecycle()
    val executionTarget by viewModel.executionTarget.collectAsStateWithLifecycle()
    val sortBy by viewModel.sortBy.collectAsStateWithLifecycle()

    var showAdvancedFilters by remember { mutableStateOf(false) }

    val activeFilterCount = listOf(
        selectedModelType != null,
        selectedCategory != null,
        selectedParameters.isNotEmpty(),
        selectedQuantizations.isNotEmpty(),
        selectedSizeCategory != null,
        selectedTags.isNotEmpty(),
        !showNsfw,
        executionTarget != null
    ).count { it }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Standards.SpacingSm)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Standards.SpacingLg),
            horizontalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
        ) {
            FilterChip(
                selected = selectedModelType == null,
                onClick = { viewModel.filterByModelType(null) },
                label = { Text("All") }
            )
            FilterChip(
                selected = selectedModelType == ModelType.GGUF,
                onClick = { viewModel.filterByModelType(ModelType.GGUF) },
                label = { Text("LLM (GGUF)") }
            )
            FilterChip(
                selected = selectedModelType == ModelType.SD,
                onClick = { viewModel.filterByModelType(ModelType.SD) },
                label = { Text("Image (SD)") }
            )
            FilterChip(
                selected = selectedModelType == ModelType.TTS,
                onClick = { viewModel.filterByModelType(ModelType.TTS) },
                label = { Text("TTS") }
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (selectedModelType == null || selectedModelType == ModelType.GGUF) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = Standards.SpacingLg),
                horizontalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
            ) {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { viewModel.filterByCategory(null) },
                    label = { Text("All") }
                )
                ModelCategory.values().forEach { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { viewModel.filterByCategory(category) },
                        label = { Text(category.displayName) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
        }

        // Tag chips
        val availableTags = remember(viewModel.models.collectAsStateWithLifecycle().value) {
            viewModel.getAvailableTags()
        }
        if (availableTags.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = Standards.SpacingLg),
                horizontalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
            ) {
                availableTags.forEach { tag ->
                    FilterChip(
                        selected = tag in selectedTags,
                        onClick = { viewModel.toggleTagFilter(tag) },
                        label = { Text(tag) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Standards.SpacingLg),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = { showAdvancedFilters = !showAdvancedFilters }
            ) {
                ExpandCollapseIcon(isExpanded = showAdvancedFilters, size = 20.dp)
                Spacer(modifier = Modifier.width(Standards.SpacingXs))
                Text("Advanced Filters")
                if (activeFilterCount > 0) {
                    Spacer(modifier = Modifier.width(Standards.SpacingXs))
                    AssistChip(
                        onClick = {},
                        label = { Text(activeFilterCount.toString()) },
                        modifier = Modifier.height(24.dp)
                    )
                }
            }

            if (activeFilterCount > 0) {
                TextButton(onClick = { viewModel.clearAllFilters() }) {
                    Text("Clear All")
                }
            }
        }

        AnimatedVisibility(
            visible = showAdvancedFilters,
            enter = Motion.Enter,
            exit = Motion.Exit
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Standards.SpacingLg, vertical = Standards.SpacingSm),
                verticalArrangement = Arrangement.spacedBy(Standards.SpacingMd)
            ) {
                // NSFW toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Show NSFW Content",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    ActionSwitch(
                        checked = showNsfw,
                        onCheckedChange = { viewModel.setShowNsfw(it) }
                    )
                }

                // Execution target filter
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Execution",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
                    ) {
                        FilterChip(
                            selected = executionTarget == null,
                            onClick = { viewModel.setExecutionTarget(null) },
                            label = { Text("All") }
                        )
                        FilterChip(
                            selected = executionTarget == "CPU",
                            onClick = { viewModel.setExecutionTarget(if (executionTarget == "CPU") null else "CPU") },
                            label = { Text("CPU") }
                        )
                        FilterChip(
                            selected = executionTarget == "NPU",
                            onClick = { viewModel.setExecutionTarget(if (executionTarget == "NPU") null else "NPU") },
                            label = { Text("NPU") }
                        )
                    }
                }

                if (selectedModelType == null || selectedModelType == ModelType.GGUF) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Parameters",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
                        ) {
                            listOf("0.5B", "1B", "3B", "6.7B", "8B", "32B", "70B").forEach { param ->
                                FilterChip(
                                    selected = param in selectedParameters,
                                    onClick = { viewModel.toggleParameterFilter(param) },
                                    label = { Text(param) }
                                )
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Quantization",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
                        ) {
                            listOf("Q4_0", "Q5_0", "Q8_0", "Q4_K_M", "Q5_K_M", "Q6_K").forEach { quant ->
                                FilterChip(
                                    selected = quant in selectedQuantizations,
                                    onClick = { viewModel.toggleQuantizationFilter(quant) },
                                    label = { Text(quant) }
                                )
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Size",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
                    ) {
                        SizeCategory.entries.forEach { size ->
                            FilterChip(
                                selected = selectedSizeCategory == size,
                                onClick = {
                                    viewModel.filterBySizeCategory(
                                        if (selectedSizeCategory == size) null else size
                                    )
                                },
                                label = { Text(size.displayName) }
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Sort By",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
                    ) {
                        FilterChip(
                            selected = sortBy == SortOption.NAME,
                            onClick = { viewModel.setSortOption(SortOption.NAME) },
                            label = { Text("Name") }
                        )
                        FilterChip(
                            selected = sortBy == SortOption.SIZE,
                            onClick = { viewModel.setSortOption(SortOption.SIZE) },
                            label = { Text("Size") }
                        )
                        FilterChip(
                            selected = sortBy == SortOption.RECENTLY_ADDED,
                            onClick = { viewModel.setSortOption(SortOption.RECENTLY_ADDED) },
                            label = { Text("Recently Added") }
                        )
                    }
                }
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(top = Standards.SpacingSm),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        )
    }
}
