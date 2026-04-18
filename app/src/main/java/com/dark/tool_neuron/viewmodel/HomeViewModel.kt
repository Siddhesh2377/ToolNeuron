package com.dark.tool_neuron.viewmodel

import android.content.Context
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.tool_neuron.model.Chat
import com.dark.tool_neuron.model.ChatDocument
import com.dark.tool_neuron.model.ChatMessage
import com.dark.tool_neuron.model.ModelConfig
import com.dark.tool_neuron.model.ModelInfo
import com.dark.tool_neuron.model.enums.PathType
import com.dark.tool_neuron.repo.ChatRepository
import com.dark.tool_neuron.repo.ModelRepository
import com.dark.tool_neuron.service.inference.InferenceClient
import com.dark.tool_neuron.service.inference.InferenceEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject

private const val MAX_TOKENS = 2048

sealed interface ModelLoadState {
    data object Idle : ModelLoadState
    data class Loading(val modelId: String) : ModelLoadState
    data class Active(val modelId: String) : ModelLoadState
    data class Error(val modelId: String, val message: String) : ModelLoadState
}

data class StreamingFragment(
    val chatId: String,
    val content: String,
    val thinkingContent: String,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelRepo: ModelRepository,
    private val chatRepo: ChatRepository,
) : ViewModel() {

    val installedModels: StateFlow<List<ModelInfo>> = modelRepo.models
    val chats: StateFlow<List<Chat>> = chatRepo.chats

    val activeModel: StateFlow<ModelInfo?> = installedModels
        .map { list -> list.firstOrNull { it.isActive } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _streamingFragment = MutableStateFlow<StreamingFragment?>(null)
    val streamingFragment: StateFlow<StreamingFragment?> = _streamingFragment.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _modelLoadState = MutableStateFlow<ModelLoadState>(ModelLoadState.Idle)
    val modelLoadState: StateFlow<ModelLoadState> = _modelLoadState.asStateFlow()

    private val _supportsThinking = MutableStateFlow(false)
    val supportsThinking: StateFlow<Boolean> = _supportsThinking.asStateFlow()

    private val _actionWindowExpanded = MutableStateFlow(false)
    val actionWindowExpanded = _actionWindowExpanded.asStateFlow()

    private val _plusMenuExpanded = MutableStateFlow(false)
    val plusMenuExpanded = _plusMenuExpanded.asStateFlow()

    private val _webSearchEnabled = MutableStateFlow(false)
    val webSearchEnabled = _webSearchEnabled.asStateFlow()

    private val _thinkingEnabled = MutableStateFlow(false)
    val thinkingEnabled = _thinkingEnabled.asStateFlow()

    private val _chatDocuments = MutableStateFlow<List<ChatDocument>>(emptyList())
    val chatDocuments: StateFlow<List<ChatDocument>> = _chatDocuments.asStateFlow()

    private val _currentChatId = MutableStateFlow<String?>(null)
    val currentChatId: StateFlow<String?> = _currentChatId.asStateFlow()

    private val _loadModelWindow = MutableStateFlow(false)
    val loadModelWindows: StateFlow<Boolean> = _loadModelWindow.asStateFlow()

    private var generationJob: Job? = null

    fun toggleActionWindow() { _actionWindowExpanded.value = !_actionWindowExpanded.value }
    fun collapseActionWindow() { _actionWindowExpanded.value = false }
    fun togglePlusMenu() { _plusMenuExpanded.value = !_plusMenuExpanded.value }
    fun dismissPlusMenu() { _plusMenuExpanded.value = false }
    fun toggleWebSearch() { _webSearchEnabled.value = !_webSearchEnabled.value }
    fun toggleThinking() { _thinkingEnabled.value = !_thinkingEnabled.value }
    fun toggleLoadModelWindow() { _loadModelWindow.value = !_loadModelWindow.value }

    fun addDocument(document: ChatDocument) { _chatDocuments.value += document }
    fun removeDocument(docId: String) { _chatDocuments.value = _chatDocuments.value.filter { it.id != docId } }

    fun selectChat(chatId: String) {
        _currentChatId.value = chatId
        _messages.value = chatRepo.getMessages(chatId)
    }

    fun createNewChat() {
        _currentChatId.value = null
        _messages.value = emptyList()
    }

    fun deleteChat(chatId: String) {
        chatRepo.deleteChat(chatId)
        if (_currentChatId.value == chatId) createNewChat()
    }

    fun pinChat(chatId: String, pinned: Boolean) {
        chatRepo.pinChat(chatId, pinned)
    }

    fun loadModel(model: ModelInfo) {
        viewModelScope.launch {
            _modelLoadState.value = ModelLoadState.Loading(model.id)
            val config = modelRepo.getConfig(model.id)
            val configJson = buildConfigJson(config)
            val result = when (model.pathType) {
                PathType.FILE -> InferenceClient.loadModel(model.path, configJson)
                PathType.CONTENT_URI -> InferenceClient.loadModelFromUri(
                    context, model.path.toUri(), configJson
                )
            }
            result
                .onSuccess {
                    modelRepo.setActive(model.id)
                    _supportsThinking.value = InferenceClient.supportsThinking()
                    _modelLoadState.value = ModelLoadState.Active(model.id)
                }
                .onFailure { err ->
                    _supportsThinking.value = false
                    _modelLoadState.value = ModelLoadState.Error(model.id, err.message ?: "Load failed")
                }
        }
    }

    fun unloadModel() {
        viewModelScope.launch {
            InferenceClient.unloadModel()
            _supportsThinking.value = false
            _modelLoadState.value = ModelLoadState.Idle
        }
    }

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _isGenerating.value) return
        val active = activeModel.value ?: return

        val chatId = ensureChat(active)
        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            chatId = chatId,
            role = ROLE_USER,
            content = trimmed,
            timestamp = System.currentTimeMillis(),
        )
        chatRepo.addMessage(userMessage)

        val isFirstTurn = chatRepo.getMessages(chatId).count { it.role == ROLE_USER } == 1
        runGeneration(chatId, isFirstTurn, trimmed)
    }

    fun regenerateLast() {
        if (_isGenerating.value) return
        val chatId = _currentChatId.value ?: return
        if (activeModel.value == null) return
        val lastAssistant = chatRepo.getMessages(chatId).lastOrNull { it.role == ROLE_ASSISTANT }
            ?: return
        chatRepo.deleteMessage(lastAssistant.id)
        runGeneration(chatId, isFirstTurn = false, userText = "")
    }

    fun deleteMessage(messageId: String) {
        val chatId = _currentChatId.value ?: return
        chatRepo.deleteMessage(messageId)
        _messages.value = chatRepo.getMessages(chatId)
    }

    fun stopGeneration() {
        if (!_isGenerating.value) return
        InferenceClient.stopGeneration()
    }

    private fun runGeneration(chatId: String, isFirstTurn: Boolean, userText: String) {
        _messages.value = chatRepo.getMessages(chatId)
        _streamingFragment.value = StreamingFragment(chatId, "", "")
        _isGenerating.value = true

        val thinkingOn = _thinkingEnabled.value && _supportsThinking.value
        InferenceClient.setThinkingEnabled(thinkingOn)

        val historyJson = buildMessagesJson(_messages.value)

        generationJob = viewModelScope.launch {
            var rawAssistant = ""
            var cleanContent = ""
            var thinkingContent = ""
            var errorMessage: String? = null

            try {
                InferenceClient.generateMultiTurn(historyJson, MAX_TOKENS)
                    .transformWhile { event ->
                        emit(event)
                        event !is InferenceEvent.Done && event !is InferenceEvent.Error
                    }
                    .collect { event ->
                        when (event) {
                            is InferenceEvent.Token -> {
                                rawAssistant += event.text
                                val (clean, think) = parseThinking(rawAssistant)
                                cleanContent = clean
                                thinkingContent = think
                                _streamingFragment.value = StreamingFragment(chatId, clean, think)
                            }
                            is InferenceEvent.Error -> errorMessage = event.message
                            else -> {}
                        }
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorMessage = e.message
            } finally {
                finalizeAssistantMessage(chatId, cleanContent, thinkingContent, errorMessage, isFirstTurn, userText)
            }
        }
    }

    private fun ensureChat(active: ModelInfo): String {
        val existing = _currentChatId.value
        if (existing != null) return existing
        val chat = chatRepo.createChat(active.id, active.name)
        _currentChatId.value = chat.id
        return chat.id
    }

    private fun finalizeAssistantMessage(
        chatId: String,
        content: String,
        thinking: String,
        error: String?,
        isFirstTurn: Boolean,
        userText: String,
    ) {
        val finalContent = when {
            error != null && content.isBlank() -> "Error: $error"
            error != null -> "$content\n\n_Error: ${error}_"
            else -> content
        }
        val finalMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            chatId = chatId,
            role = ROLE_ASSISTANT,
            content = finalContent,
            thinkingContent = thinking,
            timestamp = System.currentTimeMillis(),
        )
        chatRepo.addMessage(finalMessage)
        if (isFirstTurn) chatRepo.autoTitle(chatId, userText)
        _messages.value = chatRepo.getMessages(chatId)
        _streamingFragment.value = null
        _isGenerating.value = false
    }

    private fun buildMessagesJson(messages: List<ChatMessage>): String {
        val arr = JSONArray()
        messages.forEach { msg ->
            if (msg.role == ROLE_USER || msg.role == ROLE_ASSISTANT) {
                arr.put(JSONObject().apply {
                    put("role", msg.role)
                    put("content", msg.content)
                })
            }
        }
        return arr.toString()
    }

    private fun buildConfigJson(config: ModelConfig?): String {
        if (config == null) return "{}"
        val sb = StringBuilder(256).append('{')
        val loading = config.loadingParamsJson
        val inference = config.inferenceParamsJson
        if (loading != "{}" && loading.isNotBlank()) {
            val inner = loading.trim().removePrefix("{").removeSuffix("}")
            if (inner.isNotBlank()) sb.append(inner).append(',')
        }
        if (inference != "{}" && inference.isNotBlank()) {
            val inner = inference.trim().removePrefix("{").removeSuffix("}")
            if (inner.isNotBlank()) sb.append(inner)
        }
        if (sb.last() == ',') sb.deleteCharAt(sb.length - 1)
        sb.append('}')
        return sb.toString()
    }

    private fun parseThinking(raw: String): Pair<String, String> {
        val content = StringBuilder()
        val thinking = StringBuilder()
        var i = 0
        while (i < raw.length) {
            val next = THINK_TAGS
                .map { (open, _) -> raw.indexOf(open, i) to open }
                .filter { it.first >= 0 }
                .minByOrNull { it.first }
            if (next == null) {
                content.append(raw, i, raw.length)
                break
            }
            val (start, openTag) = next
            content.append(raw, i, start)
            val closeTag = THINK_TAGS.first { it.first == openTag }.second
            val bodyStart = start + openTag.length
            val end = raw.indexOf(closeTag, bodyStart)
            if (end < 0) {
                if (thinking.isNotEmpty()) thinking.append("\n\n")
                thinking.append(raw, bodyStart, raw.length)
                i = raw.length
            } else {
                if (thinking.isNotEmpty()) thinking.append("\n\n")
                thinking.append(raw, bodyStart, end)
                i = end + closeTag.length
            }
        }
        return content.toString().trim() to thinking.toString().trim()
    }

    private companion object {
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"

        val THINK_TAGS = listOf(
            "<think>" to "</think>",
            "[THINK]" to "[/THINK]",
            "<reasoning>" to "</reasoning>",
        )
    }
}
