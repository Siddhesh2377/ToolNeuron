package com.dark.tool_neuron.repo

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.net.Uri
import androidx.core.graphics.scale
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class UpscaleScalePreset { X2, X4, X8, CUSTOM }
enum class UpscaleProcessingMode { AUTO, FAST, SAFE_TILED }

data class UpscalePipelineProgress(
    val label: String,
    val progress: Float,
    val currentTile: Int = 0,
    val totalTiles: Int = 0,
    val currentPass: Int = 0,
    val totalPasses: Int = 0,
)

data class UpscalePipelineResult(
    val preview: Bitmap,
    val fullWidth: Int,
    val fullHeight: Int,
    val outputFile: File?,
    val scale: Float,
    val tiled: Boolean,
    val elapsedMs: Long,
)

class FastUpscaleBlockedException(message: String) : IllegalStateException(message)

class ImageUpscalePipeline(
    private val context: Context,
    private val imageGen: ImageGenManager,
) {
    suspend fun run(
        imagePath: String,
        scale: Float,
        mode: UpscaleProcessingMode,
        outputDir: File,
        onProgress: (UpscalePipelineProgress) -> Unit,
    ): UpscalePipelineResult {
        val started = System.currentTimeMillis()
        outputDir.mkdirs()
        val bounds = readBounds(imagePath)
        val targetWidth = (bounds.width * scale).roundToInt().coerceAtLeast(1)
        val targetHeight = (bounds.height * scale).roundToInt().coerceAtLeast(1)
        val oneShotRisky = isOneShotRisky(bounds.width, bounds.height, targetWidth, targetHeight, scale)
        val tiled = when (mode) {
            UpscaleProcessingMode.SAFE_TILED -> true
            UpscaleProcessingMode.AUTO -> oneShotRisky
            UpscaleProcessingMode.FAST -> {
                if (oneShotRisky) {
                    throw FastUpscaleBlockedException("Fast mode is risky for this image. Switch to Safe tiled to process it reliably.")
                }
                false
            }
        }
        onProgress(UpscalePipelineProgress(if (tiled) "Preparing tiles" else "Preparing", 0.02f))
        val result = if (tiled) runTiled(imagePath, bounds, scale, targetWidth, targetHeight, outputDir, onProgress)
            else runOneShot(imagePath, scale, targetWidth, targetHeight, onProgress)
        return result.copy(elapsedMs = (System.currentTimeMillis() - started).coerceAtLeast(1L))
    }

    private suspend fun runOneShot(
        imagePath: String,
        scale: Float,
        targetWidth: Int,
        targetHeight: Int,
        onProgress: (UpscalePipelineProgress) -> Unit,
    ): UpscalePipelineResult {
        val source = decodeBitmap(imagePath) ?: error("Couldn't read input image")
        val out = upscaleExact(source, scale, onProgress)
        val exact = if (out.width == targetWidth && out.height == targetHeight) out
            else out.scale(targetWidth, targetHeight)
        onProgress(UpscalePipelineProgress("Writing image", 0.98f))
        return UpscalePipelineResult(
            preview = exact,
            fullWidth = exact.width,
            fullHeight = exact.height,
            outputFile = null,
            scale = scale,
            tiled = false,
            elapsedMs = 0L,
        )
    }

    private suspend fun runTiled(
        imagePath: String,
        bounds: ImageBounds,
        scale: Float,
        targetWidth: Int,
        targetHeight: Int,
        outputDir: File,
        onProgress: (UpscalePipelineProgress) -> Unit,
    ): UpscalePipelineResult {
        val rawFile = File(outputDir, "upscale_${UUID.randomUUID()}.rgba")
        val pngFile = File(outputDir, "upscale_${System.currentTimeMillis()}.png")
        val tiles = buildTiles(bounds.width, bounds.height)
        RawRgbaCanvas(rawFile, targetWidth, targetHeight).use { canvas ->
            tiles.forEachIndexed { index, tile ->
                onProgress(
                    UpscalePipelineProgress(
                        label = "Tile ${index + 1}/${tiles.size}",
                        progress = (index.toFloat() / tiles.size.toFloat()).coerceIn(0.02f, 0.92f),
                        currentTile = index + 1,
                        totalTiles = tiles.size,
                    ),
                )
                val region = decodeRegion(imagePath, tile.readRect) ?: error("Couldn't decode tile")
                val upscaled = upscaleExact(region, scale) { pass ->
                    onProgress(
                        pass.copy(
                            label = "Tile ${index + 1}/${tiles.size}",
                            progress = ((index.toFloat() + pass.progress) / tiles.size.toFloat()).coerceIn(0.02f, 0.92f),
                            currentTile = index + 1,
                            totalTiles = tiles.size,
                        ),
                    )
                }
                val crop = tile.cropInReadRect(scale, upscaled.width, upscaled.height)
                val exact = Bitmap.createBitmap(upscaled, crop.left, crop.top, crop.width(), crop.height())
                canvas.writeBitmap(
                    bitmap = exact,
                    dstX = (tile.contentRect.left * scale).roundToInt(),
                    dstY = (tile.contentRect.top * scale).roundToInt(),
                )
                if (exact !== upscaled) exact.recycle()
                upscaled.recycle()
                region.recycle()
            }
            onProgress(UpscalePipelineProgress("Writing image", 0.96f))
            StreamingPngWriter.write(canvas.file, pngFile, targetWidth, targetHeight)
            val preview = canvas.preview(maxEdge = 1600)
            return UpscalePipelineResult(
                preview = preview,
                fullWidth = targetWidth,
                fullHeight = targetHeight,
                outputFile = pngFile,
                scale = scale,
                tiled = true,
                elapsedMs = 0L,
            )
        }
    }

    private suspend fun upscaleExact(
        source: Bitmap,
        scale: Float,
        onProgress: (UpscalePipelineProgress) -> Unit = {},
    ): Bitmap {
        val passes = max(1, ceil(ln(scale.toDouble()) / ln(4.0)).toInt())
        var current = source
        repeat(passes) { index ->
            onProgress(
                UpscalePipelineProgress(
                    label = "Pass ${index + 1}/$passes",
                    progress = index.toFloat() / passes.toFloat(),
                    currentPass = index + 1,
                    totalPasses = passes,
                ),
            )
            val next = imageGen.upscaleOnce(current)
            if (current !== source) current.recycle()
            current = next
        }
        val targetW = (source.width * scale).roundToInt().coerceAtLeast(1)
        val targetH = (source.height * scale).roundToInt().coerceAtLeast(1)
        return if (current.width == targetW && current.height == targetH) current else {
            val exact = current.scale(targetW, targetH)
            current.recycle()
            exact
        }
    }

    private fun isOneShotRisky(
        sourceWidth: Int,
        sourceHeight: Int,
        targetWidth: Int,
        targetHeight: Int,
        scale: Float,
    ): Boolean {
        if (max(sourceWidth, sourceHeight) > SAFE_NATIVE_INPUT_EDGE) return true
        val nativePasses = max(1, ceil(ln(scale.toDouble()) / ln(4.0)).toInt())
        val largestMultiplier = if (nativePasses <= 1) 4f else 16f
        val sourceBytes = sourceWidth.toLong() * sourceHeight.toLong() * 4L
        val intermediateBytes = (sourceWidth * largestMultiplier).toLong() *
            (sourceHeight * largestMultiplier).toLong() * 4L
        val targetBytes = targetWidth.toLong() * targetHeight.toLong() * 4L
        val riskyBytes = sourceBytes + intermediateBytes + targetBytes
        val runtime = Runtime.getRuntime()
        val available = (runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory())).coerceAtLeast(0L)
        val memoryClassBytes = ((context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)
            ?.largeMemoryClass ?: 256).toLong() * 1024L * 1024L
        val budget = min(available, memoryClassBytes / 2L).coerceAtLeast(96L * 1024L * 1024L)
        return riskyBytes > budget
    }

    private fun buildTiles(width: Int, height: Int): List<UpscaleTile> {
        val tiles = mutableListOf<UpscaleTile>()
        var y = 0
        while (y < height) {
            val contentBottom = min(y + TILE_CONTENT_EDGE, height)
            var x = 0
            while (x < width) {
                val contentRight = min(x + TILE_CONTENT_EDGE, width)
                val content = Rect(x, y, contentRight, contentBottom)
                val read = Rect(
                    max(0, content.left - TILE_OVERLAP),
                    max(0, content.top - TILE_OVERLAP),
                    min(width, content.right + TILE_OVERLAP),
                    min(height, content.bottom + TILE_OVERLAP),
                )
                tiles += UpscaleTile(read, content)
                x += TILE_CONTENT_EDGE
            }
            y += TILE_CONTENT_EDGE
        }
        return tiles
    }

    private fun readBounds(path: String): ImageBounds {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openInput(path).use { BitmapFactory.decodeStream(it, null, options) }
        check(options.outWidth > 0 && options.outHeight > 0) { "Couldn't read input image bounds" }
        return ImageBounds(options.outWidth, options.outHeight)
    }

    private fun decodeBitmap(path: String): Bitmap? =
        openInput(path).use { BitmapFactory.decodeStream(it) }

    @Suppress("DEPRECATION")
    private fun decodeRegion(path: String, rect: Rect): Bitmap? =
        openInput(path).use { input ->
            val decoder = BitmapRegionDecoder.newInstance(input, false) ?: return null
            try {
                decoder.decodeRegion(rect, BitmapFactory.Options())
            } finally {
                decoder.recycle()
            }
        }

    private fun openInput(path: String) =
        if (path.startsWith("content://") || path.startsWith("file://")) {
            context.contentResolver.openInputStream(Uri.parse(path))
                ?: error("Couldn't open input image")
        } else {
            FileInputStream(path)
        }

    private data class ImageBounds(val width: Int, val height: Int)

    private data class UpscaleTile(
        val readRect: Rect,
        val contentRect: Rect,
    ) {
        fun cropInReadRect(scale: Float, upscaledWidth: Int, upscaledHeight: Int): Rect {
            val left = ((contentRect.left - readRect.left) * scale).roundToInt().coerceIn(0, upscaledWidth - 1)
            val top = ((contentRect.top - readRect.top) * scale).roundToInt().coerceIn(0, upscaledHeight - 1)
            val right = (left + (contentRect.width() * scale).roundToInt()).coerceIn(left + 1, upscaledWidth)
            val bottom = (top + (contentRect.height() * scale).roundToInt()).coerceIn(top + 1, upscaledHeight)
            return Rect(left, top, right, bottom)
        }
    }

    private class RawRgbaCanvas(
        val file: File,
        private val width: Int,
        private val height: Int,
    ) : AutoCloseable {
        private val raf = RandomAccessFile(file, "rw")
        private val rgba = ByteArray(width * 4)

        init {
            raf.setLength(width.toLong() * height.toLong() * 4L)
        }

        fun writeBitmap(bitmap: Bitmap, dstX: Int, dstY: Int) {
            val safeWidth = min(bitmap.width, width - dstX).coerceAtLeast(0)
            val safeHeight = min(bitmap.height, height - dstY).coerceAtLeast(0)
            if (safeWidth == 0 || safeHeight == 0) return
            val pixels = IntArray(safeWidth)
            val bytes = ByteArray(safeWidth * 4)
            repeat(safeHeight) { y ->
                bitmap.getPixels(pixels, 0, safeWidth, 0, y, safeWidth, 1)
                pixelsToRgba(pixels, bytes, safeWidth)
                raf.seek(((dstY + y).toLong() * width.toLong() + dstX.toLong()) * 4L)
                raf.write(bytes)
            }
        }

        fun preview(maxEdge: Int): Bitmap {
            val scale = min(1f, maxEdge.toFloat() / max(width, height).toFloat())
            val previewW = (width * scale).roundToInt().coerceAtLeast(1)
            val previewH = (height * scale).roundToInt().coerceAtLeast(1)
            val preview = Bitmap.createBitmap(previewW, previewH, Bitmap.Config.ARGB_8888)
            val pixel = IntArray(1)
            repeat(previewH) { py ->
                val sy = ((py + 0.5f) / scale).toInt().coerceIn(0, height - 1)
                repeat(previewW) { px ->
                    val sx = ((px + 0.5f) / scale).toInt().coerceIn(0, width - 1)
                    readPixel(sx, sy, pixel)
                    preview.setPixel(px, py, pixel[0])
                }
            }
            return preview
        }

        private fun readPixel(x: Int, y: Int, out: IntArray) {
            raf.seek((y.toLong() * width.toLong() + x.toLong()) * 4L)
            raf.readFully(rgba, 0, 4)
            val r = rgba[0].toInt() and 0xff
            val g = rgba[1].toInt() and 0xff
            val b = rgba[2].toInt() and 0xff
            val a = rgba[3].toInt() and 0xff
            out[0] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }

        override fun close() {
            raf.close()
            file.delete()
        }

        private fun pixelsToRgba(pixels: IntArray, bytes: ByteArray, count: Int) {
            repeat(count) { i ->
                val argb = pixels[i]
                val o = i * 4
                bytes[o] = ((argb shr 16) and 0xff).toByte()
                bytes[o + 1] = ((argb shr 8) and 0xff).toByte()
                bytes[o + 2] = (argb and 0xff).toByte()
                bytes[o + 3] = ((argb ushr 24) and 0xff).toByte()
            }
        }
    }

    private object StreamingPngWriter {
        fun write(rawFile: File, pngFile: File, width: Int, height: Int) {
            val zdat = File(pngFile.parentFile, "${pngFile.name}.zdat")
            val row = ByteArray(width * 4)
            FileInputStream(rawFile).use { raw ->
                DeflaterOutputStream(
                    BufferedOutputStream(zdat.outputStream()),
                    Deflater(Deflater.DEFAULT_COMPRESSION),
                ).use { deflated ->
                    repeat(height) {
                        deflated.write(0)
                        raw.readFully(row)
                        deflated.write(row)
                    }
                }
            }
            BufferedOutputStream(pngFile.outputStream()).use { out ->
                out.write(PNG_SIGNATURE)
                writeChunk(out, "IHDR", ihdr(width, height))
                FileInputStream(zdat).use { input ->
                    val data = input.readBytes()
                    writeChunk(out, "IDAT", data)
                }
                writeChunk(out, "IEND", ByteArray(0))
            }
            zdat.delete()
        }

        private fun ihdr(width: Int, height: Int): ByteArray {
            val data = ByteArray(13)
            writeInt(data, 0, width)
            writeInt(data, 4, height)
            data[8] = 8
            data[9] = 6
            data[10] = 0
            data[11] = 0
            data[12] = 0
            return data
        }

        private fun writeChunk(out: OutputStream, type: String, data: ByteArray) {
            val typeBytes = type.encodeToByteArray()
            val crc = CRC32()
            writeInt(out, data.size)
            out.write(typeBytes)
            out.write(data)
            crc.update(typeBytes)
            crc.update(data)
            writeInt(out, crc.value.toInt())
        }

        private fun writeInt(out: OutputStream, value: Int) {
            out.write((value ushr 24) and 0xff)
            out.write((value ushr 16) and 0xff)
            out.write((value ushr 8) and 0xff)
            out.write(value and 0xff)
        }

        private fun writeInt(data: ByteArray, offset: Int, value: Int) {
            data[offset] = ((value ushr 24) and 0xff).toByte()
            data[offset + 1] = ((value ushr 16) and 0xff).toByte()
            data[offset + 2] = ((value ushr 8) and 0xff).toByte()
            data[offset + 3] = (value and 0xff).toByte()
        }

        private val PNG_SIGNATURE = byteArrayOf(
            137.toByte(), 80, 78, 71, 13, 10, 26, 10,
        )
    }

    companion object {
        const val SAFE_NATIVE_INPUT_EDGE = 1024
        private const val TILE_OVERLAP = 32
        private const val TILE_CONTENT_EDGE = SAFE_NATIVE_INPUT_EDGE - (TILE_OVERLAP * 2)
    }
}

private fun FileInputStream.readFully(buffer: ByteArray) {
    var offset = 0
    while (offset < buffer.size) {
        val read = read(buffer, offset, buffer.size - offset)
        if (read < 0) error("Unexpected EOF")
        offset += read
    }
}
