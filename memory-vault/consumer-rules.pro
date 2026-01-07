# Consumer ProGuard Rules for MemoryVault
# These rules are applied to projects that use this library

-keep public class com.memoryvault.MemoryVault {
    public *;
}

-keep public class com.memoryvault.VaultItem
-keep public class com.memoryvault.MessageItem
-keep public class com.memoryvault.FileItem
-keep public class com.memoryvault.CustomDataItem
-keep public class com.memoryvault.EmbeddingItem
-keep public class com.memoryvault.ScoredVaultItem

-keep public class com.memoryvault.core.VaultStats
-keep public class com.memoryvault.ValidationReport
-keep public class com.memoryvault.BackupResult
-keep public class com.memoryvault.core.DefragResult
-keep public class com.memoryvault.CorruptedBlock

-keep class net.jpountz.lz4.** { *; }