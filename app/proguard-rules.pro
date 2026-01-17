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
-keep class com.dark.plugins.api.** { *; }

# -- Data Classes & Enums --
-keepclassmembers class com.dark.tool_neuron.models.** {
    *;
}
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
-keep class androidx.compose.** { *; }
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
-keep class com.mp.ai_gguf.** { *; }
-keep class com.dark.ai_module.** { *; }
-keep class com.android.tools.mlkit.** { *; }

# -- RAG & NeuronGraph --
-keep class com.dark.tool_neuron.neuron_example.** { *; }
-keepclassmembers class com.dark.tool_neuron.neuron_example.** {
    *;
}

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
-dontwarn com.tom_roush.pdfbox.**
-keep class com.tom_roush.harmony.** { *; }
-dontwarn com.tom_roush.harmony.**
-dontwarn org.apache.commons.logging.**
-dontwarn javax.imageio.**
-dontwarn java.awt.**

# EPUB Library
-keep class nl.siegmann.epublib.** { *; }
-dontwarn nl.siegmann.epublib.**
-dontwarn org.slf4j.**
-dontwarn org.xmlpull.**

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