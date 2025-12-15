package com.mp.ai_engine.models.llm_models

data class ModelSearchResult(
    val modelId: String,
    val modelName: String,
    val modelType: ModelType,
    val provider: ModelProvider,
    val ggufModel: GGUFDatabaseModel? = null,
    val openRouterModel: OpenRouterDatabaseModel? = null,
    val sherpaTTSModel: SherpaTTSDatabaseModel? = null,
    val sherpaSTTModel: SherpaSTTDatabaseModel? = null
)

enum class ModelProvider {
    GGUF,
    OPEN_ROUTER,
    SHERPA
}