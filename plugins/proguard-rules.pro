-keep class com.dark.plugins.api.** { *; }
-keep class com.dark.plugins.model.** { *; }
-keep class com.dark.plugins.manager.PluginManager { *; }
-keepclassmembers class ** {
    @androidx.compose.runtime.Composable *;
}
