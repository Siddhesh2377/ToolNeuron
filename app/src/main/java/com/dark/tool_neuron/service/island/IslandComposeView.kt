package com.dark.tool_neuron.service.island

import android.content.Context
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView

class IslandComposeView(context: Context) : FrameLayout(context) {

    private val composeView = ComposeView(context).also {
        addView(it, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun setContent(content: @Composable () -> Unit) {
        composeView.setContent(content)
    }
}
