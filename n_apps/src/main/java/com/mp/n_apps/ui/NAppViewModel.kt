package com.mp.n_apps.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mp.n_apps.agent.AgentProgress
import com.mp.n_apps.agent.NAppToolAgent
import com.mp.n_apps.agent.ToolLogEntry
import com.mp.n_apps.data.AgentConfig
import com.mp.n_apps.data.NAppDataStore
import com.mp.n_apps.runtime.NAppRuntime
import com.mp.n_apps.schema.NApp
import com.mp.n_apps.schema.NAppParser
import com.mp.n_apps.schema.NAppValidationException
import com.mp.n_apps.vcs.NAppVersionControl
import com.mp.n_apps.vcs.VersionEntry
import com.mp.n_apps.workspace.NAppWorkspace
import com.mp.n_apps.workspace.ProjectFiles
import com.mp.n_apps.workspace.ProjectMetadata
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "NAppVM"

data class AgentChatMessage(
    val role: String,
    val content: String,
    val isJsonUpdate: Boolean = false
)

class NAppViewModel : ViewModel() {

    val runtime = NAppRuntime()

    // ── Parsed NApp ──
    private val _napp = MutableStateFlow<NApp?>(null)
    val napp: StateFlow<NApp?> = _napp.asStateFlow()

    // ── Parse errors ──
    private val _errors = MutableStateFlow<List<String>>(emptyList())
    val errors: StateFlow<List<String>> = _errors.asStateFlow()

    // ── Raw JSON for 3-tab editor ──
    private val _stateJson = MutableStateFlow("")
    val stateJson: StateFlow<String> = _stateJson.asStateFlow()

    private val _uiJson = MutableStateFlow("")
    val uiJson: StateFlow<String> = _uiJson.asStateFlow()

    private val _actionsJson = MutableStateFlow("")
    val actionsJson: StateFlow<String> = _actionsJson.asStateFlow()

    // ── Agent state ──
    private val _agentMessages = MutableStateFlow<List<AgentChatMessage>>(emptyList())
    val agentMessages: StateFlow<List<AgentChatMessage>> = _agentMessages.asStateFlow()

    private val _isAgentWorking = MutableStateFlow(false)
    val isAgentWorking: StateFlow<Boolean> = _isAgentWorking.asStateFlow()

    private val _activeAgent = MutableStateFlow<AgentConfig?>(null)
    val activeAgent: StateFlow<AgentConfig?> = _activeAgent.asStateFlow()

    private val _agentConfigs = MutableStateFlow<List<AgentConfig>>(emptyList())
    val agentConfigs: StateFlow<List<AgentConfig>> = _agentConfigs.asStateFlow()

    private val _maxRounds = MutableStateFlow(NAppDataStore.DEFAULT_MAX_ROUNDS)
    val maxRounds: StateFlow<Int> = _maxRounds.asStateFlow()

    // ── Toast ──
    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    // ══════════════════════════════════════
    //  Workspace state
    // ══════════════════════════════════════
    private val _projects = MutableStateFlow<List<ProjectMetadata>>(emptyList())
    val projects: StateFlow<List<ProjectMetadata>> = _projects.asStateFlow()

    private val _currentProjectId = MutableStateFlow<String?>(null)
    val currentProjectId: StateFlow<String?> = _currentProjectId.asStateFlow()

    private val _hasUnsavedChanges = MutableStateFlow(false)
    val hasUnsavedChanges: StateFlow<Boolean> = _hasUnsavedChanges.asStateFlow()

    // ══════════════════════════════════════
    //  VCS state
    // ══════════════════════════════════════
    private val _versionHistory = MutableStateFlow<List<VersionEntry>>(emptyList())
    val versionHistory: StateFlow<List<VersionEntry>> = _versionHistory.asStateFlow()

    // ══════════════════════════════════════
    //  Tool agent state
    // ══════════════════════════════════════
    private val _toolLog = MutableStateFlow<List<ToolLogEntry>>(emptyList())
    val toolLog: StateFlow<List<ToolLogEntry>> = _toolLog.asStateFlow()

    private val _agentProgress = MutableStateFlow<AgentProgress?>(null)
    val agentProgress: StateFlow<AgentProgress?> = _agentProgress.asStateFlow()

    // ── Internals ──
    private var dataStore: NAppDataStore? = null
    private var workspace: NAppWorkspace? = null
    private var vcs: NAppVersionControl? = null
    private var toolAgent: NAppToolAgent? = null

    // Snapshot for unsaved-changes tracking
    private var savedSnapshot: ProjectFiles? = null

    init {
        runtime.onToast = { message, _ ->
            _toastMessage.value = message
        }
    }

    fun consumeToast() {
        _toastMessage.value = null
    }

    // ════════════════════════════════════════
    //  Workspace Init
    // ════════════════════════════════════════

    fun initWorkspace(context: Context) {
        if (workspace != null) return
        workspace = NAppWorkspace(context)
        refreshProjectList()
    }

    // ════════════════════════════════════════
    //  Agent Config
    // ════════════════════════════════════════

