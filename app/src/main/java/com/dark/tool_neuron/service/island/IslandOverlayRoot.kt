package com.dark.tool_neuron.service.island

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.LocalMaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.LocalTnShapes
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun IslandSurface(
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val view = LocalView.current
    val motion = LocalMaterialTheme.current.motionScheme

    var mode by remember { mutableStateOf(IslandMode.ASSISTANT) }
    var pressed by remember { mutableStateOf(false) }

    val transition = updateTransition(expanded, label = "island-morph")
    val progress by transition.animateFloat(
        transitionSpec = {
            tween(
                durationMillis = MORPH_DURATION_MS,
                easing = MORPH_EASING,
            )
        },
        label = "progress",
    ) { if (it) 1f else 0f }

    val width = lerp(IslandGeometry.PILL_W_DP, IslandGeometry.CARD_W_DP, progress)
    val height = lerp(IslandGeometry.PILL_H_DP, IslandGeometry.CARD_H_DP, progress)
    val cornerRadius = lerp(
        IslandGeometry.PILL_H_DP / 2f,
        IslandGeometry.CARD_CORNER_DP,
        progress,
    )
    val pillAlpha = (1f - progress * 2f).coerceIn(0f, 1f)
    val cardAlpha = ((progress - 0.5f) * 2f).coerceIn(0f, 1f)

    val pressScale by animateFloatAsState(
        targetValue = if (pressed) IslandGeometry.PRESS_SCALE else 1f,
        animationSpec = motion.fastSpatialSpec(),
        label = "press-scale",
    )

    val shapeCache = remember {
        val min = (IslandGeometry.PILL_H_DP / 2f).roundToInt()
        val max = IslandGeometry.CARD_CORNER_DP.roundToInt()
        Array<Shape>(max - min + 1) { i -> islandShape((min + i).dp) }
    }
    val minCorner = (IslandGeometry.PILL_H_DP / 2f).roundToInt()
    val shape = shapeCache[(cornerRadius.roundToInt() - minCorner)
        .coerceIn(0, shapeCache.size - 1)]

    SideEffect {
        IslandPositionStore.setMorphProgress(progress)
    }

    Box(modifier = Modifier.padding(IslandGeometry.OUTER_PADDING_DP.dp)) {
        Surface(
            color = Color.Black,
            contentColor = Color.White,
            shape = shape,
            modifier = Modifier
                .size(width.dp, height.dp)
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                }
                .clip(shape)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            pressed = true
                            try { tryAwaitRelease() } finally { pressed = false }
                        },
                        onTap = {
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            onToggle()
                        },
                        onLongPress = {
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            onToggle()
                        },
                    )
                }
                .pointerInput(Unit) {
                    var accum = 0f
                    val thresholdPx = IslandGeometry.SWIPE_THRESHOLD_DP.dp.toPx()
                    detectHorizontalDragGestures(
                        onDragEnd = { accum = 0f },
                        onDragCancel = { accum = 0f },
                    ) { _, dragAmount ->
                        accum += dragAmount
                        when {
                            accum < -thresholdPx && mode == IslandMode.ASSISTANT -> {
                                mode = IslandMode.CONTROL
                                view.performHapticFeedback(HapticFeedbackConstants.GESTURE_END)
                                accum = 0f
                            }
                            accum > thresholdPx && mode == IslandMode.CONTROL -> {
                                mode = IslandMode.ASSISTANT
                                view.performHapticFeedback(HapticFeedbackConstants.GESTURE_END)
                                accum = 0f
                            }
                        }
                    }
                },
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (pillAlpha > 0.01f) {
                    PillModeBadge(
                        mode = mode,
                        modifier = Modifier.graphicsLayer { alpha = pillAlpha },
                    )
                }
                if (cardAlpha > 0.01f) {
                    IslandCardContent(
                        mode = mode,
                        modifier = Modifier.graphicsLayer { alpha = cardAlpha },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PillModeBadge(
    mode: IslandMode,
    modifier: Modifier = Modifier,
) {
    val motion = LocalMaterialTheme.current.motionScheme
    val dimens = LocalDimens.current
    AnimatedContent(
        targetState = mode,
        transitionSpec = {
            val forward = targetState.ordinal > initialState.ordinal
            slideInHorizontally(motion.defaultSpatialSpec<IntOffset>()) { w ->
                if (forward) w else -w
            } + fadeIn(motion.fastEffectsSpec<Float>()) togetherWith
                slideOutHorizontally(motion.defaultSpatialSpec<IntOffset>()) { w ->
                    if (forward) -w else w
                } + fadeOut(motion.fastEffectsSpec<Float>())
        },
        modifier = modifier,
        label = "pill-mode",
    ) { current ->
        Icon(
            imageVector = current.glyph,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(dimens.iconSm),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun IslandCardContent(
    mode: IslandMode,
    modifier: Modifier = Modifier,
) {
    val motion = LocalMaterialTheme.current.motionScheme
    AnimatedContent(
        targetState = mode,
        transitionSpec = {
            val forward = targetState.ordinal > initialState.ordinal
            slideInHorizontally(motion.defaultSpatialSpec<IntOffset>()) { w ->
                if (forward) w else -w
            } + fadeIn(motion.fastEffectsSpec<Float>()) togetherWith
                slideOutHorizontally(motion.defaultSpatialSpec<IntOffset>()) { w ->
                    if (forward) -w else w
                } + fadeOut(motion.fastEffectsSpec<Float>())
        },
        modifier = modifier.fillMaxSize(),
        label = "mode-swap",
    ) { current ->
        IslandModeLayout(
            glyph = current.glyph,
            title = current.title,
            actions = current.actions,
        )
    }
}

@Composable
private fun IslandModeLayout(
    glyph: ImageVector,
    title: String,
    actions: List<ImageVector>,
) {
    val dimens = LocalDimens.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(dimens.spacingLg),
        verticalArrangement = Arrangement.spacedBy(dimens.spacingSm, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = glyph,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(dimens.iconLg),
        )
        Text(
            text = title,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(dimens.spacingMd)) {
            actions.forEach { IslandActionButton(icon = it) }
        }
    }
}

@Composable
private fun IslandActionButton(icon: ImageVector) {
    val view = LocalView.current
    val dimens = LocalDimens.current
    val shape = LocalTnShapes.current.full
    Box(
        modifier = Modifier
            .size(dimens.actionIconSize)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.12f))
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(dimens.iconMd),
        )
    }
}

private val IslandMode.glyph: ImageVector
    get() = when (this) {
        IslandMode.ASSISTANT -> TnIcons.Sparkles
        IslandMode.CONTROL -> TnIcons.Sliders
    }

private val IslandMode.title: String
    get() = when (this) {
        IslandMode.ASSISTANT -> "Assistant"
        IslandMode.CONTROL -> "Controls"
    }

private val IslandMode.actions: List<ImageVector>
    get() = when (this) {
        IslandMode.ASSISTANT -> AssistantActions
        IslandMode.CONTROL -> ControlActions
    }

private val AssistantActions = listOf(TnIcons.Mic, TnIcons.Send)
private val ControlActions = listOf(TnIcons.Volume, TnIcons.Settings)

private const val MORPH_DURATION_MS = 420
private val MORPH_EASING: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
