package com.dark.tool_neuron.ui.screens.guide

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.LocalTnShapes

@Composable
fun GuideVlmScreen(innerPadding: PaddingValues) {
    GuideDetailLayout(
        innerPadding = innerPadding,
        icon = TnIcons.Eye,
        lede = "Attach an image and ask about it. Vision-capable repos ship a base LLM plus a sibling mmproj projector. The Store downloads both as one job into a per-repo folder, and loading the base model auto-attaches the projector.",
        steps = listOf(
            GuideStep(
                title = "Find a VLM repo in the Store",
                body = "Repos that include an mmproj file are detected automatically and tagged VLM in the catalog. Examples: LLaVA, MiniCPM-V, Qwen2-VL, Gemma3 Vision, LFM2-VL, SmolVLM.",
                visual = { VlmModelCardVisual() },
            ),
            GuideStep(
                title = "Download — both files, one job",
                body = "Tap download on a VLM card. The app pulls the base quant AND the colocated mmproj into models/vlm/<repo>/ as a single Store progress entry. There is no separate \"projector\" tile to tap.",
                visual = { FolderLayoutVisual() },
            ),
            GuideStep(
                title = "Load the model — projector auto-attaches",
                body = "Select the VLM model like any chat model. On load success, the colocated mmproj attaches automatically — no manual projector UI anywhere in the app. The Plus → Attach image tile flips on to confirm.",
                visual = { ProjectorVisual() },
            ),
            GuideStep(
                title = "Attach an image and ask",
                body = "Plus → Attach image, pick a photo, type your question — \"what's in this image?\", \"read the text\", \"identify the chart\". Send as normal. The button is disabled until a VLM is loaded.",
                visual = { AttachImagePlusVisual() },
            ),
            GuideStep(
                title = "Ask — the marker is added for you",
                body = "The app prepends the model's image-marker token (e.g. <image>) to your last user message before inference. You don't have to write it.",
                visual = { AskVisual() },
            ),
            GuideStep(
                title = "Unload releases everything",
                body = "Unloading the VLM base model also releases the projector — no separate step. Switching to a non-VLM model does the same. The Attach image tile dims back out.",
                visual = { ReleaseButtonVisual() },
            ),
        ),
        tips = listOf(
            "Base and projector live side-by-side in models/vlm/<repo>/. Deleting the folder removes both.",
            "Images cross IPC via ParcelFileDescriptors, so even multi-MB photos work without hitting the 1 MB binder limit.",
            "The Attach image tile shows a \"VLM\" badge when the projector is live.",
            "Legacy flat models/ downloads still work for chat — they just won't auto-load a projector.",
        ),
    )
}

@Composable
private fun VlmModelCardVisual() {
    val dimens = LocalDimens.current
    val shapes = LocalTnShapes.current
    Surface(
        shape = shapes.card,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(dimens.spacingMd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingMd),
        ) {
            Surface(
                shape = shapes.full,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                modifier = Modifier.size(36.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = TnIcons.Eye,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(dimens.spacingXs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "MiniCPM-V",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Surface(
                    shape = shapes.full,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                ) {
                    Text(
                        text = "vision",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(
                            horizontal = dimens.spacingSm,
                            vertical = dimens.spacingXxs,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun ProjectorVisual() {
    val dimens = LocalDimens.current
    val shapes = LocalTnShapes.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
    ) {
        Surface(
            shape = shapes.cardSmall,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = dimens.spacingSm,
                    vertical = dimens.spacingXs,
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.spacingXs),
            ) {
                Icon(
                    imageVector = TnIcons.Eye,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "VLM on",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Text(
            text = "projector loaded",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FolderLayoutVisual() {
    val dimens = LocalDimens.current
    val shapes = LocalTnShapes.current
    Surface(
        shape = shapes.card,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(dimens.spacingMd),
            verticalArrangement = Arrangement.spacedBy(dimens.spacingXs),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.spacingXs),
            ) {
                Icon(
                    imageVector = TnIcons.Package,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = "models/vlm/<repo>/",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            listOf("base.gguf", "mmproj.gguf").forEach { name ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
                    modifier = Modifier.padding(start = dimens.spacingMd),
                ) {
                    Icon(
                        imageVector = TnIcons.CircleCheck,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun AttachImagePlusVisual() {
    val dimens = LocalDimens.current
    val shapes = LocalTnShapes.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = shapes.full,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
            modifier = Modifier.size(36.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = TnIcons.Plus,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Icon(
            imageVector = TnIcons.ArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
        Surface(
            shape = shapes.cardSmall,
            color = MaterialTheme.colorScheme.primary,
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = dimens.spacingSm,
                    vertical = dimens.spacingXs,
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.spacingXs),
            ) {
                Icon(
                    imageVector = TnIcons.Photo,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = "Attach image",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

@Composable
private fun AskVisual() {
    val dimens = LocalDimens.current
    val shapes = LocalTnShapes.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Surface(
            shape = shapes.cardSmall,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
            modifier = Modifier.size(44.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = TnIcons.Photo,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Surface(
            shape = shapes.cardSmall,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Text(
                text = "What's in this picture?",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(
                    horizontal = dimens.spacingSm,
                    vertical = dimens.spacingXs,
                ),
            )
        }
    }
}

@Composable
private fun ReleaseButtonVisual() {
    val dimens = LocalDimens.current
    val shapes = LocalTnShapes.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = shapes.full,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = dimens.spacingSm,
                    vertical = dimens.spacingXs,
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.spacingXs),
            ) {
                Icon(
                    imageVector = TnIcons.X,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = "Unload model",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
