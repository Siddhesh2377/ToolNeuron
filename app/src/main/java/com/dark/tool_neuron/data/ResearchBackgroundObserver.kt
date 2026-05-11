package com.dark.tool_neuron.data

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.dark.tool_neuron.viewmodel.ResearchCoordinator
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ResearchBackgroundObserver @Inject constructor(
    private val coordinator: ResearchCoordinator,
    private val prefs: AppPreferences,
) : DefaultLifecycleObserver {

    fun register() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStop(owner: LifecycleOwner) {
        if (prefs.researchCancelOnBackground) {
            coordinator.cancelAll(reason = "Cancelled (app backgrounded)")
        }
    }
}
