package com.mp.ai_engine.gguf;

import com.mp.ai_engine.gguf.IGGUFCallback;

/**
 * GGUF model operations for local LLM inference
 * Supports both text generation and embeddings
 */
interface IGGUFOperations {

    /**
     * Load a text generation model
     *
     * @param modelPath Full path to the .gguf model file
     * @param threads Number of CPU threads to use
     * @param ctxSize Context size (token limit)
     * @param temp Temperature for sampling (0.0-2.0)
     * @param topK Top-K sampling parameter
     * @param topP Top-P (nucleus) sampling parameter
     * @param minP Minimum P sampling parameter
     * @param mirostat Mirostat sampling mode (0=disabled, 1=v1, 2=v2)
     * @param mirostatTau Mirostat tau parameter
     * @param mirostatEta Mirostat eta parameter
     * @param seed Random seed (-1 for random)
     * @return true if loaded successfully
     */
    boolean loadTextModel(
        String config
    );

    /**
     * Load an embedding model
     *
     * @param modelPath Full path to the .gguf model file
     * @param threads Number of CPU threads to use
     * @param ctxSize Context size
     * @return true if loaded successfully
     */
    boolean loadEmbeddingModel(
        String modelPath,
        int threads,
        int ctxSize
    );

    /**
     * Unload the currently loaded model and free resources
     */
    void unloadModel();

    /**
     * Generate text from prompt
     * Tokens are written to the shared memory stream created by createTokenStream()
     *
     * @param prompt Input text prompt
     * @param maxTokens Maximum number of tokens to generate
     * @param toolsJson Optional JSON string defining available tools/functions
     * @param callback Callback for notifications (new tokens, tool calls, completion)
     */
    void generateText(
        String prompt,
        int maxTokens,
        String toolsJson,
        IGGUFCallback callback
    );

    /**
     * Generate embeddings for input text
     * Returns result directly (synchronous operation)
     *
     * @param input Text to embed
     * @return Float array of embeddings
     */
    float[] generateEmbedding(String input);

    /**
     * Check if a model is currently loaded
     */
    boolean isModelLoaded();

    /**
     * Get the path of the currently loaded model
     */
    String getCurrentModelPath();
}