package com.dark.plugin_exc.api

import com.dark.hxs.HexStorage
import com.dark.hxs.HxsRecord
import com.dark.plugin_api.PluginCapability
import com.dark.plugin_api.api.HxsApi
import com.dark.plugin_exc.CapabilityGate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class HxsApiImpl(
    private val hxs: HexStorage,
    pluginId: String,
    private val gate: CapabilityGate,
) : HxsApi {

    private val collection = "plugin_$pluginId"
    private val writeLock = Mutex()

    init {
        hxs.ensureCollection(collection)
        hxs.addIndex(collection, TAG_KEY, HexStorage.WIRE_BYTES)
    }

    override suspend fun put(key: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        gate.require(PluginCapability.HXS_WRITE)
        writeLock.withLock {
            val record = HxsRecord.build {
                putString(TAG_KEY, key)
                putBytes(TAG_VALUE, bytes)
                putTimestamp(TAG_UPDATED_AT, System.currentTimeMillis())
            }
            val existing = hxs.queryString(collection, TAG_KEY, key).firstOrNull()
            if (existing != null) {
                record.id = existing.id
                hxs.update(collection, record)
            } else {
                hxs.put(collection, record)
            }
            hxs.flush(collection)
            Unit
        }
    }

    override suspend fun get(key: String): ByteArray? = withContext(Dispatchers.IO) {
        gate.require(PluginCapability.HXS_READ)
        hxs.queryString(collection, TAG_KEY, key).firstOrNull()?.getBytes(TAG_VALUE)
    }

    override suspend fun delete(key: String) = withContext(Dispatchers.IO) {
        gate.require(PluginCapability.HXS_WRITE)
        writeLock.withLock {
            hxs.queryString(collection, TAG_KEY, key).forEach { rec ->
                hxs.delete(collection, rec.id)
            }
            hxs.flush(collection)
        }
    }

    override suspend fun list(prefix: String): List<String> = withContext(Dispatchers.IO) {
        gate.require(PluginCapability.HXS_READ)
        hxs.getAll(collection).mapNotNull { rec ->
            val k = rec.getString(TAG_KEY)
            if (prefix.isEmpty() || k.startsWith(prefix)) k else null
        }
    }

    override suspend fun exists(key: String): Boolean = withContext(Dispatchers.IO) {
        gate.require(PluginCapability.HXS_READ)
        hxs.queryString(collection, TAG_KEY, key).isNotEmpty()
    }

    private companion object {
        const val TAG_KEY = 1
        const val TAG_VALUE = 2
        const val TAG_UPDATED_AT = 3
    }
}
