package com.dark.tool_neuron.data

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppVisibility @Inject constructor() : DefaultLifecycleObserver {
    private val foreground = AtomicBoolean(false)

    val isForeground: Boolean
        get() = foreground.get()

    fun register() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        foreground.set(true)
    }

    override fun onStop(owner: LifecycleOwner) {
        foreground.set(false)
    }
}
