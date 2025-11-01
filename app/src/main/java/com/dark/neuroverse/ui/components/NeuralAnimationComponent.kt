package com.dark.neuroverse.ui.components

import androidx.compose.animation.VectorConverter
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Configuration for the Neural Animation
 */
data class NeuralAnimationConfig(
    val name: String = "Default",
    val primaryColor: Color = Color(0xFF00F5FF),
    val secondaryColor: Color = Color(0xFFFF006E),
    val accentColor: Color = Color(0xFF8338EC),
    val backgroundColor: Color = Color(0xFF0A0E27),
    val starColor: Color = Color.White,
    val starGlowColor: Color = Color(0xFF00F5FF),
    val baseRotationDuration: Int = 6000,
    val basePulseDuration: Int = 2000,
    val baseOrbitDuration: Int = 4000,
    val initialLayers: Int = 3,
    val initialNeuronCount: Int = 24,
    val starCount: Int = 170
)

/**
 * Predefined Neural Animation Themes
 */
object NeuralThemes {
    val Default = NeuralAnimationConfig(
        name = "Default",
        primaryColor = Color(0xFF00F5FF),
        secondaryColor = Color(0xFFFF006E),
        accentColor = Color(0xFF8338EC),
        backgroundColor = Color(0xFF0A0E27),
        starColor = Color.White,
        starGlowColor = Color(0xFF00F5FF)
    )

    val ArcticDawn = NeuralAnimationConfig(
        name = "Arctic Dawn",
        primaryColor = Color(0xFF00BCD4),
        secondaryColor = Color(0xFF00ACC1),
        accentColor = Color(0xFF0F3438),
        backgroundColor = Color(0xFFEFF8FA),
        starColor = Color(0xFF006064),
        starGlowColor = Color(0xFF00BCD4)
    )

    val ArcticTeal = NeuralAnimationConfig(
        name = "Arctic Teal",
        primaryColor = Color(0xFF00E5CC),
        secondaryColor = Color(0xFF00BFA5),
        accentColor = Color(0xFF64FFDA),
        backgroundColor = Color(0xFF051015),
        starColor = Color(0xFFB2DFDB),
        starGlowColor = Color(0xFF00E5CC)
    )

    val CrimsonBlood = NeuralAnimationConfig(
        name = "Crimson Blood",
        primaryColor = Color(0xFFFF1744),
        secondaryColor = Color(0xFFF50057),
        accentColor = Color(0xFFFF4081),
        backgroundColor = Color(0xFF1A0308),
        starColor = Color(0xFFFFCDD2),
        starGlowColor = Color(0xFFFF1744)
    )

    val CoralDawn = NeuralAnimationConfig(
        name = "Coral Dawn",
        primaryColor = Color(0xFFFF5252),
        secondaryColor = Color(0xFFFF1744),
        accentColor = Color(0xFFD32F2F),
        backgroundColor = Color(0xFFFFF5F5),
        starColor = Color(0xFF6D1B1B),
        starGlowColor = Color(0xFFFF5252)
    )

    val MidnightMoss = NeuralAnimationConfig(
        name = "Midnight Moss",
        primaryColor = Color(0xFF689F38),
        secondaryColor = Color(0xFF558B2F),
        accentColor = Color(0xFF8BC34A),
        backgroundColor = Color(0xFF0D1A0A),
        starColor = Color(0xFFDCEDC8),
        starGlowColor = Color(0xFF689F38)
    )

    val SageGarden = NeuralAnimationConfig(
        name = "Sage Garden",
        primaryColor = Color(0xFF689F38),
        secondaryColor = Color(0xFF558B2F),
        accentColor = Color(0xFF33691E),
        backgroundColor = Color(0xFFF9FDF7),
        starColor = Color(0xFF33691E),
        starGlowColor = Color(0xFF689F38)
    )

    val allThemes = listOf(
        Default, ArcticDawn, ArcticTeal, CrimsonBlood, CoralDawn, MidnightMoss, SageGarden
    )
}

