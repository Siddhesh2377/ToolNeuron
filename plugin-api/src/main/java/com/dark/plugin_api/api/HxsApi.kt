package com.dark.plugin_api.api

interface HxsApi {
    suspend fun put(key: String, bytes: ByteArray)
    suspend fun get(key: String): ByteArray?
    suspend fun delete(key: String)
    suspend fun list(prefix: String = ""): List<String>
    suspend fun exists(key: String): Boolean
}
