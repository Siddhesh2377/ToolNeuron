# system_encryptor — keep JNI-bound class and native methods
-keep class com.dark.system_encryptor.SystemEncryptor {
    native <methods>;
    public <methods>;
    <init>();
}
