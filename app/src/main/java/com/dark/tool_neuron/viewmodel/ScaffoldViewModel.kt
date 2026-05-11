package com.dark.tool_neuron.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.download_manager.HxdManager
import com.dark.download_manager.HxdStatus
import com.dark.hxs_encryptor.BootIntegrity
import com.dark.tool_neuron.TNApplication
import com.dark.tool_neuron.data.AccessibilityGuard
import com.dark.tool_neuron.data.AppPreferences
import com.dark.tool_neuron.data.RootGuard
import com.dark.tool_neuron.data.SecurityManager
import com.dark.tool_neuron.data.SessionHolder
import com.dark.tool_neuron.model.DownloadProgress
import com.dark.tool_neuron.model.NavScreens
import com.dark.tool_neuron.repo.InstallProgressTracker
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
    private val accessibilityGuard: AccessibilityGuard,
    session: SessionHolder,
    serverController: ServerController,
    installProgress: InstallProgressTracker,
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

    val downloadProgress: StateFlow<DownloadProgress?> = combine(
        HxdManager.tasks,
        installProgress.extracting,
    ) { tasks, extracting ->
        val active = tasks.filter {
            it.status == HxdStatus.QUEUED ||
                it.status == HxdStatus.CONNECTING ||
                it.status == HxdStatus.DOWNLOADING
        }
        if (active.isEmpty()) {
            return@combine if (extracting.isNotEmpty()) DownloadProgress.Indeterminate else null
        }
        val totals = active.sumOf { it.totalBytes.coerceAtLeast(0L) }
        val anyUnknown = active.any { it.totalBytes <= 0L }
        if (anyUnknown || totals <= 0L) return@combine DownloadProgress.Indeterminate
        val downloaded = active.sumOf { it.downloadedBytes.coerceAtLeast(0L) }
        DownloadProgress.Determinate((downloaded.toFloat() / totals.toFloat()).coerceIn(0f, 1f))
    }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _rootWarning = MutableStateFlow<RootWarning?>(resolveInitialRootWarning())
    val rootWarning: StateFlow<RootWarning?> = _rootWarning.asStateFlow()

    data class RootWarning(
        val rootEvidence: Set<String>,
        val tamperEvidence: Set<String>,
        val a11yPackages: Set<String>,
    ) {
        val isEmpty: Boolean
            get() = rootEvidence.isEmpty() && tamperEvidence.isEmpty() && a11yPackages.isEmpty()
    }

    private fun resolveInitialRootWarning(): RootWarning? {
        if (prefs.rootWarningShown) return null
        val rootEvidence = when (val r = rootGuard.scan()) {
            is RootGuard.Result.Rooted -> r.evidence
            else -> emptySet()
        }
        val tamperEvidence = mutableSetOf<String>()
        val envBits = TNApplication.softEnvReasons
        if (envBits and BootIntegrity.FAIL_XPOSED != 0) tamperEvidence += "xposed/lspd"
        val a11yPackages = when (val a = accessibilityGuard.scan()) {
            is AccessibilityGuard.Result.SuspiciousAttached -> a.packages
            else -> emptySet()
        }
        val warning = RootWarning(rootEvidence, tamperEvidence.toSet(), a11yPackages)
        return if (warning.isEmpty) null else warning
    }

    fun acknowledgeRootWarning() {
        prefs.rootWarningShown = true
        _rootWarning.value = null
    }

    fun resolveStartDestination(): String {
        val tcAccepted = prefs.tcAccepted
        val onboarded = prefs.onboardingComplete
        val secDone = prefs.securitySetupDone
        val modelDone = prefs.modelSetupDone
        if (!tcAccepted) return NavScreens.TermsConditions.route
        if (!onboarded) return NavScreens.DevNotes.route
        if (!secDone) return NavScreens.SetupScreen.route
        if (!modelDone) return NavScreens.ModelSetup.route
        if (security.isLockEnabled) return NavScreens.PasswordScreen.route
        return NavScreens.HomeScreen.route
    }

    fun markOnboardingComplete() {
        prefs.onboardingComplete = true
    }

    fun markTermsAccepted() {
        if (!prefs.tcAccepted) prefs.tcAccepted = true
    }

    fun markModelSetupDone() {
        prefs.modelSetupDone = true
    }
}
