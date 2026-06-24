package com.dark.tool_neuron.model

import com.dark.tool_neuron.repo.hf.HfSibling
import com.dark.tool_neuron.util.extractQuantization

data class VlmFileGroup(
    val key: String,
    val displayName: String,
    val baseModels: List<HuggingFaceModel> = emptyList(),
    val projectors: List<HuggingFaceModel> = emptyList(),
    val baseFiles: List<HfSibling> = emptyList(),
    val projectorFiles: List<HfSibling> = emptyList(),
) {
    val totalSizeBytes: Long =
        baseModels.sumOf { it.sizeBytes } +
            projectors.sumOf { it.sizeBytes } +
            baseFiles.sumOf { it.sizeBytes } +
            projectorFiles.sumOf { it.sizeBytes }

    val quantizations: List<String> =
        (baseModels.mapNotNull { it.quantization.takeIf(String::isNotBlank) } +
            baseFiles.mapNotNull { extractQuantization(it.path) })
            .distinct()
            .sortedWith(quantComparator)

    val preferredModel: HuggingFaceModel?
        get() = baseModels.sortedWith(
            compareBy<HuggingFaceModel> { quantRank(it.quantization) }
                .thenBy { it.sizeBytes.takeIf { bytes -> bytes > 0 } ?: Long.MAX_VALUE }
                .thenBy { it.name.lowercase() },
        ).firstOrNull()
}

object VlmFileGroups {
    fun fromModels(models: List<HuggingFaceModel>): List<VlmFileGroup> =
        models.groupBy { normalizedKey(it.fileName.ifBlank { it.name }) }
            .map { (key, items) ->
                val bases = items.filterNot { it.isMmproj }.sortedWith(modelSorter)
                val projectors = items.filter { it.isMmproj }.sortedWith(modelSorter)
                VlmFileGroup(
                    key = key,
                    displayName = displayNameFor(key, bases.firstOrNull()?.name ?: projectors.firstOrNull()?.name.orEmpty()),
                    baseModels = bases,
                    projectors = projectors,
                )
            }
            .sortedWith(compareBy<VlmFileGroup> { it.displayName.lowercase() }.thenBy { it.totalSizeBytes })

    fun fromFiles(files: List<HfSibling>): List<VlmFileGroup> =
        files.groupBy { normalizedKey(it.path) }
            .map { (key, siblings) ->
                val bases = siblings.filterNot { it.path.contains("mmproj", ignoreCase = true) }
                    .sortedWith(fileSorter)
                val projectors = siblings.filter { it.path.contains("mmproj", ignoreCase = true) }
                    .sortedWith(fileSorter)
                VlmFileGroup(
                    key = key,
                    displayName = displayNameFor(key, bases.firstOrNull()?.path ?: projectors.firstOrNull()?.path.orEmpty()),
                    baseFiles = bases,
                    projectorFiles = projectors,
                )
            }
            .sortedWith(compareBy<VlmFileGroup> { it.displayName.lowercase() }.thenBy { it.totalSizeBytes })

    private fun normalizedKey(raw: String): String {
        val leaf = raw.substringAfterLast('/')
            .removeSuffix(".gguf")
            .removeSuffix(".GGUF")
            .lowercase()
        return leaf
            .replace(Regex("mmproj|projector|vision-projector|multi-modal-projector"), "")
            .replace(Regex("q[2-8](_k)?(_[msl])?|iq[1-4]_[a-z0-9_]+|f16|bf16|fp16|q4f16|int8|uint8|bnb4"), "")
            .replace(Regex("[._\\-]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { leaf }
    }

    private fun displayNameFor(key: String, fallback: String): String {
        val cleaned = key.split(' ')
            .filter { it.isNotBlank() }
            .joinToString(" ") { part ->
                when {
                    part.equals("vl", ignoreCase = true) -> "VL"
                    part.equals("vlm", ignoreCase = true) -> "VLM"
                    part.equals("llm", ignoreCase = true) -> "LLM"
                    part.length <= 2 -> part.uppercase()
                    else -> part.replaceFirstChar { c -> c.titlecase() }
                }
            }
        return cleaned.ifBlank { fallback.substringAfterLast('/').removeSuffix(".gguf") }
    }

    private val modelSorter = compareBy<HuggingFaceModel> { quantRank(it.quantization) }
        .thenBy { it.sizeBytes.takeIf { bytes -> bytes > 0 } ?: Long.MAX_VALUE }
        .thenBy { it.fileName.lowercase() }

    private val fileSorter = compareBy<HfSibling> { quantRank(extractQuantization(it.path).orEmpty()) }
        .thenBy { it.sizeBytes.takeIf { bytes -> bytes > 0 } ?: Long.MAX_VALUE }
        .thenBy { it.path.lowercase() }
}

private val quantComparator = Comparator<String> { a, b ->
    val rank = quantRank(a).compareTo(quantRank(b))
    if (rank != 0) rank else a.compareTo(b, ignoreCase = true)
}

private fun quantRank(raw: String): Int = when (raw.uppercase()) {
    "Q4_K_M" -> 0
    "Q4_K_S" -> 1
    "Q4_0" -> 2
    "Q5_K_M" -> 3
    "Q5_K_S" -> 4
    "Q6_K" -> 5
    "Q8_0" -> 6
    "F16", "FP16", "BF16" -> 7
    "MMPROJ" -> 100
    "" -> 50
    else -> 20
}
