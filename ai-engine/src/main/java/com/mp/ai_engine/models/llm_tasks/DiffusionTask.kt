package com.mp.ai_engine.models.llm_tasks

import android.graphics.Bitmap
import kotlinx.coroutines.CompletableDeferred

data class DiffusionTask(
    // === Identification ===
    val id: String,
    // Unique identifier for this generation task
    // Used for tracking, logging, and managing multiple concurrent tasks

    // === Text Prompts ===
    val prompt: String,
    // Main text description of what you want to generate
    // Example: "a beautiful landscape with mountains and a lake at sunset"
    // This guides the AI to create the desired image

    val negativePrompt: String = "",
    // What you DON'T want in the image
    // Example: "blurry, low quality, watermark, text"
    // Helps avoid unwanted elements or styles

    // === Generation Parameters ===
    val steps: Int = 20,
    // Number of denoising steps (iterations)
    // More steps = better quality but slower generation
    // Typical range: 15-50 steps
    // 20 is a good balance between speed and quality

    val cfg: Float = 7f,
    // Classifier-Free Guidance scale
    // Controls how strictly the AI follows your prompt
    // Lower (1-5): More creative/varied, less adherence to prompt
    // Higher (7-15): Stricter adherence to prompt, more "literal"
    // 7.0 is the sweet spot for most cases

    val seed: Long? = null,
    // Random seed for reproducibility
    // Same seed + same settings = identical image
    // null = random seed (different image each time)
    // Save the seed to recreate liked images with variations

    // === Image Dimensions ===
    val width: Int = 512,
    val height: Int = 512,
    // Output image dimensions in pixels
    // Must match what your model was trained for (usually 512x512, 768x768, or 1024x1024)
    // Larger sizes = more VRAM/memory needed and slower generation
    // Some models support non-square sizes (e.g., 512x768 for portrait)

    // === Advanced Features ===
    val denoiseStrength: Float = 0.6f,
    // Only used for img2img (when inputImage is provided)
    // Controls how much to change the input image
    // 0.0 = no change (copy input)
    // 1.0 = completely new image (ignore input)
    // 0.4-0.7 = good range for variations
    // Higher values = more creative freedom, less resemblance to input

    val useOpenCL: Boolean = true,
    // Whether to use OpenCL GPU acceleration (for MNN models)
    // true = use GPU (faster, more power usage)
    // false = use CPU only (slower, less power)
    // Only applicable when running on CPU mode (use_mnn = true)

    val scheduler: String = "dpm",
    // Denoising algorithm/scheduler to use
    // Options: "dpm" (DPM-Solver++) or "euler_a" (Euler Ancestral)
    // DPM: Generally better quality, good default
    // Euler_a: Sometimes better for certain styles, slightly different results
    // Affects how the image is progressively denoised

    // === Image-to-Image Features ===
    val inputImage: String? = null,
    // Base64-encoded input image for img2img generation
    // When provided: modifies this image according to prompt
    // When null: generates from scratch (text-to-image)
    // Use cases: style transfer, variations, upscaling, modifications

    val maskImage: String? = null,
    // Base64-encoded mask image for inpainting
    // White areas = modify/regenerate
    // Black areas = keep original
    // Used with inputImage to edit specific parts of an image
    // Example: remove object, change background, fill in missing areas

    // === Callbacks & Result ===
    val events: DMStreamEvents,
    // Interface for real-time event callbacks:
    // - onProgress(): Called each step with progress updates
    // - onPreview(): Called periodically with preview images (if enabled)
    // - onComplete(): Called when generation finishes with final image
    // - onError(): Called if generation fails
    // Allows UI to update in real-time during generation

    val result: CompletableDeferred<DiffusionResult>
    // Kotlin coroutine deferred result
    // Allows async/await pattern: val result = task.result.await()
    // Completes with final DiffusionResult (bitmap + seed) or exception
    // Enables both callback-style (events) and async-style (result) handling
)
data class DiffusionResult(
    val bitmap: Bitmap,
    val seed: Long?
)

interface DMStreamEvents {
    fun onProgress(p: Float, step: Int, totalSteps: Int)
    fun onPreview(previewBitmap: Bitmap, step: Int, totalSteps: Int) // Add this
    fun onComplete(bitmap: Bitmap, seed: Long?)
    fun onError(error: String)
}