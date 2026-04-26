package com.dark.tool_neuron.service.server

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.dark.native_server.BindMode
import java.net.Inet4Address
import java.net.NetworkInterface

data class BindResolution(
    val host: String,
    val displayHost: String,
    val lanHost: String?,
    val isWifiActive: Boolean,
)

object BindResolver {

    fun resolve(context: Context, mode: BindMode): BindResolution? {
        val wifi = resolveWifiIp(context)
        return when (mode) {
            BindMode.LOOPBACK_ONLY -> BindResolution(
                host = "127.0.0.1",
                displayHost = "127.0.0.1",
                lanHost = null,
                isWifiActive = false,
            )
            BindMode.ALL_INTERFACES -> BindResolution(
                host = "0.0.0.0",
                displayHost = "127.0.0.1",
                lanHost = wifi,
                isWifiActive = wifi != null,
            )
            BindMode.WIFI_ONLY -> {
                val ip = wifi ?: return null
                BindResolution(
                    host = ip,
                    displayHost = ip,
                    lanHost = ip,
                    isWifiActive = true,
                )
            }
        }
    }

    private fun resolveWifiIp(context: Context): String? {
        resolveViaActiveNetwork(context)?.let { return it }
        return resolveViaInterfaceName()
    }

    private fun resolveViaActiveNetwork(context: Context): String? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return null
        val active = cm.activeNetwork ?: return null
        val caps = cm.getNetworkCapabilities(active) ?: return null
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null
        val link = cm.getLinkProperties(active) ?: return null
        for (addr in link.linkAddresses) {
            val ia = addr.address
            if (ia is Inet4Address && !ia.isLoopbackAddress && !ia.isLinkLocalAddress) {
                return ia.hostAddress
            }
        }
        return null
    }

    private fun resolveViaInterfaceName(): String? {
        val interfaces = try {
            NetworkInterface.getNetworkInterfaces() ?: return null
        } catch (_: Exception) {
            return null
        }
        for (ni in interfaces) {
            val name = ni.name ?: continue
            if (!name.startsWith("wlan") && !name.startsWith("ap")) continue
            if (!ni.isUp || ni.isLoopback) continue
            for (addr in ni.inetAddresses) {
                if (addr is Inet4Address && !addr.isLoopbackAddress && !addr.isLinkLocalAddress) {
                    return addr.hostAddress
                }
            }
        }
        return null
    }
}
