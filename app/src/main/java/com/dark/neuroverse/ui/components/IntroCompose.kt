package com.dark.neuroverse.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


internal val paths = listOf(
// Left glyph
    """
M0.575989 0.359985H26.976V8.61598H25.632L24.096 3.28798C24 2.87198 23.888 2.58398 23.76 2.42398C23.664 2.23199 23.456 2.11998 23.136 2.08798C22.816 2.05598 22.272 2.03998 21.504 2.03998H16.656V28.968C16.656 30.056 16.704 30.712 16.8 30.936C16.896 31.128 17.2 31.256 17.712 31.32L20.352 31.656V33H7.19999V31.656L9.83999 31.32C10.352 31.256 10.656 31.128 10.752 30.936C10.848 30.712 10.896 30.056 10.896 28.968V2.03998H6.04799C5.31199 2.03998 4.76799 2.05598 4.41599 2.08798C4.09599 2.11998 3.88799 2.23199 3.79199 2.42398C3.69599 2.58398 3.58399 2.87198 3.45599 3.28798L1.91999 8.61598H0.575989V0.359985Z
""".trimIndent(),


// Right glyph
    """
M60.6169 1.70399V0.359985H69.9289V1.70399L67.2889 2.03998C66.7769 2.10398 66.4729 2.24798 66.3769 2.47198C66.2809 2.66398 66.2329 3.30398 66.2329 4.39198V33H63.5929L45.9769 4.53598V28.968C45.9769 30.056 46.0249 30.712 46.1209 30.936C46.2169 31.128 46.5209 31.256 47.0329 31.32L49.6729 31.656V33H40.3609V31.656L43.0009 31.32C43.5129 31.256 43.8169 31.128 43.9129 30.936C44.0089 30.712 44.0569 30.056 44.0569 28.968V4.39198C44.0569 3.30398 44.0089 2.66398 43.9129 2.47198C43.8169 2.24798 43.5129 2.10398 43.0009 2.03998L40.3609 1.70399V0.359985H50.0089L64.3129 23.352V4.39198C64.3129 3.30398 64.2649 2.66398 64.1689 2.47198C64.0729 2.24798 63.7689 2.10398 63.2569 2.03998L60.6169 1.70399Z
""".trimIndent()
)


@Composable
fun IntroComposable() {
    val parsedPaths = remember {
        paths.map { PathParser().parsePathString(it).toPath() }
    }

    val progresses = remember {
        paths.map { Animatable(0f) }
    }

    val primaryColor = MaterialTheme.colorScheme.primary

    LaunchedEffect(Unit) {
        progresses.forEachIndexed { i, anim ->
            delay(i * 500L)
            launch {
                anim.animateTo(1f, animationSpec = tween(2000, easing = LinearEasing))
            }
        }
    }

    val combinedPath = remember {
        Path().apply {
            parsedPaths.forEach { addPath(it) }
        }
    }
    val combinedBounds = combinedPath.getBounds()

    Canvas(modifier = Modifier.height(100.dp)) {
        val centerX = size.width / 2f
        val centerY = size.height / 2f + 200
        val scaleFactor = 2.6f

        val offsetX = centerX - (combinedBounds.width / 2f) * scaleFactor
        val offsetY = centerY - (combinedBounds.height / 2f) * scaleFactor

        translate(left = offsetX, top = offsetY) {
            scale(scaleFactor) {
                parsedPaths.forEachIndexed { i, path ->
                    val dst = Path()
                    val measure = PathMeasure()
                    measure.setPath(path, false)

                    measure.getSegment(0f, measure.length * progresses[i].value, dst, true)

                    drawPath(
                        path = dst,
                        color = primaryColor,
                        style = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }
        }
    }
}