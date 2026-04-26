package com.dark.tool_neuron.service.server

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.RemoteCallbackList
import android.util.Log
import androidx.core.app.NotificationCompat
import com.dark.native_server.BindMode
import com.dark.native_server.NativeServer
import com.dark.tool_neuron.R
import com.dark.tool_neuron.activity.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

class RemoteServerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val engine = ServerEngine()
    private val bridge by lazy {
        ServerInferenceBridge(engine, ::pushRequestEvent)
    }
    private val callbacks = RemoteCallbackList<IRemoteServerCallback>()

    @Volatile private var snapshot: ServerSnapshot = ServerSnapshot.STOPPED

    private val binder = object : IRemoteServerService.Stub() {
        override fun start(configJson: String) {
            val cfg = runCatching { JSONObject(configJson) }.getOrNull() ?: run {
                pushFailure("invalid start config")
                return
            }
            scope.launch { handleStart(cfg) }
        }

        override fun stop() {
            scope.launch { handleStop("stopped") }
        }

        override fun isRunning(): Boolean = snapshot.phase == "running"

        override fun currentSnapshotJson(): String = snapshot.toJson().toString()

        override fun rotateToken(newToken: String) {
            if (newToken.isBlank()) return
            try { NativeServer.nativeSetToken(newToken) } catch (_: Exception) {}
        }

        override fun recentRequestEventsJson(max: Int): String =
            try { NativeServer.nativeRecentRequestsJson(max.coerceAtLeast(1)) }
            catch (_: Exception) { "[]" }

        override fun clearAuditLog() {
            try { NativeServer.nativeClearAuditLog() } catch (_: Exception) {}
        }

        override fun registerCallback(cb: IRemoteServerCallback?) {
            if (cb != null) {
                callbacks.register(cb)
                try { cb.onStateChanged(snapshot.toJson().toString()) } catch (_: Exception) {}
            }
        }

        override fun unregisterCallback(cb: IRemoteServerCallback?) {
            if (cb != null) callbacks.unregister(cb)
        }
    }

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel(this)
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> scope.launch { handleStop("stopped from notification") }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        runBlocking { handleStop("service destroyed") }
        callbacks.kill()
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun handleStart(cfg: JSONObject) {
        if (snapshot.phase == "running" || snapshot.phase == "starting" || snapshot.phase == "loading_model") {
            return
        }

        val modelId    = cfg.optString("modelId")
        val modelName  = cfg.optString("modelName")
        val modelPath  = cfg.optString("modelPath")
        val modelCfg   = cfg.optString("modelConfigJson", "{}")
        val token      = cfg.optString("token")
        val port       = cfg.optInt("port", DEFAULT_PORT).coerceIn(1024, 65535)
        val bindModeS  = cfg.optString("bindMode", BindMode.ALL_INTERFACES.name)
        val bindMode   = runCatching { BindMode.valueOf(bindModeS) }.getOrDefault(BindMode.ALL_INTERFACES)
        val webUiHtml  = cfg.optString("webUiHtml", "")
        val docsHtml   = cfg.optString("docsHtml", "")

        if (modelId.isBlank() || modelPath.isBlank()) {
            pushFailure("missing model selection")
            return
        }
        if (token.isBlank()) {
            pushFailure("missing bearer token")
            return
        }

        val resolution = BindResolver.resolve(applicationContext, bindMode) ?: run {
            pushFailure("no wifi interface available for ${bindMode.name}")
            return
        }

        publish(snapshot.copy(
            phase = "loading_model",
            modelId = modelId,
            modelName = modelName,
            bindModeName = bindMode.name,
        ))

        try { startService(Intent(this, RemoteServerService::class.java)) } catch (e: Exception) {
            Log.w(TAG, "self-start failed", e)
        }

        val notif = buildNotification(this, info(resolution, port, bindMode), modelName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notif)
        }

        val loaded = try { engine.load(modelPath, modelCfg) }
        catch (e: Exception) {
            Log.e(TAG, "model load failed", e)
            false
        }
        if (!loaded) {
            pushFailure("model load failed")
            stopForegroundSafe()
            return
        }

        publish(snapshot.copy(phase = "starting"))

        val catalog = JSONArray().apply {
            put(JSONObject().apply {
                put("id", modelId)
                put("name", modelName)
                put("created", System.currentTimeMillis() / 1000)
            })
        }.toString()

        NativeServer.nativeConfigureRateLimit(
            capacity = 30.0,
            refillRps = 1.0,
            authFailThreshold = 20,
            banDurationMs = 60L * 60L * 1000L,
        )
        NativeServer.nativeAttachBridge(bridge)
        NativeServer.nativeSetToken(token)
        NativeServer.nativeSetModelsCatalog(catalog)
        if (webUiHtml.isNotBlank()) NativeServer.nativeSetWebUiHtml(webUiHtml)
        if (docsHtml.isNotBlank()) NativeServer.nativeSetDocsHtml(docsHtml)

        val ok = NativeServer.nativeStart(resolution.host, port)
        if (!ok) {
            tearDownNative()
            pushFailure("failed to bind on ${resolution.host}:$port")
            stopForegroundSafe()
            return
        }
        val nativePort = NativeServer.nativeBoundPort()
        val effective  = if (nativePort in 1024..65535) nativePort else port

        publish(ServerSnapshot(
            phase = "running",
            modelId = modelId,
            modelName = modelName,
            host = resolution.host,
            displayHost = resolution.displayHost,
            lanHost = resolution.lanHost,
            port = effective,
            bindModeName = bindMode.name,
            wifiActive = resolution.isWifiActive,
            reason = null,
        ))

        Log.i(TAG, "server up host=${resolution.host} port=$effective model=$modelId")
    }

    private suspend fun handleStop(reason: String) {
        if (snapshot.phase == "stopped") {
            stopForegroundSafe()
            stopSelf()
            return
        }
        tearDownNative()
        try { engine.unload() } catch (e: Exception) { Log.w(TAG, "engine unload failed", e) }
        publish(ServerSnapshot.STOPPED.copy(reason = reason))
        stopForegroundSafe()
        stopSelf()
        Log.i(TAG, "server stopped reason=$reason")
    }

    private fun tearDownNative() {
        try { NativeServer.nativeStop() } catch (_: Exception) {}
        try { NativeServer.nativeClearToken() } catch (_: Exception) {}
        try { NativeServer.nativeDetachBridge() } catch (_: Exception) {}
        try { NativeServer.nativeClearModelsCatalog() } catch (_: Exception) {}
        try { NativeServer.nativeClearWebUi() } catch (_: Exception) {}
        try { NativeServer.nativeClearDocs() } catch (_: Exception) {}
        try { NativeServer.nativeClearAuditLog() } catch (_: Exception) {}
        try { NativeServer.nativeResetRateLimit() } catch (_: Exception) {}
    }

    private fun stopForegroundSafe() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (_: Exception) {}
    }

    private fun pushFailure(reason: String) {
        publish(ServerSnapshot.STOPPED.copy(phase = "failed", reason = reason))
    }

    private fun publish(next: ServerSnapshot) {
        snapshot = next
        val json = next.toJson().toString()
        val n = callbacks.beginBroadcast()
        try {
            for (i in 0 until n) {
                try { callbacks.getBroadcastItem(i).onStateChanged(json) } catch (_: Exception) {}
            }
        } finally { callbacks.finishBroadcast() }
    }

    private fun pushRequestEvent(evt: ServerRequestEvent) {
        val json = JSONObject().apply {
            put("ts_ms", evt.timestampMs)
            put("method", evt.method)
            put("path", evt.path)
            put("status", evt.status)
            put("duration_ms", evt.durationMs)
            put("client", evt.client)
        }.toString()
        val n = callbacks.beginBroadcast()
        try {
            for (i in 0 until n) {
                try { callbacks.getBroadcastItem(i).onRequestEvent(json) } catch (_: Exception) {}
            }
        } finally { callbacks.finishBroadcast() }
    }

    private fun info(r: BindResolution, port: Int, mode: BindMode) = ServerInfo(
        host = r.host,
        displayHost = r.displayHost,
        lanHost = r.lanHost,
        port = port,
        bindMode = mode,
        wifiActive = r.isWifiActive,
    )

    companion object {
        const val ACTION_STOP = "com.dark.tool_neuron.server.ACTION_STOP"
        const val EXTRA_OPEN_SERVER_SCREEN = "open_server_screen"

        private const val TAG = "RemoteServer"
        private const val CHANNEL_ID = "tn_remote_server"
        private const val CHANNEL_NAME = "Tool-Neuron Remote Server"
        private const val NOTIFICATION_ID = 0xA1CE
        private const val DEFAULT_PORT = 11434

        fun ensureNotificationChannel(ctx: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
            if (nm.getNotificationChannel(CHANNEL_ID) != null) return
            val ch = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW).apply {
                description = "Sticky notification while the remote server is running"
                setShowBadge(false)
            }
            nm.createNotificationChannel(ch)
        }

        fun buildNotification(ctx: Context, info: ServerInfo, modelName: String): Notification {
            val openPi = PendingIntent.getActivity(
                ctx,
                0,
                Intent(ctx, MainActivity::class.java).apply {
                    putExtra(EXTRA_OPEN_SERVER_SCREEN, true)
                    addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val stopPi = PendingIntent.getService(
                ctx,
                1,
                Intent(ctx, RemoteServerService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val title = "Tool-Neuron server · $modelName"
            val text  = "${info.displayHost}:${info.port}  ·  ${info.bindMode.name.lowercase()}"
            return NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setContentIntent(openPi)
                .addAction(0, "Stop", stopPi)
                .build()
        }
    }
}

internal data class ServerSnapshot(
    val phase: String,
    val modelId: String?,
    val modelName: String?,
    val host: String?,
    val displayHost: String?,
    val lanHost: String?,
    val port: Int,
    val bindModeName: String,
    val wifiActive: Boolean,
    val reason: String?,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("phase", phase)
        modelId?.let { put("modelId", it) }
        modelName?.let { put("modelName", it) }
        host?.let { put("host", it) }
        displayHost?.let { put("displayHost", it) }
        lanHost?.let { put("lanHost", it) }
        put("port", port)
        put("bindMode", bindModeName)
        put("wifiActive", wifiActive)
        reason?.let { put("reason", it) }
    }

    companion object {
        val STOPPED = ServerSnapshot(
            phase = "stopped",
            modelId = null, modelName = null,
            host = null, displayHost = null, lanHost = null,
            port = 0, bindModeName = BindMode.ALL_INTERFACES.name,
            wifiActive = false, reason = null,
        )
    }
}
