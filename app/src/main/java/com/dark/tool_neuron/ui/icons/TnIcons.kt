package com.dark.tool_neuron.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

object TnIcons {
    val Menu by lazy { icon("M4 8l16 0", "M4 16l16 0") }
    val More by lazy {
        icon(
            "M 11 12 A 1 1 0 1 0 13 12 A 1 1 0 1 0 11 12 Z",
            "M 11 5 A 1 1 0 1 0 13 5 A 1 1 0 1 0 11 5 Z",
            "M 11 19 A 1 1 0 1 0 13 19 A 1 1 0 1 0 11 19 Z",
        )
    }
}
private fun icon(vararg paths: String): ImageVector {
    return ImageVector.Builder(
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        paths.forEach { svgPath ->
            addPath(
                pathData = PathParser().parsePathString(svgPath).toNodes(),
                stroke = SolidColor(Color.Unspecified),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
        }
    }.build()
}