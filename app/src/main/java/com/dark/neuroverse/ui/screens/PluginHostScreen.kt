package com.dark.neuroverse.ui.screens

import android.annotation.SuppressLint
import android.util.Log
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.dark.neuroverse.viewModel.DrawerViewModel
import com.dark.plugins.api.CommandQueue
import com.dark.plugins.manager.PluginManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/* ===================================================================== *//*  Composables                                                           *//* ===================================================================== */
@SuppressLint("StateFlowValueCalledInComposition")
@Composable
fun PluginHostScreen(
    paddingValues: PaddingValues = PaddingValues(), viewModel: DrawerViewModel
) {
    val ctx = LocalContext.current.applicationContext

    val loaded by viewModel.loadedPlugins.collectAsState()
    val activeName by viewModel.activePluginName.collectAsState()
    val isActive by viewModel.isActiveLoaded.collectAsState()
    val scope = rememberCoroutineScope()



    LaunchedEffect(loaded, activeName) {
        viewModel.selectFirstIfNone()
    }

    LaunchedEffect(false) {
        scope.launch(Dispatchers.IO + SupervisorJob()) {
            while (true){
                Log.d("PluginHostScreen", "CommandQueue: ${CommandQueue.hasCommand()}")
                CommandQueue.poll()
                delay(1000) // log once per second
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(paddingValues),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Crossfade(
            targetState = if (isActive) activeName else null,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            label = "pluginCrossfade"
        ) { name ->
            if (name == null) return@Crossfade
            remember(name) { viewModel.currentStoreOwner() }
            // The plugin is responsible for rendering its own content.
            // We assume PluginManager.currentPlugin exposes a Composable content lambda.
            PluginManager.currentPlugin.value?.content?.invoke()
        }

        /* Optional selector row — uncomment if you want the inline controls back. *//*
        Spacer(Modifier.height(12.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(
                loaded,
                key = { it.loadedPlugin?.api?.getPluginInfo()?.name ?: it.hashCode() }
            ) { plugin ->
                val name = plugin.loadedPlugin?.api?.getPluginInfo()?.name ?: "Unknown"
                val selected = name == activeName
                val bg by animateColorAsState(
                    if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant,
                    label = "chipBg"
                )
                val elev by animateDpAsState(
                    if (selected) 6.dp else 0.dp,
                    label = "chipElev"
                )

                Surface(
                    tonalElevation = elev,
                    shape = RoundedCornerShape(16.dp),
                    color = bg,
                    modifier = Modifier.size(width = 120.dp, height = 48.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        // select (left half)
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable { viewModel.setCurrentByName(name) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                name.take(2).uppercase(),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        // stop (right half)
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable { viewModel.stopPlugin(name) }
                                .background(MaterialTheme.colorScheme.error),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
        */
    }
}