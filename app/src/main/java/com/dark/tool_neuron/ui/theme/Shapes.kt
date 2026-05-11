package com.dark.tool_neuron.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.capsule.ContinuousCapsule
import com.kyant.capsule.ContinuousRoundedRectangle
import com.kyant.capsule.continuities.G2Continuity
import com.kyant.capsule.continuities.G2ContinuityProfile
private val TnContinuity = G2Continuity(
    profile = G2ContinuityProfile.RoundedRectangle.copy(
        extendedFraction = 0.5,
        arcFraction = 0.5,
        bezierCurvatureScale = 1.1,
        arcCurvatureScale = 1.1
    ),
    capsuleProfile = G2ContinuityProfile.Capsule.copy(
        extendedFraction = 0.5,
        arcFraction = 0.25
    )
)

data class TnShapes(
    val sm: Shape,   // 6dp  — badges, small chips
    val md: Shape,   // 8dp  — input fields, toggles
    val lg: Shape,   // 12dp — cards, bottom sheets
    val xl: Shape,   // 16dp — dialogs, modals
    val xxl: Shape,  // 20dp — nav bars, large panels
    val full: Shape, // capsule — FABs, pills, chips

    val card: Shape,
    val cardSmall: Shape,
    val chip: Shape,
    val actionIcon: Shape,
)

private fun tnShape(radius: Dp) = ContinuousRoundedRectangle(radius, continuity = TnContinuity)

val DefaultTnShapes = TnShapes(
    sm = tnShape(6.dp),
    md = tnShape(8.dp),
    lg = tnShape(12.dp),
    xl = tnShape(16.dp),
    xxl = tnShape(20.dp),
    full = ContinuousCapsule(continuity = TnContinuity),

    card = tnShape(12.dp),
    cardSmall = tnShape(10.dp),
    chip = ContinuousCapsule(continuity = TnContinuity),
    actionIcon = tnShape(8.dp),
)

val LocalTnShapes = compositionLocalOf { DefaultTnShapes }