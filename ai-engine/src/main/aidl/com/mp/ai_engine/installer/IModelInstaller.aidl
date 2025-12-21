package com.mp.ai_engine.installer;

import com.mp.ai_engine.installer.IInstallerCallback;

/**
 * Model installation and management operations
 * Handles downloading, installing, and deleting models
 */
interface IModelInstaller {

    /**
     * Install a GGUF model from a download URL
     *
     * @param modelName Name/identifier for the model
     * @param downloadUrl URL to download the model from
     * @param callback Callback for progress updates and completion
     */
    void installGGUFModel(
        String modelName,
        String downloadUrl,
        IInstallerCallback callback
    );

    /**
     * Install a Diffusion model from a download URL
     *
     * @param modelName Name/identifier for the model
     * @param downloadUrl URL to download the model from
     * @param callback Callback for progress updates and completion
     */
    void installDiffusionModel(
        String modelName,
        String downloadUrl,
        IInstallerCallback callback
    );

    /**
     * Install an OpenRouter model (just registers configuration)
     * No actual download needed for cloud models
     *
     * @param modelId OpenRouter model identifier
     * @param modelName Display name for the model
     */
    void installOpenRouterModel(
        String modelId,
        String modelName
    );

    /**
     * Install a GGUF model from local storage
     *
     * @param modelName Name/identifier for the model
     * @param localPath Path to the local model file
     * @param callback Callback for progress and completion
     */
    void installLocalGGUFModel(
        String modelName,
        String localPath,
        IInstallerCallback callback
    );

    /**
     * Install a Diffusion model from local storage
     *
     * @param modelName Name/identifier for the model
     * @param localPath Path to the local model folder
     * @param callback Callback for progress and completion
     */
    void installLocalDiffusionModel(
        String modelName,
        String localPath,
        IInstallerCallback callback
    );

    /**
     * Delete an installed model
     *
     * @param modelId Unique identifier of the model to delete
     * @param callback Callback for completion status
     */
    void deleteModel(String modelId, IInstallerCallback callback);

    /**
     * Check if a model is installed
     *
     * @param modelId Model identifier to check
     * @return true if the model is installed
     */
    boolean isModelInstalled(String modelId);

    /**
     * Get the file system path of an installed model
     *
     * @param modelId Model identifier
     * @return Full path to the model file/folder, or null if not found
     */
    String getModelPath(String modelId);

    /**
     * Get the disk size of an installed model
     *
     * @param modelId Model identifier
     * @return Size in bytes, or 0 if not found
     */
    long getModelSize(String modelId);

    /**
     * Cancel an ongoing download
     *
     * @param downloadUrl URL of the download to cancel
     */
    void cancelDownload(String downloadUrl);

    /**
     * Get list of all installed model IDs
     * Returns JSON array of model IDs
     */
    String getInstalledModels();
}