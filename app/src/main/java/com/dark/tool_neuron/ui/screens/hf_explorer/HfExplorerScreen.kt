package com.dark.tool_neuron.ui.screens.hf_explorer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.dark.tool_neuron.model.NavScreens
import com.dark.tool_neuron.repo.GatedFilter
import com.dark.tool_neuron.repo.HfApiError
import com.dark.tool_neuron.repo.HfFilterTaxonomy
import com.dark.tool_neuron.repo.HfFilters
import com.dark.tool_neuron.repo.HfSort
import com.dark.tool_neuron.repo.hf.HfGated
import com.dark.tool_neuron.repo.hf.HfModelSummary
import com.dark.tool_neuron.repo.hf.HfTrendingItem
import com.dark.tool_neuron.ui.components.ActionButton
import com.dark.tool_neuron.ui.components.ActionTextButton
import com.dark.tool_neuron.ui.components.CaptionText
import com.dark.tool_neuron.ui.components.SectionHeader
import com.dark.tool_neuron.ui.components.StandardCard
import com.dark.tool_neuron.ui.components.TnTextField
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.LocalTnShapes
import com.dark.tool_neuron.viewmodel.HfExplorerViewModel

@Composable
fun HfExplorerScreen(
    innerPadding: PaddingValues,
    navController: NavHostController,
    vm: HfExplorerViewModel = hiltViewModel(),
) {
    val dimens = LocalDimens.current
    val keyboard = LocalSoftwareKeyboardController.current

    val isSearching by vm.isSearching.collectAsStateWithLifecycle()
    val searchError by vm.searchError.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val filters by vm.filters.collectAsStateWithLifecycle()
    val trending by vm.trending.collectAsStateWithLifecycle()
    val trendingLoading by vm.trendingLoading.collectAsStateWithLifecycle()
    val existingPaths by vm.existingRepoPaths.collectAsStateWithLifecycle()
    val queryIsBlank by vm.queryIsBlank.collectAsStateWithLifecycle()

    var advancedOpen by remember { mutableStateOf(false) }

    val onSubmit: () -> Unit = remember(vm, keyboard) {
        {
            keyboard?.hide()
            vm.search()
        }
    }

    val onOpenRepo: (String) -> Unit = remember(navController) {
        { id -> navController.navigate(NavScreens.HfRepoDetail.routeFor(id)) }
    }

    val onAddRepo: (HfModelSummary) -> Unit = remember(vm) {
        { repo -> vm.addRepository(repo.id, repo.nameOnly) }
    }

    val showLanding = queryIsBlank && results.isEmpty() && !isSearching

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentPadding = PaddingValues(
            start = dimens.screenPadding,
            end = dimens.screenPadding,
            top = dimens.spacingMd,
            bottom = dimens.spacingLg,
        ),
        horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
        verticalArrangement = Arrangement.spacedBy(dimens.spacingSm),
    ) {
        item("search", span = { GridItemSpan(maxLineSpan) }) {
            SearchBar(
                queryFlow = vm.query,
                isSearching = isSearching,
                onQueryChange = vm::setQuery,
                onSubmit = onSubmit,
            )
        }

        item("sort", span = { GridItemSpan(maxLineSpan) }) {
            SortPills(sort = filters.sort, onChange = vm::setSort)
        }

        item("filters", span = { GridItemSpan(maxLineSpan) }) {
            FiltersCard(
                expanded = advancedOpen,
                filters = filters,
                onToggle = { advancedOpen = !advancedOpen },
                onLibraryToggle = vm::toggleLibrary,
                onAuthorChange = vm::setAuthor,
                onParamsRangeChange = vm::setParamsRange,
                onGatedChange = vm::setGated,
                onReset = vm::resetFilters,
            )
        }

        searchError?.let { error ->
            if (!isSearching) {
                item("error", span = { GridItemSpan(maxLineSpan) }) {
                    ErrorBanner(error)
                }
            }
        }

        if (isSearching) {
            item("loading", span = { GridItemSpan(maxLineSpan) }) {
                LoadingRow()
            }
        }

        if (showLanding && history.isNotEmpty()) {
            item("history", span = { GridItemSpan(maxLineSpan) }) {
                HistoryStrip(
                    history = history,
                    onPick = { entry -> vm.searchHistoryEntry(entry); keyboard?.hide() },
                    onRemove = vm::removeHistoryEntry,
                    onClear = vm::clearHistory,
                )
            }
        }

        if (showLanding && trending.isNotEmpty()) {
            item("trending-h", span = { GridItemSpan(maxLineSpan) }) {
                SectionHeader(title = "Trending")
            }
            item("trending-strip", span = { GridItemSpan(maxLineSpan) }) {
                TrendingStrip(items = trending, onOpen = onOpenRepo)
            }
        } else if (showLanding && trendingLoading) {
            item("trending-loading", span = { GridItemSpan(maxLineSpan) }) {
                LoadingRow(label = "Loading trending…")
            }
        }

        if (results.isNotEmpty()) {
            item("results-h", span = { GridItemSpan(maxLineSpan) }) {
                SectionHeader(title = "Results · ${results.size}")
            }
            items(results, key = { it.id }) { repo ->
                ResultGridCard(
                    repo = repo,
                    isAdded = existingPaths.contains(repo.idLowercase),
                    onOpen = onOpenRepo,
                    onAdd = onAddRepo,
                )
            }
        } else if (!isSearching && searchError == null && !queryIsBlank) {
            item("empty-no-results", span = { GridItemSpan(maxLineSpan) }) {
                EmptyState(
                    icon = TnIcons.SearchOff,
                    title = "No models matched",
                    detail = "Try a different keyword or relax the filters.",
                    actionLabel = "Reset filters",
                    onAction = vm::resetFilters,
                )
            }
        } else if (showLanding && history.isEmpty() && trending.isEmpty() && !trendingLoading) {
            item("empty-landing", span = { GridItemSpan(maxLineSpan) }) {
                EmptyState(
                    icon = TnIcons.Search,
                    title = "Find any model on HuggingFace",
                    detail = "Type a model family, author, or quant — press Enter or tap Search.",
                )
            }
        }
    }
}

