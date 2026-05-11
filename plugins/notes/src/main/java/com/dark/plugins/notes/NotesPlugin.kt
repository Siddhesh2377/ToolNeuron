package com.dark.plugins.notes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.MaterialTheme as M3
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dark.plugin_api.Plugin
import com.dark.plugin_api.PluginContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class NotesPlugin : Plugin {

    private lateinit var ctx: PluginContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val json = Json { ignoreUnknownKeys = true }
    private val notes = mutableStateListOf<Note>()
    private var loaded by mutableStateOf(false)

    override fun onLoad(context: PluginContext) {
        ctx = context
        scope.launch {
            val items = withContext(Dispatchers.IO) {
                val keys = runCatching { ctx.hxs.list(prefix = "note:") }.getOrDefault(emptyList())
                keys.mapNotNull { key ->
                    runCatching {
                        val bytes = ctx.hxs.get(key) ?: return@runCatching null
                        json.decodeFromString(Note.serializer(), bytes.decodeToString())
                    }.getOrNull()
                }.sortedByDescending { it.createdAt }
            }
            notes.clear()
            notes.addAll(items)
            loaded = true
        }
    }

    override fun onStart() {}
    override fun onPause() {}
    override fun onUnload() {
        scope.cancel()
    }

    @Composable
    override fun Content() {
        NotesTheme {
            NotesUi(
                notes = notes,
                loaded = loaded,
                onAdd = { title, body -> addNote(title, body) },
                onDelete = { id -> deleteNote(id) },
            )
        }
    }

    private fun addNote(title: String, body: String) {
        val cleanTitle = title.trim().ifEmpty { return }
        val cleanBody = body.trim()
        val note = Note(
            id = "n${System.currentTimeMillis()}",
            title = cleanTitle,
            body = cleanBody,
            createdAt = System.currentTimeMillis(),
        )
        notes.add(0, note)
        scope.launch(Dispatchers.IO) {
            val payload = json.encodeToString(Note.serializer(), note).encodeToByteArray()
            ctx.hxs.put("note:${note.id}", payload)
        }
    }

    private fun deleteNote(id: String) {
        notes.removeAll { it.id == id }
        scope.launch(Dispatchers.IO) {
            ctx.hxs.delete("note:$id")
        }
    }

    @Serializable
    data class Note(
        val id: String,
        val title: String,
        val body: String,
        val createdAt: Long,
    )
}

@Composable
private fun NotesTheme(content: @Composable () -> Unit) {
    val scheme = lightColorScheme(
        primary = Color(0xFFFFA726),
        onPrimary = Color(0xFF1B1300),
        primaryContainer = Color(0xFFFFE5B4),
        onPrimaryContainer = Color(0xFF5C3A00),
        secondary = Color(0xFFB07A4A),
        secondaryContainer = Color(0xFFF7E2C9),
        tertiary = Color(0xFFCB6A2A),
        tertiaryContainer = Color(0xFFFFE0C7),
        background = Color(0xFFFFFBF5),
        surface = Color(0xFFFFFBF5),
        surfaceContainer = Color(0xFFFFF1DC),
        surfaceContainerHigh = Color(0xFFFFE8C6),
        surfaceContainerHighest = Color(0xFFFFDDA8),
        onSurface = Color(0xFF1F1B14),
        onSurfaceVariant = Color(0xFF6B5C46),
        outline = Color(0xFFB39B7A),
        outlineVariant = Color(0xFFE8D2AE),
        error = Color(0xFFB3261E),
        onError = Color.White,
        errorContainer = Color(0xFFF9DEDC),
        onErrorContainer = Color(0xFF410E0B),
    )
    M3(colorScheme = scheme, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotesUi(
    notes: List<NotesPlugin.Note>,
    loaded: Boolean,
    onAdd: (title: String, body: String) -> Unit,
    onDelete: (id: String) -> Unit,
) {
    var showAdd by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Notes", style = M3.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${notes.size} saved",
                            style = M3.typography.labelSmall,
                            color = M3.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = M3.colorScheme.surface,
                ),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                containerColor = M3.colorScheme.primary,
                contentColor = M3.colorScheme.onPrimary,
                shape = RoundedCornerShape(20.dp),
            ) {
                Text("+ New note", fontWeight = FontWeight.SemiBold)
            }
        },
        containerColor = M3.colorScheme.background,
    ) { inner ->
        when {
            !loaded -> LoadingState(inner)
            notes.isEmpty() -> EmptyState(inner)
            else -> NotesList(
                notes = notes,
                onDelete = onDelete,
                innerPadding = inner,
            )
        }
    }

    if (showAdd) {
        AddNoteDialog(
            onDismiss = { showAdd = false },
            onSave = { title, body ->
                onAdd(title, body)
                showAdd = false
            },
        )
    }
}

@Composable
private fun LoadingState(inner: PaddingValues) {
    Box(
        modifier = Modifier.fillMaxSize().padding(inner),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "Loading…",
            color = M3.colorScheme.onSurfaceVariant,
            style = M3.typography.bodyMedium,
        )
    }
}

@Composable
private fun EmptyState(inner: PaddingValues) {
    Column(
        modifier = Modifier.fillMaxSize().padding(inner).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(M3.colorScheme.primaryContainer, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text("📓", fontSize = 36.sp)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "No notes yet",
            style = M3.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = M3.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Tap the + button to write your first note.",
            style = M3.typography.bodyMedium,
            color = M3.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NotesList(
    notes: List<NotesPlugin.Note>,
    onDelete: (String) -> Unit,
    innerPadding: PaddingValues,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(notes, key = { it.id }) { note ->
            AnimatedVisibility(
                visible = true,
                enter = fadeIn() + scaleIn(initialScale = 0.96f),
                exit = fadeOut() + scaleOut(targetScale = 0.96f),
            ) {
                NoteCard(note = note, onDelete = { onDelete(note.id) })
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun NoteCard(note: NotesPlugin.Note, onDelete: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = M3.colorScheme.surfaceContainer,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(M3.colorScheme.primary, CircleShape),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = note.title,
                    style = M3.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = M3.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (note.body.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = note.body,
                        style = M3.typography.bodyMedium,
                        color = M3.colorScheme.onSurfaceVariant,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Text("✕", color = M3.colorScheme.error, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun AddNoteDialog(onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New note", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = M3.colorScheme.surfaceContainerHigh,
                ) {
                    BasicTextField(
                        value = title,
                        onValueChange = { title = it },
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        textStyle = TextStyle(
                            color = M3.colorScheme.onSurface,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        decorationBox = { inner ->
                            if (title.isEmpty()) Text(
                                "Title",
                                color = M3.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold,
                            )
                            inner()
                        },
                    )
                }
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = M3.colorScheme.surfaceContainerHigh,
                ) {
                    BasicTextField(
                        value = body,
                        onValueChange = { body = it },
                        modifier = Modifier.fillMaxWidth().padding(12.dp).height(120.dp),
                        textStyle = TextStyle(color = M3.colorScheme.onSurface, fontSize = 14.sp),
                        decorationBox = { inner ->
                            if (body.isEmpty()) Text(
                                "Body (optional)",
                                color = M3.colorScheme.onSurfaceVariant,
                            )
                            inner()
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(title, body) }) {
                Text("Save", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        containerColor = M3.colorScheme.surfaceContainer,
    )
}
