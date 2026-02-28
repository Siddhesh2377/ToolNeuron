package com.dark.tool_neuron.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.tool_neuron.data.AppSettingsDataStore
import com.dark.tool_neuron.di.AppContainer
import com.dark.tool_neuron.engine.EmbeddingConfig
import com.dark.tool_neuron.engine.EmbeddingEngine
import com.dark.tool_neuron.engine.GenerationEvent
import com.dark.tool_neuron.models.engine_schema.GgufEngineSchema
import com.dark.tool_neuron.models.engine_schema.GgufInferenceParams
import com.dark.tool_neuron.models.engine_schema.PersonaSamplingProfile
import com.dark.tool_neuron.models.table_schema.Persona
import com.dark.tool_neuron.worker.MemoryExtractor
import com.dark.tool_neuron.models.messages.ContentType
import com.dark.tool_neuron.models.messages.ImageGenerationMetrics
import com.dark.tool_neuron.models.messages.MessageContent
import com.dark.tool_neuron.models.messages.Messages
import com.dark.tool_neuron.models.messages.RagResultItem
import com.dark.tool_neuron.models.messages.Role
import com.dark.tool_neuron.models.messages.ToolChainStepData
import com.dark.tool_neuron.models.plugins.PluginExecutionMetrics
import com.dark.tool_neuron.models.plugins.PluginResultData
import com.dark.tool_neuron.plugins.PluginExecutionResult
import com.dark.tool_neuron.plugins.PluginManager
import com.dark.tool_neuron.state.AppStateManager
import com.dark.tool_neuron.worker.ChatManager
import com.dark.tool_neuron.engine.EmotionalStateTracker
import com.dark.tool_neuron.worker.ControlVectorManager
import com.dark.tool_neuron.worker.KnowledgeGraphBuilder
import com.dark.tool_neuron.worker.DiffusionConfig
import com.dark.tool_neuron.worker.DiffusionInferenceParams
import com.dark.tool_neuron.worker.GenerationManager
import com.dark.tool_neuron.tts.TTSManager
import com.dark.tool_neuron.tts.TTSSettings
import com.dark.tool_neuron.worker.LlmModelWorker
import com.mp.ai_gguf.models.DecodingMetrics
import com.mp.ai_gguf.toolcalling.ToolCall
import com.mp.ai_gguf.toolcalling.ToolCallingConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import jakarta.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class AgentPhase { Idle, Planning, Executing, Summarizing, Complete }

