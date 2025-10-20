package com.dark.neuroverse.activity

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RawRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dark.neuroverse.R
import com.dark.neuroverse.ui.theme.NeuroVerseTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

class TempActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NeuroVerseTheme {
                TempScreen()
            }
        }
    }
}

data class Neuron(
    val id: Int,
    val angle: Float,
    val radius: Float,
    val orbitSpeed: Float,
    val pulsePhase: Float,
    val baseSize: Float,
    val layer: Int,
    val alpha: Animatable<Float, AnimationVector1D> = Animatable(0f) // For fade in/out
)

// OPTIMIZED: Simpler state class without animations
class NeuralNetworkState {
    private var _spikeIntensity = mutableFloatStateOf(0f)
    val spikeIntensity: State<Float> = _spikeIntensity

    private var _rotationSpeed = mutableFloatStateOf(1f)
    val rotationSpeed: State<Float> = _rotationSpeed

    private var _pulseSpeed = mutableFloatStateOf(1f)
    val pulseSpeed: State<Float> = _pulseSpeed

    private var _orbitSpeed = mutableFloatStateOf(1f)
    val orbitSpeed: State<Float> = _orbitSpeed

    private var _targetFps = mutableIntStateOf(60)
    val targetFps: State<Int> = _targetFps

    // Direct values without Animatable for better performance
    private var _currentLayers = mutableIntStateOf(3)
    val currentLayers: State<Int> = _currentLayers

    private var _currentNeuronCount = mutableIntStateOf(24)
    val currentNeuronCount: State<Int> = _currentNeuronCount

    private var _isAnimating = mutableStateOf(false)
    val isAnimating: State<Boolean> = _isAnimating

    fun spike(intensity: Float) {
        _spikeIntensity.floatValue = intensity.coerceIn(0f, 1f)
    }

    fun resetSpike() {
        _spikeIntensity.floatValue = 0f
    }

    fun setRotationSpeed(speed: Float) {
        _rotationSpeed.floatValue = speed.coerceIn(0.1f, 10f)
    }

    fun setPulseSpeed(speed: Float) {
        _pulseSpeed.floatValue = speed.coerceIn(0.1f, 10f)
    }

    fun setOrbitSpeed(speed: Float) {
        _orbitSpeed.floatValue = speed.coerceIn(0.1f, 10f)
    }

    fun setAllSpeeds(speed: Float) {
        val clampedSpeed = speed.coerceIn(0.1f, 10f)
        _rotationSpeed.floatValue = clampedSpeed
        _pulseSpeed.floatValue = clampedSpeed
        _orbitSpeed.floatValue = clampedSpeed
    }

    fun setTargetFps(fps: Int) {
        _targetFps.intValue = fps.coerceIn(15, 120)
    }

    // Simplified: Direct updates without animation
    suspend fun setLayers(layers: Int, durationMillis: Int = 800) {
        if (_isAnimating.value) return // Prevent concurrent animations

        _isAnimating.value = true
        _currentLayers.intValue = layers.coerceIn(1, 10)
        delay(durationMillis.toLong())
        _isAnimating.value = false
    }

    suspend fun setNeuronCount(count: Int, durationMillis: Int = 800) {
        if (_isAnimating.value) return // Prevent concurrent animations

        _isAnimating.value = true
        _currentNeuronCount.intValue = count.coerceIn(6, 100)
        delay(durationMillis.toLong())
        _isAnimating.value = false
    }

    fun getCurrentLayers(): Int = _currentLayers.intValue
    fun getCurrentNeuronCount(): Int = _currentNeuronCount.intValue
}

@Composable
fun rememberNeuralNetworkState(): NeuralNetworkState {
    return remember { NeuralNetworkState() }
}

