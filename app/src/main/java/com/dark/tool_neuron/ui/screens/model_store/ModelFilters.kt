package com.dark.tool_neuron.ui.screens.model_store

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dark.tool_neuron.model.ModelCategory
import com.dark.tool_neuron.model.SizeCategory
import com.dark.tool_neuron.ui.components.ActionSwitch
import com.dark.tool_neuron.ui.components.ActionToggleGroup
import com.dark.tool_neuron.ui.components.ExpandCollapseIcon
import com.dark.tool_neuron.ui.components.TnTextField
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.Motion
import com.dark.tool_neuron.viewmodel.ModelStoreViewModel
import com.dark.tool_neuron.viewmodel.SortOption

private data class FilterOption<T>(val label: String, val value: T)

private val MODEL_TYPE_OPTIONS: List<FilterOption<String?>> = listOf(
    FilterOption("All", null),
    FilterOption("Text", "gguf"),
    FilterOption("VLM", "vlm"),
    FilterOption("Embed", "embedding"),
    FilterOption("Image", "image_gen"),
    FilterOption("Upscale", "image_upscaler"),
    FilterOption("TTS", "tts"),
    FilterOption("STT", "stt"),
)

private val EXECUTION_OPTIONS: List<FilterOption<String?>> = listOf(
    FilterOption("All", null),
    FilterOption("CPU", "CPU"),
    FilterOption("NPU", "NPU"),
)

private val SIZE_OPTIONS = listOf<FilterOption<SizeCategory?>>(
    FilterOption("All", null),
    FilterOption("Small", SizeCategory.SMALL),
    FilterOption("Medium", SizeCategory.MEDIUM),
    FilterOption("Large", SizeCategory.LARGE),
)

private val SORT_OPTIONS = listOf(
    FilterOption("Name", SortOption.NAME),
    FilterOption("Size", SortOption.SIZE),
    FilterOption("Recent", SortOption.RECENTLY_ADDED),
)

