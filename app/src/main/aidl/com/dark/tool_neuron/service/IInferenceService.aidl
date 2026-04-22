package com.dark.tool_neuron.service;

import com.dark.tool_neuron.service.IModelLoadCallback;
import com.dark.tool_neuron.service.IGenerationCallback;

interface IInferenceService {

    // Model lifecycle
    void loadModel(String modelPath, String configJson, IModelLoadCallback callback);
    void loadModelFromFd(in ParcelFileDescriptor pfd, String configJson, IModelLoadCallback callback);
    void unloadModel();
    boolean isModelLoaded();
    String getModelInfo();

    // Generation
    void generate(String prompt, int maxTokens, IGenerationCallback callback);
    void generateMultiTurn(String messagesJson, int maxTokens, IGenerationCallback callback);
    void stopGeneration();

    // Sampling
    void setSampling(String samplingJson);
    void setSystemPrompt(String prompt);
    void setChatTemplate(String template);

    // Tool calling
    boolean isToolCallingSupported();
    void setToolsJson(String toolsJson);
    void enableToolCalling(String toolsJson, int grammarMode, boolean useTypedGrammar);
    void clearTools();

    // Context
    float getContextUsage();
    String getContextInfo(String prompt);

    // Thinking
    boolean supportsThinking();
    void setThinkingEnabled(boolean enabled);

    // Optimizations
    void setPromptCacheDir(String path);
    boolean warmUp();
    void setThreadMode(int mode);

    // KV state persistence
    long getStateSize();
    boolean stateSaveToFile(String path);
    boolean stateLoadFromFile(String path);

    // VLM
    boolean loadVlmProjector(String path, int threads);
    boolean loadVlmProjectorFromFd(in ParcelFileDescriptor pfd, int threads);
    void releaseVlmProjector();
    boolean isVlmLoaded();
    String getVlmInfo();
    void generateVlm(String messagesJson, int maxTokens, IGenerationCallback callback);

    // TTS
    boolean loadTtsModel(String configJson);
    void unloadTtsModel();
    boolean isTtsLoaded();
    float[] synthesize(String text, int speakerId, float speed);
    int getTtsSampleRate();

    // STT
    boolean loadSttModel(String configJson);
    void unloadSttModel();
    boolean isSttLoaded();
    String recognize(in float[] samples, int sampleRate);
}
