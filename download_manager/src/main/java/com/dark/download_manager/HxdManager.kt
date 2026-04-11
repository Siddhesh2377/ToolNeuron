package com.dark.download_manager

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.ConcurrentHashMap

// ── Public data types ─────────────────────────────────────────────────────────

/**
 * Options for a single download.
 *
 * @param expectedSha256  Optional hex SHA-256 of the expected file. Verified after completion.
 * @param userAgent       HTTP User-Agent header. Defaults to empty string (privacy: no fingerprint).
 * @param headers         Extra HTTP headers (e.g. Authorization for authenticated model downloads).
 */
data class HxdOptions(
    val expectedSha256: String? = null,
    val userAgent: String = "",
    val headers: Map<String, String> = emptyMap()
)

/** Current status of a download. Ordinals must match TaskState in download_task.h. */
enum class HxdStatus { QUEUED, CONNECTING, DOWNLOADING, PAUSED, COMPLETED, FAILED, CANCELLED }

/** Snapshot of a download's current state. Emitted via [HxdManager.observe]. */
data class HxdState(
    val id: Int,
    val url: String,
    val destPath: String,
    val downloadedBytes: Long,
    val totalBytes: Long,        // -1 if server did not send Content-Length
    val speedBps: Long,          // bytes/sec rolling average (last 5 s)
    val status: HxdStatus,
    val error: String? = null
) {
    /** 0.0–1.0 progress fraction, or -1f if total is unknown. */
    val progress: Float
        get() = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes.toFloat() else -1f

    val speedFormatted: String
        get() = when {
            speedBps >= 1_000_000L -> "%.1f MB/s".format(speedBps / 1_000_000f)
            speedBps >= 1_000L     -> "%.1f KB/s".format(speedBps / 1_000f)
            speedBps > 0L          -> "$speedBps B/s"
            else                   -> ""
        }
}

// ── Internal task descriptor ──────────────────────────────────────────────────

internal data class HxdTask(
    val id: Int,
    val url: String,
    val destPath: String,
    val options: HxdOptions
)

// ── HxdManager ────────────────────────────────────────────────────────────────

/**
 * Central coordinator for HXD downloads.
 *
 * Usage:
 * ```kotlin
 * val id = HxdManager.enqueue(context, url, destPath)
 * HxdManager.observe(id).collect { state -> ... }
 * ```
 */
object HxdManager {

    // Live state map — updated from the service coroutine
    private val _tasks = MutableStateFlow<Map<Int, HxdState>>(emptyMap())

    /** Flow of all known download states (active + completed + failed). */
    val tasks: Flow<List<HxdState>> = _tasks.asStateFlow().map { it.values.toList() }

    private val idCounter = AtomicInteger(1)
    private val lastProgressUpdate = ConcurrentHashMap<Int, Long>()

    // Queue + registry — protected by [lock]
    internal val lock         = Any()
    internal val pendingQueue = ArrayDeque<HxdTask>()

    // Registry: keeps task metadata so paused tasks can be re-queued without losing their URL/options.
    private val taskRegistry  = mutableMapOf<Int, HxdTask>()

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Adds a download to the queue and starts (or wakes) the background service.
     * Returns the download ID that can be used to observe or control this download.
     */
    fun enqueue(context: Context, url: String, destPath: String, options: HxdOptions = HxdOptions()): Int {
        val id   = idCounter.getAndIncrement()
        val task = HxdTask(id, url, destPath, options)

        synchronized(lock) {
            taskRegistry[id] = task
            pendingQueue.add(task)
            _tasks.value    = _tasks.value + (id to HxdState(
                id, url, destPath, 0L, -1L, 0L, HxdStatus.QUEUED
            ))
        }

        startService(context)
        return id
    }

    /**
     * Pauses a download. The partial file is preserved so the download can resume.
     * Call [resume] to continue later.
     */
    fun pause(id: Int) {
        HxdNative.nativePause(id)
        updateStatus(id, HxdStatus.PAUSED)
    }

    /**
     * Resumes a previously paused download.
     * Re-queues the task; the C++ engine detects the partial file and sends a Range request.
     */
    fun resume(context: Context, id: Int) {
        val task = synchronized(lock) { taskRegistry[id] } ?: return
        synchronized(lock) {
            pendingQueue.add(task)
        }
        updateStatus(id, HxdStatus.QUEUED)
        startService(context)
    }

