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
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class TNApplication : Application() {

    @Inject lateinit var integrityLazy: Lazy<NativeIntegrity>
    @Inject lateinit var appLockObserverLazy: Lazy<AppLockObserver>
    @Inject lateinit var accessibilityGuardLazy: Lazy<AccessibilityGuard>
    @Inject lateinit var researchBackgroundObserverLazy: Lazy<ResearchBackgroundObserver>

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
        if (envReasons != BootIntegrity.FAIL_NONE) {
            Log.w(TAG, "env reasons=$envReasons")
            BootIntegrity.hardFail(envReasons)
            return
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
                if (!BuildConfig.DEBUG) {
                    BootIntegrity.hardFail(BootIntegrity.FAIL_XPOSED)
                    return
                }
            }
            else -> Unit
        }

        appLockObserver.register()
        researchBackgroundObserverLazy.get().register()
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
    }
}
