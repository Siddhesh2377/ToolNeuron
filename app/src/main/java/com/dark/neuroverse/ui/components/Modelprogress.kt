package com.dark.neuroverse.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dark.ai_module.workers.ModelManager
import com.dark.neuroverse.ui.theme.Coral
import com.dark.neuroverse.ui.theme.Mint

@Composable
fun ModelLoadProgressBar(
    loadState: ModelManager.LoadState,
    modifier: Modifier = Modifier,
    normalColor: Color = Mint,
    errorColor: Color = Coral,
) {
    val show = when (loadState) {
        is ModelManager.LoadState.Loading -> true
        is ModelManager.LoadState.Error -> true
        else -> false
    }


    val target = when (loadState) {
        is ModelManager.LoadState.Loading -> loadState.progress.coerceIn(0f, 1f)
        is ModelManager.LoadState.Error -> 1f // snap to 100% on error
        else -> 0f
    }


    val progress by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 250),
        label = "modelProgressAnim"
    )


    val barColor = when (loadState) {
        is ModelManager.LoadState.Error -> errorColor
        else -> normalColor
    }


    AnimatedVisibility(
        visible = show, enter = fadeIn(tween(150)), exit = fadeOut(tween(150))
    ) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = modifier
                .fillMaxWidth()
                .height(3.dp),
            color = barColor,
            trackColor = barColor.copy(alpha = 0.18f)
        )
    }
}