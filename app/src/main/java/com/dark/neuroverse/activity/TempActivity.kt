package com.dark.neuroverse.activity

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.toUpperCase
import androidx.compose.ui.unit.dp
import com.dark.ai_module.ai.Neuron
import com.dark.ai_module.workers.ModelManager
import com.dark.neuroverse.model.Message
import com.dark.neuroverse.model.ROLE
import com.dark.neuroverse.ui.components.MarkdownText
import com.dark.neuroverse.ui.screens.UIComponents.ThinkingBubble
import com.dark.neuroverse.ui.theme.rDP
import com.dark.neuroverse.util.extractPureJson
import com.dark.plugins.repo.PluginRegistry
import com.dark.plugins.ui.theme.NeuroVersePluginTheme
import com.dark.plugins.worker.PluginManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class TempActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NeuroVersePluginTheme {
                Scaffold { padding ->
                    PluginHostScreen(padding)
                }
            }
        }
    }
}

// ✅ UPDATED PluginHostScreen Composable
@Composable
fun PluginHostScreen(paddingValues: PaddingValues) {
    val ctx = LocalContext.current.applicationContext

    val loadedPlugins by PluginManager.plugins.collectAsState()
    val currentPlugin by PluginManager.currentPlugin.collectAsState()

    val (start, end) = (12.dp to 12.dp)

    LaunchedEffect(loadedPlugins.isEmpty()) {
        if (loadedPlugins.isEmpty()) {
            PluginManager.runPlugin(ctx, "app-io-plugin.zip", Unit)
            PluginManager.runPlugin(ctx, "demo-macro-plugin.zip", Unit)
        }
    }

    // Auto select fallback plugin if none selected
    LaunchedEffect(loadedPlugins, currentPlugin) {
        if (currentPlugin == null && loadedPlugins.isNotEmpty()) {
            val fallback = loadedPlugins.first().loadedPlugin
            fallback?.api?.getPluginInfo()?.name?.let {
                PluginManager.setCurrentPluginByName(it)
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val isPluginActive = currentPlugin?.api?.getPluginInfo()?.name?.let { name ->
            loadedPlugins.any { it.loadedPlugin?.api?.getPluginInfo()?.name == name }
        } ?: false

        if (currentPlugin != null && isPluginActive) {
            val pluginName = currentPlugin?.api?.getPluginInfo()?.name
            val storeOwner = remember(pluginName) {
                pluginName?.let { PluginManager.getViewModelStoreOwner(it) }
            }

            storeOwner?.let {
                key(pluginName!!) {
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        currentPlugin?.content?.invoke()
                    }
                }
            }
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(loadedPlugins, key = { it.loadedPlugin?.api?.getPluginInfo()?.name ?: it.hashCode() }) { plugin ->
                val name = plugin.loadedPlugin?.api?.getPluginInfo()?.name ?: "Unknown"
                Row(
                    Modifier
                        .height(50.dp)
                        .width(100.dp)
                        .background(
                            MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(16.dp)
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .clickable { PluginManager.setCurrentPluginByName(name) }
                            .fillMaxHeight()
                            .weight(1f)
                            .background(
                                MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(topStart = start, bottomStart = start)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            name.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        modifier = Modifier
                            .clickable { PluginManager.stopPlugin(name) }
                            .fillMaxHeight()
                            .weight(1f)
                            .background(
                                MaterialTheme.colorScheme.error,
                                shape = RoundedCornerShape(bottomEnd = end, topEnd = end)
                            ),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}
