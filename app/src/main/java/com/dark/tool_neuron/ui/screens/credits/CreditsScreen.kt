package com.dark.tool_neuron.ui.screens.credits

import android.media.MediaPlayer
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dark.tool_neuron.R

@Composable
fun CreditsScreen(
    innerPadding: PaddingValues,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scrollState = rememberScrollState()

    var durationMs by remember { mutableIntStateOf(0) }
    var viewportPx by remember { mutableIntStateOf(0) }

    val hasExited = remember { mutableStateOf(false) }
    val safeExit = remember(onExit) {
        {
            if (!hasExited.value) {
                hasExited.value = true
                onExit()
            }
        }
    }

    val player = remember {
        runCatching { MediaPlayer.create(context, R.raw.credits) }.getOrNull()
    }

    DisposableEffect(player) {
        player?.let {
            durationMs = it.duration.coerceAtLeast(20_000)
            it.setOnCompletionListener { safeExit() }
            it.start()
        }
        onDispose {
            player?.let {
                runCatching { if (it.isPlaying) it.stop() }
                it.release()
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                player?.let { runCatching { if (it.isPlaying) it.pause() } }
                safeExit()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(scrollState.maxValue, durationMs) {
        val max = scrollState.maxValue
        if (max > 0 && durationMs > 0) {
            scrollState.animateScrollTo(
                value = max,
                animationSpec = tween(durationMillis = durationMs, easing = LinearEasing),
            )
        }
    }

    BackHandler { safeExit() }

    val background = MaterialTheme.colorScheme.surface
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .padding(innerPadding)
            .pointerInput(Unit) { detectTapGestures(onTap = { safeExit() }) }
            .onSizeChanged { viewportPx = it.height },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState, enabled = false),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(with(density) { viewportPx.toDp() }))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
            ) {
                Text(
                    text = "ToolNeuron",
                    color = accent,
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "An offline AI that lives on your phone.",
                    color = onSurfaceVariant,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(96.dp))

            CREDITS_SECTIONS.forEach { section ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = section.title.uppercase(),
                        color = onSurfaceVariant,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 4.sp,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(20.dp))
                    section.lines.forEach { line ->
                        Text(
                            text = line,
                            color = onSurface,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                Spacer(Modifier.height(72.dp))
            }

            Text(
                text = "Made with care.",
                style = MaterialTheme.typography.titleMedium,
                color = accent.copy(alpha = 0.75f),
                fontStyle = FontStyle.Italic,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(40.dp))

            Text(
                text = "Tap anywhere to leave.",
                style = MaterialTheme.typography.labelMedium,
                color = onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(with(density) { viewportPx.toDp() }))
        }
    }
}

private data class CreditsBlock(val title: String, val lines: List<String>)

private val CREDITS_SECTIONS = listOf(
    CreditsBlock(
        title = "Created by",
        lines = listOf("Siddhesh Sonar"),
    ),
    CreditsBlock(
        title = "Engines and runtimes",
        lines = listOf(
            "llama.cpp and ggml",
            "Georgi Gerganov and contributors",
            "sherpa-onnx",
            "k2-fsa team",
            "BoringSSL",
            "liboqs",
            "cpp-httplib",
            "nlohmann/json",
            "curl-impersonate",
        ),
    ),
    CreditsBlock(
        title = "Models in the catalog",
        lines = listOf(
            "LFM2 by Liquid AI",
            "Qwen3 by the Qwen team",
            "Whisper by OpenAI",
            "Piper voices by Michael Hansen",
            "Hosted on HuggingFace",
        ),
    ),
    CreditsBlock(
        title = "Android stack",
        lines = listOf(
            "Android and AOSP",
            "Jetpack Compose",
            "Material 3 Expressive",
            "AndroidX",
            "Hilt",
            "Kotlin and Coroutines",
            "kotlinx.serialization",
        ),
    ),
    CreditsBlock(
        title = "Look and feel",
        lines = listOf(
            "Tabler Icons",
            "Capsule by Kyant0",
        ),
    ),
    CreditsBlock(
        title = "Built with",
        lines = listOf(
            "Android Studio",
            "Gradle",
            "Apache Commons Compress",
            "Claude Code by Anthropic",
            "Claude Opus and Sonnet",
        ),
    ),
    CreditsBlock(
        title = "Search backend",
        lines = listOf("DuckDuckGo"),
    ),
    CreditsBlock(
        title = "Special thanks",
        lines = listOf(
            "Every person who installed this",
            "Every bug report",
            "The open-source community",
        ),
    ),
)
