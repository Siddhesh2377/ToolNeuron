package com.dark.tool_neuron.ui.screens.guide

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import com.dark.tool_neuron.ui.icons.TnIcons

@Composable
fun GuidePluginsScreen(innerPadding: PaddingValues) {
    GuideDetailLayout(
        innerPadding = innerPadding,
        icon = TnIcons.Puzzle,
        lede = "Plugins are mini-apps that run inside ToolNeuron. They get their own Compose screen, can store data in encrypted storage, and can ship their own ONNX models. Browse them from the in-app store; they download straight from a public HuggingFace repo.",
        steps = listOf(
            GuideStep(
                title = "Open the store",
                body = "Drawer → Plugins. The Store tab lists everything in the public catalog at Void2377/tool-neuron-plugins on HuggingFace. The list refreshes every time you open the screen — there's no local cache, so the repo is always the source of truth.",
            ),
            GuideStep(
                title = "Install a plugin",
                body = "Tap Install on a card. The app streams the zip into cache, verifies its SHA-256 against the catalog manifest, extracts it into the plugin store, and deletes the temp file. If the hash doesn't match, install aborts. The card flips to Installed when it's ready.",
            ),
            GuideStep(
                title = "Launch from the drawer",
                body = "Installed plugins show up as initial chips in the plugin dock when you have one open. Tap the Open button on a store card or use the dock to switch. Each plugin runs its own Compose UI inside a single host activity — moving between plugins is a fade-and-scale, not a new task.",
            ),
            GuideStep(
                title = "Capabilities and the native badge",
                body = "Every plugin declares what it needs in its manifest: data read/write, internet, ONNX, camera, mic, filesystem, notifications, clipboard. The store card shows them as chips. A 'native' badge means the zip ships .so files — pay attention to that one since it's running real ARM code on your phone.",
            ),
            GuideStep(
                title = "Uninstall and storage cleanup",
                body = "Installed tab → Uninstall. The plugin's encrypted storage collection (plugin_<id>) is dropped at the same time, so no orphaned data remains. The dex and so files are write-locked while installed; the uninstall path unlocks them first so they can be deleted on Android 14+.",
            ),
        ),
        tips = listOf(
            "ONNX execution provider is configurable from Settings → Plugins (CPU, NNAPI, or XNNPACK). Default is CPU because XNNPACK can crash on INT8 mixed-boundary models.",
            "First-party plugins right now: Notes, Counter, Expense Tracker. Expense uses MiniLM-L6-v2 (INT8, ~22 MB) for auto-categorisation, all local.",
            "Plugins run in the same process as the host. There's no separate sandbox at the OS level — capability declarations are the boundary.",
        ),
    )
}
