package com.dark.neuroverse.activity

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dark.ai_module.workers.ModelManager
import com.dark.neuroverse.ui.theme.NeuroVerseTheme
import com.mp.ai_core.NativeLib
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

class TempActivity : ComponentActivity() {

    private val nativeLib = NativeLib()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val scope = rememberCoroutineScope()
            var result by remember { mutableStateOf("") }
            var prompt by remember { mutableStateOf("Hi bro") }
            var isLoading by remember { mutableStateOf(false) }
            var isThinking by remember { mutableStateOf(false) }
            var parsedJson by remember { mutableStateOf<JSONObject?>(null) }
            var thinkingBuffer by remember { mutableStateOf("") }
            var insideThinking = false

            NeuroVerseTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(title = { Text("Model Test UI") })
                    }
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Top
                    ) {
                        // Prompt Input
                        OutlinedTextField(
                            value = prompt,
                            onValueChange = { prompt = it },
                            label = { Text("Enter Prompt") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(12.dp))

                        // Action buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Button(
                                onClick = {
                                    result = ""
                                    parsedJson = null
                                    isThinking = false
                                    isLoading = true
                                    thinkingBuffer = "" // 🔧 EDIT: reset buffer

                                    scope.launch(Dispatchers.IO) {
                                        val firstModel = ModelManager.getModel("Lucy-128k-gguf")
                                        if (firstModel == null) return@launch
                                        Log.d("TempActivity", "First Model Path: ${firstModel.modelPath}")

                                        val success = nativeLib.initModel(
                                            path = firstModel.modelPath,
                                            threads = 4,
                                            gpuLayers = -1,
                                            useMMAP = true,
                                            useMLOCK = false,
                                            ctxSize = 2048,
                                            temp = 0.7f,
                                            topK = 20,
                                            topP = 0.9f,
                                            minP = 0.0f
                                        )
                                        Log.d("TempActivity", "Init Success: $success")

                                        nativeLib.setSystemPrompt("""
                                            RULES ::
                                            - RESPONSE ONLY IN JSON FORMAT
                                            - DO NOT INCLUDE ANY EXTRA TEXT
                                        """.trimIndent())

                                        // 🔧 EDIT: Override chat template
                                        val customPrompt = """
                                            $prompt
                                        """.trimIndent()

                                        nativeLib.generateStreaming(
                                            prompt = customPrompt, // 🔧 EDIT
                                            maxTokens = 400,
                                            uiScope = scope,
                                            onGenerate = { chunk ->
                                                scope.launch {
                                                    when {
                                                        // 🔧 EDIT: suppress <think> output completely
                                                        chunk.contains("<think>") -> {
                                                            insideThinking = true
                                                            isThinking = false // never show reasoning now
                                                        }
                                                        chunk.contains("</think>") -> {
                                                            insideThinking = false
                                                        }
                                                        insideThinking -> {
                                                            // swallow reasoning silently (don’t collect in buffer)
                                                        }
                                                        else -> {
                                                            result += chunk // Collect only real answer
                                                        }
                                                    }
                                                }
                                            },
                                            onDone = {
                                                isLoading = false
                                                try {
                                                    parsedJson = JSONObject(result.trim())
                                                } catch (e: Exception) {
                                                    Log.e("TempActivity", "JSON Parse failed: $result")
                                                }
                                            },
                                            onError = {
                                                isLoading = false
                                                Log.e("TempActivity", " Error::$it")
                                            },
                                            onStart = {
                                                Log.d("TempActivity", "Started")
                                            })
                                    }
                                }
                            ) {
                                Text("Run")
                            }

                            OutlinedButton(onClick = {
                                result = ""
                                parsedJson = null
                                thinkingBuffer = ""
                            }) {
                                Text("Clear")
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // 🔧 EDIT: Hide reasoning card entirely
                        // (remove AnimatedVisibility for isThinking)

                        // Structured Output
                        AnimatedVisibility(parsedJson != null) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState())
                                    .padding(8.dp)
                            ) {
                                Text("📦 Structured Response:", style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(8.dp))
                                Text(parsedJson?.toString(2) ?: "No structured data available.", style = MaterialTheme.typography.bodyLarge)
                            }
                        }

                        // Fallback: Raw Output
                        AnimatedVisibility(result.isNotEmpty() && parsedJson == null) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState())
                                    .padding(8.dp)
                            ) {
                                Text(result, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        nativeLib.nativeRelease()
    }
}
