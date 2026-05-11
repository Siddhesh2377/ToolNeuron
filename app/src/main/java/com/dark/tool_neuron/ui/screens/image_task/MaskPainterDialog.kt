package com.dark.tool_neuron.ui.screens.image_task

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.PorterDuff
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dark.tool_neuron.ui.components.ActionTextButton
import com.dark.tool_neuron.ui.icons.TnIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private data class MaskStroke(
    val points: List<Offset>,
    val brushPx: Float,
)

@Composable
fun MaskPainterDialog(
    inputBitmap: Bitmap,
    cacheDir: File,
    onDismiss: () -> Unit,
    onConfirm: (path: String) -> Unit,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val strokes = remember { mutableStateListOf<MaskStroke>() }
    val currentPoints = remember { mutableStateListOf<Offset>() }
    var currentBrushPxSnapshot by remember { mutableStateOf(0f) }
    val brushSliderState = rememberSliderState(value = 40f, valueRange = 10f..100f)
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var isWriting by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = TnIcons.X,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Spacer(Modifier.size(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Paint mask",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Paint over the area you want regenerated",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val aspect = inputBitmap.width.toFloat() / inputBitmap.height.toFloat()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(aspect)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(12.dp),
                            )
                            .onSizeChanged { canvasSize = it }
                            .pointerInput(density) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val down = awaitFirstDown()
                                        currentBrushPxSnapshot = with(density) {
                                            brushSliderState.value.dp.toPx()
                                        }
                                        currentPoints.clear()
                                        currentPoints.add(down.position)
                                        down.consume()
                                        var pressed = true
                                        while (pressed) {
                                            val event = awaitPointerEvent()
                                            event.changes.forEach { change ->
                                                if (change.pressed) {
                                                    currentPoints.add(change.position)
                                                    change.consume()
                                                } else {
                                                    pressed = false
                                                }
                                            }
                                        }
                                        if (currentPoints.isNotEmpty()) {
                                            strokes.add(
                                                MaskStroke(
                                                    points = currentPoints.toList(),
                                                    brushPx = currentBrushPxSnapshot,
                                                ),
                                            )
                                        }
                                        currentPoints.clear()
                                    }
                                }
                            },
                    ) {
                        Image(
                            bitmap = inputBitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            strokes.forEach { drawMaskStroke(it) }
                            if (currentPoints.isNotEmpty() && currentBrushPxSnapshot > 0f) {
                                drawMaskStroke(
                                    MaskStroke(
                                        points = currentPoints.toList(),
                                        brushPx = currentBrushPxSnapshot,
                                    ),
                                )
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Brush",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${brushSliderState.value.toInt()} dp",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Slider(state = brushSliderState)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ActionTextButton(
                            onClickListener = {
                                if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex)
                            },
                            icon = TnIcons.Backspace,
                            text = "Undo",
                            enabled = strokes.isNotEmpty() && !isWriting,
                            modifier = Modifier.weight(1f),
                        )
                        ActionTextButton(
                            onClickListener = { strokes.clear() },
                            icon = TnIcons.X,
                            text = "Clear",
                            enabled = strokes.isNotEmpty() && !isWriting,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    ActionTextButton(
                        onClickListener = {
                            isWriting = true
                            val snapshot = strokes.toList()
                            val canvas = canvasSize
                            scope.launch {
                                val path = withContext(Dispatchers.IO) {
                                    rasterizeMask(inputBitmap, snapshot, canvas, cacheDir)
                                }
                                isWriting = false
                                if (path != null) onConfirm(path) else onDismiss()
                            }
                        },
                        icon = TnIcons.Check,
                        text = if (isWriting) "Saving mask…" else "Apply mask",
                        enabled = strokes.isNotEmpty() && !isWriting && canvasSize.width > 0,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawMaskStroke(stroke: MaskStroke) {
    val color = Color.White.copy(alpha = 0.55f)
    if (stroke.points.size == 1) {
        drawCircle(
            color = color,
            radius = stroke.brushPx / 2f,
            center = stroke.points.first(),
        )
        return
    }
    val path = Path().apply {
        stroke.points.forEachIndexed { i, p ->
            if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
        }
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(
            width = stroke.brushPx,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        ),
    )
}

private fun rasterizeMask(
    inputBitmap: Bitmap,
    strokes: List<MaskStroke>,
    canvasSize: IntSize,
    cacheDir: File,
): String? {
    if (strokes.isEmpty() || canvasSize.width <= 0 || canvasSize.height <= 0) return null
    val out = Bitmap.createBitmap(
        inputBitmap.width,
        inputBitmap.height,
        Bitmap.Config.ARGB_8888,
    )
    val canvas = android.graphics.Canvas(out)
    canvas.drawColor(android.graphics.Color.BLACK, PorterDuff.Mode.SRC)

    val scaleX = inputBitmap.width.toFloat() / canvasSize.width.toFloat()
    val scaleY = inputBitmap.height.toFloat() / canvasSize.height.toFloat()
    val avgScale = (scaleX + scaleY) / 2f

    val paint = Paint().apply {
        color = android.graphics.Color.WHITE
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    strokes.forEach { stroke ->
        val scaledBrush = stroke.brushPx * avgScale
        if (stroke.points.size == 1) {
            paint.style = Paint.Style.FILL
            val p = stroke.points.first()
            canvas.drawCircle(p.x * scaleX, p.y * scaleY, scaledBrush / 2f, paint)
        } else {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = scaledBrush
            val path = android.graphics.Path()
            stroke.points.forEachIndexed { i, p ->
                if (i == 0) path.moveTo(p.x * scaleX, p.y * scaleY)
                else path.lineTo(p.x * scaleX, p.y * scaleY)
            }
            canvas.drawPath(path, paint)
        }
    }

    val file = File(cacheDir, "mask_${System.currentTimeMillis()}.png")
    return runCatching {
        file.outputStream().use {
            out.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        file.absolutePath
    }.getOrNull()
}
