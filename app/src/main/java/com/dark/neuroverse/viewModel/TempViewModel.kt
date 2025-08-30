package com.dark.neuroverse.viewModel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.ai_module.ai.Neuron
import com.dark.ai_module.data.ModelsList
import com.dark.ai_module.workers.ModelManager
import com.dark.neuroverse.model.Message
import com.dark.neuroverse.model.Role
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

class TempViewModel : ViewModel() {
    //Define State Variables
    private var _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages
    val toolList: StateFlow<List<Pair<String, List<Tools>>>> = MutableStateFlow(PluginManager.getInstalledTools())

    init {
        Log.v("ViewModel", PluginManager.getInstalledTools().toString())
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
        }
    }

    fun setTools(tools: List<Tools>){

    }

    //Public Methods
    fun sendMessage(input: String, context: Context) {
        //Add user message to the list
        _messages.value += Message(role = Role.User, text = input)
        var token = ""

        viewModelScope.launch(Dispatchers.IO) {
            _messages.value += Message(role = Role.Assistant, text = "", id = "-1")

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
                val loadedPlugin = PluginManager.runPlugin(context, "Web-Searching", it)

                ToolRunner.run(loadedPlugin, context, JSONObject(it))
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

