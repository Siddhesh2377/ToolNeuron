package com.dark.tool_neuron.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.tool_neuron.model.Chat
import com.dark.tool_neuron.model.ChatDocument
import com.dark.tool_neuron.model.ChatMessage
import com.dark.tool_neuron.model.MemoryMetrics
import com.dark.tool_neuron.model.MessageKind
import com.dark.tool_neuron.model.ModelInfo
import com.dark.tool_neuron.model.TextMetrics
import com.dark.tool_neuron.repo.ChatRepository
import com.dark.tool_neuron.repo.ModelRepository
import com.dark.tool_neuron.service.inference.InferenceClient
import com.dark.tool_neuron.viewmodel.home_vm.GenerationOutcome
import com.dark.tool_neuron.viewmodel.home_vm.GenerationStatus
import com.dark.tool_neuron.viewmodel.home_vm.InferenceCoordinator
import com.dark.tool_neuron.viewmodel.home_vm.ModelLoadState
import com.dark.tool_neuron.viewmodel.home_vm.ModelSessionManager
import com.dark.tool_neuron.viewmodel.home_vm.PillState
import com.dark.tool_neuron.viewmodel.home_vm.StreamingFragment
import com.dark.tool_neuron.viewmodel.home_vm.ToolCallCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

private const val ROLE_USER = "user"
private const val ROLE_ASSISTANT = "assistant"
private val WHITESPACE = "\\s+".toRegex()

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val modelRepo: ModelRepository,
    private val chatRepo: ChatRepository,
    private val modelSession: ModelSessionManager,
    private val toolCallCoordinator: ToolCallCoordinator,
    private val inferenceCoordinator: InferenceCoordinator,
) : ViewModel() {

    val installedModels: StateFlow<List<ModelInfo>> = modelRepo.models
    val chats: StateFlow<List<Chat>> = chatRepo.chats
    val modelLoadState: StateFlow<ModelLoadState> = modelSession.loadState
    val supportsThinking: StateFlow<Boolean> = modelSession.supportsThinking

    val activeModel: StateFlow<ModelInfo?> = installedModels
        .map { list -> list.firstOrNull { it.isActive } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _streamingFragment = MutableStateFlow<StreamingFragment?>(null)
    val streamingFragment: StateFlow<StreamingFragment?> = _streamingFragment.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

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

    private val _lastTextMetrics = MutableStateFlow<TextMetrics?>(null)
    val lastTextMetrics: StateFlow<TextMetrics?> = _lastTextMetrics.asStateFlow()

    private val _lastMemoryMetrics = MutableStateFlow<MemoryMetrics?>(null)
    val lastMemoryMetrics: StateFlow<MemoryMetrics?> = _lastMemoryMetrics.asStateFlow()

    private val _contextUsage = MutableStateFlow(0f)
    val contextUsage: StateFlow<Float> = _contextUsage.asStateFlow()

    val pillState: StateFlow<PillState> = combine(
        InferenceClient.isModelLoaded,
        modelSession.loadState,
        _isGenerating,
        _thinkingEnabled,
        modelSession.supportsThinking,
    ) { isLoaded, loadState, generating, thinkingOn, supportsThink ->
        when {
            generating && thinkingOn && supportsThink -> PillState.Thinking
            generating -> PillState.Generating
            loadState is ModelLoadState.Loading -> PillState.Loading
            loadState is ModelLoadState.Error -> PillState.Error
            isLoaded -> PillState.Loaded
            else -> PillState.Idle
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PillState.Idle)

    val generationStatus: StateFlow<GenerationStatus> = combine(
        InferenceClient.isModelLoaded,
        modelSession.loadState,
        _isGenerating,
        _thinkingEnabled,
        modelSession.supportsThinking,
        activeModel,
        _messages,
        _streamingFragment,
        _contextUsage,
    ) { args ->
        val isLoaded = args[0] as Boolean
        val loadState = args[1] as ModelLoadState
        val generating = args[2] as Boolean
        val thinkingOn = args[3] as Boolean
        val supportsThink = args[4] as Boolean
        val active = args[5] as ModelInfo?
        val msgs = @Suppress("UNCHECKED_CAST") (args[6] as List<ChatMessage>)
        val streaming = args[7] as StreamingFragment?
        val ctxUsage = args[8] as Float

        val modelName = active?.name ?: streaming?.let { "Model" } ?: ""
        when {
            loadState is ModelLoadState.Loading -> GenerationStatus.ModelLoading(modelName.ifBlank { "Model" })
            loadState is ModelLoadState.Error -> GenerationStatus.Error(loadState.message, modelName.ifBlank { null })
            generating -> {
                val words = streaming?.content?.wordCount() ?: 0
                if (thinkingOn && supportsThink) {
                    GenerationStatus.Thinking(modelName.ifBlank { "Model" }, words, ctxUsage)
                } else {
                    GenerationStatus.GeneratingText(modelName.ifBlank { "Model" }, words, ctxUsage)
                }
            }
            !isLoaded && msgs.isEmpty() -> GenerationStatus.Welcome
            !isLoaded -> GenerationStatus.NoModelLoaded
            else -> GenerationStatus.Hidden
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, GenerationStatus.Welcome)

    private var generationJob: Job? = null

    init {
        viewModelScope.launch {
            combine(InferenceClient.isModelLoaded, _isGenerating) { loaded, gen ->
                loaded to gen
            }.collectLatest { (loaded, gen) ->
                if (!loaded) {
                    _contextUsage.value = 0f
                    return@collectLatest
                }
                _contextUsage.value = InferenceClient.getContextUsage()
                while (gen) {
                    delay(250)
                    _contextUsage.value = InferenceClient.getContextUsage()
                }
            }
        }
    }

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
        viewModelScope.launch { modelSession.load(model) }
    }

    fun unloadModel() {
        viewModelScope.launch { modelSession.unload() }
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
        modelSession.stopGeneration()
        generationJob?.cancel()
    }

    private fun runGeneration(chatId: String, isFirstTurn: Boolean, userText: String) {
        _messages.value = chatRepo.getMessages(chatId)
        _streamingFragment.value = StreamingFragment(chatId, "", "")
        _isGenerating.value = true

        val thinkingOn = _thinkingEnabled.value && modelSession.supportsThinking.value
        modelSession.setThinkingEnabled(thinkingOn)
        toolCallCoordinator.configureInference(_webSearchEnabled.value)

        generationJob = viewModelScope.launch {
            var outcome: GenerationOutcome? = null
            var fallbackError: String? = null
            try {
                outcome = inferenceCoordinator.run(
                    chatId = chatId,
                    onStreamingUpdate = { content, thinking ->
                        _streamingFragment.value = StreamingFragment(chatId, content, thinking)
                    },
                    onToolExecuted = { msgs -> _messages.value = msgs },
                    onMetrics = { text, mem ->
                        text?.let { _lastTextMetrics.value = it }
                        mem?.let { _lastMemoryMetrics.value = it }
                    },
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fallbackError = e.message
            } finally {
                finalizeAssistantMessage(
                    chatId = chatId,
                    content = outcome?.content.orEmpty(),
                    thinking = outcome?.thinking.orEmpty(),
                    error = outcome?.error ?: fallbackError,
                    isFirstTurn = isFirstTurn,
                    userText = userText,
                    textMetrics = outcome?.textMetrics,
                    memoryMetrics = outcome?.memoryMetrics,
                )
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
        textMetrics: TextMetrics?,
        memoryMetrics: MemoryMetrics?,
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
            kind = MessageKind.Text,
            modelName = activeModel.value?.name.orEmpty(),
            textMetrics = textMetrics,
            memoryMetrics = memoryMetrics,
        )
        chatRepo.addMessage(finalMessage)
        if (isFirstTurn) chatRepo.autoTitle(chatId, userText)
        _messages.value = chatRepo.getMessages(chatId)
        _streamingFragment.value = null
        _isGenerating.value = false
    }

    private fun String.wordCount(): Int {
        if (isBlank()) return 0
        return trim().split(WHITESPACE).count { it.isNotEmpty() }
    }
}
