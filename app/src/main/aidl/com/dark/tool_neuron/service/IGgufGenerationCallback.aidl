package com.dark.tool_neuron.service;

interface IGgufGenerationCallback {
    void onToken(String token);
    void onToolCall(String name, String args);
    void onMetrics(float tps, float ttftMs, float totalMs, int tokensEvaluated, int tokensPredicted, float modelMB, float ctxMB, float peakMB, float memPct);
    void onProgress(float progress);
    void onDone();
    void onError(String message);
}
