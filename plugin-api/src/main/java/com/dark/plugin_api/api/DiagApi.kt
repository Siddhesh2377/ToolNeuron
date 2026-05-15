package com.dark.plugin_api.api

/**
 * Minimal diagnostic surface published to plugin DEX code. Plugins emit log
 * lines, errors, and cancellations through here; the host implementation
 * forwards into the app-process TnSecurity instance which fans out to the
 * sinks (logcat + hxs persistence + UI dialog/Activity).
 *
 * Codes/stages use the host enum's numeric values from `:tn_security` —
 * see TnCode / TnStage in that module. The plugin module deliberately does
 * NOT depend on tn_security directly (would couple plugin ABI to the SDK
 * version); plugins pass through integer codes and the host adapts.
 */
interface DiagApi {

    fun log(
        level:   Int,           // 0=trace, 1=debug, 2=info, 3=warn, 4=error, 5=fatal
        message: String,
        tag:     String? = null,
        opId:    String? = null,
        file:    String? = null,
        line:    Int     = 0,
        func:    String? = null,
    )

    fun error(
        code:       Int,        // see TnCode.value
        stage:      Int,        // see TnStage.value
        message:    String,
        suggestion: String? = null,
        opId:       String? = null,
        file:       String? = null,
        line:       Int     = 0,
        func:       String? = null,
    )

    fun cancel(opId: String? = null, reason: String? = null)
}
