package com.dark.neuroverse.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dark.neuroverse.activity.MainActivity
import com.dark.neuroverse.ui.theme.SkyBlue
import com.dark.neuroverse.viewModel.PluginStoreScreenViewModel
import com.dark.plugins.model.PluginLocalDB
import com.dark.plugins.model.PluginManifest
import com.dark.plugins.model.Tools
import com.dark.plugins.ui.theme.rDP
import com.dark.plugins.worker.PluginManifestWorker
import com.google.gson.GsonBuilder
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginStoreScreen(
    viewModel: PluginStoreScreenViewModel = viewModel()
) {
    val context = LocalContext.current

    val installed by viewModel.installedPlugins.collectAsStateWithLifecycle(emptyList())
    val running by viewModel.runningPlugins.collectAsStateWithLifecycle(emptyList())
    val current by viewModel.currentPlugin.collectAsStateWithLifecycle(null)

    val runningNames by remember(running) {
        derivedStateOf { running.mapNotNull { it.api?.getPluginInfo()?.name }.toSet() }
    }
    val currentName by remember(current) {
        derivedStateOf { current?.api?.getPluginInfo()?.name }
    }

    val addLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(), onResult = { uri ->
            if (uri != null) {
                viewModel.addPluginFromUri(context, uri)
                Toast.makeText(context, "Installing plugin…", Toast.LENGTH_SHORT).show()
            }
        })

    Scaffold(topBar = {
        TopAppBar(
            title = {
            Text(
                "Plugin Store",
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Serif)
            )
        }, actions = {
            Button (onClick = {
                val intent = Intent(context, MainActivity::class.java).apply {
                    putExtra("nav", true)
                }
                context.startActivity(intent)
            }, modifier = Modifier.padding(end = rDP(12.dp)), colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                contentColor = MaterialTheme.colorScheme.primary
            )) {
                Icon(Icons.Outlined.Home, "Home")
                Spacer(Modifier.width(rDP(3.dp)))
                Text("Home")
            }
        }, scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
        )
    }, floatingActionButton = {
        FloatingActionButton(
            onClick = {
                addLauncher.launch(
                    arrayOf(
                        "application/zip",
                        "application/java-archive",
                        "application/vnd.android.package-archive",
                        "*/*"
                    )
                )
            },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) { Icon(Icons.Default.Add, contentDescription = "Add plugin from file") }
    }) { inner ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
        ) {
            if (installed.isEmpty()) {
                EmptyState(onSeed = { addLauncher.launch(arrayOf("*/*")) })
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(rDP(16.dp)),
                    verticalArrangement = Arrangement.spacedBy(rDP(12.dp))
                ) {
                    items(installed, key = { it.pluginPath }) { plugin ->
                        val isRunning = runningNames.contains(plugin.pluginName)
                        val isCurrent = currentName == plugin.pluginName
                        PluginCard(
                            plugin = plugin,
                            isRunning = isRunning,
                            isCurrent = isCurrent,
                            onDelete = {
                                val ok = viewModel.deletePlugin(plugin.pluginName)
                                val msg =
                                    if (ok) "Deleted ${plugin.pluginName}" else "Failed to delete ${plugin.pluginName}"
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            })
                    }
                    item { Spacer(Modifier.height(rDP(56.dp))) }
                }

            }
        }
    }
}

