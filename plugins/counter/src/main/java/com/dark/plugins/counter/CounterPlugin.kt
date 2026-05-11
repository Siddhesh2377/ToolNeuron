package com.dark.plugins.counter

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme as M3
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dark.plugin_api.Plugin
import com.dark.plugin_api.PluginContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder

class CounterPlugin : Plugin {

    private lateinit var ctx: PluginContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var value by mutableStateOf(0)
    private var loaded by mutableStateOf(false)

    override fun onLoad(context: PluginContext) {
        ctx = context
        scope.launch {
            val restored = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = ctx.hxs.get(STATE_KEY) ?: return@runCatching 0
                    if (bytes.size >= 4) ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).int else 0
                }.getOrDefault(0)
            }
            value = restored
            loaded = true
        }
    }

    override fun onStart() {}
    override fun onPause() {}
    override fun onUnload() {
        scope.cancel()
    }

    @Composable
    override fun Content() {
        CounterTheme {
            CounterUi(
                value = value,
                loaded = loaded,
                onInc = { update(value + 1) },
                onDec = { update(value - 1) },
                onReset = { update(0) },
            )
        }
    }

    private fun update(next: Int) {
        value = next
        scope.launch(Dispatchers.IO) {
            val bytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(next).array()
            ctx.hxs.put(STATE_KEY, bytes)
        }
    }

    private companion object {
        const val STATE_KEY = "counter:value"
    }
}

@Composable
private fun CounterTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = Color(0xFF7BD1F1),
        onPrimary = Color(0xFF002F3F),
        primaryContainer = Color(0xFF15455A),
        onPrimaryContainer = Color(0xFFC4ECFB),
        secondary = Color(0xFFB6CAD3),
        secondaryContainer = Color(0xFF324249),
        tertiary = Color(0xFFC8C0E8),
        tertiaryContainer = Color(0xFF3C3658),
        background = Color(0xFF0F1518),
        surface = Color(0xFF0F1518),
        surfaceContainer = Color(0xFF1A2225),
        surfaceContainerHigh = Color(0xFF222B2F),
        surfaceContainerHighest = Color(0xFF2C353A),
        onSurface = Color(0xFFE1E6E9),
        onSurfaceVariant = Color(0xFFB7C3C8),
        outline = Color(0xFF566066),
        outlineVariant = Color(0xFF38424A),
        error = Color(0xFFF2B8B5),
        onError = Color(0xFF601410),
        errorContainer = Color(0xFF8C1D18),
        onErrorContainer = Color(0xFFF9DEDC),
    )
    M3(colorScheme = scheme, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CounterUi(
    value: Int,
    loaded: Boolean,
    onInc: () -> Unit,
    onDec: () -> Unit,
    onReset: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Counter", style = M3.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Saved to encrypted storage",
                            style = M3.typography.labelSmall,
                            color = M3.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = M3.colorScheme.surface,
                ),
            )
        },
        containerColor = M3.colorScheme.background,
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Surface(
                modifier = Modifier.size(width = 280.dp, height = 200.dp),
                shape = RoundedCornerShape(40.dp),
                color = M3.colorScheme.surfaceContainer,
                tonalElevation = 6.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    AnimatedContent(
                        targetState = value,
                        transitionSpec = {
                            val rising = targetState > initialState
                            (slideInVertically(tween(220)) { h -> if (rising) h else -h } + fadeIn(tween(220)))
                                .togetherWith(
                                    slideOutVertically(tween(220)) { h -> if (rising) -h else h } + fadeOut(tween(220))
                                )
                        },
                        label = "counter-value",
                    ) { current ->
                        Text(
                            text = current.toString(),
                            fontSize = 88.sp,
                            fontWeight = FontWeight.Black,
                            color = M3.colorScheme.primary,
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleButton(label = "−", onClick = onDec, primary = false)
                CircleButton(label = "+", onClick = onInc, primary = true)
            }

            Spacer(Modifier.height(24.dp))

            TextButton(
                onClick = onReset,
                enabled = value != 0,
            ) {
                Text("Reset", fontWeight = FontWeight.SemiBold)
            }

            if (!loaded) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Loading saved value…",
                    style = M3.typography.labelSmall,
                    color = M3.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CircleButton(label: String, onClick: () -> Unit, primary: Boolean) {
    val bg = if (primary) M3.colorScheme.primary else M3.colorScheme.surfaceContainerHigh
    val fg = if (primary) M3.colorScheme.onPrimary else M3.colorScheme.onSurface
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(bg, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 36.sp,
            fontWeight = FontWeight.Black,
            color = fg,
        )
    }
}
