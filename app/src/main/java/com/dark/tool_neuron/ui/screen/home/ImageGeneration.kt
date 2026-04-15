package com.dark.tool_neuron.ui.screen.home

import android.graphics.Bitmap
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.random.Random
import com.dark.tool_neuron.global.Standards

// ── ImageGenerationStreamingBubble ──

@Composable
internal fun ImageGenerationStreamingBubble(
    streamingImage: Bitmap?,
    progress: Float,
    step: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Standards.SpacingSm),
        verticalArrangement = Arrangement.spacedBy(Standards.SpacingMd)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Standards.SpacingMd),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(24.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp
            )

            Column {
                Text(
                    text = "Generating image...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "$step • ${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        streamingImage?.let { bitmap ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1))
                    .clip(RoundedCornerShape(Standards.RadiusLg))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                MorphingImagePreview(
                    bitmap = bitmap,
                    progress = progress,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

// ── MorphingImagePreview ──

private data class ChunkAnimParams(
    val ampX: Float, val ampY: Float,
    val durX: Int, val durY: Int,
    val delayX: Int, val delayY: Int,
    val scatterX: Float, val scatterY: Float
)

/**
 * ChatGPT-style morphing color preview.
 *
 * Each chunk bitmap is pre-processed with a radial alpha mask so its edges
 * fade from opaque (center) to fully transparent (border). When overlapping
 * blurred chunks are drawn together, they blend seamlessly with no hard edges.
 */
@Composable
internal fun MorphingImagePreview(
    bitmap: Bitmap,
    progress: Float,
    modifier: Modifier = Modifier
) {
    val cols = 2
    val rows = 3

    // Create feathered chunks: opaque center → transparent edges
    val chunks = remember(bitmap) {
        val cw = bitmap.width / cols
        val ch = bitmap.height / rows
        List(cols * rows) { i ->
            val src = Bitmap.createBitmap(bitmap, (i % cols) * cw, (i / cols) * ch, cw, ch)
            createFeatheredChunk(src, cw, ch)
        }
    }

    DisposableEffect(chunks) {
        onDispose {
            chunks.forEach { it.recycle() }
        }
    }

    // Eased progress curve: stays abstract longer, resolves in last 30%
    val easedProgress = if (progress < 0.7f) {
        progress / 0.7f * 0.4f
    } else {
        0.4f + (progress - 0.7f) / 0.3f * 0.6f
    }
    val drift = (1f - easedProgress).coerceIn(0f, 1f)
    val blurAmount = (55f * drift).coerceAtLeast(0f)

    val infiniteTransition = rememberInfiniteTransition(label = "morph")

    BoxWithConstraints(modifier = modifier) {
        val cellW = maxWidth / cols
        val cellH = maxHeight / rows

        chunks.forEachIndexed { index, chunk ->
            key(index) {
                val params = remember {
                    val rng = Random(index * 37 + 7)
                    ChunkAnimParams(
                        ampX = 25f + rng.nextFloat() * 35f,
                        ampY = 20f + rng.nextFloat() * 30f,
                        durX = 3500 + rng.nextInt(2000),
                        durY = 3000 + rng.nextInt(2000),
                        delayX = rng.nextInt(700),
                        delayY = rng.nextInt(700),
                        scatterX = (rng.nextFloat() - 0.5f) * 50f,
                        scatterY = (rng.nextFloat() - 0.5f) * 40f
                    )
                }

                val driftX by infiniteTransition.animateFloat(
                    initialValue = -params.ampX, targetValue = params.ampX,
                    animationSpec = infiniteRepeatable(
                        tween(params.durX, delayMillis = params.delayX, easing = LinearOutSlowInEasing),
                        RepeatMode.Reverse
                    ), label = "dx$index"
                )
                val driftY by infiniteTransition.animateFloat(
                    initialValue = -params.ampY, targetValue = params.ampY,
                    animationSpec = infiniteRepeatable(
                        tween(params.durY, delayMillis = params.delayY, easing = LinearOutSlowInEasing),
                        RepeatMode.Reverse
                    ), label = "dy$index"
                )

                val col = index % cols
                val row = index / cols
                val ox = cellW * col + ((driftX + params.scatterX) * drift).dp
                val oy = cellH * row + ((driftY + params.scatterY) * drift).dp
                val blobScale = 1f + 0.6f * drift

                Image(
                    bitmap = chunk.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .size(cellW, cellH)
                        .offset(x = ox, y = oy)
                        .graphicsLayer {
                            scaleX = blobScale
                            scaleY = blobScale
                        }
                        .blur(blurAmount.dp, BlurredEdgeTreatment.Unbounded)
                )
            }
        }
    }
}

// ── Helper: createFeatheredChunk ──

/** Create a bitmap with radial alpha fade: opaque center → transparent edges. */
private fun createFeatheredChunk(src: Bitmap, w: Int, h: Int): Bitmap {
    val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(result)

    // Draw original chunk
    canvas.drawBitmap(src, 0f, 0f, null)

    // Punch out edges with radial gradient alpha mask (DST_IN keeps center, fades edges)
    val maskPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    maskPaint.xfermode = android.graphics.PorterDuffXfermode(
        android.graphics.PorterDuff.Mode.DST_IN
    )
    val radius = maxOf(w, h) * 0.75f
    maskPaint.shader = android.graphics.RadialGradient(
        w / 2f, h / 2f, radius,
        intArrayOf(0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(), 0x00FFFFFF),
        floatArrayOf(0f, 0.45f, 1f),
        android.graphics.Shader.TileMode.CLAMP
    )
    canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), maskPaint)

    src.recycle()
    return result
}
