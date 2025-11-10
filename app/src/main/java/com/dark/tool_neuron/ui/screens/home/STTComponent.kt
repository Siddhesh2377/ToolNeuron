package com.dark.tool_neuron.ui.screens.home


import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.ui.theme.SkyBlue
import com.dark.tool_neuron.ui.theme.rDP
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

@Composable
fun STTButton(
    isRecording: Boolean,
    isProcessing: Boolean,
    isReady: Boolean,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = Modifier.size(rDP(36.dp)),
        contentAlignment = Alignment.Center
    ) {
        // Pulse animation when recording
        if (isRecording) {
            Box(
                modifier = Modifier
                    .size(rDP(36.dp))
                    .scale(scale)
                    .background(
                        color = Color.Red.copy(alpha = 0.3f),
                        shape = CircleShape
                    )
            )
        }

        IconButton(
            onClick = onClick,
            enabled = isReady && !isProcessing,
            modifier = Modifier.size(rDP(36.dp)),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = when {
                    isRecording -> Color.Red.copy(alpha = 0.2f)
                    isProcessing -> SkyBlue.copy(alpha = 0.2f)
                    isReady -> MaterialTheme.colorScheme.background
                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                },
                contentColor = when {
                    isRecording -> Color.Red
                    isProcessing -> SkyBlue
                    isReady -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                }
            )
        ) {
            when {
                isProcessing -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(rDP(20.dp)),
                        strokeWidth = 2.dp,
                        color = SkyBlue
                    )
                }
                isRecording -> {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop Recording",
                        modifier = Modifier.size(rDP(20.dp))
                    )
                }
                else -> {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Start Recording",
                        modifier = Modifier.size(rDP(20.dp))
                    )
                }
            }
        }
    }
}

// Audio recording function with stop callback
@SuppressLint("MissingPermission")
suspend fun recordAudioUntilStopped(
    context: Context,
    shouldStop: () -> Boolean
): File = withContext(Dispatchers.IO) {
    val sampleRate = 16000
    val bufferSize = AudioRecord.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )

    val recorder = AudioRecord(
        MediaRecorder.AudioSource.MIC,
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
        bufferSize
    )

    val pcmFile = File(context.cacheDir, "temp_audio_${System.currentTimeMillis()}.pcm")
    val wavFile = File(context.cacheDir, "temp_audio_${System.currentTimeMillis()}.wav")

    try {
        FileOutputStream(pcmFile).use { fos ->
            val buffer = ByteArray(bufferSize)
            recorder.startRecording()
            Log.d("ChatInputBar", "Recording started")

            // Record until shouldStop returns true or max 30 seconds
            val startTime = System.currentTimeMillis()
            while (isActive && !shouldStop() && (System.currentTimeMillis() - startTime) < 30000) {
                val read = recorder.read(buffer, 0, buffer.size)
                if (read > 0) {
                    fos.write(buffer, 0, read)
                }
            }

            recorder.stop()
            recorder.release()
            Log.d("ChatInputBar", "Recording stopped, PCM size: ${pcmFile.length()} bytes")
        }

        // Convert PCM to WAV
        pcmToWav(pcmFile, wavFile, sampleRate)
        pcmFile.delete()

        Log.d("ChatInputBar", "WAV file created: ${wavFile.absolutePath}, size: ${wavFile.length()}")
        wavFile
    } catch (e: Exception) {
        Log.e("ChatInputBar", "Recording error", e)
        recorder.stop()
        recorder.release()
        pcmFile.delete()
        throw e
    }
}

private fun pcmToWav(pcmFile: File, wavFile: File, sampleRate: Int) {
    val pcmData = pcmFile.readBytes()
    val channels = 1
    val bitsPerSample = 16
    val byteRate = sampleRate * channels * bitsPerSample / 8
    val blockAlign = channels * bitsPerSample / 8

    val header = ByteBuffer.allocate(44).apply {
        order(ByteOrder.LITTLE_ENDIAN)
        put("RIFF".toByteArray(Charsets.US_ASCII))
        putInt(36 + pcmData.size)
        put("WAVE".toByteArray(Charsets.US_ASCII))
        put("fmt ".toByteArray(Charsets.US_ASCII))
        putInt(16)
        putShort(1.toShort())
        putShort(channels.toShort())
        putInt(sampleRate)
        putInt(byteRate)
        putShort(blockAlign.toShort())
        putShort(bitsPerSample.toShort())
        put("data".toByteArray(Charsets.US_ASCII))
        putInt(pcmData.size)
    }

    FileOutputStream(wavFile).use {
        it.write(header.array())
        it.write(pcmData)
    }
}