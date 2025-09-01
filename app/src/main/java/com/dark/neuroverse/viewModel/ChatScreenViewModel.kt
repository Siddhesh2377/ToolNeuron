package com.dark.neuroverse.viewModel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.ai_module.ai.Neuron
import com.dark.ai_module.data.ModelsList
import com.dark.ai_module.model.ModelsData
import com.dark.ai_module.workers.ModelManager
import com.dark.neuroverse.model.Message
import com.dark.neuroverse.model.Role
import com.dark.neuroverse.util.extractPureJson
import com.dark.plugins.manager.PluginManager
import com.dark.plugins.model.Tools
import com.dark.plugins.worker.ToolRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

class ChatScreenViewModel : ViewModel() {
    //Define State Variables
    private var _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages
    val toolList: MutableStateFlow<List<Pair<String, List<Tools>>>> = MutableStateFlow(emptyList())
    val selectedTools: MutableStateFlow<List<Tools>> = MutableStateFlow(emptyList())
    val modelList: MutableStateFlow<List<ModelsData>> = MutableStateFlow(emptyList())

    init {
        viewModelScope.launch(Dispatchers.IO) {
            //Initialize NativeLib
            val model = ModelManager.getFirstModel()
            if (model == null) return@launch

            Log.d("Model", "Model: ${model.modelPath}")

            ModelManager.loadModel(
                modelData = model,
                defaults = ModelManager.ManagerDefaults(systemPrompt = ModelsList.generalPurposeSystemPrompt),
                chatTemplate = ModelsList.chatTemplate,
                forceReload = true
            ) {
                Log.d("Model", "Model loaded successfully $model")
            }

            //Load Tools
            toolList.value = PluginManager.toolsList.value
            viewModelScope.launch(Dispatchers.IO) {
                modelList.value = ModelManager.getAllModels()
            }

            Log.d("Tools", "Tools loaded successfully ${toolList.value.size}")
        }
    }


    fun selectModel(model: ModelsData) {
        ModelManager.unLoadModel()
        viewModelScope.launch(Dispatchers.IO) {
            ModelManager.loadModel(
                modelData = model,
                defaults = ModelManager.ManagerDefaults(
                    systemPrompt = if (toolList.value.isEmpty()) ModelsList.generalPurposeSystemPrompt else ModelsList.getToolCallSystemPrompt(
                        buildToolsListForPrompt = selectedTools.value.joinToString {
                            it.toolName + ":" + it.args.entries.joinToString { (key, value) -> "$key:$value" }
                        })
                ),
                chatTemplate = ModelsList.chatTemplate,
                forceReload = true
            ) {
                Log.d("Model", "Model loaded successfully ${modelList.value[0]}")
            }
        }
    }

    fun selectTool(tools: Tools) {
        selectedTools.value += tools
        Neuron.setSystemPrompt(ModelsList.getToolCallSystemPrompt(buildToolsListForPrompt = selectedTools.value.joinToString {
            it.toolName + ":" + it.args.entries.joinToString { (key, value) -> "$key:$value" }
        }))
    }

    //Public Methods
    fun sendMessage(input: String, context: Context) {
        //Add user message to the list
        _messages.value += Message(role = Role.User, text = input)
        var token = ""

        viewModelScope.launch(Dispatchers.IO) {
            _messages.value += Message(
                role = Role.Assistant,
                text = "",
                id = "-1",
                viaPlugin = if (selectedTools.value.isNotEmpty()) selectedTools.value.joinToString { it.toolName } else "")

            val response = Neuron.generateStreaming(
                prompt = input, onToken = { tok ->
                    token += tok
                    _messages.update {
                        it.map { message ->
                            if (message.id == "-1") {
                                message.copy(text = token)
                            } else {
                                message
                            }
                        }
                    }
                })

            response.let { it ->
                Log.d("Response", "Response: $it")
                val json = extractPureJson(it)
                val loadedPlugin = PluginManager.runPlugin(context, "Web-Searching", json)

                ToolRunner.run(loadedPlugin, context, JSONObject(json))
                _messages.update {
                    it.map { message ->
                        if (message.id == "-1") {
                            message.copy(id = UUID.randomUUID().toString())
                        } else {
                            message
                        }
                    }
                }
            }
        }
    }

    fun stopGenerating() {
        Neuron.stopGeneration()
    }
}