package com.dark.tool_neuron

import android.app.ActivityManager
import android.app.Application
import android.os.Build
import android.os.Process
import android.util.Log
import com.dark.hxs_encryptor.BootIntegrity
import com.dark.tn_security.LogcatSink
import com.dark.tn_security.TnModule
import com.dark.tn_security.TnSecurity
import com.dark.tool_neuron.data.AccessibilityGuard
import com.dark.tool_neuron.service.inference.HxsTnSink
import com.dark.networking.WebNative
import com.dark.tool_neuron.service.inference.InferenceClient
import com.dark.tool_neuron.data.AppLockObserver
import com.dark.tool_neuron.data.NativeIntegrity
import com.dark.tool_neuron.plugin_host.PluginContainerHost
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class TNApplication : Application() {

    @Inject lateinit var integrityLazy: Lazy<NativeIntegrity>
    @Inject lateinit var appLockObserverLazy: Lazy<AppLockObserver>
    @Inject lateinit var accessibilityGuardLazy: Lazy<AccessibilityGuard>
    @Inject lateinit var pluginContainerHostLazy: Lazy<PluginContainerHost>

    override fun onCreate() {
        super.onCreate()

        // tn_security init runs FIRST so every subsequent boot step can emit
        // structured events through it. App process owns its own TnSecurity
        // instance distinct from the inference service's; service events
        // arrive over AIDL via InferenceClient.tnCallback.
        try {
            val crashDir = java.io.File(filesDir, "tn_security/crashes")
            TnSecurity.init(
                context = this,
                module = TnModule.TN_APP,
                crashDir = crashDir,
                installSignalHandlers = true,
            )
            TnSecurity.addSink(LogcatSink())
            TnSecurity.addSink(HxsTnSink(filesDir))
            // Replay any crash JSON left by a previous main-process death (signal
            // handlers wrote them but no sink saw the Crash event because the
            // process was already gone). Idempotent; safe on every cold start.
            try { TnSecurity.drainCrashFiles(crashDir) }
            catch (t: Throwable) { Log.w(TAG, "drainCrashFiles failed", t) }
        } catch (t: Throwable) {
            Log.e(TAG, "tn_security init failed in app process", t)
        }

        if (!isMainProcess()) {
            Log.i(TAG, "non-main process (${currentProcessName()}), skipping boot init")
            return
        }

        // Install the bundled CA bundle + curl-impersonate Chrome116 profile
        // BEFORE any networking call can fire. http_execute in :networking
        // refuses to issue requests when no CA bundle is configured (fail-
        // closed TLS), so this must run early in the main-process boot
        // sequence. Idempotent via WebNative.ensureReady's caInstalled gate.
        try { WebNative.ensureReady(this) }
        catch (t: Throwable) { Log.e(TAG, "WebNative.ensureReady failed", t) }

        // Bind to :inference here (not MainActivity) so RAG / SD / STT calls
        // initiated by background work or by ViewModels constructed before the
        // first Activity don't time out on the 15 s ensureBound wait.
        try { InferenceClient.bind(this) }
        catch (t: Throwable) { Log.e(TAG, "InferenceClient.bind failed", t) }

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
