package com.dark.neuroverse.activity

import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.dark.neuroverse.BuildConfig
import com.mp.ai_core.NativeLib
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Sample activity that wires NativeLib + on-device tool-calling.
 *
 * ⚠️ Requires the NativeLib changes discussed earlier:
 *   - external fun nativeSetToolsJson(toolsJson: String)
 *   - StreamCallback has fun onToolCall(name: String, argsJson: String)
 *   - generateStreaming(...) wrapper accepts toolsJson + onToolCall lambdas
 */
class AiSampleToolActivity : ComponentActivity() {

    private val native = NativeLib.getGenerationInstance()
    private var streamJob: Job? = null

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val modelPath: MutableStateFlow<String?> = MutableStateFlow("")

        CoroutineScope(Dispatchers.IO).launch {
            modelPath.value = com.dark.ai_module.workers.ModelManager.getFirstModel()?.modelPath

            val ok = native.initModel(
                path = "/storage/emulated/0/Download/GRaPE-mini-beta-thinking.Q4_K.gguf",
                threads = Runtime.getRuntime().availableProcessors().coerceAtLeast(2) - 1,
                gpuLayers = 10,
                useMMAP = true,
                useMLOCK = false,
                ctxSize = 4096,
                temp = 0.7f,
                topK = 40,
                topP = 0.9f,
                minP = 0.0f
            )
            if (!ok) Log.e("AiSampleToolActivity", "Failed to init model at $modelPath")
        }

        // Slim system prompt: the JNI side will also inject tool preamble
        native.setSystemPrompt("You are a concise assistant. Prefer calling tools when helpful.")

