package com.dark.tool_neuron.ui.screens.model_config

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import com.dark.tool_neuron.util.VlmPaths
import com.dark.download_manager.formatBytes
import org.json.JSONObject
import java.io.File
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelConfigScreen(
    modelInfo: ModelInfo,
    initialConfig: ModelConfig?,
    onSave: (ModelConfig, ProviderType) -> Unit,
    onBack: () -> Unit,
) {
    val dimens = LocalDimens.current
    val loadingJson = remember {
        try { JSONObject(initialConfig?.loadingParamsJson ?: "{}") } catch (_: Exception) { JSONObject() }
    }
    val inferenceJson = remember {
        try { JSONObject(initialConfig?.inferenceParamsJson ?: "{}") } catch (_: Exception) { JSONObject() }
    }

    // Loading
    var contextSize by remember { mutableIntStateOf(loadingJson.optInt("contextSize", 4096)) }
    var threadMode by remember { mutableIntStateOf(loadingJson.optInt("threadMode", 0)) }
    var flashAttn by remember { mutableStateOf(loadingJson.optBoolean("flashAttn", false)) }
    var opOffload by remember { mutableStateOf(loadingJson.optBoolean("opOffload", false)) }
    var cacheTypeK by remember { mutableStateOf(loadingJson.optString("cacheTypeK", "q8_0")) }
    var cacheTypeV by remember { mutableStateOf(loadingJson.optString("cacheTypeV", "q8_0")) }

    // Sampling — basic
    var temperature by remember { mutableFloatStateOf(inferenceJson.optDouble("temperature", 0.7).toFloat()) }
    var topK by remember { mutableIntStateOf(inferenceJson.optInt("topK", 40)) }
    var topP by remember { mutableFloatStateOf(inferenceJson.optDouble("topP", 0.9).toFloat()) }
    var minP by remember { mutableFloatStateOf(inferenceJson.optDouble("minP", 0.05).toFloat()) }
    var repeatPenalty by remember { mutableFloatStateOf(inferenceJson.optDouble("repeatPenalty", 1.1).toFloat()) }
    var maxTokens by remember { mutableIntStateOf(inferenceJson.optInt("maxTokens", 2048)) }
    var seed by remember { mutableStateOf(inferenceJson.optInt("seed", -1).toString()) }

    // Prompt
    var systemPrompt by remember { mutableStateOf(inferenceJson.optString("systemPrompt", "")) }

    // Sampling — advanced
    var mirostat by remember { mutableIntStateOf(inferenceJson.optInt("mirostat", 0)) }
    var mirostatTau by remember { mutableFloatStateOf(inferenceJson.optDouble("mirostatTau", 5.0).toFloat()) }
    var mirostatEta by remember { mutableFloatStateOf(inferenceJson.optDouble("mirostatEta", 0.1).toFloat()) }
    var frequencyPenalty by remember { mutableFloatStateOf(inferenceJson.optDouble("frequencyPenalty", 0.0).toFloat()) }
    var presencePenalty by remember { mutableFloatStateOf(inferenceJson.optDouble("presencePenalty", 0.0).toFloat()) }
    var penaltyLastN by remember { mutableIntStateOf(inferenceJson.optInt("penaltyLastN", 64)) }
    var dryMultiplier by remember { mutableFloatStateOf(inferenceJson.optDouble("dryMultiplier", 0.0).toFloat()) }
    var dryBase by remember { mutableFloatStateOf(inferenceJson.optDouble("dryBase", 1.75).toFloat()) }
    var dryAllowedLength by remember { mutableIntStateOf(inferenceJson.optInt("dryAllowedLength", 2)) }
    var xtcProbability by remember { mutableFloatStateOf(inferenceJson.optDouble("xtcProbability", 0.0).toFloat()) }
    var xtcThreshold by remember { mutableFloatStateOf(inferenceJson.optDouble("xtcThreshold", 0.1).toFloat()) }

    // Memory (KV)
    var kvWindow by remember { mutableIntStateOf(inferenceJson.optInt("kvWindow", 0)) }
    var kvSink by remember { mutableIntStateOf(inferenceJson.optInt("kvSink", 4)) }
    var kvEvictAtFull by remember { mutableStateOf(inferenceJson.optBoolean("kvEvictAtFull", true)) }

    // TTS / STT / Embedding
    var ttsThreads by remember { mutableIntStateOf(loadingJson.optInt("numThreads", 2)) }
    var speed by remember { mutableFloatStateOf(inferenceJson.optDouble("speed", 1.0).toFloat()) }
    var speakerId by remember { mutableIntStateOf(inferenceJson.optInt("speakerId", 0)) }
    var sttThreads by remember { mutableIntStateOf(loadingJson.optInt("numThreads", 2)) }
    var language by remember { mutableStateOf(inferenceJson.optString("language", "en")) }

    // VLM projector
    val baseFile = remember(modelInfo.path) { File(modelInfo.path) }
    val projectorCandidates = remember(modelInfo.path) { VlmPaths.listColocatedMmprojs(baseFile) }
    val isVlmBase = projectorCandidates.isNotEmpty()
    var selectedProjector by remember {
        mutableStateOf(loadingJson.optString("vlmProjector", ""))
    }

    // Section toggles
    var showAdvancedSampling by remember { mutableStateOf(false) }
    var showMemory by remember { mutableStateOf(false) }
    var selectedProviderType by remember(modelInfo.id) { mutableStateOf(modelInfo.providerType) }

    fun resetToDefaults() {
        contextSize = 4096; threadMode = 0; flashAttn = false; opOffload = false
        cacheTypeK = "q8_0"; cacheTypeV = "q8_0"
        temperature = 0.7f; topK = 40; topP = 0.9f; minP = 0.05f
        repeatPenalty = 1.1f; maxTokens = 2048; seed = "-1"
        systemPrompt = ""
        mirostat = 0; mirostatTau = 5f; mirostatEta = 0.1f
        frequencyPenalty = 0f; presencePenalty = 0f; penaltyLastN = 64
        dryMultiplier = 0f; dryBase = 1.75f; dryAllowedLength = 2
        xtcProbability = 0f; xtcThreshold = 0.1f
        kvWindow = 0; kvSink = 4; kvEvictAtFull = true
    }

    fun buildConfig(): ModelConfig {
        val loading = JSONObject()
        val inference = JSONObject()
        when (selectedProviderType) {
            ProviderType.GGUF, ProviderType.VISION_CHAT, ProviderType.TOOL_SEARCH -> {
                loading.put("contextSize", contextSize)
                loading.put("threadMode", threadMode)
                loading.put("flashAttn", flashAttn)
                loading.put("opOffload", opOffload)
                loading.put("cacheTypeK", cacheTypeK)
                loading.put("cacheTypeV", cacheTypeV)
                if (isVlmBase && selectedProjector.isNotBlank()) {
                    loading.put("vlmProjector", selectedProjector)
                }

                inference.put("temperature", temperature)
                inference.put("topK", topK)
                inference.put("topP", topP)
                inference.put("minP", minP)
                inference.put("repeatPenalty", repeatPenalty)
                inference.put("maxTokens", maxTokens)
                inference.put("seed", seed.toIntOrNull() ?: -1)
                if (systemPrompt.isNotBlank()) inference.put("systemPrompt", systemPrompt)

                if (mirostat != 0) {
                    inference.put("mirostat", mirostat)
                    inference.put("mirostatTau", mirostatTau)
                    inference.put("mirostatEta", mirostatEta)
                }
                if (frequencyPenalty != 0f) inference.put("frequencyPenalty", frequencyPenalty)
                if (presencePenalty != 0f) inference.put("presencePenalty", presencePenalty)
                inference.put("penaltyLastN", penaltyLastN)
                if (dryMultiplier > 0f) {
                    inference.put("dryMultiplier", dryMultiplier)
                    inference.put("dryBase", dryBase)
                    inference.put("dryAllowedLength", dryAllowedLength)
                }
                if (xtcProbability > 0f) {
                    inference.put("xtcProbability", xtcProbability)
                    inference.put("xtcThreshold", xtcThreshold)
                }

                if (kvWindow > 0) {
                    inference.put("kvWindow", kvWindow)
                    inference.put("kvSink", kvSink)
                    inference.put("kvEvictAtFull", kvEvictAtFull)
                }
            }
            ProviderType.TTS -> {
                copyJson(loadingJson, loading)
                loading.put("numThreads", ttsThreads)
                inference.put("speed", speed)
                inference.put("speakerId", speakerId)
            }
            ProviderType.STT -> {
                copyJson(loadingJson, loading)
                loading.put("numThreads", sttThreads)
                inference.put("language", language)
            }
            ProviderType.EMBEDDING -> {
                copyJson(loadingJson, loading)
                loading.put("numThreads", sttThreads)
            }
            ProviderType.IMAGE_GEN, ProviderType.IMAGE_UPSCALER -> {
                copyJson(loadingJson, loading)
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
                        onClickListener = ::resetToDefaults,
                        icon = TnIcons.Refresh,
                        contentDescription = "Reset to defaults"
                    )
                    Spacer(Modifier.width(dimens.spacingSm))
                    ActionButton(
                        onClickListener = { onSave(buildConfig(), selectedProviderType) },
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
            item {
                Spacer(Modifier.height(dimens.spacingSm))
                StandardCard(title = "Identity") {
                    Column(verticalArrangement = Arrangement.spacedBy(dimens.spacingMd)) {
                        Text(
                            text = "Tell ToolNeuron what this file is. This drives Model Manager tabs, Image Task lists, RAG choices, and remote server exposure.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        MODEL_TYPES.forEach { type ->
                            ModelIdentityOption(
                                type = type,
                                selected = selectedProviderType == type,
                                onSelect = { selectedProviderType = type },
                            )
                        }
                        Text(
                            text = modelTypeDescription(selectedProviderType),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            when (selectedProviderType) {
                ProviderType.IMAGE_GEN, ProviderType.IMAGE_UPSCALER -> {
                    item {
                        Spacer(Modifier.height(dimens.spacingSm))
                        StandardCard(title = "Image model") {
                            Text(
                                text = "This model is configured from the Image Task screen. " +
                                    "There are no per-model knobs here yet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                ProviderType.GGUF, ProviderType.VISION_CHAT, ProviderType.TOOL_SEARCH -> {
                    item {
                        Spacer(Modifier.height(dimens.spacingSm))
                        StandardCard(title = "Loading") {
                            Column(verticalArrangement = Arrangement.spacedBy(dimens.spacingMd)) {
                                ConfigSlider(
                                    label = "Context Size",
                                    value = contextSize.toFloat(),
                                    onValueChange = { contextSize = it.roundToInt() },
                                    valueRange = 512f..32768f,
                                    steps = ((32768 - 512) / 512) - 1,
                                    valueDisplay = contextSize.toString(),
                                    explanation = "How much the model can remember in this conversation. " +
                                        "Example: 4096 ≈ a few pages of text. Bigger uses more RAM and is slower to start.",
                                )
                                ConfigSlider(
                                    label = "Threads",
                                    value = threadMode.toFloat(),
                                    onValueChange = { threadMode = it.roundToInt() },
                                    valueRange = 0f..2f,
                                    steps = 1,
                                    valueDisplay = when (threadMode) {
                                        0 -> "Power saver"
                                        1 -> "Balanced"
                                        else -> "Performance"
                                    },
                                    explanation = "How hard your CPU works. " +
                                        "Example: Power saver = quiet + slow. Performance = fast + hot battery.",
                                )
                                ConfigSwitch(
                                    label = "Flash Attention",
                                    checked = flashAttn,
                                    onCheckedChange = { flashAttn = it },
                                    explanation = "A faster way to do the model's attention math. " +
                                        "Example: Same output, just quicker. Most modern models support it. Try turning it on.",
                                )
                                ConfigSwitch(
                                    label = "GPU Offload",
                                    checked = opOffload,
                                    onCheckedChange = { opOffload = it },
                                    explanation = "Allows the GGUF runtime to offload supported operations to the device GPU/backend. " +
                                        "This can speed up chat on supported phones, but may fail or heat up on some drivers. " +
                                        "If loading crashes or replies slow down, turn it off and reload the model.",
                                )
                                ConfigToggleGroup(
                                    label = "KV Cache — Keys",
                                    items = CACHE_TYPES,
                                    selected = cacheTypeK,
                                    onSelect = { cacheTypeK = it },
                                    explanation = "How precisely past words are remembered. " +
                                        "Example: f16 = full quality but big. q8_0 = small + nearly identical (use this). q4_0 = tiny + slight quality loss.",
                                )
                                ConfigToggleGroup(
                                    label = "KV Cache — Values",
                                    items = CACHE_TYPES,
                                    selected = cacheTypeV,
                                    onSelect = { cacheTypeV = it },
                                    explanation = "Same as keys, but for the value half of attention. Match Keys unless you know why.",
                                )
                            }
                        }
                    }

                    if (isVlmBase) {
                        item {
                            StandardCard(title = "Projector (VLM)") {
                                Column(verticalArrangement = Arrangement.spacedBy(dimens.spacingSm)) {
                                    Text(
                                        text = "Pick which projector file this model loads for image input. " +
                                            "Leave default to use the first one found in the VLM folder.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    val firstName = projectorCandidates.first().name
                                    val effectiveSelection = selectedProjector.ifBlank { firstName }
                                    projectorCandidates.forEach { file ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { selectedProjector = file.name },
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
                                        ) {
                                            Icon(
                                                imageVector = if (effectiveSelection.equals(file.name, ignoreCase = true))
                                                    TnIcons.CircleCheck else TnIcons.Circle,
                                                contentDescription = null,
                                                tint = if (effectiveSelection.equals(file.name, ignoreCase = true))
                                                    MaterialTheme.colorScheme.primary
                                                else
                                                    MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp),
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = file.name,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Medium,
                                                )
                                                Text(
                                                    text = formatBytes(file.length()),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                    }
                                    if (projectorCandidates.size == 1) {
                                        Text(
                                            text = "Only one projector in this folder — download more from the repo to switch.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item {
                        StandardCard(title = "Prompt") {
                            Column(verticalArrangement = Arrangement.spacedBy(dimens.spacingMd)) {
                                ConfigTextField(
                                    label = "System Prompt",
                                    value = systemPrompt,
                                    onValueChange = { systemPrompt = it },
                                    placeholder = "You are a helpful assistant.",
                                    singleLine = false,
                                    minLines = 3,
                                    maxLines = 8,
                                    explanation = "The model's hidden instructions. Sets its job and personality. " +
                                        "Example: \"You are a friendly tutor who explains things simply.\" " +
                                        "If left blank, the model has no anchor and tends to make stuff up.",
                                )
                            }
                        }
                    }

                    item {
                        StandardCard(title = "Sampling — Basic") {
                            Column(verticalArrangement = Arrangement.spacedBy(dimens.spacingMd)) {
                                ConfigSlider(
                                    label = "Temperature",
                                    value = temperature,
                                    onValueChange = { temperature = (it * 100).roundToInt() / 100f },
                                    valueRange = 0f..2f,
                                    valueDisplay = "%.2f".format(temperature),
                                    explanation = "Creativity dial. " +
                                        "Example: 0.0 = robot, always picks the most predictable word. " +
                                        "0.7 = good for chat. 1.5 = chaotic poetry.",
                                )
                                ConfigSlider(
                                    label = "Top K",
                                    value = topK.toFloat(),
                                    onValueChange = { topK = it.roundToInt() },
                                    valueRange = 1f..200f,
                                    steps = 198,
                                    valueDisplay = topK.toString(),
                                    explanation = "Only consider the K most likely next words, ignore the rest. " +
                                        "Example: 40 = pick from a shortlist of 40 words. Lower = safer, higher = more variety.",
                                )
                                ConfigSlider(
                                    label = "Top P",
                                    value = topP,
                                    onValueChange = { topP = (it * 100).roundToInt() / 100f },
                                    valueRange = 0f..1f,
                                    valueDisplay = "%.2f".format(topP),
                                    explanation = "Keep the smallest group of words that together cover P probability. " +
                                        "Example: 0.9 = use the top words that add up to 90%. Cuts the weird tail. Standard.",
                                )
                                ConfigSlider(
                                    label = "Min P",
                                    value = minP,
                                    onValueChange = { minP = (it * 100).roundToInt() / 100f },
                                    valueRange = 0f..0.5f,
                                    valueDisplay = "%.2f".format(minP),
                                    explanation = "Drop any word whose chance is less than this. " +
                                        "Example: 0.05 = ignore anything under 5% likely. Stops total nonsense words.",
                                )
                                ConfigSlider(
                                    label = "Repeat Penalty",
                                    value = repeatPenalty,
                                    onValueChange = { repeatPenalty = (it * 100).roundToInt() / 100f },
                                    valueRange = 1f..2f,
                                    valueDisplay = "%.2f".format(repeatPenalty),
                                    explanation = "Punish words it just used. " +
                                        "Example: 1.0 = no punishment. 1.1 = mild ('don't repeat the word \"the\" too much'). " +
                                        "1.5+ = strong (avoids loops but can get weird).",
                                )
                                ConfigSlider(
                                    label = "Max Tokens",
                                    value = maxTokens.toFloat(),
                                    onValueChange = { maxTokens = it.roundToInt() },
                                    valueRange = 128f..8192f,
                                    steps = ((8192 - 128) / 128) - 1,
                                    valueDisplay = maxTokens.toString(),
                                    explanation = "How long the reply can get before forcing it to stop. " +
                                        "Example: 512 ≈ a paragraph. 4096 ≈ a long essay. " +
                                        "1 token ≈ ¾ of a word.",
                                )
                                ConfigTextField(
                                    label = "Seed",
                                    value = seed,
                                    onValueChange = { seed = it },
                                    placeholder = "-1",
                                    singleLine = true,
                                    keyboardType = KeyboardType.Number,
                                    explanation = "Reproducibility. " +
                                        "Example: same seed + same question = same answer every time. " +
                                        "-1 = different every time (normal).",
                                )
                            }
                        }
                    }

                    item {
                        SectionToggle(
                            title = "Sampling — Advanced",
                            expanded = showAdvancedSampling,
                            onToggle = { showAdvancedSampling = !showAdvancedSampling },
                        )
                    }
                    item {
                        AnimatedVisibility(visible = showAdvancedSampling) {
                            StandardCard(title = "Advanced") {
                                Column(verticalArrangement = Arrangement.spacedBy(dimens.spacingMd)) {
                                    ConfigToggleGroup(
                                        label = "Mirostat",
                                        items = listOf("Off", "v1", "v2"),
                                        selected = MIROSTAT_LABELS[mirostat.coerceIn(0, 2)],
                                        onSelect = { mirostat = MIROSTAT_LABELS.indexOf(it).coerceAtLeast(0) },
                                        explanation = "Cruise control for creativity. Auto-adjusts so output stays as surprising as you ask. " +
                                            "Example: leave Off normally. Switch to v2 if you want consistent style instead of using temperature.",
                                    )
                                    if (mirostat != 0) {
                                        ConfigSlider(
                                            label = "Mirostat Tau (target surprise)",
                                            value = mirostatTau,
                                            onValueChange = { mirostatTau = (it * 10).roundToInt() / 10f },
                                            valueRange = 0f..10f,
                                            valueDisplay = "%.1f".format(mirostatTau),
                                            explanation = "Target surprise level. " +
                                                "Example: 5.0 = balanced. Higher = more creative.",
                                        )
                                        ConfigSlider(
                                            label = "Mirostat Eta (learning rate)",
                                            value = mirostatEta,
                                            onValueChange = { mirostatEta = (it * 100).roundToInt() / 100f },
                                            valueRange = 0f..1f,
                                            valueDisplay = "%.2f".format(mirostatEta),
                                            explanation = "How fast Mirostat reacts to drift. 0.1 is standard.",
                                        )
                                    }
                                    ConfigSlider(
                                        label = "Frequency Penalty",
                                        value = frequencyPenalty,
                                        onValueChange = { frequencyPenalty = (it * 100).roundToInt() / 100f },
                                        valueRange = 0f..2f,
                                        valueDisplay = "%.2f".format(frequencyPenalty),
                                        explanation = "Punish words used many times. " +
                                            "Example: 0.5 = nudges 'you've said dog 8 times, try a synonym'. 0 = off.",
                                    )
                                    ConfigSlider(
                                        label = "Presence Penalty",
                                        value = presencePenalty,
                                        onValueChange = { presencePenalty = (it * 100).roundToInt() / 100f },
                                        valueRange = 0f..2f,
                                        valueDisplay = "%.2f".format(presencePenalty),
                                        explanation = "Punish any word that's been used (even once). " +
                                            "Example: 0.5 = pushes the model toward fresh vocabulary. 0 = off.",
                                    )
                                    ConfigSlider(
                                        label = "Penalty Window",
                                        value = penaltyLastN.toFloat(),
                                        onValueChange = { penaltyLastN = it.roundToInt() },
                                        valueRange = 0f..2048f,
                                        steps = 31,
                                        valueDisplay = if (penaltyLastN == 0) "Off" else penaltyLastN.toString(),
                                        explanation = "How far back the penalties look. " +
                                            "Example: 64 = only the most recent reply. 256 = the whole answer so far.",
                                    )
                                    ConfigSlider(
                                        label = "DRY Multiplier",
                                        value = dryMultiplier,
                                        onValueChange = { dryMultiplier = (it * 100).roundToInt() / 100f },
                                        valueRange = 0f..2f,
                                        valueDisplay = "%.2f".format(dryMultiplier),
                                        explanation = "DRY = Don't Repeat Yourself. Smart anti-loop sampler. " +
                                            "Example: 0.8 = stops the model from echoing 'I think that I think that...'. 0 = off.",
                                    )
                                    if (dryMultiplier > 0f) {
                                        ConfigSlider(
                                            label = "DRY Base",
                                            value = dryBase,
                                            onValueChange = { dryBase = (it * 100).roundToInt() / 100f },
                                            valueRange = 1f..3f,
                                            valueDisplay = "%.2f".format(dryBase),
                                            explanation = "How fast the penalty grows for longer repeats. 1.75 is standard.",
                                        )
                                        ConfigSlider(
                                            label = "DRY Allowed Length",
                                            value = dryAllowedLength.toFloat(),
                                            onValueChange = { dryAllowedLength = it.roundToInt() },
                                            valueRange = 1f..10f,
                                            steps = 8,
                                            valueDisplay = dryAllowedLength.toString(),
                                            explanation = "Repeats this many tokens are still OK. " +
                                                "Example: 2 = 'the cat the cat' is fine, but 'the cat the cat the cat' gets punished.",
                                        )
                                    }
                                    ConfigSlider(
                                        label = "XTC Probability",
                                        value = xtcProbability,
                                        onValueChange = { xtcProbability = (it * 100).roundToInt() / 100f },
                                        valueRange = 0f..1f,
                                        valueDisplay = "%.2f".format(xtcProbability),
                                        explanation = "XTC = sometimes hide the most obvious word so the model picks a fresher one. " +
                                            "Example: 0.5 = half the time exclude top tokens. Boosts creativity without losing coherence. 0 = off.",
                                    )
                                    if (xtcProbability > 0f) {
                                        ConfigSlider(
                                            label = "XTC Threshold",
                                            value = xtcThreshold,
                                            onValueChange = { xtcThreshold = (it * 100).roundToInt() / 100f },
                                            valueRange = 0f..0.5f,
                                            valueDisplay = "%.2f".format(xtcThreshold),
                                            explanation = "Only exclude top tokens above this probability. 0.1 is standard.",
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item {
                        SectionToggle(
                            title = "Memory (KV Cache)",
                            expanded = showMemory,
                            onToggle = { showMemory = !showMemory },
                        )
                    }
                    item {
                        AnimatedVisibility(visible = showMemory) {
                            StandardCard(title = "Long-conversation handling") {
                                Column(verticalArrangement = Arrangement.spacedBy(dimens.spacingMd)) {
                                    ConfigSlider(
                                        label = "Sliding Window",
                                        value = kvWindow.toFloat(),
                                        onValueChange = { kvWindow = it.roundToInt() },
                                        valueRange = 0f..8192f,
                                        steps = 31,
                                        valueDisplay = if (kvWindow == 0) "Off" else kvWindow.toString(),
                                        explanation = "When the conversation gets too long, normally everything stops. With this on, the model forgets the middle but keeps the start and the recent stuff. " +
                                            "Example: like a person with short-term memory — they remember your name (start) and what you just said, but forget the boring middle. 0 = off.",
                                    )
                                    if (kvWindow > 0) {
                                        ConfigSlider(
                                            label = "Pinned Start Tokens",
                                            value = kvSink.toFloat(),
                                            onValueChange = { kvSink = it.roundToInt() },
                                            valueRange = 0f..32f,
                                            steps = 31,
                                            valueDisplay = kvSink.toString(),
                                            explanation = "Number of tokens at the very start to never forget. " +
                                                "Example: 4 = pin the first 4 tokens (usually the system prompt's opener). Keeps personality stable.",
                                        )
                                        ConfigSwitch(
                                            label = "Auto-trim when full",
                                            checked = kvEvictAtFull,
                                            onCheckedChange = { kvEvictAtFull = it },
                                            explanation = "If the cache is about to overflow before generation starts, automatically free space. " +
                                                "Example: turn this on if you load a big system prompt — it'll keep things from blocking.",
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                ProviderType.TTS -> {
                    item {
                        Spacer(Modifier.height(dimens.spacingSm))
                        StandardCard(title = "Text-to-Speech") {
                            Column(verticalArrangement = Arrangement.spacedBy(dimens.spacingMd)) {
                                ConfigSlider(
                                    label = "Threads",
                                    value = ttsThreads.toFloat(),
                                    onValueChange = { ttsThreads = it.roundToInt() },
                                    valueRange = 1f..4f,
                                    steps = 2,
                                    valueDisplay = ttsThreads.toString(),
                                    explanation = "How many CPU cores synthesize speech. More = faster + hotter battery.",
                                )
                                ConfigSlider(
                                    label = "Speed",
                                    value = speed,
                                    onValueChange = { speed = (it * 10).roundToInt() / 10f },
                                    valueRange = 0.5f..2f,
                                    valueDisplay = "%.1fx".format(speed),
                                    explanation = "Playback speed. 1.0 = normal. 1.5 = fast. 0.7 = slow.",
                                )
                                ConfigSlider(
                                    label = "Speaker ID",
                                    value = speakerId.toFloat(),
                                    onValueChange = { speakerId = it.roundToInt() },
                                    valueRange = 0f..10f,
                                    steps = 9,
                                    valueDisplay = speakerId.toString(),
                                    explanation = "Which voice to use (multi-speaker models only). 0 is the default voice.",
                                )
                            }
                        }
                    }
                }

                ProviderType.STT -> {
                    item {
                        Spacer(Modifier.height(dimens.spacingSm))
                        StandardCard(title = "Speech-to-Text") {
                            Column(verticalArrangement = Arrangement.spacedBy(dimens.spacingMd)) {
                                ConfigSlider(
                                    label = "Threads",
                                    value = sttThreads.toFloat(),
                                    onValueChange = { sttThreads = it.roundToInt() },
                                    valueRange = 1f..4f,
                                    steps = 2,
                                    valueDisplay = sttThreads.toString(),
                                    explanation = "How many CPU cores recognize speech. More = faster transcription.",
                                )
                                ConfigTextField(
                                    label = "Language",
                                    value = language,
                                    onValueChange = { language = it },
                                    placeholder = "en",
                                    singleLine = true,
                                    explanation = "Language code (en, es, fr, hi, etc.). Match the language you'll speak.",
                                )
                            }
                        }
                    }
                }

                ProviderType.EMBEDDING -> {
                    item {
                        Spacer(Modifier.height(dimens.spacingSm))
                        StandardCard(title = "Embedding (RAG)") {
                            Column(verticalArrangement = Arrangement.spacedBy(dimens.spacingMd)) {
                                ConfigSlider(
                                    label = "Threads",
                                    value = sttThreads.toFloat(),
                                    onValueChange = { sttThreads = it.roundToInt() },
                                    valueRange = 1f..4f,
                                    steps = 2,
                                    valueDisplay = sttThreads.toString(),
                                    explanation = "How many CPU cores compute embeddings. More = faster document indexing.",
                                )
                                Text(
                                    "This model is used to index documents you attach to chats. " +
                                        "It loads automatically the first time you add a document, " +
                                        "or when you open a chat that already has docs.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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

private val CACHE_TYPES = listOf("f16", "q8_0", "q5_0", "q4_0")
private val MIROSTAT_LABELS = listOf("Off", "v1", "v2")
private val MODEL_TYPES = listOf(
    ProviderType.GGUF,
    ProviderType.VISION_CHAT,
    ProviderType.TOOL_SEARCH,
    ProviderType.EMBEDDING,
    ProviderType.IMAGE_GEN,
    ProviderType.IMAGE_UPSCALER,
    ProviderType.TTS,
    ProviderType.STT,
)

@Composable
private fun ModelIdentityOption(
    type: ProviderType,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val dimens = LocalDimens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = dimens.spacingXs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
    ) {
        Icon(
            imageVector = if (selected) TnIcons.CircleCheck else TnIcons.Circle,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = modelTypeLabel(type),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
            Text(
                text = modelTypeDescription(type),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun modelTypeLabel(type: ProviderType): String = when (type) {
    ProviderType.GGUF -> "Chat"
    ProviderType.VISION_CHAT -> "Vision"
    ProviderType.TOOL_SEARCH -> "Tool/Search"
    ProviderType.EMBEDDING -> "RAG"
    ProviderType.IMAGE_GEN -> "Image Gen"
    ProviderType.IMAGE_UPSCALER -> "Upscaler"
    ProviderType.TTS -> "TTS"
    ProviderType.STT -> "STT"
}

private fun modelTypeDescription(type: ProviderType): String = when (type) {
    ProviderType.GGUF -> "Chat model: used by normal chat and OpenAI-compatible text completions."
    ProviderType.VISION_CHAT -> "Vision chat model: used by normal chat and image-attached chat when a projector is available."
    ProviderType.TOOL_SEARCH -> "Tool/Search model: intended for search query planning, source filtering, and structured tool-call output."
    ProviderType.EMBEDDING -> "Embedding model: used for document indexing and RAG retrieval."
    ProviderType.IMAGE_GEN -> "Image generation model: used by image create/inpaint tasks."
    ProviderType.IMAGE_UPSCALER -> "Image upscaler: used by image upscale tasks."
    ProviderType.TTS -> "Text-to-speech model: reads replies aloud."
    ProviderType.STT -> "Speech-to-text model: transcribes voice input."
}

@Composable
private fun ConfigSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    valueDisplay: String,
    explanation: String,
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
                style = MaterialTheme.typography.bodyMedium,
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
        ExplanationText(explanation)
    }
}

@Composable
private fun ConfigSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    explanation: String,
) {
    Column {
        SwitchRow(title = label, checked = checked, onCheckedChange = onCheckedChange)
        ExplanationText(explanation)
    }
}

@Composable
private fun ConfigToggleGroup(
    label: String,
    items: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    explanation: String,
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        ActionToggleGroup(
            items = items,
            selectedItem = selected,
            onItemSelected = onSelect,
            itemLabel = { it },
        )
        ExplanationText(explanation)
    }
}

@Composable
private fun ConfigTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    singleLine: Boolean,
    explanation: String,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else 8,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    Column {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier.fillMaxWidth(),
        )
        ExplanationText(explanation)
    }
}

@Composable
private fun SectionToggle(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val dimens = LocalDimens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Icon(
            imageVector = if (expanded) TnIcons.ChevronUp else TnIcons.ChevronDown,
            contentDescription = if (expanded) "Collapse" else "Expand",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(dimens.iconSm),
        )
    }
}

@Composable
private fun ExplanationText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

private fun copyJson(src: JSONObject, dst: JSONObject) {
    val keys = src.keys()
    while (keys.hasNext()) {
        val k = keys.next()
        dst.put(k, src.get(k))
    }
}
