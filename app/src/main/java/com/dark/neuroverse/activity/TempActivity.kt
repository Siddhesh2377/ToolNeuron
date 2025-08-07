package com.dark.neuroverse.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dark.ai_module.workers.ModelManager
import com.dark.plugins.ui.theme.NeuroVersePluginTheme
import com.dark.plugins.worker.PluginManager

class TempActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NeuroVersePluginTheme {
                Scaffold { padding -> PluginHostScreen(padding) }
            }
        }
    }
}

@Composable
fun PluginHostScreen(paddingValues: PaddingValues) {
    val ctx = LocalContext.current.applicationContext
    val loadedPlugins by PluginManager.plugins.collectAsState()
    val currentPlugin by PluginManager.currentPlugin.collectAsState()

    /* ----------  one-shot startup work  ---------- */
    LaunchedEffect(Unit) {
        ModelManager.getModel("Qwen3-Zero-Coder-Reasoning-0.8B")?.let { mdl ->
            ModelManager.loadModel(ctx, mdl) {
                if (PluginManager.plugins.value.isEmpty()) {
                    listOf(
                        "app-io-plugin.zip",
                        "demo-macro-plugin.zip",
                        "ai-chat-plugin.zip"
                    ).forEach { PluginManager.runPlugin(ctx, it, Unit) }
                }
            }
        }
    }

    /* ----------  fallback select  ---------- */
    LaunchedEffect(loadedPlugins, currentPlugin) {
        if (currentPlugin == null && loadedPlugins.isNotEmpty()) {
            loadedPlugins.first().loadedPlugin
                ?.api?.getPluginInfo()?.name
                ?.let(PluginManager::setCurrentPluginByName)
        }
    }

    /* ----------  derived, memoised state  ---------- */
    val pluginName = currentPlugin?.api?.getPluginInfo()?.name
    val isPluginActive by remember(pluginName, loadedPlugins) {
        mutableStateOf(
            pluginName != null && loadedPlugins.any {
                it.loadedPlugin?.api?.getPluginInfo()?.name == pluginName
            }
        )
    }

    /* ----------  UI  ---------- */
    Column(
        Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        /* ---- animated plugin content ---- */
        Crossfade(
            targetState = if (isPluginActive) pluginName else null,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) { name ->
            if (name == null) return@Crossfade           // no plugin yet
            val storeOwner = remember(name) { PluginManager.getViewModelStoreOwner(name) }
            storeOwner.let { currentPlugin?.content?.invoke() }
        }

//        Spacer(Modifier.height(12.dp))
//
//        /* ---- plugin selector row ---- */
//        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
//            items(
//                loadedPlugins,
//                key = { it.loadedPlugin?.api?.getPluginInfo()?.name ?: it.hashCode() }
//            ) { plugin ->
//                val name = plugin.loadedPlugin?.api?.getPluginInfo()?.name ?: "Unknown"
//                val selected = name == pluginName
//
//                // small “alive” animations
//                val bg by animateColorAsState(
//                    if (selected) MaterialTheme.colorScheme.primary
//                    else MaterialTheme.colorScheme.surfaceVariant,
//                    label = "chipBg"
//                )
//                val elev by animateDpAsState(
//                    if (selected) 6.dp else 0.dp,
//                    label = "chipElev"
//                )
//
//                Surface(
//                    tonalElevation = elev,
//                    shape = RoundedCornerShape(16.dp),
//                    color = bg,
//                    modifier = Modifier.size(width = 100.dp, height = 50.dp)
//                ) {
//                    Row(
//                        verticalAlignment = Alignment.CenterVertically,
//                        horizontalArrangement = Arrangement.Center
//                    ) {
//                        /*  select (left half)  */
//                        Box(
//                            Modifier
//                                .weight(1f)
//                                .fillMaxHeight()
//                                .clickable { PluginManager.setCurrentPluginByName(name) },
//                            contentAlignment = Alignment.Center
//                        ) {
//                            Text(
//                                name.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
//                                color = MaterialTheme.colorScheme.onPrimary
//                            )
//                        }
//                        /*  stop (right half)  */
//                        Icon(
//                            Icons.Default.Close,
//                            contentDescription = "Close",
//                            modifier = Modifier
//                                .weight(1f)
//                                .fillMaxHeight()
//                                .clickable { PluginManager.stopPlugin(name) }
//                                .background(MaterialTheme.colorScheme.error),
//                            tint = MaterialTheme.colorScheme.onPrimary
//                        )
//                    }
//                }
//            }
//        }
    }
}
