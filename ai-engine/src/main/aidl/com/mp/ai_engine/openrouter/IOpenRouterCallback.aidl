package com.mp.ai_engine.openrouter;

/**
 * Callback interface for OpenRouter text generation
 * Uses direct token passing (no shared memory needed)
 * All methods are oneway (non-blocking)
 */
oneway interface IOpenRouterCallback {

    /**
     * Called when a new token is received from the API
     * Token is passed directly through AIDL
     *
     * @param token The generated token/text chunk
     */
    void onToken(String token);

    /**
     * Called when the model requests a tool/function call
     *
     * @param toolName Name of the tool to call
     * @param toolArgs JSON string of tool arguments
     */
    void onToolCall(String toolName, String toolArgs);

    /**
     * Called when text generation is complete
     */
    void onComplete();

    /**
     * Called when an error occurs
     *
     * @param error Error message
     */
    void onError(String error);
}