    /**
     * Cancels a download and deletes the partial file.
     */
    fun cancel(id: Int) {
        val task = synchronized(lock) { taskRegistry[id] }
        HxdNative.nativeCancel(id)
        // Delete partial temp file
        task?.let { File(it.destPath + ".hxd_tmp").delete() }
        updateStatus(id, HxdStatus.CANCELLED)
        synchronized(lock) { taskRegistry.remove(id) }
    }

    /** Cancels all active downloads. */
    fun cancelAll() {
        val ids = synchronized(lock) { taskRegistry.keys.toList() }
        ids.forEach { cancel(it) }
    }

    /** Observe a specific download's state changes. */
    fun observe(id: Int): Flow<HxdState?> = _tasks.asStateFlow().map { it[id] }

    // ── Internal update methods (called from HxdService) ──────────────────────

    internal fun updateProgress(id: Int) {
        val prog    = HxdNative.nativeGetProgress(id)  // [downloaded, total, speed, state]
        val current = _tasks.value[id] ?: return
        val status  = HxdStatus.entries.getOrNull(prog[3].toInt()) ?: current.status

        if (status == HxdStatus.DOWNLOADING) {
            val now = System.currentTimeMillis()
            val last = lastProgressUpdate[id] ?: 0L
            if (now - last < 200L) return
            lastProgressUpdate[id] = now
        } else {
            lastProgressUpdate.remove(id)
        }

        _tasks.value = _tasks.value + (id to current.copy(
            downloadedBytes = prog[0],
            totalBytes      = prog[1],
            speedBps        = prog[2],
            status          = status
        ))
    }

    internal fun updateStatus(id: Int, status: HxdStatus, error: String? = null) {
        val current = _tasks.value[id] ?: return
        _tasks.value = _tasks.value + (id to current.copy(status = status, error = error))
    }

    // ── Persistence helpers (called from HxdService) ──────────────────────────

    internal fun saveQueue(queueFile: File) {
        val snap = synchronized(lock) { taskRegistry.values.toList() }
        if (snap.isEmpty()) { queueFile.delete(); return }

        val states = snap.map { task ->
            (_tasks.value[task.id]?.status ?: HxdStatus.QUEUED).ordinal.toByte()
        }
        val downloaded = snap.map { _tasks.value[it.id]?.downloadedBytes ?: 0L }
        val totals     = snap.map { _tasks.value[it.id]?.totalBytes ?: -1L }

        HxdNative.nativeSaveQueue(
            queueFile.absolutePath,
            snap.map { it.id }.toIntArray(),
            states.toByteArray(),
            downloaded.toLongArray(),
            totals.toLongArray(),
            snap.map { it.url }.toTypedArray(),
            snap.map { it.destPath }.toTypedArray()
        )
    }

    internal fun restoreQueue(context: Context, queueFile: File) {
        if (!queueFile.exists()) return
        val rows = HxdNative.nativeLoadQueue(queueFile.absolutePath)

        for (row in rows) {
            val id           = row[0].toIntOrNull() ?: continue
            val stateOrdinal = row[1].toIntOrNull() ?: 0
            val downloaded   = row[2].toLongOrNull() ?: 0L
            val total        = row[3].toLongOrNull() ?: -1L
            val url          = row[4]
            val destPath     = row[5]
            val status       = HxdStatus.entries.getOrNull(stateOrdinal) ?: HxdStatus.FAILED

            val task = HxdTask(id, url, destPath, HxdOptions())
            synchronized(lock) {
                taskRegistry[id] = task
                _tasks.value = _tasks.value + (id to HxdState(
                    id, url, destPath, downloaded, total, 0L, status
                ))
                // Re-queue tasks that were in progress — they will resume via Range request
                if (status == HxdStatus.DOWNLOADING ||
                    status == HxdStatus.CONNECTING  ||
                    status == HxdStatus.QUEUED) {
                    pendingQueue.add(task)
                }
            }
        }

        if (synchronized(lock) { pendingQueue.isNotEmpty() }) {
            startService(context)
        }
    }

    private fun startService(context: Context) {
        context.startForegroundService(Intent(context, HxdService::class.java))
    }
}
