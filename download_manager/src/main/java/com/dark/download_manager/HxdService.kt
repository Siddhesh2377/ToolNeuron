package com.dark.download_manager

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class HxdService : Service() {

    private val scope               = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val semaphore           = Semaphore(MAX_CONCURRENT)
    private val notificationManager by lazy { getSystemService(NotificationManager::class.java) }
    private val queueFile           by lazy  { File(filesDir, QUEUE_FILE) }

    companion object {
        private const val MAX_CONCURRENT = 3
        private const val QUEUE_FILE     = "hxd_queue.bin"
    }

    override fun onCreate() {
        super.onCreate()
        HxdNotification.createChannel(this)
        startForeground(HxdNotification.NOTIFICATION_ID, HxdNotification.build(this, emptyList()))

        HxdManager.restoreQueue(this, queueFile)

        launchNotificationUpdater()
        launchDispatcher()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        HxdManager.saveQueue(queueFile)
        scope.cancel()
        super.onDestroy()
    }

    private fun launchNotificationUpdater() {
        scope.launch {
            HxdManager.tasks.collect { states ->
                val active = states.filter {
                    it.status == HxdStatus.DOWNLOADING || it.status == HxdStatus.CONNECTING
                }
                notificationManager.notify(
                    HxdNotification.NOTIFICATION_ID,
                    HxdNotification.build(this@HxdService, active)
                )
            }
        }
    }

    private fun launchDispatcher() {
        scope.launch {
            while (isActive) {
                val task = synchronized(HxdManager.lock) {
                    HxdManager.pendingQueue.removeFirstOrNull()
                }

                if (task == null) {
                    delay(300)
                    if (semaphore.availablePermits == MAX_CONCURRENT &&
                        synchronized(HxdManager.lock) { HxdManager.pendingQueue.isEmpty() }) {
                        HxdManager.saveQueue(queueFile)
                        stopSelf()
                    }
                    continue
                }

                semaphore.acquire()
                launch {
                    try { executeDownload(task) }
                    finally { semaphore.release() }
                }
            }
        }
    }

    private suspend fun executeDownload(task: HxdTask) = withContext(Dispatchers.IO) {
        val maxAttempts = task.options.maxAttempts.coerceAtLeast(1)
        var attempt = 1
        while (attempt <= maxAttempts && HxdManager.isRegistered(task.id)) {
            HxdManager.updateAttempt(task.id, attempt)
            val retry = executeAttempt(task, attempt, maxAttempts)
            if (!retry) return@withContext
            attempt++
        }
    }

    private suspend fun executeAttempt(task: HxdTask, attempt: Int, maxAttempts: Int): Boolean = withContext(Dispatchers.IO) {
        val id = task.id
        HxdManager.updateStatus(id, HxdStatus.CONNECTING, retryMessage(attempt, maxAttempts))

        val resumeOffset = HxdNative.nativePrepare(id, task.destPath)
        if (resumeOffset < 0) {
            HxdManager.updateStatus(id, HxdStatus.FAILED, "Cannot create destination file")
            return@withContext false
        }

        val digest: MessageDigest? = task.options.expectedSha256
            ?.let { MessageDigest.getInstance("SHA-256") }

        var conn: HttpURLConnection? = null
        try {
            conn = openConnection(task, resumeOffset)
            val code = conn.responseCode

            if (code != 200 && code != 206) {
                HxdNative.nativeFail(id)
                return@withContext retryOrFail(id, attempt, maxAttempts, "HTTP $code", isTransientHttp(code))
            }

            val contentLen = conn.contentLengthLong
            if (contentLen > 0) {
                val total = if (code == 206) resumeOffset + contentLen else contentLen
                HxdNative.nativeSetTotal(id, total)
            }

            HxdManager.updateStatus(id, HxdStatus.DOWNLOADING)

            val buf = ByteArray(65_536)
            var aborted = false

            conn.inputStream.use { stream ->
                var n: Int
                while (stream.read(buf).also { n = it } != -1) {
                    digest?.update(buf, 0, n)
                    if (!HxdNative.nativeWriteChunk(id, buf, 0, n)) {
                        aborted = true
                        break
                    }
                    HxdManager.updateProgress(id)
                }
            }

            if (aborted) {
                val prog   = HxdNative.nativeGetProgress(id)
                val status = HxdStatus.entries.getOrNull(prog[3].toInt()) ?: HxdStatus.FAILED

                HxdNative.nativeCleanup(id)

                if (status == HxdStatus.CANCELLED) {
                    File(task.destPath + ".hxd_tmp").delete()
                    HxdManager.updateStatus(id, HxdStatus.CANCELLED)
                } else {
                    HxdManager.updateStatus(id, HxdStatus.PAUSED)
                }
                return@withContext false
            }

            if (digest != null) {
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                if (!actual.equals(task.options.expectedSha256, ignoreCase = true)) {
                    HxdNative.nativeFail(id)
                    HxdManager.updateStatus(id, HxdStatus.FAILED, "Checksum mismatch")
                    return@withContext false
                }
            }

            if (HxdNative.nativeComplete(id)) {
                HxdManager.updateStatus(id, HxdStatus.COMPLETED)
            } else {
                HxdManager.updateStatus(id, HxdStatus.FAILED, "Failed to finalize file")
            }
            false

        } catch (e: Exception) {
            HxdNative.nativeFail(id)
            retryOrFail(id, attempt, maxAttempts, e.message ?: "Network error", transient = true)
        } finally {
            conn?.disconnect()
        }
    }

    private suspend fun retryOrFail(
        id: Int,
        attempt: Int,
        maxAttempts: Int,
        reason: String,
        transient: Boolean,
    ): Boolean {
        if (!transient || attempt >= maxAttempts) {
            HxdManager.updateStatus(id, HxdStatus.FAILED, reason)
            return false
        }
        val delayMs = retryDelayMs(attempt)
        val next = System.currentTimeMillis() + delayMs
        HxdManager.updateAttempt(id, attempt, next, "$reason · retrying")
        HxdManager.updateStatus(id, HxdStatus.QUEUED, "$reason · retrying")
        delay(delayMs)
        return HxdManager.isRegistered(id)
    }

    private fun retryMessage(attempt: Int, maxAttempts: Int): String? =
        if (attempt > 1) "Retry $attempt/$maxAttempts" else null

    private fun retryDelayMs(attempt: Int): Long = when (attempt) {
        1 -> 2_000L
        2 -> 6_000L
        else -> 15_000L
    }

    private fun isTransientHttp(code: Int): Boolean =
        code == 408 || code == 425 || code == 429 || code in 500..599

    private fun openConnection(task: HxdTask, resumeOffset: Long): HttpURLConnection {
        return (URL(task.url).openConnection() as HttpURLConnection).apply {
            connectTimeout      = 30_000
            readTimeout         = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", task.options.userAgent)
            setRequestProperty("Cookie", "")
            if (resumeOffset > 0) {
                setRequestProperty("Range", "bytes=$resumeOffset-")
            }
            task.options.headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
    }
}
