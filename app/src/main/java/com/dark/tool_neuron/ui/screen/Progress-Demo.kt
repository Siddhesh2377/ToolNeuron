package com.dark.tool_neuron.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.ui.components.PixelProgressBar
import com.dark.tool_neuron.ui.components.ProgressMode

@Composable
fun ProgressDemoScreen(paddingValues: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(paddingValues)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        var progress by remember { mutableFloatStateOf(0f) }
        var isLoading by remember { mutableStateOf(true) }

        // Indeterminate Progress Bar
        Text("Indeterminate Mode (Loading)")
        PixelProgressBar(
            modifier = Modifier.fillMaxWidth(),
            mode = ProgressMode.INDETERMINATE,
            color = Color(0xFF6200EE),
            backgroundColor = Color(0xFFE0E0E0),
            rows = 2,
            cornerRadius = CornerRadius(2f, 2f),
            pixelSize = 8.dp,
            pixelGap = 3.dp,
            indeterminateSpeed = 1200,
            indeterminateWidth = 0.3f
        )

        // Static Progress Bar (no animation)
        Text("Static Progress (60%)")
        PixelProgressBar(
            progress = progress,
            modifier = Modifier.fillMaxWidth(),
            mode = ProgressMode.DETERMINATE,
            color = Color(0xFFFF6B6B),
            backgroundColor = Color(0xFFE0E0E0),
            rows = 3,
            cornerRadius = CornerRadius(4f, 4f),
            pixelSize = 6.dp,
            pixelGap = 2.dp,
            shimmerEnabled = true // Disable shimmer cleanly
        )


        LaunchedEffect(isLoading) {
            while (isLoading) {
                kotlinx.coroutines.delay(50)
                progress = (progress + 0.01f).coerceIn(0f, 1f)
                if (progress >= 1f) {
                    isLoading = false
                }
            }
        }
    }
}