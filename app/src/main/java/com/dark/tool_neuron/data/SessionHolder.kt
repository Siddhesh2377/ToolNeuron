package com.dark.tool_neuron.data

import com.dark.hxs_encryptor.AuthNative
import com.dark.hxs_encryptor.HxsEncryptor
import com.dark.hxs_encryptor.PolicyEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionHolder @Inject constructor(
    private val encryptor: HxsEncryptor,
) {

    @Volatile private var token: ByteArray? = null

    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    fun set(newToken: ByteArray) {
        token?.let { encryptor.secureWipe(it) }
        token = newToken
        _active.value = true
    }

    fun get(): ByteArray? = token

    fun require(): ByteArray =
        token ?: throw SecurityException("No active session")

    fun clear() {
        token?.let { encryptor.secureWipe(it) }
        token = null
        _active.value = false
        AuthNative.invalidate()
    }

    fun isAllowed(feature: PolicyEngine.Feature): Boolean =
        PolicyEngine.isAllowed(feature, token)

    fun requireAllowed(feature: PolicyEngine.Feature) {
        PolicyEngine.requireAllowed(feature, token)
    }
}
