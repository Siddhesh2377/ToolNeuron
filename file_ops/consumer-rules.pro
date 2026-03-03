# file_ops — keep JNI-bound class and native methods
-keep class com.dark.file_ops.FileOps {
    native <methods>;
    public <methods>;
    <init>();
}
