package com.dark.tool_neuron.data

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class AuthState(
    val securityMode: String,
    val salt: ByteArray,
    val hash: ByteArray,
    val failedAttempts: Int = 0,
    val nextAttemptAtMs: Long = 0L,
    val panicSalt: ByteArray = ByteArray(0),
    val panicHash: ByteArray = ByteArray(0),
    val lastSeenNowMs: Long = 0L,
) {
    val hasPanic: Boolean get() = panicSalt.isNotEmpty() && panicHash.isNotEmpty()

    fun encode(): ByteArray {
        val saltLen = salt.size
        val hashLen = hash.size
        val panicSet = hasPanic
        val panicLen = if (panicSet) 1 + 2 + panicSalt.size + 2 + panicHash.size else 1
        val total = 1 + 1 + 2 + saltLen + 2 + hashLen + 2 + 8 + panicLen + 8
        val buf = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(VERSION)
        buf.put(if (securityMode == AppPreferences.SECURITY_APP_PASSWORD) MODE_APP_PASSWORD else MODE_NONE)
        buf.putShort(saltLen.toShort())
        buf.put(salt)
        buf.putShort(hashLen.toShort())
        buf.put(hash)
        buf.putShort(failedAttempts.coerceIn(0, Short.MAX_VALUE.toInt()).toShort())
        buf.putLong(nextAttemptAtMs)
        if (panicSet) {
            buf.put(1)
            buf.putShort(panicSalt.size.toShort())
            buf.put(panicSalt)
            buf.putShort(panicHash.size.toShort())
            buf.put(panicHash)
        } else {
            buf.put(0)
        }
        buf.putLong(lastSeenNowMs)
        return buf.array()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AuthState) return false
        return securityMode == other.securityMode &&
            salt.contentEquals(other.salt) &&
            hash.contentEquals(other.hash) &&
            failedAttempts == other.failedAttempts &&
            nextAttemptAtMs == other.nextAttemptAtMs &&
            panicSalt.contentEquals(other.panicSalt) &&
            panicHash.contentEquals(other.panicHash) &&
            lastSeenNowMs == other.lastSeenNowMs
    }

    override fun hashCode(): Int {
        var r = securityMode.hashCode()
        r = r * 31 + salt.contentHashCode()
        r = r * 31 + hash.contentHashCode()
        r = r * 31 + failedAttempts
        r = r * 31 + nextAttemptAtMs.hashCode()
        r = r * 31 + panicSalt.contentHashCode()
        r = r * 31 + panicHash.contentHashCode()
        r = r * 31 + lastSeenNowMs.hashCode()
        return r
    }

    companion object {
        private const val VERSION: Byte = 4
        private const val VERSION_V3: Byte = 3
        private const val VERSION_V2: Byte = 2
        private const val VERSION_V1: Byte = 1
        private const val MODE_NONE: Byte = 0
        private const val MODE_APP_PASSWORD: Byte = 1

        val DEFAULT = AuthState(
            securityMode = AppPreferences.SECURITY_NONE,
            salt = ByteArray(0),
            hash = ByteArray(0),
        )

        fun decode(data: ByteArray): AuthState {
            if (data.size < 6) return DEFAULT
            val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            val version = buf.get()
            if (version != VERSION && version != VERSION_V3 && version != VERSION_V2 && version != VERSION_V1) return DEFAULT
            val modeByte = buf.get()
            val mode = if (modeByte == MODE_APP_PASSWORD) {
                AppPreferences.SECURITY_APP_PASSWORD
            } else {
                AppPreferences.SECURITY_NONE
            }
            val saltLen = buf.getShort().toInt() and 0xFFFF
            if (buf.remaining() < saltLen + 2) return DEFAULT
            val salt = ByteArray(saltLen).also { buf.get(it) }
            val hashLen = buf.getShort().toInt() and 0xFFFF
            if (buf.remaining() < hashLen) return DEFAULT
            val hash = ByteArray(hashLen).also { buf.get(it) }

            var failedAttempts = 0
            var nextAttemptAtMs = 0L
            var panicSalt = ByteArray(0)
            var panicHash = ByteArray(0)
            var lastSeenNowMs = 0L
            if (version != VERSION_V1) {
                if (buf.remaining() < 10) return AuthState(mode, salt, hash)
                failedAttempts = buf.getShort().toInt() and 0xFFFF
                nextAttemptAtMs = buf.getLong()
            }
            if ((version == VERSION || version == VERSION_V3) && buf.remaining() >= 1) {
                val has = buf.get().toInt()
                if (has == 1) {
                    if (buf.remaining() < 2) return AuthState(mode, salt, hash, failedAttempts, nextAttemptAtMs)
                    val psl = buf.getShort().toInt() and 0xFFFF
                    if (buf.remaining() < psl + 2) {
                        return AuthState(mode, salt, hash, failedAttempts, nextAttemptAtMs)
                    }
                    panicSalt = ByteArray(psl).also { buf.get(it) }
                    val phl = buf.getShort().toInt() and 0xFFFF
                    if (buf.remaining() < phl) {
                        return AuthState(mode, salt, hash, failedAttempts, nextAttemptAtMs)
                    }
                    panicHash = ByteArray(phl).also { buf.get(it) }
                }
            }
            if (version == VERSION && buf.remaining() >= 8) {
                lastSeenNowMs = buf.getLong()
            }
            return AuthState(mode, salt, hash, failedAttempts, nextAttemptAtMs, panicSalt, panicHash, lastSeenNowMs)
        }
    }
}
