-keep class com.dark.plugin_exc.** { *; }
-keep interface com.dark.plugin_exc.** { *; }

-keepclassmembers class com.dark.plugin_exc.** {
    <init>(...);
}

-keep class ai.onnxruntime.** { *; }
-keep interface ai.onnxruntime.** { *; }
