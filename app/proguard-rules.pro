# ==================== ToolNeuron App ProGuard Rules ====================
# Surgical rules: only keep what actually breaks without the rule.
# Everything else gets obfuscated by R8.

# ========== Kotlinx Serialization ==========
# Keep generated serializers and companions for @Serializable classes
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.dark.tool_neuron.**$$serializer { *; }
-keepclassmembers class com.dark.tool_neuron.** {
    *** Companion;
}
-keepclasseswithmembers class com.dark.tool_neuron.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# Keep @Serializable annotated classes (field names used by JSON codec)
-keep @kotlinx.serialization.Serializable class com.dark.tool_neuron.** { *; }

# ========== Room Database ==========
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase {
    <init>(...);
}
# Type converters are found by reflection
-keep class com.dark.tool_neuron.models.converters.Converters { *; }

# ========== AIDL Interfaces (IPC binder) ==========
-keep class com.dark.tool_neuron.service.ILLMService { *; }
-keep class com.dark.tool_neuron.service.ILLMService$Stub { *; }
-keep class com.dark.tool_neuron.service.ILLMService$Stub$Proxy { *; }
-keep class com.dark.tool_neuron.service.IGgufGenerationCallback { *; }
-keep class com.dark.tool_neuron.service.IGgufGenerationCallback$Stub { *; }
-keep class com.dark.tool_neuron.service.IGgufGenerationCallback$Stub$Proxy { *; }
-keep class com.dark.tool_neuron.service.IModelLoadCallback { *; }
-keep class com.dark.tool_neuron.service.IModelLoadCallback$Stub { *; }
-keep class com.dark.tool_neuron.service.IModelLoadCallback$Stub$Proxy { *; }
-keep class com.dark.tool_neuron.service.IDiffusionGenerationCallback { *; }
-keep class com.dark.tool_neuron.service.IDiffusionGenerationCallback$Stub { *; }
-keep class com.dark.tool_neuron.service.IDiffusionGenerationCallback$Stub$Proxy { *; }

# ========== Hilt / Dagger ==========
-keep class dagger.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.internal.GeneratedComponent { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }
-keepclasseswithmembers class * {
    @dagger.* <fields>;
}
-keepclasseswithmembers class * {
    @javax.inject.* <fields>;
}
-keep class **_HiltModules { *; }
-keep class **_HiltComponents { *; }
-keep class **_ComponentTreeDeps { *; }
-keep @dagger.Module class com.dark.tool_neuron.di.** { *; }
-keep @javax.inject.Qualifier class com.dark.tool_neuron.di.** { *; }

# ========== Jetpack Compose ==========
# Only keep stability annotations for Compose compiler, not all Composable members
-keep @androidx.compose.runtime.Stable class * { *; }
-keep @androidx.compose.runtime.Immutable class * { *; }

# ========== Retrofit & OkHttp ==========
# Keep Retrofit interface methods (annotation-based dispatch)
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
# Keep Retrofit response data classes (Gson reflection)
-keep class com.dark.tool_neuron.network.HuggingFaceRepoResponse { *; }
-keep class com.dark.tool_neuron.network.HuggingFaceFileResponse { *; }

# ========== Gson ==========
-keep class * implements com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ========== Sealed class hierarchies used in type checks ==========
-keep class * extends com.dark.tool_neuron.models.state.AppState { *; }
-keep class * extends com.dark.tool_neuron.models.plugins.ToolState { *; }
-keep class * extends com.dark.tool_neuron.service.ModelDownloadService$DownloadState { *; }
-keep class * extends com.dark.tool_neuron.plugins.PluginExecutionResult { *; }
-keep class * extends com.dark.tool_neuron.worker.ModelLoadResult { *; }

