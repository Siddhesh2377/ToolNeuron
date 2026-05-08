package com.dark.tool_neuron.viewmodel

import androidx.lifecycle.ViewModel
import com.dark.tool_neuron.data.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class TermsConditionsViewModel @Inject constructor(
    private val prefs: AppPreferences,
) : ViewModel() {

    val alreadyAccepted: Boolean
        get() = prefs.tcAccepted

    fun accept() {
        if (!prefs.tcAccepted) prefs.tcAccepted = true
    }
}
