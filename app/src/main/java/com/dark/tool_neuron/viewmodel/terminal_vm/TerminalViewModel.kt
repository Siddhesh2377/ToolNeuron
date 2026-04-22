package com.dark.tool_neuron.viewmodel.terminal_vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.tool_neuron.terminal.BootstrapInstaller
import com.dark.tool_neuron.terminal.BootstrapState
import com.dark.tool_neuron.terminal.TerminalSessionManager
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TerminalViewModel @Inject constructor(
    val installer: BootstrapInstaller,
    private val manager: TerminalSessionManager,
) : ViewModel() {

    val bootstrapState: StateFlow<BootstrapState> = installer.state
    val sessions: StateFlow<List<TerminalSession>> = manager.sessions
    val activeSessionIndex: StateFlow<Int?> = manager.activeId

    private val _titlesTick = MutableStateFlow(0)
    val titlesTick: StateFlow<Int> = _titlesTick.asStateFlow()

    private val _bell = MutableStateFlow(0L)
    val bell: StateFlow<Long> = _bell.asStateFlow()

    var clipboardText: String = ""
        private set

    init {
        if (manager.client == null) {
            manager.client = object : TerminalSessionClient {
                override fun onTextChanged(changedSession: TerminalSession?) { _titlesTick.value = _titlesTick.value + 1 }
                override fun onTitleChanged(changedSession: TerminalSession?) { _titlesTick.value = _titlesTick.value + 1 }
                override fun onSessionFinished(finishedSession: TerminalSession?) { _titlesTick.value = _titlesTick.value + 1 }
                override fun onCopyTextToClipboard(session: TerminalSession?, text: String?) { clipboardText = text.orEmpty() }
                override fun onPasteTextFromClipboard(session: TerminalSession?) { }
                override fun onBell(session: TerminalSession?) { _bell.value = System.currentTimeMillis() }
                override fun onColorsChanged(session: TerminalSession?) { }
                override fun onTerminalCursorStateChange(state: Boolean) { }
                override fun getTerminalCursorStyle(): Int = 0
                override fun logError(tag: String?, message: String?) { }
                override fun logWarn(tag: String?, message: String?) { }
                override fun logInfo(tag: String?, message: String?) { }
                override fun logDebug(tag: String?, message: String?) { }
                override fun logVerbose(tag: String?, message: String?) { }
                override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) { }
                override fun logStackTrace(tag: String?, e: Exception?) { }
            }
        }
    }

    fun install() {
        viewModelScope.launch {
            val res = installer.install()
            if (res.isSuccess && sessions.value.isEmpty()) {
                manager.newSession("home")
            }
        }
    }

    fun ensureFirstSession() {
        if (sessions.value.isEmpty() && installer.isInstalled()) {
            manager.newSession("home")
        }
    }

    fun newSession() {
        if (installer.isInstalled()) manager.newSession()
    }

    fun selectSession(index: Int) = manager.activate(index)
    fun closeSession(session: TerminalSession) = manager.close(session)

    fun writeToActive(text: String) {
        val idx = activeSessionIndex.value ?: return
        val session = sessions.value.getOrNull(idx) ?: return
        session.write(text)
    }
}