@Composable
private fun SearchBar(
    queryFlow: kotlinx.coroutines.flow.StateFlow<String>,
    isSearching: Boolean,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val query by queryFlow.collectAsStateWithLifecycle()
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current
    Surface(
        shape = tnShapes.cardSmall,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(
                start = dimens.spacingSm,
                end = dimens.spacingXs,
                top = dimens.spacingXxs,
                bottom = dimens.spacingXxs,
            ),
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingXs),
        ) {
            Icon(
                imageVector = TnIcons.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            TnTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = "qwen, mistral, llama, deepseek…",
                singleLine = true,
                maxLines = 1,
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                trailingIcon = if (query.isNotEmpty()) {
                    @Composable {
                        Icon(
                            imageVector = TnIcons.X,
                            contentDescription = "Clear",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable { onQueryChange("") },
                        )
                    }
                } else null,
            )
            if (isSearching) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                ActionButton(
                    onClickListener = onSubmit,
                    icon = TnIcons.Search,
                    contentDescription = "Search",
                )
            }
        }
    }
}

@Composable
private fun SortPills(sort: HfSort, onChange: (HfSort) -> Unit) {
    val dimens = LocalDimens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(dimens.spacingXs),
    ) {
        HfSort.entries.forEach { s ->
            Pill(text = s.label, selected = sort == s, onClick = { onChange(s) })
        }
    }
}

@Composable
private fun HistoryStrip(
    history: List<String>,
    onPick: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
) {
    val dimens = LocalDimens.current
    StandardCard(
        title = "Recent searches",
        icon = TnIcons.Refresh,
        trailing = {
            ActionTextButton(
                onClickListener = onClear,
                icon = TnIcons.Trash,
                text = "Clear",
            )
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(dimens.spacingXs)) {
                history.forEach { entry ->
                    HistoryChip(text = entry, onClick = { onPick(entry) }, onRemove = { onRemove(entry) })
                }
            }
        }
    }
}

