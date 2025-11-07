package com.dark.neuroverse.activity

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dark.ai_module.model.ModelData
import com.dark.ai_module.workers.ModelManager
import com.dark.neuroverse.ui.theme.NeuroVerseTheme
import kotlinx.coroutines.launch

class TempActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NeuroVerseTheme {
                val modelName = intent?.getStringExtra("modelName") ?: "defaultModel"
                var model by remember { mutableStateOf(ModelData()) }
                val coroutineScope = rememberCoroutineScope()

                LaunchedEffect(Unit) {
                    model = ModelManager.getModel(modelName) ?: ModelData()
                }
                if (model.modelName.isNotBlank()) {
                    ModelEditorScreen(model, onSave = { data ->
                        coroutineScope.launch {
                            ModelManager.updateModel(data)
                            ModelManager.loadGenerationModel(data) {
                                Log.d("ModelEditor", "Model updated")
                                finish()
                            }
                        }
                    }, onBack = { finish() })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelEditorScreen(model: ModelData, onSave: (ModelData) -> Unit, onBack: () -> Unit) {
    var modelState by remember { mutableStateOf(model) }
    var selectedSection by remember { mutableIntStateOf(0) }
    val scrollState = rememberScrollState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Model Settings", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { onSave(modelState) }) {
                        Icon(Icons.Default.Check, "Save", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 0.dp
            ) {
                NavigationBarItem(
                    selected = selectedSection == 0,
                    onClick = { selectedSection = 0 },
                    icon = { Icon(Icons.Default.Settings, null) },
                    label = { Text("Basic") }
                )
                NavigationBarItem(
                    selected = selectedSection == 1,
                    onClick = { selectedSection = 1 },
                    icon = { Icon(Icons.Default.Speed, null) },
                    label = { Text("Performance") }
                )
                NavigationBarItem(
                    selected = selectedSection == 2,
                    onClick = { selectedSection = 2 },
                    icon = { Icon(Icons.Default.Tune, null) },
                    label = { Text("Sampling") }
                )
                NavigationBarItem(
                    selected = selectedSection == 3,
                    onClick = { selectedSection = 3 },
                    icon = { Icon(Icons.Default.ChatBubble, null) },
                    label = { Text("Prompts") }
                )
            }
        }
    ) { padding ->
        AnimatedContent(
            targetState = selectedSection,
            transitionSpec = {
                fadeIn(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)) togetherWith
                        fadeOut(tween(150))
            },
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) { section ->
            Column(
                modifier = Modifier
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                when (section) {
                    0 -> BasicSection(modelState) { modelState = it }
                    1 -> PerformanceSection(modelState) { modelState = it }
                    2 -> SamplingSection(modelState) { modelState = it }
                    3 -> PromptsSection(modelState) { modelState = it }
                }
            }
        }
    }
}

@Composable
fun BasicSection(model: ModelData, onChange: (ModelData) -> Unit) {
    SectionCard("Model Information") {
        TextFieldSetting("Model Name", model.modelName) {
            onChange(model.copy(modelName = it))
        }

        TextFieldSetting("Model Path", model.modelPath, enabled = false) {
            onChange(model.copy(modelPath = it))
        }

        SwitchRow("Tool Calling Support", model.isToolCalling) {
            onChange(model.copy(isToolCalling = it))
        }
    }
}

@Composable
fun PerformanceSection(model: ModelData, onChange: (ModelData) -> Unit) {
    SectionCard("Hardware") {
        SliderField(
            "CPU Threads",
            model.threads.toFloat(),
            1f..Runtime.getRuntime().availableProcessors().toFloat(),
            isInt = true
        ) { onChange(model.copy(threads = it.toInt())) }

        SliderField(
            "GPU Layers",
            model.gpuLayers.toFloat(),
            0f..60f,
            isInt = true
        ) { onChange(model.copy(gpuLayers = it.toInt())) }

        SliderField(
            "Context Size",
            model.ctxSize.toFloat(),
            512f..16384f,
            isInt = true,
            step = 512f
        ) { onChange(model.copy(ctxSize = it.toInt())) }
    }

    SectionCard("Memory") {
        SwitchRow("MMAP", model.useMMAP) {
            onChange(model.copy(useMMAP = it))
        }
        SwitchRow("MLOCK", model.useMLOCK) {
            onChange(model.copy(useMLOCK = it))
        }
    }
}

