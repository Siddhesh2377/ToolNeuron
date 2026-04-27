package com.dark.tool_neuron.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.tool_neuron.data.AppPreferences
import com.dark.tool_neuron.data.RootGuard
import com.dark.tool_neuron.data.SecurityManager
import com.dark.tool_neuron.data.SessionHolder
import com.dark.tool_neuron.model.NavScreens
import com.dark.tool_neuron.service.server.ServerController
import com.dark.tool_neuron.service.server.ServerState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ScaffoldViewModel @Inject constructor(
    private val prefs: AppPreferences,
    private val security: SecurityManager,
    private val rootGuard: RootGuard,
    session: SessionHolder,
    serverController: ServerController,
) : ViewModel() {

    val serverRunning: StateFlow<Boolean> = serverController.state
        .map {
            it is ServerState.Running ||
                it is ServerState.Starting ||
                it is ServerState.LoadingModel
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, serverController.isBusy)

    val shouldLock: StateFlow<Boolean> = combine(
        session.active,
        MutableStateFlow(security.isLockEnabled),
    ) { active, _ ->
        security.isLockEnabled && !active
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = security.isLockEnabled && !session.active.value,
    )

    private val _rootWarning = MutableStateFlow<RootWarning?>(resolveInitialRootWarning())
    val rootWarning: StateFlow<RootWarning?> = _rootWarning.asStateFlow()

    data class RootWarning(val evidence: Set<String>)

    private fun resolveInitialRootWarning(): RootWarning? {
        if (prefs.rootWarningShown) return null
        return when (val r = rootGuard.scan()) {
            is RootGuard.Result.Rooted -> RootWarning(r.evidence)
            else -> null
        }
    }

    fun acknowledgeRootWarning() {
        prefs.rootWarningShown = true
        _rootWarning.value = null
    }

    fun resolveStartDestination(): String {
        val onboarded = prefs.onboardingComplete
        val secDone = prefs.securitySetupDone
        val modelDone = prefs.modelSetupDone
        if (!onboarded) return NavScreens.DevNotes.route
        if (!secDone) return NavScreens.SetupScreen.route
        if (!modelDone) return NavScreens.ModelSetup.route
        if (security.isLockEnabled) return NavScreens.PasswordScreen.route
        return NavScreens.HomeScreen.route
    }

    fun markOnboardingComplete() {
        prefs.onboardingComplete = true
    }

    fun markModelSetupDone() {
        prefs.modelSetupDone = true
    }
}
