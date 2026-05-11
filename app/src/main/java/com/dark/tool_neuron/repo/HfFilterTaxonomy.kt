package com.dark.tool_neuron.repo

object HfFilterTaxonomy {

    val PIPELINE_TAGS_TOP = listOf(
        "text-generation",
        "image-text-to-text",
        "text-to-image",
        "automatic-speech-recognition",
        "text-to-speech",
        "feature-extraction",
        "sentence-similarity",
        "text-classification",
    )

    val PIPELINE_TAGS_ALL = listOf(
        "text-generation",
        "image-text-to-text",
        "text-to-image",
        "image-to-text",
        "image-to-image",
        "image-to-video",
        "text-to-video",
        "video-text-to-text",
        "automatic-speech-recognition",
        "text-to-speech",
        "text-to-audio",
        "audio-to-audio",
        "audio-classification",
        "audio-text-to-text",
        "voice-activity-detection",
        "feature-extraction",
        "sentence-similarity",
        "fill-mask",
        "summarization",
        "translation",
        "question-answering",
        "table-question-answering",
        "visual-question-answering",
        "document-question-answering",
        "text-classification",
        "token-classification",
        "zero-shot-classification",
        "text-ranking",
        "image-classification",
        "object-detection",
        "image-segmentation",
        "depth-estimation",
        "zero-shot-image-classification",
        "zero-shot-object-detection",
        "image-feature-extraction",
        "visual-document-retrieval",
        "video-classification",
        "video-to-video",
        "text-to-3d",
        "image-to-3d",
        "keypoint-detection",
        "mask-generation",
        "unconditional-image-generation",
        "graph-ml",
        "tabular-classification",
        "tabular-regression",
        "time-series-forecasting",
        "reinforcement-learning",
        "robotics",
        "any-to-any",
    )

    val LIBRARIES_TOP = listOf(
        "gguf", "transformers", "safetensors", "diffusers",
        "sentence-transformers", "mlx", "onnx", "tflite",
        "peft", "llamafile", "transformers.js",
    )

    val LIBRARIES_ALL = listOf(
        "gguf", "transformers", "safetensors", "diffusers",
        "sentence-transformers", "mlx", "onnx", "tflite",
        "peft", "llamafile", "transformers.js",
        "pytorch", "tensorflow", "jax", "tensorboard",
        "stable-baselines3", "ml-agents", "keras", "tf-keras",
        "joblib", "adapter-transformers", "setfit", "timm",
        "sample-factory", "openvino", "flair", "coreml",
        "nemo", "fastai", "espnet", "bertopic", "spacy",
        "fasttext", "rust", "open_clip", "sklearn",
        "keras-hub", "asteroid", "executorch", "speechbrain",
        "allennlp", "fairseq", "paddlepaddle", "PaddleOCR",
        "stanza", "pyannote-audio", "optimum_habana",
        "optimum_graphcore", "span-marker", "paddlenlp",
        "unity-sentis", "dduf", "univa",
    )

    val APPS = listOf(
        "llama.cpp", "ollama", "lm-studio", "jan",
        "mlx-lm", "vllm", "draw-things",
    )

    val INFERENCE_PROVIDERS = listOf(
        "groq", "novita", "cerebras", "sambanova",
        "nscale", "fal", "hyperbolic", "together-ai",
    )

    val LANGUAGES_TOP = listOf(
        "en", "multilingual", "zh", "fr", "es", "de",
        "ja", "ko", "pt", "it", "ru", "ar", "hi",
    )

    val LANGUAGES_ALL = listOf(
        "en", "multilingual", "zh", "fr", "es", "de",
        "ja", "ko", "pt", "it", "ru", "ar", "hi",
        "th", "tr", "vi", "id", "pl", "nl", "sv",
        "ro", "uk", "fi", "fa", "cs", "bn", "el",
        "he", "da", "ms", "ta", "hu", "ur", "bg",
        "ca", "te", "sw", "no", "mr", "sr", "sk",
        "sl", "et", "gu", "my", "ml", "hr", "lt",
        "is", "lv", "gl", "tl", "pa", "kn", "km",
        "eu", "af", "am", "ka",
    )

    val LICENSES_TOP = listOf(
        "apache-2.0", "mit", "llama3.1", "llama3", "llama2",
        "gemma", "cc-by-4.0", "cc-by-nc-4.0", "openrail", "other",
    )

