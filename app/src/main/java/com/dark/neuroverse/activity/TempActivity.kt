package com.dark.neuroverse.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Easing
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.dark.neuroverse.ui.theme.NeuroVerseTheme
import kotlinx.coroutines.delay
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
            delay(frameDelay)
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
                TempScreen()
            }
        }
    }
}

fun colorsILike() {

    //3. Arctic Dawn (Warm Ice)
    var primaryColor = Color(0xFF00BCD4)
    var secondaryColor = Color(0xFF00ACC1)
    var accentColor = Color(0xFF0F3438)
    var backgroundColor = Color(0xFFEFF8FA)
    var starColor = Color(0xFF006064)
    var starGlowColor = Color(0xFF00BCD4)

    //4. Arctic Teal (My Favorite for "Cold")
    primaryColor = Color(0xFF00E5CC)
    secondaryColor = Color(0xFF00BFA5)
    accentColor = Color(0xFF64FFDA)
    backgroundColor = Color(0xFF051015)
    starColor = Color(0xFFB2DFDB)
    starGlowColor = Color(0xFF00E5CC)

    //1. Crimson Blood (Deep Red)
    primaryColor = Color(0xFFFF1744)
    secondaryColor = Color(0xFFF50057)
    accentColor = Color(0xFFFF4081)
    backgroundColor = Color(0xFF1A0308)
    starColor = Color(0xFFFFCDD2)
    starGlowColor = Color(0xFFFF1744)

    //3. Coral Dawn (Warm Light)
    primaryColor = Color(0xFFFF5252)
    secondaryColor = Color(0xFFFF1744)
    accentColor = Color(0xFFD32F2F)
    backgroundColor = Color(0xFFFFF5F5)
    starColor = Color(0xFF6D1B1B)
    starGlowColor = Color(0xFFFF5252)

    //2. Midnight Moss (Very Minimal)
    primaryColor = Color(0xFF689F38)
    secondaryColor = Color(0xFF558B2F)
    accentColor = Color(0xFF8BC34A)
    backgroundColor = Color(0xFF0D1A0A)
    starColor = Color(0xFFDCEDC8)
    starGlowColor = Color(0xFF689F38)

    //2. Sage Garden (Minimal Light)
    primaryColor = Color(0xFF689F38)
    secondaryColor = Color(0xFF558B2F)
    accentColor = Color(0xFF33691E)
    backgroundColor = Color(0xFFF9FDF7)
    starColor = Color(0xFF33691E)
    starGlowColor = Color(0xFF689F38)
}

@Composable
fun TempScreen() {
    val neuralState = rememberNeuralNetworkState()

    // Current theme state
    var currentTheme by remember { mutableStateOf(myThemes.last()) } // default Sage Garden

    // Animate colors
    val primary by animateColorAsState(currentTheme.primary)
    val secondary by animateColorAsState(currentTheme.secondary)
    val accent by animateColorAsState(currentTheme.accent)
    val background by animateColorAsState(currentTheme.background)
    val star by animateColorAsState(currentTheme.star)
    val starGlow by animateColorAsState(currentTheme.starGlow)

    Scaffold(containerColor = background) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Card(
                Modifier.size(370.dp),
                shape = CircleShape,
                elevation = CardDefaults.outlinedCardElevation(0.dp)
            ) {
                FuturisticNeuralAnimation(
                    modifier = Modifier.fillMaxSize(),
                    state = neuralState,
                    neuronCount = 44,
                    layers = 2,
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

            Spacer(Modifier.height(32.dp))

            // Buttons row
            LazyRow(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(myThemes) { theme ->
                    Button(
                        onClick = { currentTheme = theme },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.LightGray)
                    ) {
                        Text(theme.name, color = Color.Black)
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
