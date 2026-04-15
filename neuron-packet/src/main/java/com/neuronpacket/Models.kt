package com.neuronpacket

enum class LoadingMode(val value: Int) {
    TRANSIENT(0),
    EMBEDDED(1);

    companion object {
        fun fromValue(value: Int) = entries.firstOrNull { it.value == value } ?: EMBEDDED
    }
}

enum class Permission(val value: Int) {
    READ(0x01),
    ADMIN(0x04);

    companion object {
        fun fromValue(value: Int) = entries.firstOrNull { it.value == value } ?: READ
    }
}

data class ExportResult(
    val success: Boolean,
    val packetId: String,
    val recoveryKey: String,
    val errorMessage: String
)

data class ImportResult(
    val success: Boolean,
    val packetId: String,
    val loadingMode: Int,
    val errorMessage: String
)

data class AuthResult(
    val success: Boolean,
    val slotId: Int,
    val permissions: Int,
    val dek: ByteArray?,
    val errorMessage: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as AuthResult
        return success == other.success && slotId == other.slotId &&
               permissions == other.permissions && dek.contentEquals(other.dek)
    }

    override fun hashCode(): Int {
        var result = success.hashCode()
        result = 31 * result + slotId
        result = 31 * result + permissions
        result = 31 * result + (dek?.contentHashCode() ?: 0)
        return result
    }
}

data class UserCredentials(
    val password: String,
    val label: String,
    val permissions: Permission = Permission.READ
)

data class PacketMetadata(
    val packetId: String = "",
    val name: String = "",
    val description: String = "",
    val domain: String = "",
    val language: String = "en",
    val version: String = "1.0",
    val tags: List<String> = emptyList(),
    val loadingMode: LoadingMode = LoadingMode.EMBEDDED
)

data class ExportConfig(
    val adminPassword: String,
    val readOnlyUsers: List<UserCredentials> = emptyList(),
    val loadingMode: LoadingMode = LoadingMode.EMBEDDED,
    val compress: Boolean = true
)

data class PacketSession(
    val packetId: String,
    val permissions: Permission,
    val loadingMode: LoadingMode,
    internal val dek: ByteArray?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as PacketSession
        return packetId == other.packetId && permissions == other.permissions
    }

    override fun hashCode(): Int = packetId.hashCode() * 31 + permissions.hashCode()
}