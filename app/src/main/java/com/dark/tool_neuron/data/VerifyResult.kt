package com.dark.tool_neuron.data

sealed interface VerifyResult {
    data object Success : VerifyResult
    data object WrongPin : VerifyResult
    data class LockedOut(val retryAtMs: Long) : VerifyResult
    data object Wiped : VerifyResult
    data object NoLock : VerifyResult
}

object LockoutPolicy {
    const val WIPE_THRESHOLD = 10

    fun backoffMillis(failedAttempts: Int): Long = when {
        failedAttempts <= 3 -> 0L
        failedAttempts == 4 -> 60_000L
        failedAttempts == 5 -> 5 * 60_000L
        failedAttempts == 6 -> 15 * 60_000L
        failedAttempts == 7 -> 60 * 60_000L
        failedAttempts == 8 -> 4 * 60 * 60_000L
        failedAttempts == 9 -> 12 * 60 * 60_000L
        else -> 24 * 60 * 60_000L
    }
}
