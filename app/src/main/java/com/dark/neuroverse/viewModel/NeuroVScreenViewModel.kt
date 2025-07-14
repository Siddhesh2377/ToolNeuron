package com.dark.neuroverse.viewModel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.ai_manager.ai.local.Neuron
import com.dark.neuroverse.utils.extractPureJson
import com.dark.task_manager.register.TaskRegistry
import com.dark.task_manager.register.TaskRouter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

class NeuroVScreenViewModel : ViewModel() {
    private val _result = MutableStateFlow(JSONObject())
    val result: StateFlow<JSONObject> = _result

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating

    fun setIsGenerating(isGenerating: Boolean) {
        _isGenerating.value = isGenerating
    }

    fun updateResult(result: JSONObject) {
        _result.value = result
    }


    suspend fun processUserPrompt(isSearchOnline: Boolean, prompt: String) {
        setIsGenerating(true)
        when (isSearchOnline) {
            true -> {
                val raw = TaskRouter.processSearchRequest(prompt)

                val json = JSONObject().put("query", raw)

                TaskRegistry.startTask(TaskRegistry.getTasks()[0].taskInfo.taskName, json) {
                  //  viewModelScope.launch {
//                        Log.d("TaskDemoScreen", "Raw output: $it")
//                        val summary = it.getString("summary")
//                        val articleText = summarizeFetchedArticle(summary)
//                        it.remove("summary")
//                        it.put("summary", articleText)
                        updateResult(it)
                        setIsGenerating(false)
                   // }
                }
            }
            false -> {
                val raw = TaskRouter.processUserPrompt(prompt)
                Log.d("TaskDemoScreen", "Raw output: $raw")
                val jsonText = extractPureJson(raw)

                try {
                    val jsonObject = JSONObject(jsonText)
                    val toolCall = jsonObject.getJSONObject("tool_call")
                    val args = toolCall.getJSONObject("args")

                    TaskRegistry.startTask(toolCall.getString("name"), args) {
                        setIsGenerating(false)
                        updateResult(it)
                    }
                } catch (e: Exception) {
                    Log.e("TaskRouter", "Failed to parse tool_call JSON: ${e.message}")
                }
            }
        }
    }

    suspend fun summarizeFetchedArticle(articleText: String): String {
        val prompt = """
        Summarize the following article in a detailed but concise way, like a Wikipedia intro paragraph:
        
        "$articleText"
    """.trimIndent()
        Neuron.updateSystemPrompt("Summarize the given article in a detailed but concise way, like a Wikipedia intro paragraph")

        return Neuron.generateResponseStreaming(prompt) {}
    }
}