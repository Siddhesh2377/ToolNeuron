package com.dark.tool_neuron.service;

import com.dark.tool_neuron.service.IModelLoadCallback;
import com.dark.tool_neuron.service.IGenerationCallback;
import com.dark.tool_neuron.service.ITnEventCallback;
import com.dark.tool_neuron.service.IDiffusionCallback;
import android.graphics.Bitmap;

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

    // Summarize the conversation, drop the in-context KV cache, and emit the
    // summary text via the callback (same event vocabulary as generateMultiTurn).
    // Caller is expected to replace its in-memory chat history with a fresh
    // thread embedding the returned summary.
    void compactConversation(String messagesJson, int maxTokens, IGenerationCallback callback);

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
    String getMemoryStatsJson();
    String getVtCacheStatsJson();
    String getVlmKvCacheStatsJson();

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
    boolean loadVlmProjector(String path, int threads, int imageMinTokens, int imageMaxTokens);
    boolean loadVlmProjectorFromFd(in ParcelFileDescriptor pfd, int threads, int imageMinTokens, int imageMaxTokens);
    void releaseVlmProjector();
    boolean isVlmLoaded();
    String getVlmInfo();
    String getVlmDefaultMarker();
    void generateVlm(String messagesJson, in ParcelFileDescriptor[] imageFds, int maxTokens, int imageQuality, IGenerationCallback callback);
    boolean precomputeVlmVision(in ParcelFileDescriptor pfd, int imageQuality);

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
    String recognizeFromFd(in ParcelFileDescriptor pfd, int sampleCount, int sampleRate);

    // RAG (retrieval-augmented generation).
    // Runs inside the :inference process so a parser/embedding crash can't
    // take MainActivity down. PFDs are used for any payload >500 KB to avoid
    // binder TransactionTooLargeException (1 MB hard cap).
    boolean ragEnsureReady(int threads, int chunkSize, int chunkOverlap, int dims, int topK, int topN, boolean lateChunking);
    boolean ragLoadEmbeddingModel(String path);
    boolean ragLoadEmbeddingModelFromFd(in ParcelFileDescriptor pfd);
    void    ragUnloadEmbeddingModel();
    boolean ragIsLoaded();
    int     ragIngestBytes(in ParcelFileDescriptor pfd, long size, String mimeHint, String nameHint, String docId);
    String  ragExtractText(in ParcelFileDescriptor pfd, long size, String mimeHint, String nameHint);
    String  ragQuery(String query);
    String  ragQueryFiltered(String query, String docIdPrefix);
    int     ragRemoveDocument(String docId);
    void    ragClear();
    String  ragInfo();
    int     ragDocumentCount();
    int     ragChunkCount();
    int     ragExportIndex(in ParcelFileDescriptor pfd);
    int     ragImportIndex(in ParcelFileDescriptor pfd, long size);
    void    ragRelease();

    // Stable Diffusion (image generation).
    // Same isolation rationale as RAG: a QNN/MNN/UNet/VAE crash would
    // otherwise take MainActivity down. State flows are forwarded as
    // IDiffusionCallback events (one method per StateFlow).
    boolean sdEnsureRuntime(String runtimeDir, String tarXzSourcePath);
    boolean sdLoadDiffusionModel(
        String name, String modelDir,
        int textEmbeddingSize, boolean runOnCpu, boolean useCpuClip,
        boolean isPony, boolean safetyMode,
        int width, int height);
    boolean sdLoadUpscaler(String modelPath, boolean useMnn, boolean useOpenCL);
    void    sdGenerate(
        String prompt, String negativePrompt, int steps, float cfgScale,
        long seed, boolean hasSeed,
        int width, int height, String scheduler, boolean useOpenCL,
        String inputImage, String mask, float denoiseStrength,
        boolean showDiffusionProcess, int showDiffusionStride);
    void    sdCancelGeneration();
    void    sdResetGenerationState();
    void    sdUpscale(in Bitmap bitmap);
    void    sdStop();
    void    sdCleanup();
    boolean sdIsBackendRunning();
    String  sdGetSupportedResolutions(String modelDir, int baseW, int baseH);
    void    sdRegisterEvents(IDiffusionCallback cb);
    void    sdUnregisterEvents(IDiffusionCallback cb);

    // Errors / crash diagnostics — unified via :tn_security.
    // The service is the producer; clients subscribe with registerTnEvents.
    // Multiple callbacks supported (RemoteCallbackList).
    void registerTnEvents(ITnEventCallback cb);
    void unregisterTnEvents(ITnEventCallback cb);

    /**
     * Replay any crash JSON files left on disk by previous service-process
     * lifetimes (written by tn_security's signal handlers). The service
     * deletes each file as it ships it via onCrashReplay. Idempotent; safe
     * to call on every bind.
     */
    void replayPendingCrashes();
}
