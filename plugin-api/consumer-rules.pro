-keep class com.dark.plugin_api.** { *; }
-keep interface com.dark.plugin_api.** { *; }

-keepclassmembers class com.dark.plugin_api.** {
    <init>(...);
}
