package com.mp.ai_engine.openrouter;

import com.mp.ai_engine.openrouter.IOpenRouterCallback;

/**
 * OpenRouter operations for cloud-based LLM inference
 * Uses pure callback approach (no shared memory needed due to network latency)
 */
interface IOpenRouterOperations {

    /**
     * Load/configure an OpenRouter model
     * This is lightweight - just stores configuration
     *
     * @param modelId OpenRouter model identifier (e.g., "anthropic/claude-3-sonnet")
     * @param endpoint API endpoint URL
     * @param temperature Sampling temperature (0.0-2.0)
     * @param topP Top-P sampling parameter
     * @param frequencyPenalty Frequency penalty (-2.0 to 2.0)
     * @param presencePenalty Presence penalty (-2.0 to 2.0)
     * @return true if configuration successful
     */
    boolean loadModel(
        String modelId,
        String endpoint,
        float temperature,
        float topP,
        float frequencyPenalty,
        float presencePenalty
    );

    /**
     * Clear current model configuration
     */
    void unloadModel();

    /**
     * Generate text via OpenRouter API
     * Tokens are streamed via callback (no shared memory)
     *
     * @param prompt Input text prompt
     * @param maxTokens Maximum tokens to generate
     * @param callback Callback for token streaming and events
     */
    void generateText(
        String prompt,
        int maxTokens,
        IOpenRouterCallback callback
    );

    /**
     * Check if a model is configured
     */
    boolean isModelLoaded();

    /**
     * Get the currently configured model ID
     */
    String getCurrentModelId();
}