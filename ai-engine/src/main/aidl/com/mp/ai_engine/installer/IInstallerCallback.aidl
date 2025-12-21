package com.mp.ai_engine.installer;

/**
 * Callback interface for model installation operations
 * Provides progress updates and completion status
 * All methods are oneway (non-blocking)
 */
oneway interface IInstallerCallback {

    /**
     * Called periodically during download/installation to report progress
     *
     * @param progress Overall progress (0.0 to 1.0)
     * @param downloaded Bytes downloaded so far
     * @param total Total bytes to download
     */
    void onProgress(float progress, long downloaded, long total);

    /**
     * Called when installation completes successfully
     *
     * @param modelPath Full path to the installed model
     */
    void onComplete(String modelPath);

    /**
     * Called when installation fails
     *
     * @param error Error message describing what went wrong
     */
    void onError(String error);
}