        setContent {
            MaterialTheme {
                var prompt by remember { mutableStateOf("Add 23 + 1") }
                var output by remember { mutableStateOf("") }

                fun startStreaming(toolsJson: String?) {
                    // Clear previous
                    streamJob?.cancel()
                    output = ""

                    streamJob = native.generateStreaming(
                        prompt = prompt,
                        maxTokens = 512,
                        uiScope = lifecycleScope,
                        onStart = { /* no-op */ },
                        onGenerate = { chunk -> output += chunk },
                        onError = { msg -> output += "\n[error] $msg" },
                        onDone = { /* turn finished */ },
                        toolsJson = toolsJson,
                        onToolCall = { name, args ->
                            // 1) Run tool locally
                            val result = runTool(name, args)

                            // 2) Second pass: feed result back to the model for a natural-language answer
                            val followup = buildString {
                                appendLine("Use this tool result to answer the user clearly.")
                                appendLine("TOOL_RESULT(name=\"$name\"): $result")
                            }
                            native.generateStreaming(
                                prompt = followup,
                                maxTokens = 384,
                                uiScope = lifecycleScope,
                                onStart = { /* no-op */ },
                                onGenerate = { chunk -> output += chunk },
                                onError = { msg -> output += "\n[error] $msg" },
                                onDone = { /* final */ },
                                toolsJson = null
                            )
                        }
                    )
                }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("AI Tool Demo", fontWeight = FontWeight.SemiBold) },
                            colors = TopAppBarDefaults.topAppBarColors()
                        )
                    }
                ) { padding ->
                    Column(
                        modifier = Modifier
                            .padding(padding)
                            .padding(16.dp)
                            .fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Prompt", fontWeight = FontWeight.Medium)
                        BasicTextField(
                            value = prompt,
                            onValueChange = { prompt = it },
                            modifier = Modifier
                                .weight(1.0f)
                                .padding(8.dp)
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = {
                                // Enable tools for first pass
                                startStreaming(SAMPLE_TOOLS_JSON)
                            }) { Text("Ask with tools") }

                            Button(onClick = {
                                // No tools; pure text generation
                                startStreaming(null)
                            }) { Text("Ask (no tools)") }

                            Button(onClick = {
                                native.nativeStopGeneration()
                                streamJob?.cancel()
                            }) { Text("Stop") }
                        }

                        Text("Output", fontWeight = FontWeight.Medium)
                        Text(
                            text = output.ifEmpty { "(waiting for output)" },
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            native.nativeStopGeneration()
            native.nativeRelease()
        } catch (t: Throwable) {
            Log.w("AiSampleToolActivity", "release error", t)
        }
    }

    private fun buildDeviceInfoJson(): String {
        return JSONObject()
            .put("manufacturer", Build.MANUFACTURER)
            .put("model", Build.MODEL)
            .put("sdk", Build.VERSION.SDK_INT)
            .put("device", Build.DEVICE)
            .toString()
    }

    fun runTool(name: String, argsJson: String): String {
        fun ok(obj: JSONObject) = obj.put("ok", true).toString()
        fun err(msg: String, extra: JSONObject? = null) =
            JSONObject().put("ok", false).put("error", msg).apply { if (extra != null) put("extra", extra) }.toString()

        return try {
            val root = JSONObject(argsJson)
            val args = root.optJSONArray("tool_calls")?.optJSONObject(0)?.optJSONObject("arguments")
                ?: root.optJSONObject("arguments") ?: root

            when (name) {
                "device_info" -> buildDeviceInfoJson()

                "sum" -> {
                    val a = args.optDouble("a")
                    val b = args.optDouble("b")
                    ok(JSONObject().put("a", a).put("b", b).put("sum", a + b))
                }

                "stats" -> {
                    val arr = args.getJSONArray("values")
                    val list = (0 until arr.length()).map { arr.getDouble(it) }
                    val n = list.size
                    val sum = list.sum()
                    val mean = sum / n
                    val sorted = list.sorted()
                    val median = if (n % 2 == 1) sorted[n/2] else (sorted[n/2 - 1] + sorted[n/2]) / 2.0
                    val variance = list.fold(0.0) { acc, x -> acc + (x - mean) * (x - mean) } / n
                    val stdev = kotlin.math.sqrt(variance)
                    ok(JSONObject()
                        .put("count", n)
                        .put("sum", sum)
                        .put("mean", mean)
                        .put("median", median)
                        .put("stdev", stdev))
                }

                "unit_convert" -> {
                    val quantity = args.getString("quantity")
                    val from = args.getString("from").lowercase()
                    val to = args.getString("to").lowercase()
                    val value = args.getDouble("value")

                    fun lengthToMeters(v: Double, u: String) = when (u) {
                        "m" -> v
                        "cm" -> v / 100.0
                        "km" -> v * 1000.0
                        "in" -> v * 0.0254
                        "ft" -> v * 0.3048
                        "mi" -> v * 1609.344
                        else -> Double.NaN
                    }
                    fun metersTo(v: Double, u: String) = when (u) {
                        "m" -> v
                        "cm" -> v * 100.0
                        "km" -> v / 1000.0
                        "in" -> v / 0.0254
                        "ft" -> v / 0.3048
                        "mi" -> v / 1609.344
                        else -> Double.NaN
                    }
                    fun tempToK(v: Double, u: String) = when (u) {
                        "k" -> v
                        "c" -> v + 273.15
                        "f" -> (v - 32.0) * 5.0/9.0 + 273.15
                        else -> Double.NaN
                    }
                    fun kTo(v: Double, u: String) = when (u) {
                        "k" -> v
                        "c" -> v - 273.15
                        "f" -> (v - 273.15) * 9.0/5.0 + 32.0
                        else -> Double.NaN
                    }

                    val result = when (quantity) {
                        "length" -> {
                            val m = lengthToMeters(value, from)
                            val out = metersTo(m, to)
                            out
                        }
                        "temperature" -> {
                            val k = tempToK(value, from)
                            val out = kTo(k, to)
                            out
                        }
                        else -> Double.NaN
                    }

                    if (result.isNaN()) err("Unsupported unit conversion", JSONObject(argsJson))
                    else ok(JSONObject().put("quantity", quantity).put("from", from).put("to", to).put("input", value).put("output", result))
                }

                "regex_extract" -> {
                    val pattern = args.getString("pattern")
                    val input = args.getString("input")
                    val flagsArr = args.optJSONArray("flags") ?: org.json.JSONArray()
                    var flags = 0
                    for (i in 0 until flagsArr.length()) {
                        flags = flags or when (flagsArr.getString(i)) {
                            "i" -> java.util.regex.Pattern.CASE_INSENSITIVE
                            "m" -> java.util.regex.Pattern.MULTILINE
                            "s" -> java.util.regex.Pattern.DOTALL
                            "u" -> java.util.regex.Pattern.UNICODE_CASE
                            else -> 0
                        }
                    }
                    val p = java.util.regex.Pattern.compile(pattern, flags)
                    val m = p.matcher(input)
                    val matches = org.json.JSONArray()
                    while (m.find()) {
                        val one = org.json.JSONArray()
                        for (g in 0..m.groupCount()) one.put(m.group(g))
                        matches.put(one)
                    }
                    ok(JSONObject().put("matches", matches))
                }

                "sort_and_filter" -> {
                    val itemsArr = args.getJSONArray("items")
                    val predsArr = args.optJSONArray("predicates") ?: org.json.JSONArray()
                    val sortBy = args.optString("sort_by", null)
                    val order = args.optString("order", "asc")

                    fun passesPred(o: JSONObject, key: String, op: String, v: Any?): Boolean {
                        val lhs = if (o.has(key)) o.get(key) else return false
                        fun asDoubleOrNull(x: Any?): Double? = when (x) {
                            is Number -> x.toDouble()
                            is String -> x.toDoubleOrNull()
                            is Boolean -> if (x) 1.0 else 0.0
                            else -> null
                        }
                        return when (op) {
                            "==" -> lhs == v
                            "!=" -> lhs != v
                            "contains" -> lhs is String && v is String && lhs.contains(v, ignoreCase = true)
                            ">", ">=", "<", "<=" -> {
                                val a = asDoubleOrNull(lhs) ?: return false
                                val b = asDoubleOrNull(v) ?: return false
                                when (op) {
                                    ">"  -> a >  b
                                    ">=" -> a >= b
                                    "<"  -> a <  b
                                    "<=" -> a <= b
                                    else -> false
                                }
                            }
                            else -> false
                        }
                    }

                    val filtered = buildList {
                        for (i in 0 until itemsArr.length()) {
                            val obj = itemsArr.getJSONObject(i)
                            var okItem = true
                            for (j in 0 until predsArr.length()) {
                                val p = predsArr.getJSONObject(j)
                                if (!passesPred(obj, p.getString("key"), p.getString("op"), p.get("value"))) {
                                    okItem = false; break
                                }
                            }
                            if (okItem) add(JSONObject(obj.toString())) // copy
                        }
                    }

                    val sorted = if (sortBy != null) {
                        filtered.sortedWith { a, b ->
                            val av = if (a.has(sortBy)) a.get(sortBy) else null
                            val bv = if (b.has(sortBy)) b.get(sortBy) else null
                            val cmp = when {
                                av == null && bv == null -> 0
                                av == null -> -1
                                bv == null -> 1
                                av is Number && bv is Number -> av.toDouble().compareTo(bv.toDouble())
                                else -> av.toString().compareTo(bv.toString(), ignoreCase = true)
                            }
                            if (order == "desc") -cmp else cmp
                        }
                    } else filtered

                    val outArr = org.json.JSONArray()
                    sorted.forEach { outArr.put(it) }
                    ok(JSONObject().put("result", outArr))
                }

                "json_path" -> {
                    val jsonStr = args.getString("json")
                    val path = args.getJSONArray("path").let { arr -> (0 until arr.length()).map { arr.getString(it) } }
                    val any = org.json.JSONTokener(jsonStr).nextValue()

                    fun getAt(any: Any?, segs: List<String>): Any? {
                        if (segs.isEmpty()) return any
                        val head = segs.first()
                        val tail = segs.drop(1)
                        return when (any) {
                            is JSONObject -> if (any.has(head)) getAt(any.get(head), tail) else null
                            is org.json.JSONArray -> {
                                val idx = head.toIntOrNull() ?: return null
                                if (idx in 0 until any.length()) getAt(any.get(idx), tail) else null
                            }
                            else -> null
                        }
                    }

                    val value = getAt(any, path)
                    ok(JSONObject().put("value", value))
                }

                else -> err("Unknown tool: $name")
            }
        } catch (t: Throwable) {
            err(t.message ?: "tool error")
        }
    }

    companion object {
        // Two tools: device_info() and sum(a,b)
        val SAMPLE_TOOLS_JSON = """
[
  {"type":"function","function":{"name":"searchWeb","description":"This Tool Helps In WebSearch","parameters":{"type":"object","properties":{"query":{"type":"string"}},"required":"[query]"}}}
]
""".trimIndent()

    }
}
