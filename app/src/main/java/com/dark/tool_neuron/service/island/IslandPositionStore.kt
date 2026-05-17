package com.dark.tool_neuron.service.island

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object IslandPositionStore {

    private const val PREF_NAME = "island_overlay"
    private const val KEY_OFFSET_Y = "offsetY"

    private lateinit var prefs: SharedPreferences

    private val _position = MutableStateFlow(IslandPosition())
    val position: StateFlow<IslandPosition> = _position.asStateFlow()

    private val _dodgeY = MutableStateFlow(0f)
    val dodgeY: StateFlow<Float> = _dodgeY.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _accessibilityActive = MutableStateFlow(false)
    val accessibilityActive: StateFlow<Boolean> = _accessibilityActive.asStateFlow()

    private val _morphProgress = MutableStateFlow(0f)
    val morphProgress: StateFlow<Float> = _morphProgress.asStateFlow()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        _position.value = IslandPosition(
            offsetYDp = prefs.getFloat(KEY_OFFSET_Y, 0f),
        )
    }

    fun setOffset(yDp: Float) {
        _position.value = IslandPosition(offsetYDp = yDp)
        prefs.edit().putFloat(KEY_OFFSET_Y, yDp).apply()
    }

    fun setDodgeY(dyDp: Float) {
        _dodgeY.value = dyDp
    }

    fun setRunning(running: Boolean) {
        _running.value = running
    }

    fun setAccessibilityActive(active: Boolean) {
        _accessibilityActive.value = active
        if (!active) _dodgeY.value = 0f
    }

    fun setMorphProgress(progress: Float) {
        _morphProgress.value = progress
    }
}
