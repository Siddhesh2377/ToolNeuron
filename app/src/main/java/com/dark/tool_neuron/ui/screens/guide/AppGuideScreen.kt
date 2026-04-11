package com.dark.tool_neuron.ui.screens.guide

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.ui.components.ActionButton
import com.dark.tool_neuron.ui.components.ActionTextButton
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.LocalTnShapes
import kotlinx.coroutines.delay

private data class GuideSection(
    val title: String,
    val description: String,
    val visual: @Composable () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppGuideScreen(onClose: () -> Unit) {
    val dimens = LocalDimens.current
    val shapes = LocalTnShapes.current

    val sections = remember {
        listOf(
            GuideSection(
                title = "Download Models",
                description = "Browse the Store or search HuggingFace. Download models directly to your device.",
                visual = { MiniModelCard() }
            ),
            GuideSection(
                title = "Load & Chat",
                description = "Tap a model to load it. Type your message and the AI responds in real-time.",
                visual = { MiniChatBubbles() }
            ),
            GuideSection(
                title = "Model Store",
                description = "Filter by size, quantization, or category. Add custom HuggingFace repos.",
                visual = { MiniFilterChips() }
            ),
            GuideSection(
                title = "Voice Input",
                description = "Tap the mic to speak. On-device speech recognition, no internet needed.",
                visual = { MiniMicVisual() }
            ),
            GuideSection(
                title = "Privacy First",
                description = "Everything runs locally on your device. Your data never leaves your phone.",
                visual = { MiniPrivacyVisual() }
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "App Guide",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    ActionButton(
                        onClickListener = onClose,
                        icon = TnIcons.ArrowLeft,
                        contentDescription = "Close"
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                modifier = Modifier.navigationBarsPadding()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = dimens.screenPadding)
                        .padding(vertical = dimens.screenPadding),
                    contentAlignment = Alignment.Center
                ) {
                    ActionTextButton(
                        onClickListener = onClose,
                        icon = TnIcons.Check,
                        text = "Got it",
                        modifier = Modifier
                            .widthIn(max = 600.dp)
                            .fillMaxWidth()
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = dimens.screenPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(dimens.spacingMd)
            ) {
                item { Spacer(Modifier.height(dimens.spacingSm)) }

                itemsIndexed(sections) { index, section ->
                    var visible by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        delay(index * 80L)
                        visible = true
                    }

                    AnimatedVisibility(
                        visible = visible,
                        enter = fadeIn(tween(300)) + slideInVertically(
                            tween(350),
                            initialOffsetY = { it / 4 }
                        )
                    ) {
                        GuideCard(
                            section = section,
                            isEven = index % 2 == 1
                        )
                    }
                }

                item { Spacer(Modifier.height(dimens.spacingMd)) }
            }
        }
    }
}

@Composable
private fun GuideCard(section: GuideSection, isEven: Boolean) {
    val dimens = LocalDimens.current
    val shapes = LocalTnShapes.current

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = shapes.card
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimens.cardPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingMd)
        ) {
            if (isEven) {
                VisualSide(
                    visual = section.visual,
                    modifier = Modifier.weight(0.45f)
                )
                TextSide(
                    title = section.title,
                    description = section.description,
                    modifier = Modifier.weight(0.55f)
                )
            } else {
                TextSide(
                    title = section.title,
                    description = section.description,
                    modifier = Modifier.weight(0.55f)
                )
                VisualSide(
                    visual = section.visual,
                    modifier = Modifier.weight(0.45f)
                )
            }
        }
    }
}

@Composable
private fun TextSide(title: String, description: String, modifier: Modifier) {
    val dimens = LocalDimens.current
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(dimens.spacingXs))
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun VisualSide(visual: @Composable () -> Unit, modifier: Modifier) {
    val shapes = LocalTnShapes.current
    val dimens = LocalDimens.current
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f),
        shape = shapes.cardSmall
    ) {
        Box(
            modifier = Modifier.padding(dimens.spacingMd),
            contentAlignment = Alignment.Center
        ) {
            visual()
        }
    }
}

@Composable
private fun MiniModelCard() {
    val dimens = LocalDimens.current
    val shapes = LocalTnShapes.current

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = shapes.cardSmall
    ) {
        Column(modifier = Modifier.padding(dimens.spacingSm)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(dimens.spacingXs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "LLM",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                RoundedCornerShape(dimens.spacingXxs)
                            )
                            .padding(horizontal = dimens.spacingXs, vertical = dimens.spacingXxs)
                    )
                    Text(
                        text = "Qwen3 0.6B",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
                Icon(
                    TnIcons.Download,
                    contentDescription = null,
                    modifier = Modifier.size(dimens.iconSm),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                )
            }
            Spacer(Modifier.height(dimens.spacingXs))
            Text(
                text = "400 MB",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        RoundedCornerShape(dimens.spacingXxs)
                    )
                    .padding(horizontal = dimens.spacingXs, vertical = dimens.spacingXxs)
            )
        }
    }
}

@Composable
private fun MiniChatBubbles() {
    val dimens = LocalDimens.current
    val shapes = LocalTnShapes.current

    Column(
        verticalArrangement = Arrangement.spacedBy(dimens.spacingXs),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                shape = shapes.cardSmall
            ) {
                Text(
                    text = "Hello!",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(
                        horizontal = dimens.spacingSm,
                        vertical = dimens.spacingXs
                    )
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                shape = shapes.cardSmall
            ) {
                Text(
                    text = "Hi there! How can I help?",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(
                        horizontal = dimens.spacingSm,
                        vertical = dimens.spacingXs
                    )
                )
            }
        }
    }
}

@Composable
private fun MiniFilterChips() {
    val dimens = LocalDimens.current
    val shapes = LocalTnShapes.current
    val labels = listOf("Q4", "Q8", "Small")

    Row(
        horizontalArrangement = Arrangement.spacedBy(dimens.spacingXs),
        modifier = Modifier.fillMaxWidth()
    ) {
        labels.forEach { label ->
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                shape = shapes.full
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(
                        horizontal = dimens.spacingSm,
                        vertical = dimens.spacingXxs
                    )
                )
            }
        }
    }
}

@Composable
private fun MiniMicVisual() {
    val dimens = LocalDimens.current
    val waveColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(dimens.iconLg)
                .drawBehind {
                    val cx = size.width / 2
                    val cy = size.height / 2
                    val r1 = size.width / 2 + dimens.spacingSm.toPx()
                    val r2 = size.width / 2 + dimens.spacingMd.toPx()
                    drawArc(
                        color = waveColor,
                        startAngle = -60f,
                        sweepAngle = 120f,
                        useCenter = false,
                        topLeft = Offset(cx - r1, cy - r1),
                        size = Size(r1 * 2, r1 * 2),
                        style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = waveColor.copy(alpha = 0.15f),
                        startAngle = -60f,
                        sweepAngle = 120f,
                        useCenter = false,
                        topLeft = Offset(cx - r2, cy - r2),
                        size = Size(r2 * 2, r2 * 2),
                        style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                TnIcons.Mic,
                contentDescription = null,
                modifier = Modifier.size(dimens.iconMd),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun MiniPrivacyVisual() {
    val dimens = LocalDimens.current
    val shapes = LocalTnShapes.current

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            TnIcons.ShieldCheck,
            contentDescription = null,
            modifier = Modifier.size(dimens.iconLg),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(dimens.spacingSm))
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            shape = shapes.full
        ) {
            Text(
                text = "On-Device",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(
                    horizontal = dimens.spacingSm,
                    vertical = dimens.spacingXxs
                )
            )
        }
    }
}
