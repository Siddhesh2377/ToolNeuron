package com.dark.tool_neuron.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import com.dark.tool_neuron.data.AppPreferences
import com.dark.tool_neuron.data.SecurityManager
import com.dark.tool_neuron.model.NavScreens
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ScaffoldViewModel @Inject constructor(
    private val prefs: AppPreferences,
    private val security: SecurityManager
) : ViewModel() {

    fun resolveStartDestination(): String {
        val onboarded = prefs.onboardingComplete
        val secDone = prefs.securitySetupDone
        val modelDone = prefs.modelSetupDone
        Log.d("ScaffoldVM", "resolveStart: onboarded=$onboarded, securityDone=$secDone, modelDone=$modelDone")
        if (!onboarded) return NavScreens.DevNotes.route
        if (!secDone) return NavScreens.SetupScreen.route
        if (!modelDone) return NavScreens.ModelSetup.route
        if (security.isAppPassword) return NavScreens.PasswordScreen.route
        return NavScreens.HomeScreen.route
    }

    fun markOnboardingComplete() {
        prefs.onboardingComplete = true
    }

    fun markModelSetupDone() {
        prefs.modelSetupDone = true
    }
}
