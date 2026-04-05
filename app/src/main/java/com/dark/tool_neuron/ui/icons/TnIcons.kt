package com.dark.tool_neuron.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

object TnIcons {
    val Menu by lazy { icon("M4 6h16", "M4 12h16", "M4 18h16") }
    val More by lazy {
        icon(
            "M 11 12 A 1 1 0 1 0 13 12 A 1 1 0 1 0 11 12 Z",
            "M 11 5 A 1 1 0 1 0 13 5 A 1 1 0 1 0 11 5 Z",
            "M 11 19 A 1 1 0 1 0 13 19 A 1 1 0 1 0 11 19 Z",
        )
    }
    val ChevronUp by lazy { icon("M6 15L12 9L18 15") }
    val ChevronDown by lazy { icon("M6 9L12 15L18 9") }
    val X by lazy { icon("M6 6L18 18", "M18 6L6 18") }
    val Lock by lazy {
        icon(
            "M8 11V7a4 4 0 0 1 8 0v4",
            "M5 11h14v10H5z"
        )
    }
    val Eye by lazy {
        icon(
            "M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z",
            "M12 10a2 2 0 1 0 0 4 2 2 0 0 0 0-4z"
        )
    }
    val EyeOff by lazy {
        icon(
            "M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94",
            "M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24",
            "M1 1l22 22"
        )
    }
    val Wrench by lazy {
        icon("M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z")
    }
    val PlayerStop by lazy {
        ImageVector.Builder(
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            addPath(
                pathData = PathParser().parsePathString("M7 7h10v10H7z").toNodes(),
                fill = SolidColor(Color.Black)
            )
        }.build()
    }

    val Send by lazy { icon("M10 14l11 -11", "M21 3l-6.5 18a.55 .55 0 0 1 -1 0l-3.5 -7l-7 -3.5a.55 .55 0 0 1 0 -1l18 -6.5") }
    val Download by lazy {
        icon(
            "M12 10L12 2",
            "M16 6L12 10L8 6",
            "M2 15C2.6 15.5 3.2 16 4.5 16C7 16 7 14 9.5 14C12.1 14 11.9 16 14.5 16C17 16 17 14 19.5 14C20.8 14 21.4 14.5 22 15",
            "M2 21C2.6 21.5 3.2 22 4.5 22C7 22 7 20 9.5 20C12.1 20 11.9 22 14.5 22C17 22 17 20 19.5 20C20.8 20 21.4 20.5 22 21"
        )
    }
    val Leaf by lazy {
        icon(
            "M11 20A7 7 0 0 1 9.8 6.1C15.5 5 17 4.48 19 2c1 2 2 4.18 2 8 0 5.5-4.78 10-10 10Z",
            "M2 21c0-3 1.85-5.36 5.08-6C9.5 14.52 12 13 13 12"
        )
    }
    val HatGlasses by lazy {
        icon(
            "M14 18a2 2 0 0 0-4 0",
            "m19 11-2.11-6.657a2 2 0 0 0-2.752-1.148l-1.276.61A2 2 0 0 1 12 4H8.5a2 2 0 0 0-1.925 1.456L5 11",
            "M2 11h20",
            "M17 15a3 3 0 1 0 0 6 3 3 0 0 0 0-6z",
            "M7 15a3 3 0 1 0 0 6 3 3 0 0 0 0-6z"
        )
    }
    val TextSize by lazy {
        icon(
            "M3 7v-2h13v2",
            "M10 5v14",
            "M12 19h-4",
            "M15 13v-1h6v1",
            "M18 12v7",
            "M17 19h2"
        )
    }
    val Photo by lazy { icon("M15 8h.01", "M3 6a3 3 0 0 1 3 -3h12a3 3 0 0 1 3 3v12a3 3 0 0 1 -3 3h-12a3 3 0 0 1 -3 -3v-12z", "M3 16l5 -5c.928 -.893 2.072 -.893 3 0l4 4", "M14 14l1 -1c.928 -.893 2.072 -.893 3 0l3 3") }

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
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
        }
    }.build()
}
