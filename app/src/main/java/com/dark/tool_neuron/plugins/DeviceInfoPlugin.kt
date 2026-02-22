package com.dark.tool_neuron.plugins

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.models.plugins.PluginInfo
import com.dark.tool_neuron.plugins.api.SuperPlugin
import com.dark.tool_neuron.ui.theme.rDp
import com.mp.ai_gguf.toolcalling.ToolCall
import com.mp.ai_gguf.toolcalling.ToolDefinitionBuilder
import org.json.JSONObject

class DeviceInfoPlugin(private val context: Context) : SuperPlugin {

    companion object {
        private const val TAG = "DeviceInfoPlugin"
        const val TOOL_GET_DEVICE_INFO = "get_device_info"
    }

    override fun getPluginInfo(): PluginInfo {
        return PluginInfo(
            name = "Device Info",
            description = "Get device specifications and status information",
            author = "ToolNeuron",
            version = "1.0.0",
            toolDefinitionBuilder = listOf(
                ToolDefinitionBuilder(
                    TOOL_GET_DEVICE_INFO,
                    "Get device specs and status. Returns device model, OS version, battery level, storage, RAM, and network info based on the requested info type."
                )
                    .stringParam("info_type", "Type of info to retrieve: basic, battery, storage, network, memory, or all", required = true)
            )
        )
    }

    override suspend fun executeTool(toolCall: ToolCall): Result<Any> {
        return try {
            when (toolCall.name) {
                TOOL_GET_DEVICE_INFO -> executeGetDeviceInfo(toolCall)
                else -> Result.failure(IllegalArgumentException("Unknown tool: ${toolCall.name}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun executeGetDeviceInfo(toolCall: ToolCall): Result<Any> {
        val infoType = toolCall.getString("info_type").lowercase().trim()

        val info = when (infoType) {
            "basic" -> getBasicInfo()
            "battery" -> getBatteryInfo()
            "storage" -> getStorageInfo()
            "network" -> getNetworkInfo()
            "memory" -> getMemoryInfo()
            "all" -> {
                val all = mutableMapOf<String, String>()
                all.putAll(getBasicInfo())
                all.putAll(getBatteryInfo())
                all.putAll(getStorageInfo())
                all.putAll(getNetworkInfo())
                all.putAll(getMemoryInfo())
                all
            }
            else -> return Result.failure(
                IllegalArgumentException("Unknown info_type: $infoType. Use basic, battery, storage, network, memory, or all")
            )
        }

        val response = DeviceInfoResponse(
            infoType = infoType,
            info = info
        )
        return Result.success(response)
    }

    private fun getBasicInfo(): Map<String, String> {
        return mapOf(
            "Model" to Build.MODEL,
            "Brand" to Build.BRAND,
            "Manufacturer" to Build.MANUFACTURER,
            "Device" to Build.DEVICE,
            "OS Version" to Build.VERSION.RELEASE,
            "SDK Version" to Build.VERSION.SDK_INT.toString(),
            "Product" to Build.PRODUCT,
            "Hardware" to Build.HARDWARE
        )
    }

    private fun getBatteryInfo(): Map<String, String> {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val batteryLevel = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        val isCharging = batteryManager?.isCharging ?: false

        return mapOf(
            "Battery Level" to "${batteryLevel}%",
            "Is Charging" to isCharging.toString()
        )
    }

    private fun getStorageInfo(): Map<String, String> {
        val statFs = StatFs(Environment.getDataDirectory().path)
        val totalBytes = statFs.blockSizeLong * statFs.blockCountLong
        val freeBytes = statFs.blockSizeLong * statFs.availableBlocksLong

        return mapOf(
            "Storage Total" to formatBytes(totalBytes),
            "Storage Free" to formatBytes(freeBytes),
            "Storage Used" to formatBytes(totalBytes - freeBytes)
        )
    }

    private fun getNetworkInfo(): Map<String, String> {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val network = connectivityManager?.activeNetwork
        val capabilities = network?.let { connectivityManager.getNetworkCapabilities(it) }

        val networkType = when {
            capabilities == null -> "None"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "Bluetooth"
            else -> "Other"
        }

        val isConnected = capabilities != null &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)

        return mapOf(
            "Network Type" to networkType,
            "Network Connected" to isConnected.toString()
        )
    }

    private fun getMemoryInfo(): Map<String, String> {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memInfo)

        val totalRam = memInfo.totalMem
        val freeRam = memInfo.availMem
        val lowMemory = memInfo.lowMemory

        val runtime = Runtime.getRuntime()
        val jvmTotal = runtime.totalMemory()
        val jvmFree = runtime.freeMemory()

        return mapOf(
            "RAM Total" to formatBytes(totalRam),
            "RAM Free" to formatBytes(freeRam),
            "RAM Used" to formatBytes(totalRam - freeRam),
            "Low Memory" to lowMemory.toString(),
            "JVM Total" to formatBytes(jvmTotal),
            "JVM Free" to formatBytes(jvmFree)
        )
    }

    private fun formatBytes(bytes: Long): String {
        val kb = 1024L
        val mb = kb * 1024
        val gb = mb * 1024

        return when {
            bytes >= gb -> "%.2f GB".format(bytes.toDouble() / gb)
            bytes >= mb -> "%.2f MB".format(bytes.toDouble() / mb)
            bytes >= kb -> "%.2f KB".format(bytes.toDouble() / kb)
            else -> "$bytes B"
        }
    }

    @Composable
    override fun ToolCallUI() {
        // No standalone UI needed
    }

    @Composable
    override fun CacheToolUI(data: JSONObject) {
        val infoType = data.optString("infoType", "unknown")
        val infoObj = data.optJSONObject("info")

        val infoMap = mutableListOf<Pair<String, String>>()
        if (infoObj != null) {
            val keys = infoObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                infoMap.add(key to infoObj.optString(key, ""))
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(rDp(6.dp)),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
        ) {
            Column(
                modifier = Modifier.padding(rDp(10.dp)),
                verticalArrangement = Arrangement.spacedBy(rDp(6.dp))
            ) {
                Text(
                    text = "Device Info (${infoType.replaceFirstChar { it.uppercase() }})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                infoMap.forEach { (label, value) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = value,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

data class DeviceInfoResponse(
    val infoType: String,
    val info: Map<String, String>
)
