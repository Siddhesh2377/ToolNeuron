package com.dark.tool_neuron.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.tool_neuron.data.AppPreferences
import com.dark.tool_neuron.data.PinStrength
import com.dark.tool_neuron.data.SecurityManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val prefs: AppPreferences,
    private val security: SecurityManager
) : ViewModel() {

    private val _selectedMode = MutableStateFlow<String?>(null)
    val selectedMode = _selectedMode.asStateFlow()

    // Password setup flow
    private val _password = MutableStateFlow("")
    val password = _password.asStateFlow()

    private val _confirmPassword = MutableStateFlow("")
    val confirmPassword = _confirmPassword.asStateFlow()

    private val _isConfirmStep = MutableStateFlow(false)
    val isConfirmStep = _isConfirmStep.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting = _isSubmitting.asStateFlow()

    fun selectMode(mode: String) {
        _selectedMode.value = mode
        _password.value = ""
        _confirmPassword.value = ""
        _isConfirmStep.value = false
        _error.value = null
    }

    fun appendDigit(digit: Char) {
        if (_isConfirmStep.value) {
            if (_confirmPassword.value.length < MIN_PIN_LENGTH) {
                _confirmPassword.value += digit
                _error.value = null
            }
        } else {
            if (_password.value.length < MIN_PIN_LENGTH) {
                _password.value += digit
                _error.value = null
            }
        }
    }

    fun deleteLast() {
        if (_isConfirmStep.value) {
            val c = _confirmPassword.value
            if (c.isNotEmpty()) _confirmPassword.value = c.dropLast(1)
        } else {
            val p = _password.value
            if (p.isNotEmpty()) _password.value = p.dropLast(1)
        }
        _error.value = null
    }

    fun clearAll() {
        if (_isConfirmStep.value) {
            _confirmPassword.value = ""
        } else {
            _password.value = ""
        }
        _error.value = null
    }

    fun submitPassword(onSuccess: () -> Unit) {
        val pwd = _password.value
        when (val eval = PinStrength.evaluate(pwd)) {
            is PinStrength.Result.TooShort -> {
                _error.value = "At least ${eval.min} digits"
                return
            }
            PinStrength.Result.AllSameDigit -> {
                _error.value = "PIN must use more than one digit"
                return
            }
            PinStrength.Result.Sequential -> {
                _error.value = "PIN cannot be sequential"
                return
            }
            PinStrength.Result.CommonlyUsed -> {
                _error.value = "PIN is too common"
                return
            }
            PinStrength.Result.Ok -> Unit
        }

        if (!_isConfirmStep.value) {
            _isConfirmStep.value = true
            return
        }

        if (_confirmPassword.value != pwd) {
            _confirmPassword.value = ""
            _error.value = "Passwords don't match"
            return
        }

        if (_isSubmitting.value) return
        _isSubmitting.value = true
        viewModelScope.launch {
            withContext(Dispatchers.Default) { security.setPassword(pwd) }
            prefs.setupDone = true
            prefs.securitySetupDone = true
            prefs.onboardingComplete = true
            _isSubmitting.value = false
            onSuccess()
        }
    }

    fun goBack() {
        if (_isConfirmStep.value) {
            _isConfirmStep.value = false
            _confirmPassword.value = ""
            _error.value = null
        } else {
            _selectedMode.value = null
            _password.value = ""
        }
    }

    fun completeWithNoLock() {
        security.setupWithoutLock()
        prefs.setupDone = true
        prefs.securitySetupDone = true
        prefs.onboardingComplete = true
    }

    companion object {
        const val MIN_PIN_LENGTH = 6
    }
}
