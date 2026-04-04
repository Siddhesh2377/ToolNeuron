package com.dark.tool_neuron.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class Dimens(
    val screenPadding: Dp,
    val cardPadding: Dp,
    val itemSpacing: Dp,
    val sectionSpacing: Dp,
    val iconSize: Dp,
    val avatarSize: Dp,
    val progressBarHeight: Dp,
    val progressBarWidth: Dp
)

val CompactDimens = Dimens(
    screenPadding    = 16.dp,
    cardPadding      = 12.dp,
    itemSpacing      = 8.dp,
    sectionSpacing   = 24.dp,
    iconSize         = 24.dp,
    avatarSize       = 40.dp,
    progressBarHeight = 20.dp,
    progressBarWidth = 8.dp
)

val MediumDimens = Dimens(
    screenPadding    = 24.dp,
    cardPadding      = 16.dp,
    itemSpacing      = 12.dp,
    sectionSpacing   = 32.dp,
    iconSize         = 24.dp,
    avatarSize       = 48.dp,
    progressBarHeight = 22.dp,
    progressBarWidth = 10.dp
)

val ExpandedDimens = Dimens(
    screenPadding    = 32.dp,
    cardPadding      = 24.dp,
    itemSpacing      = 16.dp,
    sectionSpacing   = 40.dp,
    iconSize         = 28.dp,
    avatarSize       = 56.dp,
    progressBarHeight = 24.dp,
    progressBarWidth = 12.dp
)

val LocalDimens = compositionLocalOf { CompactDimens }
