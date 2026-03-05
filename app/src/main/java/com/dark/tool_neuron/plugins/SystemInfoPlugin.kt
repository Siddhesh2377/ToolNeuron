package com.dark.tool_neuron.plugins

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
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
import com.dark.gguf_lib.toolcalling.ToolCall
import com.dark.gguf_lib.toolcalling.ToolDefinitionBuilder
import org.json.JSONObject
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

// ── Response Model ──

data class SystemInfoResponse(
    val dateTime: String,
    val timezone: String,
    val batteryPercent: Int,
    val isCharging: Boolean,
    val networkType: String,
    val deviceName: String
)

// ── Plugin ──

class SystemInfoPlugin(private val context: Context) : SuperPlugin {

    companion object {
        const val TOOL_GET_SYSTEM_INFO = "get_system_info"
    }

    override fun getPluginInfo(): PluginInfo {
        return PluginInfo(
            name = "System Info",
            description = "Get current date/time, battery level, and network status",
            author = "ToolNeuron",
            version = "1.0.0",
            toolDefinitionBuilder = listOf(
                ToolDefinitionBuilder(
                    TOOL_GET_SYSTEM_INFO,
                    "Get current system information: date/time, battery level, network status, and device name"
                )
            )
        )
    }

    // ── Serialization ──

    override fun serializeResult(data: Any): String = when (data) {
        is SystemInfoResponse -> JSONObject().apply {
            put("dateTime", data.dateTime)
            put("timezone", data.timezone)
            put("batteryPercent", data.batteryPercent)
            put("isCharging", data.isCharging)
            put("networkType", data.networkType)
            put("deviceName", data.deviceName)
        }.toString()
        else -> data.toString()
    }

    // ── Execution ──

    override suspend fun executeTool(toolCall: ToolCall): Result<Any> {
        return try {
            val now = ZonedDateTime.now()
            val batteryInfo = getBatteryInfo()
            val networkType = getNetworkType()

            Result.success(SystemInfoResponse(
                dateTime = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                timezone = now.zone.id,
                batteryPercent = batteryInfo.first,
                isCharging = batteryInfo.second,
                networkType = networkType,
                deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun getBatteryInfo(): Pair<Int, Boolean> {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val percent = if (scale > 0) (level * 100 / scale) else -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
        return percent to isCharging
    }

    private fun getNetworkType(): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return "Unknown"
        val network = cm.activeNetwork ?: return "Disconnected"
        val caps = cm.getNetworkCapabilities(network) ?: return "Disconnected"

        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "Connected"
        }
    }

    // ── UI ──

    @Composable
    override fun ToolCallUI() {
        // No standalone UI needed
    }

    @Composable
    override fun CacheToolUI(data: JSONObject) {
        val dateTime = data.optString("dateTime", "")
        val timezone = data.optString("timezone", "")
        val battery = data.optInt("batteryPercent", -1)
        val isCharging = data.optBoolean("isCharging", false)
        val network = data.optString("networkType", "")
        val device = data.optString("deviceName", "")

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "System Info",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                val items = listOf(
                    "Time" to "$dateTime ($timezone)",
                    "Battery" to "${battery}%${if (isCharging) " ⚡" else ""}",
                    "Network" to network,
                    "Device" to device
                )

                items.forEach { (label, value) ->
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
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}
