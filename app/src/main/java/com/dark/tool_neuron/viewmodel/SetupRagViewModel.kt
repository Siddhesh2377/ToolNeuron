package com.dark.tool_neuron.viewmodel

import androidx.lifecycle.ViewModel
import com.dark.tool_neuron.data.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class SetupRagViewModel @Inject constructor(
    private val prefs: AppPreferences,
) : ViewModel() {

    private val _smartRerank = MutableStateFlow(prefs.ragSmartRerank)
    val smartRerank: StateFlow<Boolean> = _smartRerank.asStateFlow()

    private val _multiQuery = MutableStateFlow(prefs.ragMultiQuery)
    val multiQuery: StateFlow<Boolean> = _multiQuery.asStateFlow()

    private val _deepResearch = MutableStateFlow(prefs.ragDeepResearch)
    val deepResearch: StateFlow<Boolean> = _deepResearch.asStateFlow()

    fun setSmartRerank(value: Boolean) {
        prefs.ragSmartRerank = value
        _smartRerank.value = value
    }

    fun setMultiQuery(value: Boolean) {
        prefs.ragMultiQuery = value
        _multiQuery.value = value
    }

    fun setDeepResearch(value: Boolean) {
        prefs.ragDeepResearch = value
        _deepResearch.value = value
    }
}