@Composable
private fun HistoryChip(text: String, onClick: () -> Unit, onRemove: () -> Unit) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current
    Surface(
        shape = tnShapes.full,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(
                    start = dimens.spacingSm,
                    end = dimens.spacingXs,
                    top = dimens.spacingXxs,
                    bottom = dimens.spacingXxs,
                ),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )
            Icon(
                imageVector = TnIcons.X,
                contentDescription = "Remove",
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                modifier = Modifier
                    .padding(start = dimens.spacingXs)
                    .size(14.dp)
                    .clickable(onClick = onRemove),
            )
        }
    }
}

@Composable
private fun FiltersCard(
    expanded: Boolean,
    filters: HfFilters,
    onToggle: () -> Unit,
    onLibraryToggle: (String) -> Unit,
    onAuthorChange: (String) -> Unit,
    onParamsRangeChange: (Long, Long) -> Unit,
    onGatedChange: (GatedFilter) -> Unit,
    onReset: () -> Unit,
) {
    val dimens = LocalDimens.current
    val activeCount = filters.activeCount
    StandardCard(
        title = if (activeCount == 0) "Filters" else "Filters · $activeCount active",
        icon = TnIcons.Sliders,
        trailing = {
            ActionButton(
                onClickListener = onToggle,
                icon = if (expanded) TnIcons.ChevronUp else TnIcons.ChevronDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
            )
        },
    ) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(dimens.spacingMd)) {

                FilterGroupHeader("Parameter count")
                ParamRangeSlider(
                    minMillions = filters.paramsMinMillions,
                    maxMillions = filters.paramsMaxMillions,
                    onChange = onParamsRangeChange,
                )

                FilterGroupHeader("Format")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(dimens.spacingXs),
                ) {
                    LIBRARY_OPTIONS.forEach { (id, label) ->
                        Pill(
                            text = label,
                            selected = filters.libraries.contains(id),
                            onClick = { onLibraryToggle(id) },
                        )
                    }
                }

                FilterGroupHeader("Gated")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(dimens.spacingXs),
                ) {
                    GatedFilter.entries.forEach { g ->
                        Pill(
                            text = g.label,
                            selected = filters.gated == g,
                            onClick = { onGatedChange(g) },
                        )
                    }
                }

                FilterGroupHeader("Author")
                LightInputBox {
                    TnTextField(
                        value = filters.author,
                        onValueChange = onAuthorChange,
                        placeholder = "bartowski, ggml-org, meta-llama…",
                        singleLine = true,
                        maxLines = 1,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    ActionTextButton(
                        onClickListener = onReset,
                        icon = TnIcons.Refresh,
                        text = "Reset",
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterGroupHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun LightInputBox(content: @Composable () -> Unit) {
    val tnShapes = LocalTnShapes.current
    Surface(
        shape = tnShapes.cardSmall,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        content()
    }
}

@Composable
private fun ParamRangeSlider(
    minMillions: Long,
    maxMillions: Long,
    onChange: (Long, Long) -> Unit,
) {
    val dimens = LocalDimens.current
    val steps = HfFilterTaxonomy.PARAM_STEPS_MILLIONS
    val maxIndex = steps.size - 1

    val initialMin = remember(minMillions) {
        steps.indexOfFirst { it >= minMillions }.coerceIn(0, maxIndex).toFloat()
    }
    val initialMax = remember(maxMillions) {
        if (maxMillions == 0L) maxIndex.toFloat()
        else steps.indexOfLast { it <= maxMillions }.coerceAtLeast(0).toFloat()
    }
    var range by remember { mutableStateOf(initialMin..initialMax) }

    val minLabel = HfFilterTaxonomy.paramStepLabel(steps[range.start.toInt()])
    val maxIdx = range.endInclusive.toInt()
    val maxLabel = if (maxIdx == maxIndex) "∞" else HfFilterTaxonomy.paramStepLabel(steps[maxIdx])

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$minLabel — $maxLabel",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            if (minMillions > 0L || maxMillions > 0L) {
                Text(
                    text = "Reset",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable {
                            range = 0f..maxIndex.toFloat()
                            onChange(0L, 0L)
                        }
                        .padding(horizontal = dimens.spacingSm, vertical = dimens.spacingXxs),
                )
            }
        }
        RangeSlider(
            value = range,
            onValueChange = { range = it },
            valueRange = 0f..maxIndex.toFloat(),
            steps = maxIndex - 1,
            onValueChangeFinished = {
                val lo = steps[range.start.toInt()]
                val hi = if (range.endInclusive.toInt() == maxIndex) 0L
                else steps[range.endInclusive.toInt()]
                onChange(lo, hi)
            },
            colors = SliderDefaults.colors(
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
            ),
        )
    }
}

