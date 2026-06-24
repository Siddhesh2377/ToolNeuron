package com.dark.tool_neuron.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.download_manager.HxdManager
import com.dark.download_manager.HxdState
import com.dark.download_manager.HxdStatus
import com.dark.tool_neuron.model.DownloadHistoryEntry
import com.dark.tool_neuron.repo.DownloadCoordinator
import com.dark.tool_neuron.repo.DownloadHistoryRepository
import com.dark.tool_neuron.repo.DownloadLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class ActiveDownloadItem(
    val hxdId: Int,
    val displayName: String,
    val type: String,
    val state: HxdState,
)

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val coordinator: DownloadCoordinator,
    private val historyRepo: DownloadHistoryRepository,
) : ViewModel() {

    val activeDownloads: StateFlow<List<ActiveDownloadItem>> = combine(
        HxdManager.tasks,
        coordinator.labelsFlow,
    ) { tasks, labels ->
        tasks.asSequence()
            .filter {
                it.status == HxdStatus.QUEUED ||
                    it.status == HxdStatus.CONNECTING ||
                    it.status == HxdStatus.DOWNLOADING ||
                    it.status == HxdStatus.PAUSED ||
                    it.status == HxdStatus.FAILED
            }
            .map { state ->
                val label = labels[state.id] ?: DownloadLabel.fromUrl(state.url)
                ActiveDownloadItem(
                    hxdId = state.id,
                    displayName = label.displayName,
                    type = label.type,
                    state = state,
                )
            }
            .sortedBy { it.hxdId }
            .toList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val history: StateFlow<List<DownloadHistoryEntry>> = historyRepo.history
        .map { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), historyRepo.history.value)

    fun cancel(hxdId: Int) {
        HxdManager.cancel(hxdId)
    }

    fun retry(hxdId: Int) {
        HxdManager.resume(context, hxdId)
    }

    fun clear(hxdId: Int) {
        HxdManager.clear(hxdId)
    }

    fun clearHistory() {
        historyRepo.clearAll()
    }

}
