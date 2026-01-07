# MemoryVault ProGuard Rules

# Keep all public API classes
-keep public class com.memoryvault.MemoryVault {
    public *;
}

-keep public class com.memoryvault.VaultItem { *; }
-keep public class com.memoryvault.MessageItem { *; }
-keep public class com.memoryvault.FileItem { *; }
-keep public class com.memoryvault.CustomDataItem { *; }
-keep public class com.memoryvault.EmbeddingItem { *; }
-keep public class com.memoryvault.ScoredVaultItem { *; }

-keep public class com.memoryvault.core.VaultStats { *; }
-keep public class com.memoryvault.ValidationReport { *; }
-keep public class com.memoryvault.BackupResult { *; }
-keep public class com.memoryvault.core.DefragResult { *; }

# Keep data class fields and constructors
-keepclassmembers class * {
    public <init>(...);
}

# Keep enum classes
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Kotlin coroutines
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# LZ4 compression
-keep class net.jpountz.lz4.** { *; }
-dontwarn net.jpountz.util.**

# AndroidKeyStore
-keep class android.security.keystore.** { *; }
-keep class javax.crypto.** { *; }
-dontwarn javax.crypto.**

# Keep serialization methods
-keepclassmembers class * {
    public byte[] toBytes();
    public static ** fromBytes(byte[]);
}