@Composable
private fun TrendingStrip(
    items: List<HfTrendingItem>,
    onOpen: (String) -> Unit,
) {
    val dimens = LocalDimens.current
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
    ) {
        items(items, key = { it.id }) { item ->
            TrendingCard(item = item, onOpen = { onOpen(item.id) })
        }
    }
}

@Composable
private fun TrendingCard(item: HfTrendingItem, onOpen: () -> Unit) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current
    Surface(
        shape = tnShapes.cardSmall,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        modifier = Modifier
            .width(200.dp)
            .clickable(onClick = onOpen),
    ) {
        Column(
            modifier = Modifier.padding(dimens.cardPadding),
            verticalArrangement = Arrangement.spacedBy(dimens.spacingXs),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.spacingXs),
            ) {
                AuthorAvatar(author = item.author, size = 24.dp)
                Text(
                    text = item.author,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = item.id.substringAfter("/"),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(dimens.spacingXs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatPill(label = "↓", value = formatCount(item.downloads))
                StatPill(label = "♥", value = formatCount(item.likes))
            }
        }
    }
}

@Composable
private fun ResultGridCard(
    repo: HfModelSummary,
    isAdded: Boolean,
    onOpen: (String) -> Unit,
    onAdd: (HfModelSummary) -> Unit,
) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current
    Surface(
        shape = tnShapes.cardSmall,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 150.dp)
            .clickable { onOpen(repo.id) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(dimens.cardPadding),
            verticalArrangement = Arrangement.spacedBy(dimens.spacingXs),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                AuthorAvatar(author = repo.author, size = 32.dp)
                if (repo.gated != HfGated.OPEN) {
                    GatedBadge(repo.gated)
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = repo.nameOnly,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = repo.author,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.spacingXs),
            ) {
                StatPill(label = "↓", value = formatCount(repo.downloads))
                StatPill(label = "♥", value = formatCount(repo.likes))
                Spacer(Modifier.weight(1f))
                AddIcon(isAdded = isAdded, onAdd = { onAdd(repo) })
            }
        }
    }
}

