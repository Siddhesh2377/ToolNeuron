-keep class com.dark.plugins.api.** { *; }
-keep class com.dark.plugins.model.** { *; }
-keep class com.dark.plugins.manager.PluginManager { *; }
-keep class com.dark.plugins.sys.uiAction.UiCommandQueue { *; }
-keep class com.dark.plugins.ui.theme.ThemeKt { *; }
-keepclassmembers class ** {
    @androidx.compose.runtime.Composable *;
}
# Explicitly keep interface and method names
-keep interface com.dark.plugins.api.ComposePlugin { *; }

# Keep all method names in classes implementing ComposePlugin
-keepclassmembers class * implements com.dark.plugins.api.ComposePlugin {
    @androidx.compose.runtime.Composable <methods>;
    public *** content(...);
}

# Keep all subclasses of PluginApi (even if in other packages)
-keep class * extends com.dark.plugins.api.PluginApi { *; }
# Keep all PluginApi subclasses and their Context constructors
-keep class * extends com.dark.plugins.api.PluginApi {
    public <init>(android.content.Context);
}
