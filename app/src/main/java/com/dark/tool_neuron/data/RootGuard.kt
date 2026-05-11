package com.dark.tool_neuron.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RootGuard @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    sealed interface Result {
        data object Clean : Result
        data class Rooted(val evidence: Set<String>) : Result
    }

    fun scan(): Result {
        val evidence = mutableSetOf<String>()

        for (path in SU_PATHS) {
            if (File(path).exists()) evidence += "su:$path"
        }

        val tags = Build.TAGS
        if (tags != null && tags.contains("test-keys")) evidence += "tags:test-keys"

        val pm = context.packageManager
        for (pkg in ROOT_MANAGERS) {
            if (isInstalled(pm, pkg)) evidence += "pkg:$pkg"
        }

        for (path in MAGISK_HINTS) {
            if (File(path).exists()) evidence += "magisk:$path"
        }

        return if (evidence.isEmpty()) Result.Clean else Result.Rooted(evidence)
    }

    private fun isInstalled(pm: PackageManager, pkg: String): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(pkg, 0)
        }
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    } catch (_: Throwable) {
        false
    }

    companion object {
        private val SU_PATHS = listOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/data/local/su",
            "/su/bin/su",
            "/magisk/.core/bin/su",
        )
        private val MAGISK_HINTS = listOf(
            "/sbin/.magisk",
            "/cache/.disable_magisk",
            "/dev/magisk.img",
            "/data/adb/magisk",
        )
        private val ROOT_MANAGERS = listOf(
            "com.topjohnwu.magisk",
            "eu.chainfire.supersu",
            "com.noshufou.android.su",
            "com.noshufou.android.su.elite",
            "com.koushikdutta.superuser",
            "com.thirdparty.superuser",
            "com.yellowes.su",
            "com.kingroot.kinguser",
            "com.kingo.root",
            "com.zachspong.temprootremovejb",
            "com.ramdroid.appquarantine",
        )
    }
}
