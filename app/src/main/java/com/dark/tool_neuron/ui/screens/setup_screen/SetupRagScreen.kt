package com.dark.tool_neuron.ui.screens.setup_screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.LocalTnShapes
import com.dark.tool_neuron.ui.theme.Motion
import com.dark.tool_neuron.viewmodel.SetupRagViewModel
import kotlinx.coroutines.delay

@Composable
fun SetupRagScreen(
    innerPadding: PaddingValues,
    viewModel: SetupRagViewModel = hiltViewModel(),
) {
    val dimens = LocalDimens.current
    val smartRerank by viewModel.smartRerank.collectAsStateWithLifecycle()
    val multiQuery by viewModel.multiQuery.collectAsStateWithLifecycle()
    val deepResearch by viewModel.deepResearch.collectAsStateWithLifecycle()

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(80); visible = true }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = dimens.screenPadding),
    ) {
        Spacer(Modifier.height(dimens.spacingXl))

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(Motion.entrance()) + slideInVertically(Motion.entrance()) { it / 4 },
        ) {
            Column {
                Icon(
                    imageVector = TnIcons.BookOpen,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(dimens.spacingLg))
                Text(
                    text = "Smart document search",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(dimens.spacingXs))
                Text(
                    text = "When you attach documents to a chat, ToolNeuron searches them to answer your questions. " +
                        "These switches make the search smarter — at the cost of being slower. Pick what fits you. " +
                        "You can change them anytime in Settings.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(dimens.spacingXl))

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(Motion.entrance()) + slideInVertically(Motion.entrance()) { it / 3 },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(dimens.spacingSm)) {
                RagToggleCard(
                    icon = TnIcons.Sparkles,
                    title = "Smart rerank",
                    subtitle = "Slower · Better for short questions",
                    explanation = "After finding chunks of your documents, the AI re-reads them and picks the most relevant " +
                        "ones before answering. Helps when the right chunk is buried in noise.",
                    checked = smartRerank,
                    onCheckedChange = viewModel::setSmartRerank,
                )
                RagToggleCard(
                    icon = TnIcons.Search,
                    title = "Thorough search",
                    subtitle = "Slower · Better for vague questions",
                    explanation = "Your question is rephrased 3 different ways and we search for each version. " +
                        "Catches answers that are worded differently from how you asked.",
                    checked = multiQuery,
                    onCheckedChange = viewModel::setMultiQuery,
                )
                RagToggleCard(
                    icon = TnIcons.Database,
                    title = "Deep research",
                    subtitle = "Slowest · Best for hard questions",
                    explanation = "If the AI's first answer attempt isn't enough, it can ask itself follow-up questions " +
                        "and search again. Up to 3 extra rounds. Best for multi-part or complex questions.",
                    checked = deepResearch,
                    onCheckedChange = viewModel::setDeepResearch,
                )
            }
        }

        Spacer(Modifier.height(dimens.spacingLg))

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(Motion.entrance()),
        ) {
            HelperFootnote()
        }

        Spacer(Modifier.height(dimens.spacingLg))
    }
}

@Composable
private fun RagToggleCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    explanation: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val tnShapes = LocalTnShapes.current
    val dimens = LocalDimens.current

    val borderColor by animateColorAsState(
        targetValue = if (checked) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outlineVariant,
        animationSpec = Motion.state(),
        label = "ragBorder-$title",
    )
    val containerColor by animateColorAsState(
        targetValue = if (checked) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        else MaterialTheme.colorScheme.surface,
        animationSpec = Motion.state(),
        label = "ragBg-$title",
    )

    Surface(
        onClick = { onCheckedChange(!checked) },
        modifier = Modifier.fillMaxWidth(),
        shape = tnShapes.card,
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Column(modifier = Modifier.padding(dimens.spacingMd)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(36.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (checked) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(Modifier.width(dimens.spacingSm))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    colors = SwitchDefaults.colors(),
                )
            }
            Spacer(Modifier.height(dimens.spacingXs))
            Text(
                text = explanation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
            )
        }
    }
}

@Composable
private fun HelperFootnote() {
    val tnShapes = LocalTnShapes.current
    val dimens = LocalDimens.current
    Surface(
        shape = tnShapes.card,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(dimens.spacingMd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = TnIcons.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(dimens.spacingSm))
            Text(
                text = "Don't worry — leaving everything off works fine. Turn things on if your answers feel off " +
                    "and you don't mind a slower response.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