@Composable
fun FuturisticNeuralAnimation(
    modifier: Modifier = Modifier,
    state: NeuralNetworkState = rememberNeuralNetworkState(),
    primaryColor: Color = Color(0xFF00F5FF),
    secondaryColor: Color = Color(0xFFFF006E),
    accentColor: Color = Color(0xFF8338EC),
    backgroundColor: Color = Color(0xFF0A0E27),
    starColor: Color = Color.White,
    starGlowColor: Color? = null,
    baseRotationDuration: Int = 6000,
    basePulseDuration: Int = 2000,
    baseOrbitDuration: Int = 4000
) {
    // Use dynamic neurons that respond to state changes
    val neurons = rememberDynamicNeurons(state)

    val stars = remember {
        List(170) {
            Triple(
                Random.nextFloat(), Random.nextFloat(), 1f + Random.nextFloat() * 2.5f
            )
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "neural")
    val sineEasing = Easing { fraction ->
        0.5f + 0.5f * sin((fraction * 2 * PI - PI / 2).toFloat())
    }

    val rotationSpeedMultiplier = state.rotationSpeed.value
    val pulseSpeedMultiplier = state.pulseSpeed.value
    val orbitSpeedMultiplier = state.orbitSpeed.value
    val fps = state.targetFps.value

    val actualRotationDuration = (baseRotationDuration / rotationSpeedMultiplier).toInt()
    val actualPulseDuration = (basePulseDuration / pulseSpeedMultiplier).toInt()
    val actualOrbitDuration = (baseOrbitDuration / orbitSpeedMultiplier).toInt()

    val frameDelay = 1000L / fps

    val time by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f, animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = actualRotationDuration, easing = sineEasing
            ), repeatMode = RepeatMode.Restart
        ), label = "time"
    )

    val pulse by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f, animationSpec = infiniteRepeatable(
            animation = tween(actualPulseDuration, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "pulse"
    )

    val orbit by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f, animationSpec = infiniteRepeatable(
            animation = tween(actualOrbitDuration, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "orbit"
    )

    val spikeValue = state.spikeIntensity.value
    val effectiveStarGlow = starGlowColor ?: primaryColor

    LaunchedEffect(spikeValue) {
        if (spikeValue > 0f) {
            delay(frameDelay)
            state.spike(spikeValue * 0.85f)
        }
    }

    Canvas(modifier = modifier.background(backgroundColor)) {
        val center = Offset(size.width / 2, size.height / 2)
        val maxRadius = size.minDimension / 2 * 0.85f

        // Calculate positions with alpha for fade effects
        val positions = neurons.map { neuron ->
            val animAngle = (neuron.angle + time * neuron.orbitSpeed) * PI / 180
            val r = (neuron.radius / 240f) * maxRadius
            val spikeBoost = if (spikeValue > 0) r * 0.3f * spikeValue else 0f

            Triple(
                Offset(
                    x = center.x + (r + spikeBoost) * cos(animAngle).toFloat(),
                    y = center.y + (r + spikeBoost) * sin(animAngle).toFloat()
                ), neuron, neuron.alpha.value // Include alpha for fading
            )
        }

        // === STARS ===
        stars.forEachIndexed { index, (xRatio, yRatio, starSize) ->
            val x = xRatio * size.width
            val y = yRatio * size.height
            val twinkle = sin((pulse + index * 24f) * PI / 180).toFloat()
            val alpha = 0.3f + twinkle * 0.4f

            drawCircle(
                color = starColor.copy(alpha = alpha), center = Offset(x, y), radius = starSize
            )

            if (index % 3 == 0) {
                drawCircle(
                    color = effectiveStarGlow.copy(alpha = alpha * 0.4f),
                    center = Offset(x, y),
                    radius = starSize * 2f
                )
            }
        }

        // === NEBULA CLOUDS ===
        repeat(5) { cloud ->
            val cloudX = size.width * (0.2f + cloud * 0.15f)
            val cloudY = size.height * (0.3f + (cloud % 3) * 0.2f)
            val cloudSize = size.minDimension * (0.2f + cloud * 0.05f)

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.08f),
                        secondaryColor.copy(alpha = 0.05f),
                        Color.Transparent
                    ), center = Offset(cloudX, cloudY), radius = cloudSize
                ), center = Offset(cloudX, cloudY), radius = cloudSize
            )
        }

        // === SPIKE WAVES ===
        if (spikeValue > 0.1f) {
            repeat(3) { wave ->
                val waveRadius = maxRadius * spikeValue * (1f + wave * 0.3f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = spikeValue * 0.4f), Color.Transparent
                        ), center = center, radius = waveRadius
                    ), center = center, radius = waveRadius, style = Stroke(width = 2f)
                )
            }
        }

        // === ORBITING PARTICLES ===
        positions.forEach { (pos, neuron, alpha) ->
            if (alpha > 0.1f) { // Only draw if visible
                val orbitPhase = (orbit + neuron.id * 40f) % 360f
                val orbitRadius = 18f + sin((pulse + neuron.id * 20f) * PI / 180).toFloat() * 5f
                repeat(3) { orbitIndex ->
                    val orbitAngle = (orbitPhase + orbitIndex * 120f) * PI / 180
                    val orbitPos = Offset(
                        x = pos.x + orbitRadius * cos(orbitAngle).toFloat(),
                        y = pos.y + orbitRadius * sin(orbitAngle).toFloat()
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                accentColor.copy(alpha = 0.8f * alpha), Color.Transparent
                            ), center = orbitPos, radius = 4f
                        ), center = orbitPos, radius = 2f
                    )
                }
            }
        }

        // === CONNECTIONS ===
        positions.forEachIndexed { i, (pos, neuron, alpha) ->
            if (alpha > 0.1f) {
                positions.forEachIndexed { j, (otherPos, _, otherAlpha) ->
                    if (i < j && otherAlpha > 0.1f) {
                        val distance = kotlin.math.sqrt(
                            (pos.x - otherPos.x).pow(2) + (pos.y - otherPos.y).pow(2)
                        )

                        val connectionThreshold = maxRadius * 0.45f
                        if (distance < connectionThreshold) {
                            val strength = 1f - (distance / connectionThreshold)
                            val combinedAlpha = (alpha + otherAlpha) / 2f
                            val connectionAlpha =
                                strength * 0.6f * (1f + spikeValue * 2f) * combinedAlpha
                            val flowPhase = ((time + neuron.id * 30f) % 360f) / 360f

                            drawLine(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        primaryColor.copy(alpha = connectionAlpha * 0.3f),
                                        secondaryColor.copy(alpha = connectionAlpha * 0.8f),
                                        accentColor.copy(alpha = connectionAlpha * 0.6f),
                                        primaryColor.copy(alpha = connectionAlpha * 0.3f)
                                    ), start = pos, end = otherPos
                                ),
                                start = pos,
                                end = otherPos,
                                strokeWidth = (1f + strength * 2f) * (1f + spikeValue),
                                cap = StrokeCap.Round
                            )

                            if (strength > 0.7f) {
                                val particlePos = Offset(
                                    x = pos.x + (otherPos.x - pos.x) * flowPhase,
                                    y = pos.y + (otherPos.y - pos.y) * flowPhase
                                )

                                drawCircle(
                                    color = accentColor.copy(alpha = 0.9f * combinedAlpha),
                                    radius = 2f * (1f + spikeValue),
                                    center = particlePos
                                )
                            }
                        }
                    }
                }
            }
        }

        // === NEURONS ===
        positions.forEach { (pos, neuron, alpha) ->
            if (alpha > 0.01f) { // Draw even very faint neurons for smooth transitions
                val pulseVal = sin((pulse + neuron.pulsePhase) * PI / 180).toFloat()
                val size = neuron.baseSize * (1f + pulseVal * 0.3f) * (1f + spikeValue * 0.8f)

                // Outer glow
                val glowSize = size * (3f + spikeValue * 3f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = (0.5f + spikeValue * 0.5f) * alpha),
                            secondaryColor.copy(alpha = 0.2f * alpha),
                            Color.Transparent
                        ), center = pos, radius = glowSize
                    ), center = pos, radius = glowSize
                )

                // Ring
                drawCircle(
                    color = accentColor.copy(alpha = (0.7f + pulseVal * 0.3f) * alpha),
                    center = pos,
                    radius = size * 1.3f
                )

                // Core
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = alpha),
                            (if (spikeValue > 0.3f) accentColor else primaryColor).copy(alpha = alpha),
                            secondaryColor.copy(alpha = alpha)
                        ), center = pos, radius = size
                    ), center = pos, radius = size
                )

                // Spark
                if (spikeValue > 0.5f) {
                    drawCircle(
                        color = Color.White.copy(alpha = spikeValue * alpha),
                        center = pos,
                        radius = size * 0.4f
                    )
                }
            }
        }

        // === CENTRAL CORE ===
        val coreSize = 12f * (1f + spikeValue * 1.5f)

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    primaryColor.copy(alpha = 0.7f + spikeValue * 0.3f),
                    accentColor.copy(alpha = 0.3f),
                    Color.Transparent
                ), center = center, radius = coreSize * 4f
            ), center = center, radius = coreSize * 4f
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White, primaryColor, secondaryColor
                ), center = center, radius = coreSize
            ), center = center, radius = coreSize
        )
    }
}

