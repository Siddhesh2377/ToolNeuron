package com.mp.ai_engine.models.llm_models

import com.mp.ai_engine.models.image_models.DiffusionDatabaseModel

data class ModelSearchResult(
    val modelId: String,
    val modelName: String,
    val modelType: ModelType,
    val provider: ModelProvider,
    val ggufModel: GGUFDatabaseModel? = null,
    val openRouterModel: OpenRouterDatabaseModel? = null,
    val sherpaTTSModel: SherpaTTSDatabaseModel? = null,
    val sherpaSTTModel: SherpaSTTDatabaseModel? = null,
    val diffusionModel: DiffusionDatabaseModel? = null
)

enum class ModelProvider {
    GGUF, OPEN_ROUTER, SHERPA, DIFFUSION
}