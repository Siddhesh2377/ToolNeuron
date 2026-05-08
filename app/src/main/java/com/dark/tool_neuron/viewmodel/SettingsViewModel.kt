package com.dark.tool_neuron.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.tool_neuron.data.AppPreferences
import com.dark.tool_neuron.data.SecurityManager
import com.dark.tool_neuron.data.VerifyResult
import com.dark.tool_neuron.model.ModelInfo
import com.dark.tool_neuron.model.NavScreens
import com.dark.tool_neuron.model.enums.ProviderType
import com.dark.tool_neuron.repo.ModelRepository
import com.dark.tool_neuron.repo.RagManager
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.voice.VoiceModelManager
import com.dark.tool_neuron.ui.screens.settings.model.SettingsChoiceOption
import com.dark.tool_neuron.ui.screens.settings.model.SettingsDialog
import com.dark.tool_neuron.ui.screens.settings.model.SettingsItem
import com.dark.tool_neuron.ui.screens.settings.model.SettingsSection
import com.dark.tool_neuron.ui.screens.settings.model.SettingsState
import com.dark.download_manager.formatBytes
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val security: SecurityManager,
    private val ragManager: RagManager,
    private val modelRepo: ModelRepository,
    private val prefs: AppPreferences,
    private val voiceManager: VoiceModelManager,
) : ViewModel() {

    private val _navEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val navEvents: SharedFlow<String> = _navEvents.asSharedFlow()

    private val _dialog = MutableStateFlow<SettingsDialog?>(null)
    private val _snackbar = MutableStateFlow<String?>(null)
    private val _lockEnabled = MutableStateFlow(security.isLockEnabled)
    private val _panicPinSet = MutableStateFlow(security.hasPanicPin)
    private val _activeTts = MutableStateFlow(prefs.activeTtsModelId)
    private val _activeStt = MutableStateFlow(prefs.activeSttModelId)
    private val _ragSmartRerank = MutableStateFlow(prefs.ragSmartRerank)
    private val _ragMultiQuery = MutableStateFlow(prefs.ragMultiQuery)
    private val _ragDeepResearch = MutableStateFlow(prefs.ragDeepResearch)
    private val _researchMaxIter = MutableStateFlow(prefs.researchMaxIterations)
    private val _researchMaxQ = MutableStateFlow(prefs.researchMaxQuestions)
    private val _researchPerSearch = MutableStateFlow(prefs.researchResultsPerSearch)
    private val _researchCancelBg = MutableStateFlow(prefs.researchCancelOnBackground)
    private val _researchActiveModel = MutableStateFlow(prefs.activeResearchModelId)
    private val appVersion: String = resolveVersion()

    val state: StateFlow<SettingsState> = combine(
        modelRepo.models,
        ragManager.defaultEmbeddingModelId,
        _lockEnabled,
        _dialog,
        _snackbar,
        _activeTts,
        _activeStt,
        _panicPinSet,
        _ragSmartRerank,
        _ragMultiQuery,
        _ragDeepResearch,
        _researchMaxIter,
        _researchMaxQ,
        _researchPerSearch,
        _researchCancelBg,
        _researchActiveModel,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val models = values[0] as List<ModelInfo>
        val defaultEmbedding = values[1] as String?
        val lockOn = values[2] as Boolean
        val dialog = values[3] as SettingsDialog?
        val snackbar = values[4] as String?
        val activeTts = values[5] as String
        val activeStt = values[6] as String
        val panicSet = values[7] as Boolean
        val rerank = values[8] as Boolean
        val multiQuery = values[9] as Boolean
        val deepResearch = values[10] as Boolean
        val researchMaxIter = values[11] as Int
        val researchMaxQ = values[12] as Int
        val researchPerSearch = values[13] as Int
        val researchCancelBg = values[14] as Boolean
        val researchActiveModel = values[15] as String

        SettingsState(
            sections = buildSections(
                models = models,
                defaultEmbedding = defaultEmbedding,
                lockEnabled = lockOn,
                activeTts = activeTts,
                activeStt = activeStt,
                panicPinSet = panicSet,
                ragSmartRerank = rerank,
                ragMultiQuery = multiQuery,
                ragDeepResearch = deepResearch,
                researchMaxIter = researchMaxIter,
                researchMaxQ = researchMaxQ,
                researchPerSearch = researchPerSearch,
                researchCancelBg = researchCancelBg,
                researchActiveModel = researchActiveModel,
            ),
            dialog = dialog,
            snackbarMessage = snackbar,
            appVersion = appVersion,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = SettingsState(appVersion = appVersion),
    )

    fun dismissDialog() { _dialog.value = null }

    fun clearSnackbar() { _snackbar.value = null }

    fun requestChoiceDialog(item: SettingsItem.Choice) {
        _dialog.value = SettingsDialog.Choice(
            itemId = item.id,
            title = item.title,
            options = item.options,
            selectedKey = item.selectedKey,
            allowClear = item.id in CLEARABLE_CHOICE_IDS,
            onSelect = item.onSelect,
        )
    }

    private fun buildSections(
        models: List<ModelInfo>,
        defaultEmbedding: String?,
        lockEnabled: Boolean,
        activeTts: String,
        activeStt: String,
        panicPinSet: Boolean,
        ragSmartRerank: Boolean,
        ragMultiQuery: Boolean,
        ragDeepResearch: Boolean,
        researchMaxIter: Int,
        researchMaxQ: Int,
        researchPerSearch: Int,
        researchCancelBg: Boolean,
        researchActiveModel: String,
    ): List<SettingsSection> = listOf(
        chatAndRagSection(models, defaultEmbedding, ragSmartRerank, ragMultiQuery, ragDeepResearch),
        researchSection(models, researchMaxIter, researchMaxQ, researchPerSearch, researchCancelBg, researchActiveModel),
        voiceSection(models, activeTts, activeStt),
        privacySection(lockEnabled, panicPinSet),
        aboutSection(),
    )

    private fun researchSection(
        models: List<ModelInfo>,
        maxIter: Int,
        maxQ: Int,
        perSearch: Int,
        cancelBg: Boolean,
        activeModelId: String,
    ): SettingsSection {
        val ggufModels = models.filter { it.providerType == ProviderType.GGUF }
        val modelOptions = ggufModels.map {
            SettingsChoiceOption(key = it.id, label = it.name, description = formatBytes(it.fileSize))
        }
        val resolvedModel = activeModelId.takeIf { it.isNotBlank() && ggufModels.any { m -> m.id == it } }
        return SettingsSection(
            id = "research",
            title = "Research",
            description = "Multi-iteration web research pipeline.",
            icon = TnIcons.Compass,
            items = listOf(
                SettingsItem.Choice(
                    id = ID_RESEARCH_MAX_ITER,
                    title = "Max iterations",
                    subtitle = "Search → fetch → ask follow-ups, repeated up to this many times.",
                    icon = TnIcons.Compass,
                    selectedKey = maxIter.toString(),
                    options = (1..10).map { SettingsChoiceOption(it.toString(), "$it") },
                    onSelect = { key ->
                        val v = key?.toIntOrNull() ?: AppPreferences.DEFAULT_RESEARCH_MAX_ITERATIONS
                        prefs.researchMaxIterations = v
                        _researchMaxIter.value = prefs.researchMaxIterations
                        _dialog.value = null
                    },
                ),
                SettingsItem.Choice(
                    id = ID_RESEARCH_MAX_Q,
                    title = "Follow-up questions per iteration",
                    subtitle = "Upper bound on new questions generated each round.",
                    icon = TnIcons.MessageCircle,
                    selectedKey = maxQ.toString(),
                    options = (1..6).map { SettingsChoiceOption(it.toString(), "$it") },
                    onSelect = { key ->
                        val v = key?.toIntOrNull() ?: AppPreferences.DEFAULT_RESEARCH_MAX_QUESTIONS
                        prefs.researchMaxQuestions = v
                        _researchMaxQ.value = prefs.researchMaxQuestions
                        _dialog.value = null
                    },
                ),
                SettingsItem.Choice(
                    id = ID_RESEARCH_PER_SEARCH,
                    title = "Results per search",
                    subtitle = "How many DuckDuckGo hits to fetch per query.",
                    icon = TnIcons.Search,
                    selectedKey = perSearch.toString(),
                    options = (3..10).map { SettingsChoiceOption(it.toString(), "$it") },
                    onSelect = { key ->
                        val v = key?.toIntOrNull() ?: AppPreferences.DEFAULT_RESEARCH_RESULTS_PER_SEARCH
                        prefs.researchResultsPerSearch = v
                        _researchPerSearch.value = prefs.researchResultsPerSearch
                        _dialog.value = null
                    },
                ),
                SettingsItem.Toggle(
                    id = ID_RESEARCH_CANCEL_BG,
                    title = "Cancel on background",
                    subtitle = "Stop in-flight research when the app is backgrounded.",
                    icon = TnIcons.PlayerStop,
                    checked = cancelBg,
                    onToggle = { value ->
                        prefs.researchCancelOnBackground = value
                        _researchCancelBg.value = value
                    },
                ),
                SettingsItem.Choice(
                    id = ID_RESEARCH_ACTIVE_MODEL,
                    title = "Research model",
                    subtitle = "Defaults to the active chat model when unset.",
                    icon = TnIcons.Sparkles,
                    selectedKey = resolvedModel,
                    options = modelOptions,
                    emptyMessage = if (modelOptions.isEmpty()) "Install a chat model first" else "Use active chat model",
                    onSelect = { key ->
                        prefs.activeResearchModelId = key.orEmpty()
                        _researchActiveModel.value = prefs.activeResearchModelId
                        _dialog.value = null
                    },
                ),
            ),
        )
    }

    private fun chatAndRagSection(
        models: List<ModelInfo>,
        defaultEmbedding: String?,
        ragSmartRerank: Boolean,
        ragMultiQuery: Boolean,
        ragDeepResearch: Boolean,
    ): SettingsSection {
        val embeddings = models.filter { it.providerType == ProviderType.EMBEDDING }
        val options = embeddings.map {
            SettingsChoiceOption(key = it.id, label = it.name, description = formatBytes(it.fileSize))
        }
        return SettingsSection(
            id = "chat_rag",
            title = "Chat & RAG",
            description = "Defaults for document indexing and retrieval.",
            icon = TnIcons.MessageCircle,
            items = listOf(
                SettingsItem.Choice(
                    id = ID_DEFAULT_EMBEDDING,
                    title = "Default embedding model",
                    subtitle = "Used when you attach a document to a chat.",
                    icon = TnIcons.Database,
                    selectedKey = defaultEmbedding,
                    options = options,
                    emptyMessage = if (options.isEmpty()) "Install one from the Store" else "Auto-pick first",
                    onSelect = { key ->
                        ragManager.setDefaultEmbeddingModelId(key)
                        _dialog.value = null
                    },
                ),
                SettingsItem.Toggle(
                    id = ID_RAG_RERANK,
                    title = "Smart rerank",
                    subtitle = "Ask the chat model to rescore retrieved chunks (slower, better)",
                    icon = TnIcons.Database,
                    checked = ragSmartRerank,
                    onToggle = { value ->
                        prefs.ragSmartRerank = value
                        _ragSmartRerank.value = value
                    },
                ),
                SettingsItem.Toggle(
                    id = ID_RAG_MULTI_QUERY,
                    title = "Thorough search",
                    subtitle = "Rewrite query into 3 variants and fuse results (slower, better recall)",
                    icon = TnIcons.Database,
                    checked = ragMultiQuery,
                    onToggle = { value ->
                        prefs.ragMultiQuery = value
                        _ragMultiQuery.value = value
                    },
                ),
                SettingsItem.Toggle(
                    id = ID_RAG_DEEP_RESEARCH,
                    title = "Deep research",
                    subtitle = "Let the model issue follow-up retrievals (slowest, best for hard questions)",
                    icon = TnIcons.Database,
                    checked = ragDeepResearch,
                    onToggle = { value ->
                        prefs.ragDeepResearch = value
                        _ragDeepResearch.value = value
                    },
                ),
                SettingsItem.Action(
                    id = ID_RAG_DEBUG,
                    title = "Retrieval debug",
                    subtitle = "Inspect dense, BM25, fused, and final context",
                    icon = TnIcons.Database,
                    onClick = { _navEvents.tryEmit(NavScreens.RagDebug.route) },
                ),
            ),
        )
    }

    private fun voiceSection(
        models: List<ModelInfo>,
        activeTts: String,
        activeStt: String,
    ): SettingsSection {
        val ttsModels = models.filter { it.providerType == ProviderType.TTS }
        val sttModels = models.filter { it.providerType == ProviderType.STT }
        val ttsOptions = ttsModels.map {
            SettingsChoiceOption(key = it.id, label = it.name, description = formatBytes(it.fileSize))
        }
        val sttOptions = sttModels.map {
            SettingsChoiceOption(key = it.id, label = it.name, description = formatBytes(it.fileSize))
        }
        val resolvedTts = activeTts.takeIf { it.isNotBlank() && ttsModels.any { m -> m.id == it } }
        val resolvedStt = activeStt.takeIf { it.isNotBlank() && sttModels.any { m -> m.id == it } }
        return SettingsSection(
            id = "voice",
            title = "Voice",
            description = "Defaults for text-to-speech and speech-to-text.",
            icon = TnIcons.Volume,
            items = listOf(
                SettingsItem.Choice(
                    id = ID_DEFAULT_TTS,
                    title = "Default text-to-speech model",
                    subtitle = "Used when you tap Speak on a reply.",
                    icon = TnIcons.Volume,
                    selectedKey = resolvedTts,
                    options = ttsOptions,
                    emptyMessage = if (ttsOptions.isEmpty()) "Install one from the Store" else "Auto-pick first",
                    onSelect = { key -> applyActiveTts(key) },
                ),
                SettingsItem.Choice(
                    id = ID_DEFAULT_STT,
                    title = "Default speech-to-text model",
                    subtitle = "Used when you tap the mic.",
                    icon = TnIcons.Mic,
                    selectedKey = resolvedStt,
                    options = sttOptions,
                    emptyMessage = if (sttOptions.isEmpty()) "Install one from the Store" else "Auto-pick first",
                    onSelect = { key -> applyActiveStt(key) },
                ),
            ),
        )
    }

    private fun applyActiveTts(key: String?) {
        val next = key.orEmpty()
        prefs.activeTtsModelId = next
        _activeTts.value = next
        _dialog.value = null
        viewModelScope.launch { voiceManager.unloadTts() }
    }

    private fun applyActiveStt(key: String?) {
        val next = key.orEmpty()
        prefs.activeSttModelId = next
        _activeStt.value = next
        _dialog.value = null
        viewModelScope.launch { voiceManager.unloadStt() }
    }

    private fun privacySection(lockEnabled: Boolean, panicPinSet: Boolean): SettingsSection {
        val statusLabel = if (lockEnabled) "Enabled (PIN)" else "Disabled"
        val items = mutableListOf<SettingsItem>(
            SettingsItem.Info(
                id = "lock_status",
                title = "App lock",
                subtitle = "Requires a PIN on launch.",
                icon = TnIcons.Lock,
                value = statusLabel,
            ),
        )
        if (lockEnabled) {
            items += SettingsItem.Action(
                id = "change_pin",
                title = "Change PIN",
                subtitle = "Enter a new 6-digit PIN.",
                icon = TnIcons.Edit,
                onClick = { openPinDialog(isChange = true) },
            )
            if (panicPinSet) {
                items += SettingsItem.Action(
                    id = "change_panic_pin",
                    title = "Change panic PIN",
                    subtitle = "Replace the existing wipe-on-entry PIN.",
                    icon = TnIcons.AlertTriangle,
                    onClick = { openPanicPinDialog(isChange = true) },
                )
                items += SettingsItem.Action(
                    id = "remove_panic_pin",
                    title = "Remove panic PIN",
                    subtitle = "Stop wiping on a second PIN entry.",
                    icon = TnIcons.Trash,
                    destructive = true,
                    onClick = { openRemovePanicPinDialog() },
                )
            } else {
                items += SettingsItem.Action(
                    id = "set_panic_pin",
                    title = "Set panic PIN",
                    subtitle = "A second PIN that wipes the vault when entered.",
                    icon = TnIcons.AlertTriangle,
                    onClick = { openPanicPinDialog(isChange = false) },
                )
            }
            items += SettingsItem.Action(
                id = "disable_lock",
                title = "Disable app lock",
                subtitle = "Enter your current PIN to remove it.",
                icon = TnIcons.ShieldCheck,
                destructive = true,
                onClick = { openDisableLockDialog() },
            )
        } else {
            items += SettingsItem.Action(
                id = "enable_lock",
                title = "Enable app lock",
                subtitle = "Set a PIN required on launch.",
                icon = TnIcons.Shield,
                onClick = { openPinDialog(isChange = false) },
            )
        }
        return SettingsSection(
            id = "privacy",
            title = "Privacy",
            description = "App lock and tamper checks.",
            icon = TnIcons.Shield,
            items = items,
        )
    }

    private fun openPanicPinDialog(isChange: Boolean) {
        _dialog.value = SettingsDialog.PinEntry(
            title = if (isChange) "Change panic PIN" else "Set panic PIN",
            message = "Pick a 6-digit PIN that's different from your real one. Entering it instead of the real PIN wipes everything on this device.",
            minLength = 6,
            onSubmit = { pin ->
                viewModelScope.launch {
                    val ok = withContext(Dispatchers.Default) { security.setPanicPin(pin) }
                    _dialog.value = null
                    if (ok) {
                        _panicPinSet.value = true
                        _snackbar.value = if (isChange) "Panic PIN updated" else "Panic PIN set"
                    } else {
                        _snackbar.value = "Couldn't set panic PIN"
                    }
                }
            },
        )
    }

    private fun openRemovePanicPinDialog() {
        _dialog.value = SettingsDialog.Confirm(
            title = "Remove panic PIN?",
            message = "Entering it will no longer wipe the vault. Your real PIN is unaffected.",
            confirmLabel = "Remove",
            destructive = true,
            onConfirm = {
                val ok = security.clearPanicPin()
                _dialog.value = null
                if (ok) {
                    _panicPinSet.value = false
                    _snackbar.value = "Panic PIN removed"
                } else {
                    _snackbar.value = "Couldn't remove panic PIN"
                }
            },
        )
    }

    private fun openPinDialog(isChange: Boolean) {
        _dialog.value = SettingsDialog.PinEntry(
            title = if (isChange) "Change PIN" else "Set app lock",
            message = "Enter a PIN of at least 6 digits.",
            minLength = 6,
            onSubmit = { pin ->
                viewModelScope.launch {
                    withContext(Dispatchers.Default) { security.setPassword(pin) }
                    _lockEnabled.value = true
                    _panicPinSet.value = false
                    _dialog.value = null
                    _snackbar.value = if (isChange) "PIN updated" else "App lock enabled"
                }
            },
        )
    }

    private fun openDisableLockDialog() {
        _dialog.value = SettingsDialog.PinEntry(
            title = "Disable app lock",
            message = "Enter your current PIN to confirm.",
            minLength = 6,
            confirmLabel = "Disable",
            onSubmit = { pin ->
                viewModelScope.launch {
                    val outcome = withContext(Dispatchers.Default) { security.verifyPassword(pin) }
                    when (outcome) {
                        VerifyResult.Success -> {
                            security.disableLock()
                            _lockEnabled.value = false
                            _panicPinSet.value = false
                            _dialog.value = null
                            _snackbar.value = "App lock disabled"
                        }
                        VerifyResult.WrongPin -> {
                            _dialog.value = null
                            _snackbar.value = "Incorrect PIN"
                        }
                        is VerifyResult.LockedOut -> {
                            _dialog.value = null
                            _snackbar.value = "Too many tries — locked until ${outcome.retryAtMs}"
                        }
                        VerifyResult.Wiped -> {
                            _dialog.value = null
                            _lockEnabled.value = false
                            _panicPinSet.value = false
                            _snackbar.value = "Vault wiped"
                        }
                        VerifyResult.NoLock -> {
                            _dialog.value = null
                            _lockEnabled.value = false
                            _panicPinSet.value = false
                        }
                    }
                }
            },
        )
    }

    private fun aboutSection(): SettingsSection = SettingsSection(
        id = "about",
        title = "About",
        description = "App info and legal.",
        icon = TnIcons.Info,
        items = listOf(
            SettingsItem.Info(
                id = "version",
                title = "Version",
                icon = TnIcons.InfoCircle,
                value = appVersion,
            ),
            SettingsItem.Info(
                id = "license",
                title = "License",
                subtitle = "Open source, permissive.",
                icon = TnIcons.BookOpen,
                value = "MIT",
            ),
            SettingsItem.Action(
                id = "terms",
                title = "Terms of use",
                subtitle = "How this app handles your data.",
                icon = TnIcons.Shield,
                onClick = { _navEvents.tryEmit(NavScreens.TermsConditions.route) },
            ),
            SettingsItem.Action(
                id = "credits",
                title = "Roll the credits",
                subtitle = "See who built this and what it runs on.",
                icon = TnIcons.Star,
                onClick = { _navEvents.tryEmit(NavScreens.Credits.route) },
            ),
        ),
    )

    private fun resolveVersion(): String = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.versionName ?: "1.0"
    }.getOrDefault("1.0")

    companion object {
        const val SECTION_CHAT_RAG = "chat_rag"
        const val SECTION_RESEARCH = "research"
        const val SECTION_VOICE = "voice"
        const val SECTION_PRIVACY = "privacy"
        const val SECTION_ABOUT = "about"

        private const val ID_DEFAULT_EMBEDDING = "default_embedding_model"
        private const val ID_DEFAULT_TTS = "default_tts_model"
        private const val ID_DEFAULT_STT = "default_stt_model"
        private const val ID_RAG_DEBUG = "rag_debug"
        private const val ID_RAG_RERANK = "rag_smart_rerank"
        private const val ID_RAG_MULTI_QUERY = "rag_multi_query"
        private const val ID_RAG_DEEP_RESEARCH = "rag_deep_research"
        private const val ID_RESEARCH_MAX_ITER = "research_max_iter"
        private const val ID_RESEARCH_MAX_Q = "research_max_q"
        private const val ID_RESEARCH_PER_SEARCH = "research_per_search"
        private const val ID_RESEARCH_CANCEL_BG = "research_cancel_bg"
        private const val ID_RESEARCH_ACTIVE_MODEL = "research_active_model"
        private val CLEARABLE_CHOICE_IDS = setOf(
            ID_DEFAULT_EMBEDDING,
            ID_DEFAULT_TTS,
            ID_DEFAULT_STT,
            ID_RESEARCH_ACTIVE_MODEL,
        )
    }
}
