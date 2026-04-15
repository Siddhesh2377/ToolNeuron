package com.dark.tool_neuron.models.plugins

/**
 * Sealed class representing the different states a tool can be in
 */
sealed class ToolState {
    /**
     * Tool is idle, waiting to be called
     */
    data object Idle : ToolState()

    /**
     * Tool is currently executing
     * @param message Optional progress message
     */
    data class InProgress(val message: String = "Processing...") : ToolState()

    /**
     * Tool execution completed successfully
     * @param data The result data
     * @param message Success message
     */
    data class Success(val data: Any, val message: String = "Success") : ToolState()

    /**
     * Tool execution failed
     * @param error The error that occurred
     * @param message Error message
     */
    data class Error(val error: Throwable, val message: String = error.message ?: "Unknown error") : ToolState()
}