    fun initConfig(context: Context) {
        if (dataStore != null) return
        dataStore = NAppDataStore(context)
        viewModelScope.launch { dataStore!!.migrateIfNeeded() }
        viewModelScope.launch { dataStore!!.activeAgent.collect { _activeAgent.value = it } }
        viewModelScope.launch { dataStore!!.agents.collect { _agentConfigs.value = it } }
        viewModelScope.launch { dataStore!!.maxRounds.collect { _maxRounds.value = it } }
    }

    fun addAgent(agent: AgentConfig) {
        viewModelScope.launch { dataStore?.addAgent(agent) }
    }

    fun updateAgentConfig(agent: AgentConfig) {
        viewModelScope.launch { dataStore?.updateAgent(agent) }
    }

    fun deleteAgentConfig(agentId: String) {
        viewModelScope.launch { dataStore?.deleteAgent(agentId) }
    }

    fun setActiveAgent(agentId: String) {
        viewModelScope.launch { dataStore?.setActiveAgent(agentId) }
    }

    fun setMaxRounds(rounds: Int) {
        viewModelScope.launch { dataStore?.setMaxRounds(rounds) }
    }

    // ════════════════════════════════════════
    //  Project CRUD
    // ════════════════════════════════════════

    fun createProject(name: String) {
        val ws = workspace ?: return
        val metadata = ws.createProject(name)
        refreshProjectList()
        openProject(metadata.id)
    }

    fun openProject(projectId: String) {
        val ws = workspace ?: return
        val files = ws.openProject(projectId) ?: return

        _currentProjectId.value = projectId
        savedSnapshot = files

        // Setup VCS for this project
        vcs = NAppVersionControl(ws.getProjectDir(projectId), projectId)
        refreshVersionHistory()

        // Setup tool agent
        rebuildToolAgent()

        // Load files into editor
        loadFromJsonFiles(files.stateJson, files.uiJson, files.actionsJson)
        _hasUnsavedChanges.value = false
    }

    fun saveProject() {
        val ws = workspace ?: return
        val projectId = _currentProjectId.value ?: return
        val files = currentProjectFiles()
        ws.saveProject(projectId, files)
        savedSnapshot = files
        _hasUnsavedChanges.value = false
        refreshProjectList()
    }

    fun deleteProject(projectId: String) {
        val ws = workspace ?: return
        ws.deleteProject(projectId)
        if (_currentProjectId.value == projectId) {
            _currentProjectId.value = null
            vcs = null
            toolAgent = null
            _versionHistory.value = emptyList()
            _toolLog.value = emptyList()
            clearEditor()
        }
        refreshProjectList()
    }

    // ════════════════════════════════════════
    //  VCS
    // ════════════════════════════════════════

    fun commitVersion(message: String) {
        val v = vcs ?: return
        v.commit(currentProjectFiles(), message)
        refreshVersionHistory()
        // Also save to disk
        saveProject()
    }

    fun revertToVersion(versionNumber: Int) {
        val v = vcs ?: return
        val files = v.revert(versionNumber) ?: return
        loadFromJsonFiles(files.stateJson, files.uiJson, files.actionsJson)
        _hasUnsavedChanges.value = true
    }

    // ════════════════════════════════════════
    //  Load / Parse
    // ════════════════════════════════════════

    fun loadFromJsonFiles(stateJson: String?, uiJson: String, actionsJson: String?) {
        _stateJson.value = stateJson ?: ""
        _uiJson.value = uiJson
        _actionsJson.value = actionsJson ?: ""
        _errors.value = emptyList()

        checkUnsavedChanges()

        val result = NAppParser.parse(
            stateJson = stateJson?.ifBlank { null },
            uiJson = uiJson,
            actionsJson = actionsJson?.ifBlank { null }
        )

        result.fold(
            onSuccess = { parsed ->
                _napp.value = parsed
                runtime.load(parsed)
                Log.d(TAG, "Loaded NApp: ${parsed.manifest.app.name}, ${parsed.ui.components.size} components")
            },
            onFailure = { error ->
                if (error is NAppValidationException) {
                    _errors.value = error.errors
                } else {
                    _errors.value = listOf(error.message ?: "Unknown parse error")
                }
                Log.e(TAG, "Parse error: ${error.message}")
            }
        )
    }

    fun updateStateJson(json: String) {
        _stateJson.value = json
        checkUnsavedChanges()
    }

    fun updateUiJson(json: String) {
        _uiJson.value = json
        checkUnsavedChanges()
    }

    fun updateActionsJson(json: String) {
        _actionsJson.value = json
        checkUnsavedChanges()
    }

    fun reparse() {
        loadFromJsonFiles(
            stateJson = _stateJson.value.ifBlank { null },
            uiJson = _uiJson.value,
            actionsJson = _actionsJson.value.ifBlank { null }
        )
    }

    // ════════════════════════════════════════
    //  Runtime callbacks
    // ════════════════════════════════════════

    fun onStateChange(key: String, value: Any?) {
        runtime.onStateChange(key, value)
    }

