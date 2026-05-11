package com.dark.tool_neuron.repo.research

import com.dark.tool_neuron.model.FetchedDoc
import com.dark.tool_neuron.model.ResearchContext
import com.dark.tool_neuron.model.StructuredDoc

interface ResearchModelClient {

    suspend fun generateQuestions(context: ResearchContext, max: Int): List<String>

    suspend fun compress(blobs: List<FetchedDoc>, question: String): String

    suspend fun finalDocument(
        allCompressed: String,
        question: String,
        sources: List<FetchedDoc>,
        iterationsUsed: Int,
        modelName: String,
        totalFetchedBytes: Long,
        durationMs: Long,
    ): StructuredDoc
}