/**
 * Animated Colors for smooth theme transitions
 */
class AnimatedColors {
    private val colorSpace = ColorSpaces.Srgb

    val primary = Animatable(Color(0xFF00F5FF), Color.VectorConverter(colorSpace))
    val secondary = Animatable(Color(0xFFFF006E), Color.VectorConverter(colorSpace))
    val accent = Animatable(Color(0xFF8338EC), Color.VectorConverter(colorSpace))
    val background = Animatable(Color(0xFF0A0E27), Color.VectorConverter(colorSpace))
    val star = Animatable(Color.White, Color.VectorConverter(colorSpace))
    val starGlow = Animatable(Color(0xFF00F5FF), Color.VectorConverter(colorSpace))

    suspend fun animateTo(config: NeuralAnimationConfig) = withContext(Dispatchers.Main) {
        launch {
            primary.animateTo(
                config.primaryColor,
                spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)
            )
        }
        launch {
            secondary.animateTo(
                config.secondaryColor,
                spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)
            )
        }
        launch {
            accent.animateTo(
                config.accentColor,
                spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)
            )
        }
        launch {
            background.animateTo(
                config.backgroundColor,
                spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow)
            )
        }
        launch {
            star.animateTo(
                config.starColor,
                spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)
            )
        }
        launch {
            starGlow.animateTo(
                config.starGlowColor,
                spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)
            )
        }
    }
}

/**
 * Represents a single neuron in the network
 */
data class Neuron(
    val id: Int,
    val angle: Float,
    val radius: Float,
    val orbitSpeed: Float,
    val pulsePhase: Float,
    val baseSize: Float,
    val layer: Int,
    val alpha: Animatable<Float, AnimationVector1D> = Animatable(0f)
)

/**
 * State management for the Neural Network Animation
 */
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

    var _currentLayers = mutableIntStateOf(3)
    val currentLayers: State<Int> = _currentLayers

    var _currentNeuronCount = mutableIntStateOf(24)
    val currentNeuronCount: State<Int> = _currentNeuronCount

    private var _isAnimating = mutableStateOf(false)
    val isAnimating: State<Boolean> = _isAnimating

    var _currentConfig = mutableStateOf(NeuralAnimationConfig())
    val currentConfig: State<NeuralAnimationConfig> = _currentConfig

    val animatedColors = AnimatedColors()

    init {
        // Set initial colors without animation
        _currentConfig.value = NeuralAnimationConfig()
    }

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

    suspend fun setLayers(layers: Int, durationMillis: Int = 800) {
        if (_isAnimating.value) return
        _isAnimating.value = true
        _currentLayers.intValue = layers.coerceIn(1, 10)
        delay(durationMillis.toLong())
        _isAnimating.value = false
    }

    suspend fun setNeuronCount(count: Int, durationMillis: Int = 800) {
        if (_isAnimating.value) return
        _isAnimating.value = true
        _currentNeuronCount.intValue = count.coerceIn(6, 100)
        delay(durationMillis.toLong())
        _isAnimating.value = false
    }

    suspend fun setTheme(config: NeuralAnimationConfig) {
        _currentConfig.value = config
        animatedColors.animateTo(config)
    }

    fun getCurrentLayers(): Int = _currentLayers.intValue
    fun getCurrentNeuronCount(): Int = _currentNeuronCount.intValue
}

/**
 * Remember Neural Network State
 */
@Composable
fun rememberNeuralNetworkState(
    initialLayers: Int = 3,
    initialNeuronCount: Int = 24,
    initialConfig: NeuralAnimationConfig = NeuralAnimationConfig()
): NeuralNetworkState {
    val state = remember {
        NeuralNetworkState().apply {
            _currentLayers.intValue = initialLayers
            _currentNeuronCount.intValue = initialNeuronCount
            _currentConfig.value = initialConfig
        }
    }

    // Initialize animated colors immediately without animation
    LaunchedEffect(Unit) {
        with(state.animatedColors) {
            primary.snapTo(initialConfig.primaryColor)
            secondary.snapTo(initialConfig.secondaryColor)
            accent.snapTo(initialConfig.accentColor)
            background.snapTo(initialConfig.backgroundColor)
            star.snapTo(initialConfig.starColor)
            starGlow.snapTo(initialConfig.starGlowColor)
        }
    }

    return state
}

