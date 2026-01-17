r# ==================== NeuronPacket Library ProGuard Rules ====================

# Keep all public API
-keep public class com.neuronpacket.NeuronPacketManager {
    public *;
}

# Keep native methods
-keep public class com.neuronpacket.NeuronPacketNative {
    public *;
    native <methods>;
}

# Keep all model classes
-keep public class com.neuronpacket.LoadingMode { *; }
-keep public class com.neuronpacket.Permission { *; }
-keep public class com.neuronpacket.ExportResult { *; }
-keep public class com.neuronpacket.ImportResult { *; }
-keep public class com.neuronpacket.AuthResult { *; }
-keep public class com.neuronpacket.UserCredentials { *; }
-keep public class com.neuronpacket.PacketMetadata { *; }
-keep public class com.neuronpacket.ExportConfig { *; }
-keep public class com.neuronpacket.PacketSession { *; }

# Keep enum entries
-keepclassmembers enum com.neuronpacket.** {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep data class fields
-keepclassmembers class com.neuronpacket.** {
    <init>(...);
    public <fields>;
    public <methods>;
}

# Kotlin serialization
-keep @kotlinx.serialization.Serializable class com.neuronpacket.** { *; }

# Keep native library loading
-keepclasseswithmembernames class * {
    native <methods>;
}

# AndroidKeyStore for encryption
-keep class android.security.keystore.** { *; }
-keep class javax.crypto.** { *; }
-dontwarn javax.crypto.**

# Keep line numbers for debugging
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile