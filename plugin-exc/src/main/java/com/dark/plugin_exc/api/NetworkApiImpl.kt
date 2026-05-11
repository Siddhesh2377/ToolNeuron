package com.dark.plugin_exc.api

import com.dark.networking.WebNative
import com.dark.plugin_api.PluginCapability
import com.dark.plugin_api.api.NetworkApi
import com.dark.plugin_api.api.NetworkMethod
import com.dark.plugin_api.api.NetworkRequest
import com.dark.plugin_api.api.NetworkResponse
import com.dark.plugin_exc.CapabilityGate

internal class NetworkApiImpl(
    private val gate: CapabilityGate,
) : NetworkApi {

    override suspend fun fetch(request: NetworkRequest): NetworkResponse {
        gate.require(PluginCapability.INTERNET)
        if (request.method != NetworkMethod.GET) {
            throw UnsupportedOperationException("network method ${request.method} not yet supported")
        }
        val result = WebNative.fetchBytes(
            url = request.url,
            timeoutMs = request.timeoutMs.toInt(),
            headers = request.headers,
        )
        val r = result.getOrElse { throw it }
        return NetworkResponse(
            statusCode = r.status,
            headers = emptyMap(),
            body = r.body,
        )
    }
}