@Composable
fun rememberDynamicNeurons(
    state: NeuralNetworkState
): SnapshotStateList<Neuron> {
    val neurons = remember { mutableStateListOf<Neuron>() }
    val currentLayers = state.getCurrentLayers()
    val currentNeuronCount = state.getCurrentNeuronCount()

    LaunchedEffect(currentLayers, currentNeuronCount) {
        val targetSize = currentNeuronCount

        // OPTIMIZED: Remove excess neurons quickly
        if (neurons.size > targetSize) {
            val neuronsToRemove = neurons.size - targetSize
            val toRemove = neurons.takeLast(neuronsToRemove)

            // Fade out in parallel
            toRemove.forEach { neuron ->
                launch {
                    neuron.alpha.animateTo(
                        0f, animationSpec = tween(150, easing = FastOutSlowInEasing)
                    )
                }
            }

            delay(170)

            // Batch removal
            repeat(neuronsToRemove) {
                if (neurons.isNotEmpty()) {
                    neurons.removeAt(neurons.lastIndex)
                }
            }
        }

        // OPTIMIZED: Add new neurons in batches
        if (neurons.size < targetSize) {
            val neuronsToAdd = targetSize - neurons.size
            val newNeurons = mutableListOf<Neuron>()

            repeat(neuronsToAdd) {
                val id = neurons.size + it
                val layer = (id * currentLayers) / targetSize
                val neuronsInLayer = maxOf(1, targetSize / currentLayers)
                val posInLayer = id % neuronsInLayer

                newNeurons.add(
                    Neuron(
                        id = id,
                        angle = (posInLayer * 360f / neuronsInLayer) + (layer * 15f),
                        radius = 80f + (layer * 60f),
                        orbitSpeed = 0.3f + (Random.nextFloat() * 0.4f) * (if (layer % 2 == 0) 1f else -1f),
                        pulsePhase = Random.nextFloat() * 360f,
                        baseSize = 3f + Random.nextFloat() * 2f,
                        layer = layer
                    )
                )
            }

            neurons.addAll(newNeurons)

            // Animate in smaller batches
            val batchSize = 8
            newNeurons.chunked(batchSize).forEach { batch ->
                batch.forEach { neuron ->
                    launch {
                        neuron.alpha.animateTo(
                            1f, animationSpec = tween(200, easing = FastOutSlowInEasing)
                        )
                    }
                }
                delay(30)
            }
        }
    }

    return neurons
}

