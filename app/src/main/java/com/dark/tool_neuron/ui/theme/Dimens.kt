package com.dark.tool_neuron.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class Dimens(
    // Screen / Layout
    val screenPadding: Dp,
    val cardPadding: Dp,
    val itemSpacing: Dp,
    val sectionSpacing: Dp,

    // Progress
    val progressBarHeight: Dp,
    val progressBarWidth: Dp,

    // Avatar
    val avatarSize: Dp,

    // Spacing scale
    val spacingXxs: Dp,
    val spacingXs: Dp,
    val spacingSm: Dp,
    val spacingMd: Dp,
    val spacingLg: Dp,
    val spacingXl: Dp,
    val spacingXxl: Dp,
    val spacingXxxl: Dp,

    // Icon sizes
    val iconSm: Dp,
    val iconMd: Dp,
    val iconLg: Dp,

    // Border radius
    val radiusSm: Dp,
    val radiusMd: Dp,
    val radiusLg: Dp,
    val radiusXl: Dp,
    val radiusXxl: Dp,
    val radiusFull: Dp,

    // Cards
    val cardCornerRadius: Dp,
    val cardSmallCornerRadius: Dp,
    val cardElevation: Dp,

    // Action icon
    val actionIconSize: Dp,
    val actionIconPadding: Dp,
    val actionIconRoundedSize: Dp,
    val actionIconSpace: Dp,

    // Component heights
    val switchRowHeight: Dp,
    val toggleGroupHeight: Dp,
    val badgeHeight: Dp,
    val sectionHeaderHeight: Dp,

    // Chips
    val chipHeight: Dp,
    val chipCornerRadius: Dp,
    val chipHorizontalPadding: Dp,
    val chipIconSize: Dp,
    val chipSpacing: Dp,

    // Timeline
    val timelineNodeSize: Dp,
    val timelineLineWidth: Dp,

    // Pin pad
    val padButtonSize: Dp,
    val padButtonGap: Dp,
    val padFontSize: Int,
)

// Compact (phones, small screens)
val CompactDimens = Dimens(
    screenPadding = 16.dp,
    cardPadding = 12.dp,
    itemSpacing = 8.dp,
    sectionSpacing = 24.dp,
    progressBarHeight = 20.dp,
    progressBarWidth = 8.dp,
    avatarSize = 40.dp,

    spacingXxs = 2.dp,
    spacingXs = 4.dp,
    spacingSm = 8.dp,
    spacingMd = 12.dp,
    spacingLg = 16.dp,
    spacingXl = 24.dp,
    spacingXxl = 32.dp,
    spacingXxxl = 48.dp,

    iconSm = 14.dp,
    iconMd = 18.dp,
    iconLg = 24.dp,

    radiusSm = 6.dp,
    radiusMd = 8.dp,
    radiusLg = 12.dp,
    radiusXl = 16.dp,
    radiusXxl = 20.dp,
    radiusFull = 100.dp,

    cardCornerRadius = 12.dp,
    cardSmallCornerRadius = 10.dp,
    cardElevation = 1.dp,

    actionIconSize = 30.dp,
    actionIconPadding = 6.dp,
    actionIconRoundedSize = 8.dp,
    actionIconSpace = 8.dp,

    switchRowHeight = 48.dp,
    toggleGroupHeight = 36.dp,
    badgeHeight = 22.dp,
    sectionHeaderHeight = 32.dp,

    chipHeight = 24.dp,
    chipCornerRadius = 12.dp,
    chipHorizontalPadding = 8.dp,
    chipIconSize = 14.dp,
    chipSpacing = 6.dp,

    timelineNodeSize = 8.dp,
    timelineLineWidth = 2.dp,

    padButtonSize = 60.dp,
    padButtonGap = 14.dp,
    padFontSize = 22,
)

// Medium (large phones, small tablets)
val MediumDimens = Dimens(
    screenPadding = 24.dp,
    cardPadding = 16.dp,
    itemSpacing = 12.dp,
    sectionSpacing = 32.dp,
    progressBarHeight = 22.dp,
    progressBarWidth = 10.dp,
    avatarSize = 48.dp,

    spacingXxs = 2.dp,
    spacingXs = 4.dp,
    spacingSm = 8.dp,
    spacingMd = 14.dp,
    spacingLg = 18.dp,
    spacingXl = 26.dp,
    spacingXxl = 34.dp,
    spacingXxxl = 52.dp,

    iconSm = 16.dp,
    iconMd = 20.dp,
    iconLg = 26.dp,

    radiusSm = 6.dp,
    radiusMd = 8.dp,
    radiusLg = 12.dp,
    radiusXl = 16.dp,
    radiusXxl = 20.dp,
    radiusFull = 100.dp,

    cardCornerRadius = 14.dp,
    cardSmallCornerRadius = 12.dp,
    cardElevation = 1.dp,

    actionIconSize = 34.dp,
    actionIconPadding = 7.dp,
    actionIconRoundedSize = 9.dp,
    actionIconSpace = 9.dp,

    switchRowHeight = 52.dp,
    toggleGroupHeight = 40.dp,
    badgeHeight = 24.dp,
    sectionHeaderHeight = 36.dp,

    chipHeight = 26.dp,
    chipCornerRadius = 13.dp,
    chipHorizontalPadding = 10.dp,
    chipIconSize = 15.dp,
    chipSpacing = 7.dp,

    timelineNodeSize = 9.dp,
    timelineLineWidth = 2.dp,

    padButtonSize = 68.dp,
    padButtonGap = 16.dp,
    padFontSize = 24,
)

// Expanded (tablets, foldables unfolded)
val ExpandedDimens = Dimens(
    screenPadding = 32.dp,
    cardPadding = 24.dp,
    itemSpacing = 16.dp,
    sectionSpacing = 40.dp,
    progressBarHeight = 24.dp,
    progressBarWidth = 12.dp,
    avatarSize = 56.dp,

    spacingXxs = 2.dp,
    spacingXs = 6.dp,
    spacingSm = 10.dp,
    spacingMd = 16.dp,
    spacingLg = 20.dp,
    spacingXl = 28.dp,
    spacingXxl = 36.dp,
    spacingXxxl = 56.dp,

    iconSm = 16.dp,
    iconMd = 22.dp,
    iconLg = 28.dp,

    radiusSm = 6.dp,
    radiusMd = 10.dp,
    radiusLg = 14.dp,
    radiusXl = 18.dp,
    radiusXxl = 22.dp,
    radiusFull = 100.dp,

    cardCornerRadius = 16.dp,
    cardSmallCornerRadius = 14.dp,
    cardElevation = 1.dp,

    actionIconSize = 36.dp,
    actionIconPadding = 8.dp,
    actionIconRoundedSize = 10.dp,
    actionIconSpace = 10.dp,

    switchRowHeight = 56.dp,
    toggleGroupHeight = 44.dp,
    badgeHeight = 26.dp,
    sectionHeaderHeight = 40.dp,

    chipHeight = 28.dp,
    chipCornerRadius = 14.dp,
    chipHorizontalPadding = 12.dp,
    chipIconSize = 16.dp,
    chipSpacing = 8.dp,

    timelineNodeSize = 10.dp,
    timelineLineWidth = 2.dp,

    padButtonSize = 76.dp,
    padButtonGap = 18.dp,
    padFontSize = 26,
)

val LocalDimens = compositionLocalOf { CompactDimens }