/**
 * Main Neural Animation Composable - Optimized
 */
@Composable
fun FuturisticNeuralAnimation(
    modifier: Modifier = Modifier,
    config: NeuralAnimationConfig = NeuralAnimationConfig(),
    state: NeuralNetworkState? = null
) {
    val neuralState = state ?: rememberNeuralNetworkState(
        initialLayers = config.initialLayers,
        initialNeuronCount = config.initialNeuronCount,
        initialConfig = config
    )

    // Update theme when config changes
    LaunchedEffect(config) {
        neuralState.setTheme(config)
    }

    val neurons = rememberDynamicNeurons(neuralState)

    // Reduce star count for performance
    val stars = remember(config.starCount) {
        List(minOf(config.starCount, 100)) {
            Triple(
                Random.nextFloat(), Random.nextFloat(), 1f + Random.nextFloat() * 2f
            )
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "neural")
    val sineEasing = remember {
        Easing { fraction ->
            0.5f + 0.5f * sin((fraction * 2 * PI - PI / 2).toFloat())
        }
    }

    val rotationSpeedMultiplier = neuralState.rotationSpeed.value
    val pulseSpeedMultiplier = neuralState.pulseSpeed.value
    val orbitSpeedMultiplier = neuralState.orbitSpeed.value
    val fps = neuralState.targetFps.value

    val actualRotationDuration = (config.baseRotationDuration / rotationSpeedMultiplier).toInt()
    val actualPulseDuration = (config.basePulseDuration / pulseSpeedMultiplier).toInt()
    val actualOrbitDuration = (config.baseOrbitDuration / orbitSpeedMultiplier).toInt()

    val frameDelay = 1000L / fps

    val time by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f, animationSpec = infiniteRepeatable(
            animation = tween(actualRotationDuration, easing = sineEasing),
            repeatMode = RepeatMode.Restart
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

    val spikeValue = neuralState.spikeIntensity.value

    // Get animated colors
    val primaryColor = neuralState.animatedColors.primary.value
    val secondaryColor = neuralState.animatedColors.secondary.value
    val accentColor = neuralState.animatedColors.accent.value
    val backgroundColor = neuralState.animatedColors.background.value
    val starColor = neuralState.animatedColors.star.value
    val starGlowColor = neuralState.animatedColors.starGlow.value

    LaunchedEffect(spikeValue) {
        if (spikeValue > 0f) {
            delay(frameDelay)
            neuralState.spike(spikeValue * 0.85f)
        }
    }

    Canvas(modifier = modifier.background(backgroundColor)) {
        val center = Offset(size.width / 2, size.height / 2)
        val maxRadius = size.minDimension / 2 * 0.85f

        // Pre-calculate neuron positions (optimization)
        val positions = neurons.mapNotNull { neuron ->
            if (neuron.alpha.value < 0.01f) return@mapNotNull null

            val animAngle = (neuron.angle + time * neuron.orbitSpeed) * PI / 180
            val r = (neuron.radius / 240f) * maxRadius
            val spikeBoost = if (spikeValue > 0) r * 0.3f * spikeValue else 0f

            Triple(
                Offset(
                    x = center.x + (r + spikeBoost) * cos(animAngle).toFloat(),
                    y = center.y + (r + spikeBoost) * sin(animAngle).toFloat()
                ), neuron, neuron.alpha.value
            )
        }

        // === STARS (Optimized - render every other frame) ===
        if (time.toInt() % 2 == 0) {
            stars.forEachIndexed { index, (xRatio, yRatio, starSize) ->
                val x = xRatio * size.width
                val y = yRatio * size.height
                val twinkle = sin((pulse + index * 24f) * PI / 180).toFloat()
                val alpha = 0.3f + twinkle * 0.4f

                drawCircle(
                    color = starColor.copy(alpha = alpha), center = Offset(x, y), radius = starSize
                )

                if (index % 4 == 0) {
                    drawCircle(
                        color = starGlowColor.copy(alpha = alpha * 0.3f),
                        center = Offset(x, y),
                        radius = starSize * 1.8f
                    )
                }
            }
        }

        // === NEBULA CLOUDS (Reduced) ===
        repeat(3) { cloud ->
            val cloudX = size.width * (0.25f + cloud * 0.25f)
            val cloudY = size.height * (0.35f + (cloud % 2) * 0.3f)
            val cloudSize = size.minDimension * (0.18f + cloud * 0.04f)

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.06f),
                        secondaryColor.copy(alpha = 0.03f),
                        Color.Transparent
                    ), center = Offset(cloudX, cloudY), radius = cloudSize
                ), center = Offset(cloudX, cloudY), radius = cloudSize
            )
        }

        // === SPIKE WAVES ===
        if (spikeValue > 0.15f) {
            repeat(2) { wave ->
                val waveRadius = maxRadius * spikeValue * (1f + wave * 0.4f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = spikeValue * 0.35f), Color.Transparent
                        ), center = center, radius = waveRadius
                    ), center = center, radius = waveRadius, style = Stroke(width = 1.5f)
                )
            }
        }

        // === ORBITING PARTICLES (Optimized) ===
        positions.forEach { (pos, neuron, alpha) ->
            if (alpha > 0.2f && neuron.id % 2 == 0) {
                val orbitPhase = (orbit + neuron.id * 40f) % 360f
                val orbitRadius = 16f + sin((pulse + neuron.id * 20f) * PI / 180).toFloat() * 4f
                repeat(2) { orbitIndex ->
                    val orbitAngle = (orbitPhase + orbitIndex * 180f) * PI / 180
                    val orbitPos = Offset(
                        x = pos.x + orbitRadius * cos(orbitAngle).toFloat(),
                        y = pos.y + orbitRadius * sin(orbitAngle).toFloat()
                    )
                    drawCircle(
                        color = accentColor.copy(alpha = 0.7f * alpha),
                        center = orbitPos,
                        radius = 1.5f
                    )
                }
            }
        }

        // === CONNECTIONS (Optimized) ===
        val connectionThreshold = maxRadius * 0.4f
        positions.forEachIndexed { i, (pos, neuron, alpha) ->
            positions.forEachIndexed { j, (otherPos, _, otherAlpha) ->
                if (i < j) {
                    val dx = pos.x - otherPos.x
                    val dy = pos.y - otherPos.y
                    val distance = kotlin.math.sqrt(dx * dx + dy * dy)

                    if (distance < connectionThreshold) {
                        val strength = 1f - (distance / connectionThreshold)
                        val combinedAlpha = (alpha + otherAlpha) / 2f
                        val connectionAlpha =
                            strength * 0.5f * (1f + spikeValue * 1.5f) * combinedAlpha

                        drawLine(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    primaryColor.copy(alpha = connectionAlpha * 0.3f),
                                    secondaryColor.copy(alpha = connectionAlpha * 0.7f),
                                    accentColor.copy(alpha = connectionAlpha * 0.5f)
                                ), start = pos, end = otherPos
                            ),
                            start = pos,
                            end = otherPos,
                            strokeWidth = (1f + strength * 1.5f) * (1f + spikeValue * 0.5f),
                            cap = StrokeCap.Round
                        )

                        if (strength > 0.75f) {
                            val flowPhase = ((time + neuron.id * 30f) % 360f) / 360f
                            val particlePos = Offset(
                                x = pos.x + dx * flowPhase, y = pos.y + dy * flowPhase
                            )

                            drawCircle(
                                color = accentColor.copy(alpha = 0.8f * combinedAlpha),
                                radius = 1.5f * (1f + spikeValue * 0.5f),
                                center = particlePos
                            )
                        }
                    }
                }
            }
        }

        // === NEURONS ===
        positions.forEach { (pos, neuron, alpha) ->
            val pulseVal = sin((pulse + neuron.pulsePhase) * PI / 180).toFloat()
            val size = neuron.baseSize * (1f + pulseVal * 0.25f) * (1f + spikeValue * 0.6f)

            // Outer glow
            val glowSize = size * (2.5f + spikeValue * 2f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = (0.4f + spikeValue * 0.4f) * alpha),
                        secondaryColor.copy(alpha = 0.15f * alpha),
                        Color.Transparent
                    ), center = pos, radius = glowSize
                ), center = pos, radius = glowSize
            )

            // Ring
            drawCircle(
                color = accentColor.copy(alpha = (0.6f + pulseVal * 0.25f) * alpha),
                center = pos,
                radius = size * 1.25f
            )

            // Core
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = alpha * 0.9f),
                        (if (spikeValue > 0.3f) accentColor else primaryColor).copy(alpha = alpha),
                        secondaryColor.copy(alpha = alpha * 0.8f)
                    ), center = pos, radius = size
                ), center = pos, radius = size
            )

            // Spark
            if (spikeValue > 0.5f) {
                drawCircle(
                    color = Color.White.copy(alpha = spikeValue * alpha * 0.8f),
                    center = pos,
                    radius = size * 0.35f
                )
            }
        }

        // === CENTRAL CORE ===
        val coreSize = 7f * (1f + spikeValue * 1.2f)

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    primaryColor.copy(alpha = 0.65f + spikeValue * 0.25f),
                    accentColor.copy(alpha = 0.25f),
                    Color.Transparent
                ), center = center, radius = coreSize * 3.5f
            ), center = center, radius = coreSize * 3.5f
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

