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
    val AlertTriangle by lazy { icon("M12 9v4", "M10.363 3.591l-8.106 13.534a1.914 1.914 0 0 0 1.636 2.871h16.214a1.914 1.914 0 0 0 1.636 -2.87l-8.106 -13.536a1.914 1.914 0 0 0 -3.274 0", "M12 16h.01") }

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

    val Copy by lazy {
        icon(
            "M10 8h10a2 2 0 0 1 2 2v10a2 2 0 0 1 -2 2h-10a2 2 0 0 1 -2 -2v-10a2 2 0 0 1 2 -2z",
            "M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2"
        )
    }
    val Code by lazy { icon("m16 18 6-6-6-6", "m8 6-6 6 6 6") }
    val Shield by lazy {
        icon(
            "M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"
        )
    }
    val Backspace by lazy {
        icon(
            "M21 4H8l-7 8 7 8h13a2 2 0 0 0 2-2V6a2 2 0 0 0-2-2z",
            "M18 9l-6 6",
            "M12 9l6 6"
        )
    }
    val ArrowLeft by lazy { icon("M19 12H5", "M12 19l-7-7 7-7") }
    val Refresh by lazy { icon("M21 12a9 9 0 0 0-9-9 9.75 9.75 0 0 0-6.74 2.74L3 8", "M3 3v5h5", "M3 12a9 9 0 0 0 9 9 9.75 9.75 0 0 0 6.74-2.74L21 16", "M21 21v-5h-5") }
    val Search by lazy { icon("M11 3a8 8 0 1 0 0 16 8 8 0 0 0 0-16z", "M21 21l-4.35-4.35") }
    val Trash by lazy { icon("M3 6h18", "M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6", "M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2") }
    val Info by lazy { icon("M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20z", "M12 16v-4", "M12 8h.01") }
    val Sparkles by lazy { icon("M12 3l1.912 5.813a2 2 0 0 0 1.275 1.275L21 12l-5.813 1.912a2 2 0 0 0-1.275 1.275L12 21l-1.912-5.813a2 2 0 0 1-1.275-1.275L3 12l5.813-1.912a2 2 0 0 1 1.275-1.275L12 3z") }
    val Check by lazy { icon("M20 6L9 17l-5-5") }
    val Plus by lazy { icon("M12 5v14", "M5 12h14") }
    val Edit by lazy { icon("M17 3a2.85 2.85 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z", "m15 5 4 4") }
    val Database by lazy { icon("M12 2C6.48 2 2 4.02 2 6.5v11C2 19.98 6.48 22 12 22s10-2.02 10-4.5v-11C22 4.02 17.52 2 12 2z", "M2 6.5C2 8.98 6.48 11 12 11s10-2.02 10-4.5", "M2 12c0 2.48 4.48 4.5 10 4.5s10-2.02 10-4.5") }
    val ArrowRight by lazy { icon("M5 12h14", "M12 5l7 7-7 7") }
    val CircleCheck by lazy { icon("M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20z", "M9 12l2 2 4-4") }
    val SearchOff by lazy { icon("M5.5 5.5L18.5 18.5", "M11 3a8 8 0 1 0 0 16 8 8 0 0 0 0-16z") }
    val Prompt by lazy { icon("M9 3H5a2 2 0 0 0-2 2v4m6-6h10a2 2 0 0 1 2 2v4M9 3v18m0 0h10a2 2 0 0 0 2-2v-4M9 21H5a2 2 0 0 1-2-2v-4m0-4h18") }
    val InfoCircle by lazy { icon("M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20z", "M12 16v-4", "M12 8h.01") }
    val OAuth by lazy {
        icon(
            "M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0",
            "M12.556 6c.65 0 1.235 .373 1.508 .947l2.839 7.848a1.646 1.646 0 0 1 -1.01 2.108a1.673 1.673 0 0 1 -2.068 -.851l-.46 -1.052h-2.73l-.398 .905a1.67 1.67 0 0 1 -1.977 1.045l-.153 -.047a1.647 1.647 0 0 1 -1.056 -1.956l2.824 -7.852a1.664 1.664 0 0 1 1.409 -1.087l1.272 -.008"
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
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
        }
    }.build()
}
