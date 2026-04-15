package com.neuronpacket

internal class NeuronPacketNative {
    external fun exportPacket(
        outputPath: String,
        name: String,
        domain: String,
        payload: ByteArray,
        adminPassword: String,
        loadingMode: Int,
        userPasswords: Array<String>?,
        userLabels: Array<String>?
    ): ExportResult

    external fun openPacket(packetPath: String): ImportResult
    external fun authenticate(password: String): AuthResult
    external fun decryptPayload(dek: ByteArray): ByteArray?
    external fun closePacket()
    external fun isOpen(): Boolean
    external fun getUserCount(): Int
    external fun getLoadingMode(): Int
    external fun getPacketId(): String
    external fun getMetadataJson(): String

    companion object {
        init {
            System.loadLibrary("neuronpacket")
        }
    }
}