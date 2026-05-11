package com.dark.tool_neuron.repo.hf

import org.json.JSONArray
import org.json.JSONObject

internal object HfJsonParse {

    fun parseGated(any: Any?): HfGated {
        if (any == null) return HfGated.OPEN
        if (any == false) return HfGated.OPEN
        if (any == true) return HfGated.GATED
        val s = any.toString()
        return when {
            s.equals("auto", ignoreCase = true) -> HfGated.AUTO
            s.equals("true", ignoreCase = true) -> HfGated.GATED
            s.equals("false", ignoreCase = true) -> HfGated.OPEN
            else -> HfGated.OPEN
        }
    }

    fun stringList(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull {
            arr.optString(it).takeIf { s -> s.isNotBlank() }
        }
    }

    fun parseSummary(obj: JSONObject): HfModelSummary? {
        val id = obj.optString("id", "").takeIf { it.contains("/") } ?: return null
        return HfModelSummary(
            id = id,
            author = obj.optString("author", id.substringBefore("/")),
            downloads = obj.optLong("downloads", 0L),
            likes = obj.optLong("likes", 0L),
            gated = parseGated(obj.opt("gated")),
            tags = stringList(obj.optJSONArray("tags")),
            pipelineTag = obj.optString("pipeline_tag", "").takeIf { it.isNotBlank() },
            libraryName = obj.optString("library_name", "").takeIf { it.isNotBlank() },
            lastModified = obj.optString("lastModified", "").takeIf { it.isNotBlank() },
            createdAt = obj.optString("createdAt", "").takeIf { it.isNotBlank() },
        )
    }

    fun parseSiblings(arr: JSONArray?): List<HfSibling> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val path = o.optString("rfilename", "").takeIf { it.isNotBlank() }
                ?: o.optString("path", "").takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val size = o.optLong("size", 0L).takeIf { it > 0L }
                ?: o.optJSONObject("lfs")?.optLong("size", 0L)
                ?: 0L
            val sha = o.optJSONObject("lfs")?.optString("sha256", "")
                ?.takeIf { it.isNotBlank() }
            HfSibling(path = path, sizeBytes = size, sha256 = sha)
        }
    }

    fun parseGguf(obj: JSONObject?): HfGgufMeta? {
        if (obj == null) return null
        return HfGgufMeta(
            architecture = obj.optString("architecture", "").takeIf { it.isNotBlank() },
            contextLength = obj.optLong("context_length", -1L).takeIf { it > 0L },
            totalBytes = obj.optLong("total", -1L).takeIf { it > 0L },
            bosToken = obj.optString("bos_token", "").takeIf { it.isNotBlank() },
            eosToken = obj.optString("eos_token", "").takeIf { it.isNotBlank() },
        )
    }

    fun parseCardData(obj: JSONObject?): HfCardData? {
        if (obj == null) return null
        val licenseRaw = obj.opt("license")
        val license = when (licenseRaw) {
            is String -> licenseRaw.takeIf { it.isNotBlank() }
            is JSONArray -> stringList(licenseRaw).firstOrNull()
            else -> null
        }
        val baseModelRaw = obj.opt("base_model")
        val baseModel = when (baseModelRaw) {
            is String -> listOfNotNull(baseModelRaw.takeIf { it.isNotBlank() })
            is JSONArray -> stringList(baseModelRaw)
            else -> emptyList()
        }
        val languageRaw = obj.opt("language")
        val languages = when (languageRaw) {
            is String -> listOfNotNull(languageRaw.takeIf { it.isNotBlank() })
            is JSONArray -> stringList(languageRaw)
            else -> emptyList()
        }
        return HfCardData(
            license = license,
            baseModel = baseModel,
            languages = languages,
            pipelineTag = obj.optString("pipeline_tag", "").takeIf { it.isNotBlank() },
            tags = stringList(obj.optJSONArray("tags")),
            gatedPrompt = obj.optString("extra_gated_prompt", "").takeIf { it.isNotBlank() },
        )
    }

    fun parseDetail(obj: JSONObject): HfModelDetail? {
        val summary = parseSummary(obj) ?: return null
        return HfModelDetail(
            summary = summary,
            files = parseSiblings(obj.optJSONArray("siblings")),
            ggufMeta = parseGguf(obj.optJSONObject("gguf")),
            cardData = parseCardData(obj.optJSONObject("cardData")),
            spaces = stringList(obj.optJSONArray("spaces")),
        )
    }

    fun parseQuick(obj: JSONObject): HfQuickResult? {
        val id = obj.optString("id", "").takeIf { it.isNotBlank() } ?: return null
        return HfQuickResult(
            id = id,
            author = obj.optString("author", id.substringBefore("/")),
            type = obj.optString("type", "model"),
        )
    }

    fun parseTrendingItem(obj: JSONObject): HfTrendingItem? {
        val data = obj.optJSONObject("repoData") ?: obj
        val id = data.optString("id", "").takeIf { it.contains("/") } ?: return null
        return HfTrendingItem(
            id = id,
            author = data.optString("author", id.substringBefore("/")),
            downloads = data.optLong("downloads", 0L),
            likes = data.optLong("likes", 0L),
            pipelineTag = data.optString("pipeline_tag", "").takeIf { it.isNotBlank() },
            numParameters = data.optLong("numParameters", -1L).takeIf { it > 0L },
            gated = parseGated(data.opt("gated")),
            lastModified = data.optString("lastModified", "").takeIf { it.isNotBlank() },
        )
    }

    fun parseTagsCatalog(obj: JSONObject): HfTagsCatalog {
        return HfTagsCatalog(
            pipelineTags = parseTagBucket(obj.optJSONArray("pipeline_tag")),
            libraries = parseTagBucket(obj.optJSONArray("library")),
            licenses = parseTagBucket(obj.optJSONArray("license")),
            languages = parseTagBucket(obj.optJSONArray("language")),
            regions = parseTagBucket(obj.optJSONArray("region")),
            other = parseTagBucket(obj.optJSONArray("other")),
        )
    }

    private fun parseTagBucket(arr: JSONArray?): List<HfTagEntry> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id", "").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            HfTagEntry(
                id = id,
                label = o.optString("label", "").takeIf { it.isNotBlank() } ?: id,
                type = o.optString("type", ""),
            )
        }
    }
}
