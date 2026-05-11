# HXD public API — keep for consumers of this module
-keep class com.dark.download_manager.HxdManager        { *; }
-keep class com.dark.download_manager.HxdOptions        { *; }
-keep class com.dark.download_manager.HxdState          { *; }
-keep class com.dark.download_manager.HxdStatus         { *; }
-keep class com.dark.download_manager.HxdService        { *; }

# JNI bridge — native method names must not be obfuscated
-keepclasseswithmembernames class com.dark.download_manager.HxdNative {
    native <methods>;
}