    fun onAction(actionId: String) {
        runtime.onAction(actionId)
    }

    // ════════════════════════════════════════
    //  Agent (Tool-based)
    // ════════════════════════════════════════

    fun sendAgentCommand(text: String) {
        val active = _activeAgent.value
        if (text.isBlank() || active == null || active.apiKey.isBlank()) return

        val currentMessages = _agentMessages.value.toMutableList()
        currentMessages.add(AgentChatMessage(role = "user", content = text))
        _agentMessages.value = currentMessages

        viewModelScope.launch {
            _isAgentWorking.value = true
            _toolLog.value = emptyList()
            _agentProgress.value = null

            val agent = toolAgent
            if (agent != null) {
                val result = agent.runAgentLoop(
                    apiKey = active.apiKey,
                    baseUrl = active.providerUrl,
                    model = active.modelName,
                    userMessage = text,
                    maxIterations = _maxRounds.value
                )

                _toolLog.value = result.toolLog

                if (result.toolLog.isNotEmpty()) {
                    reparse()
                }

                val msgs = _agentMessages.value.toMutableList()
                msgs.add(
                    AgentChatMessage(
                        role = "assistant",
                        content = result.finalText,
                        isJsonUpdate = result.toolLog.isNotEmpty()
                    )
                )
                _agentMessages.value = msgs
            } else {
                val msgs = _agentMessages.value.toMutableList()
                msgs.add(
                    AgentChatMessage(
                        role = "assistant",
                        content = "No project open. Create or open a project first."
                    )
                )
                _agentMessages.value = msgs
            }

            _isAgentWorking.value = false
            _agentProgress.value = null
        }
    }

    fun clearAgentHistory() {
        toolAgent?.clearHistory()
        _agentMessages.value = emptyList()
        _toolLog.value = emptyList()
    }

    // ════════════════════════════════════════
    //  Counter example for quick-start
    // ════════════════════════════════════════

    fun loadCounterExample() {
        loadFromJsonFiles(
            stateJson = """
{
  "schema": {
    "count": { "type": "number", "default": 0 }
  }
}
            """.trimIndent(),
            uiJson = """
{
  "components": [
    { "id": "title", "type": "text", "content": "Counter", "style": "h1" },
    { "id": "display", "type": "text", "content": "Count: {{count}}", "style": "h2" },
    { "id": "btn_row", "type": "row", "children": ["dec_btn", "inc_btn", "reset_btn"], "spacing": 8 },
    { "id": "dec_btn", "type": "button", "text": "−", "actionId": "decrement", "style": "outlined" },
    { "id": "inc_btn", "type": "button", "text": "+", "actionId": "increment", "style": "primary" },
    { "id": "reset_btn", "type": "button", "text": "Reset", "actionId": "reset", "style": "text", "visible": "{{count != 0}}" }
  ]
}
            """.trimIndent(),
            actionsJson = """
{
  "actions": {
    "decrement": { "type": "decrement", "target": "count" },
    "increment": { "type": "increment", "target": "count" },
    "reset": { "type": "set_state", "target": "count", "value": 0 }
  }
}
            """.trimIndent()
        )
    }

    // ════════════════════════════════════════
    //  Internal helpers
    // ════════════════════════════════════════

    private fun currentProjectFiles(): ProjectFiles {
        return ProjectFiles(
            manifestJson = "{}",
            stateJson = _stateJson.value.ifBlank { "{\"schema\":{}}" },
            uiJson = _uiJson.value.ifBlank { "{\"components\":[]}" },
            actionsJson = _actionsJson.value.ifBlank { "{\"actions\":{}}" }
        )
    }

    private fun refreshProjectList() {
        _projects.value = workspace?.listProjects() ?: emptyList()
    }

    private fun refreshVersionHistory() {
        _versionHistory.value = vcs?.listHistory() ?: emptyList()
    }

    private fun rebuildToolAgent() {
        toolAgent = NAppToolAgent(
            getCurrentFiles = { currentProjectFiles() },
            updateFiles = { files ->
                _stateJson.value = files.stateJson
                _uiJson.value = files.uiJson
                _actionsJson.value = files.actionsJson
                checkUnsavedChanges()
            },
            vcs = vcs,
            onToolExecuted = { entry ->
                _toolLog.value = _toolLog.value + entry
            },
            onProgressUpdate = { progress ->
                _agentProgress.value = progress
            }
        )
    }

    private fun checkUnsavedChanges() {
        val snapshot = savedSnapshot ?: return
        _hasUnsavedChanges.value =
            snapshot.stateJson != _stateJson.value ||
            snapshot.uiJson != _uiJson.value ||
            snapshot.actionsJson != _actionsJson.value
    }

    private fun clearEditor() {
        _stateJson.value = ""
        _uiJson.value = ""
        _actionsJson.value = ""
        _napp.value = null
        _errors.value = emptyList()
        _agentMessages.value = emptyList()
        _hasUnsavedChanges.value = false
        savedSnapshot = null
    }
}
