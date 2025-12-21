package com.mp.ai_engine.diffusion;

import com.mp.ai_engine.diffusion.IDiffusionCallback;

/**
 * Diffusion model operations for image generation
 * Supports text-to-image and image-to-image generation
 */
interface IDiffusionOperations {

    /**
     * Load a diffusion model
     *
     * @param modelFolder Path to folder containing model files
     * @param modelId Unique model identifier
     * @param runOnCpu If true, run on CPU; if false, use NPU/GPU acceleration
     * @param useCpuClip If true, run CLIP on CPU (hybrid mode)
     * @param width Default image width
     * @param height Default image height
     * @param textEmbeddingSize Text embedding dimension (768 for SD1.x, 1024 for SD2.x)
     * @return true if loaded successfully
     */
    boolean loadModel(
        String config
    );

    /**
     * Unload current model and free resources
     */
    void unloadModel();

    /**
     * Generate image from text prompt (text-to-image)
     *
     * @param prompt Positive text prompt
     * @param negativePrompt Negative prompt (what to avoid)
     * @param steps Number of diffusion steps
     * @param cfg Classifier-free guidance scale
     * @param width Output image width
     * @param height Output image height
     * @param denoiseStrength Denoising strength (0.0-1.0)
     * @param useOpenCL Use OpenCL acceleration if available
     * @param scheduler Scheduler type (e.g., "euler_a", "ddim")
     * @param seed Random seed (-1 for random)
     * @param callback Callback for progress updates and result
     * @return HardwareBuffer containing the generated image (async, also sent via callback)
     */
    void generateImage(
        String prompt,
        String negativePrompt,
        int steps,
        float cfg,
        int width,
        int height,
        float denoiseStrength,
        boolean useOpenCL,
        String scheduler,
        long seed,
        IDiffusionCallback callback
    );

    /**
     * Generate image from input image (image-to-image)
     *
     * @param prompt Positive text prompt
     * @param negativePrompt Negative prompt
     * @param inputImageBase64 Base64-encoded input image (RGB format)
     * @param maskImageBase64 Optional base64-encoded mask (null if not using inpainting)
     * @param steps Number of diffusion steps
     * @param cfg Classifier-free guidance scale
     * @param width Output image width
     * @param height Output image height
     * @param denoiseStrength Denoising strength (0.0-1.0, higher = more change)
     * @param useOpenCL Use OpenCL acceleration
     * @param scheduler Scheduler type
     * @param seed Random seed
     * @param callback Callback for progress and result
     */
    void generateImageFromImage(
        String prompt,
        String negativePrompt,
        String inputImageBase64,
        String maskImageBase64,
        int steps,
        float cfg,
        int width,
        int height,
        float denoiseStrength,
        boolean useOpenCL,
        String scheduler,
        long seed,
        IDiffusionCallback callback
    );

    /**
     * Check if a model is currently loaded
     */
    boolean isModelLoaded();

    /**
     * Get the currently loaded model ID
     */
    String getCurrentModelId();
}