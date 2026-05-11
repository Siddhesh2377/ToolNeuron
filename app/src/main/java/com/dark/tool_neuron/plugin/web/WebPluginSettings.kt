package com.dark.tool_neuron.plugin.web

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.dark.tool_neuron.repo.PluginPrefsRepository
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.LocalTnShapes
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private val kProfiles: List<Pair<String, String>> = listOf(
    "chrome116" to "Chrome 116 (latest)",
    "chrome110" to "Chrome 110",
    "chrome107" to "Chrome 107",
    "chrome104" to "Chrome 104",
    "chrome101" to "Chrome 101",
    "chrome100" to "Chrome 100",
    "chrome99" to "Chrome 99",
    "edge101" to "Edge 101",
    "edge99" to "Edge 99",
    "safari15_5" to "Safari 15.5",
    "safari15_3" to "Safari 15.3",
)

@Composable
internal fun WebPluginSettings(
    pluginId: String,
    prefs: PluginPrefsRepository,
) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current

    val initial = remember { readConfig(prefs.getConfig(pluginId)) }
    var profile by remember { mutableStateOf(initial.profile) }
    var menuOpen by remember { mutableStateOf(false) }

    val label = kProfiles.firstOrNull { it.first == profile }?.second ?: profile

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(dimens.spacingSm),
    ) {
        Text(
            text = "Browser profile",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Box {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { menuOpen = true },
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = tnShapes.cardSmall,
            ) {
                Row(
                    modifier = Modifier.padding(
                        horizontal = dimens.cardPadding,
                        vertical = dimens.spacingSm,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = TnIcons.ChevronDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
            ) {
                kProfiles.forEach { (id, title) ->
                    DropdownMenuItem(
                        text = { Text(title) },
                        onClick = {
                            menuOpen = false
                            profile = id
                            prefs.setConfig(pluginId, writeConfig(WebConfig(profile = id)))
                        },
                    )
                }
            }
        }

        Text(
            text = "Determines the TLS + HTTP/2 fingerprint used when contacting DuckDuckGo. Newer profiles blend better with real traffic.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private data class WebConfig(val profile: String = "chrome116")

private fun readConfig(json: String): WebConfig {
    val obj = runCatching { Json.parseToJsonElement(json).jsonObject }.getOrNull()
        ?: return WebConfig()
    val profile = obj["profile"]?.jsonPrimitive?.contentOrNull ?: "chrome116"
    return WebConfig(profile = profile)
}

private fun writeConfig(cfg: WebConfig): String =
    Json.encodeToString(
        JsonObject.serializer(),
        buildJsonObject { put("profile", cfg.profile) },
    )
