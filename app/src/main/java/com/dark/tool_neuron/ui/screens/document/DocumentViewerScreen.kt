package com.dark.tool_neuron.ui.screens.document

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dark.tool_neuron.model.DocSection
import com.dark.tool_neuron.model.DocSource
import com.dark.tool_neuron.model.IterationLogEntry
import com.dark.tool_neuron.model.ResearchDocument
import com.dark.tool_neuron.ui.components.markdown.MarkdownText
import com.dark.tool_neuron.ui.components.markdown.MarkdownTypographies
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.LocalTnShapes
import com.dark.tool_neuron.viewmodel.DocumentViewerViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.platform.LocalLocale

@Composable
fun DocumentViewerScreen(
    docId: String,
    innerPadding: PaddingValues,
    viewModel: DocumentViewerViewModel = hiltViewModel(),
) {
    val dimens = LocalDimens.current
    val document by viewModel.document.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()

    LaunchedEffect(docId) { viewModel.load(docId) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
    ) {
        when {
            loading -> CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary,
            )
            document == null -> Text(
                "Document not found.",
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> DocumentBody(document!!, dimens.spacingLg)
        }
    }
}

@Composable
private fun DocumentBody(doc: ResearchDocument, horizontalPadding: androidx.compose.ui.unit.Dp) {
    val dimens = LocalDimens.current
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = horizontalPadding),
        contentPadding = PaddingValues(vertical = dimens.spacingLg),
        verticalArrangement = Arrangement.spacedBy(dimens.spacingMd),
    ) {
        item {
            Text(
                text = doc.title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
        }
        item {
            Text(
                text = "Question: ${doc.question}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (doc.structured.summary.isNotBlank()) {
            item {
                Surface(
                    shape = LocalTnShapes.current.lg,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Log.i("LOG", doc.structured.summary)
                    MarkdownText(
                        text = doc.structured.summary,
                        modifier = Modifier.padding(dimens.spacingMd),
                        typography = MarkdownTypographies.Document,
                    )
                }
            }
        }

        items(items = doc.structured.sections, key = { it.heading + it.body.hashCode() }) { section ->
            SectionBlock(section)
        }

        if (doc.structured.sources.isNotEmpty()) {
            item { SectionHeader(TnIcons.Globe, "Sources") }
            items(items = doc.structured.sources, key = { it.url + it.iteration }) { src ->
                SourceRow(src)
            }
        }

        if (doc.structured.iterationLog.isNotEmpty()) {
            item { SectionHeader(TnIcons.MessageCircle, "Generated questions") }
            items(items = doc.structured.iterationLog, key = { it.iteration }) { entry ->
                IterationBlock(entry)
            }
        }

        item { Footer(doc) }
    }
}

@Composable
private fun SectionBlock(section: DocSection) {
    Column {
        if (section.heading.isNotBlank()) {
            Text(
                text = section.heading,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.size(4.dp))
        }
        Log.i("LOG", section.body)
        MarkdownText(text = section.body, typography = MarkdownTypographies.Document)
    }
}

@Composable
private fun SectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
) {
    val dimens = LocalDimens.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.spacingXs),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(dimens.iconSm),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SourceRow(src: DocSource) {
    val dimens = LocalDimens.current
    Column {
        Text(
            text = src.title.ifBlank { src.url },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = "[iter ${src.iteration}] ${src.url}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(dimens.spacingXs))
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
    }
}

@Composable
private fun IterationBlock(entry: IterationLogEntry) {
    val dimens = LocalDimens.current
    Column {
        Text(
            text = "Iteration ${entry.iteration}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.size(dimens.spacingXxs))
        if (entry.questions.isEmpty()) {
            Text(
                "(no follow-ups)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            entry.questions.forEach { q ->
                Text(
                    "• $q",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun Footer(doc: ResearchDocument) {
    val dimens = LocalDimens.current
    val ts = SimpleDateFormat("yyyy-MM-dd HH:mm", LocalLocale.current.platformLocale).format(Date(doc.createdAt))
    val secs = doc.durationMs / 1000
    Surface(
        shape = LocalTnShapes.current.lg,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(dimens.spacingMd)) {
            FooterRow("Generated", ts)
            FooterRow("Duration", "${secs}s")
            FooterRow("Iterations", "${doc.iterationsUsed}")
            FooterRow("Sources fetched", "${doc.structured.sources.size}")
            FooterRow("Total bytes", "${doc.structured.totalFetchedBytes / 1024} KB")
            FooterRow("Model", doc.structured.modelName.ifBlank { doc.modelId })
        }
    }
}

@Composable
private fun FooterRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Spacer(Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
        )
    }
}
