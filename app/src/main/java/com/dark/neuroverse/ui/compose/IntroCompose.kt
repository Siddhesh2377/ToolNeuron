package com.dark.neuroverse.ui.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    // N letter path
    "M76.376 90.64L15.576 11.92V76.688C15.576 79.0773 15.6187 80.912 15.704 82.192C15.8747 83.3867 16.2587 84.3253 16.856 85.008C17.5387 85.6053 18.52 86.16 19.8 86.672C21.08 87.0987 22.9147 87.3973 25.304 87.568C25.3893 88.4213 25.3893 89.2747 25.304 90.128C23.4267 90.128 21.336 90.0853 19.032 90C16.8133 89.9147 14.936 89.872 13.4 89.872C11.864 89.872 9.944 89.9147 7.64 90C5.42133 90.0853 3.416 90.128 1.624 90.128C1.53867 89.2747 1.53867 88.4213 1.624 87.568C4.61067 87.2267 6.744 86.8427 8.024 86.416C9.304 85.9893 10.072 85.1787 10.328 83.984C10.6693 82.704 10.84 80.656 10.84 77.84V14.224C10.84 10.896 10.6693 8.50666 10.328 7.056C9.98667 5.52 9.09067 4.496 7.64 3.98399C6.27467 3.472 4.01333 3.04533 0.856001 2.70399C0.770667 1.93599 0.770667 1.12533 0.856001 0.271996C2.81867 0.271996 4.86667 0.314662 7 0.399994C9.21867 0.485326 11.3947 0.527992 13.528 0.527992C13.784 0.527992 14.5947 0.527992 15.96 0.527992C17.4107 0.527992 18.6907 0.485326 19.8 0.399994L76.888 74.512V13.84C76.888 11.536 76.8453 9.744 76.76 8.464C76.6747 7.09866 76.2907 6.11733 75.608 5.52C74.328 4.06933 71.1707 3.17333 66.136 2.83199C66.0507 1.97866 66.0507 1.16799 66.136 0.399994C67.928 0.399994 70.1467 0.44266 72.792 0.527992C75.4373 0.527992 77.528 0.527992 79.064 0.527992C80.6 0.527992 82.4773 0.527992 84.696 0.527992C87 0.44266 89.0907 0.399994 90.968 0.399994C91.0533 1.16799 91.0533 1.97866 90.968 2.83199C87.9813 3.17333 85.8053 3.6 84.44 4.112C83.16 4.53867 82.3493 5.392 82.008 6.672C81.752 7.86666 81.624 9.872 81.624 12.688V90.128C81.0267 90.2987 80.1733 90.4267 79.064 90.512C78.04 90.5973 77.144 90.64 76.376 90.64Z",

    // V letter path
    "M187.18 0.271996C187.265 1.12533 187.265 1.97866 187.18 2.83199C184.108 3.00266 181.804 3.30133 180.268 3.728C178.817 4.15466 177.665 5.09333 176.812 6.544C175.959 7.90933 174.935 10.1707 173.74 13.328L144.94 89.872C144.343 90.128 143.532 90.2987 142.508 90.384C141.569 90.5547 140.759 90.64 140.076 90.64L111.02 12.56C109.911 9.57333 108.972 7.43999 108.204 6.16C107.436 4.88 106.327 4.06933 104.876 3.728C103.511 3.30133 101.249 3.00266 98.092 2.83199C98.0067 1.97866 98.0067 1.12533 98.092 0.271996C100.311 0.271996 102.913 0.314662 105.9 0.399994C108.887 0.485326 111.532 0.527992 113.836 0.527992C116.225 0.527992 118.572 0.485326 120.876 0.399994C123.265 0.314662 125.655 0.271996 128.044 0.271996C128.129 1.12533 128.129 1.97866 128.044 2.83199C124.631 3.08799 122.412 3.38666 121.388 3.728C120.364 3.984 119.852 4.70933 119.852 5.904C119.852 6.33067 119.937 6.88533 120.108 7.56799C120.279 8.16533 120.535 8.93333 120.876 9.87199L145.452 77.072L169.26 11.28C170.113 8.976 170.54 7.312 170.54 6.28799C170.54 4.92266 169.857 4.06933 168.492 3.728C167.212 3.38666 164.908 3.08799 161.58 2.83199C161.495 1.97866 161.495 1.12533 161.58 0.271996C163.713 0.271996 165.889 0.314662 168.108 0.399994C170.412 0.485326 172.631 0.527992 174.764 0.527992C176.471 0.527992 178.476 0.485326 180.78 0.399994C183.169 0.314662 185.303 0.271996 187.18 0.271996Z"
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

    Canvas(modifier = Modifier.fillMaxSize()) {
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val scaleFactor = 1f

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
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }
        }
    }
}