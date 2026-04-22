package com.dark.tool_neuron.terminal

import android.content.Context
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TerminalSessionManager @Inject constructor(
    @ApplicationContext private val appContext: Context,
    val installer: BootstrapInstaller,
) {

    private val _sessions = MutableStateFlow<List<TerminalSession>>(emptyList())
    val sessions: StateFlow<List<TerminalSession>> = _sessions.asStateFlow()

    private val _activeId = MutableStateFlow<Int?>(null)
    val activeId: StateFlow<Int?> = _activeId.asStateFlow()

    @Volatile
    var client: TerminalSessionClient? = null

    fun newSession(title: String? = null): TerminalSession? {
        val clientRef = client ?: return null
        val env = installer.shellEnvironment().map { (k, v) -> "$k=$v" }.toTypedArray()
        val shell = installer.shellPath()
        val cwd = installer.homeDir.absolutePath.also { File(it).mkdirs() }
        val args = if (shell.endsWith("/bash")) arrayOf("-l") else emptyArray()

        val session = TerminalSession(
            shell,
            cwd,
            args,
            env,
            4000,
            clientRef,
        )
        if (title != null) session.mSessionName = title
        _sessions.update { it + session }
        _activeId.value = _sessions.value.lastIndex
        updateService()
        return session
    }

    fun activate(index: Int) {
        if (index in _sessions.value.indices) _activeId.value = index
    }

    fun close(session: TerminalSession) {
        session.finishIfRunning()
        _sessions.update { list ->
            val remaining = list.filter { it !== session }
            _activeId.value = remaining.indices.lastOrNull()
            remaining
        }
        updateService()
    }

    fun closeAll() {
        _sessions.value.forEach { it.finishIfRunning() }
        _sessions.value = emptyList()
        _activeId.value = null
        updateService()
    }

    private fun updateService() {
        val count = _sessions.value.size
        if (count > 0) TerminalService.start(appContext) else TerminalService.stop(appContext)
    }
}