@Composable
private fun AddIcon(isAdded: Boolean, onAdd: () -> Unit) {
    val tnShapes = LocalTnShapes.current
    if (isAdded) {
        Surface(
            shape = tnShapes.full,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
            modifier = Modifier.size(28.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    TnIcons.Check,
                    contentDescription = "Added",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    } else {
        Surface(
            shape = tnShapes.full,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(28.dp)
                .clickable(onClick = onAdd),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    TnIcons.Plus,
                    contentDescription = "Add",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun GatedBadge(gated: HfGated) {
    val tnShapes = LocalTnShapes.current
    Surface(
        shape = tnShapes.full,
        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f),
    ) {
        Text(
            text = if (gated == HfGated.AUTO) "Gated · auto" else "Gated",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.tertiary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun AuthorAvatar(author: String, size: androidx.compose.ui.unit.Dp) {
    val tint = remember(author) { authorTint(author) }
    val initials = remember(author) { authorInitials(author) }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = tint,
        modifier = Modifier
            .size(size)
            .aspectRatio(1f),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = initials,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
    }
}

@Composable
internal fun Pill(text: String, selected: Boolean, onClick: () -> Unit) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current
    val bg = if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.primary
    Surface(
        shape = tnShapes.full,
        color = bg,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = fg,
            modifier = Modifier.padding(
                horizontal = dimens.spacingMd,
                vertical = dimens.spacingXs,
            ),
        )
    }
}

@Composable
internal fun StatPill(label: String, value: String) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current
    Surface(
        shape = tnShapes.full,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = dimens.spacingSm,
                vertical = 2.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            )
            Text(
                value,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun ErrorBanner(error: HfApiError) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current
    val (text, icon) = when (error) {
        is HfApiError.RateLimited -> {
            val sec = error.retryAfterSeconds
            val msg = if (sec != null) "Rate limited — try again in ${formatRetry(sec)}"
            else "Rate limited — try again in a few minutes"
            msg to TnIcons.AlertTriangle
        }
        is HfApiError.NotFound -> "Not found" to TnIcons.SearchOff
        is HfApiError.Forbidden -> "Access denied" to TnIcons.AlertTriangle
        is HfApiError.Network -> "Network error — check your connection" to TnIcons.AlertTriangle
        is HfApiError.Parse -> "Couldn't read response" to TnIcons.AlertTriangle
        is HfApiError.Http -> when (error.status) {
            204 -> "No repositories matched" to TnIcons.SearchOff
            else -> "HTTP ${error.status}" to TnIcons.AlertTriangle
        }
    }
    Surface(
        shape = tnShapes.cardSmall,
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = dimens.spacingMd, vertical = dimens.spacingSm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun LoadingRow(label: String = "Searching HuggingFace…") {
    val dimens = LocalDimens.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
        modifier = Modifier.padding(horizontal = dimens.spacingSm),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
        )
        CaptionText(text = label)
    }
}

@Composable
private fun EmptyState(
    title: String,
    detail: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector = TnIcons.Search,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val dimens = LocalDimens.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = dimens.spacingLg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dimens.spacingSm),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(40.dp),
        )
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        CaptionText(text = detail)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.size(dimens.spacingXxs))
            ActionTextButton(
                onClickListener = onAction,
                icon = TnIcons.Refresh,
                text = actionLabel,
            )
        }
    }
}

internal fun formatCount(n: Long): String = when {
    n >= 1_000_000_000 -> "%.1fB".format(n / 1_000_000_000.0)
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0)
    n >= 1_000 -> "%.1fk".format(n / 1_000.0)
    else -> n.toString()
}

private fun formatRetry(sec: Int): String = when {
    sec >= 60 -> "${sec / 60}m"
    else -> "${sec}s"
}

private fun authorInitials(author: String): String {
    if (author.isBlank()) return "?"
    val parts = author.split('-', '_', '.', ' ').filter { it.isNotEmpty() }
    return when {
        parts.size >= 2 -> "${parts[0].first().uppercaseChar()}${parts[1].first().uppercaseChar()}"
        else -> author.take(2).uppercase()
    }
}

private val AVATAR_PALETTE = listOf(
    Color(0xFF6366F1),
    Color(0xFF8B5CF6),
    Color(0xFFEC4899),
    Color(0xFFEF4444),
    Color(0xFFF59E0B),
    Color(0xFF10B981),
    Color(0xFF06B6D4),
    Color(0xFF3B82F6),
)

private fun authorTint(author: String): Color {
    val h = author.hashCode().let { if (it < 0) -it else it }
    return AVATAR_PALETTE[h % AVATAR_PALETTE.size]
}

private val LIBRARY_OPTIONS = listOf(
    "gguf" to "GGUF",
    "transformers" to "Transformers",
    "safetensors" to "Safetensors",
    "onnx" to "ONNX",
    "mlx" to "MLX",
    "diffusers" to "Diffusers",
)
