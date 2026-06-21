package com.dark.tool_neuron.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
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
import com.dark.tool_neuron.model.WebSearchEvent
import com.dark.tool_neuron.model.WebSearchUiState
import com.dark.gguf_lib.ImageQuality
import com.dark.tool_neuron.data.AppPreferences
import com.dark.tool_neuron.data.AppVisibility
import com.dark.tool_neuron.data.UserNotificationManager
import com.dark.tool_neuron.repo.ChatRepository
import com.dark.tool_neuron.repo.DownloadCoordinator
import com.dark.tool_neuron.repo.ModelRepository
import com.dark.tool_neuron.repo.RagManager
import com.dark.tool_neuron.repo.VlmVisionCacheRepository
import com.dark.tool_neuron.ui.components.ContextStatsReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import com.dark.tool_neuron.service.server.ServerController
import com.dark.tool_neuron.service.inference.InferenceClient
import com.dark.tool_neuron.service.inference.InferenceEvent
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch
import com.dark.tool_neuron.util.ChatExporter
import com.dark.tool_neuron.util.ChatShareHelper
import com.dark.tool_neuron.util.ExportFormat
import java.util.UUID
import javax.inject.Inject

private const val ROLE_USER = "user"
private const val ROLE_ASSISTANT = "assistant"
private const val TAG_PRECOMPUTE = "VlmPrecompute"
private val WHITESPACE = "\\s+".toRegex()

