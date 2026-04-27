package com.dark.tool_neuron.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.tool_neuron.data.AppPreferences
import com.dark.tool_neuron.data.SecurityManager
import com.dark.tool_neuron.data.ThemeController
import com.dark.tool_neuron.data.VerifyResult
import com.dark.tool_neuron.model.ModelInfo
import com.dark.tool_neuron.model.enums.ProviderType
import com.dark.tool_neuron.repo.DocumentRepository
import com.dark.tool_neuron.repo.ModelRepository
import com.dark.tool_neuron.repo.RagManager
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.voice.VoiceModelManager
import com.dark.tool_neuron.ui.screens.settings.model.SettingsChoiceOption
import com.dark.tool_neuron.ui.screens.settings.model.SettingsDialog
import com.dark.tool_neuron.ui.screens.settings.model.SettingsItem
import com.dark.tool_neuron.ui.screens.settings.model.SettingsSection
import com.dark.tool_neuron.ui.screens.settings.model.SettingsState
import com.dark.tool_neuron.ui.theme.ColorPalette
import com.dark.download_manager.formatBytes
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val themeController: ThemeController,
    private val security: SecurityManager,
    private val ragManager: RagManager,
    private val modelRepo: ModelRepository,
    private val documentRepo: DocumentRepository,
    private val prefs: AppPreferences,
    private val voiceManager: VoiceModelManager,
) : ViewModel() {

    private val _dialog = MutableStateFlow<SettingsDialog?>(null)
    private val _snackbar = MutableStateFlow<String?>(null)
    private val _diskUsage = MutableStateFlow("")
    private val _lockEnabled = MutableStateFlow(security.isLockEnabled)
    private val _panicPinSet = MutableStateFlow(security.hasPanicPin)
    private val _activeTts = MutableStateFlow(prefs.activeTtsModelId)
    private val _activeStt = MutableStateFlow(prefs.activeSttModelId)
    private val appVersion: String = resolveVersion()

    val state: StateFlow<SettingsState> = combine(
        modelRepo.models,
        ragManager.defaultEmbeddingModelId,
        themeController.mode,
        themeController.palette,
        _diskUsage,
        _lockEnabled,
        _dialog,
        _snackbar,
        _activeTts,
        _activeStt,
        _panicPinSet,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val models = values[0] as List<ModelInfo>
        val defaultEmbedding = values[1] as String?
        val themeMode = values[2] as ThemeController.Mode
        val palette = values[3] as ColorPalette
        val disk = values[4] as String
        val lockOn = values[5] as Boolean
        val dialog = values[6] as SettingsDialog?
        val snackbar = values[7] as String?
        val activeTts = values[8] as String
        val activeStt = values[9] as String
        val panicSet = values[10] as Boolean

        SettingsState(
            sections = buildSections(
                models = models,
                defaultEmbedding = defaultEmbedding,
                themeMode = themeMode,
                palette = palette,
                diskUsage = disk,
                lockEnabled = lockOn,
                activeTts = activeTts,
                activeStt = activeStt,
                panicPinSet = panicSet,
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

    init { refreshDiskUsage() }

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
        themeMode: ThemeController.Mode,
        palette: ColorPalette,
        diskUsage: String,
        lockEnabled: Boolean,
        activeTts: String,
        activeStt: String,
        panicPinSet: Boolean,
    ): List<SettingsSection> = listOf(
        chatAndRagSection(models, defaultEmbedding),
        voiceSection(models, activeTts, activeStt),
        appearanceSection(themeMode, palette),
        privacySection(lockEnabled, panicPinSet),
        storageSection(diskUsage),
        aboutSection(),
    )

    private fun chatAndRagSection(
        models: List<ModelInfo>,
        defaultEmbedding: String?,
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

    private fun appearanceSection(
        themeMode: ThemeController.Mode,
        palette: ColorPalette,
    ): SettingsSection {
        val modeOptions = listOf(
            SettingsChoiceOption(ThemeController.Mode.SYSTEM.name, "Follow system"),
            SettingsChoiceOption(ThemeController.Mode.LIGHT.name, "Light"),
            SettingsChoiceOption(ThemeController.Mode.DARK.name, "Dark"),
        )
        val paletteOptions = ColorPalette.entries.map {
            SettingsChoiceOption(it.name, it.displayName)
        }
        return SettingsSection(
            id = "appearance",
            title = "Appearance",
            description = "Theme and accent colors.",
            icon = TnIcons.Sparkles,
            items = listOf(
                SettingsItem.Choice(
                    id = "theme_mode",
                    title = "Theme",
                    subtitle = "Pick light, dark, or follow system.",
                    icon = TnIcons.Sparkles,
                    selectedKey = themeMode.name,
                    options = modeOptions,
                    onSelect = { key ->
                        if (key != null) themeController.setMode(ThemeController.Mode.valueOf(key))
                        _dialog.value = null
                    },
                ),
                SettingsItem.Choice(
                    id = "color_palette",
                    title = "Color palette",
                    subtitle = "Dynamic pulls from your wallpaper; presets use hand-tuned colors.",
                    icon = TnIcons.Star,
                    selectedKey = palette.name,
                    options = paletteOptions,
                    onSelect = { key ->
                        if (key != null) themeController.setPalette(ColorPalette.valueOf(key))
                        _dialog.value = null
                    },
                ),
            ),
        )
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

    private fun storageSection(diskUsage: String): SettingsSection = SettingsSection(
        id = "storage",
        title = "Storage",
        description = "Indexed documents and cache live in app-private storage.",
        icon = TnIcons.Database,
        items = listOf(
            SettingsItem.Info(
                id = "disk_usage",
                title = "Models on device",
                subtitle = "Total size of downloaded GGUF/ONNX weights.",
                icon = TnIcons.Package,
                value = diskUsage.ifBlank { "Calculating…" },
            ),
            SettingsItem.Action(
                id = "clear_rag_index",
                title = "Clear document index",
                subtitle = "Removes all chat-attached documents and their embeddings.",
                icon = TnIcons.Trash,
                destructive = true,
                onClick = {
                    _dialog.value = SettingsDialog.Confirm(
                        title = "Clear document index?",
                        message = "All documents you attached to chats will be removed. Chats stay; only the RAG index is wiped.",
                        confirmLabel = "Clear",
                        destructive = true,
                        onConfirm = {
                            documentRepo.clearAll()
                            viewModelScope.launch { ragManager.release() }
                            _dialog.value = null
                            _snackbar.value = "Document index cleared"
                        },
                    )
                },
            ),
            SettingsItem.Action(
                id = "clear_cache",
                title = "Clear image & network cache",
                subtitle = "Frees temporary files cached by the app.",
                icon = TnIcons.Refresh,
                onClick = {
                    _dialog.value = SettingsDialog.Confirm(
                        title = "Clear cache?",
                        message = "Cached thumbnails and response bodies will be deleted. Your chats and models are untouched.",
                        confirmLabel = "Clear",
                        destructive = false,
                        onConfirm = {
                            viewModelScope.launch {
                                withContext(Dispatchers.IO) {
                                    context.cacheDir.deleteRecursively()
                                    context.cacheDir.mkdirs()
                                }
                                refreshDiskUsage()
                                _dialog.value = null
                                _snackbar.value = "Cache cleared"
                            }
                        },
                    )
                },
            ),
        ),
    )

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
        ),
    )

    private fun refreshDiskUsage() {
        viewModelScope.launch {
            val size = withContext(Dispatchers.IO) {
                dirSize(File(context.filesDir, "models"))
            }
            _diskUsage.value = formatBytes(size)
        }
    }

    private fun dirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        var total = 0L
        val stack = ArrayDeque<File>()
        stack.addLast(dir)
        while (stack.isNotEmpty()) {
            val f = stack.removeLast()
            if (f.isDirectory) {
                f.listFiles()?.forEach { stack.addLast(it) }
            } else {
                total += f.length()
            }
        }
        return total
    }

    private fun resolveVersion(): String = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.versionName ?: "1.0"
    }.getOrDefault("1.0")

    companion object {
        private const val ID_DEFAULT_EMBEDDING = "default_embedding_model"
        private const val ID_DEFAULT_TTS = "default_tts_model"
        private const val ID_DEFAULT_STT = "default_stt_model"
        private val CLEARABLE_CHOICE_IDS = setOf(
            ID_DEFAULT_EMBEDDING,
            ID_DEFAULT_TTS,
            ID_DEFAULT_STT,
        )
    }
}
