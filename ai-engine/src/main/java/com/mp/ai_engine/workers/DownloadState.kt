package com.mp.ai_engine.workers

/**
 * Represents the state of a single download
 */
sealed class DownloadState {
    abstract val downloadId: String
    abstract val modelName: String
    
    data class Idle(
        override val downloadId: String,
        override val modelName: String
    ) : DownloadState()
    
    data class Downloading(
        override val downloadId: String,
        override val modelName: String,
        val progress: Float, // 0.0 to 1.0
        val downloadedBytes: Long = 0L,
        val totalBytes: Long = 0L
    ) : DownloadState() {
        val progressPercent: Int get() = (progress * 100).toInt()
    }
    
    data class Installing(
        override val downloadId: String,
        override val modelName: String
    ) : DownloadState()
    
    data class Completed(
        override val downloadId: String,
        override val modelName: String,
        val filePath: String
    ) : DownloadState()
    
    data class Failed(
        override val downloadId: String,
        override val modelName: String,
        val error: String
    ) : DownloadState()
    
    data class Cancelled(
        override val downloadId: String,
        override val modelName: String
    ) : DownloadState()
}

/**
 * Container for all active downloads
 */
data class DownloadsState(
    val downloads: Map<String, DownloadState> = emptyMap()
) {
    val activeDownloads: List<DownloadState>
        get() = downloads.values.filter { 
            it is DownloadState.Downloading || it is DownloadState.Installing 
        }
    
    val completedDownloads: List<DownloadState.Completed>
        get() = downloads.values.filterIsInstance<DownloadState.Completed>()
    
    val failedDownloads: List<DownloadState.Failed>
        get() = downloads.values.filterIsInstance<DownloadState.Failed>()
    
    fun getDownload(downloadId: String): DownloadState? = downloads[downloadId]
    
    fun isDownloading(downloadId: String): Boolean = 
        downloads[downloadId] is DownloadState.Downloading
    
    fun isActive(downloadId: String): Boolean {
        val state = downloads[downloadId]
        return state is DownloadState.Downloading || state is DownloadState.Installing
    }
}