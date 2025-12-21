package com.mp.ai_engine.gguf;

/**
 * Callback interface for GGUF text generation
 * All methods are oneway (non-blocking) for performance
 */
oneway interface IGGUFCallback {

    /**
     * Called when a new token has been written to shared memory
     *
     * @param position Byte position in shared memory where token starts
     * @param length Length of the token in bytes
     */
    void onNewToken(String token);

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
    void onComplete(String finalResult);

    /**
     * Called when an error occurs during generation
     *
     * @param error Error message
     */
    void onError(String error);
}