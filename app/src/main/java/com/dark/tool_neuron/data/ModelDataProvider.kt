package com.dark.tool_neuron.data

import com.mp.ai_engine.models.llm_models.CloudModel
import com.mp.ai_engine.models.llm_models.ModelType

/**
 * Provider for pre-configured CloudModel list
 * Can be replaced with Firebase/API data later
 */
object ModelDataProvider {

    /**
     * Get list of available GGUF models
     * This can be replaced with Firebase fetch or API call
     */
    fun getGGUFModels(): List<CloudModel> {
        return listOf(
            CloudModel(
                modelName = "Llama-3.2-1B-Instruct",
                modelDescription = "Meta's Llama 3.2 1B instruction-tuned model. Fast and efficient for general tasks.",
                providerName = "GGUF",
                modelType = ModelType.TEXT,
                modelFileSize = "1.2 GB",
                modelFileLink = "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf",
                isLocal = false,
                metaData = mapOf(
                    "architecture" to "LLAMA",
                    "threads" to "4",
                    "gpu-layers" to "0",
                    "ctxSize" to "8192",
                    "useMMAP" to "true",
                    "useMLOCK" to "false",
                    "maxTokens" to "2048",
                    "temp" to "0.7",
                    "topK" to "40",
                    "topP" to "0.9",
                    "min-p" to "0.05",
                    "mirostat" to "0",
                    "mirostatTau" to "5.0",
                    "mirostatEta" to "0.1",
                    "seed" to "-1",
                    "systemPrompt" to "You are a helpful AI assistant.",
                    "chatTemplate" to "",
                    "tags" to "llama,instruct,small,fast"
                )
            ),
            
            CloudModel(
                modelName = "Llama-3.2-3B-Instruct",
                modelDescription = "Meta's Llama 3.2 3B instruction-tuned model. Balanced performance and quality.",
                providerName = "GGUF",
                modelType = ModelType.TEXT,
                modelFileSize = "3.4 GB",
                modelFileLink = "https://huggingface.co/bartowski/Llama-3.2-3B-Instruct-GGUF/resolve/main/Llama-3.2-3B-Instruct-Q4_K_M.gguf",
                isLocal = false,
                metaData = mapOf(
                    "architecture" to "LLAMA",
                    "threads" to "4",
                    "gpu-layers" to "0",
                    "ctxSize" to "8192",
                    "useMMAP" to "true",
                    "useMLOCK" to "false",
                    "maxTokens" to "2048",
                    "temp" to "0.7",
                    "topK" to "40",
                    "topP" to "0.9",
                    "min-p" to "0.05",
                    "mirostat" to "0",
                    "mirostatTau" to "5.0",
                    "mirostatEta" to "0.1",
                    "seed" to "-1",
                    "systemPrompt" to "You are a helpful AI assistant.",
                    "chatTemplate" to "",
                    "tags" to "llama,instruct,medium"
                )
            ),
            
            CloudModel(
                modelName = "Phi-3.5-mini-instruct",
                modelDescription = "Microsoft's Phi-3.5 mini instruction model. Excellent for mobile devices.",
                providerName = "GGUF",
                modelType = ModelType.TEXT,
                modelFileSize = "2.3 GB",
                modelFileLink = "https://huggingface.co/bartowski/Phi-3.5-mini-instruct-GGUF/resolve/main/Phi-3.5-mini-instruct-Q4_K_M.gguf",
                isLocal = false,
                metaData = mapOf(
                    "architecture" to "PHI3",
                    "threads" to "4",
                    "gpu-layers" to "0",
                    "ctxSize" to "4096",
                    "useMMAP" to "true",
                    "useMLOCK" to "false",
                    "maxTokens" to "2048",
                    "temp" to "0.7",
                    "topK" to "40",
                    "topP" to "0.9",
                    "min-p" to "0.05",
                    "mirostat" to "0",
                    "mirostatTau" to "5.0",
                    "mirostatEta" to "0.1",
                    "seed" to "-1",
                    "systemPrompt" to "You are a helpful AI assistant.",
                    "chatTemplate" to "",
                    "tags" to "phi,microsoft,efficient"
                )
            ),
            
            CloudModel(
                modelName = "Qwen2.5-1.5B-Instruct",
                modelDescription = "Alibaba's Qwen 2.5 1.5B instruction model. Great multilingual support.",
                providerName = "GGUF",
                modelType = ModelType.TEXT,
                modelFileSize = "1.8 GB",
                modelFileLink = "https://huggingface.co/bartowski/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/Qwen2.5-1.5B-Instruct-Q4_K_M.gguf",
                isLocal = false,
                metaData = mapOf(
                    "architecture" to "QWEN2",
                    "threads" to "4",
                    "gpu-layers" to "0",
                    "ctxSize" to "4096",
                    "useMMAP" to "true",
                    "useMLOCK" to "false",
                    "maxTokens" to "2048",
                    "temp" to "0.7",
                    "topK" to "40",
                    "topP" to "0.9",
                    "min-p" to "0.05",
                    "mirostat" to "0",
                    "mirostatTau" to "5.0",
                    "mirostatEta" to "0.1",
                    "seed" to "-1",
                    "systemPrompt" to "You are a helpful AI assistant.",
                    "chatTemplate" to "",
                    "tags" to "qwen,multilingual,efficient"
                )
            ),
            
            CloudModel(
                modelName = "SmolLM2-1.7B-Instruct",
                modelDescription = "HuggingFace's SmolLM2 instruction model. Optimized for efficiency.",
                providerName = "GGUF",
                modelType = ModelType.TEXT,
                modelFileSize = "1.5 GB",
                modelFileLink = "https://huggingface.co/bartowski/SmolLM2-1.7B-Instruct-GGUF/resolve/main/SmolLM2-1.7B-Instruct-Q4_K_M.gguf",
                isLocal = false,
                metaData = mapOf(
                    "architecture" to "LLAMA",
                    "threads" to "4",
                    "gpu-layers" to "0",
                    "ctxSize" to "4096",
                    "useMMAP" to "true",
                    "useMLOCK" to "false",
                    "maxTokens" to "2048",
                    "temp" to "0.7",
                    "topK" to "40",
                    "topP" to "0.9",
                    "min-p" to "0.05",
                    "mirostat" to "0",
                    "mirostatTau" to "5.0",
                    "mirostatEta" to "0.1",
                    "seed" to "-1",
                    "systemPrompt" to "You are a helpful AI assistant.",
                    "chatTemplate" to "",
                    "tags" to "smol,efficient,small"
                )
            ),
            
            CloudModel(
                modelName = "Gemma-2-2B-Instruct",
                modelDescription = "Google's Gemma 2 2B instruction model. High quality and efficient.",
                providerName = "GGUF",
                modelType = ModelType.TEXT,
                modelFileSize = "2.7 GB",
                modelFileLink = "https://huggingface.co/bartowski/gemma-2-2b-it-GGUF/resolve/main/gemma-2-2b-it-Q4_K_M.gguf",
                isLocal = false,
                metaData = mapOf(
                    "architecture" to "GEMMA2",
                    "threads" to "4",
                    "gpu-layers" to "0",
                    "ctxSize" to "8192",
                    "useMMAP" to "true",
                    "useMLOCK" to "false",
                    "maxTokens" to "2048",
                    "temp" to "0.7",
                    "topK" to "40",
                    "topP" to "0.9",
                    "min-p" to "0.05",
                    "mirostat" to "0",
                    "mirostatTau" to "5.0",
                    "mirostatEta" to "0.1",
                    "seed" to "-1",
                    "systemPrompt" to "You are a helpful AI assistant.",
                    "chatTemplate" to "",
                    "tags" to "gemma,google,quality"
                )
            )
        )
    }
    
