# ==================== NeuroVerse App ProGuard Rules ====================

# -- Core App Classes --
-keep class com.dark.tool_neuron.model.** { *; }
-keep class com.dark.tool_neuron.models.** { *; }
-keep class com.dark.tool_neuron.network.** { *; }
-keep class com.dark.tool_neuron.activity.** { *; }
-keep class com.dark.tool_neuron.viewmodel.** { *; }
-keep class com.dark.tool_neuron.viewModel.** { *; }
-keep class com.dark.tool_neuron.ui.** { *; }
-keep class com.dark.tool_neuron.repo.** { *; }
-keep class com.dark.tool_neuron.worker.** { *; }
-keep class com.dark.tool_neuron.engine.** { *; }
-keep class com.dark.tool_neuron.service.** { *; }
-keep class com.dark.tool_neuron.util.** { *; }
-keep class com.dark.tool_neuron.utils.** { *; }
-keep class com.dark.tool_neuron.plugins.** { *; }
-keep class com.dark.tool_neuron.tts.** { *; }
-keep class com.dark.tool_neuron.di.** { *; }
-keep class com.dark.tool_neuron.vault.** { *; }
-keep class com.dark.tool_neuron.data.** { *; }
-keep class com.dark.tool_neuron.data_packs.** { *; }
-keep class com.dark.tool_neuron.state.** { *; }
-keep class com.dark.tool_neuron.global.** { *; }
-keep class com.dark.plugins.api.** { *; }

# Network data classes (for Retrofit)
-keep class com.dark.tool_neuron.network.HuggingFaceRepoResponse { *; }
-keep class com.dark.tool_neuron.network.HuggingFaceFileResponse { *; }

# Worker data classes (for model parsing and loading)
-keep class com.dark.tool_neuron.worker.DiffusionConfig { *; }
-keep class com.dark.tool_neuron.worker.DiffusionInferenceParams { *; }
-keep class com.dark.tool_neuron.worker.GGUFModelInfo { *; }
-keep class com.dark.tool_neuron.worker.DiffusionModelInfo { *; }
-keep class com.dark.tool_neuron.worker.ModelInfo { *; }
-keep class * extends com.dark.tool_neuron.worker.ModelLoadResult { *; }

# -- Data Classes & Enums --
-keepclassmembers class com.dark.tool_neuron.models.** {
    *;
}

# Explicit model subdirectories (for better serialization protection)
-keep class com.dark.tool_neuron.models.data.** { *; }
-keep class com.dark.tool_neuron.models.engine_schema.** { *; }
-keep class com.dark.tool_neuron.models.enums.** { *; }
-keep class com.dark.tool_neuron.models.messages.** { *; }
-keep class com.dark.tool_neuron.models.plugins.** { *; }
-keep class com.dark.tool_neuron.models.state.** { *; }
-keep class com.dark.tool_neuron.models.vault.** { *; }

# Keep sealed classes and their subclasses
-keep class * extends com.dark.tool_neuron.models.state.AppState { *; }
-keep class * extends com.dark.tool_neuron.models.plugins.ToolState { *; }
-keep class * extends com.dark.tool_neuron.service.ModelDownloadService$DownloadState { *; }
-keep class * extends com.dark.tool_neuron.plugins.PluginExecutionResult { *; }

