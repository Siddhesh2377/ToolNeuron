package com.dark.tool_neuron.ui.screens.model_config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.model.ModelConfig
import com.dark.tool_neuron.model.ModelInfo
import com.dark.tool_neuron.model.enums.ProviderType
import com.dark.tool_neuron.ui.components.ActionButton
import com.dark.tool_neuron.ui.components.ActionToggleGroup
import com.dark.tool_neuron.ui.components.StandardCard
import com.dark.tool_neuron.ui.components.SwitchRow
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.LocalTnShapes
import org.json.JSONObject
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelConfigScreen(
    modelInfo: ModelInfo,
    initialConfig: ModelConfig?,
    onSave: (ModelConfig) -> Unit,
    onBack: () -> Unit,
) {
    val dimens = LocalDimens.current
    val loadingJson = remember {
        try { JSONObject(initialConfig?.loadingParamsJson ?: "{}") } catch (_: Exception) { JSONObject() }
    }
    val inferenceJson = remember {
        try { JSONObject(initialConfig?.inferenceParamsJson ?: "{}") } catch (_: Exception) { JSONObject() }
    }

    var contextSize by remember { mutableIntStateOf(loadingJson.optInt("contextSize", 4096)) }
    var threadMode by remember { mutableIntStateOf(loadingJson.optInt("threadMode", 0)) }
    var flashAttn by remember { mutableStateOf(loadingJson.optBoolean("flashAttn", false)) }
    var cacheTypeK by remember { mutableStateOf(loadingJson.optString("cacheTypeK", "f16")) }
    var cacheTypeV by remember { mutableStateOf(loadingJson.optString("cacheTypeV", "f16")) }

    var temperature by remember { mutableFloatStateOf(inferenceJson.optDouble("temperature", 0.7).toFloat()) }
    var topK by remember { mutableIntStateOf(inferenceJson.optInt("topK", 40)) }
    var topP by remember { mutableFloatStateOf(inferenceJson.optDouble("topP", 0.9).toFloat()) }
    var minP by remember { mutableFloatStateOf(inferenceJson.optDouble("minP", 0.05).toFloat()) }
    var repeatPenalty by remember { mutableFloatStateOf(inferenceJson.optDouble("repeatPenalty", 1.1).toFloat()) }
    var maxTokens by remember { mutableIntStateOf(inferenceJson.optInt("maxTokens", 4096)) }
    var seed by remember { mutableStateOf(inferenceJson.optInt("seed", -1).toString()) }

    var ttsThreads by remember { mutableIntStateOf(loadingJson.optInt("numThreads", 2)) }
    var speed by remember { mutableFloatStateOf(inferenceJson.optDouble("speed", 1.0).toFloat()) }
    var speakerId by remember { mutableIntStateOf(inferenceJson.optInt("speakerId", 0)) }

    var sttThreads by remember { mutableIntStateOf(loadingJson.optInt("numThreads", 2)) }
    var language by remember { mutableStateOf(inferenceJson.optString("language", "en")) }

    fun buildConfig(): ModelConfig {
        val loading = JSONObject()
        val inference = JSONObject()
        when (modelInfo.providerType) {
            ProviderType.GGUF -> {
                loading.put("contextSize", contextSize)
                loading.put("threadMode", threadMode)
                loading.put("flashAttn", flashAttn)
                loading.put("cacheTypeK", cacheTypeK)
                loading.put("cacheTypeV", cacheTypeV)
                inference.put("temperature", temperature)
                inference.put("topK", topK)
                inference.put("topP", topP)
                inference.put("minP", minP)
                inference.put("repeatPenalty", repeatPenalty)
                inference.put("maxTokens", maxTokens)
                inference.put("seed", seed.toIntOrNull() ?: -1)
            }
            ProviderType.TTS -> {
                loading.put("numThreads", ttsThreads)
                inference.put("speed", speed)
                inference.put("speakerId", speakerId)
            }
            ProviderType.STT -> {
                loading.put("numThreads", sttThreads)
                inference.put("language", language)
            }
        }
        return ModelConfig(
            id = initialConfig?.id ?: modelInfo.id,
            modelId = modelInfo.id,
            loadingParamsJson = loading.toString(),
            inferenceParamsJson = inference.toString(),
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Model Config", style = MaterialTheme.typography.titleMedium)
                        Text(
                            modelInfo.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    ActionButton(
                        onClickListener = onBack,
                        icon = TnIcons.ArrowLeft,
                        contentDescription = "Back"
                    )
                },
                actions = {
                    ActionButton(
                        onClickListener = { onSave(buildConfig()) },
                        icon = TnIcons.Check,
                        contentDescription = "Save"
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = dimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(dimens.itemSpacing)
        ) {
            when (modelInfo.providerType) {
                ProviderType.GGUF -> {
                    item {
                        Spacer(Modifier.height(dimens.spacingSm))
                        StandardCard(title = "Loading Config") {
                            Column(verticalArrangement = Arrangement.spacedBy(dimens.spacingSm)) {
                                ConfigSlider(
                                    label = "Context Size",
                                    value = contextSize.toFloat(),
                                    onValueChange = { contextSize = it.roundToInt() },
                                    valueRange = 512f..32768f,
                                    steps = ((32768 - 512) / 512) - 1,
                                    valueDisplay = contextSize.toString()
                                )
                                ConfigSlider(
                                    label = "Threads",
                                    value = threadMode.toFloat(),
                                    onValueChange = { threadMode = it.roundToInt() },
                                    valueRange = 0f..8f,
                                    steps = 7,
                                    valueDisplay = if (threadMode == 0) "Auto" else threadMode.toString()
                                )
                                SwitchRow(
                                    title = "Flash Attention",
                                    checked = flashAttn,
                                    onCheckedChange = { flashAttn = it }
                                )
                                val cacheTypes = listOf("f16", "q8_0", "q5_0", "q4_0")
                                Text(
                                    "KV Cache Key Type",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                ActionToggleGroup(
                                    items = cacheTypes,
                                    selectedItem = cacheTypeK,
                                    onItemSelected = { cacheTypeK = it },
                                    itemLabel = { it }
                                )
                                Text(
                                    "KV Cache Value Type",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                ActionToggleGroup(
                                    items = cacheTypes,
                                    selectedItem = cacheTypeV,
                                    onItemSelected = { cacheTypeV = it },
                                    itemLabel = { it }
                                )
                            }
                        }
                    }
                    item {
                        StandardCard(title = "Sampling Config") {
                            Column(verticalArrangement = Arrangement.spacedBy(dimens.spacingSm)) {
                                ConfigSlider(
                                    label = "Temperature",
                                    value = temperature,
                                    onValueChange = { temperature = (it * 100).roundToInt() / 100f },
                                    valueRange = 0f..2f,
                                    valueDisplay = "%.2f".format(temperature)
                                )
                                ConfigSlider(
                                    label = "Top K",
                                    value = topK.toFloat(),
                                    onValueChange = { topK = it.roundToInt() },
                                    valueRange = 1f..100f,
                                    steps = 98,
                                    valueDisplay = topK.toString()
                                )
                                ConfigSlider(
                                    label = "Top P",
                                    value = topP,
                                    onValueChange = { topP = (it * 100).roundToInt() / 100f },
                                    valueRange = 0f..1f,
                                    valueDisplay = "%.2f".format(topP)
                                )
                                ConfigSlider(
                                    label = "Min P",
                                    value = minP,
                                    onValueChange = { minP = (it * 100).roundToInt() / 100f },
                                    valueRange = 0f..1f,
                                    valueDisplay = "%.2f".format(minP)
                                )
                                ConfigSlider(
                                    label = "Repeat Penalty",
                                    value = repeatPenalty,
                                    onValueChange = { repeatPenalty = (it * 100).roundToInt() / 100f },
                                    valueRange = 0f..2f,
                                    valueDisplay = "%.2f".format(repeatPenalty)
                                )
                                ConfigSlider(
                                    label = "Max Tokens",
                                    value = maxTokens.toFloat(),
                                    onValueChange = { maxTokens = it.roundToInt() },
                                    valueRange = 128f..8192f,
                                    steps = ((8192 - 128) / 128) - 1,
                                    valueDisplay = maxTokens.toString()
                                )
                                OutlinedTextField(
                                    value = seed,
                                    onValueChange = { seed = it },
                                    label = { Text("Seed (-1 = random)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
                ProviderType.TTS -> {
                    item {
                        Spacer(Modifier.height(dimens.spacingSm))
                        StandardCard(title = "TTS Config") {
                            Column(verticalArrangement = Arrangement.spacedBy(dimens.spacingSm)) {
                                ConfigSlider(
                                    label = "Threads",
                                    value = ttsThreads.toFloat(),
                                    onValueChange = { ttsThreads = it.roundToInt() },
                                    valueRange = 1f..4f,
                                    steps = 2,
                                    valueDisplay = ttsThreads.toString()
                                )
                                ConfigSlider(
                                    label = "Speed",
                                    value = speed,
                                    onValueChange = { speed = (it * 10).roundToInt() / 10f },
                                    valueRange = 0.5f..2f,
                                    valueDisplay = "%.1f".format(speed)
                                )
                                ConfigSlider(
                                    label = "Speaker ID",
                                    value = speakerId.toFloat(),
                                    onValueChange = { speakerId = it.roundToInt() },
                                    valueRange = 0f..10f,
                                    steps = 9,
                                    valueDisplay = speakerId.toString()
                                )
                            }
                        }
                    }
                }
                ProviderType.STT -> {
                    item {
                        Spacer(Modifier.height(dimens.spacingSm))
                        StandardCard(title = "STT Config") {
                            Column(verticalArrangement = Arrangement.spacedBy(dimens.spacingSm)) {
                                ConfigSlider(
                                    label = "Threads",
                                    value = sttThreads.toFloat(),
                                    onValueChange = { sttThreads = it.roundToInt() },
                                    valueRange = 1f..4f,
                                    steps = 2,
                                    valueDisplay = sttThreads.toString()
                                )
                                OutlinedTextField(
                                    value = language,
                                    onValueChange = { language = it },
                                    label = { Text("Language") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(dimens.spacingXl)) }
        }
    }
}

@Composable
private fun ConfigSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    valueDisplay: String,
    steps: Int = 0,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = valueDisplay,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}
