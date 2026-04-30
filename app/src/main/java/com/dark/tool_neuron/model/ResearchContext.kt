package com.dark.tool_neuron.model

data class ResearchContext(
    val originalQuestion: String,
    val accumulatedSummary: String,
    val previousQuestions: List<String>,
    val iteration: Int,
)
