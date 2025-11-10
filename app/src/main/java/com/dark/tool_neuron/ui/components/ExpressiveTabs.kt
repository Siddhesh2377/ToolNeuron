package com.dark.tool_neuron.ui.components


import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.ui.theme.rDP

/**
 * Material 3 Expressive Tab Switch Component
 *
 * A modern, animated tab switcher with Material 3 expressive design principles
 *
 * @param selectedIndex Current selected tab index
 * @param options List of tab labels
 * @param onTabSelected Callback when a tab is selected
 * @param modifier Modifier for the component
 */
@Composable
fun M3TabSwitch(
    selectedIndex: Int,
    options: List<String>,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(rDP(12.dp)))
            .background(MaterialTheme.colorScheme.surface)
            .padding(rDP(6.dp))
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(rDP(6.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            options.forEachIndexed { index, label ->
                M3TabButton(
                    text = label,
                    isSelected = index == selectedIndex,
                    onClick = { onTabSelected(index) }
                )
            }
        }
    }
}
@Composable
private fun M3TabButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Smooth color transitions without spring
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected)
            MaterialTheme.colorScheme.primary
        else
            Color.Transparent,
        animationSpec = tween(
            durationMillis = 300,
            easing = FastOutSlowInEasing
        ),
        label = "backgroundColor"
    )

    val textColor by animateColorAsState(
        targetValue = if (isSelected)
            MaterialTheme.colorScheme.onPrimary
        else
            MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(
            durationMillis = 300,
            easing = FastOutSlowInEasing
        ),
        label = "textColor"
    )

    // Expressive scale animation
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.0f else 0.96f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "scale"
    )

    // Shadow elevation for depth
    val elevation by animateDpAsState(
        targetValue = if (isSelected) rDP(3.dp) else rDP(0.dp),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "elevation"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .shadow(
                elevation = elevation,
                shape = RoundedCornerShape(rDP(8.dp)),
                clip = false
            )
            .clip(RoundedCornerShape(rDP(8.dp)))
            .background(backgroundColor)
            .clickable(
                onClick = onClick,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            )
            .padding(horizontal = rDP(24.dp), vertical = rDP(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = textColor,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
            )
        )
    }
}