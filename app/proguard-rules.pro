# -- Keep all NeuroVerse model classes and their full members --
-keep class com.dark.neuroverse.model.** { *; }

# -- Keep all AI module core/model/worker/helper classes (and their members) --
-keep class com.dark.ai_module.model.** { *; }
-keep class com.dark.ai_module.workers.** { *; }
-keep class com.dark.ai_module.helpers.** { *; }

# -- If you use Python/Chaquopy or JNI from NeuroVerse or AI module classes --
-keep class com.chaquo.python.** { *; }
-keep class com.dark.neurov.** { *; }
-keep class com.dark.neuroverse.** { *; }
-keep class com.dark.neuroverse.activity.** { *; }
-keep class com.dark.neuroverse.viewModel.** { *; }
-keep class com.dark.neuroverse.ui.** { *; }

# -- General, keep all native and JNI-related classes --
-keep class * extends java.lang.Exception
-keep class * extends java.lang.Throwable


# --- Keep plugin API fully intact for reflection ---
-keep class com.dark.plugins.api.** { *; }

# Keep the ComposePlugin interface and its method names
-keep interface com.dark.plugins.api.ComposePlugin { *; }

# Keep method signatures for all classes implementing ComposePlugin
-keepclassmembers class * implements com.dark.plugins.api.ComposePlugin {
    @androidx.compose.runtime.Composable <methods>;
    public *** content(...);
}

# Keep PluginApi and its subclasses completely
-keep class com.dark.plugins.api.PluginApi { *; }
-keep class * extends com.dark.plugins.api.PluginApi { *; }

# Keep Kotlin metadata (needed to preserve annotations & method info)
-keep class kotlin.Metadata { *; }
-keepattributes *Annotation*,InnerClasses,EnclosingMethod,Signature

# Don't strip Compose @Composable methods in any class
-keepclassmembers class ** {
    @androidx.compose.runtime.Composable *;
}

# Keep @Keep annotated classes/members
-keep @androidx.annotation.Keep class * { *; }
# Keep all PluginApi subclasses and their Context constructors
-keep class * extends com.dark.plugins.api.PluginApi {
    public <init>(android.content.Context);
}
