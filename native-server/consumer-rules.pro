# NativeServer JNI surface — native methods bind by exact name; the JVM bridge
# upcalls into InferenceBridge.startGeneration / cancelGeneration / onRequestEvent
# via a NewGlobalRef'd jobject. R8 must not rename or strip these.
-keep class com.dark.native_server.NativeServer {
    native <methods>;
    public <methods>;
}
-keep class com.dark.native_server.NativeServer$* { *; }
-keep class com.dark.native_server.InferenceBridge { *; }
-keep class com.dark.native_server.BindMode { *; }