# ========== Enums (values/valueOf accessed by reflection) ==========
-keepclassmembers enum * {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ========== Plugin API (interface used for dynamic dispatch) ==========
-keep class com.dark.tool_neuron.plugins.api.SuperPlugin { *; }
-keep class com.dark.plugins.api.** { *; }

# ========== JNI: AAR libraries with native code ==========
# ai_gguf - callbacks invoked from C++ via JNI
# CRITICAL: JNI calls methods on concrete implementing classes, not the interface.
# All classes implementing these callbacks must keep their method signatures intact.
-keep interface com.mp.ai_gguf.models.EmbeddingCallback { *; }
-keep interface com.mp.ai_gguf.models.StreamCallback { *; }
-keep class com.mp.ai_gguf.models.EmbeddingResult { *; }
-keep class * implements com.mp.ai_gguf.models.StreamCallback { *; }
-keep class * implements com.mp.ai_gguf.models.EmbeddingCallback { *; }
-keepclassmembers class com.mp.ai_gguf.** {
    native <methods>;
}
-keepclassmembers interface com.mp.ai_gguf.models.** {
    *;
}
# Keep callback anonymous classes created in engines
-keep class com.dark.tool_neuron.engine.EmbeddingEngine$** { *; }
-keep class com.dark.tool_neuron.engine.GGUFEngine$** { *; }

# ai_supertonic_tts - JNI callbacks
-keep interface com.mp.ai_supertonic_tts.callback.TTSCallback { *; }
-keep class * implements com.mp.ai_supertonic_tts.callback.TTSCallback { *; }
-keepclassmembers class com.mp.ai_supertonic_tts.** {
    native <methods>;
}
-keepclassmembers interface com.mp.ai_supertonic_tts.** {
    *;
}

# ai_sd - Stable Diffusion AAR
-keepclassmembers class com.dark.ai_sd.** {
    native <methods>;
}

# ai_module
-keepclassmembers class com.dark.ai_module.** {
    native <methods>;
}

# ========== ONNX Runtime ==========
-keep class ai.onnxruntime.** { *; }
-keepclassmembers class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# ========== Sentence Embeddings (reflection-loaded) ==========
-keep class com.ml.shubham0204.sentence_embeddings.** { *; }
-keep class com.model2vec.** { *; }

# ========== Worker data classes (model parsing, parcelable-like access) ==========
-keep class com.dark.tool_neuron.worker.DiffusionConfig { *; }
-keep class com.dark.tool_neuron.worker.DiffusionInferenceParams { *; }
-keep class com.dark.tool_neuron.worker.GGUFModelInfo { *; }
-keep class com.dark.tool_neuron.worker.DiffusionModelInfo { *; }
-keep class com.dark.tool_neuron.worker.ModelInfo { *; }

# ========== Document Parsing Libraries ==========
# Apache POI (heavy reflection internally)
-keep class org.apache.poi.** { *; }
-dontwarn org.apache.poi.**
-keep class org.apache.xmlbeans.** { *; }
-dontwarn org.apache.xmlbeans.**
-keep class org.openxmlformats.schemas.** { *; }
-dontwarn org.openxmlformats.schemas.**
-keep class schemaorg_apache_xmlbeans.** { *; }
-dontwarn schemaorg_apache_xmlbeans.**
-keep class com.microsoft.schemas.** { *; }
-dontwarn com.microsoft.schemas.**
-dontwarn org.apache.commons.compress.**
-dontwarn org.apache.commons.logging.**
-keep class org.apache.commons.compress.** { *; }

# PDFBox-Android (reflection-heavy)
-keep class com.tom_roush.pdfbox.** { *; }
-keepclassmembers class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**
-keep class com.tom_roush.harmony.** { *; }
-dontwarn com.tom_roush.harmony.**
-keep class org.apache.fontbox.** { *; }
-dontwarn org.apache.fontbox.**
-keep class org.apache.pdfbox.** { *; }
-dontwarn org.apache.pdfbox.**
-dontwarn javax.imageio.**
-dontwarn java.awt.**

# EPUB Library
-keep class nl.siegmann.epublib.** { *; }
-dontwarn nl.siegmann.epublib.**
-dontwarn org.slf4j.**
-dontwarn org.xmlpull.**

# ========== Native Methods (global catch-all) ==========
-keepclasseswithmembernames class * {
    native <methods>;
}

# ========== General Android Components ==========
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

# ========== Parcelable ==========
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# ========== @Keep annotation ==========
-keep @androidx.annotation.Keep class * { *; }
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}

# ========== Kotlin Metadata (needed for reflection-based libs) ==========
-keep class kotlin.Metadata { *; }

# ========== Debugging ==========
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ========== Strip Logging in Release ==========
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# ========== Suppress harmless warnings ==========
-dontwarn com.google.re2j.Matcher
-dontwarn com.google.re2j.Pattern
-dontwarn aQute.bnd.annotation.spi.ServiceConsumer
-dontwarn aQute.bnd.annotation.spi.ServiceProvider
-dontwarn aQute.bnd.annotation.baseline.BaselineIgnore
-dontwarn edu.umd.cs.findbugs.annotations.Nullable
-dontwarn edu.umd.cs.findbugs.annotations.SuppressFBWarnings
-dontwarn org.osgi.framework.Bundle
-dontwarn org.osgi.framework.BundleContext
-dontwarn org.osgi.framework.FrameworkUtil
-dontwarn org.osgi.framework.ServiceReference
-dontwarn org.osgi.framework.wiring.BundleRevision
-dontwarn okhttp3.**
-dontwarn okio.**
