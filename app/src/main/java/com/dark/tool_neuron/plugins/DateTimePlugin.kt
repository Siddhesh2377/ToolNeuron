package com.dark.tool_neuron.plugins

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
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

class DateTimePlugin : SuperPlugin {

    companion object {
        private const val TAG = "DateTimePlugin"
        const val TOOL_GET_CURRENT_DATETIME = "get_current_datetime"
        const val TOOL_DATE_ARITHMETIC = "date_arithmetic"
        const val TOOL_TIMEZONE_CONVERT = "timezone_convert"
    }

    override fun getPluginInfo(): PluginInfo {
        return PluginInfo(
            name = "Date & Time",
            description = "Get current date/time, perform date arithmetic, and convert between timezones",
            author = "ToolNeuron",
            version = "1.0.0",
            toolDefinitionBuilder = listOf(
                ToolDefinitionBuilder(
                    TOOL_GET_CURRENT_DATETIME,
                    "Get the current date and time, optionally in a specific timezone and format"
                )
                    .stringParam("timezone", "IANA timezone ID (e.g. 'America/New_York', 'Europe/London', 'Asia/Tokyo'). Defaults to system timezone", required = false)
                    .stringParam("format", "Output format: 'full' (date and time), 'date' (date only), 'time' (time only), 'iso' (ISO-8601). Defaults to 'full'", required = false),

                ToolDefinitionBuilder(
                    TOOL_DATE_ARITHMETIC,
                    "Add or subtract a duration from a given date/time string"
                )
                    .stringParam("date", "The input date/time string (ISO-8601 format, e.g. '2024-06-15T10:30:00')", required = true)
                    .stringParam("operation", "The operation to perform: 'add' or 'subtract'", required = true)
                    .numberParam("amount", "The amount to add or subtract", required = true)
                    .stringParam("unit", "The unit of duration: 'days', 'hours', 'minutes', 'months', or 'years'", required = true),

                ToolDefinitionBuilder(
                    TOOL_TIMEZONE_CONVERT,
                    "Convert a date/time from one timezone to another"
                )
                    .stringParam("time", "The input date/time string (ISO-8601 format, e.g. '2024-06-15T10:30:00')", required = true)
                    .stringParam("from_timezone", "Source IANA timezone ID (e.g. 'America/New_York')", required = true)
                    .stringParam("to_timezone", "Target IANA timezone ID (e.g. 'Asia/Tokyo')", required = true)
            )
        )
    }

    override fun serializeResult(data: Any): String = when (data) {
        is DateTimeResponse -> JSONObject().apply {
            put("tool", data.tool)
            put("result", data.result)
            put("timezone", data.timezone)
            put("format", data.format)
        }.toString()
        else -> data.toString()
    }

