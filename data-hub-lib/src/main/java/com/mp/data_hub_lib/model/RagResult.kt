package com.mp.data_hub_lib.model

import com.mp.data_hub_lib.worker.Doc

data class RagResult(
    val docs: List<Doc>,
    val stats: GenerationStats
)