@HiltViewModel
class ChatViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val chatManager: ChatManager,
    private val generationManager: GenerationManager
) : ViewModel() {

    private val appContext = context
    private val appSettings = AppSettingsDataStore(context)
    private val ttsDataStore = com.dark.tool_neuron.tts.TTSDataStore(context)
    private val controlVectorManager = ControlVectorManager(context)
    private val emotionalStateTracker = EmotionalStateTracker()

    init {
        controlVectorManager.emotionalStateTracker = emotionalStateTracker
    }

    val streamingEnabled: StateFlow<Boolean> = appSettings.streamingEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val chatMemoryEnabled: StateFlow<Boolean> = appSettings.chatMemoryEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private val _messages = mutableStateListOf<Messages>()
    val messages: SnapshotStateList<Messages> = _messages

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _currentChatId = MutableStateFlow<String?>(null)
    val currentChatId: StateFlow<String?> = _currentChatId

    private val isNewConversation: Boolean get() = _currentChatId.value == null

    // Streaming state
    private val _streamingUserMessage = MutableStateFlow<String?>(null)
    val streamingUserMessage: StateFlow<String?> = _streamingUserMessage

    private val _streamingAssistantMessage = MutableStateFlow("")
    val streamingAssistantMessage: StateFlow<String> = _streamingAssistantMessage

    // Image generation state
    private val _streamingImage = MutableStateFlow<Bitmap?>(null)
    val streamingImage: StateFlow<Bitmap?> = _streamingImage

    private val _imageGenerationProgress = MutableStateFlow(0f)
    val imageGenerationProgress: StateFlow<Float> = _imageGenerationProgress

    private val _imageGenerationStep = MutableStateFlow("")
    val imageGenerationStep: StateFlow<String> = _imageGenerationStep

    // Tool calling state
    private val _currentToolName = MutableStateFlow<String?>(null)
    val currentToolName: StateFlow<String?> = _currentToolName

    // Tool chain steps (used by both streaming UI and persistence)
    private val _toolChainSteps = MutableStateFlow<List<ToolChainStepData>>(emptyList())
    val toolChainSteps: StateFlow<List<ToolChainStepData>> = _toolChainSteps

    private val _currentToolChainRound = MutableStateFlow(0)
    val currentToolChainRound: StateFlow<Int> = _currentToolChainRound

    private val _maxToolChainRounds = MutableStateFlow(5)
    val maxToolChainRounds: StateFlow<Int> = _maxToolChainRounds

    // Agent phase state (Plan → Execute → Summarize)
    private val _agentPhase = MutableStateFlow(AgentPhase.Idle)
    val agentPhase: StateFlow<AgentPhase> = _agentPhase.asStateFlow()

    private val _agentPlan = MutableStateFlow<String?>(null)
    val agentPlan: StateFlow<String?> = _agentPlan.asStateFlow()

    private val _agentSummary = MutableStateFlow<String?>(null)
    val agentSummary: StateFlow<String?> = _agentSummary.asStateFlow()

    // Track generation job for proper cancellation
    private var generationJob: Job? = null

    // Track current generation state
    private var currentUserMessage: Messages? = null
    private var currentGeneratedContent: String = ""
    private var currentMetrics: DecodingMetrics? = null
    private var currentImageMetrics: ImageGenerationMetrics? = null
    private var currentGeneratedImage: Bitmap? = null
    private var imageGenerationStartTime: Long = 0

    // Track if user message was already added to prevent duplicates
    private var userMessageAdded = false

    // UI state
    private val _showDynamicWindow = MutableStateFlow(false)
    val showDynamicWindow: StateFlow<Boolean> = _showDynamicWindow

    private val _showModelList = MutableStateFlow(false)
    val showModelList: StateFlow<Boolean> = _showModelList

    private val _currentGenerationType = MutableStateFlow(GenerationManager.ModelType.TEXT_GENERATION)
    val currentGenerationType: StateFlow<GenerationManager.ModelType> = _currentGenerationType

    // Thinking mode toggle — when enabled, adds /think to system prompt for supported models
    private val _thinkingModeEnabled = MutableStateFlow(false)
    val thinkingModeEnabled: StateFlow<Boolean> = _thinkingModeEnabled.asStateFlow()

    fun toggleThinkingMode() {
        _thinkingModeEnabled.value = !_thinkingModeEnabled.value
    }

    fun setThinkingMode(enabled: Boolean) {
        _thinkingModeEnabled.value = enabled
    }

    // Model state
    val isTextModelLoaded = LlmModelWorker.isGgufModelLoaded
    val isImageModelLoaded = LlmModelWorker.isDiffusionModelLoaded

    // TTS state
    val ttsPlayingMsgId = TTSManager.currentPlayingMsgId
    val ttsIsPlaying = TTSManager.isPlaying
    val ttsSynthesizing = TTSManager.isSynthesizing
    val ttsModelLoaded = TTSManager.isModelLoaded
    val ttsAvailableVoices = TTSManager.availableVoices

    // RAG state
    private val _currentRagContext = MutableStateFlow<String?>(null)
    val currentRagContext: StateFlow<String?> = _currentRagContext

    private val _currentRagResults = MutableStateFlow<List<RagQueryDisplayResult>>(emptyList())
    val currentRagResults: StateFlow<List<RagQueryDisplayResult>> = _currentRagResults

    // Processing phase indicator
    private val _currentProcessingPhase = MutableStateFlow<String?>(null)
    val currentProcessingPhase: StateFlow<String?> = _currentProcessingPhase

    fun setProcessingPhase(phase: String?) {
        _currentProcessingPhase.value = phase
    }

    // Memory state
    private val _currentMemoryContext = MutableStateFlow<String?>(null)
    val currentMemoryContext: StateFlow<String?> = _currentMemoryContext

    private val _currentMemoryResults = MutableStateFlow<List<com.dark.tool_neuron.worker.ScoredVaultContent>>(emptyList())
    val currentMemoryResults: StateFlow<List<com.dark.tool_neuron.worker.ScoredVaultContent>> = _currentMemoryResults

    // AI Persona state
    private val _activePersona = MutableStateFlow<Persona?>(null)
    val activePersona: StateFlow<Persona?> = _activePersona.asStateFlow()

    // AI Memory state
    val aiMemoryEnabled: StateFlow<Boolean> = appSettings.aiMemoryEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private val embeddingEngine: EmbeddingEngine by lazy {
        EmbeddingEngine().also { engine ->
            val modelPath = EmbeddingEngine.getModelPath(appContext)
            if (modelPath.exists()) {
                viewModelScope.launch(Dispatchers.IO) {
                    engine.initialize(EmbeddingConfig(modelPath = modelPath.absolutePath))
                        .onSuccess {
                            Log.d("ChatViewModel", "Embedding engine initialized for memory retrieval")
                            // Auto-backfill embeddings for memories that don't have one yet
                            try {
                                val count = memoryExtractor.backfillEmbeddings()
                                if (count > 0) Log.d("ChatViewModel", "Auto-backfilled $count memory embeddings")
                            } catch (e: Exception) {
                                Log.w("ChatViewModel", "Embedding backfill failed: ${e.message}")
                            }
                        }
                        .onFailure { Log.w("ChatViewModel", "Embedding engine init failed: ${it.message}") }
                }
            }
        }
    }

    private val memoryExtractor: MemoryExtractor by lazy {
        val db = AppContainer.getDatabase()
        MemoryExtractor(
            aiMemoryDao = AppContainer.getAiMemoryDao(),
            generationManager = generationManager,
            embeddingEngine = embeddingEngine,
            knowledgeEntityDao = db.knowledgeEntityDao(),
            knowledgeRelationDao = db.knowledgeRelationDao()
        )
    }

    // ==================== Auto-restore last chat ====================

    init {
        viewModelScope.launch {
            try {
                val lastChatId = appSettings.lastChatId.first()
                if (lastChatId != null) {
                    chatManager.getChatMessages(lastChatId).onSuccess { loadedMessages ->
                        if (loadedMessages.isNotEmpty()) {
                            _currentChatId.value = lastChatId
                            _messages.clear()
                            _messages.addAll(loadedMessages)
                            AppStateManager.setHasMessages(true)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Could not restore last chat: ${e.message}")
            }
        }

        // Persist chat ID whenever it changes
        viewModelScope.launch {
            _currentChatId.collect { chatId ->
                appSettings.saveLastChatId(chatId)
            }
        }

        // Load active persona and apply its sampling profile + control vectors
        viewModelScope.launch {
            appSettings.activePersonaId.collect { personaId ->
                val persona = if (personaId != null) {
                    AppContainer.getPersonaDao().getById(personaId)
                } else null
                _activePersona.value = persona
                applyPersonaSamplingProfile(persona)
                applyPersonaControlVectors(persona)
            }
        }
    }

    // ==================== RAG Controls ====================

    fun setRagContext(context: String?, results: List<RagQueryDisplayResult> = emptyList()) {
        _currentRagContext.value = context
        _currentRagResults.value = results
    }

    fun clearRagContext() {
        _currentRagContext.value = null
        _currentRagResults.value = emptyList()
    }

    // ==================== Memory Controls ====================

    fun setMemoryContext(context: String, results: List<com.dark.tool_neuron.worker.ScoredVaultContent>) {
        _currentMemoryContext.value = context
        _currentMemoryResults.value = results
    }

    fun clearMemoryContext() {
        _currentMemoryContext.value = null
        _currentMemoryResults.value = emptyList()
    }

    // ==================== Persona Controls ====================

    fun setActivePersona(personaId: String?) {
        viewModelScope.launch {
            appSettings.saveActivePersonaId(personaId)
        }
    }

    // ==================== KV Cache State Persistence ====================

    // ==================== Persona Engine: Sampling + Control Vectors ====================

    private val lenientJson = Json { ignoreUnknownKeys = true }

    /**
     * Apply a persona's sampling profile to the model's sampler chain.
     * Merges persona overrides with model defaults — null fields keep model config.
     * Called when persona changes or model loads.
     */
    private fun applyPersonaSamplingProfile(persona: Persona?) {
        if (!generationManager.isTextModelLoaded()) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val profileJson = persona?.samplingProfile?.takeIf { it.isNotBlank() }

                if (profileJson == null) {
                    // No persona or no profile — reset to model config defaults
                    resetSamplerToModelDefaults()
                    // Clear any logit biases from previous persona
                    generationManager.setLogitBias("[]")
                    Log.d(TAG, "Persona cleared — reset sampler to model defaults")
                    return@launch
                }

                val profile = lenientJson.decodeFromString<PersonaSamplingProfile>(profileJson)

                // Start from model config defaults, then overlay persona overrides
                val modelParams = getModelInferenceParams()
                val paramsObj = JSONObject().apply {
                    // Always send all params so model defaults are restored for
                    // fields the persona doesn't override (full replace, not merge)
                    put("temperature", profile.temperature ?: modelParams.temperature)
                    put("topK", profile.topK ?: modelParams.topK)
                    put("topP", profile.topP ?: modelParams.topP)
                    put("minP", profile.minP ?: modelParams.minP)
                    put("mirostat", profile.mirostat ?: modelParams.mirostat)
                    put("mirostatTau", profile.mirostatTau ?: modelParams.mirostatTau)
                    put("seed", profile.seed ?: modelParams.seed)
                    // Persona-only params (no model config equivalent — use struct defaults)
                    put("repeatPenalty", profile.repeatPenalty ?: 1.0)
                    put("frequencyPenalty", profile.frequencyPenalty ?: 0.0)
                    put("presencePenalty", profile.presencePenalty ?: 0.0)
                    put("penaltyLastN", profile.penaltyLastN ?: 64)
                    put("dryMultiplier", profile.dryMultiplier ?: 0.0)
                    put("dryBase", profile.dryBase ?: 1.75)
                    put("dryAllowedLength", profile.dryAllowedLength ?: 2)
                    put("dryPenaltyLastN", profile.dryPenaltyLastN ?: -1)
                    put("xtcProbability", profile.xtcProbability ?: 0.0)
                    put("xtcThreshold", profile.xtcThreshold ?: 0.1)
                }

                generationManager.updateSamplerParams(paramsObj.toString())
                Log.d(TAG, "Applied persona sampling profile (full override)")

                // Apply banned tokens as logit bias (-100 = hard suppress)
                val tokens = profile.bannedTokens
                if (tokens != null && tokens.isNotEmpty()) {
                    val biasArray = JSONArray()
                    tokens.forEach { token ->
                        biasArray.put(JSONObject().apply {
                            put("token", token)
                            put("bias", -100.0)
                        })
                    }
                    generationManager.setLogitBias(biasArray.toString())
                    Log.d(TAG, "Applied persona banned tokens: ${tokens.size} tokens")
                } else {
                    generationManager.setLogitBias("[]")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to apply persona sampling profile: ${e.message}")
            }
        }
    }

    /**
     * Reset the native sampler chain to the current model's config defaults.
     * Called when persona is cleared or has no sampling profile.
     */
    private suspend fun resetSamplerToModelDefaults() {
        val params = getModelInferenceParams()
        val paramsObj = JSONObject().apply {
            put("temperature", params.temperature)
            put("topK", params.topK)
            put("topP", params.topP)
            put("minP", params.minP)
            put("mirostat", params.mirostat)
            put("mirostatTau", params.mirostatTau)
            put("seed", params.seed)
            // Reset persona-only params to disabled defaults
            put("repeatPenalty", 1.0)
            put("frequencyPenalty", 0.0)
            put("presencePenalty", 0.0)
            put("penaltyLastN", 64)
            put("dryMultiplier", 0.0)
            put("dryBase", 1.75)
            put("dryAllowedLength", 2)
            put("dryPenaltyLastN", -1)
            put("xtcProbability", 0.0)
            put("xtcThreshold", 0.1)
        }
        generationManager.updateSamplerParams(paramsObj.toString())
    }

    /**
     * Read the model's inference params from Room DB.
     * Returns defaults if unavailable.
     */
    private suspend fun getModelInferenceParams(): GgufInferenceParams {
        val modelId = LlmModelWorker.currentGgufModelId.value ?: return GgufInferenceParams()
        val config = AppContainer.getModelRepository().getConfigByModelId(modelId)
        if (config?.modelInferenceParams != null) {
            return GgufEngineSchema.fromJson(null, config.modelInferenceParams).inferenceParams
        }
        return GgufInferenceParams()
    }

    /**
     * Apply a persona's control vectors (steering vectors) to the model.
     * Supports both axis-based format {"warmth": 0.7, "energy": 0.3, ...}
     * and legacy path-based format [{"path": "...", "strength": 0.8}].
     * Called when persona changes or model loads.
     */
    private fun applyPersonaControlVectors(persona: Persona?) {
        if (!generationManager.isTextModelLoaded()) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val raw = persona?.controlVectors?.takeIf { it.isNotBlank() }
                if (raw == null) {
                    controlVectorManager.clearAll()
                    return@launch
                }

                if (raw.trimStart().startsWith("[")) {
                    // Legacy path-based format — pass through via IPC
                    generationManager.loadControlVectors(raw)
                    Log.d(TAG, "Applied legacy path-based control vectors")
                } else {
                    // Axis-based format — apply full personality via ControlVectorManager
                    val axisStrengths = mutableMapOf<String, Float>()
                    val json = JSONObject(raw)
                    json.keys().forEach { key ->
                        axisStrengths[key] = json.getDouble(key).toFloat()
                    }
                    controlVectorManager.applyPersonality(axisStrengths)
                    Log.d(TAG, "Applied personality interventions: $axisStrengths")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to apply persona control vectors: ${e.message}")
            }
        }
    }

    // ==================== Dynamic Emotional Steering ====================

    /**
     * Run Tier 1 keyword mood analysis on user message, then
     * re-apply control vectors with mood-adjusted strengths.
     */
    private suspend fun updateMoodAndVectors(userMessage: String) {
        val persona = _activePersona.value ?: return
        val raw = persona.controlVectors.takeIf { it.isNotBlank() } ?: return
        // Only works with axis-based format (not legacy path-based)
        if (raw.trimStart().startsWith("[")) return

        try {
            val tier1 = emotionalStateTracker.analyzeKeywords(userMessage)
            emotionalStateTracker.update(tier1 = tier1)

            // Parse persona baseline axes
            val baseJson = JSONObject(raw)
            val baseAxes = mutableMapOf<String, Float>()
            baseJson.keys().forEach { key -> baseAxes[key] = baseJson.getDouble(key).toFloat() }

            // Apply mood offset to baseline and re-apply full personality
            val adjusted = emotionalStateTracker.adjustVectorsForMood(baseAxes)
            controlVectorManager.applyPersonality(adjusted)
            Log.d(TAG, "Mood-adjusted vectors applied: $adjusted")
        } catch (e: Exception) {
            Log.d(TAG, "Mood steering skipped: ${e.message}")
        }
    }

    // ==================== KV Cache State Persistence ====================

    /**
     * Get the directory for storing KV cache state files.
     * Files are keyed by chatId + modelId to ensure model compatibility.
     */
    private fun getKvCacheDir(): File {
        val dir = File(appContext.filesDir, "kv_cache")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Get the KV cache file path for a specific chat and model combination.
     * Returns null if no model is loaded.
     */
    private fun getKvCacheFile(chatId: String): File? {
        val modelId = LlmModelWorker.currentGgufModelId.value ?: return null
        // Use modelId hash to keep filename short but unique
        val modelHash = modelId.hashCode().toUInt().toString(16)
        return File(getKvCacheDir(), "${chatId}_${modelHash}.bin")
    }

    /**
     * Save KV cache for the current chat to disk (background, fire-and-forget).
     * Only saves if a text model is loaded and there's an active chat.
     */
    private fun saveKvCacheForCurrentChat() {
        val chatId = _currentChatId.value ?: return
        if (!generationManager.isTextModelLoaded()) return
        val file = getKvCacheFile(chatId) ?: return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val ok = generationManager.saveKvCacheState(file.absolutePath)
                if (ok) {
                    Log.d(TAG, "KV cache saved for chat $chatId → ${file.name}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save KV cache: ${e.message}")
            }
        }
    }

    /**
     * Try to restore KV cache for a chat from disk.
     * Only restores if the same model is loaded (file encodes model compatibility).
     */
    private fun tryRestoreKvCache(chatId: String) {
        if (!generationManager.isTextModelLoaded()) return
        val file = getKvCacheFile(chatId) ?: return
        if (!file.exists()) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val ok = generationManager.loadKvCacheState(file.absolutePath)
                if (ok) {
                    Log.d(TAG, "KV cache restored for chat $chatId ← ${file.name}")
                } else {
                    // Model mismatch or corrupt file — clean up
                    file.delete()
                    Log.d(TAG, "KV cache file incompatible, deleted: ${file.name}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restore KV cache: ${e.message}")
            }
        }
    }

    // ==================== Chat Management ====================

    fun startNewConversation() {
        // Save KV cache for the outgoing chat before switching
        saveKvCacheForCurrentChat()

        // Reset emotional state and conversation-specific memory for fresh conversation
        emotionalStateTracker.reset()
        controlVectorManager.resetConversationMemory()

        _currentChatId.value = null
        _messages.clear()
        _streamingUserMessage.value = null
        _streamingAssistantMessage.value = ""
        _streamingImage.value = null
        _imageGenerationProgress.value = 0f
        _imageGenerationStep.value = ""
        currentUserMessage = null
        currentGeneratedContent = ""
        currentGeneratedImage = null
        currentMetrics = null
        currentImageMetrics = null
        userMessageAdded = false
        _error.value = null
        _currentToolName.value = null
        _toolChainSteps.value = emptyList()
        _currentToolChainRound.value = 0
        _agentPhase.value = AgentPhase.Idle
        _agentPlan.value = null
        _agentSummary.value = null
        AppStateManager.setHasMessages(false)
    }

    fun loadChat(chatId: String) {
        // Save KV cache for the outgoing chat before switching
        saveKvCacheForCurrentChat()

        // Reset emotional state and conversation memory for the new conversation context
        emotionalStateTracker.reset()
        controlVectorManager.resetConversationMemory()

        viewModelScope.launch {
            _currentChatId.value = chatId
            chatManager.getChatMessages(chatId).onSuccess { loadedMessages ->
                _messages.clear()
                _messages.addAll(loadedMessages)
                AppStateManager.setHasMessages(loadedMessages.isNotEmpty())

                // Try to restore KV cache for this chat (same model only)
                tryRestoreKvCache(chatId)
            }.onFailure { e ->
                _error.value = "Failed to load chat: ${e.message}"
                AppStateManager.setError("Failed to load chat: ${e.message}")
            }
        }
    }

    // ==================== Model Selection ====================

    fun switchToTextGeneration() {
        if (!generationManager.isTextModelLoaded()) {
            _error.value = "Text generation model not loaded"
            return
        }
        _currentGenerationType.value = GenerationManager.ModelType.TEXT_GENERATION
        generationManager.setCurrentModelType(GenerationManager.ModelType.TEXT_GENERATION)
    }

    fun switchToImageGeneration() {
        if (!generationManager.isImageModelLoaded()) {
            _error.value = "Image generation model not loaded"
            return
        }
        _currentGenerationType.value = GenerationManager.ModelType.IMAGE_GENERATION
        generationManager.setCurrentModelType(GenerationManager.ModelType.IMAGE_GENERATION)
    }

    // ==================== Unified Text Generation Entry Point ====================

    fun sendChat(prompt: String) {
        if (!generationManager.isTextModelLoaded()) {
            _error.value = "Please load a text generation model first"
            AppStateManager.setError("Please load a text generation model first")
            return
        }
        if (_isGenerating.value) return

        _isGenerating.value = true
        _streamingUserMessage.value = prompt
        _streamingAssistantMessage.value = ""
        userMessageAdded = false
        currentGeneratedContent = ""
        currentMetrics = null
        _error.value = null

        currentUserMessage = Messages(
            msgId = "",
            role = Role.User,
            content = MessageContent(contentType = ContentType.Text, content = prompt),
            decodingMetrics = null
        )
        AppStateManager.setHasMessages(true)

        generationJob = viewModelScope.launch {
            try {
                // Read maxTokens from the current model's config
                val maxTokens = getCurrentModelMaxTokens()

                val isNewChat = isNewConversation
                val hasTools = PluginManager.hasEnabledTools()
                        && PluginManager.isToolCallingModelLoaded.value
                val ragContext = _currentRagContext.value

                // For existing chats, save user message upfront
                if (!isNewChat) {
                    val chatId = _currentChatId.value ?: run {
                        _error.value = "No chat selected"
                        resetStreamingState()
                        return@launch
                    }
                    chatManager.addUserMessage(chatId, prompt).onSuccess { userMsg ->
                        currentUserMessage = userMsg
                    }.onFailure { e ->
                        _error.value = "Failed to save message: ${e.message}"
                        resetStreamingState()
                        return@launch
                    }
                }

                // Tier 1: Fast keyword mood analysis before generation
                updateMoodAndVectors(prompt)

                when {
                    hasTools -> agentFlow(prompt, ragContext, maxTokens, isNewChat)
                    else -> simpleFlow(prompt, ragContext, maxTokens, isNewChat)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in sendChat", e)
                _error.value = e.message
                AppStateManager.setError(e.message ?: "Unknown error")
                resetStreamingState()
            } finally {
                // Persist KV cache after every completed generation turn so
                // the cache file is always up-to-date for the current chat.
                saveKvCacheForCurrentChat()
            }
        }
    }

    // Keep old name as alias for backward compatibility with callers
    fun sendTextMessage(prompt: String) = sendChat(prompt)

    /**
     * Regenerate the last assistant response.
     * Removes the last assistant message and re-sends the last user prompt.
     */
    fun regenerateLastMessage() {
        if (!generationManager.isTextModelLoaded()) {
            _error.value = "Please load a text generation model first"
            return
        }
        if (_isGenerating.value) return

        val chatId = _currentChatId.value ?: return

        // Find the last user message to get the prompt
        val lastUserMsg = _messages.lastOrNull { it.role == Role.User }
        if (lastUserMsg == null) {
            _error.value = "No user message to regenerate from"
            return
        }

        // Remove the last assistant message from in-memory list immediately
        val lastAssistantMsg = _messages.lastOrNull { it.role == Role.Assistant }
        if (lastAssistantMsg != null) {
            _messages.remove(lastAssistantMsg)
        }

        val prompt = lastUserMsg.content.content

        // Set up generation state without creating a new user message
        _isGenerating.value = true
        _streamingUserMessage.value = prompt
        _streamingAssistantMessage.value = ""
        userMessageAdded = true // already added — skip re-adding user message
        currentGeneratedContent = ""
        currentMetrics = null
        _error.value = null

        generationJob = viewModelScope.launch {
            try {
                // Await vault deletion before starting generation (prevents race condition)
                if (lastAssistantMsg != null) {
                    chatManager.deleteMessage(lastAssistantMsg.msgId)
                }
                val maxTokens = getCurrentModelMaxTokens()
                val hasTools = PluginManager.hasEnabledTools()
                        && PluginManager.isToolCallingModelLoaded.value
                val ragContext = _currentRagContext.value

                when {
                    hasTools -> agentFlow(prompt, ragContext, maxTokens, isNewChat = false, isRegeneration = true)
                    else -> simpleFlow(prompt, ragContext, maxTokens, isNewChat = false, isRegeneration = true)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in regenerateLastMessage", e)
                _error.value = e.message
                AppStateManager.setError(e.message ?: "Unknown error")
                resetStreamingState()
            }
        }
    }

    /**
     * Read the effective maxTokens: persona override > model config > default.
     */
    private suspend fun getCurrentModelMaxTokens(): Int {
        // Persona override takes priority
        _activePersona.value?.samplingProfile?.takeIf { it.isNotBlank() }?.let { json ->
            try {
                val profile = lenientJson.decodeFromString<PersonaSamplingProfile>(json)
                profile.maxTokens?.let { return it }
            } catch (_: Exception) {}
        }

        // Fall back to model config
        val modelId = LlmModelWorker.currentGgufModelId.value ?: return 4096
        val config = AppContainer.getModelRepository().getConfigByModelId(modelId)
        if (config?.modelInferenceParams != null) {
            val schema = GgufEngineSchema.fromJson(null, config.modelInferenceParams)
            return schema.inferenceParams.maxTokens
        }
        return 4096
    }

    // ==================== Agent Flow (Plan → Execute → Summarize) ====================

    private suspend fun agentFlow(
        prompt: String,
        ragContext: String?,
        maxTokens: Int,
        isNewChat: Boolean,
        isRegeneration: Boolean = false
    ) {
        val fullPrompt = ragContext?.let { "$it\n\n$prompt" } ?: prompt

        // Phase 1: Plan
        _agentPhase.value = AgentPhase.Planning
        AppStateManager.setGeneratingText()
        Log.d(TAG, "Agent Phase 1: Generating plan")
        val plan = generatePlan(fullPrompt)
        _agentPlan.value = plan
        Log.d(TAG, "Agent plan: $plan")

        // Phase 2: Bounded generate-execute loop
        _agentPhase.value = AgentPhase.Executing
        _streamingAssistantMessage.value = ""
        Log.d(TAG, "Agent Phase 2: Generate → Execute loop")
        val steps = executeAgentLoop(fullPrompt, plan)
        Log.d(TAG, "Agent execution complete: ${steps.size} steps executed")

        // If no tools were executed or all failed, fall back to simple text generation
        if (steps.isEmpty() || steps.all { !it.success }) {
            Log.d(TAG, "No successful tool calls, falling back to simple flow")
            _agentPhase.value = AgentPhase.Idle
            _agentPlan.value = null
            PluginManager.clearGrammar()
            simpleFlow(prompt, ragContext, maxTokens, isNewChat, isRegeneration)
            return
        }

        // Phase 3: Summary
        _agentPhase.value = AgentPhase.Summarizing
        _streamingAssistantMessage.value = ""
        AppStateManager.setGeneratingText()
        Log.d(TAG, "Agent Phase 3: Generating summary")
        val summary = generateSummary(fullPrompt, steps)
        _agentSummary.value = summary
        _streamingAssistantMessage.value = summary
        _agentPhase.value = AgentPhase.Complete
        Log.d(TAG, "Agent flow complete")

        // Persist
        persistAgentChat(prompt, isNewChat, plan, steps, summary)

        // Emotion probing + P7 learning after agent response
        if (summary.isNotBlank()) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    controlVectorManager.onGenerationTurnComplete(summary, prompt)
                } catch (e: Exception) {
                    Log.e(TAG, "Emotion probe/learning failed: ${e.message}")
                }
            }
        }

        // Background memory extraction after agent response
        if (aiMemoryEnabled.value && summary.isNotBlank()) {
            val chatId = _currentChatId.value
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val persona = _activePersona.value
                    val memPersonaId = persona?.takeIf { it.buildEffectiveSystemPrompt().isNotBlank() }?.id
                    memoryExtractor.extractAndStore(prompt, summary, chatId, persona?.name, memPersonaId)
                } catch (e: Exception) {
                    Log.e(TAG, "Memory extraction failed: ${e.message}")
                }
            }
        }
    }

    /** Phase 1: Generate a brief plan describing which tools to use. */
    private suspend fun generatePlan(prompt: String): String {
        PluginManager.clearGrammar()
        val toolDescriptions = PluginManager.getToolDescriptionsText()
        val systemPrompt = buildString {
            appendLine("Available tools:")
            appendLine(toolDescriptions)
            appendLine()
            appendLine("Write a 1-2 sentence plan: which tools to call and what arguments to pass. Be specific and concise.")
        }
        val messages = listOf(
            JSONObject().put("role", "system").put("content", systemPrompt),
            JSONObject().put("role", "user").put("content", prompt)
        )
        return generatePlainText(messages, maxTokens = 150)
    }

    /**
     * Phase 2: Bounded generate → execute loop.
     * Each round: generate 1 tool call (grammar-constrained) → execute it → feed result back.
     * Stops when: no tool call generated, duplicate detected, or max rounds reached.
     */
    private suspend fun executeAgentLoop(
        prompt: String,
        plan: String,
        maxRounds: Int = 5
    ): List<ToolChainStepData> {
        val steps = mutableListOf<ToolChainStepData>()
        val seenCalls = mutableSetOf<String>()
        var consecutiveFailures = 0
        _toolChainSteps.value = emptyList()
        _maxToolChainRounds.value = maxRounds

        val toolSignatures = PluginManager.getToolSignaturesText()
        val enabledNames = PluginManager.getEnabledToolNames().map { it.lowercase() }
        val truncatedPlan = plan.take(200)

        for (round in 1..maxRounds) {
            // Generate next tool call
            PluginManager.restoreGrammar()
            // Round 1: include tool signatures + plan (model needs to know params)
            // Round 2+: minimal prompt — grammar already constrains which tools to call,
            // just provide context about what's done + what the user wants
            val systemPrompt = if (steps.isEmpty()) {
                buildString {
                    appendLine("Tools: $toolSignatures")
                    appendLine("Plan: $truncatedPlan")
                    appendLine("Call the first tool with ALL required arguments.")
                }
            } else {
                buildString {
                    appendLine("Done: ${steps.joinToString("; ") { "${it.toolName}=${it.result.take(100)}" }}")
                    appendLine("Call the NEXT tool needed, or stop if the plan is complete.")
                }
            }
            val messages = listOf(
                JSONObject().put("role", "system").put("content", systemPrompt),
                JSONObject().put("role", "user").put("content", prompt)
            )
            Log.d(TAG, "Agent loop round $round: generating tool call")
            val toolCalls = generateAndCollectToolCalls(messages, maxTokens = 300)
            if (toolCalls.isEmpty()) {
                Log.d(TAG, "Agent loop round $round: no tool call generated, stopping")
                break
            }

            // Process each tool call from this generation (usually 1)
            var generatedDuplicate = false
            for ((rawName, rawArgs) in toolCalls) {
                val callKey = "${rawName.lowercase()}:${rawArgs.hashCode()}"
                if (callKey in seenCalls) {
                    Log.w(TAG, "Duplicate tool call detected, stopping loop: $rawName")
                    generatedDuplicate = true
                    break
                }
                seenCalls.add(callKey)

                _currentToolChainRound.value = steps.size + 1

                // Parse
                val parsed = extractToolCallFromArgs(rawName, rawArgs)
                if (parsed == null) {
                    Log.e(TAG, "Failed to parse tool call: $rawName")
                    steps.add(ToolChainStepData(
                        round = steps.size + 1,
                        toolName = rawName,
                        pluginName = "Unknown",
                        args = rawArgs.take(500),
                        result = "Failed to parse arguments",
                        executionTimeMs = 0,
                        success = false
                    ))
                    _toolChainSteps.value = steps.toList()
                    consecutiveFailures++
                    if (consecutiveFailures >= 2) {
                        Log.w(TAG, "2 consecutive failures, stopping agent loop")
                        break
                    }
                    continue
                }

                // Execute
                val (toolName, argsObj) = parsed
                val normalizedName = normalizeToolName(toolName)

                // Validate tool name against enabled tools
                if (normalizedName.lowercase() !in enabledNames) {
                    Log.w(TAG, "Hallucinated tool name '$normalizedName', not in enabled tools: $enabledNames")
                    steps.add(ToolChainStepData(
                        round = steps.size + 1,
                        toolName = normalizedName,
                        pluginName = "Unknown",
                        args = rawArgs.take(500),
                        result = "Tool not found: $normalizedName",
                        executionTimeMs = 0,
                        success = false
                    ))
                    _toolChainSteps.value = steps.toList()
                    consecutiveFailures++
                    if (consecutiveFailures >= 2) {
                        Log.w(TAG, "2 consecutive failures, stopping agent loop")
                        break
                    }
                    continue
                }

                _currentToolName.value = normalizedName
                AppStateManager.setExecutingPlugin("", normalizedName)

                val toolCall = ToolCall(name = normalizedName, arguments = argsObj)
                val result = PluginManager.executeToolForMultiTurn(toolCall)

                val isSuccess = !result.isError
                if (isSuccess) {
                    consecutiveFailures = 0
                    AppStateManager.setPluginExecutionComplete(
                        pluginName = result.pluginName,
                        toolName = normalizedName,
                        success = true,
                        executionTimeMs = result.executionTimeMs
                    )
                } else {
                    consecutiveFailures++
                    AppStateManager.setPluginExecutionComplete(
                        pluginName = result.pluginName,
                        toolName = normalizedName,
                        success = false,
                        executionTimeMs = result.executionTimeMs,
                        errorMessage = result.resultJson
                    )
                }

                steps.add(ToolChainStepData(
                    round = steps.size + 1,
                    toolName = normalizedName,
                    pluginName = result.pluginName,
                    args = rawArgs.take(500),
                    result = result.resultJson.take(500),
                    executionTimeMs = result.executionTimeMs,
                    success = isSuccess
                ))
                _toolChainSteps.value = steps.toList()
                Log.d(TAG, "Agent loop round $round: executed ${normalizedName} (${result.executionTimeMs}ms)")

                if (consecutiveFailures >= 2) {
                    Log.w(TAG, "2 consecutive failures, stopping agent loop")
                    break
                }

                // Add plugin result message for in-memory UI display
                if (result.rawData != null) {
                    val resultData = PluginResultData(
                        pluginName = result.pluginName,
                        toolName = normalizedName,
                        inputParams = argsObj.toString(),
                        resultData = result.resultJson,
                        success = isSuccess
                    )
                    val pluginMessage = Messages(
                        role = Role.Assistant,
                        content = MessageContent(
                            contentType = ContentType.PluginResult,
                            content = "Plugin '${result.pluginName}' executed tool '$normalizedName'",
                            pluginResultData = resultData
                        ),
                        pluginMetrics = PluginExecutionMetrics(
                            pluginName = result.pluginName,
                            toolName = normalizedName,
                            executionTimeMs = result.executionTimeMs,
                            success = isSuccess
                        )
                    )
                    if (!userMessageAdded && currentUserMessage != null) {
                        _messages.add(currentUserMessage!!)
                        userMessageAdded = true
                    }
                    _messages.add(pluginMessage)
                }
            }

            if (generatedDuplicate || consecutiveFailures >= 2) break
        }

        _currentToolName.value = null
        return steps
    }

    /** Phase 3: Generate a natural language summary from all tool results. */
    private suspend fun generateSummary(
        prompt: String,
        steps: List<ToolChainStepData>
    ): String {
        PluginManager.clearGrammar()
        val resultsText = steps.mapIndexed { i, step ->
            "${i + 1}. ${step.pluginName} (${step.toolName}): ${step.result}"
        }.joinToString("\n")

        // Inject character persona so agent responses maintain character voice.
        // Without this, agent summaries use a generic "helpful assistant" tone
        // which breaks immersion when the user has an active persona.
        val activePersona = _activePersona.value
        val systemPrompt = if (activePersona != null) {
            val rawPersona = activePersona.buildEffectiveSystemPrompt().takeIf { it.isNotBlank() }
            val personaBlock = if (rawPersona != null) activePersona.applyTemplateVars(rawPersona) else ""
            buildString {
                if (personaBlock.isNotEmpty()) {
                    append(personaBlock)
                    append("\n\n")
                }
                append("Summarize the tool execution results concisely for the user. Stay in character.")
            }
        } else {
            "You are a helpful assistant. Summarize the tool execution results concisely for the user."
        }
        val userContent = "My request: $prompt\n\nTool Results:\n$resultsText\n\nProvide a helpful summary."

        val messages = listOf(
            JSONObject().put("role", "system").put("content", systemPrompt),
            JSONObject().put("role", "user").put("content", userContent)
        )
        val summary = generatePlainText(messages, maxTokens = 512)
        PluginManager.restoreGrammar()  // Re-enable grammar for next message
        return summary
    }

    /** Persist agent chat results to vault. */
    private suspend fun persistAgentChat(
        prompt: String,
        isNewChat: Boolean,
        plan: String,
        steps: List<ToolChainStepData>,
        summary: String
    ) {
        val ragResultItems = _currentRagResults.value.takeIf { it.isNotEmpty() }?.map { result ->
            RagResultItem(
                ragName = result.ragName,
                content = result.content,
                score = result.score,
                nodeId = result.nodeId
            )
        }

        if (isNewChat) {
            chatManager.createNewChat().onSuccess { newChatId ->
                _currentChatId.value = newChatId
                chatManager.addUserMessage(newChatId, prompt)

                // Save plugin result messages
                _messages.filter { it.content.contentType == ContentType.PluginResult }
                    .forEach { chatManager.addMessage(newChatId, it) }

                // Save assistant message with full agent data
                chatManager.addAssistantMessage(
                    chatId = newChatId,
                    content = summary,
                    decodingMetrics = currentMetrics,
                    ragResults = ragResultItems,
                    toolChainSteps = steps,
                    agentPlan = plan,
                    agentSummary = summary
                )

                // Reload to get proper IDs
                chatManager.getChatMessages(newChatId).onSuccess { loadedMessages ->
                    _messages.clear()
                    _messages.addAll(loadedMessages)
                }

                AppStateManager.setGenerationComplete()
                AppStateManager.chatRefreshed()
                val spokenMsgId = _messages.lastOrNull { it.role == Role.Assistant }?.msgId
                resetStreamingState()
                viewModelScope.launch { autoSpeakIfEnabled(summary, spokenMsgId) }
            }.onFailure { e ->
                _error.value = "Failed to create chat: ${e.message}"
                AppStateManager.setError("Failed to create chat: ${e.message}")
                resetStreamingState()
            }
        } else {
            val chatId = _currentChatId.value ?: return

            // Add user message to in-memory list if not already added
            if (!userMessageAdded && currentUserMessage != null) {
                _messages.add(currentUserMessage!!)
                userMessageAdded = true
            }

            val assistantMessage = Messages(
                role = Role.Assistant,
                content = MessageContent(contentType = ContentType.Text, content = summary),
                decodingMetrics = currentMetrics,
                ragResults = ragResultItems,
                toolChainSteps = steps,
                agentPlan = plan,
                agentSummary = summary
            )
            _messages.add(assistantMessage)

            chatManager.addAssistantMessage(
                chatId = chatId,
                content = summary,
                decodingMetrics = currentMetrics,
                ragResults = ragResultItems,
                toolChainSteps = steps,
                agentPlan = plan,
                agentSummary = summary
            )

            AppStateManager.setGenerationComplete()
            AppStateManager.chatRefreshed()
            val spokenMsgId = assistantMessage.msgId
            resetStreamingState()
            viewModelScope.launch { autoSpeakIfEnabled(summary, spokenMsgId) }
        }
    }

    // ==================== Simple Flow (no tools) ====================

    private suspend fun simpleFlow(
        prompt: String,
        ragContext: String?,
        maxTokens: Int,
        isNewChat: Boolean,
        isRegeneration: Boolean = false
    ) {
        AppStateManager.setGeneratingText()
        val fullPrompt = ragContext?.let { "$it\n\n$prompt" } ?: prompt

        var responseForMemory: String? = null

        if (isNewChat) {
            // Build conversation messages with memory
            val conversationMessages = buildConversationMessages(fullPrompt)
            val response = generatePlainText(conversationMessages, maxTokens)
            val filteredResponse = filterToolCallSyntax(response)
            _streamingAssistantMessage.value = filteredResponse
            responseForMemory = filteredResponse
            createChatWithMessages(prompt, filteredResponse, currentMetrics)
        } else {
            val chatId = _currentChatId.value ?: return

            // Build existing conversation messages
            val conversationMessages = buildExistingConversationMessages(fullPrompt, isRegeneration)
            val response = generatePlainText(conversationMessages, maxTokens)
            val filteredResponse = filterToolCallSyntax(response)
            _streamingAssistantMessage.value = filteredResponse

            if (!userMessageAdded && currentUserMessage != null) {
                _messages.add(currentUserMessage!!)
                userMessageAdded = true
            }

            val ragResultItems = _currentRagResults.value.takeIf { it.isNotEmpty() }?.map { result ->
                RagResultItem(
                    ragName = result.ragName,
                    content = result.content,
                    score = result.score,
                    nodeId = result.nodeId
                )
            }

            if (filteredResponse.isNotBlank()) {
                responseForMemory = filteredResponse
                val assistantMessage = Messages(
                    role = Role.Assistant,
                    content = MessageContent(contentType = ContentType.Text, content = filteredResponse),
                    decodingMetrics = currentMetrics,
                    ragResults = ragResultItems
                )
                _messages.add(assistantMessage)
                chatManager.addAssistantMessage(chatId, filteredResponse, currentMetrics, ragResultItems)
                AppStateManager.setGenerationComplete()
                AppStateManager.chatRefreshed()
                val spokenMsgId = assistantMessage.msgId
                resetStreamingState()
                viewModelScope.launch { autoSpeakIfEnabled(filteredResponse, spokenMsgId) }
            } else {
                AppStateManager.setGenerationComplete()
                resetStreamingState()
            }
        }

        // Emotion probing + P7 learning after response
        if (responseForMemory != null) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    controlVectorManager.onGenerationTurnComplete(responseForMemory, prompt)
                } catch (e: Exception) {
                    Log.e(TAG, "Emotion probe/learning failed: ${e.message}")
                }
            }
        }

        // Background memory extraction after response
        if (aiMemoryEnabled.value && responseForMemory != null) {
            val chatId = _currentChatId.value
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val persona = _activePersona.value
                    val memPersonaId = persona?.takeIf { it.buildEffectiveSystemPrompt().isNotBlank() }?.id
                    memoryExtractor.extractAndStore(prompt, responseForMemory, chatId, persona?.name, memPersonaId)
                } catch (e: Exception) {
                    Log.e(TAG, "Memory extraction failed: ${e.message}")
                }
            }
        }
    }

    // ==================== LLM Generation Helpers ====================

    /**
     * Detect if text ends with a repeating pattern (common with small models).
     * Returns the index to trim to (keep one copy of the pattern), or -1 if no repetition.
     */
    private fun detectRepetitionTrimIndex(
        text: String,
        minPatternLen: Int = 20,
        minRepeats: Int = 3,
        maxCheckLen: Int = 600
    ): Int {
        if (text.length < minPatternLen * minRepeats) return -1

        val checkLen = minOf(text.length, maxCheckLen)
        val startOffset = text.length - checkLen
        val window = text.substring(startOffset)

        for (patternLen in minPatternLen until checkLen / minRepeats) {
            val pattern = window.substring(window.length - patternLen)
            var count = 1
            var pos = window.length - patternLen * 2

            while (pos >= 0) {
                if (window.substring(pos, pos + patternLen) == pattern) {
                    count++
                    pos -= patternLen
                } else {
                    break
                }
            }

            if (count >= minRepeats && patternLen * count >= 60) {
                // Keep content up to end of first occurrence of the pattern
                val repeatStartInWindow = window.length - patternLen * count
                return startOffset + repeatStartInWindow + patternLen
            }
        }
        return -1
    }

    /** Generate plain text (no grammar/tool detection). Streams to UI with batching. */
    private suspend fun generatePlainText(
        messages: List<JSONObject>,
        maxTokens: Int
    ): String {
        val jsonArray = JSONArray(messages)
        val resultBuilder = StringBuilder()
        currentMetrics = null
        var lastEmitTime = 0L
        var lastRepCheckLen = 0
        var repetitionTrimIndex = -1

        generationManager.generateMultiTurnStreaming(
            jsonArray.toString(), maxTokens
        ).collect { event ->
            when (event) {
                is GenerationEvent.Token -> {
                    resultBuilder.append(event.text)
                    val now = System.currentTimeMillis()
                    if (now - lastEmitTime >= 50) {
                        _streamingAssistantMessage.value = resultBuilder.toString()
                        lastEmitTime = now
                    }

                    // Periodically check for repetition loops (every ~200 new chars)
                    if (repetitionTrimIndex < 0 && resultBuilder.length - lastRepCheckLen >= 200) {
                        lastRepCheckLen = resultBuilder.length
                        val trimIdx = detectRepetitionTrimIndex(resultBuilder.toString())
                        if (trimIdx >= 0) {
                            Log.w(TAG, "Repetition loop detected at ~$trimIdx chars, stopping generation")
                            repetitionTrimIndex = trimIdx
                            generationManager.stopTextGeneration()
                        }
                    }
                }
                is GenerationEvent.Done -> {
                    // Final emit to ensure UI has complete text
                    _streamingAssistantMessage.value = resultBuilder.toString()
                }
                is GenerationEvent.Metrics -> { currentMetrics = event.metrics }
                is GenerationEvent.Error -> {
                    Log.e(TAG, "Generation error: ${event.message}")
                    throw Exception(event.message)
                }
                is GenerationEvent.ToolCall -> {
                    // Ignore tool calls during plain text generation
                    Log.d(TAG, "Ignoring tool call during plain text: ${event.name}")
                }
            }
        }

        var result = resultBuilder.toString().trim()

        // Trim repetitive tail if detected during streaming
        if (repetitionTrimIndex in 1 until result.length) {
            Log.d(TAG, "Trimming repetitive output: keeping ${repetitionTrimIndex} of ${result.length} chars")
            result = result.substring(0, repetitionTrimIndex).trim()
            _streamingAssistantMessage.value = result
        }

        return result
    }

    /** Generate with grammar and collect all tool calls from a single generation. */
    private suspend fun generateAndCollectToolCalls(
        messages: List<JSONObject>,
        maxTokens: Int
    ): List<Pair<String, String>> {
        val toolCalls = mutableListOf<Pair<String, String>>()
        var text = ""
        val jsonArray = JSONArray(messages)

        generationManager.generateMultiTurnStreaming(
            jsonArray.toString(), maxTokens
        ).collect { event ->
            when (event) {
                is GenerationEvent.Token -> {
                    text += event.text
                }
                is GenerationEvent.ToolCall -> {
                    toolCalls.add(Pair(event.name, event.args))
                    Log.d(TAG, "Collected tool call: ${event.name}")
                }
                is GenerationEvent.Done -> {}
                is GenerationEvent.Metrics -> { currentMetrics = event.metrics }
                is GenerationEvent.Error -> {
                    Log.e(TAG, "Generation error during tool call collection: ${event.message}")
                }
            }
        }

        // Fallback: parse text if no ToolCall events were received
        if (toolCalls.isEmpty() && text.isNotBlank()) {
            Log.d(TAG, "No ToolCall events, trying text parsing fallback")
            val enabledNames = PluginManager.getEnabledToolNames().map { it.lowercase() }
            parseToolCallsFromText(text)?.let { parsed ->
                // Filter against enabled tools to reject hallucinated names
                val valid = parsed.filter { (name, _) ->
                    normalizeToolName(name).lowercase() in enabledNames
                }
                if (valid.size < parsed.size) {
                    Log.w(TAG, "Filtered out ${parsed.size - valid.size} hallucinated tool calls from fallback parsing")
                }
                toolCalls.addAll(valid)
                Log.d(TAG, "Fallback parsed ${valid.size} valid tool calls from text")
            }
        }

        return toolCalls
    }

    /** Parse multiple tool calls from text output (handles various formats). */
    private fun parseToolCallsFromText(text: String): List<Pair<String, String>>? {
        val results = mutableListOf<Pair<String, String>>()

        // Try: single tool call via existing parser
        tryParseToolCallFromContent(text)?.let { (name, args) ->
            results.add(Pair(name, args))
        }

        // Try: JSON array with tool_calls containing multiple entries
        if (results.isEmpty()) {
            try {
                val json = JSONObject(text.trim())
                val toolCallsArray = json.optJSONArray("tool_calls")
                if (toolCallsArray != null) {
                    for (i in 0 until toolCallsArray.length()) {
                        val call = toolCallsArray.getJSONObject(i)
                        val name = call.getString("name")
                        val args = call.getJSONObject("arguments").toString()
                        results.add(Pair(name, JSONObject().apply {
                            put("tool_calls", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("name", name)
                                    put("arguments", JSONObject(args))
                                })
                            })
                        }.toString()))
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Multi-tool JSON parse failed: ${e.message}")
            }
        }

        return results.takeIf { it.isNotEmpty() }
    }

    /**
     * Read the system prompt from the currently loaded model's config.
     * Returns empty string if no system prompt is configured.
     */
    private suspend fun getCurrentModelSystemPrompt(userQuery: String = ""): String {
        val modelId = LlmModelWorker.currentGgufModelId.value ?: return ""
        val config = AppContainer.getModelRepository().getConfigByModelId(modelId)
        val basePrompt = if (config?.modelInferenceParams != null) {
            val schema = GgufEngineSchema.fromJson(null, config.modelInferenceParams)
            schema.inferenceParams.systemPrompt
        } else ""

        // Inject thinking mode directive if enabled
        val thinkingDirective = if (_thinkingModeEnabled.value) "/think" else "/no_think"

        // Build persona prompt from structured character card fields
        val activePersona = _activePersona.value
        val rawPersonaPrompt = activePersona?.buildEffectiveSystemPrompt()?.takeIf { it.isNotBlank() } ?: ""
        // Apply {{char}}/{{user}} template variables
        val personaPrompt = if (rawPersonaPrompt.isNotEmpty() && activePersona != null) {
            activePersona.applyTemplateVars(rawPersonaPrompt)
        } else rawPersonaPrompt

        // Build knowledge graph context (scoped to active persona)
        // Bare "Assistant" persona (no personality/description) uses null → global memory
        val personaId = if (rawPersonaPrompt.isNotEmpty()) activePersona?.id else null
        val knowledgeContext = if (aiMemoryEnabled.value && userQuery.isNotBlank()) {
            try {
                val db = AppContainer.getDatabase()
                KnowledgeGraphBuilder.buildContextForQuery(
                    userQuery, db.knowledgeEntityDao(), db.knowledgeRelationDao(),
                    personaId = personaId
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to build KG context: ${e.message}")
                ""
            }
        } else ""

        // Build memory block (scoped to active persona)
        val memoryBlock = if (aiMemoryEnabled.value && userQuery.isNotBlank()) {
            try {
                memoryExtractor.buildMemoryBlock(userQuery, personaId = personaId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to build memory block: ${e.message}")
                ""
            }
        } else ""

        // Assemble: thinkingDirective + model system prompt + KG context + memory + persona (LAST)
        // Small models treat the LAST content in system prompt as highest priority,
        // so persona must come last to maintain character consistency.
        return buildString {
            append(thinkingDirective)
            if (basePrompt.isNotEmpty()) {
                append("\n")
                append(basePrompt)
            }
            if (knowledgeContext.isNotEmpty()) {
                append("\n\n")
                append(knowledgeContext)
            }
            if (memoryBlock.isNotEmpty()) {
                append("\n\n")
                append(memoryBlock)
            }
            if (personaPrompt.isNotEmpty()) {
                append("\n\n")
                append(personaPrompt)
            }
        }
    }

    /** Build conversation messages for new chat (with optional memory). */
    private suspend fun buildConversationMessages(userPrompt: String): List<JSONObject> {
        val messages = mutableListOf<JSONObject>()
        // Use persona name as assistant role (Layla's trick — small models treat role name as identity)
        val assistantRole = _activePersona.value?.name ?: "assistant"
        // Prepend system prompt from model config if configured
        val systemPrompt = getCurrentModelSystemPrompt(userQuery = userPrompt)
        if (systemPrompt.isNotEmpty()) {
            messages.add(JSONObject().put("role", "system").put("content", systemPrompt))
        }
        if (chatMemoryEnabled.value) {
            _messages.forEach { msg ->
                when (msg.role) {
                    Role.User -> messages.add(
                        JSONObject().put("role", "user").put("content", msg.content.content)
                    )
                    Role.Assistant -> {
                        if (msg.content.contentType == ContentType.Text) {
                            messages.add(
                                JSONObject().put("role", assistantRole).put("content", msg.content.content)
                            )
                        }
                    }
                }
            }
        }
        // Post-history character reinforcement (most influential position for small models)
        injectPostHistoryInstruction(messages)
        messages.add(JSONObject().put("role", "user").put("content", userPrompt))
        return sanitizeRoleAlternation(messages)
    }

    /** Build conversation messages for existing chat.
     *  @param isRegeneration when true, the last user message in _messages is the one being
     *         regenerated. We exclude it from history and re-append userPrompt (which may
     *         include RAG context) at the end — identical to normal send ordering.
     */
    private suspend fun buildExistingConversationMessages(
        userPrompt: String,
        isRegeneration: Boolean = false
    ): List<JSONObject> {
        val messages = mutableListOf<JSONObject>()
        val assistantRole = _activePersona.value?.name ?: "assistant"
        // Prepend system prompt from model config if configured
        val systemPrompt = getCurrentModelSystemPrompt(userQuery = userPrompt)
        if (systemPrompt.isNotEmpty()) {
            messages.add(JSONObject().put("role", "system").put("content", systemPrompt))
        }
        if (chatMemoryEnabled.value) {
            // During regeneration: skip the last user message (we re-append it below
            // with RAG context, in the correct position after post-history).
            val lastUserMsgId = if (isRegeneration) {
                _messages.lastOrNull { it.role == Role.User }?.msgId
            } else null

            _messages.forEach { msg ->
                if (isRegeneration && msg.msgId == lastUserMsgId) return@forEach
                when (msg.role) {
                    Role.User -> messages.add(
                        JSONObject().put("role", "user").put("content", msg.content.content)
                    )
                    Role.Assistant -> {
                        if (msg.content.contentType == ContentType.Text) {
                            messages.add(
                                JSONObject().put("role", assistantRole).put("content", msg.content.content)
                            )
                        }
                    }
                }
            }
        }
        // Post-history character reinforcement (most influential position for small models)
        injectPostHistoryInstruction(messages)
        // Always append the user prompt at the end (includes RAG context if present)
        messages.add(JSONObject().put("role", "user").put("content", userPrompt))
        return sanitizeRoleAlternation(messages)
    }

    /**
     * Inject post-history character reinforcement as a system message right before the
     * user's latest message. Research shows this is the most influential position for
     * maintaining character consistency in small models (SillyTavern/MiniMax approach).
     */
    private fun injectPostHistoryInstruction(messages: MutableList<JSONObject>) {
        val persona = _activePersona.value ?: return
        val raw = persona.buildPostHistoryInstruction().takeIf { it.isNotBlank() } ?: return
        val instruction = persona.applyTemplateVars(raw)
        messages.add(JSONObject().put("role", "system").put("content", instruction))
    }

    /** Ensure no two consecutive messages share the same role (required by llama.cpp chat templates). */
    private fun sanitizeRoleAlternation(messages: List<JSONObject>): List<JSONObject> {
        if (messages.size <= 1) return messages
        val result = mutableListOf(messages.first())
        for (i in 1 until messages.size) {
            val current = messages[i]
            val previous = result.last()
            if (current.getString("role") == previous.getString("role")
                && current.getString("role") != "system") {
                // Merge: append current content to previous
                val merged = previous.getString("content") + "\n" + current.getString("content")
                result[result.lastIndex] = JSONObject()
                    .put("role", previous.getString("role"))
                    .put("content", merged)
            } else {
                result.add(current)
            }
        }
        return result
    }

    // ==================== Tool Call Parsing Utilities ====================

    /**
     * Try to extract tool name and arguments from potentially malformed JSON.
     * Returns Pair(toolName, arguments JSONObject) or null if extraction fails.
     */
    private fun extractToolCallFromArgs(toolCallName: String, toolCallArgs: String): Pair<String, JSONObject>? {
        // Strategy 1: Parse as valid JSON with tool_calls array
        try {
            val argsObject = JSONObject(toolCallArgs)
            val toolCallsArray = argsObject.optJSONArray("tool_calls")
            if (toolCallsArray != null && toolCallsArray.length() > 0) {
                val firstCall = toolCallsArray.getJSONObject(0)
                return Pair(firstCall.getString("name"), firstCall.getJSONObject("arguments"))
            }
            // Maybe it's a direct {"name":"...","arguments":{...}} object
            if (argsObject.has("name") && argsObject.has("arguments")) {
                return Pair(argsObject.getString("name"), argsObject.getJSONObject("arguments"))
            }
        } catch (e: Exception) {
            Log.d(TAG, "Strategy 1 (full JSON) failed: ${e.message}")
        }

        // Strategy 2: Regex extract the first {"name":"...","arguments":{...}} from the text
        try {
            val nameArgRegex = Regex(
                """\{\s*"name"\s*:\s*"([^"]+)"\s*,\s*"arguments"\s*:\s*(\{[^}]*\})""",
                RegexOption.DOT_MATCHES_ALL
            )
            val match = nameArgRegex.find(toolCallArgs)
            if (match != null) {
                val name = match.groupValues[1]
                val argsStr = match.groupValues[2]
                return Pair(name, JSONObject(argsStr))
            }
        } catch (e: Exception) {
            Log.d(TAG, "Strategy 2 (regex name+args) failed: ${e.message}")
        }

        // Strategy 3: Extract arguments with nested braces (handles deeper JSON)
        try {
            val nameIdx = toolCallArgs.indexOf("\"name\"")
            val argsIdx = toolCallArgs.indexOf("\"arguments\"")
            if (nameIdx >= 0 && argsIdx >= 0) {
                val nameValRegex = Regex(""""name"\s*:\s*"([^"]+)"""")
                val nameMatch = nameValRegex.find(toolCallArgs)
                val name = nameMatch?.groupValues?.get(1) ?: toolCallName

                val argsStart = toolCallArgs.indexOf('{', argsIdx)
                if (argsStart >= 0) {
                    var depth = 0
                    var argsEnd = argsStart
                    for (i in argsStart until toolCallArgs.length) {
                        when (toolCallArgs[i]) {
                            '{' -> depth++
                            '}' -> {
                                depth--
                                if (depth == 0) {
                                    argsEnd = i
                                    break
                                }
                            }
                        }
                    }
                    val argsStr = toolCallArgs.substring(argsStart, argsEnd + 1)
                    return Pair(name, JSONObject(argsStr))
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Strategy 3 (balanced braces) failed: ${e.message}")
        }

        Log.e(TAG, "All JSON extraction strategies failed for: ${toolCallArgs.take(200)}")
        return null
    }

    /**
     * Try to parse a tool call from generated token content.
     * Handles Qwen XML format, JSON tool_calls array, and direct JSON objects.
     */
    private fun tryParseToolCallFromContent(content: String): Pair<String, String>? {
        try {
            // Format 1: Qwen <tool_call> XML tags
            val toolCallXmlRegex = Regex(
                "<tool_call>\\s*(\\{.*?\\})\\s*</tool_call>",
                RegexOption.DOT_MATCHES_ALL
            )
            val xmlMatch = toolCallXmlRegex.find(content)
            if (xmlMatch != null) {
                val jsonStr = xmlMatch.groupValues[1]
                val json = JSONObject(jsonStr)
                val name = json.getString("name")
                val argsJson = JSONObject().apply {
                    put("tool_calls", JSONArray().apply {
                        put(JSONObject().apply {
                            put("name", name)
                            put("arguments", json.getJSONObject("arguments"))
                        })
                    })
                }.toString()
                return Pair(name, argsJson)
            }

            // Format 2: JSON with tool_calls array
            val toolCallsJsonRegex = Regex(
                "\\{\\s*\"tool_calls\"\\s*:\\s*\\[.*?\\]\\s*\\}",
                RegexOption.DOT_MATCHES_ALL
            )
            val jsonMatch = toolCallsJsonRegex.find(content)
            if (jsonMatch != null) {
                val jsonStr = jsonMatch.value
                val json = JSONObject(jsonStr)
                val toolCallsArray = json.getJSONArray("tool_calls")
                if (toolCallsArray.length() > 0) {
                    val firstCall = toolCallsArray.getJSONObject(0)
                    val name = firstCall.getString("name")
                    return Pair(name, jsonStr)
                }
            }

            // Format 3: Direct JSON object with name and arguments
            val directJsonRegex = Regex(
                "\\{\\s*\"name\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"arguments\"\\s*:\\s*(\\{.*?\\})\\s*\\}",
                RegexOption.DOT_MATCHES_ALL
            )
            val directMatch = directJsonRegex.find(content)
            if (directMatch != null) {
                val name = directMatch.groupValues[1]
                val argsJson = JSONObject().apply {
                    put("tool_calls", JSONArray().apply {
                        put(JSONObject().apply {
                            put("name", name)
                            put("arguments", JSONObject(directMatch.groupValues[2]))
                        })
                    })
                }.toString()
                return Pair(name, argsJson)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse tool call from content: ${e.message}")
        }
        return null
    }

    /** Normalize tool name: "Web Scraping" → "web_scraping" */
    private fun normalizeToolName(toolName: String): String {
        return toolName.lowercase().replace(" ", "_").replace("-", "_")
    }

    /** Filter out tool call syntax and code blocks from generated text. */
    private fun filterToolCallSyntax(content: String): String {
        var filtered = content
        filtered = filtered.replace(Regex("<tool_call>\\s*\\{.*?\\}\\s*</tool_call>", RegexOption.DOT_MATCHES_ALL), "")
        filtered = filtered.replace(Regex("```json\\s*\\{[^`]*```", RegexOption.DOT_MATCHES_ALL), "")
        filtered = filtered.replace(Regex("```\\s*\\{[^`]*```", RegexOption.DOT_MATCHES_ALL), "")
        filtered = filtered.replace(Regex("\\{\\s*\"tool_calls\"\\s*:[^}]*\\}\\s*", RegexOption.DOT_MATCHES_ALL), "")
        filtered = filtered.replace(Regex("\\{\\s*\"name\"\\s*:\\s*\"[^\"]+\"\\s*,\\s*\"arguments\"\\s*:\\s*\\{.*?\\}\\s*\\}", RegexOption.DOT_MATCHES_ALL), "")
        filtered = filtered.trim()
        filtered = filtered.replace(Regex("\\n{3,}"), "\n\n")
        return filtered
    }

    // ==================== Image Generation ====================

    fun sendImageRequest(
        prompt: String,
        negativePrompt: String? = null,
        steps: Int? = null,
        cfgScale: Float? = null,
        seed: Long = -1L,
        width: Int? = null,
        height: Int? = null,
        scheduler: String? = null
    ) {
        if (!generationManager.isImageModelLoaded()) {
            _error.value = "Please load an image generation model first"
            AppStateManager.setError("Please load an image generation model first")
            return
        }

        if (_isGenerating.value) return

        viewModelScope.launch {
            val modelId = LlmModelWorker.currentDiffusionModelId.value
            if (modelId == null) {
                _error.value = "Model configuration not found"
                return@launch
            }

            val config = getModelConfig(modelId)
            val inferenceParams = if (config != null) {
                DiffusionInferenceParams.fromJson(config.modelInferenceParams)
            } else {
                DiffusionInferenceParams()
            }
            val diffusionConfig = if (config != null) {
                parseDiffusionConfig(config)
            } else {
                DiffusionConfig()
            }

            val finalNegativePrompt = negativePrompt ?: inferenceParams.negativePrompt
            val finalSteps = steps ?: inferenceParams.steps
            val finalCfgScale = cfgScale ?: inferenceParams.cfgScale
            val finalWidth = width ?: diffusionConfig.width
            val finalHeight = height ?: diffusionConfig.height
            val finalScheduler = scheduler ?: inferenceParams.scheduler

            _streamingUserMessage.value = prompt
            imageGenerationStartTime = System.currentTimeMillis()
            userMessageAdded = false

            if (isNewConversation) {
                currentUserMessage = Messages(
                    msgId = "",
                    role = Role.User,
                    content = MessageContent(contentType = ContentType.Text, content = "Generate image: $prompt")
                )
                AppStateManager.setHasMessages(true)
                generateImageForNewChat(prompt, finalNegativePrompt, finalSteps, finalCfgScale, seed, finalWidth, finalHeight, finalScheduler, inferenceParams.showDiffusionProcess, inferenceParams.showDiffusionStride)
            } else {
                val chatId = _currentChatId.value
                if (chatId == null) {
                    _error.value = "No chat selected"
                    return@launch
                }
                chatManager.addUserMessage(chatId, "Generate image: $prompt").onSuccess { userMessage ->
                    currentUserMessage = userMessage
                    AppStateManager.setHasMessages(true)
                    generateImage(chatId, userMessage, prompt, finalNegativePrompt, finalSteps, finalCfgScale, seed, finalWidth, finalHeight, finalScheduler, inferenceParams.showDiffusionProcess, inferenceParams.showDiffusionStride)
                }.onFailure { e ->
                    _error.value = "Failed to save message: ${e.message}"
                    resetStreamingState()
                }
            }
        }
    }

    suspend fun getModelConfig(modelId: String): com.dark.tool_neuron.models.table_schema.ModelConfig? {
        return AppContainer.getModelRepository().getConfigByModelId(modelId)
    }

    private fun parseDiffusionConfig(config: com.dark.tool_neuron.models.table_schema.ModelConfig): DiffusionConfig {
        if (config.modelLoadingParams == null) return DiffusionConfig()
        return try {
            val json = org.json.JSONObject(config.modelLoadingParams)
            DiffusionConfig(
                textEmbeddingSize = json.optInt("text_embedding_size", 768),
                runOnCpu = json.optBoolean("run_on_cpu", false),
                useCpuClip = json.optBoolean("use_cpu_clip", true),
                isPony = json.optBoolean("is_pony", false),
                httpPort = json.optInt("http_port", 8081),
                safetyMode = json.optBoolean("safety_mode", false),
                width = json.optInt("width", 512),
                height = json.optInt("height", 512)
            )
        } catch (e: Exception) {
            DiffusionConfig()
        }
    }

    private fun generateImageForNewChat(
        prompt: String, negativePrompt: String, steps: Int, cfgScale: Float,
        seed: Long, width: Int, height: Int, scheduler: String,
        showDiffusionProcess: Boolean = true, showDiffusionStride: Int = 1
    ) {
        generationJob = viewModelScope.launch {
            _error.value = null
            _streamingImage.value = null
            _imageGenerationProgress.value = 0f
            currentGeneratedImage = null
            _isGenerating.value = true
            AppStateManager.setGeneratingImage()

            try {
                generationManager.generateImageStreaming(prompt, negativePrompt, steps, cfgScale, seed, width, height, scheduler, showDiffusionProcess = showDiffusionProcess, showDiffusionStride = showDiffusionStride).collect { event ->
                    when (event) {
                        is LlmModelWorker.DiffusionGenerationEvent.Progress -> {
                            _imageGenerationProgress.value = event.progress
                            _imageGenerationStep.value = "Step ${event.currentStep}/${event.totalSteps}"
                            event.intermediateImage?.let { _streamingImage.value = it }
                        }
                        is LlmModelWorker.DiffusionGenerationEvent.Complete -> {
                            _imageGenerationProgress.value = 1f
                            _streamingImage.value = event.image
                            currentGeneratedImage = event.image
                            val generationTime = System.currentTimeMillis() - imageGenerationStartTime
                            currentImageMetrics = ImageGenerationMetrics(steps = steps, cfgScale = cfgScale, seed = event.seed, width = event.width, height = event.height, scheduler = scheduler, generationTimeMs = generationTime)
                            _isGenerating.value = false
                            val imageBase64 = generationManager.bitmapToBase64(event.image)
                            createChatWithImageMessage("Generate image: $prompt", imageBase64, prompt, event.seed)
                        }
                        is LlmModelWorker.DiffusionGenerationEvent.Error -> {
                            handleImageGenerationError(prompt, event.message)
                        }
                    }
                }
            } catch (e: Exception) {
                handleImageGenerationException(prompt, e)
            }
        }
    }

    private fun generateImage(
        chatId: String, userMessage: Messages, prompt: String, negativePrompt: String,
        steps: Int, cfgScale: Float, seed: Long, width: Int, height: Int, scheduler: String,
        showDiffusionProcess: Boolean = true, showDiffusionStride: Int = 1
    ) {
        generationJob = viewModelScope.launch {
            _error.value = null
            _streamingImage.value = null
            _imageGenerationProgress.value = 0f
            _isGenerating.value = true
            AppStateManager.setGeneratingImage()

            try {
                generationManager.generateImageStreaming(prompt, negativePrompt, steps, cfgScale, seed, width, height, scheduler, showDiffusionProcess = showDiffusionProcess, showDiffusionStride = showDiffusionStride).collect { event ->
                    when (event) {
                        is LlmModelWorker.DiffusionGenerationEvent.Progress -> {
                            _imageGenerationProgress.value = event.progress
                            _imageGenerationStep.value = "Step ${event.currentStep}/${event.totalSteps}"
                            event.intermediateImage?.let { _streamingImage.value = it }
                        }
                        is LlmModelWorker.DiffusionGenerationEvent.Complete -> {
                            _imageGenerationProgress.value = 1f
                            _streamingImage.value = event.image
                            _isGenerating.value = false
                            val generationTime = System.currentTimeMillis() - imageGenerationStartTime
                            currentImageMetrics = ImageGenerationMetrics(steps = steps, cfgScale = cfgScale, seed = event.seed, width = event.width, height = event.height, scheduler = scheduler, generationTimeMs = generationTime)
                            if (!userMessageAdded) { _messages.add(userMessage); userMessageAdded = true }
                            val imageBase64 = generationManager.bitmapToBase64(event.image)
                            val imageMessage = Messages(
                                role = Role.Assistant,
                                content = MessageContent(contentType = ContentType.Image, content = "Generated image for: $prompt", imageData = imageBase64, imagePrompt = prompt, imageSeed = event.seed),
                                imageMetrics = currentImageMetrics
                            )
                            _messages.add(imageMessage)
                            chatManager.addImageMessage(chatId, imageBase64, prompt, event.seed, currentImageMetrics)
                            AppStateManager.setGenerationComplete()
                            AppStateManager.chatRefreshed()
                            resetStreamingState()
                        }
                        is LlmModelWorker.DiffusionGenerationEvent.Error -> {
                            handleImageGenerationErrorExisting(chatId, userMessage, prompt, event.message)
                        }
                    }
                }
            } catch (e: Exception) {
                handleImageGenerationExceptionExisting(chatId, userMessage, prompt, e)
            }
        }
    }

    // ==================== Helper Functions ====================

    private suspend fun createChatWithMessages(
        userPrompt: String,
        assistantResponse: String,
        metrics: DecodingMetrics?,
        toolChainSteps: List<ToolChainStepData>? = null
    ) {
        val filteredResponse = filterToolCallSyntax(assistantResponse)
        val ragResultItems = _currentRagResults.value.takeIf { it.isNotEmpty() }?.map { result ->
            RagResultItem(ragName = result.ragName, content = result.content, score = result.score, nodeId = result.nodeId)
        }
        val pluginResults = _messages.filter { it.content.contentType == ContentType.PluginResult }

        val newChatId = chatManager.createNewChat().getOrElse { e ->
            _error.value = "Failed to create chat: ${e.message}"
            AppStateManager.setError("Failed to create chat: ${e.message}")
            return
        }
        _currentChatId.value = newChatId

        chatManager.addUserMessage(newChatId, userPrompt).getOrElse { e ->
            _error.value = "Failed to save chat: ${e.message}"
            AppStateManager.setError("Failed to save chat: ${e.message}")
            return
        }

        pluginResults.forEach { pluginMsg ->
            chatManager.addMessage(newChatId, pluginMsg)
        }
        if (filteredResponse.isNotBlank()) {
            chatManager.addAssistantMessage(newChatId, filteredResponse, metrics, ragResultItems, toolChainSteps)
        }

        val loadedMessages = chatManager.getChatMessages(newChatId).getOrElse {
            AppStateManager.setGenerationComplete()
            resetStreamingState()
            return
        }
        _messages.clear()
        _messages.addAll(loadedMessages)
        AppStateManager.setGenerationComplete()
        AppStateManager.chatRefreshed()
        val spokenMsgId = loadedMessages.lastOrNull { it.role == Role.Assistant }?.msgId
        resetStreamingState()
        viewModelScope.launch { autoSpeakIfEnabled(filteredResponse, spokenMsgId) }
    }

    private suspend fun createChatWithImageMessage(
        userPrompt: String, imageBase64: String, imagePrompt: String, seed: Long
    ) {
        val newChatId = chatManager.createNewChat().getOrElse { e ->
            _error.value = "Failed to create chat: ${e.message}"
            AppStateManager.setError("Failed to create chat: ${e.message}")
            return
        }
        _currentChatId.value = newChatId

        val userMessage = chatManager.addUserMessage(newChatId, userPrompt).getOrElse { e ->
            _error.value = "Failed to save chat: ${e.message}"
            AppStateManager.setError("Failed to save chat: ${e.message}")
            return
        }
        _messages.add(userMessage)
        userMessageAdded = true

        val imageMessage = chatManager.addImageMessage(newChatId, imageBase64, imagePrompt, seed, currentImageMetrics).getOrElse { e ->
            _error.value = "Failed to save image: ${e.message}"
            AppStateManager.setError("Failed to save image: ${e.message}")
            return
        }
        _messages.add(imageMessage)
        AppStateManager.setGenerationComplete()
        AppStateManager.chatRefreshed()
        resetStreamingState()
    }

    // ==================== Error Handlers ====================

    private fun handleImageGenerationError(prompt: String, errorMessage: String) {
        _isGenerating.value = false
        _error.value = errorMessage
        AppStateManager.setError(errorMessage)
        resetStreamingState()
    }

    private fun handleImageGenerationException(prompt: String, exception: Exception) {
        _isGenerating.value = false
        _error.value = exception.message
        AppStateManager.setError(exception.message ?: "Unknown error")
        resetStreamingState()
    }

    private fun handleImageGenerationErrorExisting(chatId: String, userMessage: Messages, prompt: String, errorMessage: String) {
        _isGenerating.value = false
        _error.value = errorMessage
        AppStateManager.setError(errorMessage)
        if (!userMessageAdded) { _messages.add(userMessage); userMessageAdded = true }
        _messages.add(Messages(role = Role.Assistant, content = MessageContent(contentType = ContentType.Text, content = "Error generating image: $errorMessage")))
        resetStreamingState()
    }

    private fun handleImageGenerationExceptionExisting(chatId: String, userMessage: Messages, prompt: String, exception: Exception) {
        _isGenerating.value = false
        _error.value = exception.message
        AppStateManager.setError(exception.message ?: "Unknown error")
        if (!userMessageAdded) { _messages.add(userMessage); userMessageAdded = true }
        resetStreamingState()
    }

    private fun resetStreamingState() {
        _isGenerating.value = false
        _streamingUserMessage.value = null
        _streamingAssistantMessage.value = ""
        _streamingImage.value = null
        _imageGenerationProgress.value = 0f
        _imageGenerationStep.value = ""
        currentUserMessage = null
        currentGeneratedContent = ""
        currentGeneratedImage = null
        currentMetrics = null
        currentImageMetrics = null
        userMessageAdded = false
        _currentToolName.value = null
        _currentProcessingPhase.value = null
        _toolChainSteps.value = emptyList()
        _currentToolChainRound.value = 0
        _agentPhase.value = AgentPhase.Idle
        _agentPlan.value = null
        _agentSummary.value = null
        _currentRagContext.value = null
        _currentRagResults.value = emptyList()
    }

    // ==================== Generation Control ====================

    fun stop() {
        if (TTSManager.isPlaying.value) { TTSManager.stopPlayback() }
        when (_currentGenerationType.value) {
            GenerationManager.ModelType.TEXT_GENERATION -> {
                generationManager.stopTextGeneration()
                // Delay job cancel briefly so the flow can receive final Metrics event from native
                viewModelScope.launch {
                    kotlinx.coroutines.delay(200)
                    generationJob?.cancel()
                    generationJob = null
                    handleTextStop()
                    _isGenerating.value = false
                    AppStateManager.setGenerationComplete()
                }
                return
            }
            GenerationManager.ModelType.IMAGE_GENERATION -> {
                generationManager.stopImageGeneration()
                handleImageStop()
            }
            GenerationManager.ModelType.AUDIO_GENERATION -> {
                stopTTS()
            }
        }

        generationJob?.cancel()
        generationJob = null
        _isGenerating.value = false
        AppStateManager.setGenerationComplete()
    }

    private fun handleTextStop() {
        val chatId = _currentChatId.value

        // Capture state before launching coroutine to avoid race with resetStreamingState()
        val savedUserMessage = currentUserMessage
        val savedContent = currentGeneratedContent
        val savedMetrics = currentMetrics
        val savedUserMessageAdded = userMessageAdded

        if (chatId != null && savedUserMessage != null && savedContent.isNotEmpty()) {
            viewModelScope.launch {
                if (!savedUserMessageAdded) { _messages.add(savedUserMessage); userMessageAdded = true }
                val assistantMessage = Messages(
                    role = Role.Assistant,
                    content = MessageContent(contentType = ContentType.Text, content = "$savedContent [stopped]"),
                    decodingMetrics = savedMetrics
                )
                _messages.add(assistantMessage)
                chatManager.addAssistantMessage(chatId, "$savedContent [stopped]", savedMetrics)
                // Restore grammar in case we stopped mid-agent-flow
                try { PluginManager.restoreGrammar() } catch (_: Exception) {}
                resetStreamingState()
            }
        } else {
            if (savedUserMessage != null && !savedUserMessageAdded) {
                _messages.add(savedUserMessage)
                userMessageAdded = true
            }
            // Restore grammar in case we stopped mid-agent-flow
            try { PluginManager.restoreGrammar() } catch (_: Exception) {}
            resetStreamingState()
        }
    }

    private fun handleImageStop() {
        val chatId = _currentChatId.value

        // Capture state before launching coroutine to avoid race with resetStreamingState()
        val savedUserMessage = currentUserMessage
        val savedImage = currentGeneratedImage
        val savedImageMetrics = currentImageMetrics
        val savedUserMessageAdded = userMessageAdded

        if (chatId != null && savedUserMessage != null && savedImage != null) {
            viewModelScope.launch {
                if (!savedUserMessageAdded) { _messages.add(savedUserMessage); userMessageAdded = true }
                val imageBase64 = generationManager.bitmapToBase64(savedImage)
                val imageMessage = Messages(
                    role = Role.Assistant,
                    content = MessageContent(contentType = ContentType.Image, content = "Image generation stopped", imageData = imageBase64),
                    imageMetrics = savedImageMetrics
                )
                _messages.add(imageMessage)
                chatManager.addImageMessage(chatId, imageBase64, "", -1L, savedImageMetrics)
                resetStreamingState()
            }
        } else {
            if (savedUserMessage != null && !savedUserMessageAdded) {
                _messages.add(savedUserMessage)
                userMessageAdded = true
            }
            resetStreamingState()
        }
    }

    // ==================== TTS Controls ====================

    private suspend fun autoSpeakIfEnabled(text: String, msgId: String? = null) {
        if (text.isBlank()) return
        val settings = ttsDataStore.settings.first()
        if (!settings.autoSpeak) return

        if (!TTSManager.isLoaded()) {
            val modelDir = TTSManager.getModelDirectory() ?: return
            withContext(Dispatchers.IO) {
                TTSManager.loadModel(modelDir, settings.useNNAPI)
            }
            if (!TTSManager.isLoaded()) return
        }

        TTSManager.speak(text = text, settings = settings, msgId = msgId)
    }

    fun speakMessage(message: Messages) {
        if (message.content.contentType != ContentType.Text) return
        val text = message.content.content
        if (text.isBlank()) return

        viewModelScope.launch {
            if (!TTSManager.isLoaded()) {
                val modelDir = TTSManager.getModelDirectory()
                if (modelDir == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(appContext, "Install the TTS model from Settings", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }
                val settings = ttsDataStore.settings.first()
                withContext(Dispatchers.IO) { TTSManager.loadModel(modelDir, settings.useNNAPI) }
                if (!TTSManager.isLoaded()) return@launch
            }
            val settings = ttsDataStore.settings.first()
            TTSManager.speak(text = text, settings = settings, msgId = message.msgId)
        }
    }

    fun stopTTS() {
        TTSManager.stopPlayback()
    }

    // ==================== UI Controls ====================

    fun clearMessages() {
        _messages.clear()
        resetStreamingState()
        _error.value = null
        AppStateManager.setHasMessages(false)
    }

    fun clearError() {
        _error.value = null
        AppStateManager.clearError()
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            chatManager.deleteMessage(messageId).onSuccess {
                _messages.removeIf { it.msgId == messageId }
            }.onFailure { e ->
                _error.value = "Failed to delete message: ${e.message}"
            }
        }
    }

    fun showDynamicWindow() {
        _showDynamicWindow.value = _showDynamicWindow.value.not()
    }

    fun hideDynamicWindow() {
        _showDynamicWindow.value = false
    }

    fun showModelList() {
        _showModelList.value = true
    }

    fun hideModelList() {
        _showModelList.value = false
    }

    companion object {
        private const val TAG = "ChatViewModel"
    }
}
