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
-keep class android.content.Context

# -- General, keep all native and JNI-related classes --
-keep class * extends java.lang.Exception
-keep class * extends java.lang.Throwable

# -- (Optional) Keep annotations, required by some reflection/Python/JNI libs --
-keepattributes *Annotation*,InnerClasses,EnclosingMethod,Signature