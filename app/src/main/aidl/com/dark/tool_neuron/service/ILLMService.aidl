package com.dark.tool_neuron.service;

import com.dark.tool_neuron.service.IGgufGenerationCallback;
import com.dark.tool_neuron.service.IModelLoadCallback;
import com.dark.tool_neuron.service.IDiffusionGenerationCallback;

interface ILLMService {

    //Gguf
    void loadGgufModel(String modelPath, String modelName, String loadingParams, String inferenceParams, IModelLoadCallback callback);
    void loadGgufModelFromFd(in ParcelFileDescriptor pfd, String modelName, String loadingParams, String inferenceParams, IModelLoadCallback callback);
    void generateGguf(String prompt, int maxTokens, IGgufGenerationCallback callback);
    void stopGenerationGguf();
    void unloadModelGguf();
    String getModelInfoGguf();
    boolean setToolsJsonGguf(String toolsJson);
    void clearToolsGguf();

    // Multi-turn tool calling
    boolean enableToolCallingGguf(String toolsJson, int grammarMode, boolean useTypedGrammar);
    void generateGgufMultiTurn(String messagesJson, int maxTokens, IGgufGenerationCallback callback);
    void setGrammarModeGguf(int mode);
    void setTypedGrammarGguf(boolean enabled);
    boolean isToolCallingSupportedGguf();

    // Persona engine: dynamic sampling + logit bias + control vectors
    boolean updateSamplerParamsGguf(String paramsJson);
    boolean setLogitBiasGguf(String biasJson);
    boolean loadControlVectorsGguf(String vectorsJson);
    boolean clearControlVectorGguf();

    // KV cache state persistence
    long getStateSizeGguf();
    boolean stateSaveToFileGguf(String path);
    boolean stateLoadFromFileGguf(String path);

    // New optimizations
    void setSpeculativeDecodingGguf(boolean enabled, int nDraft, int ngramSize);
    void setPromptCacheDirGguf(String path);
    boolean warmUpGguf();
    boolean supportsThinkingGguf();
    void setThinkingEnabledGguf(boolean enabled);
    float getContextUsageGguf();

    // Context window tracking
    String getContextInfoGguf(String prompt);

    // Character engine
    boolean setPersonalityGguf(String personalityJson);
    boolean setMoodGguf(int mood);
    boolean setCustomMoodGguf(float tempMod, float topPMod, float repPenaltyMod);
    String getCharacterContextGguf();
    String buildPromptGguf(String userPrompt);
    boolean setUncensoredGguf(boolean enabled);
    boolean isUncensoredGguf();

    // Upscaler
    void loadUpscaler(String modelPath, IModelLoadCallback callback);
    void releaseUpscaler();

    //Diffusion
    void loadDiffusionModel(
        String name,
        String modelDir,
        int height,
        int width,
        int textEmbeddingSize,
        boolean runOnCpu,
        boolean useCpuClip,
        boolean isPony,
        int httpPort,
        boolean safetyMode,
        IModelLoadCallback callback
    );

    void generateDiffusionImage(
        String prompt,
        String negativePrompt,
        int steps,
        float cfgScale,
        long seed,
        int width,
        int height,
        String scheduler,
        boolean useOpenCL,
        String inputImage,
        String mask,
        float denoiseStrength,
        boolean showDiffusionProcess,
        int showDiffusionStride,
        IDiffusionGenerationCallback callback
    );

    void stopGenerationDiffusion();
    void restartDiffusionBackend(IModelLoadCallback callback);
    void stopDiffusionBackend();
    String getDiffusionBackendState();
    String getCurrentDiffusionModel();
}
