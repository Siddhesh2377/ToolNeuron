package com.dark.neuroverse.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.dark.neuroverse.ui.theme.NeuroVerseTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

data class Neuron(
    val id: Int,
    var angle: Float,
    var radius: Float,
    val orbitSpeed: Float,
    val pulsePhase: Float,
    val baseSize: Float,
    val layer: Int
)

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

    fun spike(intensity: Float) {
        _spikeIntensity.floatValue = intensity.coerceIn(0f, 1f)
    }

    fun resetSpike() {
        _spikeIntensity.floatValue = 0f
    }

    // Speed controls (0.1f to 5f recommended)
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

    // FPS control (15 to 120)
    fun setTargetFps(fps: Int) {
        _targetFps.intValue = fps.coerceIn(15, 120)
    }
}

@Composable
fun rememberNeuralNetworkState(): NeuralNetworkState {
    return remember { NeuralNetworkState() }
}

@Composable
fun FuturisticNeuralAnimation(
    modifier: Modifier = Modifier,
    state: NeuralNetworkState = rememberNeuralNetworkState(),
    neuronCount: Int = 24,
    layers: Int = 3,
    primaryColor: Color = Color(0xFF00F5FF),
    secondaryColor: Color = Color(0xFFFF006E),
    accentColor: Color = Color(0xFF8338EC),
    backgroundColor: Color = Color(0xFF0A0E27),
    starColor: Color = Color.White,
    starGlowColor: Color? = null,
    baseRotationDuration: Int = 6000, // Base duration in milliseconds
    basePulseDuration: Int = 2000,    // Base pulse duration
    baseOrbitDuration: Int = 4000     // Base orbit duration
) {
    val neurons = remember {
        mutableListOf<Neuron>().apply {
            repeat(layers) { layer ->
                val neuronsInLayer = neuronCount / layers
                repeat(neuronsInLayer) { i ->
                    add(
                        Neuron(
                            id = layer * neuronsInLayer + i,
                            angle = (i * 360f / neuronsInLayer) + (layer * 15f),
                            radius = 80f + (layer * 60f),
                            orbitSpeed = 0.3f + (Random.nextFloat() * 0.4f) * (if (layer % 2 == 0) 1f else -1f),
                            pulsePhase = Random.nextFloat() * 360f,
                            baseSize = 3f + Random.nextFloat() * 2f,
                            layer = layer
                        )
                    )
                }
            }
        }
    }

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

    // Dynamic speeds from state
    val rotationSpeedMultiplier = state.rotationSpeed.value
    val pulseSpeedMultiplier = state.pulseSpeed.value
    val orbitSpeedMultiplier = state.orbitSpeed.value
    val fps = state.targetFps.value

    // Calculate actual durations based on speed multipliers
    val actualRotationDuration = (baseRotationDuration / rotationSpeedMultiplier).toInt()
    val actualPulseDuration = (basePulseDuration / pulseSpeedMultiplier).toInt()
    val actualOrbitDuration = (baseOrbitDuration / orbitSpeedMultiplier).toInt()

    // Frame limiting
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

    // Auto-decay spike with frame control
    LaunchedEffect(spikeValue) {
        if (spikeValue > 0f) {
            kotlinx.coroutines.delay(frameDelay)
            state.spike(spikeValue * 0.85f)
        }
    }

    Canvas(modifier = modifier.background(backgroundColor)) {
        val center = Offset(size.width / 2, size.height / 2)
        val maxRadius = size.minDimension / 2 * 0.85f

        val positions = neurons.map { neuron ->
            val animAngle = (neuron.angle + time * neuron.orbitSpeed) * PI / 180
            val r = (neuron.radius / 240f) * maxRadius
            val spikeBoost = if (spikeValue > 0) r * 0.3f * spikeValue else 0f

            Offset(
                x = center.x + (r + spikeBoost) * cos(animAngle).toFloat(),
                y = center.y + (r + spikeBoost) * sin(animAngle).toFloat()
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

        // === ORBITING PARTICLES (using orbit animation) ===
        positions.forEachIndexed { i, pos ->
            val orbitPhase = (orbit + i * 40f) % 360f
            val orbitRadius = 18f + sin((pulse + i * 20f) * PI / 180).toFloat() * 5f
            repeat(3) { orbitIndex ->
                val orbitAngle = (orbitPhase + orbitIndex * 120f) * PI / 180
                val orbitPos = Offset(
                    x = pos.x + orbitRadius * cos(orbitAngle).toFloat(),
                    y = pos.y + orbitRadius * sin(orbitAngle).toFloat()
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.8f), Color.Transparent
                        ), center = orbitPos, radius = 4f
                    ), center = orbitPos, radius = 2f
                )
            }
        }

        // === CONNECTIONS ===
        neurons.forEachIndexed { i, neuron ->
            val pos = positions[i]

            neurons.forEachIndexed { j, other ->
                if (i < j) {
                    val otherPos = positions[j]
                    val distance = kotlin.math.sqrt(
                        (pos.x - otherPos.x).pow(2) + (pos.y - otherPos.y).pow(2)
                    )

                    val connectionThreshold = maxRadius * 0.45f
                    if (distance < connectionThreshold) {
                        val strength = 1f - (distance / connectionThreshold)
                        val alpha = strength * 0.6f * (1f + spikeValue * 2f)
                        val flowPhase = ((time + neuron.id * 30f) % 360f) / 360f

                        drawLine(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    primaryColor.copy(alpha = alpha * 0.3f),
                                    secondaryColor.copy(alpha = alpha * 0.8f),
                                    accentColor.copy(alpha = alpha * 0.6f),
                                    primaryColor.copy(alpha = alpha * 0.3f)
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
                                color = accentColor.copy(alpha = 0.9f),
                                radius = 2f * (1f + spikeValue),
                                center = particlePos
                            )
                        }
                    }
                }
            }
        }

        // === NEURONS ===
        neurons.forEachIndexed { i, neuron ->
            val pos = positions[i]
            val pulseVal = sin((pulse + neuron.pulsePhase) * PI / 180).toFloat()
            val size = neuron.baseSize * (1f + pulseVal * 0.3f) * (1f + spikeValue * 0.8f)

            // Outer glow
            val glowSize = size * (3f + spikeValue * 3f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = (0.5f + spikeValue * 0.5f)),
                        secondaryColor.copy(alpha = 0.2f),
                        Color.Transparent
                    ), center = pos, radius = glowSize
                ), center = pos, radius = glowSize
            )

            // Ring
            drawCircle(
                color = accentColor.copy(alpha = 0.7f + pulseVal * 0.3f),
                center = pos,
                radius = size * 1.3f
            )

            // Core
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White,
                        if (spikeValue > 0.3f) accentColor else primaryColor,
                        secondaryColor
                    ), center = pos, radius = size
                ), center = pos, radius = size
            )

            // Spark
            if (spikeValue > 0.5f) {
                drawCircle(
                    color = Color.White.copy(alpha = spikeValue), center = pos, radius = size * 0.4f
                )
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

class TempActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NeuroVerseTheme {
                Scaffold {
                    val neuralState = rememberNeuralNetworkState()

                    // Set speeds (optional)
                    LaunchedEffect(Unit) {
                        neuralState.setRotationSpeed(2f)  // 1.5x faster rotation
                        neuralState.setPulseSpeed(1f)       // 2x faster pulse
                        neuralState.setOrbitSpeed(3f)     // 1.2x faster orbits
                        neuralState.setTargetFps(90)        // 60 FPS
                    }

                    // Variable spike patterns
                    LaunchedEffect(Unit) {
                        while (true) {
                            neuralState.spike(0.3f)
                            kotlinx.coroutines.delay(2000)

                            neuralState.spike(0.3f)
                            kotlinx.coroutines.delay(800)

                            neuralState.spike(0.3f)
                            kotlinx.coroutines.delay(1500)

                            neuralState.spike(0.3f)
                            kotlinx.coroutines.delay(1200)

                            kotlinx.coroutines.delay(3000)
                        }
                    }

                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(it),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Card(
                            Modifier.size(370.dp),
                            shape = CircleShape,
                            elevation = CardDefaults.outlinedCardElevation(
                                defaultElevation = 0.dp
                            ),
                        ) {
                            FuturisticNeuralAnimation(
                                modifier = Modifier.fillMaxSize(),
                                state = neuralState,
                                neuronCount = 44,
                                layers = 3,
                                primaryColor = Color(0xFFFF6B6B),
                                secondaryColor = Color(0xFFEE5A6F),
                                accentColor = Color(0xFF000000),
                                backgroundColor = Color(0xFFFFFBF5),
                                starColor = Color(0xFF262626),
                                starGlowColor = Color(0xFFFF6B6B),
                                baseRotationDuration = 6000,
                                basePulseDuration = 2000,
                                baseOrbitDuration = 1000
                            )
                        }
                    }
                }
            }
        }
    }
}