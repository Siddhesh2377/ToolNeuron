package com.dark.tool_neuron.model

data class GGUFModels(
    //Model Details
    var modelName: String = "",
    var architecture: String = "",          //gemma, llama, qwen
    var modelType: String = "TXT",          //TXT, VLM, EMBED
    var modelFileLink: String = "",

    // Performance settings
    var gpuLayers: Int = 0,
    var useMMAP: Boolean = true,
    var useMLOCK: Boolean = false,
    var ctxSize: Int = 4_048,

    // Sampling settings
    var temp: Float = 0.7f,
    var topK: Int = 20,
    var topP: Float = 0.5f,
    var minP: Float = 0.0f,
    var maxTokens: Int = 2048,

    // Text behavior tuning
    var mirostat: Int = 1,                  // 0=off, 1=v1, 2=v2 (adaptive sampling)
    var mirostatTau: Float = 5.0f,          // target entropy
    var mirostatEta: Float = 0.1f,          // learning rate for mirostat

    // Misc control
    var seed: Int = -1,                     // -1=random, else fixed generation
    var isImported: Boolean = false,
    var modelUrl: String? = null,
    var isToolCalling: Boolean = false,

    // Prompt configuration
    var systemPrompt: String = "You are a helpful assistant.",
    var chatTemplate: String? = null
)