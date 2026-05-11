package com.dark.tool_neuron.ui.screens.guide

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import com.dark.tool_neuron.ui.icons.TnIcons

@Composable
fun GuideImagesScreen(innerPadding: PaddingValues) {
    GuideDetailLayout(
        innerPadding = innerPadding,
        icon = TnIcons.Photo,
        lede = "Generate images on the device with Stable Diffusion. The engine ships as a separate AAR (:ai_sd) and runs through QNN on Qualcomm NPUs when the SoC supports it, falling back to MNN on CPU/GPU otherwise. Models live alongside the regular chat catalog.",
        steps = listOf(
            GuideStep(
                title = "First-time runtime setup",
                body = "Image generation needs a one-time setup that extracts the QNN runtime libs and the safety checker. The Images screen prompts you on first use — let it finish before downloading models. The extract progress is reported per-file so you can see what's happening.",
            ),
            GuideStep(
                title = "Pick a model variant",
                body = "Models in the store ship in variants for different SoCs: 8gen1 (SM8450), 8gen2 (SM8550), and 'min' (anything Gen 3 or newer, also runs below 8gen1). The store filters automatically to what your device can actually run — if a model has no compatible variant, it's hidden.",
            ),
            GuideStep(
                title = "Generate an image",
                body = "Drawer → Images. Type a prompt, optionally a negative prompt, pick step count (default 20 for fast preview, 30+ for final) and CFG scale, hit Generate. The UNet step latency is the dominant cost — about 2 s/step on a 7s Gen 3 with the min variant. Progress is per-step.",
            ),
            GuideStep(
                title = "Save or share",
                body = "Once a generation completes, the result lives in the gallery for the current session. Tap to enlarge, long-press to save to your Pictures folder or share. Nothing leaves the device unless you share it explicitly.",
            ),
        ),
        tips = listOf(
            "CLIP runs on the CPU even with QNN — it's small and doesn't benefit from the NPU. The UNet is where the NPU helps most.",
            "Batch size is fixed at 1. There's no benefit to batching on mobile, and VTCM is tight (2 MB on Gen 3).",
            "Image generation is heavy. Plug in or expect noticeable battery drain on multi-image sessions.",
        ),
    )
}