    val LICENSES_ALL = listOf(
        "apache-2.0", "mit", "llama4", "llama3.3", "llama3.2", "llama3.1", "llama3", "llama2",
        "gemma", "openrail", "openrail++", "creativeml-openrail-m",
        "cc-by-4.0", "cc-by-nc-4.0", "cc-by-sa-4.0", "cc-by-nc-sa-4.0",
        "cc-by-nc-nd-4.0", "cc-by-nd-4.0", "cc-by-3.0", "cc-by-2.0",
        "cc-by-nc-3.0", "cc-by-nc-2.0", "cc-by-sa-3.0", "cc-by-2.5",
        "cc0-1.0", "cc",
        "afl-3.0", "agpl-3.0", "apple-amlr", "artistic-2.0",
        "bigcode-openrail-m", "bigscience-bloom-rail-1.0", "bigscience-openrail-m",
        "bsd-2-clause", "bsd-3-clause", "bsd-3-clause-clear", "bsd", "bsl-1.0",
        "c-uda", "cdla-permissive-1.0", "cdla-permissive-2.0", "cdla-sharing-1.0",
        "deepfloyd-if-license", "epl-2.0", "etalab-2.0", "eupl-1.1", "eupl-1.2",
        "fair-noncommercial-research-license", "gfdl", "gpl", "gpl-2.0", "gpl-3.0",
        "grok2-community", "h-research", "intel-research", "isc",
        "lgpl", "lgpl-2.1", "lgpl-3.0", "mpl-2.0", "ms-pl", "ncsa",
        "odbl", "ofl-1.1", "open-mdw", "osl-3.0", "postgresql",
        "unlicense", "wtfpl", "zlib", "other",
    )

    val REGIONS = listOf("us", "eu")

    val OTHER_TAGS = listOf(
        "endpoints_compatible",
        "text-generation-inference",
        "text-embeddings-inference",
        "4-bit",
        "8-bit",
        "custom_code",
        "merge",
        "moe",
        "co2_eq_emissions",
        "model-index",
    )

    val QUANT_TAGS = listOf(
        "Q2_K", "Q3_K_S", "Q3_K_M", "Q3_K_L",
        "Q4_0", "Q4_K_S", "Q4_K_M",
        "Q5_0", "Q5_K_S", "Q5_K_M",
        "Q6_K", "Q8_0",
        "F16", "BF16",
        "IQ1_S", "IQ2_XXS", "IQ2_XS", "IQ2_S",
        "IQ3_XXS", "IQ3_XS", "IQ3_S",
        "IQ4_NL", "IQ4_XS",
        "gptq", "awq", "exl2", "bnb",
    )

    val PARAM_STEPS_MILLIONS: List<Long> = listOf(
        0L, 100L, 500L, 1_000L, 3_000L, 7_000L,
        13_000L, 30_000L, 70_000L, 175_000L, 500_000L,
    )

    fun paramStepLabel(millions: Long): String = when {
        millions == 0L -> "Any"
        millions >= 1_000L -> "${millions / 1_000L}B"
        else -> "${millions}M"
    }

    fun pipelineTagLabel(tag: String): String =
        tag.split("-").joinToString(" ") { it.replaceFirstChar(Char::uppercase) }

    fun languageLabel(code: String): String = LANG_LABELS[code] ?: code.uppercase()

    private val LANG_LABELS = mapOf(
        "en" to "English", "multilingual" to "Multilingual",
        "zh" to "Chinese", "fr" to "French", "es" to "Spanish",
        "de" to "German", "ja" to "Japanese", "ko" to "Korean",
        "pt" to "Portuguese", "it" to "Italian", "ru" to "Russian",
        "ar" to "Arabic", "hi" to "Hindi", "th" to "Thai",
        "tr" to "Turkish", "vi" to "Vietnamese", "id" to "Indonesian",
        "pl" to "Polish", "nl" to "Dutch", "sv" to "Swedish",
        "ro" to "Romanian", "uk" to "Ukrainian", "fi" to "Finnish",
        "fa" to "Persian", "cs" to "Czech", "bn" to "Bengali",
        "el" to "Greek", "he" to "Hebrew", "da" to "Danish",
        "ms" to "Malay", "ta" to "Tamil", "hu" to "Hungarian",
        "ur" to "Urdu", "bg" to "Bulgarian", "ca" to "Catalan",
        "te" to "Telugu", "sw" to "Swahili", "no" to "Norwegian",
        "mr" to "Marathi", "sr" to "Serbian", "sk" to "Slovak",
        "sl" to "Slovenian", "et" to "Estonian", "gu" to "Gujarati",
        "my" to "Burmese", "ml" to "Malayalam", "hr" to "Croatian",
        "lt" to "Lithuanian", "is" to "Icelandic", "lv" to "Latvian",
        "gl" to "Galician", "tl" to "Tagalog", "pa" to "Punjabi",
        "kn" to "Kannada", "km" to "Khmer", "eu" to "Basque",
        "af" to "Afrikaans", "am" to "Amharic", "ka" to "Georgian",
    )
}