enum class ImageEncodeStatus { Pending, Encoding, Cached, Error }

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
    private val webSearchCoordinator: WebSearchCoordinator,
    private val appPrefs: AppPreferences,
    private val vlmCache: VlmVisionCacheRepository,
    private val downloadCoordinator: DownloadCoordinator,
    private val appVisibility: AppVisibility,
    private val userNotifications: UserNotificationManager,
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
        .map { list ->
            list.filter {
                it.providerType == ProviderType.GGUF ||
                    it.providerType == ProviderType.VISION_CHAT
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val embeddingModelInstalled: StateFlow<Boolean> = modelRepo.models
        .map { list -> list.any { it.providerType == ProviderType.EMBEDDING } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
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

    val webSearchActive: StateFlow<Boolean> = webSearchCoordinator.activeRuns
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val webSearchMessages = mutableMapOf<String, Pair<String, String>>()

    private val _thinkingEnabled = MutableStateFlow(false)
    val thinkingEnabled = _thinkingEnabled.asStateFlow()

    private val _chatDocuments = MutableStateFlow<List<ChatDocument>>(emptyList())
    val chatDocuments: StateFlow<List<ChatDocument>> = _chatDocuments.asStateFlow()

    private val _pendingImages = MutableStateFlow<List<Uri>>(emptyList())
    val pendingImages: StateFlow<List<Uri>> = _pendingImages.asStateFlow()

    private val _imageEncodeStatus = MutableStateFlow<Map<Uri, ImageEncodeStatus>>(emptyMap())
    val imageEncodeStatus: StateFlow<Map<Uri, ImageEncodeStatus>> = _imageEncodeStatus.asStateFlow()
    private val precomputeJobs = mutableMapOf<Uri, Job>()

    private val _contextStatsReport = MutableStateFlow<ContextStatsReport?>(null)
    val contextStatsReport: StateFlow<ContextStatsReport?> = _contextStatsReport.asStateFlow()

    fun openContextStats() {
        viewModelScope.launch {
            _contextStatsReport.value = withContext(Dispatchers.IO) { buildContextStatsReport() }
        }
    }

    fun dismissContextStats() { _contextStatsReport.value = null }

    private fun buildContextStatsReport(): ContextStatsReport {
        val mem = parseJsonOrNull(InferenceClient.getMemoryStatsJson())
        val vt  = parseJsonOrNull(InferenceClient.getVtCacheStatsJson())
        val vkv = parseJsonOrNull(InferenceClient.getVlmKvCacheStatsJson())
        val visionStats = vlmCache.stats()
        return ContextStatsReport(
            nCtx              = mem?.optInt("n_ctx", 0) ?: 0,
            nUsed             = mem?.optInt("n_used", 0) ?: 0,
            contextUsagePct   = mem?.optDouble("context_usage_pct", 0.0) ?: 0.0,
            modelMb           = mem?.optDouble("model_mb", 0.0) ?: 0.0,
            kvCacheMb         = mem?.optDouble("kv_cache_mb", 0.0) ?: 0.0,
            currentRssMb      = mem?.optDouble("current_rss_mb", 0.0) ?: 0.0,
            peakRssMb         = mem?.optDouble("peak_rss_mb", 0.0) ?: 0.0,
            memAvailableMb    = mem?.optDouble("mem_available_mb", 0.0) ?: 0.0,
            memTotalMb        = mem?.optDouble("mem_total_mb", 0.0) ?: 0.0,
            threadMode        = mem?.optInt("thread_mode", 0) ?: 0,
            modelLoaded       = mem?.optBoolean("model_loaded", false) ?: false,
            vlmLoaded         = mem?.optBoolean("vlm_loaded", false) ?: false,
            vtCacheInit       = mem?.optBoolean("vt_cache_init", false) ?: false,
            vtCacheEntries    = vt?.optInt("entry_count", 0) ?: 0,
            vtCacheBytes      = vt?.optLong("total_bytes", 0L) ?: 0L,
            vtCacheHits       = vt?.optLong("hits", 0L) ?: 0L,
            vtCacheMisses     = vt?.optLong("misses", 0L) ?: 0L,
            vlmKvCacheInit    = mem?.optBoolean("vlm_kv_cache_init", false) ?: false,
            vlmKvCacheEntries = vkv?.optInt("entry_count", 0) ?: 0,
            vlmKvCacheBytes   = vkv?.optLong("total_bytes", 0L) ?: 0L,
            vlmKvCacheHits    = vkv?.optLong("hits", 0L) ?: 0L,
            vlmKvCacheMisses  = vkv?.optLong("misses", 0L) ?: 0L,
            visionIndexEntries = visionStats.entryCount,
            visionIndexBytes   = visionStats.totalBytes,
        )
    }

    private fun parseJsonOrNull(json: String?): JSONObject? =
        json?.takeIf { it.isNotBlank() }?.let { runCatching { JSONObject(it) }.getOrNull() }

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

    private val _manualGenerationStatus = MutableStateFlow<GenerationStatus?>(null)

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
        _manualGenerationStatus,
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
        val manualStatus = args[9] as GenerationStatus?

        val modelName = active?.name ?: streaming?.let { "Model" } ?: ""
        when {
            manualStatus != null -> manualStatus
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

    private fun showTemporaryStatus(status: GenerationStatus, durationMs: Long = 4_500L) {
        _manualGenerationStatus.value = status
        viewModelScope.launch {
            delay(durationMs)
            if (_manualGenerationStatus.value == status) _manualGenerationStatus.value = null
        }
    }

    private var generationJob: Job? = null

    private val _compactionState = MutableStateFlow(CompactionState())
    val compactionState: StateFlow<CompactionState> = _compactionState.asStateFlow()
    private var compactionJob: Job? = null

    init {
        viewModelScope.launch {
            combine(InferenceClient.isModelLoaded, InferenceClient.contextUsage) { loaded, usage ->
                if (loaded) usage else 0f
            }.collect { _contextUsage.value = it }
        }

        viewModelScope.launch {
            webSearchCoordinator.events.collect { event -> handleWebSearchEvent(event) }
        }
    }

    private fun handleWebSearchEvent(event: WebSearchEvent) {
        val (chatId, messageId) = webSearchMessages[event.runId] ?: return
        val msg = chatRepo.getMessageById(messageId) ?: return
        val current = WebSearchUiState.fromJson(msg.webSearchState)
        val next = current.applyEvent(event)
        chatRepo.updateMessage(msg.copy(webSearchState = next.toJson()))
        if (chatId == _currentChatId.value) {
            _messages.value = chatRepo.getMessages(chatId)
        }
        if (event is WebSearchEvent.Done || event is WebSearchEvent.Cancelled || event is WebSearchEvent.Failed) {
            webSearchMessages.remove(event.runId)
        }
    }

    fun toggleActionWindow() { _actionWindowExpanded.value = !_actionWindowExpanded.value }
    fun collapseActionWindow() { _actionWindowExpanded.value = false }
    fun toggleWebSearch() {
        val next = !_webSearchEnabled.value
        _webSearchEnabled.value = next
        if (next) _thinkingEnabled.value = false
    }

    fun toggleThinking() {
        val next = !_thinkingEnabled.value
        _thinkingEnabled.value = next
        if (next) _webSearchEnabled.value = false
    }

    fun cancelWebSearch(runId: String) {
        val ref = webSearchMessages[runId]
        if (ref != null) {
            val (chatId, msgId) = ref
            val msg = chatRepo.getMessageById(msgId)
            if (msg != null) {
                val current = WebSearchUiState.fromJson(msg.webSearchState)
                if (!current.isStopping() && current.phase !in setOf(
                        WebSearchUiState.PHASE_DONE,
                        WebSearchUiState.PHASE_CANCELLED,
                        WebSearchUiState.PHASE_FAILED,
                    )
                ) {
                    val stopping = current.copy(phase = WebSearchUiState.PHASE_STOPPING)
                    chatRepo.updateMessage(msg.copy(webSearchState = stopping.toJson()))
                    if (chatId == _currentChatId.value) {
                        _messages.value = chatRepo.getMessages(chatId)
                    }
                }
            }
        }
        webSearchCoordinator.cancel(runId)
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
        if (_pendingImages.value.contains(uri)) return
        _pendingImages.value = _pendingImages.value + uri
        startPrecomputeFor(uri)
    }

    fun removeImage(uri: Uri) {
        _pendingImages.value = _pendingImages.value.filterNot { it == uri }
        precomputeJobs.remove(uri)?.cancel()
        _imageEncodeStatus.value = _imageEncodeStatus.value - uri
    }

    fun clearPendingImages() {
        _pendingImages.value = emptyList()
        precomputeJobs.values.forEach { it.cancel() }
        precomputeJobs.clear()
        _imageEncodeStatus.value = emptyMap()
    }

    private fun startPrecomputeFor(uri: Uri) {
        val activeId = activeModel.value?.id ?: run {
            updateStatus(uri, ImageEncodeStatus.Pending)
            return
        }
        precomputeJobs[uri]?.cancel()
        updateStatus(uri, ImageEncodeStatus.Pending)
        precomputeJobs[uri] = viewModelScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    app.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                } ?: run {
                    updateStatus(uri, ImageEncodeStatus.Error)
                    return@launch
                }
                val sha = withContext(Dispatchers.IO) { VlmVisionCacheRepository.sha256(bytes) }
                if (vlmCache.isCached(activeId, sha)) {
                    vlmCache.touch(activeId, sha)
                    updateStatus(uri, ImageEncodeStatus.Cached)
                    return@launch
                }
                if (!isVlmLoaded.value) {
                    updateStatus(uri, ImageEncodeStatus.Pending)
                    return@launch
                }
                updateStatus(uri, ImageEncodeStatus.Encoding)
                val quality = runCatching { ImageQuality.valueOf(appPrefs.vlmImageQuality) }
                    .getOrDefault(ImageQuality.MEDIUM)
                val ok = InferenceClient.precomputeVision(app, uri, quality)
                if (ok) {
                    vlmCache.markCached(activeId, sha, bytes.size.toLong(), imageMaxTokens = -1)
                    updateStatus(uri, ImageEncodeStatus.Cached)
                } else {
                    updateStatus(uri, ImageEncodeStatus.Error)
                }
            } catch (_: CancellationException) {
                throw kotlin.coroutines.cancellation.CancellationException()
            } catch (e: Exception) {
                Log.e(TAG_PRECOMPUTE, "precompute uri=$uri failed", e)
                updateStatus(uri, ImageEncodeStatus.Error)
            } finally {
                precomputeJobs.remove(uri)
            }
        }
    }

    private fun updateStatus(uri: Uri, status: ImageEncodeStatus) {
        _imageEncodeStatus.value = _imageEncodeStatus.value + (uri to status)
    }

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
            modelSession.prepareForHeavyBackgroundWork()
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
            modelSession.prepareForHeavyBackgroundWork()
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
            modelSession.prepareForHeavyBackgroundWork()
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

    fun exportChat(chatId: String, format: ExportFormat) {
        val chat = chatRepo.getChatById(chatId) ?: return
        val messages = chatRepo.getMessages(chatId)
        if (messages.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val body = ChatExporter.format(chat, messages, format)
            val filename = ChatExporter.suggestedFilename(chat, format)
            ChatShareHelper.writeAndShare(
                context = app,
                filename = filename,
                body = body,
                mimeType = format.mimeType,
                title = chat.title,
            )
        }
    }

    fun loadModel(model: ModelInfo) {
        if (serverController.isBusy) return
        if (_isGenerating.value) return
        if (webSearchActive.value) return
        modelSession.markActivity()
        viewModelScope.launch { modelSession.load(model) }
    }

    fun unloadModel() {
        if (serverController.isBusy) return
        if (_isGenerating.value) return
        if (webSearchActive.value) return
        viewModelScope.launch { modelSession.unload() }
    }

    fun sendMessage(text: String) {
        if (serverController.isBusy) return
        if (webSearchActive.value) return
        modelSession.markActivity()
        val trimmed = text.trim()
        val images = _pendingImages.value
        if (_isGenerating.value) return
        if (trimmed.isEmpty() && images.isEmpty()) return
        // Defense-in-depth: refuse send while any attached image is still
        // pre-warming. Firing generation while ViT is encoding pegs the
        // CPU on two threads at 96-100% each, starves the UI thread, and
        // ANRs after ~5 s. The Compose canSend gate should prevent this
        // too — this is the backstop in case a recomposition lags.
        val statusMap = _imageEncodeStatus.value
        if (images.any { uri ->
                val s = statusMap[uri] ?: ImageEncodeStatus.Pending
                s == ImageEncodeStatus.Pending || s == ImageEncodeStatus.Encoding
            }) return
        val active = activeModel.value
            ?: chatModels.value.firstOrNull()?.also { modelRepo.setActive(it.id) }
            ?: run {
                val message = if (downloadCoordinator.activeCount.value > 0) {
                    "A model is still downloading. Wait for it to finish, then send again."
                } else {
                    "Install or import a chat model before sending a message."
                }
                showTemporaryStatus(GenerationStatus.Error(message))
                _loadModelWindow.value = true
                return
            }
        if (images.isNotEmpty() && active.providerType != ProviderType.VISION_CHAT) {
            showTemporaryStatus(GenerationStatus.Error("Image chat requires a Vision chat model."))
            _loadModelWindow.value = true
            return
        }

        val webSearchQuery = parseWebSearchInput(trimmed)
        if (webSearchQuery != null) {
            startWebSearch(active, webSearchQuery)
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

        if (InferenceClient.isModelLoaded.value) {
            runGeneration(chatId, isFirstTurn, trimmed)
        } else {
            viewModelScope.launch {
                try {
                    modelSession.load(active)
                } catch (e: Exception) {
                    Log.w("HomeViewModel", "auto-load failed: ${e.message}")
                    return@launch
                }
                if (modelSession.loadState.value is ModelLoadState.Active) {
                    runGeneration(chatId, isFirstTurn, trimmed)
                }
            }
        }
    }

    private fun parseWebSearchInput(text: String): String? {
        if (text.startsWith("/search", ignoreCase = true)) {
            val q = text.removePrefix("/search").removePrefix("/SEARCH").trim()
            return q.ifBlank { null }
        }
        if (_webSearchEnabled.value) return text
        return null
    }

    private fun webSearchQueryWithContext(messages: List<ChatMessage>, userQuery: String): String {
        val normalized = userQuery.trim()
        val lowInformationFollowUp = normalized.split(Regex("\\s+")).size <= 4 &&
            (normalized.contains("link", ignoreCase = true) ||
                normalized.contains("url", ignoreCase = true) ||
                normalized.contains("download", ignoreCase = true) ||
                normalized.contains("exact", ignoreCase = true))
        if (!lowInformationFollowUp) return normalized

        val priorTopic = messages.asReversed()
            .asSequence()
            .filter { it.role == ROLE_USER }
            .map { it.content.trim() }
            .firstOrNull { content ->
                content.isNotBlank() &&
                    content.length > normalized.length &&
                    !content.equals(normalized, ignoreCase = true)
            }
            ?: return normalized
        return "$priorTopic $normalized"
    }

    private fun startWebSearch(active: ModelInfo, userQuery: String) {
        if (webSearchActive.value) return
        val chatId = ensureChat(active)
        val previousMessages = chatRepo.getMessages(chatId)
        val searchQuery = webSearchQueryWithContext(previousMessages, userQuery)
        val isFirstTurn = previousMessages.count { it.role == ROLE_USER } == 1
        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            chatId = chatId,
            role = ROLE_USER,
            content = userQuery,
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
            content = searchQuery,
            timestamp = System.currentTimeMillis() + 1,
            kind = MessageKind.Text,
            modelName = active.name,
            webSearchRunId = placeholderRunId,
            webSearchState = WebSearchUiState(userQuery = searchQuery).toJson(),
        )
        chatRepo.addMessage(cardMessage)

        val runId = webSearchCoordinator.start(
            scope = viewModelScope,
            userQuery = searchQuery,
        )
        webSearchMessages[runId] = chatId to cardMessageId

        chatRepo.updateMessage(cardMessage.copy(webSearchRunId = runId))
        _messages.value = chatRepo.getMessages(chatId)
        if (isFirstTurn) chatRepo.autoTitle(chatId, userQuery)
    }

    fun regenerateLast() {
        if (_isGenerating.value) return
        val chatId = _currentChatId.value ?: return
        val active = activeModel.value
        if (active == null) {
            _loadModelWindow.value = true
            return
        }
        val lastAssistant = chatRepo.getMessages(chatId).lastOrNull { it.role == ROLE_ASSISTANT }
            ?: return
        // Web-search cards are assistant messages too; if regenerate is hit
        // on one, re-run the search instead of falling into the chat path
        // (which would delete the card and treat the original user message
        // as a plain chat send).
        if (lastAssistant.webSearchRunId != null) {
            val query = lastAssistant.content
            chatRepo.deleteMessage(lastAssistant.id)
            val msgs = chatRepo.getMessages(chatId)
            val lastUser = msgs.lastOrNull { it.role == ROLE_USER }
            if (lastUser != null) chatRepo.deleteMessage(lastUser.id)
            _messages.value = chatRepo.getMessages(chatId)
            startWebSearch(active, query)
            return
        }
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

    fun editMessage(messageId: String, newContent: String) {
        if (_isGenerating.value) return
        val chatId = _currentChatId.value ?: return
        val trimmed = newContent.trim()
        if (trimmed.isEmpty()) return

        val all = chatRepo.getMessages(chatId)
        val target = all.firstOrNull { it.id == messageId } ?: return
        if (target.content == trimmed) return
        if (target.archivedByCompactId != null) return

        chatRepo.updateMessage(target.copy(content = trimmed))

        if (target.role == ROLE_USER) {
            all.filter { it.timestamp > target.timestamp }
                .forEach { chatRepo.deleteMessage(it.id) }
            _messages.value = chatRepo.getMessages(chatId)
            if (activeModel.value != null) {
                runGeneration(chatId, isFirstTurn = false, userText = trimmed)
            } else {
                _loadModelWindow.value = true
            }
        } else {
            _messages.value = chatRepo.getMessages(chatId)
        }
    }

    fun stopGeneration() {
        if (!_isGenerating.value) return
        modelSession.stopGeneration()
        generationJob?.cancel()
    }

    /**
     * Summarize the active chat via the inference service, then replace the
     * persisted chat history with a single assistant message containing the
     * summary. The KV cache is wiped by the service so the next user turn
     * prefills against the fresh summary-only prefix.
     */
    fun compactConversation() {
        if (serverController.isBusy) return
        if (_isGenerating.value) return
        if (_compactionState.value.active) return
        val chatId = _currentChatId.value ?: return
        val activeModelName = activeModel.value?.name ?: ""
        val existing = chatRepo.getMessages(chatId)
        if (existing.isEmpty()) return

        val arr = JSONArray()
        existing.forEach { msg ->
            if (msg.role != ROLE_USER && msg.role != ROLE_ASSISTANT) return@forEach
            arr.put(JSONObject().apply {
                put("role", msg.role)
                put("content", msg.content)
            })
        }
        if (arr.length() == 0) return
        val messagesJson = arr.toString()

        // Budget: assume model context is at least 2k; aim for a summary that
        // leaves room for at least one full turn after the swap.
        val maxSummaryTokens = 768

        val start = System.currentTimeMillis()
        val summary = StringBuilder()
        var tokensIn = 0
        var tokensOut = 0
        var promptProgress = 0f

        _compactionState.value = CompactionState(
            active = true,
            elapsedMs = 0,
            tokensIn = 0,
            fraction = 0f,
        )

        compactionJob = viewModelScope.launch {
            try {
                // transformWhile is what lets the flow terminate when the
                // service signals Done/Error. callbackFlow never closes
                // itself, so a plain collect would hang here and we'd
                // never archive + insert the summary card downstream.
                InferenceClient.compactConversation(messagesJson, maxSummaryTokens)
                    .transformWhile { event ->
                        emit(event)
                        event !is InferenceEvent.Done && event !is InferenceEvent.Error
                    }
                    .collect { event ->
                        when (event) {
                            is InferenceEvent.Token -> {
                                summary.append(event.text)
                                tokensOut += 1
                                // The generation phase advances the bar by
                                // elapsed time rather than token budget,
                                // since maxSummaryTokens (768) is usually
                                // a wild over-estimate of what the model
                                // emits (~60–150 tokens) and the
                                // budget-based scheme stalled near 52%.
                                val genElapsed = System.currentTimeMillis() - start
                                val genFrac = ((genElapsed - 1500).coerceAtLeast(0)
                                    .toFloat() / GEN_PHASE_TARGET_MS)
                                    .coerceIn(0f, 0.95f)
                                _compactionState.value = _compactionState.value.copy(
                                    elapsedMs = genElapsed,
                                    fraction = (0.5f + genFrac * 0.5f).coerceIn(0f, 0.97f),
                                )
                            }
                            is InferenceEvent.Progress -> {
                                promptProgress = event.progress.coerceIn(0f, 1f)
                                _compactionState.value = _compactionState.value.copy(
                                    elapsedMs = System.currentTimeMillis() - start,
                                    fraction = (promptProgress * 0.5f).coerceIn(0f, 0.5f),
                                )
                            }
                            is InferenceEvent.Metrics -> {
                                val j = runCatching { JSONObject(event.metricsJson) }.getOrNull()
                                if (j != null) {
                                    tokensIn = j.optInt("tokensEvaluated", tokensIn)
                                    tokensOut = j.optInt("tokensPredicted", tokensOut)
                                    _compactionState.value = _compactionState.value.copy(
                                        elapsedMs = System.currentTimeMillis() - start,
                                        tokensIn = tokensIn,
                                    )
                                }
                            }
                            is InferenceEvent.Done -> {
                                _compactionState.value = _compactionState.value.copy(
                                    elapsedMs = System.currentTimeMillis() - start,
                                    fraction = 1f,
                                )
                            }
                            is InferenceEvent.Error -> {
                                Log.w("HomeViewModel", "compact failed: ${event.message}")
                            }
                            else -> Unit
                        }
                    }

                val summaryText = summary.toString().trim()
                if (summaryText.isNotEmpty()) {
                    val compactId = UUID.randomUUID().toString()
                    // Mark every currently-active message as folded into
                    // this compact. They stay on disk + in the UI list, but
                    // InferenceCoordinator will skip them from now on.
                    chatRepo.getMessages(chatId)
                        .filter { it.archivedByCompactId == null }
                        .forEach { msg ->
                            chatRepo.updateMessage(msg.copy(archivedByCompactId = compactId))
                        }
                    // The summary card itself is the new in-context anchor.
                    chatRepo.addMessage(
                        ChatMessage(
                            id = compactId,
                            chatId = chatId,
                            role = ROLE_ASSISTANT,
                            content = summaryText,
                            timestamp = System.currentTimeMillis(),
                            kind = MessageKind.CompactSummary,
                            modelName = activeModelName,
                        )
                    )
                    _messages.value = chatRepo.getMessages(chatId)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("HomeViewModel", "compact threw: ${e.message}")
            } finally {
                // Let the user see 100% before the banner collapses.
                if (_compactionState.value.fraction >= 0.97f) {
                    _compactionState.value = _compactionState.value.copy(fraction = 1f)
                    try { delay(450) } catch (_: CancellationException) {}
                }
                _compactionState.value = CompactionState(active = false)
                compactionJob = null
            }
        }
    }

    companion object {
        // Rough wall-clock target the gen-phase progress bar animates over.
        // Hit at the high end on ~120 summary tokens at ~12 tok/s on a
        // mid-range device. We never let the bar exceed 97% before Done so
        // a fast summary doesn't visibly "finish then re-snap" at completion.
        private const val GEN_PHASE_TARGET_MS = 12_000L
    }

    private fun runGeneration(chatId: String, isFirstTurn: Boolean, userText: String) {
        modelSession.setGenerationBusy(true)
        _messages.value = chatRepo.getMessages(chatId)
        _streamingFragment.value = StreamingFragment(chatId, "", "")
        _isGenerating.value = true
        _loadModelWindow.value = false

        val thinkingOn = _thinkingEnabled.value && modelSession.supportsThinking.value
        modelSession.setThinkingEnabled(thinkingOn)
        toolCallCoordinator.configureInference(
            webOn = _webSearchEnabled.value,
            userSystemPrompt = modelSession.userSystemPrompt.value,
        )

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
                modelSession.setGenerationBusy(false)
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
        if (!wasStopped && error == null && !appVisibility.isForeground) {
            val chatTitle = chats.value.firstOrNull { it.id == chatId }?.title.orEmpty()
            userNotifications.notifyAssistantResponse(chatTitle, finalContent)
        }
        _streamingFragment.value = null
        _isGenerating.value = false
    }

    private fun String.wordCount(): Int {
        if (isBlank()) return 0
        return trim().split(WHITESPACE).count { it.isNotEmpty() }
    }
}

/**
 * Snapshot of an in-flight conversation compaction. The UI binds to this and
 * renders a progress banner while [active] is true.
 */
data class CompactionState(
    val active: Boolean = false,
    val elapsedMs: Long = 0L,
    val tokensIn: Int = 0,
    val fraction: Float = 0f,
)
