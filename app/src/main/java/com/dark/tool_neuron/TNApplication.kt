package com.dark.tool_neuron

import android.app.ActivityManager
import android.app.Application
import android.os.Build
import android.os.Process
import android.util.Log
import com.dark.hxs_encryptor.BootIntegrity
import com.dark.tool_neuron.data.AccessibilityGuard
import com.dark.tool_neuron.data.AppLockObserver
import com.dark.tool_neuron.data.NativeIntegrity
import com.dark.tool_neuron.data.ResearchBackgroundObserver
import com.dark.tool_neuron.plugin_host.PluginContainerHost
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class TNApplication : Application() {

    @Inject lateinit var integrityLazy: Lazy<NativeIntegrity>
    @Inject lateinit var appLockObserverLazy: Lazy<AppLockObserver>
    @Inject lateinit var accessibilityGuardLazy: Lazy<AccessibilityGuard>
    @Inject lateinit var researchBackgroundObserverLazy: Lazy<ResearchBackgroundObserver>
    @Inject lateinit var pluginContainerHostLazy: Lazy<PluginContainerHost>

    override fun onCreate() {
        super.onCreate()

        if (!isMainProcess()) {
            Log.i(TAG, "non-main process (${currentProcessName()}), skipping boot init")
            return
        }

        val integrity = integrityLazy.get()
        val appLockObserver = appLockObserverLazy.get()
        val accessibilityGuard = accessibilityGuardLazy.get()

        val envReasons = integrity.scanProcessEnvironment()
        val hardEnvReasons = envReasons and (BootIntegrity.FAIL_DEBUGGER or BootIntegrity.FAIL_FRIDA)
        if (hardEnvReasons != BootIntegrity.FAIL_NONE) {
            Log.w(TAG, "env reasons=$envReasons (hard=$hardEnvReasons)")
            BootIntegrity.hardFail(hardEnvReasons)
            return
        }
        if (envReasons and BootIntegrity.FAIL_XPOSED != 0) {
            Log.w(TAG, "xposed/lspd-like signal detected; deferring to user warning")
            softEnvReasons = envReasons
        }

        val outcome = integrity.bootVerify()
        if (!outcome.ok) {
            Log.w(TAG, "boot verify failed reasons=${outcome.reasons}")
            BootIntegrity.hardFail(outcome.reasons)
            return
        }

        if (!BootIntegrity.verifyHookBaselines()) {
            Log.w(TAG, "hook baseline mismatch detected")
            BootIntegrity.hardFail(BootIntegrity.FAIL_INLINE_HOOK)
            return
        }

        when (val a11y = accessibilityGuard.scan()) {
            is AccessibilityGuard.Result.SuspiciousAttached -> {
                Log.w(TAG, "unknown a11y services attached: ${a11y.packages}")
            }
            else -> Unit
        }

        appLockObserver.register()
        researchBackgroundObserverLazy.get().register()
        pluginContainerHostLazy.get()
    }

    private fun isMainProcess(): Boolean = currentProcessName() == packageName

    private fun currentProcessName(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return Application.getProcessName()
        }
        val pid = Process.myPid()
        val am = getSystemService(ACTIVITY_SERVICE) as? ActivityManager
        return am?.runningAppProcesses?.firstOrNull { it.pid == pid }?.processName ?: ""
    }

    companion object {
        private const val TAG = "TNApplication"

        @Volatile
        var softEnvReasons: Int = 0
            internal set
    }
}