-keepclassmembers enum * {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# -- Room Database --
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase {
    <init>(...);
}
-keep class com.dark.tool_neuron.database.** { *; }
-keep class com.dark.tool_neuron.models.table_schema.** { *; }
-keep class com.dark.tool_neuron.models.converters.** { *; }

# -- Kotlinx Serialization --
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
-keep @kotlinx.serialization.Serializable class com.dark.tool_neuron.** { *; }

# -- Jetpack Compose --
-keepclassmembers class ** {
    @androidx.compose.runtime.Composable *;
}
-keep @androidx.compose.runtime.Stable class * { *; }
-keep @androidx.compose.runtime.Immutable class * { *; }

# -- Hilt/Dagger --
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

# -- ONNX Runtime --
-keep class ai.onnxruntime.** { *; }
-keepclassmembers class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# -- Sentence Embeddings --
-keep class com.ml.shubham0204.sentence_embeddings.** { *; }
-keep class com.model2vec.** { *; }

# -- AI Core & GGUF --
-keep class com.mp.ai_core.** { *; }
-keep class com.dark.gguf_lib.** { *; }
-keep class com.dark.ai_module.** { *; }
-keep class com.dark.ai_sd.** { *; }
-keep class com.android.tools.mlkit.** { *; }

# -- Supertonic TTS (AAR with JNI callbacks) --
-keep class com.mp.ai_supertonic_tts.** { *; }
-keep interface com.mp.ai_supertonic_tts.callback.TTSCallback { *; }
-keepclassmembers class com.mp.ai_supertonic_tts.** {
    native <methods>;
    public <methods>;
    public <fields>;
}
-keepclassmembers interface com.mp.ai_supertonic_tts.** {
    *;
}

# Keep all callback interfaces and their methods (critical for JNI)
-keep interface com.dark.gguf_lib.models.EmbeddingCallback { *; }
-keep interface com.dark.gguf_lib.models.StreamCallback { *; }
-keep class com.dark.gguf_lib.models.EmbeddingResult { *; }
-keep class com.dark.gguf_lib.models.** { *; }

# Keep all methods that might be called from native code
-keepclassmembers class com.dark.gguf_lib.** {
    native <methods>;
    public <methods>;
    public <fields>;
}

# Keep callback implementations (created as anonymous classes)
-keep class com.dark.tool_neuron.engine.EmbeddingEngine$** { *; }
-keepclassmembers class com.dark.tool_neuron.engine.EmbeddingEngine {
    *;
}

# Prevent callback interface methods from being renamed or removed
-keepclassmembers interface com.dark.gguf_lib.models.** {
    *;
}

# -- Plugins (data classes used in type checks, sealed hierarchies, SuperPlugin interface) --
-keep class com.dark.tool_neuron.plugins.api.SuperPlugin { *; }
-keepclassmembers class com.dark.tool_neuron.plugins.** {
    *;
}
-keep class com.dark.tool_neuron.plugins.PluginManager$MultiTurnToolResult { *; }

# -- Vault (serialization helper + data classes) --
-keepclassmembers class com.dark.tool_neuron.vault.** {
    *;
}

# -- Hilt DI modules & qualifiers --
-keep @dagger.Module class com.dark.tool_neuron.di.** { *; }
-keep @javax.inject.Qualifier class com.dark.tool_neuron.di.** { *; }

# -- RAG & NeuronGraph --
-keep class com.dark.tool_neuron.neuron_example.** { *; }
-keepclassmembers class com.dark.tool_neuron.neuron_example.** {
    *;
}

# Keep NeuronGraph serializable classes explicitly
-keep class com.dark.tool_neuron.neuron_example.GraphSettings { *; }
-keep class com.dark.tool_neuron.neuron_example.SourceType { *; }
-keep class com.dark.tool_neuron.neuron_example.EdgeType { *; }
-keep class com.dark.tool_neuron.neuron_example.NeuronEdge { *; }
-keep class com.dark.tool_neuron.neuron_example.NodeMetadata { *; }
-keep class com.dark.tool_neuron.neuron_example.NeuronNode { *; }
-keep class com.dark.tool_neuron.neuron_example.QueryResult { *; }
-keep class com.dark.tool_neuron.neuron_example.GraphStats { *; }

# -- NeuronPacket Library --
-keep class com.neuronpacket.** { *; }
-keepclassmembers class com.neuronpacket.** {
    *;
}

# -- MemoryVault Library --
-keep class com.memoryvault.** { *; }
-keepclassmembers class com.memoryvault.** {
    *;
}

# -- UMS (Unified Memory System with JNI) --
-keep class com.dark.ums.** { *; }
-keepclassmembers class com.dark.ums.** {
    native <methods>;
    public <methods>;
    public <fields>;
}

# -- System Encryptor (native encryption with JNI) --
-keep class com.dark.system_encryptor.** { *; }
-keepclassmembers class com.dark.system_encryptor.** {
    native <methods>;
    public <methods>;
    public <fields>;
}

# -- File Operations --
-keep class com.dark.file_ops.** { *; }

# -- Document Parsing Libraries --

# Apache POI (Excel, Word)
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

# PDFBox-Android (PDF)
-keep class com.tom_roush.pdfbox.** { *; }
-keepclassmembers class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**
-keep class com.tom_roush.harmony.** { *; }
-dontwarn com.tom_roush.harmony.**
-dontwarn org.apache.commons.logging.**
-dontwarn javax.imageio.**
-dontwarn java.awt.**

# Keep PDFBox classes used by reflection
-keep class org.apache.fontbox.** { *; }
-keep class org.apache.pdfbox.** { *; }
-dontwarn org.apache.fontbox.**
-dontwarn org.apache.pdfbox.**

# Keep DocumentParser and its methods
-keep class com.dark.tool_neuron.util.DocumentParser { *; }
-keepclassmembers class com.dark.tool_neuron.util.DocumentParser {
    *;
}

# EPUB Library
-keep class nl.siegmann.epublib.** { *; }
-dontwarn nl.siegmann.epublib.**
-dontwarn org.slf4j.**
-dontwarn org.xmlpull.**

# Jsoup optional dependencies (RE2J regex library)
-dontwarn com.google.re2j.Matcher
-dontwarn com.google.re2j.Pattern

# Log4j2 (suppress optional OSGi and aQute.bnd dependencies)
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

# -- Retrofit & OkHttp --
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# -- Gson --
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# -- Keep Annotation --
-keep @androidx.annotation.Keep class * { *; }
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}

# -- Native Methods --
-keepclasseswithmembernames class * {
    native <methods>;
}

# -- Prevent R8 repackaging of JNI-accessed classes (AGP 9.1.0+ default) --
# Native code uses FindClass() with full package paths — repackaging breaks these lookups
-keeppackagenames com.dark.gguf_lib.**
-keeppackagenames com.dark.ai_sd.**
-keeppackagenames com.dark.ums.**
-keeppackagenames com.dark.system_encryptor.**
-keeppackagenames com.mp.ai_supertonic_tts.**

# -- Keep Line Numbers for Debugging --
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# -- Remove Logging in Release --
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# -- General Android --
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends androidx.lifecycle.ViewModel { *; }
-keep public class * extends androidx.lifecycle.AndroidViewModel { *; }

# -- Parcelable --
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# -- Keep Kotlin Metadata --
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}