/**
 * Remember and manage dynamic neurons
 */
@Composable
private fun rememberDynamicNeurons(
    state: NeuralNetworkState
): SnapshotStateList<Neuron> {
    val neurons = remember { mutableStateListOf<Neuron>() }
    val currentLayers = state.getCurrentLayers()
    val currentNeuronCount = state.getCurrentNeuronCount()

    LaunchedEffect(currentLayers, currentNeuronCount) {
        if (neurons.size > currentNeuronCount) {
            val neuronsToRemove = neurons.size - currentNeuronCount
            val toRemove = neurons.takeLast(neuronsToRemove)

            toRemove.forEach { neuron ->
                launch {
                    neuron.alpha.animateTo(0f, tween(150, easing = FastOutSlowInEasing))
                }
            }

            delay(170)
            repeat(neuronsToRemove) {
                if (neurons.isNotEmpty()) neurons.removeAt(neurons.lastIndex)
            }
        }

        if (neurons.size < currentNeuronCount) {
            val neuronsToAdd = currentNeuronCount - neurons.size
            val newNeurons = mutableListOf<Neuron>()

            repeat(neuronsToAdd) {
                val id = neurons.size + it
                val layer = (id * currentLayers) / currentNeuronCount
                val neuronsInLayer = maxOf(1, currentNeuronCount / currentLayers)
                val posInLayer = id % neuronsInLayer

                newNeurons.add(
                    Neuron(
                        id = id,
                        angle = (posInLayer * 360f / neuronsInLayer) + (layer * 15f),
                        radius = 80f + (layer * 60f),
                        orbitSpeed = 0.3f + (Random.nextFloat() * 0.4f) * (if (layer % 2 == 0) 1f else -1f),
                        pulsePhase = Random.nextFloat() * 360f,
                        baseSize = 1f + Random.nextFloat() * 1f,
                        layer = layer
                    )
                )
            }

            neurons.addAll(newNeurons)

            val batchSize = 10
            newNeurons.chunked(batchSize).forEach { batch ->
                batch.forEach { neuron ->
                    launch {
                        neuron.alpha.animateTo(1f, tween(200, easing = FastOutSlowInEasing))
                    }
                }
                delay(25)
            }
        }
    }

    return neurons
}