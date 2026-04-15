package com.dark.tool_neuron.plugins

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
import com.dark.gguf_lib.toolcalling.ToolCall
import com.dark.gguf_lib.toolcalling.ToolDefinitionBuilder
import org.json.JSONObject
import kotlin.math.*

class CalculatorPlugin : SuperPlugin {

    companion object {
        private const val TAG = "CalculatorPlugin"
        const val TOOL_CALCULATE = "calculate"
        const val TOOL_UNIT_CONVERT = "unit_convert"
    }

    override fun getPluginInfo(): PluginInfo {
        return PluginInfo(
            name = "Calculator",
            description = "Perform mathematical calculations and unit conversions",
            author = "ToolNeuron",
            version = "1.0.0",
            toolDefinitionBuilder = listOf(
                ToolDefinitionBuilder(
                    TOOL_CALCULATE,
                    "Evaluate a mathematical expression. Supports +, -, *, /, ^, sqrt, sin, cos, tan, log, abs, pi, e"
                )
                    .stringParam("expression", "The mathematical expression to evaluate (e.g. '2+3*4', 'sqrt(16)', 'sin(3.14/2)')", required = true),

                ToolDefinitionBuilder(
                    TOOL_UNIT_CONVERT,
                    "Convert a value between units. Supports length (m, km, mi, ft, in, cm), weight (kg, g, lb, oz), temperature (C, F, K)"
                )
                    .numberParam("value", "The numeric value to convert", required = true)
                    .stringParam("from_unit", "Source unit (e.g. 'km', 'lb', 'C')", required = true)
                    .stringParam("to_unit", "Target unit (e.g. 'mi', 'kg', 'F')", required = true)
            )
        )
    }

    override fun serializeResult(data: Any): String = when (data) {
        is CalculatorResponse -> JSONObject().apply {
            put("expression", data.expression)
            put("result", data.result)
            put("formattedResult", data.formattedResult)
        }.toString()
        is UnitConversionResponse -> JSONObject().apply {
            put("value", data.value)
            put("from_unit", data.fromUnit)
            put("to_unit", data.toUnit)
            put("result", data.result)
            put("formattedResult", data.formattedResult)
        }.toString()
        else -> data.toString()
    }

