package com.dark.tool_neuron.service;

import android.graphics.Bitmap;

/**
 * One-way callbacks for SD events. The service fan-outs every observed
 * StateFlow change into these calls. Clients reconstruct their local
 * MutableStateFlow from the kind discriminator + arg payload.
 *
 * Bitmaps are passed via the binder transaction (Parcelable copy through
 * ashmem). For intermediate-image streaming this is the same cost the
 * single-process implementation pays today — no extra copy introduced.
 */
oneway interface IDiffusionCallback {
    // BackendState kinds: 0=Idle, 1=Starting, 2=Running, 3=Error
    void onBackendState(int kind, String errorMessage);

    // GenerationState kinds: 0=Idle, 1=Progress, 2=Complete, 3=Error
    // For Progress: progress/currentStep/totalSteps populated; intermediate may be null.
    // For Complete: complete bitmap populated, seed/width/height meaningful.
    // For Error: errorMessage populated.
    void onGenerationState(
        int kind,
        float progress,
        int currentStep,
        int totalSteps,
        in Bitmap intermediate,
        in Bitmap complete,
        long seed,
        int width,
        int height,
        String errorMessage);

    void onIsGenerating(boolean generating);

    // UpscaleState kinds: 0=Idle, 1=Processing, 2=Complete, 3=Error
    void onUpscaleState(int kind, in Bitmap complete, int width, int height, int timeMs, String errorMessage);

    // RuntimeSetupState kinds:
    //   0=Idle, 1=Downloading, 2=CopyingAsset, 3=Extracting,
    //   4=CopyingSafetyChecker, 5=InitializingRuntime, 6=Complete, 7=Error
    void onRuntimeSetupState(int kind, long progressBytes, long totalBytes, String detail, String errorMessage);
}
