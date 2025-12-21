package com.mp.ai_engine.workers.installer

import com.mp.ai_engine.workers.DownloadState
import com.mp.ai_engine.workers.DownloadsState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Centralized manager for tracking all download states
 * Thread-safe and reactive with StateFlow
 */
object DownloadProgressManager {

    private val _downloadsState = MutableStateFlow(DownloadsState())
    val downloadsState: StateFlow<DownloadsState> = _downloadsState.asStateFlow()

    private val states = ConcurrentHashMap<String, DownloadState>()

    /**
     * Start tracking a new download
     */
    fun startDownload(downloadId: String, modelName: String) {
        states[downloadId] = DownloadState.Idle(downloadId, modelName)
        emitUpdate()
    }

    /**
     * Update download progress
     */
    fun updateProgress(
        downloadId: String,
        progress: Float,
        downloadedBytes: Long = 0L,
        totalBytes: Long = 0L
    ) {
        val current = states[downloadId]
        if (current != null) {
            states[downloadId] = DownloadState.Downloading(
                downloadId = downloadId,
                modelName = current.modelName,
                progress = progress.coerceIn(0f, 1f),
                downloadedBytes = downloadedBytes,
                totalBytes = totalBytes
            )
            emitUpdate()
        }
    }

    /**
     * Mark download as installing
     */
    fun markInstalling(downloadId: String) {
        val current = states[downloadId]
        if (current != null) {
            states[downloadId] = DownloadState.Installing(
                downloadId = downloadId,
                modelName = current.modelName
            )
            emitUpdate()
        }
    }

    /**
     * Mark download as complete
     */
    fun markComplete(downloadId: String, filePath: String) {
        val current = states[downloadId]
        if (current != null) {
            states[downloadId] = DownloadState.Completed(
                downloadId = downloadId,
                modelName = current.modelName,
                filePath = filePath
            )
            emitUpdate()
        }
    }

    /**
     * Mark download as failed
     */
    fun markFailed(downloadId: String, error: String) {
        val current = states[downloadId]
        if (current != null) {
            states[downloadId] = DownloadState.Failed(
                downloadId = downloadId,
                modelName = current.modelName,
                error = error
            )
            emitUpdate()
        }
    }

    /**
     * Mark download as cancelled
     */
    fun markCancelled(downloadId: String) {
        val current = states[downloadId]
        if (current != null) {
            states[downloadId] = DownloadState.Cancelled(
                downloadId = downloadId,
                modelName = current.modelName
            )
            emitUpdate()
        }
    }

    /**
     * Remove a download from tracking
     */
    fun removeDownload(downloadId: String) {
        states.remove(downloadId)
        emitUpdate()
    }

    /**
     * Get current state of a specific download
     */
    fun getDownloadState(downloadId: String): DownloadState? = states[downloadId]

    /**
     * Check if download is active
     */
    fun isActive(downloadId: String): Boolean {
        return _downloadsState.value.isActive(downloadId)
    }

    /**
     * Get all active downloads
     */
    fun getActiveDownloads(): List<DownloadState> {
        return _downloadsState.value.activeDownloads
    }

    /**
     * Clear all completed and failed downloads
     */
    fun clearInactive() {
        states.entries.removeIf {
            val state = it.value
            state is DownloadState.Completed ||
                    state is DownloadState.Failed ||
                    state is DownloadState.Cancelled
        }
        emitUpdate()
    }

    /**
     * Clear all downloads
     */
    fun clearAll() {
        states.clear()
        emitUpdate()
    }

    private fun emitUpdate() {
        _downloadsState.value = DownloadsState(states.toMap())
    }
}