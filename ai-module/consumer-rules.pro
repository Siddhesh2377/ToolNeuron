## Consumer ProGuard rules for ai-module

# Keep AI module public APIs that may be accessed via reflection or JNI
-keep class com.dark.ai_module.model.** { *; }
-keep class com.dark.ai_module.workers.** { *; }
-keep class com.dark.ai_module.helpers.** { *; }