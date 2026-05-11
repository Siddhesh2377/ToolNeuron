package com.dark.tool_neuron.ui.screens.rag_debug

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dark.tool_neuron.repo.RagDebugHit
import com.dark.tool_neuron.repo.RagDebugResult
import com.dark.tool_neuron.ui.components.ActionButton
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.LocalTnShapes
import com.dark.tool_neuron.viewmodel.RagDebugViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RagDebugScreen(
    viewModel: RagDebugViewModel,
    onBack: () -> Unit,
) {
    val dimens = LocalDimens.current
    val query by viewModel.query.collectAsStateWithLifecycle()
    val selectedChatId by viewModel.selectedChatId.collectAsStateWithLifecycle()
    val result by viewModel.result.collectAsStateWithLifecycle()
    val running by viewModel.isRunning.collectAsStateWithLifecycle()
    val ready by viewModel.ragReady.collectAsStateWithLifecycle()
    val embedding by viewModel.embeddingName.collectAsStateWithLifecycle()

    val chats = remember(selectedChatId) { viewModel.chats() }
    val chatTitle = chats.firstOrNull { it.id == selectedChatId }?.title
        ?: chats.firstOrNull()?.title
        ?: "(no chat)"

    var menuOpen by remember { mutableStateOf(false) }
    var tab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("RAG Retrieval Debug") },
                navigationIcon = {
                    ActionButton(
                        onClickListener = onBack,
                        icon = TnIcons.ArrowLeft,
                        contentDescription = "Back",
                        modifier = Modifier.padding(start = dimens.screenPadding),
                    )
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                horizontal = dimens.screenPadding,
                vertical = dimens.spacingSm,
            ),
            verticalArrangement = Arrangement.spacedBy(dimens.spacingSm),
        ) {
            item("status") {
                StatusRow(ready = ready, embedding = embedding, chatTitle = chatTitle)
            }
            item("inputs") {
                Column(verticalArrangement = Arrangement.spacedBy(dimens.spacingXs)) {
                    Box {
                        TextButton(onClick = { menuOpen = true }) {
                            Text("Chat: $chatTitle")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            chats.forEach { chat ->
                                DropdownMenuItem(
                                    text = { Text(chat.title) },
                                    onClick = {
                                        viewModel.selectChat(chat.id)
                                        menuOpen = false
                                    },
                                )
                            }
                            if (chats.isEmpty()) {
                                DropdownMenuItem(text = { Text("No chats") }, onClick = { menuOpen = false }, enabled = false)
                            }
                        }
                    }
                    OutlinedTextField(
                        value = query,
                        onValueChange = viewModel::setQuery,
                        label = { Text("Query") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4,
                    )
                    Button(
                        onClick = { viewModel.run() },
                        enabled = !running && query.isNotBlank() && chats.isNotEmpty() && ready,
                    ) {
                        if (running) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Run query")
                        }
                    }
                }
            }
            item("tabs") {
                val current = result
                if (current != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(dimens.spacingXs)) {
                        TabChip("Fused (${current.fused.size})", selected = tab == 0) { tab = 0 }
                        TabChip("Dense (${current.dense.size})", selected = tab == 1) { tab = 1 }
                        TabChip("BM25 (${current.bm25.size})", selected = tab == 2) { tab = 2 }
                        TabChip("Context", selected = tab == 3) { tab = 3 }
                        TabChip("Engine", selected = tab == 4) { tab = 4 }
                    }
                }
            }
            val r = result
            if (r != null) {
                when (tab) {
                    0 -> hitItems(r.fused)
                    1 -> hitItems(r.dense)
                    2 -> hitItems(r.bm25)
                    3 -> item("context") { ContextBlock(r) }
                    4 -> item("engine") { EngineInfoBlock(r.engineInfo) }
                }
            } else {
                item("placeholder") {
                    Text(
                        text = if (!ready) "RAG engine is not ready. Install an embedding model and attach a document first."
                            else "Enter a query and tap Run to see retrieval internals.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.hitItems(hits: List<RagDebugHit>) {
    if (hits.isEmpty()) {
        item("empty") { EmptyState() }
        return
    }
    items(hits, key = { "${it.docId}|${it.chunkIndex}|${it.score}" }) { hit ->
        HitCard(hit)
    }
}

@Composable
private fun StatusRow(ready: Boolean, embedding: String?, chatTitle: String) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current
    Surface(
        shape = tnShapes.cardSmall,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(dimens.spacingSm),
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        if (ready) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error,
                        CircleShape,
                    ),
            )
            Text(
                text = if (ready) "Ready · ${embedding ?: "no model"}" else "Not ready",
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun TabChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
private fun HitCard(hit: RagDebugHit) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current
    Surface(
        shape = tnShapes.cardSmall,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(dimens.spacingSm)) {
            Row(horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm)) {
                Text(
                    text = "chunk ${hit.chunkIndex}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "score %.4f".format(hit.score),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = hit.docId,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                maxLines = 1,
            )
            SelectionContainer {
                Text(
                    text = hit.text.take(600),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Text(
        text = "(no results)",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
    )
}

@Composable
private fun ContextBlock(result: RagDebugResult) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current
    Surface(
        shape = tnShapes.cardSmall,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(dimens.spacingSm)) {
            Text(
                text = "~${result.approxContextTokens} tokens",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
            )
            SelectionContainer {
                Text(
                    text = result.contextBlock.ifBlank { "(no context — augment skipped)" },
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
private fun EngineInfoBlock(info: String) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current
    Surface(
        shape = tnShapes.cardSmall,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
    ) {
        Column(modifier = Modifier.padding(dimens.spacingSm)) {
            SelectionContainer {
                Text(
                    text = info.ifBlank { "{}" },
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}
