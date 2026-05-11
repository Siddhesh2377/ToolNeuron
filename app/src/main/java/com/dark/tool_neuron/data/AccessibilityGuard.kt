package com.dark.tool_neuron.data

import android.content.Context
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccessibilityGuard @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    fun scan(): Result {
        val raw = try {
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            )
        } catch (_: SecurityException) {
            return Result.Unknown
        } ?: ""

        if (raw.isBlank()) return Result.Clean

        val services = raw.split(":").filter { it.isNotBlank() }
        val unknown = services.mapNotNull { entry ->
            val pkg = entry.substringBefore('/').trim()
            if (pkg.isBlank()) null
            else if (pkg in ALLOWLIST) null
            else pkg
        }

        return if (unknown.isEmpty()) Result.Clean else Result.SuspiciousAttached(unknown.toSet())
    }

    sealed interface Result {
        data object Clean : Result
        data object Unknown : Result
        data class SuspiciousAttached(val packages: Set<String>) : Result
    }

    companion object {
        private val ALLOWLIST = setOf(
            "com.google.android.marvin.talkback",
            "com.google.android.apps.accessibility.voiceaccess",
            "com.google.android.apps.accessibility.reveal",
            "com.samsung.accessibility",
            "com.samsung.android.accessibility.talkback",
            "com.samsung.android.app.talkback",
            "com.miui.accessibility",
            "com.xiaomi.accessibility",
            "com.oneplus.accessibility",
            "com.oppo.accessibility",
            "com.huawei.accessibility",
            "com.android.switchaccess",
            "com.android.systemui",
        )
    }
}