@Composable
fun SamplingSection(model: ModelData, onChange: (ModelData) -> Unit) {
    SectionCard("Generation Parameters") {
        SliderField("Temperature", model.temp, 0f..2f) {
            onChange(model.copy(temp = it))
        }

        SliderField("Top K", model.topK.toFloat(), 0f..100f, isInt = true) {
            onChange(model.copy(topK = it.toInt()))
        }

        SliderField("Top P", model.topP, 0f..1f) {
            onChange(model.copy(topP = it))
        }

        SliderField("Min P", model.minP, 0f..1f) {
            onChange(model.copy(minP = it))
        }

        SliderField(
            "Max Tokens",
            model.maxTokens.toFloat(),
            256f..8192f,
            isInt = true,
            step = 256f
        ) { onChange(model.copy(maxTokens = it.toInt())) }

        SliderField("Seed", model.seed.toFloat(), -1f..9999f, isInt = true) {
            onChange(model.copy(seed = it.toInt()))
        }
    }

    SectionCard("Mirostat") {
        DropdownField(
            "Mode",
            listOf("Off", "v1", "v2"),
            model.mirostat
        ) { onChange(model.copy(mirostat = it)) }

        SliderField("Tau", model.mirostatTau, 1f..10f) {
            onChange(model.copy(mirostatTau = it))
        }

        SliderField("Eta", model.mirostatEta, 0.01f..1f) {
            onChange(model.copy(mirostatEta = it))
        }
    }
}

@Composable
fun PromptsSection(model: ModelData, onChange: (ModelData) -> Unit) {
    SectionCard("System Prompt") {
        OutlinedTextField(
            value = model.systemPrompt,
            onValueChange = { onChange(model.copy(systemPrompt = it)) },
            modifier = Modifier.fillMaxWidth().height(180.dp),
            placeholder = { Text("You are a helpful assistant.") },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            )
        )
        Text(
            "~${model.systemPrompt.split(" ").size} tokens",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    SectionCard("Chat Template") {
        OutlinedTextField(
            value = model.chatTemplate ?: "",
            onValueChange = { onChange(model.copy(chatTemplate = it)) },
            modifier = Modifier.fillMaxWidth().height(180.dp),
            placeholder = { Text("Jinja2 template...") },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            )
        )
    }
}

@Composable
fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            content()
        }
    }
}

@Composable
fun SliderField(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    isInt: Boolean = false,
    step: Float = 0f,
    onChange: (Float) -> Unit
) {
    var textValue by remember(value) {
        mutableStateOf(if (isInt) value.toInt().toString() else "%.2f".format(value))
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            OutlinedTextField(
                value = textValue,
                onValueChange = {
                    textValue = it
                    it.toFloatOrNull()?.let { v ->
                        if (v in range) onChange(v)
                    }
                },
                modifier = Modifier.width(90.dp).height(52.dp),
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                )
            )
        }
        Slider(
            value = value,
            onValueChange = {
                onChange(it)
                textValue = if (isInt) it.toInt().toString() else "%.2f".format(it)
            },
            valueRange = range,
            steps = if (step > 0f) ((range.endInclusive - range.start) / step).toInt() - 1 else 0
        )
    }
}

@Composable
fun TextFieldSetting(label: String, value: String, enabled: Boolean = true, onChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            )
        )
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onChange(!checked) }
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
        Switch(
            checked = checked,
            onCheckedChange = onChange
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropdownField(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = options[selectedIndex],
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                )
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEachIndexed { i, opt ->
                    DropdownMenuItem(
                        text = { Text(opt) },
                        onClick = {
                            onSelect(i)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}