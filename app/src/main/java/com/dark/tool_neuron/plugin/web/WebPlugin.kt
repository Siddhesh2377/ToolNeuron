package com.dark.tool_neuron.plugin.web

import android.content.Context
import androidx.compose.runtime.Composable
import com.dark.networking.WebNative
import com.dark.networking.WebSearchResult
import com.dark.tool_neuron.plugin.api.Plugin
import com.dark.tool_neuron.plugin.api.ToolDef
import com.dark.tool_neuron.repo.PluginPrefsRepository
import com.dark.tool_neuron.ui.icons.TnIcons
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebPlugin @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: PluginPrefsRepository,
) : Plugin {

    override val id = ID

    override val displayName = "Web Search"

    override val description =
        "Search the web via DuckDuckGo. Uses Chrome TLS impersonation to resist rate-limits and fingerprint-based bot detection."

    override val icon = TnIcons.Globe

    override val tools: List<ToolDef> = listOf(
        ToolDef(
            name = TOOL_SEARCH,
            description = "Search the web for up-to-date information. Returns a list of results each with title, url, and snippet.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("query") {
                        put("type", "string")
                        put("description", "The search query.")
                    }
                    putJsonObject("max_results") {
                        put("type", "integer")
                        put("description", "Maximum number of results (1-10, default 5).")
                    }
                }
                putJsonArray("required") { add(kotlinx.serialization.json.JsonPrimitive("query")) }
            },
        ),
    )

    @Composable
    override fun Settings() {
        WebPluginSettings(pluginId = id, prefs = prefs)
    }

    override suspend fun execute(toolName: String, argsJson: String): Result<String> {
        return when (toolName) {
            TOOL_SEARCH -> doSearch(argsJson)
            else -> Result.failure(IllegalArgumentException("unknown tool: $toolName"))
        }
    }

    private suspend fun doSearch(argsJson: String): Result<String> = runCatching {
        WebNative.ensureReady(context, profile = readProfile())
        val root = Json.parseToJsonElement(argsJson).jsonObject
        val query = resolveQuery(root)
        require(query.isNotEmpty()) { "missing or empty 'query'" }
        val max = (root["max_results"]?.jsonPrimitive?.intOrNull ?: DEFAULT_MAX_RESULTS)
            .coerceIn(1, MAX_ALLOWED_RESULTS)

        val results = WebNative.search(query = query, maxResults = max).getOrThrow()
        encodeResults(query, results)
    }

    private fun resolveQuery(root: kotlinx.serialization.json.JsonObject): String {
        root["query"]?.jsonPrimitive?.contentOrNull?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it }
        for (alias in listOf("q", "search", "input", "text", "prompt", "keyword", "keywords", "param")) {
            root[alias]?.jsonPrimitive?.contentOrNull?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { return it }
        }
        return root.entries
            .firstNotNullOfOrNull { (_, v) -> v.jsonPrimitive.contentOrNull?.trim()?.takeIf { it.isNotEmpty() } }
            .orEmpty()
    }

    private fun readProfile(): String {
        val cfg = runCatching { Json.parseToJsonElement(prefs.getConfig(id)).jsonObject }.getOrNull()
        return cfg?.get("profile")?.jsonPrimitive?.contentOrNull ?: DEFAULT_PROFILE
    }

    private fun encodeResults(query: String, results: List<WebSearchResult>): String =
        Json.encodeToString(
            kotlinx.serialization.json.JsonObject.serializer(),
            buildJsonObject {
                put("query", query)
                putJsonArray("results") {
                    results.forEach { r ->
                        add(
                            buildJsonObject {
                                put("title", r.title)
                                put("url", r.url)
                                put("snippet", r.snippet)
                            }
                        )
                    }
                }
            },
        )

    companion object {
        const val ID = "web"
        const val TOOL_SEARCH = "web_search"
        const val DEFAULT_PROFILE = "chrome116"
        const val DEFAULT_MAX_RESULTS = 5
        const val MAX_ALLOWED_RESULTS = 10
    }
}
