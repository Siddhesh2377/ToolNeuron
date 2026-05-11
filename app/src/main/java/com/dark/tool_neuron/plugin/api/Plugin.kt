package com.dark.tool_neuron.plugin.api

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

interface Plugin {

    val id: String

    val displayName: String

    val description: String

    val icon: ImageVector

    val tools: List<ToolDef>

    @Composable
    fun Settings()

    suspend fun execute(toolName: String, argsJson: String): Result<String>
}