@Composable
private fun PluginCard(
    plugin: PluginLocalDB, isRunning: Boolean, isCurrent: Boolean, onDelete: () -> Unit
) {
    val colors = MaterialTheme.colorScheme

    // Parse manifest if present on the row
    val manifest: PluginManifest? = remember(plugin) {
        plugin.manifestJsonOrNull()?.let { raw ->
            runCatching { PluginManifestWorker(raw).getPluginManifest() }.getOrNull()
        }
    }

    var showAdvanced by remember { mutableStateOf(false) }
    val chevron by animateFloatAsState(
        targetValue = if (showAdvanced) 180f else 0f,
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "chevron"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(250))
            .clip(RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = rDP(2.dp))
    ) {
        Column(modifier = Modifier.padding(rDP(14.dp))) {
            // Header row: name + badges + delete
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(text = manifest?.name?.ifBlank { plugin.pluginName } ?: plugin.pluginName,
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Serif),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        manifest?.let { m ->
                            m.metaData.pluginApi.takeIf { it.isNotBlank() }?.let { InfoChip(it) }
                            val author = m.authorText()
                            if (author.isNotBlank()) InfoChip("by $author")
                        }
                    }
                }
                if (isCurrent) {
                    AssistChip(
                        onClick = {},
                        label = { Text("Current") },
                        leadingIcon = { Icon(Icons.Outlined.Star, contentDescription = null) })
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete, contentDescription = "Delete", tint = colors.error
                    )
                }
            }

            Spacer(Modifier.height(rDP(8.dp)))

            // Primary info (structured)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                InfoLine(
                    "Status",
                    (if (isRunning) "Running" else "Stopped") + (if (isCurrent) " · Current" else "")
                )
                InfoLine("Description", manifest?.description?.takeIf { it.isNotBlank() } ?: "—")
                InfoLine("Main class", manifest?.mainClass?.takeIf { it.isNotBlank() } ?: "—")
            }

            Spacer(Modifier.height(rDP(8.dp)))

            // Actions Row with animated Run/Stop
            Row(horizontalArrangement = Arrangement.spacedBy(rDP(8.dp))) {
                TextButton(onClick = { showAdvanced = !showAdvanced }) {
                    Icon(
                        Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.rotate(chevron)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (showAdvanced) "Hide advanced" else "Advanced info")
                }
            }

            // Advanced section
            AnimatedVisibility(
                visible = showAdvanced,
                enter = slideInVertically(initialOffsetY = { it / 4 }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it / 4 }) + fadeOut()
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        InfoLine("Installed version", plugin.pluginVersion)
                        manifest?.version?.takeIf { it.isNotBlank() }
                            ?.let { InfoLine("Manifest version", it) }
                        InfoLine("Plugin path", plugin.pluginPath)
                        manifest?.metaData?.pluginApi?.takeIf { it.isNotBlank() }
                            ?.let { InfoLine("API", it) }
                        manifest?.metaData?.role?.takeIf { it.isNotBlank() }
                            ?.let { InfoLine("Role", it) }
                        manifest?.authorText()?.takeIf { it.isNotBlank() }
                            ?.let { InfoLine("Author", it) }
                    }

                    // Tools list
                    if (!manifest?.tools.isNullOrEmpty()) {
                        Column {
                            Text(
                                "Tools",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Spacer(Modifier.height(6.dp))
                            manifest.tools.forEach { tool -> ToolCard(tool) }
                        }
                    } else {
                        InfoLine("Tools", "None")
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append("$label: ") }
            append(value)
        }, style = MaterialTheme.typography.bodyMedium
    )
}

@Composable
private fun InfoChip(text: String) {
    SuggestionChip(onClick = {}, label = { Text(text) })
}

@Composable
private fun ToolCard(tool: Tools) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = rDP(8.dp)),
        colors = CardDefaults.cardColors(containerColor = SkyBlue.copy(alpha = 0.1f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                tool.toolName.ifBlank { "Unnamed tool" },
                style = MaterialTheme.typography.titleSmall
            )
            InfoLine("Description", tool.description.ifBlank { "—" })
            if (tool.args.isNotEmpty()) {
                Text("Args", style = MaterialTheme.typography.labelLarge)
                KeyValueBlock(tool.args)
            } else {
                Text("Args: —", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun KeyValueBlock(map: Map<String, Any?>) {
    val mono = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        map.forEach { (k, v) ->
            Text("- $k: ${valueToString(v)}", style = mono)
        }
    }
}

private fun valueToString(v: Any?): String = when (v) {
    null -> "null"
    is String -> v
    is Number, is Boolean -> v.toString()
    is Map<*, *> -> GsonBuilder().setPrettyPrinting().create().toJson(v)
    is Iterable<*> -> v.joinToString(prefix = "[", postfix = "]") { valueToString(it) }
    else -> v.toString()
}

@Composable
private fun EmptyState(onSeed: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp), contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "No plugins yet",
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Serif)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Tap below to Load Plugins From Device",
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Serif)
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onSeed) { Text("+  Add Plugins") }
        }
    }
}

// ——— Helpers ———
private fun PluginManifest.authorText(): String {
    return runCatching {
        val meta = JSONObject(this.rawCode).optJSONObject("metaData")
        meta?.optString("author", meta.optString("autor", "")) ?: ""
    }.getOrDefault("")
}

private fun PluginLocalDB.manifestJsonOrNull(): String? {
    fun read(name: String): String? = runCatching {
        val f = this.javaClass.getDeclaredField(name)
        f.isAccessible = true
        (f.get(this) as? String)?.takeIf { it.isNotBlank() }
    }.getOrNull()
    return read("manifestCode") ?: read("manifest") ?: read("rawManifest") ?: read("manifestJson")
}