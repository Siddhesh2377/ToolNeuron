package com.dark.plugin_exc

import com.dark.hxs_encryptor.PolicyEngine
import com.dark.plugin_api.PluginCapability
import com.dark.plugin_api.PluginManifest

class CapabilityGate(private val manifest: PluginManifest) {

    fun require(capability: PluginCapability) {
        if (PolicyEngine.isTampered()) {
            throw SecurityException("plugin operations blocked: tamper latch set")
        }
        if (capability !in manifest.capabilities) {
            throw SecurityException(
                "plugin '${manifest.id}' did not declare capability $capability"
            )
        }
    }

    fun isAllowed(capability: PluginCapability): Boolean =
        !PolicyEngine.isTampered() && capability in manifest.capabilities
}
