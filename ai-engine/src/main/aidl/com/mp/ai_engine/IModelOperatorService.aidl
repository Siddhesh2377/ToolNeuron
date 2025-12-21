package com.mp.ai_engine;

import com.mp.ai_engine.gguf.IGGUFOperations;
import com.mp.ai_engine.openrouter.IOpenRouterOperations;
import com.mp.ai_engine.diffusion.IDiffusionOperations;
import com.mp.ai_engine.installer.IModelInstaller;

/**
 * Main service interface for AI model operations
 * This is the entry point for all model-related operations
 */
interface IModelOperatorService {

    /**
     * Get GGUF operations interface for local LLM models
     */
    IGGUFOperations getGGUFOperations();

    /**
     * Get OpenRouter operations interface for cloud-based models
     */
    IOpenRouterOperations getOpenRouterOperations();

    /**
     * Get Diffusion operations interface for image generation
     */
    IDiffusionOperations getDiffusionOperations();

    /**
     * Get model installer interface for downloading/managing models
     */
    IModelInstaller getInstaller();

    /**
     * Get current service status as JSON
     * Returns info about loaded models, memory usage, etc.
     */
    String getServiceStatus();

    /**
     * Shutdown service and cleanup all resources
     */
    void shutdown();
}