package com.dark.tool_neuron.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.tool_neuron.data.SecurityManager
import com.dark.tool_neuron.data.VerifyResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PasswordViewModel @Inject constructor(
    private val securityManager: SecurityManager
) : ViewModel() {

    private val _password = MutableStateFlow("")
    val password = _password.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _isVerifying = MutableStateFlow(false)
    val isVerifying = _isVerifying.asStateFlow()

    private val _unlocked = MutableStateFlow(false)
    val unlocked = _unlocked.asStateFlow()

    private val _wiped = MutableStateFlow(false)
    val wiped = _wiped.asStateFlow()

    private val _lockedUntilMs = MutableStateFlow(securityManager.snapshotLockoutState().nextAttemptAtMs)
    val lockedUntilMs = _lockedUntilMs.asStateFlow()

    val maxLength = MIN_PIN_LENGTH
    val minLength = MIN_PIN_LENGTH

    fun appendDigit(digit: Char) {
        if (_password.value.length < maxLength) {
            _password.value += digit
            _error.value = null
        }
    }

    fun deleteLast() {
        val current = _password.value
        if (current.isNotEmpty()) {
            _password.value = current.dropLast(1)
            _error.value = null
        }
    }

    fun clearAll() {
        _password.value = ""
        _error.value = null
    }

    fun reset() {
        _password.value = ""
        _error.value = null
        _isVerifying.value = false
        _unlocked.value = false
        _wiped.value = false
        _lockedUntilMs.value = 0L
    }

    fun submit() {
        val pwd = _password.value
        if (pwd.length < MIN_PIN_LENGTH) {
            _error.value = "At least $MIN_PIN_LENGTH digits"
            return
        }

        _isVerifying.value = true
        viewModelScope.launch(Dispatchers.Default) {
            val outcome = securityManager.verifyPassword(pwd)
            _isVerifying.value = false
            when (outcome) {
                VerifyResult.Success -> {
                    _unlocked.value = true
                }
                VerifyResult.WrongPin -> {
                    _password.value = ""
                    _error.value = "Wrong PIN"
                }
                is VerifyResult.LockedOut -> {
                    _password.value = ""
                    _lockedUntilMs.value = outcome.retryAtMs
                    _error.value = "Locked — try again later"
                }
                VerifyResult.Wiped -> {
                    _password.value = ""
                    _wiped.value = true
                    _error.value = "Too many attempts — vault wiped"
                }
                VerifyResult.NoLock -> {
                    _unlocked.value = true
                }
            }
        }
    }

    companion object {
        const val MIN_PIN_LENGTH = 6
    }
}
