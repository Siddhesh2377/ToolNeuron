package com.dark.neuroverse.activity

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.executor.llms.all.simpleOpenRouterExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.streaming.StreamFrame
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dark.neuroverse.ui.theme.NeuroVerseTheme
import kotlinx.coroutines.launch

class TempActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            NeuroVerseTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AgentScreen()
                }
            }
        }
    }
}

@Composable
fun AgentScreen() {
    var response by remember { mutableStateOf("Click button to ask agent") }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // Initialize agent with streaming node
    val agent = remember {
        val promptExecutor = simpleOpenRouterExecutor(
            apiKey = "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"
        )

        val agentStrategy = strategy("Simple streaming agent") {
            // Custom streaming node
            val streamResponse by node<String, String> { input ->
                llm.writeSession {
                    updatePrompt { user(input) }

                    // Request streaming from LLM
                    val stream = requestLLMStreaming()

                    val builder = StringBuilder()
                    stream.collect { chunk ->
                        when (chunk) {
                            is StreamFrame.Append -> {
                                builder.append(chunk.text)
                                response = builder.toString()
                            }
                            is StreamFrame.End -> {
                                // Stream ended
                            }
                            is StreamFrame.ToolCall -> {
                                // Handle tool calls if needed
                            }
                        }
                    }

                    builder.toString()
                }
            }

            edge(nodeStart forwardTo streamResponse)
            edge(streamResponse forwardTo nodeFinish)
        }

        val agentConfig = AIAgentConfig.withSystemPrompt(
            prompt = "You are a helpful assistant. Keep responses concise.",
            llm = LLModel(
                provider = LLMProvider.OpenRouter,
                id = "deepseek/deepseek-chat-v3.1:free",
                capabilities = listOf(
                    LLMCapability.Temperature,
                    LLMCapability.Tools,
                    LLMCapability.Completion
                ),
                contextLength = 32_768,
            )
        )

        AIAgent(
            promptExecutor = promptExecutor,
            strategy = agentStrategy,
            agentConfig = agentConfig,
            toolRegistry = ToolRegistry { }
        )
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = {
                    scope.launch {
                        isLoading = true
                        response = "" // Clear previous response
                        try {
                            agent.run("Explain black holes in simple terms")
                        } catch (e: Exception) {
                            response = "Error: ${e.message}"
                            Log.e("AgentError", e.message, e)
                        } finally {
                            isLoading = false
                        }
                    }
                },
                enabled = !isLoading
            ) {
                Text(if (isLoading) "Loading..." else "Ask Agent")
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Text(
                    text = response,
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(scrollState)
                )
            }
        }
    }
}