private val PARAMETER_BUCKETS = listOf("0.5B", "1B", "3B", "6.7B", "8B", "32B", "70B")
private val QUANT_BUCKETS = listOf("Q4_0", "Q5_0", "Q8_0", "Q4_K_M", "Q5_K_M", "Q6_K")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchAppBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onCloseSearch: () -> Unit,
) {
    TopAppBar(
        title = {
            TnTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = "Search models...",
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        navigationIcon = {
            IconButton(onClick = onCloseSearch) {
                Icon(TnIcons.ArrowLeft, "Close search")
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModelFiltersSection(viewModel: ModelStoreViewModel) {
    val dimens = LocalDimens.current
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
        executionTarget != null,
        sortBy != SortOption.NAME,
    ).count { it }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 480.dp)
            .verticalScroll(rememberScrollState())
            .padding(vertical = dimens.spacingSm),
        verticalArrangement = Arrangement.spacedBy(dimens.spacingSm),
    ) {
        // Model type — horizontal scroll keeps the row compact at the top
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = dimens.spacingLg),
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
        ) {
            MODEL_TYPE_OPTIONS.forEach { opt ->
                FilterChip(
                    selected = selectedModelType == opt.value,
                    onClick = { viewModel.filterByModelType(opt.value) },
                    label = { Text(opt.label) },
                )
            }
        }

        FilterSection(label = "Category") {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
                verticalArrangement = Arrangement.spacedBy(dimens.spacingXs),
                modifier = Modifier.fillMaxWidth(),
            ) {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { viewModel.filterByCategory(null) },
                    label = { Text("All") },
                )
                ModelCategory.entries.forEach { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = {
                            viewModel.filterByCategory(if (selectedCategory == category) null else category)
                        },
                        label = { Text(category.displayName) },
                    )
                }
            }
        }

        // Advanced toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dimens.spacingLg),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { showAdvancedFilters = !showAdvancedFilters }) {
                ExpandCollapseIcon(isExpanded = showAdvancedFilters, size = 20.dp)
                Spacer(Modifier.width(dimens.spacingXs))
                Text("Advanced filters")
                if (activeFilterCount > 0) {
                    Spacer(Modifier.width(dimens.spacingSm))
                    CountBadge(activeFilterCount)
                }
            }
            if (activeFilterCount > 0) {
                TextButton(onClick = { viewModel.clearAllFilters() }) { Text("Clear all") }
            }
        }

        AnimatedVisibility(
            visible = showAdvancedFilters,
            enter = Motion.Enter,
            exit = Motion.Exit,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimens.spacingLg),
                verticalArrangement = Arrangement.spacedBy(dimens.spacingMd),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Show NSFW content", style = MaterialTheme.typography.bodyMedium)
                    ActionSwitch(checked = showNsfw, onCheckedChange = { viewModel.setShowNsfw(it) })
                }

                FilterSection(label = "Execution") {
                    SegmentedFilter(
                        options = EXECUTION_OPTIONS,
                        selectedValue = executionTarget,
                        onSelect = { viewModel.setExecutionTarget(it) },
                    )
                }

                FilterSection(label = "Size") {
                    SegmentedFilter<SizeCategory?>(
                        options = SIZE_OPTIONS,
                        selectedValue = selectedSizeCategory,
                        onSelect = { viewModel.filterBySizeCategory(it) },
                    )
                }

                FilterSection(label = "Sort by") {
                    SegmentedFilter(
                        options = SORT_OPTIONS,
                        selectedValue = sortBy,
                        onSelect = { viewModel.setSortOption(it) },
                    )
                }

                FilterSection(label = "Parameters") {
                    ChipFlowRow {
                        PARAMETER_BUCKETS.forEach { param ->
                            FilterChip(
                                selected = param in selectedParameters,
                                onClick = { viewModel.toggleParameterFilter(param) },
                                label = { Text(param) },
                            )
                        }
                    }
                }

                FilterSection(label = "Quantization") {
                    ChipFlowRow {
                        QUANT_BUCKETS.forEach { quant ->
                            FilterChip(
                                selected = quant in selectedQuantizations,
                                onClick = { viewModel.toggleQuantizationFilter(quant) },
                                label = { Text(quant) },
                            )
                        }
                    }
                }

                val models by viewModel.models.collectAsStateWithLifecycle()
                val availableTags = remember(models) { viewModel.getAvailableTags() }
                if (availableTags.isNotEmpty()) {
                    FilterSection(label = "Tags") {
                        ChipFlowRow {
                            availableTags.forEach { tag ->
                                FilterChip(
                                    selected = tag in selectedTags,
                                    onClick = { viewModel.toggleTagFilter(tag) },
                                    label = { Text(tag) },
                                )
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(top = dimens.spacingSm),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
        )
    }
}

@Composable
private fun FilterSection(
    label: String,
    content: @Composable () -> Unit,
) {
    val dimens = LocalDimens.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.spacingLg),
        verticalArrangement = Arrangement.spacedBy(dimens.spacingXs),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipFlowRow(content: @Composable () -> Unit) {
    val dimens = LocalDimens.current
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
        verticalArrangement = Arrangement.spacedBy(dimens.spacingXs),
        modifier = Modifier.fillMaxWidth(),
    ) { content() }
}

@Composable
private fun <T> SegmentedFilter(
    options: List<FilterOption<T>>,
    selectedValue: T,
    onSelect: (T) -> Unit,
) {
    val selected = options.firstOrNull { it.value == selectedValue } ?: options.first()
    ActionToggleGroup(
        items = options,
        selectedItem = selected,
        onItemSelected = { onSelect(it.value) },
        itemLabel = { it.label },
    )
}

@Composable
private fun CountBadge(count: Int) {
    Surface(
        modifier = Modifier
            .size(20.dp)
            .clip(CircleShape),
        color = MaterialTheme.colorScheme.primary,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
