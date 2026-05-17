package com.dark.tool_neuron.service.island

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.AndroidUiDispatcher
import com.dark.tool_neuron.R
import com.dark.tool_neuron.activity.MainActivity
import com.dark.tool_neuron.ui.theme.ToolNeuronTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import androidx.compose.ui.util.lerp

class IslandOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var islandView: IslandComposeView? = null
    private val lifecycleOwner = OverlayLifecycleOwner()
    private val expanded = mutableStateOf(false)

    private val serviceScope = CoroutineScope(SupervisorJob() + AndroidUiDispatcher.Main)
    private val animY = Animatable(0f)
    private val dodgeSpring = spring<Float>(
        dampingRatio = 0.55f,
        stiffness = Spring.StiffnessMediumLow,
    )
    private var placementJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        IslandPositionStore.init(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        setupNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        if (Settings.canDrawOverlays(this)) {
            attachIsland()
            IslandPositionStore.setRunning(true)
        } else {
            Log.w(TAG, "SYSTEM_ALERT_WINDOW not granted; service running headless")
        }
    }

    private fun attachIsland() {
        if (islandView != null) return
        val view = IslandComposeView(this).apply {
            setContent {
                ToolNeuronTheme {
                    IslandSurface(
                        expanded = expanded.value,
                        onToggle = { expanded.value = !expanded.value },
                    )
                }
            }
        }
        lifecycleOwner.attachToDecorView(view)
        islandView = view
        try {
            windowManager.addView(view, initialParams())
            startPlacement()
        } catch (t: Throwable) {
            Log.e(TAG, "addView(island) failed", t)
            islandView = null
        }
    }

    private fun initialParams(): WindowManager.LayoutParams {
        val density = resources.displayMetrics.density
        val pos = IslandPositionStore.position.value
        serviceScope.launch {
            animY.snapTo(pos.offsetYDp)
        }
        val (initialW, initialH) = sizeForProgress(0f, density)
        return WindowManager.LayoutParams(
            initialW,
            initialH,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = (pos.offsetYDp * density).toInt()
            title = IslandGeometry.OVERLAY_WINDOW_TITLE
            windowAnimations = 0
        }
    }

    private fun sizeForProgress(progress: Float, density: Float): Pair<Int, Int> {
        val pad = IslandGeometry.OUTER_PADDING_DP * 2f
        val wDp = lerp(IslandGeometry.PILL_W_DP, IslandGeometry.CARD_W_DP, progress) + pad
        val hDp = lerp(IslandGeometry.PILL_H_DP, IslandGeometry.CARD_H_DP, progress) + pad
        return (wDp * density).toInt() to (hDp * density).toInt()
    }

    private fun startPlacement() {
        placementJob?.cancel()
        placementJob = serviceScope.launch {
            launch {
                IslandPositionStore.position.drop(1).collect { pos ->
                    animY.snapTo(pos.offsetYDp + IslandPositionStore.dodgeY.value)
                }
            }
            launch {
                IslandPositionStore.dodgeY.drop(1).collect { dodge ->
                    val target = IslandPositionStore.position.value.offsetYDp + dodge
                    animY.animateTo(target, dodgeSpring)
                }
            }
            launch {
                combine(
                    snapshotFlow { animY.value },
                    IslandPositionStore.morphProgress,
                ) { y, p -> y to p }.collect { (y, p) ->
                    applyLayoutParams(y, p)
                }
            }
        }
    }

    private fun applyLayoutParams(yDp: Float, progress: Float) {
        val view = islandView ?: return
        val params = view.layoutParams as? WindowManager.LayoutParams ?: return
        val density = resources.displayMetrics.density
        val (w, h) = sizeForProgress(progress, density)
        params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        params.x = 0
        params.y = (yDp * density).toInt()
        params.width = w
        params.height = h
        try {
            windowManager.updateViewLayout(view, params)
        } catch (_: Throwable) {
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        placementJob?.cancel()
        serviceScope.cancel()
        islandView?.let { view ->
            try { windowManager.removeView(view) } catch (_: Throwable) {}
        }
        islandView = null
        lifecycleOwner.destroy()
        IslandPositionStore.setRunning(false)
        super.onDestroy()
    }

    private fun setupNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Assistant Island",
            NotificationManager.IMPORTANCE_LOW,
        ).apply { setShowBadge(false) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openPi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopPi = PendingIntent.getService(
            this, 1,
            Intent(this, IslandOverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.inference_leaf)
            .setContentTitle("Tool Neuron")
            .setContentText("Assistant Island active")
            .setContentIntent(openPi)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Hide", stopPi).build())
            .build()
    }

    companion object {
        const val ACTION_STOP = "com.dark.tool_neuron.island.ACTION_STOP"
        private const val TAG = "IslandOverlayService"
        private const val CHANNEL_ID = "tn_island"
        private const val NOTIFICATION_ID = 0x544E4953 + 1
    }
}
