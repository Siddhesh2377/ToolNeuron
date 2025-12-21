package com.mp.ai_engine.diffusion;

import android.graphics.Bitmap;

/**
 * Callback interface for diffusion image generation
 * Provides progress updates, preview images, and final result
 * All methods are oneway (non-blocking)
 */
oneway interface IDiffusionCallback {

    /**
     * Called periodically during generation to report progress
     *
     * @param progress Overall progress (0.0 to 1.0)
     * @param step Current diffusion step
     * @param totalSteps Total number of steps
     */
    void onProgress(float progress, int step, int totalSteps);

    /**
     * Called with preview images during generation (if enabled)
     * Preview images are lower quality for performance
     *
     * @param previewImage HardwareBuffer containing preview image
     * @param step Current step when preview was generated
     * @param totalSteps Total steps
     */
    void onPreview(in Bitmap previewImage, int step, int totalSteps);

    /**
     * Called when generation is complete with final image
     *
     * @param finalImage HardwareBuffer containing the final high-quality image
     * @param seed The seed used for generation (useful for reproducing results)
     */
    void onComplete(in Bitmap finalImage, long seed);

    /**
     * Called when an error occurs during generation
     *
     * @param error Error message
     */
    void onError(String error);
}