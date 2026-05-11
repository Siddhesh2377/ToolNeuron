package com.dark.tool_neuron.model

data class HuggingFaceModel(
    val id: String,
    val name: String,
    val fileName: String,
    val fileUri: String,
    val approximateSize: String,
    val sizeBytes: Long = 0,
    val repoId: String = "",
    val repoPath: String = "",
    val quantization: String = "",
    val tags: List<String> = emptyList(),
    val modelType: String = "gguf",
    val isVlm: Boolean = false,
    val isMmproj: Boolean = false,
    val mmprojFileName: String = "",
    val mmprojFileUri: String = "",
    val mmprojSizeBytes: Long = 0,

    // Image-gen / upscaler fields. modelType in {"image_gen", "image_upscaler"}.
    val isSdxl: Boolean = false,
    val requiresNpu: Boolean = false,
    val isUpscaler: Boolean = false,
    val featureLabel: String = "",
    val defaultPrompt: String = "",
    val defaultNegativePrompt: String = "",
    val generationSize: Int = 512,
)

data class HFRepository(
    val id: String,
    val name: String,
    val repoPath: String,
    val isEnabled: Boolean = true,
    val category: ModelCategory = ModelCategory.GENERAL,
)

enum class ModelCategory(val displayName: String) {
    GENERAL("General"),
    MEDICAL("Medical"),
    RESEARCH("Research"),
    CODING("Coding"),
    UNCENSORED("Uncensored"),
    BUSINESS("Business"),
    CYBERSECURITY("Cybersecurity"),
}

enum class SizeCategory(val displayName: String) {
    SMALL("Small (<1GB)"),
    MEDIUM("Medium (1-5GB)"),
    LARGE("Large (>5GB)");

    companion object {
        fun fromSize(sizeStr: String): SizeCategory? {
            val bytes = parseSizeToBytes(sizeStr)
            return when {
                bytes <= 0 -> null
                bytes < 1024L * 1024 * 1024 -> SMALL
                bytes < 5L * 1024 * 1024 * 1024 -> MEDIUM
                else -> LARGE
            }
        }

        fun parseSizeToBytes(sizeStr: String): Long {
            val cleaned = sizeStr.trim().uppercase()
            val number = cleaned.filter { it.isDigit() || it == '.' }.toDoubleOrNull() ?: return 0
            return when {
                cleaned.endsWith("GB") -> (number * 1024 * 1024 * 1024).toLong()
                cleaned.endsWith("MB") -> (number * 1024 * 1024).toLong()
                cleaned.endsWith("KB") -> (number * 1024).toLong()
                else -> 0
            }
        }
    }
}
