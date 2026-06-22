package com.dark.tool_neuron.model

enum class ModelInstallPhase(val isActive: Boolean, val label: String) {
    IDLE(false, "Idle"),
    QUEUED(true, "Queued"),
    DOWNLOADING(true, "Downloading"),
    INSTALLING(true, "Installing"),
    VERIFYING(true, "Verifying"),
    INSTALLED(false, "Installed"),
    FAILED(false, "Failed"),
    CANCELLED(false, "Cancelled"),
}

data class ModelInstallProgress(
    val modelId: String,
    val installKey: String,
    val displayName: String,
    val type: String,
    val phase: ModelInstallPhase,
    val message: String = "",
    val hxdId: Int? = null,
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val isActive: Boolean get() = phase.isActive
}
