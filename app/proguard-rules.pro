# -- Keep all NeuroVerse model classes and their full members --
-keep class com.dark.tool_neuron.model.** { *; }
-keep class com.dark.tool_neuron.models.data.HuggingFaceModel { *; }
-keep class com.dark.tool_neuron.models.data.HFModelRepository { *; }
-keep class com.dark.tool_neuron.network.** { *; }
-keep class com.dark.tool_neuron.models.data.ModelType { *; }
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
-keep class com.dark.ai_module.model.** { *; }

# Keep AI core classes (from AAR)
-keep class com.mp.ai_core.helpers.** { *; }
-keep class com.mp.ai_core.services.** { *; }
-keep class com.mp.ai_core.** { *; }