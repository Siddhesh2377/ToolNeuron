package com.dark.neuroverse.activity

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.dark.ai_module.ai.Neuron
import com.dark.ai_module.workers.ModelManager
import com.dark.neuroverse.ui.theme.NeuroVerseTheme
import com.dark.neuroverse.util.extractPureJson
import com.dark.plugins.repo.PluginRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class TempActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            NeuroVerseTheme {
                Scaffold { paddingValues ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .background(MaterialTheme.colorScheme.background),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        var response by remember { mutableStateOf("") }

                        // Load model on launch
                        LaunchedEffect(Unit) {
                            ModelManager.loadModel(
                                this@TempActivity, ModelManager.getModel("Kodify-Nano-GGUF")!!
                            ) {
                                Log.d("Model", "SmolLM2-360M-Instruct-Text-2-JSON Model loaded")
                            }
                        }

                        Text(
                            "Tap to Open Play Store > Manage Apps & Device",
                            style = MaterialTheme.typography.titleMedium
                        )

                        Button(onClick = {
                            //openPlayStoreToApps()
                            CoroutineScope(Dispatchers.IO).launch {
                                val final = Neuron.generateResponseStreaming(
                                    """
                                    You are an automation planner AI.
                                    Your job is to convert user commands into JSON that follows the schema provided below.

                                    RULES:
                                    - Output only valid JSON
                                    - No extra text, no explanation, no markdown
                                    - Use only tools and arguments as defined

                                    TOOLS:
                                    1. AppIOPlugin
                                       - Actions: "open", "close"
                                       - Args: {
                                           "action": "open" or "close",
                                           "packageName": "<app name>"
                                       }

                                    2. UiActionPlugin
                                       - Actions: "scrollDown", "clickButton"
                                       - Args for scrollDown: {
                                           "action": "scrollDown",
                                           "times": "<number>"
                                       }
                                       - Args for clickButton: {
                                           "action": "clickButton",
                                           "buttonText": "<text>",
                                           "fallBackText": "<fallback text>",
                                           "parents": <int>
                                       }

                                    OUTPUT JSON FORMAT:
                                    {
                                      "title": "<short title>",
                                      "description": "<description>",
                                      "tools_called": ["AppIOPlugin", "UiActionPlugin"],
                                      "steps": [
                                        {
                                          "tool": "<tool name>",
                                          "args": {
                                            "action": "open",
                                            "packageName": "PlayStore"
                                          }
                                        },
                                        {
                                          "tool": "<tool name>",
                                          "args": {
                                            "action": "scrollDown",
                                            "times": "4"
                                          }
                                        }
                                      ]
                                    }

                                    USER-INPUT:
                                    open WhatsApp and Scroll down 5 times
                                    """.trimIndent()
                                ) {
                                    response += it
                                }

                                Log.d("Response", final)
                                PluginRegistry.runComplexPlugins(JSONObject(extractPureJson(final)))
                            }

                        }) {
                            Text("Run")
                        }

                        Text(response)
                    }
                }
            }
        }
    }

    private fun openPlayStoreToApps() {

        PluginRegistry.runPlugin("AppIOPlugin", JSONObject().apply {
            put("tasks", JSONArray().apply {
                put(JSONObject().apply {
                    put("task", "checkForAppUpdates")
                })
            })

            put("actions", JSONArray().apply {
//                                put(JSONObject().apply {
//                                    put("action", "clickButton")
//                                    put("parents", 1)
//                                    put("buttonText", "All apps up to date")
//                                    put("fallBackText", "Checking for updates...")
//                                })
                put(JSONObject().apply {
                    put("action", "scrollDown")
                    put("times", 4)
                })
            })
        })
    }
}