    override suspend fun executeTool(toolCall: ToolCall): Result<Any> {
        return try {
            when (toolCall.name) {
                TOOL_CALCULATE -> executeCalculate(toolCall)
                TOOL_UNIT_CONVERT -> executeUnitConvert(toolCall)
                else -> Result.failure(IllegalArgumentException("Unknown tool: ${toolCall.name}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun executeCalculate(toolCall: ToolCall): Result<Any> {
        val expression = toolCall.getString("expression")
        if (expression.isBlank()) {
            return Result.failure(IllegalArgumentException("Expression is empty"))
        }

        return try {
            val result = evaluateExpression(expression)
            val response = CalculatorResponse(
                expression = expression,
                result = result,
                formattedResult = formatNumber(result)
            )
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException("Failed to evaluate '$expression': ${e.message}"))
        }
    }

    private fun executeUnitConvert(toolCall: ToolCall): Result<Any> {
        val value = toolCall.getDouble("value", 0.0)
        val fromUnit = toolCall.getString("from_unit").lowercase().trim()
        val toUnit = toolCall.getString("to_unit").lowercase().trim()

        return try {
            val result = convertUnit(value, fromUnit, toUnit)
            val response = UnitConversionResponse(
                value = value,
                fromUnit = fromUnit,
                toUnit = toUnit,
                result = result,
                formattedResult = "${formatNumber(value)} $fromUnit = ${formatNumber(result)} $toUnit"
            )
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException("Failed to convert $value $fromUnit to $toUnit: ${e.message}"))
        }
    }

    // Simple expression evaluator
    private fun evaluateExpression(expr: String): Double {
        val cleaned = expr.trim()
            .replace("pi", Math.PI.toString())
            .replace("PI", Math.PI.toString())
            .replace("e(?![a-z])", Math.E.toString())

        return parseExpression(cleaned, IntArray(1) { 0 })
    }

    private fun parseExpression(expr: String, pos: IntArray): Double {
        var result = parseTerm(expr, pos)
        while (pos[0] < expr.length) {
            skipWhitespace(expr, pos)
            if (pos[0] >= expr.length) break
            when (expr[pos[0]]) {
                '+' -> { pos[0]++; result += parseTerm(expr, pos) }
                '-' -> { pos[0]++; result -= parseTerm(expr, pos) }
                else -> break
            }
        }
        return result
    }

    private fun parseTerm(expr: String, pos: IntArray): Double {
        var result = parsePower(expr, pos)
        while (pos[0] < expr.length) {
            skipWhitespace(expr, pos)
            if (pos[0] >= expr.length) break
            when (expr[pos[0]]) {
                '*' -> { pos[0]++; result *= parsePower(expr, pos) }
                '/' -> {
                    pos[0]++
                    val divisor = parsePower(expr, pos)
                    if (divisor == 0.0) throw ArithmeticException("Division by zero")
                    result /= divisor
                }
                '%' -> {
                    pos[0]++
                    val divisor = parsePower(expr, pos)
                    if (divisor == 0.0) throw ArithmeticException("Modulo by zero")
                    result %= divisor
                }
                else -> break
            }
        }
        return result
    }

    private fun parsePower(expr: String, pos: IntArray): Double {
        var result = parseUnary(expr, pos)
        skipWhitespace(expr, pos)
        if (pos[0] < expr.length && expr[pos[0]] == '^') {
            pos[0]++
            result = result.pow(parseUnary(expr, pos))
        }
        return result
    }

    private fun parseUnary(expr: String, pos: IntArray): Double {
        skipWhitespace(expr, pos)
        if (pos[0] < expr.length && expr[pos[0]] == '-') {
            pos[0]++
            return -parsePrimary(expr, pos)
        }
        if (pos[0] < expr.length && expr[pos[0]] == '+') {
            pos[0]++
        }
        return parsePrimary(expr, pos)
    }

    private fun parsePrimary(expr: String, pos: IntArray): Double {
        skipWhitespace(expr, pos)

        // Parentheses
        if (pos[0] < expr.length && expr[pos[0]] == '(') {
            pos[0]++
            val result = parseExpression(expr, pos)
            skipWhitespace(expr, pos)
            if (pos[0] < expr.length && expr[pos[0]] == ')') pos[0]++
            return result
        }

        // Function calls
        val funcNames = listOf("sqrt", "sin", "cos", "tan", "asin", "acos", "atan", "log", "log10", "ln", "abs", "ceil", "floor", "round")
        for (func in funcNames) {
            if (pos[0] + func.length <= expr.length && expr.substring(pos[0], pos[0] + func.length) == func) {
                pos[0] += func.length
                skipWhitespace(expr, pos)
                if (pos[0] < expr.length && expr[pos[0]] == '(') {
                    pos[0]++
                    val arg = parseExpression(expr, pos)
                    skipWhitespace(expr, pos)
                    if (pos[0] < expr.length && expr[pos[0]] == ')') pos[0]++
                    return applyFunction(func, arg)
                }
            }
        }

        // Number
        return parseNumber(expr, pos)
    }

    private fun parseNumber(expr: String, pos: IntArray): Double {
        skipWhitespace(expr, pos)
        val start = pos[0]
        while (pos[0] < expr.length && (expr[pos[0]].isDigit() || expr[pos[0]] == '.')) {
            pos[0]++
        }
        if (pos[0] == start) throw IllegalArgumentException("Expected number at position ${pos[0]}")
        return expr.substring(start, pos[0]).toDouble()
    }

    private fun skipWhitespace(expr: String, pos: IntArray) {
        while (pos[0] < expr.length && expr[pos[0]].isWhitespace()) pos[0]++
    }

    private fun applyFunction(name: String, arg: Double): Double = when (name) {
        "sqrt" -> sqrt(arg)
        "sin" -> sin(arg)
        "cos" -> cos(arg)
        "tan" -> tan(arg)
        "asin" -> asin(arg)
        "acos" -> acos(arg)
        "atan" -> atan(arg)
        "log", "log10" -> log10(arg)
        "ln" -> ln(arg)
        "abs" -> abs(arg)
        "ceil" -> ceil(arg)
        "floor" -> floor(arg)
        "round" -> round(arg)
        else -> throw IllegalArgumentException("Unknown function: $name")
    }

    private fun convertUnit(value: Double, from: String, to: String): Double {
        // Temperature conversions
        if (from in listOf("c", "celsius") || to in listOf("c", "celsius") ||
            from in listOf("f", "fahrenheit") || to in listOf("f", "fahrenheit") ||
            from in listOf("k", "kelvin") || to in listOf("k", "kelvin")) {
            return convertTemperature(value, normalizeTemp(from), normalizeTemp(to))
        }

        // Convert to base unit, then to target
        val fromFactor = unitFactors[from] ?: throw IllegalArgumentException("Unknown unit: $from")
        val toFactor = unitFactors[to] ?: throw IllegalArgumentException("Unknown unit: $to")

        // Check same category
        val fromCat = unitCategory[from] ?: throw IllegalArgumentException("Unknown unit category: $from")
        val toCat = unitCategory[to] ?: throw IllegalArgumentException("Unknown unit category: $to")
        if (fromCat != toCat) throw IllegalArgumentException("Cannot convert between $fromCat and $toCat")

        return value * fromFactor / toFactor
    }

    private fun normalizeTemp(unit: String): String = when (unit) {
        "c", "celsius" -> "c"
        "f", "fahrenheit" -> "f"
        "k", "kelvin" -> "k"
        else -> unit
    }

    private fun convertTemperature(value: Double, from: String, to: String): Double {
        if (from == to) return value
        // Convert to Celsius first
        val celsius = when (from) {
            "c" -> value
            "f" -> (value - 32) * 5.0 / 9.0
            "k" -> value - 273.15
            else -> throw IllegalArgumentException("Unknown temperature unit: $from")
        }
        // Convert from Celsius to target
        return when (to) {
            "c" -> celsius
            "f" -> celsius * 9.0 / 5.0 + 32
            "k" -> celsius + 273.15
            else -> throw IllegalArgumentException("Unknown temperature unit: $to")
        }
    }

    private val unitFactors = mapOf(
        // Length (base: meter)
        "m" to 1.0, "meter" to 1.0, "meters" to 1.0,
        "km" to 1000.0, "kilometer" to 1000.0,
        "cm" to 0.01, "centimeter" to 0.01,
        "mm" to 0.001, "millimeter" to 0.001,
        "mi" to 1609.344, "mile" to 1609.344, "miles" to 1609.344,
        "ft" to 0.3048, "foot" to 0.3048, "feet" to 0.3048,
        "in" to 0.0254, "inch" to 0.0254, "inches" to 0.0254,
        "yd" to 0.9144, "yard" to 0.9144,
        // Weight (base: kilogram)
        "kg" to 1.0, "kilogram" to 1.0,
        "g" to 0.001, "gram" to 0.001,
        "mg" to 0.000001, "milligram" to 0.000001,
        "lb" to 0.453592, "pound" to 0.453592, "pounds" to 0.453592,
        "oz" to 0.0283495, "ounce" to 0.0283495,
        "ton" to 1000.0,
        // Time (base: second)
        "s" to 1.0, "sec" to 1.0, "second" to 1.0, "seconds" to 1.0,
        "min" to 60.0, "minute" to 60.0, "minutes" to 60.0,
        "h" to 3600.0, "hr" to 3600.0, "hour" to 3600.0, "hours" to 3600.0,
        "day" to 86400.0, "days" to 86400.0,
        // Data (base: byte)
        "b" to 1.0, "byte" to 1.0, "bytes" to 1.0,
        "kb" to 1024.0, "kilobyte" to 1024.0,
        "mb" to 1048576.0, "megabyte" to 1048576.0,
        "gb" to 1073741824.0, "gigabyte" to 1073741824.0,
        "tb" to 1099511627776.0, "terabyte" to 1099511627776.0
    )

    private val unitCategory = mapOf(
        // Length
        "m" to "length", "meter" to "length", "meters" to "length",
        "km" to "length", "kilometer" to "length",
        "cm" to "length", "centimeter" to "length",
        "mm" to "length", "millimeter" to "length",
        "mi" to "length", "mile" to "length", "miles" to "length",
        "ft" to "length", "foot" to "length", "feet" to "length",
        "in" to "length", "inch" to "length", "inches" to "length",
        "yd" to "length", "yard" to "length",
        // Weight
        "kg" to "weight", "kilogram" to "weight",
        "g" to "weight", "gram" to "weight",
        "mg" to "weight", "milligram" to "weight",
        "lb" to "weight", "pound" to "weight", "pounds" to "weight",
        "oz" to "weight", "ounce" to "weight",
        "ton" to "weight",
        // Time
        "s" to "time", "sec" to "time", "second" to "time", "seconds" to "time",
        "min" to "time", "minute" to "time", "minutes" to "time",
        "h" to "time", "hr" to "time", "hour" to "time", "hours" to "time",
        "day" to "time", "days" to "time",
        // Data
        "b" to "data", "byte" to "data", "bytes" to "data",
        "kb" to "data", "kilobyte" to "data",
        "mb" to "data", "megabyte" to "data",
        "gb" to "data", "gigabyte" to "data",
        "tb" to "data", "terabyte" to "data"
    )

    private fun formatNumber(value: Double): String {
        return if (value == value.toLong().toDouble()) {
            value.toLong().toString()
        } else {
            "%.6f".format(value).trimEnd('0').trimEnd('.')
        }
    }

    @Composable
    override fun ToolCallUI() {
        // No standalone UI needed
    }

    @Composable
    override fun CacheToolUI(data: JSONObject) {
        when {
            data.has("expression") -> CalculateResultUI(data)
            data.has("from_unit") -> UnitConvertResultUI(data)
            else -> {
                Text(
                    text = data.toString(2),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }

    @Composable
    private fun CalculateResultUI(data: JSONObject) {
        val expression = data.optString("expression", "")
        val result = data.optString("formattedResult", data.optString("result", ""))

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
                    text = "Calculator",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = expression,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "= $result",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }

    @Composable
    private fun UnitConvertResultUI(data: JSONObject) {
        val formatted = data.optString("formattedResult", "")

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
                    text = "Unit Conversion",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = formatted,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

data class CalculatorResponse(
    val expression: String,
    val result: Double,
    val formattedResult: String
)

data class UnitConversionResponse(
    val value: Double,
    val fromUnit: String,
    val toUnit: String,
    val result: Double,
    val formattedResult: String
)
