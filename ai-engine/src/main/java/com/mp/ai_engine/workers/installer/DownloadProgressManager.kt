package com.mp.ai_engine.workers.installer

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Centralized manager for tracking download progress across the application
 */
object DownloadProgressManager {
    
    private val _downloadStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, DownloadState>> = _downloadStates.asStateFlow()
    
    private val states = ConcurrentHashMap<String, DownloadState>()
    
    /**
     * Update progress for a specific download
     * @param downloadUrl Unique identifier for the download
     * @param progress Progress value (0-100)
     */
    fun updateProgress(downloadUrl: String, progress: Float) {
        states[downloadUrl] = DownloadState.Downloading(progress)
        emitUpdate()
    }
    
    /**
     * Mark a download as complete
     * @param downloadUrl Unique identifier for the download
     * @param filePath Path where the file was saved
     */
    fun markComplete(downloadUrl: String, filePath: String) {
        states[downloadUrl] = DownloadState.Complete(filePath)
        emitUpdate()
    }
    
    /**
     * Mark a download as failed
     * @param downloadUrl Unique identifier for the download
     * @param error Error message
     */
    fun markFailed(downloadUrl: String, error: String) {
        states[downloadUrl] = DownloadState.Failed(error)
        emitUpdate()
    }
    
    /**
     * Remove a download from tracking (e.g., after cancellation or cleanup)
     * @param downloadUrl Unique identifier for the download
     */
    fun removeDownload(downloadUrl: String) {
        states.remove(downloadUrl)
        emitUpdate()
    }
    
    /**
     * Get the current state of a specific download
     * @param downloadUrl Unique identifier for the download
     * @return Current download state or null if not found
     */
    fun getDownloadState(downloadUrl: String): DownloadState? {
        return states[downloadUrl]
    }
    
    /**
     * Check if a download is currently in progress
     * @param downloadUrl Unique identifier for the download
     * @return True if download is in progress
     */
    fun isDownloading(downloadUrl: String): Boolean {
        return states[downloadUrl]?.isDownloading == true
    }
    
    /**
     * Get all active downloads (currently downloading)
     * @return List of download URLs that are currently downloading
     */
    fun getActiveDownloads(): List<String> {
        return states.filter { it.value.isDownloading }.keys.toList()
    }
    
    /**
     * Clear all download states
     */
    fun clearAll() {
        states.clear()
        emitUpdate()
    }
    
    private fun emitUpdate() {
        _downloadStates.value = states.toMap()
    }
}