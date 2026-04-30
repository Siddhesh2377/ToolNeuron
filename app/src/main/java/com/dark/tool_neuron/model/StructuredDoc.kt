package com.dark.tool_neuron.model

import org.json.JSONArray
import org.json.JSONObject

data class DocSection(
    val heading: String,
    val body: String,
)

data class DocSource(
    val url: String,
    val title: String,
    val iteration: Int,
)

data class IterationLogEntry(
    val iteration: Int,
    val questions: List<String>,
)

data class StructuredDoc(
    val title: String,
    val summary: String,
    val sections: List<DocSection>,
    val sources: List<DocSource>,
    val iterationLog: List<IterationLogEntry>,
    val modelName: String,
    val iterationsUsed: Int,
    val totalFetchedBytes: Long,
    val durationMs: Long,
) {
    fun toJson(): String {
        val obj = JSONObject()
        obj.put("title", title)
        obj.put("summary", summary)
        obj.put("modelName", modelName)
        obj.put("iterationsUsed", iterationsUsed)
        obj.put("totalFetchedBytes", totalFetchedBytes)
        obj.put("durationMs", durationMs)
        obj.put("sections", JSONArray().also { arr ->
            sections.forEach {
                arr.put(JSONObject().put("heading", it.heading).put("body", it.body))
            }
        })
        obj.put("sources", JSONArray().also { arr ->
            sources.forEach {
                arr.put(
                    JSONObject()
                        .put("url", it.url)
                        .put("title", it.title)
                        .put("iteration", it.iteration),
                )
            }
        })
        obj.put("iterationLog", JSONArray().also { arr ->
            iterationLog.forEach { entry ->
                val qArr = JSONArray()
                entry.questions.forEach { qArr.put(it) }
                arr.put(JSONObject().put("iteration", entry.iteration).put("questions", qArr))
            }
        })
        return obj.toString()
    }

    companion object {
        fun fromJson(json: String): StructuredDoc {
            val obj = JSONObject(json)
            val sections = obj.optJSONArray("sections")?.let { arr ->
                List(arr.length()) { i ->
                    val o = arr.getJSONObject(i)
                    DocSection(o.optString("heading"), o.optString("body"))
                }
            }.orEmpty()
            val sources = obj.optJSONArray("sources")?.let { arr ->
                List(arr.length()) { i ->
                    val o = arr.getJSONObject(i)
                    DocSource(o.optString("url"), o.optString("title"), o.optInt("iteration", 0))
                }
            }.orEmpty()
            val iterationLog = obj.optJSONArray("iterationLog")?.let { arr ->
                List(arr.length()) { i ->
                    val o = arr.getJSONObject(i)
                    val qArr = o.optJSONArray("questions")
                    val qs = qArr?.let { q -> List(q.length()) { idx -> q.optString(idx) } }.orEmpty()
                    IterationLogEntry(o.optInt("iteration", 0), qs)
                }
            }.orEmpty()
            return StructuredDoc(
                title = obj.optString("title"),
                summary = obj.optString("summary"),
                sections = sections,
                sources = sources,
                iterationLog = iterationLog,
                modelName = obj.optString("modelName"),
                iterationsUsed = obj.optInt("iterationsUsed", 0),
                totalFetchedBytes = obj.optLong("totalFetchedBytes", 0L),
                durationMs = obj.optLong("durationMs", 0L),
            )
        }
    }
}

data class ResearchDocument(
    val docId: String,
    val title: String,
    val originChatId: String,
    val originMessageId: String,
    val question: String,
    val structured: StructuredDoc,
    val createdAt: Long,
    val durationMs: Long,
    val modelId: String,
    val iterationsUsed: Int,
)
