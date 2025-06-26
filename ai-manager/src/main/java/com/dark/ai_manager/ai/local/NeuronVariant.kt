package com.dark.ai_manager.ai.local

enum class NeuronVariant( val modelName: String,  val  path: String){
    NVRouter("nv-router", "/storage/emulated/0/Download/Kodify_Nano_q4_k_s.gguf"),
    NVRunner("nv-router", "/storage/emulated/0/Download/Models/qwen2.5-coder-3b-instruct-q5_0.gguf"),
    NVGeneral("nv-general", "/storage/emulated/0/jan-nano-4b-Q4_K_M.gguf")
}