@Composable
fun TempScreen() {
    val neuralState = rememberNeuralNetworkState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val interfaceSwitched = rememberSoundPlayer(context, R.raw.interface_switch)
    val interfaceSuccess = rememberSoundPlayer(context, R.raw.interface_success)
    val interfaceError = rememberSoundPlayer(context, R.raw.error_interface)
    val interfaceStage = rememberSoundPlayer(context, R.raw.stages_interface) // 6sec sound effect

    var currentTheme by remember { mutableStateOf(myThemes.last()) }

    val primary by animateColorAsState(
        currentTheme.primary, tween(durationMillis = 600), label = "primary"
    )
    val secondary by animateColorAsState(
        currentTheme.secondary, tween(durationMillis = 600), label = "secondary"
    )
    val accent by animateColorAsState(
        currentTheme.accent, tween(durationMillis = 600), label = "accent"
    )
    val background by animateColorAsState(
        currentTheme.background, tween(durationMillis = 600), label = "background"
    )
    val star by animateColorAsState(currentTheme.star, tween(durationMillis = 600), label = "star")
    val starGlow by animateColorAsState(
        currentTheme.starGlow, tween(durationMillis = 600), label = "starGlow"
    )
    var size by remember { mutableStateOf(200.dp) }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Card(
                Modifier.animateContentSize().size(size),
                shape = CircleShape,
                elevation = CardDefaults.outlinedCardElevation(0.dp)
            ) {
                FuturisticNeuralAnimation(
                    modifier = Modifier.fillMaxSize(),
                    state = neuralState,
                    primaryColor = primary,
                    secondaryColor = secondary,
                    accentColor = accent,
                    backgroundColor = background,
                    starColor = star,
                    starGlowColor = starGlow,
                    baseRotationDuration = 6000,
                    basePulseDuration = 2000,
                    baseOrbitDuration = 1000
                )
            }
            Spacer(Modifier.height(24.dp))

            Row (Modifier.fillMaxWidth().padding(horizontal = 46.dp)){
                ModernSlider("Label", "Description", size.value, { size = it.dp }, 100f..300f, 10)
            }

            Spacer(Modifier.height(24.dp))

            // === LAYER CONTROLS ===
            Text(
                "Layers",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(8.dp)
            )

            FlowRow(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.Center,
                maxItemsInEachRow = 5
            ) {
                listOf(1, 2, 3, 4, 5).forEach { layers ->
                    Button(
                        onClick = {
                            interfaceSuccess()
                            // OPTIMIZED: Direct call, no coroutine needed
                            coroutineScope.launch {
                                neuralState.setLayers(layers)
                            }
                        }, modifier = Modifier.padding(4.dp), colors = ButtonDefaults.buttonColors()
                    ) {
                        Text("$layers")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // === NEURON COUNT CONTROLS ===
            Text(
                "Neuron Count",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(8.dp)
            )

            FlowRow(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.Center,
                maxItemsInEachRow = 5
            ) {
                listOf(12, 24, 36, 48, 60).forEach { count ->
                    Button(
                        onClick = {
                            interfaceSuccess()
                            // OPTIMIZED: Direct call
                            coroutineScope.launch {
                                neuralState.setNeuronCount(count)
                            }
                        }, modifier = Modifier.padding(4.dp), colors = ButtonDefaults.buttonColors()
                    ) {
                        Text("$count")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // === QUICK PRESETS ===
            Text(
                "Quick Presets",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(8.dp)
            )

            FlowRow(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.Center,
                maxItemsInEachRow = 3
            ) {
                Button(
                    onClick = {
                        interfaceSuccess()
                        coroutineScope.launch {
                            neuralState.setLayers(2)
                            neuralState.setNeuronCount(24)
                        }
                    }, modifier = Modifier.padding(4.dp)
                ) {
                    Text("Simple")
                }

                Button(
                    onClick = {
                        interfaceSuccess()
                        coroutineScope.launch {
                            neuralState.setLayers(3)
                            neuralState.setNeuronCount(46)
                        }
                    }, modifier = Modifier.padding(4.dp)
                ) {
                    Text("Balanced")
                }

                Button(
                    onClick = {
                        interfaceSuccess()
                        coroutineScope.launch {
                            neuralState.setLayers(5)
                            neuralState.setNeuronCount(80)
                        }
                    }, modifier = Modifier.padding(4.dp)
                ) {
                    Text("Complex")
                }
            }

            Spacer(Modifier.height(24.dp))

            // === THEME CONTROLS ===
            Text(
                "Themes",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(8.dp)
            )

            FlowRow(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp), horizontalArrangement = Arrangement.Center
            ) {
                myThemes.forEach { theme ->
                    Button(
                        onClick = {
                            interfaceSwitched()
                            currentTheme = theme
                        }, modifier = Modifier.padding(4.dp), colors = ButtonDefaults.buttonColors(
                            containerColor = theme.primary
                        )
                    ) {
                        Text(theme.name, maxLines = 1)
                    }
                }
            }
        }
    }
}

data class ThemeColors(
    val name: String,
    val primary: Color,
    val secondary: Color,
    val accent: Color,
    val background: Color,
    val star: Color,
    val starGlow: Color
)

val myThemes = listOf(
    ThemeColors(
        "Arctic Dawn",
        primary = Color(0xFF00BCD4),
        secondary = Color(0xFF00ACC1),
        accent = Color(0xFF0F3438),
        background = Color(0xFFEFF8FA),
        star = Color(0xFF006064),
        starGlow = Color(0xFF00BCD4)
    ), ThemeColors(
        "Arctic Teal",
        primary = Color(0xFF00E5CC),
        secondary = Color(0xFF00BFA5),
        accent = Color(0xFF64FFDA),
        background = Color(0xFF051015),
        star = Color(0xFFB2DFDB),
        starGlow = Color(0xFF00E5CC)
    ), ThemeColors(
        "Crimson Blood",
        primary = Color(0xFFFF1744),
        secondary = Color(0xFFF50057),
        accent = Color(0xFFFF4081),
        background = Color(0xFF1A0308),
        star = Color(0xFFFFCDD2),
        starGlow = Color(0xFFFF1744)
    ), ThemeColors(
        "Coral Dawn",
        primary = Color(0xFFFF5252),
        secondary = Color(0xFFFF1744),
        accent = Color(0xFFD32F2F),
        background = Color(0xFFFFF5F5),
        star = Color(0xFF6D1B1B),
        starGlow = Color(0xFFFF5252)
    ), ThemeColors(
        "Midnight Moss",
        primary = Color(0xFF689F38),
        secondary = Color(0xFF558B2F),
        accent = Color(0xFF8BC34A),
        background = Color(0xFF0D1A0A),
        star = Color(0xFFDCEDC8),
        starGlow = Color(0xFF689F38)
    ), ThemeColors(
        "Sage Garden",
        primary = Color(0xFF689F38),
        secondary = Color(0xFF558B2F),
        accent = Color(0xFF33691E),
        background = Color(0xFFF9FDF7),
        star = Color(0xFF33691E),
        starGlow = Color(0xFF689F38)
    )
)

@Composable
fun rememberSoundPlayer(context: Context, @RawRes soundResId: Int): () -> Unit {
    val soundPool = remember {
        SoundPool.Builder().setMaxStreams(5).setAudioAttributes(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        ).build()
    }

    var soundId by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        soundId = soundPool.load(context, soundResId, 1)
    }

    DisposableEffect(Unit) {
        onDispose {
            soundPool.release()
        }
    }

    return {
        if (soundId != 0) {
            soundPool.play(soundId, 1f, 1f, 1, 0, 1f)
        }
    }
}