    /**
     * Get Sherpa TTS models
     */
    fun getSherpaTTSModels(): List<CloudModel> {
        return listOf(
            CloudModel(
                modelName = "vits-piper-en_US-libritts-high",
                modelDescription = "High quality English TTS model",
                providerName = "SHERPA_TTS",
                modelType = ModelType.TTS,
                modelFileSize = "25 MB",
                modelFileLink = "https://example.com/sherpa-tts-en.zip",
                isLocal = false,
                metaData = mapOf(
                    "modelFileName" to "en_US-libritts-high.onnx",
                    "voicesFileName" to "espeak-ng-data",
                    "dataDirName" to "espeak-ng-data",
                    "voices" to """[{"id":"0","name":"Default"}]"""
                )
            )
        )
    }
    
    /**
     * Get Sherpa STT models
     */
    fun getSherpaSTTModels(): List<CloudModel> {
        return listOf(
            CloudModel(
                modelName = "sherpa-onnx-streaming-zipformer-en",
                modelDescription = "Streaming English speech recognition",
                providerName = "SHERPA_STT",
                modelType = ModelType.STT,
                modelFileSize = "30 MB",
                modelFileLink = "https://example.com/sherpa-stt-en.zip",
                isLocal = false,
                metaData = mapOf(
                    "encoder" to "encoder-epoch-99-avg-1.onnx",
                    "decoder" to "decoder-epoch-99-avg-1.onnx",
                    "tokens" to "tokens.txt"
                )
            )
        )
    }
    
    /**
     * Get OpenRouter models (API-based, no download)
     */
    fun getOpenRouterModels(): List<CloudModel> {
        return listOf(
            CloudModel(
                modelName = "GPT-4",
                modelDescription = "OpenAI's most capable model via OpenRouter",
                providerName = "OPENROUTER",
                modelType = ModelType.TEXT,
                modelFileSize = "N/A (API)",
                modelFileLink = "openai/gpt-4",
                isLocal = false,
                metaData = mapOf(
                    "modelId" to "openai/gpt-4",
                    "apiEndpoint" to "https://openrouter.ai/api/v1/chat/completions",
                    "maxTokens" to "8192",
                    "temperature" to "0.7",
                    "topP" to "1.0",
                    "supportsTools" to "true",
                    "supportsVision" to "false",
                    "supportsStreaming" to "true",
                    "promptCostPer1M" to "30.0",
                    "completionCostPer1M" to "60.0"
                )
            )
        )
    }
}