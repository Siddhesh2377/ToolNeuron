package com.dark.tool_neuron.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.tool_neuron.data.AppPreferences
import com.dark.tool_neuron.model.HFRepository
import com.dark.tool_neuron.repo.GatedFilter
import com.dark.tool_neuron.repo.HfApiError
import com.dark.tool_neuron.repo.HfFilters
import com.dark.tool_neuron.repo.HfSort
import com.dark.tool_neuron.repo.RepositoryDataStore
import com.dark.tool_neuron.repo.hf.HfClient
import com.dark.tool_neuron.repo.hf.HfModelDetail
import com.dark.tool_neuron.repo.hf.HfModelSummary
import com.dark.tool_neuron.repo.hf.HfSibling
import com.dark.tool_neuron.repo.hf.HfTagsCatalog
import com.dark.tool_neuron.repo.hf.HfTrendingItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import javax.inject.Inject

private const val MAX_HISTORY = 20
private const val DEFAULT_LIMIT = 10
private const val TRENDING_LIMIT = 8

enum class HfFileFilter { ALL, GGUF, MMPROJ }
enum class HfFileSizeBucket(val label: String, val maxBytes: Long) {
    ANY("Any", Long.MAX_VALUE),
    UNDER_1GB("< 1 GB", 1_073_741_824L),
    UNDER_4GB("< 4 GB", 4L * 1_073_741_824L),
    UNDER_8GB("< 8 GB", 8L * 1_073_741_824L),
}

sealed interface HfRepoDetailState {
    data object Idle : HfRepoDetailState
    data object Loading : HfRepoDetailState
    data class Success(
        val detail: HfModelDetail,
        val readme: String?,
        val readmeError: String?,
    ) : HfRepoDetailState
    data class Failed(val error: HfApiError) : HfRepoDetailState
}

