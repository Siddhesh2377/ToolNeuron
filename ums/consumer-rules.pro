# UMS — keep JNI-bound classes and native methods
-keep class com.dark.ums.UnifiedMemorySystem {
    native <methods>;
    public <methods>;
    <init>();
}
-keep class com.dark.ums.UmsRecord {
    *;
}
-keep class com.dark.ums.UmsRecord$Builder {
    public <methods>;
}
-keep class com.dark.ums.UmsRecord$Companion {
    public <methods>;
}
