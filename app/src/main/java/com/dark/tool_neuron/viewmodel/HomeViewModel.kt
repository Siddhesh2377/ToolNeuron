package com.dark.tool_neuron.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.tool_neuron.model.Chat
import com.dark.tool_neuron.model.ChatDocument
import com.dark.tool_neuron.model.ChatMessage
import com.dark.tool_neuron.model.Citation
import com.dark.tool_neuron.model.MemoryMetrics
import com.dark.tool_neuron.model.MessageKind
import com.dark.tool_neuron.model.ModelInfo
import com.dark.tool_neuron.model.TextMetrics
import com.dark.tool_neuron.model.enums.ProviderType
import com.dark.tool_neuron.model.ResearchEvent
import com.dark.tool_neuron.model.ResearchUiState
import com.dark.tool_neuron.repo.ChatRepository
import com.dark.tool_neuron.repo.ModelRepository
import com.dark.tool_neuron.repo.RagManager
import com.dark.tool_neuron.service.server.ServerController
import com.dark.tool_neuron.service.inference.InferenceClient
import com.dark.tool_neuron.viewmodel.home_vm.GenerationOutcome
import com.dark.tool_neuron.viewmodel.home_vm.GenerationStatus
import com.dark.tool_neuron.viewmodel.home_vm.InferenceCoordinator
import com.dark.tool_neuron.viewmodel.home_vm.ModelLoadState
import com.dark.tool_neuron.viewmodel.home_vm.ModelSessionManager
import com.dark.tool_neuron.viewmodel.home_vm.PillState
import com.dark.tool_neuron.viewmodel.home_vm.StreamingFragment
import com.dark.tool_neuron.viewmodel.home_vm.ToolCallCoordinator
import com.dark.tool_neuron.voice.VoiceModelManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val app: Application,
    private val modelRepo: ModelRepository,
    private val chatRepo: ChatRepository,
    private val modelSession: ModelSessionManager,
    private val toolCallCoordinator: ToolCallCoordinator,
    private val inferenceCoordinator: InferenceCoordinator,
    private val ragManager: RagManager,
    private val voiceManager: VoiceModelManager,
    private val serverController: ServerController,
    private val researchCoordinator: ResearchCoordinator,
) : ViewModel() {

    val speakingMessageId: StateFlow<String?> = voiceManager.speakingId
    val isRecording: StateFlow<Boolean> = voiceManager.isRecording
    val recordingAmplitude: StateFlow<Float> = voiceManager.recordingAmplitude
    val voiceError: StateFlow<String?> = voiceManager.error

    private val _loadingSpeakId = MutableStateFlow<String?>(null)
    val loadingSpeakId: StateFlow<String?> = _loadingSpeakId.asStateFlow()
    private var speakJob: Job? = null

    private val _transcribedText = MutableStateFlow<String?>(null)
    val transcribedText: StateFlow<String?> = _transcribedText.asStateFlow()

    private val _isTranscribing = MutableStateFlow(false)
    val isTranscribing: StateFlow<Boolean> = _isTranscribing.asStateFlow()

    fun clearVoiceError() { voiceManager.clearError() }
    fun consumeTranscribedText() { _transcribedText.value = null }

    fun speakMessage(messageId: String, text: String) {
        startSpeakJob(messageId, text)
    }

    fun stopSpeaking() {
        speakJob?.cancel()
        speakJob = null
        _loadingSpeakId.value = null
        voiceManager.stopSpeaking()
    }

    fun toggleSpeakMessage(messageId: String, text: String) {
        if (voiceManager.speakingId.value == messageId || _loadingSpeakId.value == messageId) {
            stopSpeaking()
        } else {
            startSpeakJob(messageId, text)
        }
    }

    private fun startSpeakJob(messageId: String, text: String) {
        speakJob?.cancel()
        _loadingSpeakId.value = messageId
        speakJob = viewModelScope.launch {
            try {
                voiceManager.speak(messageId, text)
            } finally {
                if (_loadingSpeakId.value == messageId) _loadingSpeakId.value = null
            }
        }
    }

    fun startRecording(): Boolean = voiceManager.startRecording()

    fun cancelRecording() { voiceManager.cancelRecording() }

    fun stopRecordingAndTranscribe() {
        viewModelScope.launch {
            _isTranscribing.value = true
            try {
                val text = voiceManager.stopRecordingAndRecognize()?.trim()
                if (!text.isNullOrEmpty()) _transcribedText.value = text
            } finally {
                _isTranscribing.value = false
                voiceManager.unloadStt()
            }
        }
    }

    fun voiceSttAvailable(): Boolean = voiceManager.hasStt()
    fun voiceTtsAvailable(): Boolean = voiceManager.hasTts()
    fun voiceMicGranted(): Boolean = voiceManager.sttPermissionGranted()

    val installedModels: StateFlow<List<ModelInfo>> = modelRepo.models

    val chatModels: StateFlow<List<ModelInfo>> = modelRepo.models
        .map { list -> list.filter { it.providerType == ProviderType.GGUF } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
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

    private val _webSearchEnabled = MutableStateFlow(false)
    val webSearchEnabled = _webSearchEnabled.asStateFlow()

    private val _researchEnabled = MutableStateFlow(false)
    val researchEnabled: StateFlow<Boolean> = _researchEnabled.asStateFlow()

    val researchActive: StateFlow<Boolean> = researchCoordinator.activeRuns
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val researchMessages = mutableMapOf<String, Pair<String, String>>()

    private val _thinkingEnabled = MutableStateFlow(false)
    val thinkingEnabled = _thinkingEnabled.asStateFlow()

    private val _chatDocuments = MutableStateFlow<List<ChatDocument>>(emptyList())
    val chatDocuments: StateFlow<List<ChatDocument>> = _chatDocuments.asStateFlow()

    private val _pendingImages = MutableStateFlow<List<Uri>>(emptyList())
    val pendingImages: StateFlow<List<Uri>> = _pendingImages.asStateFlow()

    val vlmError: StateFlow<String?> = modelSession.vlmAutoLoadError

    val isVlmLoaded: StateFlow<Boolean> = InferenceClient.isVlmLoaded

    private val _documentError = MutableStateFlow<String?>(null)
    val documentError: StateFlow<String?> = _documentError.asStateFlow()

    private val _isIngestingDocument = MutableStateFlow(false)
    val isIngestingDocument: StateFlow<Boolean> = _isIngestingDocument.asStateFlow()

    val ragReady: StateFlow<Boolean> = ragManager.isReady
    val activeEmbeddingName: StateFlow<String?> = ragManager.activeEmbeddingName
    val retrievalStatus: StateFlow<RagManager.RetrievalStatus> = ragManager.retrievalStatus
    val deepIndexing: StateFlow<Set<String>> = ragManager.deepIndexing
    val raptorBuilding: StateFlow<Set<String>> = ragManager.raptorBuilding

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
            combine(InferenceClient.isModelLoaded, InferenceClient.contextUsage) { loaded, usage ->
                if (loaded) usage else 0f
            }.collect { _contextUsage.value = it }
        }

        viewModelScope.launch {
            researchCoordinator.events.collect { event -> handleResearchEvent(event) }
        }
    }

    private fun handleResearchEvent(event: ResearchEvent) {
        val (chatId, messageId) = researchMessages[event.runId] ?: return
        val msg = chatRepo.getMessageById(messageId) ?: return
        val current = ResearchUiState.fromJson(msg.researchState)
        val next = current.applyEvent(event)
        chatRepo.updateMessage(msg.copy(researchState = next.toJson()))
        if (chatId == _currentChatId.value) {
            _messages.value = chatRepo.getMessages(chatId)
        }
        if (event is ResearchEvent.Done || event is ResearchEvent.Cancelled || event is ResearchEvent.Failed) {
            researchMessages.remove(event.runId)
        }
    }

    fun toggleActionWindow() { _actionWindowExpanded.value = !_actionWindowExpanded.value }
    fun collapseActionWindow() { _actionWindowExpanded.value = false }
    fun toggleWebSearch() { _webSearchEnabled.value = !_webSearchEnabled.value }
    fun toggleResearch() { _researchEnabled.value = !_researchEnabled.value }
    fun toggleThinking() { _thinkingEnabled.value = !_thinkingEnabled.value }

    fun cancelResearch(runId: String) {
        val ref = researchMessages[runId]
        if (ref != null) {
            val (chatId, msgId) = ref
            val msg = chatRepo.getMessageById(msgId)
            if (msg != null) {
                val current = ResearchUiState.fromJson(msg.researchState)
                if (!current.isStopping() && current.phase !in setOf(
                        ResearchUiState.PHASE_DONE,
                        ResearchUiState.PHASE_CANCELLED,
                        ResearchUiState.PHASE_FAILED,
                    )
                ) {
                    val stopping = current.copy(phase = ResearchUiState.PHASE_STOPPING)
                    chatRepo.updateMessage(msg.copy(researchState = stopping.toJson()))
                    if (chatId == _currentChatId.value) {
                        _messages.value = chatRepo.getMessages(chatId)
                    }
                }
            }
        }
        researchCoordinator.cancel(runId)
    }
    fun toggleLoadModelWindow() {
        if (_isGenerating.value) {
            _loadModelWindow.value = false
            return
        }
        _loadModelWindow.value = !_loadModelWindow.value
    }

    fun addImage(uri: Uri) {
        runCatching {
            app.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        _pendingImages.value = _pendingImages.value + uri
    }

    fun removeImage(uri: Uri) {
        _pendingImages.value = _pendingImages.value.filterNot { it == uri }
    }

    fun clearPendingImages() { _pendingImages.value = emptyList() }

    fun clearVlmError() { modelSession.clearVlmAutoLoadError() }

    fun addDocument(uri: Uri, name: String, size: Long, mimeType: String?) {
        val active = activeModel.value ?: run {
            _documentError.value = "Load a chat model before adding documents."
            return
        }
        val chatId = _currentChatId.value ?: ensureChat(active)
        viewModelScope.launch {
            _isIngestingDocument.value = true
            _documentError.value = null
            val result = ragManager.ingestDocument(chatId, uri, name, size, mimeType)
            _isIngestingDocument.value = false
            result.onSuccess { doc ->
                _chatDocuments.value = _chatDocuments.value + doc
            }.onFailure { err ->
                _documentError.value = err.message ?: "Failed to add document"
            }
        }
    }

    fun removeDocument(docId: String) {
        viewModelScope.launch {
            ragManager.removeDocument(docId)
            _chatDocuments.value = _chatDocuments.value.filter { it.id != docId }
        }
    }

    fun deepIndexDocument(docId: String) {
        viewModelScope.launch {
            _documentError.value = null
            val result = ragManager.deepIndex(docId)
            result.onSuccess { updated ->
                _chatDocuments.value = _chatDocuments.value.map { if (it.id == updated.id) updated else it }
            }.onFailure { err ->
                _documentError.value = err.message ?: "Deep index failed"
            }
        }
    }

    fun buildRaptor(docId: String) {
        if (raptorBuilding.value.contains(docId)) return
        viewModelScope.launch {
            _documentError.value = null
            val result = ragManager.buildRaptorTree(docId)
            result.onSuccess { updated ->
                _chatDocuments.value = _chatDocuments.value.map { if (it.id == updated.id) updated else it }
            }.onFailure { err ->
                _documentError.value = err.message ?: "RAPTOR build failed"
            }
        }
    }

    fun clearDocumentError() { _documentError.value = null }

    fun prepareRagIfAvailable() {
        viewModelScope.launch { ragManager.ensureReady() }
    }

    fun selectChat(chatId: String) {
        _currentChatId.value = chatId
        _messages.value = chatRepo.getMessages(chatId)
        val docs = ragManager.documentsForChat(chatId)
        _chatDocuments.value = docs
        if (docs.isNotEmpty()) {
            viewModelScope.launch {
                val hydrated = ragManager.hydrateChat(chatId)
                if (_currentChatId.value == chatId) _chatDocuments.value = hydrated
            }
        }
    }

    fun attachDocumentFromPrevChat(source: ChatDocument) {
        val active = activeModel.value ?: run {
            _documentError.value = "Load a chat model before adding documents."
            return
        }
        val chatId = _currentChatId.value ?: ensureChat(active)
        viewModelScope.launch {
            _isIngestingDocument.value = true
            _documentError.value = null
            val result = ragManager.attachExisting(chatId, source)
            _isIngestingDocument.value = false
            result.onSuccess { doc ->
                if (_currentChatId.value == chatId) {
                    val current = _chatDocuments.value
                    if (current.none { it.id == doc.id }) {
                        _chatDocuments.value = current + doc
                    }
                }
            }.onFailure { err ->
                _documentError.value = err.message ?: "Failed to attach document"
            }
        }
    }

    fun documentsByChat(): List<Pair<Chat, List<ChatDocument>>> {
        val activeChatId = _currentChatId.value
        val activeSourceIds = activeChatId?.let { id ->
            ragManager.documentsForChat(id).mapNotNullTo(mutableSetOf()) { it.sourceId.takeIf { s -> s.isNotBlank() } }
        } ?: emptySet()
        val docsByChatId = ragManager.allDocuments()
            .asSequence()
            .filter { it.sourceId.isNotBlank() }
            .filter { it.chatId != null && it.chatId != activeChatId }
            .filter { it.sourceId !in activeSourceIds }
            .groupBy { it.chatId!! }
        return chatRepo.chats.value
            .asSequence()
            .filter { it.id != activeChatId }
            .mapNotNull { chat ->
                val docs = docsByChatId[chat.id].orEmpty().sortedByDescending { it.addedAt }
                if (docs.isEmpty()) null else chat to docs
            }
            .toList()
    }

    fun createNewChat() {
        _currentChatId.value = null
        _messages.value = emptyList()
        _chatDocuments.value = emptyList()
    }

    fun deleteChat(chatId: String) {
        chatRepo.deleteChat(chatId)
        if (_currentChatId.value == chatId) createNewChat()
    }

    fun pinChat(chatId: String, pinned: Boolean) {
        chatRepo.pinChat(chatId, pinned)
    }

    fun loadModel(model: ModelInfo) {
        if (serverController.isBusy) return
        if (_isGenerating.value) return
        if (researchActive.value) return
        viewModelScope.launch { modelSession.load(model) }
    }

    fun unloadModel() {
        if (serverController.isBusy) return
        if (_isGenerating.value) return
        if (researchActive.value) return
        viewModelScope.launch { modelSession.unload() }
    }

    fun sendMessage(text: String) {
        if (serverController.isBusy) return
        if (researchActive.value) return
        val trimmed = text.trim()
        val images = _pendingImages.value
        if (_isGenerating.value) return
        if (trimmed.isEmpty() && images.isEmpty()) return
        val active = activeModel.value ?: run {
            _loadModelWindow.value = true
            return
        }

        val researchQuestion = parseResearchInput(trimmed)
        if (researchQuestion != null) {
            startResearch(active, researchQuestion)
            return
        }

        val chatId = ensureChat(active)
        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            chatId = chatId,
            role = ROLE_USER,
            content = trimmed,
            timestamp = System.currentTimeMillis(),
            imageUris = images.map { it.toString() },
            kind = if (images.isNotEmpty()) MessageKind.Image else MessageKind.Text,
        )
        chatRepo.addMessage(userMessage)
        _pendingImages.value = emptyList()

        val isFirstTurn = chatRepo.getMessages(chatId).count { it.role == ROLE_USER } == 1
        runGeneration(chatId, isFirstTurn, trimmed)
    }

    private fun parseResearchInput(text: String): String? {
        if (text.startsWith("/research", ignoreCase = true)) {
            val q = text.removePrefix("/research").removePrefix("/RESEARCH").trim()
            return q.ifBlank { null }
        }
        if (_researchEnabled.value) return text
        return null
    }

    private fun startResearch(active: ModelInfo, question: String) {
        if (researchActive.value) return
        val chatId = ensureChat(active)
        val isFirstTurn = chatRepo.getMessages(chatId).count { it.role == ROLE_USER } == 1
        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            chatId = chatId,
            role = ROLE_USER,
            content = question,
            timestamp = System.currentTimeMillis(),
            kind = MessageKind.Text,
        )
        chatRepo.addMessage(userMessage)
        _pendingImages.value = emptyList()

        val cardMessageId = UUID.randomUUID().toString()
        val placeholderRunId = "pending_$cardMessageId"
        val cardMessage = ChatMessage(
            id = cardMessageId,
            chatId = chatId,
            role = ROLE_ASSISTANT,
            content = question,
            timestamp = System.currentTimeMillis() + 1,
            kind = MessageKind.Text,
            modelName = active.name,
            researchRunId = placeholderRunId,
            researchState = ResearchUiState().toJson(),
        )
        chatRepo.addMessage(cardMessage)

        val runId = researchCoordinator.start(
            scope = viewModelScope,
            chatId = chatId,
            messageId = cardMessageId,
            question = question,
            modelId = active.id,
            modelName = active.name,
        )
        researchMessages[runId] = chatId to cardMessageId

        chatRepo.updateMessage(cardMessage.copy(researchRunId = runId))
        _messages.value = chatRepo.getMessages(chatId)
        if (isFirstTurn) chatRepo.autoTitle(chatId, question)
    }

    fun regenerateLast() {
        if (_isGenerating.value) return
        val chatId = _currentChatId.value ?: return
        if (activeModel.value == null) {
            _loadModelWindow.value = true
            return
        }
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

    fun forkFromMessage(messageId: String) {
        if (_isGenerating.value) return
        val sourceChatId = _currentChatId.value ?: return
        val newChat = chatRepo.forkChat(sourceChatId, messageId) ?: return
        selectChat(newChat.id)
    }

    fun editUserMessage(messageId: String, newContent: String) {
        if (_isGenerating.value) return
        val chatId = _currentChatId.value ?: return
        val trimmed = newContent.trim()
        if (trimmed.isEmpty()) return

        val all = chatRepo.getMessages(chatId)
        val target = all.firstOrNull { it.id == messageId } ?: return
        if (target.role != ROLE_USER) return
        if (target.content == trimmed) return

        chatRepo.updateMessage(target.copy(content = trimmed))

        all.filter { it.timestamp > target.timestamp }
            .forEach { chatRepo.deleteMessage(it.id) }

        _messages.value = chatRepo.getMessages(chatId)

        if (activeModel.value != null) {
            runGeneration(chatId, isFirstTurn = false, userText = trimmed)
        } else {
            _loadModelWindow.value = true
        }
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
        _loadModelWindow.value = false

        val thinkingOn = _thinkingEnabled.value && modelSession.supportsThinking.value
        modelSession.setThinkingEnabled(thinkingOn)
        toolCallCoordinator.configureInference(_webSearchEnabled.value)

        generationJob = viewModelScope.launch {
            var outcome: GenerationOutcome? = null
            var fallbackError: String? = null
            var wasStopped = false
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
                wasStopped = true
            } catch (e: Exception) {
                fallbackError = e.message
            } finally {
                val partial = _streamingFragment.value
                val resolvedContent = outcome?.content?.takeIf { it.isNotBlank() }
                    ?: partial?.content.orEmpty()
                val resolvedThinking = outcome?.thinking?.takeIf { it.isNotBlank() }
                    ?: partial?.thinkingContent.orEmpty()
                finalizeAssistantMessage(
                    chatId = chatId,
                    content = resolvedContent,
                    thinking = resolvedThinking,
                    error = if (wasStopped) null else (outcome?.error ?: fallbackError),
                    isFirstTurn = isFirstTurn,
                    userText = userText,
                    textMetrics = outcome?.textMetrics,
                    memoryMetrics = outcome?.memoryMetrics,
                    citations = outcome?.citations.orEmpty(),
                    wasStopped = wasStopped,
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
        citations: List<Citation> = emptyList(),
        wasStopped: Boolean = false,
    ) {
        if (wasStopped && content.isBlank() && thinking.isBlank()) {
            _streamingFragment.value = null
            _isGenerating.value = false
            return
        }
        val finalContent = when {
            error != null && content.isBlank() -> "Error: $error"
            error != null -> "$content\n\n_Error: ${error}_"
            wasStopped -> "$content\n\n_Stopped_"
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
            citations = citations,
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