@HiltViewModel
class HfExplorerViewModel @Inject constructor(
    private val client: HfClient,
    private val repoDataStore: RepositoryDataStore,
    private val prefs: AppPreferences,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val queryIsBlank: StateFlow<Boolean> = _query
        .map { it.isBlank() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private val _filters = MutableStateFlow(HfFilters())
    val filters: StateFlow<HfFilters> = _filters.asStateFlow()

    private val _results = MutableStateFlow<List<HfModelSummary>>(emptyList())
    val results: StateFlow<List<HfModelSummary>> = _results.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _searchError = MutableStateFlow<HfApiError?>(null)
    val searchError: StateFlow<HfApiError?> = _searchError.asStateFlow()

    private val _trending = MutableStateFlow<List<HfTrendingItem>>(emptyList())
    val trending: StateFlow<List<HfTrendingItem>> = _trending.asStateFlow()

    private val _trendingLoading = MutableStateFlow(false)
    val trendingLoading: StateFlow<Boolean> = _trendingLoading.asStateFlow()

    private val _tagsCatalog = MutableStateFlow(HfTagsCatalog.EMPTY)
    val tagsCatalog: StateFlow<HfTagsCatalog> = _tagsCatalog.asStateFlow()

    private val _history = MutableStateFlow(loadHistory())
    val history: StateFlow<List<String>> = _history.asStateFlow()

    private val _detailState = MutableStateFlow<HfRepoDetailState>(HfRepoDetailState.Idle)
    val detailState: StateFlow<HfRepoDetailState> = _detailState.asStateFlow()

    private val _fileFilter = MutableStateFlow(HfFileFilter.GGUF)
    val fileFilter: StateFlow<HfFileFilter> = _fileFilter.asStateFlow()

    private val _fileSizeBucket = MutableStateFlow(HfFileSizeBucket.ANY)
    val fileSizeBucket: StateFlow<HfFileSizeBucket> = _fileSizeBucket.asStateFlow()

    val existingRepoPaths: StateFlow<Set<String>> =
        repoDataStore.repositories
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
            .let { upstream ->
                MutableStateFlow(upstream.value.map { it.repoPath.lowercase() }.toSet()).also { sink ->
                    viewModelScope.launch {
                        upstream.collect { list ->
                            sink.value = list.map { it.repoPath.lowercase() }.toSet()
                        }
                    }
                }
            }

    private var searchJob: Job? = null
    private var detailJob: Job? = null

    init {
        loadTagsCatalog()
        loadTrending()
    }

    fun setQuery(q: String) {
        _query.value = q
        if (q.isBlank()) {
            _searchError.value = null
        }
    }

    fun search() {
        val q = _query.value.trim()
        if (q.isBlank() && _filters.value.activeCount == 0) {
            _searchError.value = null
            _results.value = emptyList()
            return
        }
        runSearch(q, pushHistoryOnSuccess = q.isNotBlank())
    }

    private fun runSearch(query: String, pushHistoryOnSuccess: Boolean) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _isSearching.value = true
            _searchError.value = null
            try {
                client.searchModels(query, _filters.value, DEFAULT_LIMIT).fold(
                    onSuccess = { repos ->
                        _results.value = repos
                        if (pushHistoryOnSuccess) pushHistory(query)
                    },
                    onFailure = { e ->
                        if (e is CancellationException) throw e
                        _results.value = emptyList()
                        _searchError.value = (e as? HfApiError) ?: HfApiError.Network(e.message ?: "unknown")
                    },
                )
                _isSearching.value = false
            } catch (e: CancellationException) {
                throw e
            }
        }
    }

    fun searchHistoryEntry(q: String) {
        _query.value = q
        search()
    }

    fun clearHistory() {
        _history.value = emptyList()
        prefs.hfSearchHistory = ""
    }

    fun removeHistoryEntry(q: String) {
        val updated = _history.value.filter { it != q }
        _history.value = updated
        prefs.hfSearchHistory = saveHistory(updated)
    }

    fun setSort(s: HfSort) = updateAndSearch { copy(sort = s) }
    fun setAuthor(s: String) = update { copy(author = s) }
    fun setGated(g: GatedFilter) = updateAndSearch { copy(gated = g) }
    fun setParamsRange(minMillions: Long, maxMillions: Long) =
        updateAndSearch { copy(paramsMinMillions = minMillions, paramsMaxMillions = maxMillions) }

    fun toggleLibrary(value: String) = updateAndSearch { copy(libraries = libraries.toggle(value)) }

    fun resetFilters() {
        val hadAny = _filters.value.activeCount > 0
        _filters.value = HfFilters()
        if (hadAny && _query.value.isNotBlank()) search()
    }

    fun visibleResults(): List<HfModelSummary> = _results.value

    fun addRepository(modelId: String, displayName: String? = null) {
        viewModelScope.launch {
            val existing = repoDataStore.repositories.value
            if (existing.any { it.repoPath.equals(modelId, ignoreCase = true) }) return@launch
            repoDataStore.addRepository(
                HFRepository(
                    id = "hf-${modelId.replace("/", "-").lowercase()}",
                    name = displayName ?: modelId.substringAfter("/"),
                    repoPath = modelId,
                ),
            )
        }
    }

    fun loadRepoDetail(repoPath: String) {
        detailJob?.cancel()
        _detailState.value = HfRepoDetailState.Loading
        detailJob = viewModelScope.launch {
            client.modelDetail(repoPath).fold(
                onSuccess = { detail ->
                    _detailState.value = HfRepoDetailState.Success(detail, null, null)
                    val readme = client.readme(repoPath)
                    readme.fold(
                        onSuccess = { md ->
                            val current = _detailState.value
                            if (current is HfRepoDetailState.Success && current.detail.summary.id == detail.summary.id) {
                                _detailState.value = current.copy(readme = md, readmeError = null)
                            }
                        },
                        onFailure = { e ->
                            val current = _detailState.value
                            if (current is HfRepoDetailState.Success && current.detail.summary.id == detail.summary.id) {
                                _detailState.value = current.copy(readme = null, readmeError = e.message)
                            }
                        },
                    )
                },
                onFailure = { e ->
                    _detailState.value = HfRepoDetailState.Failed(
                        (e as? HfApiError) ?: HfApiError.Network(e.message ?: "unknown"),
                    )
                },
            )
        }
    }

    fun setFileFilter(f: HfFileFilter) { _fileFilter.value = f }
    fun setFileSizeBucket(b: HfFileSizeBucket) { _fileSizeBucket.value = b }

    fun visibleFiles(detail: HfModelDetail): List<HfSibling> {
        var list: List<HfSibling> = detail.files
        list = when (_fileFilter.value) {
            HfFileFilter.ALL -> list
            HfFileFilter.GGUF -> list.filter { it.path.endsWith(".gguf", ignoreCase = true) }
            HfFileFilter.MMPROJ -> list.filter { it.path.contains("mmproj", ignoreCase = true) }
        }
        val cap = _fileSizeBucket.value.maxBytes
        if (cap != Long.MAX_VALUE) list = list.filter { it.sizeBytes in 1..cap }
        return list.sortedBy { it.sizeBytes }
    }

    private fun loadTagsCatalog() {
        viewModelScope.launch {
            client.tagsCatalog().onSuccess { _tagsCatalog.value = it }
        }
    }

    private fun loadTrending() {
        viewModelScope.launch {
            _trendingLoading.value = true
            client.trending(TRENDING_LIMIT).onSuccess { _trending.value = it }
            _trendingLoading.value = false
        }
    }

    private inline fun update(block: HfFilters.() -> HfFilters) {
        _filters.value = _filters.value.block()
    }

    private inline fun updateAndSearch(block: HfFilters.() -> HfFilters) {
        _filters.value = _filters.value.block()
        if (_query.value.isNotBlank() || _filters.value.activeCount > 0) {
            runSearch(_query.value.trim(), pushHistoryOnSuccess = false)
        }
    }

    private fun Set<String>.toggle(value: String): Set<String> =
        if (contains(value)) this - value else this + value

    private fun loadHistory(): List<String> {
        val raw = prefs.hfSearchHistory
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }
        }.getOrDefault(emptyList())
    }

    private fun saveHistory(list: List<String>): String {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        return arr.toString()
    }

    private fun pushHistory(q: String) {
        val trimmed = q.trim()
        if (trimmed.isBlank()) return
        val deduped = (listOf(trimmed) + _history.value.filter { it != trimmed }).take(MAX_HISTORY)
        _history.value = deduped
        prefs.hfSearchHistory = saveHistory(deduped)
    }
}
