# -- Keep all NeuroVerse model classes and their full members --
-keep class com.dark.tool_neuron.model.** { *; }
-keep class com.dark.tool_neuron.activity.** { *; }
-keep class com.dark.tool_neuron.viewModel.** { *; }
-keep class com.dark.tool_neuron.ui.** { *; }
-keep class com.dark.plugins.api.** { *; }

# Keep Composable functions
-keepclassmembers class ** {
    @androidx.compose.runtime.Composable *;
}

# Keep classes with @Keep annotation
-keep @androidx.annotation.Keep class * { *; }

# Keep AI module classes
-keep class com.dark.ai_module.model.** { *; }

# Keep AI core classes (from AAR)
-keep class com.mp.ai_core.audio.stt.** { *; }
-keep class com.mp.ai_core.audio.tts.** { *; }
-keep class com.mp.ai_core.helpers.** { *; }
-keep class com.mp.ai_core.services.** { *; }
-keep class com.mp.ai_core.** { *; }

# ============================================
# Keep sherpa-onnx classes (CRITICAL - ADD THIS!)
# ============================================
-keep class com.k2fsa.sherpa.onnx.** { *; }

# Keep Kotlin data classes for sherpa-onnx
-keepclassmembers class com.k2fsa.sherpa.onnx.** {
    <init>(...);
    *** component*();
    *** copy(...);
}

# Keep Kotlin top-level functions (like getFeatureConfig)
-keep class com.k2fsa.sherpa.onnx.**Kt { *; }

# Keep native methods (sherpa-onnx uses JNI)
-keepclasseswithmembernames class com.k2fsa.sherpa.onnx.** {
    native <methods>;
}

# Keep all fields and methods in sherpa-onnx classes
-keepclassmembers class com.k2fsa.sherpa.onnx.** {
    public *;
    private *;
    protected *;
}

# Prevent obfuscation of sherpa-onnx classes
-keepnames class com.k2fsa.sherpa.onnx.** { *; }