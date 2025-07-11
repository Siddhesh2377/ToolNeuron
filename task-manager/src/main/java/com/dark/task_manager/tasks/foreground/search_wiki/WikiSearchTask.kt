package com.dark.task_manager.tasks.foreground.search_wiki

import android.content.Context
import android.util.Log
import com.dark.task_manager.api.TaskApi
import com.dark.task_manager.model.TaskInfo
import com.dark.task_manager.model.TaskType
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class WikiSearchTask(context: Context) : TaskApi(context) {

    override fun getTaskInfo(): TaskInfo {
        return TaskInfo(
            taskName = "Wiki Search",
            description = """
            Given a user request, extract the most relevant complete **search question** for Wikipedia.
            The query must be in **full question form**, like "What is the brain?" or "Who discovered gravity?".
            Avoid keywords, avoid short phrases. Use the user’s exact intention and reframe it as a question.
        """.trimIndent(),
            args = """{ "query": "String (Full search question to pass to Wikipedia)" }""".trimIndent(),
            taskType = TaskType.FOREGROUND
        )
    }

    override fun onStart(any: Any) {
        Log.d(getTaskInfo().taskName, "WikiSearchTask started")
    }

    override fun onRun(any: Any): Any {
        val args = any as? JSONObject ?: return JSONObject().put("error", "Invalid arguments")
        val rawQuery = args.optString("query", "").trim()

        if (rawQuery.isEmpty()) {
            Log.w(getTaskInfo().taskName, "No query provided")
            return JSONObject().put("error", "No query provided")
        }

        val normalizedQuery = normalizeQuery(rawQuery)
        Log.d(getTaskInfo().taskName, "Normalized query: $normalizedQuery")

        val rawResult = searchDuckDuckGo(normalizedQuery)

        val parsedResult = when (rawResult.optString("source")) {
            "duckduckgo" -> parseDuckDuckGoResult(rawResult)
            "wikipedia" -> parseWikipediaResult(rawResult)
            else -> JSONObject().put("summary", "No valid source to parse.")
        }

        Log.d(getTaskInfo().taskName, "Parsed result: $parsedResult")

        return JSONObject().put("result", parsedResult).put("type", "wiki_search")
    }

    override fun onStop() {
        Log.d(getTaskInfo().taskName, "WikiSearchTask stopped")
    }

    private fun normalizeQuery(raw: String): String {
        val query = raw.trim().lowercase().replace("?", "")
        val out = query.trim().lowercase().replace(" ", "+")
        // Capitalize properly for Wikipedia title matching (optional)
        return out.split(" ")
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
            .trim()
    }

    private fun searchDuckDuckGo(query: String): JSONObject {
        try {
            // Step 1: Try DuckDuckGo JSON
            val ddgUrl =
                "https://api.duckduckgo.com/?q=$query&format=json&no_redirect=1&no_html=1"
            Log.d(getTaskInfo().taskName, "Fetching from DuckDuckGo: $ddgUrl")
            val ddgConn = URL(ddgUrl).openConnection() as HttpURLConnection
            ddgConn.requestMethod = "GET"
            val ddgResp = ddgConn.inputStream.bufferedReader().readText()
            val ddgJson = JSONObject(ddgResp)

            val summary = ddgJson.optString("AbstractText")
            val url = ddgJson.optString("AbstractURL")

            if (summary.isNotBlank() || url.isNotBlank()) {
                return JSONObject()
                    .put("source", "duckduckgo")
                    .put("summary", summary)
                    .put("url", url)
                    .put("related", ddgJson.optJSONArray("RelatedTopics") ?: JSONArray())
            }

            // Step 2: Fallback to Wikipedia search
            val wikiSearchUrl =
                "https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch=$query&format=json"
            val wikiSearchConn = URL(wikiSearchUrl).openConnection() as HttpURLConnection
            wikiSearchConn.requestMethod = "GET"
            val wikiSearchResp = wikiSearchConn.inputStream.bufferedReader().readText()
            val wikiSearchJson = JSONObject(wikiSearchResp)
            val hits = wikiSearchJson.getJSONObject("query").getJSONArray("search")

            if (hits.length() > 0) {
                val title = hits.getJSONObject(0).getString("title").replace(" ", "_")
                val summaryUrl = "https://en.wikipedia.org/api/rest_v1/page/summary/$title"
                val sumConn = URL(summaryUrl).openConnection() as HttpURLConnection
                sumConn.requestMethod = "GET"
                val sumResp = sumConn.inputStream.bufferedReader().readText()
                val sumJson = JSONObject(sumResp)

                return JSONObject()
                    .put("source", "wikipedia")
                    .put("title", sumJson.optString("title"))
                    .put("summary", sumJson.optString("extract"))
                    .put(
                        "url", sumJson.optJSONObject("content_urls")
                            ?.getJSONObject("desktop")
                            ?.optString("page")
                    )
            }

            // Step 3: Absolute fallback
            val fallbackUrl = "https://duckduckgo.com/?q=$query&ia=web"
            return JSONObject()
                .put("source", "duckduckgo-web")
                .put("summary", "No structured answer found. Try checking manually.")
                .put("url", fallbackUrl)

        } catch (e: Exception) {
            e.printStackTrace()
            return JSONObject().put("error", "Failed to fetch: ${e.localizedMessage}")
        }
    }

    private fun parseDuckDuckGoResult(json: JSONObject): JSONObject {
        val summary = json.optString("summary").trim()
        val url = json.optString("url")
        val related = json.optJSONArray("related") ?: JSONArray()
        val relatedItems = JSONArray()

        // Extract all related topics (flat or nested)
        for (i in 0 until related.length()) {
            val item = related.getJSONObject(i)
            if (item.has("Text") && item.has("FirstURL")) {
                relatedItems.put(
                    JSONObject()
                        .put("text", item.getString("Text"))
                        .put("url", item.getString("FirstURL"))
                )
            }

            // Handle nested topics
            if (item.has("Topics")) {
                val topics = item.getJSONArray("Topics")
                for (j in 0 until topics.length()) {
                    val subItem = topics.getJSONObject(j)
                    if (subItem.has("Text") && subItem.has("FirstURL")) {
                        if (relatedItems.length() == 3) break
                        relatedItems.put(
                            JSONObject()
                                .put("text", subItem.getString("Text"))
                                .put("url", subItem.getString("FirstURL"))
                        )
                    }
                }
            }
        }

        // Main answer fallback logic
        return if (summary.isNotBlank()) {
            JSONObject()
                .put("title", "DuckDuckGo Instant Answer")
                .put("summary", summary)
                .put("url", url)
                .put("related_items", relatedItems)
        } else if (relatedItems.length() > 0) {
            val firstRelated = relatedItems.getJSONObject(0)
            JSONObject()
                .put("title", "DuckDuckGo Related Result")
                .put("summary", firstRelated.getString("text"))
                .put("url", firstRelated.getString("url"))
                .put("related_items", relatedItems)
        } else {
            JSONObject()
                .put("title", "DuckDuckGo")
                .put("summary", "No clear answer found. Try checking the search page.")
                .put("url", url)
                .put("related_items", JSONArray())
        }
    }

    private fun parseWikipediaResult(json: JSONObject): JSONObject {
        val title = json.optString("title", "Wikipedia")
        val summary = json.optString("summary", "No summary found.")
        val url = json.optString("url", "https://en.wikipedia.org")

        return JSONObject()
            .put("title", title)
            .put("summary", summary)
            .put("url", url)
    }

}