    override suspend fun executeTool(toolCall: ToolCall): Result<Any> {
        return try {
            when (toolCall.name) {
                TOOL_GET_CURRENT_DATETIME -> executeGetCurrentDatetime(toolCall)
                TOOL_DATE_ARITHMETIC -> executeDateArithmetic(toolCall)
                TOOL_TIMEZONE_CONVERT -> executeTimezoneConvert(toolCall)
                else -> Result.failure(IllegalArgumentException("Unknown tool: ${toolCall.name}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun executeGetCurrentDatetime(toolCall: ToolCall): Result<Any> {
        val timezoneStr = toolCall.getString("timezone")
        val format = toolCall.getString("format").ifBlank { "full" }.lowercase().trim()

        val zoneId = if (timezoneStr.isBlank()) {
            ZoneId.systemDefault()
        } else {
            try {
                ZoneId.of(timezoneStr)
            } catch (e: Exception) {
                return Result.failure(IllegalArgumentException("Invalid timezone: '$timezoneStr'. Use IANA format like 'America/New_York'"))
            }
        }

        val now = ZonedDateTime.now(zoneId)
        val formatted = formatDateTime(now, format)

        val response = DateTimeResponse(
            tool = TOOL_GET_CURRENT_DATETIME,
            result = formatted,
            timezone = zoneId.id,
            format = format
        )
        return Result.success(response)
    }

    private fun executeDateArithmetic(toolCall: ToolCall): Result<Any> {
        val dateStr = toolCall.getString("date")
        val operation = toolCall.getString("operation").lowercase().trim()
        val amount = toolCall.getInt("amount", 0).toLong()
        val unit = toolCall.getString("unit").lowercase().trim()

        if (dateStr.isBlank()) {
            return Result.failure(IllegalArgumentException("Date string is empty"))
        }
        if (operation != "add" && operation != "subtract") {
            return Result.failure(IllegalArgumentException("Operation must be 'add' or 'subtract', got: '$operation'"))
        }

        val dateTime = try {
            LocalDateTime.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        } catch (e: DateTimeParseException) {
            try {
                LocalDateTime.parse(dateStr, DateTimeFormatter.ISO_DATE_TIME)
            } catch (e2: DateTimeParseException) {
                return Result.failure(IllegalArgumentException("Invalid date format: '$dateStr'. Use ISO-8601 format like '2024-06-15T10:30:00'"))
            }
        }

        val effectiveAmount = if (operation == "subtract") -amount else amount

        val resultDateTime = when (unit) {
            "days" -> dateTime.plusDays(effectiveAmount)
            "hours" -> dateTime.plusHours(effectiveAmount)
            "minutes" -> dateTime.plusMinutes(effectiveAmount)
            "months" -> dateTime.plusMonths(effectiveAmount)
            "years" -> dateTime.plusYears(effectiveAmount)
            else -> return Result.failure(IllegalArgumentException("Unknown unit: '$unit'. Use 'days', 'hours', 'minutes', 'months', or 'years'"))
        }

        val formatted = resultDateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

        val response = DateTimeResponse(
            tool = TOOL_DATE_ARITHMETIC,
            result = formatted,
            timezone = "local",
            format = "iso"
        )
        return Result.success(response)
    }

    private fun executeTimezoneConvert(toolCall: ToolCall): Result<Any> {
        val timeStr = toolCall.getString("time")
        val fromTz = toolCall.getString("from_timezone")
        val toTz = toolCall.getString("to_timezone")

        if (timeStr.isBlank()) {
            return Result.failure(IllegalArgumentException("Time string is empty"))
        }

        val fromZone = try {
            ZoneId.of(fromTz)
        } catch (e: Exception) {
            return Result.failure(IllegalArgumentException("Invalid source timezone: '$fromTz'"))
        }

        val toZone = try {
            ZoneId.of(toTz)
        } catch (e: Exception) {
            return Result.failure(IllegalArgumentException("Invalid target timezone: '$toTz'"))
        }

        val localDateTime = try {
            LocalDateTime.parse(timeStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        } catch (e: DateTimeParseException) {
            try {
                LocalDateTime.parse(timeStr, DateTimeFormatter.ISO_DATE_TIME)
            } catch (e2: DateTimeParseException) {
                return Result.failure(IllegalArgumentException("Invalid time format: '$timeStr'. Use ISO-8601 format like '2024-06-15T10:30:00'"))
            }
        }

        val sourceZoned = localDateTime.atZone(fromZone)
        val targetZoned = sourceZoned.withZoneSameInstant(toZone)
        val formatted = targetZoned.format(DateTimeFormatter.ISO_ZONED_DATE_TIME)

        val response = DateTimeResponse(
            tool = TOOL_TIMEZONE_CONVERT,
            result = formatted,
            timezone = toZone.id,
            format = "iso"
        )
        return Result.success(response)
    }

    private fun formatDateTime(dateTime: ZonedDateTime, format: String): String {
        return when (format) {
            "date" -> dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            "time" -> dateTime.format(DateTimeFormatter.ofPattern("HH:mm:ss"))
            "iso" -> dateTime.format(DateTimeFormatter.ISO_ZONED_DATE_TIME)
            else -> dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z"))
        }
    }

    @Composable
    override fun ToolCallUI() {
        // No standalone UI needed
    }

    @Composable
    override fun CacheToolUI(data: JSONObject) {
        val tool = data.optString("tool", "")
        val result = data.optString("result", "")
        val timezone = data.optString("timezone", "")

        val title = when (tool) {
            TOOL_GET_CURRENT_DATETIME -> "Current Date & Time"
            TOOL_DATE_ARITHMETIC -> "Date Arithmetic"
            TOOL_TIMEZONE_CONVERT -> "Timezone Conversion"
            else -> "Date & Time"
        }

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
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = timezone,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = result,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

data class DateTimeResponse(
    val tool: String,
    val result: String,
    val timezone: String,
    val format: String
)
