package com.dark.tool_neuron.data

import com.dark.hxs_encryptor.AuthNative
import com.dark.hxs_encryptor.HxsEncryptor
import com.dark.hxs_encryptor.PolicyEngine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecurityManager @Inject constructor(
    private val prefs: AppPreferences,
    private val session: SessionHolder,
    private val encryptor: HxsEncryptor,
    private val keyStore: AppKeyStore,
) {

    init {
        val state = prefs.readAuthState()
        PolicyEngine.setPassthrough(state.securityMode != AppPreferences.SECURITY_APP_PASSWORD)
    }

    val isLockEnabled: Boolean
        get() = prefs.readAuthState().securityMode == AppPreferences.SECURITY_APP_PASSWORD

    val hasPanicPin: Boolean
        get() = prefs.readAuthState().hasPanic

    fun snapshotLockoutState(): AuthState = prefs.readAuthState()

    fun setPassword(password: String) {
        val pin = password.toByteArray(Charsets.UTF_8)
        try {
            val result = AuthNative.setup(pin)
            prefs.writeAuthState(
                AuthState(
                    securityMode = AppPreferences.SECURITY_APP_PASSWORD,
                    salt = result.salt,
                    hash = result.hash,
                    failedAttempts = 0,
                    nextAttemptAtMs = 0L,
                ),
            )
            encryptor.secureWipe(result.hash)
            PolicyEngine.setPassthrough(false)
        } finally {
            encryptor.secureWipe(pin)
        }
    }

    fun verifyPassword(password: String, nowMs: Long = System.currentTimeMillis()): VerifyResult {
        val state = prefs.readAuthState()
        if (state.securityMode != AppPreferences.SECURITY_APP_PASSWORD) {
            return VerifyResult.NoLock
        }
        if (state.salt.isEmpty() || state.hash.isEmpty()) {
            return VerifyResult.NoLock
        }

        val clockRolledBack = state.lastSeenNowMs > 0L && nowMs + CLOCK_SKEW_GRACE_MS < state.lastSeenNowMs

        val pin = password.toByteArray(Charsets.UTF_8)
        try {
            if (state.hasPanic) {
                val panicToken = AuthNative.verify(pin, state.panicSalt, state.panicHash)
                if (panicToken != null) {
                    encryptor.secureWipe(panicToken)
                    hardWipe()
                    return VerifyResult.Wiped
                }
            }

            if (state.nextAttemptAtMs > nowMs && !clockRolledBack) {
                return VerifyResult.LockedOut(state.nextAttemptAtMs)
            }

            val token = AuthNative.verify(pin, state.salt, state.hash)
            if (token != null && !clockRolledBack) {
                session.set(token)
                prefs.writeAuthState(
                    state.copy(
                        failedAttempts = 0,
                        nextAttemptAtMs = 0L,
                        lastSeenNowMs = maxOf(nowMs, state.lastSeenNowMs),
                    ),
                )
                return VerifyResult.Success
            }
            if (token != null) {
                encryptor.secureWipe(token)
            }

            val bump = if (clockRolledBack) 2 else 1
            val nextAttempts = state.failedAttempts + bump
            if (nextAttempts >= LockoutPolicy.WIPE_THRESHOLD) {
                hardWipe()
                return VerifyResult.Wiped
            }

            val backoff = LockoutPolicy.backoffMillis(nextAttempts)
            val baseNow = maxOf(nowMs, state.lastSeenNowMs)
            val retryAt = if (backoff == 0L) 0L else baseNow + backoff
            prefs.writeAuthState(
                state.copy(
                    failedAttempts = nextAttempts,
                    nextAttemptAtMs = retryAt,
                    lastSeenNowMs = baseNow,
                ),
            )
            return if (retryAt > baseNow) VerifyResult.LockedOut(retryAt) else VerifyResult.WrongPin
        } finally {
            encryptor.secureWipe(pin)
        }
    }

    fun setPanicPin(panicPin: String): Boolean {
        val current = prefs.readAuthState()
        if (current.securityMode != AppPreferences.SECURITY_APP_PASSWORD) return false

        val pinBytes = panicPin.toByteArray(Charsets.UTF_8)
        try {
            val result = AuthNative.setup(pinBytes)
            prefs.writeAuthState(current.copy(panicSalt = result.salt, panicHash = result.hash))
            encryptor.secureWipe(result.hash)
            return true
        } finally {
            encryptor.secureWipe(pinBytes)
        }
    }

    fun clearPanicPin(): Boolean {
        val current = prefs.readAuthState()
        if (current.securityMode != AppPreferences.SECURITY_APP_PASSWORD) return false
        prefs.writeAuthState(current.copy(panicSalt = ByteArray(0), panicHash = ByteArray(0)))
        return true
    }

    fun disableLock(): Boolean {
        val current = prefs.readAuthState()
        if (current.securityMode != AppPreferences.SECURITY_APP_PASSWORD) return false
        prefs.writeAuthState(AuthState.DEFAULT)
        session.clear()
        PolicyEngine.setPassthrough(true)
        return true
    }

    fun setupWithoutLock() {
        val current = prefs.readAuthState()
        if (current.securityMode == AppPreferences.SECURITY_APP_PASSWORD) {
            throw SecurityException("Password is set — use disableLock with an active session")
        }
        prefs.writeAuthState(AuthState.DEFAULT)
        PolicyEngine.setPassthrough(true)
    }

    fun hardWipe() {
        session.clear()
        PolicyEngine.invalidateSession()
        PolicyEngine.markTampered()
        prefs.clearAuthState()
        keyStore.wipe()
    }

    companion object {
        private const val CLOCK_SKEW_GRACE_MS = 5 * 60_000L
    }
}
