package com.dark.tool_neuron.viewModel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dark.tool_neuron.data.AppSettingsDataStore
import com.dark.tool_neuron.data.MigrationProgress
import com.dark.tool_neuron.data.UmsMigrationEngine
import com.dark.tool_neuron.data.VaultManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class SecurityMode { REGULAR, PROTECTED }

sealed interface VaultGateState {
    data object SecuritySelection : VaultGateState
    data class Setup(val mode: SecurityMode) : VaultGateState
    data object Unlock : VaultGateState
    data object Deriving : VaultGateState
    data class Migrating(
        val phase: Int,
        val phaseName: String,
        val current: Int,
        val total: Int,
        val logs: List<String>
    ) : VaultGateState
    data class MigrationComplete(
        val migrated: Int,
        val skipped: Int,
        val failures: List<String>
    ) : VaultGateState
    data class Error(val message: String, val isMigration: Boolean = false) : VaultGateState
}

class VaultGateViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "VaultGateVM"
    }

    private val context get() = getApplication<Application>()
    private val settings by lazy { AppSettingsDataStore(context) }

    private val _state = MutableStateFlow<VaultGateState>(computeInitialState())
    val state: StateFlow<VaultGateState> = _state

    private var selectedMode: SecurityMode = SecurityMode.REGULAR

    val needsMigration: Boolean by lazy {
        val roomDb = context.getDatabasePath("llm_models_database").exists()
        val vault = File(context.filesDir, "memory_vault/vault.mvlt").exists()
        roomDb || vault
    }

    private fun computeInitialState(): VaultGateState {
        return if (VaultManager.exists(context)) {
            VaultGateState.Unlock
        } else {
            VaultGateState.SecuritySelection
        }
    }

    fun selectMode(mode: SecurityMode) {
        selectedMode = mode
        when (mode) {
            SecurityMode.REGULAR -> {
                // No passphrase needed -- go straight to init
                _state.value = VaultGateState.Setup(mode)
            }
            SecurityMode.PROTECTED -> {
                _state.value = VaultGateState.Setup(mode)
            }
        }
    }

    fun confirmRegularSetup(onComplete: () -> Unit) {
        _state.value = VaultGateState.Deriving
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    VaultManager.initPlaintext(context)
                } catch (e: Exception) {
                    Log.e(TAG, "Plaintext init failed", e)
                    false
                }
            }

            if (!ok) {
                _state.value = VaultGateState.Error("Failed to create vault.")
                return@launch
            }

            withContext(Dispatchers.IO) { settings.saveSecurityMode("REGULAR") }

            if (needsMigration) {
                runMigration(onComplete)
            } else {
                onComplete()
            }
        }
    }

    fun submitPassphrase(passphrase: String, onComplete: () -> Unit) {
        _state.value = VaultGateState.Deriving
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    VaultManager.initEncrypted(context, passphrase)
                } catch (e: Exception) {
                    Log.e(TAG, "Encrypted init failed", e)
                    false
                }
            }

            if (!ok) {
                _state.value = VaultGateState.Error("Wrong passphrase. Try again.")
                return@launch
            }

            withContext(Dispatchers.IO) { settings.saveSecurityMode("PROTECTED") }

            if (needsMigration) {
                runMigration(onComplete)
            } else {
                onComplete()
            }
        }
    }

    private fun runMigration(onComplete: () -> Unit) {
        val logs = mutableListOf<String>()
        var resultMigrated = 0
        var resultSkipped = 0

        fun addLog(msg: String) {
            logs.add(msg)
            if (logs.size > 100) logs.removeAt(0)
        }

        val progress = object : MigrationProgress {
            override fun onPhaseStart(phase: Int, phaseName: String, totalItems: Int) {
                addLog("Phase $phase: $phaseName ($totalItems items)")
                _state.value = VaultGateState.Migrating(phase, phaseName, 0, totalItems, logs.toList())
            }

            override fun onItemComplete(phase: Int, current: Int, total: Int) {
                _state.value = VaultGateState.Migrating(
                    phase,
                    (_state.value as? VaultGateState.Migrating)?.phaseName ?: "",
                    current, total, logs.toList()
                )
            }

            override fun onItemSkipped(phase: Int, itemId: String, reason: String) {
                addLog("WARN: Skipped $itemId - $reason")
            }

            override fun onPhaseComplete(phase: Int, migrated: Int, skipped: Int) {
                addLog("Phase $phase complete: $migrated migrated, $skipped skipped")
            }

            override fun onComplete(totalMigrated: Int, totalSkipped: Int) {
                resultMigrated = totalMigrated
                resultSkipped = totalSkipped
            }

            override fun onFatalError(phase: Int, error: String) {
                addLog("FATAL: $error")
                _state.value = VaultGateState.Error(error, isMigration = true)
            }
        }

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val engine = UmsMigrationEngine(context, progress)

                if (!engine.checkDiskSpace()) {
                    withContext(Dispatchers.Main) {
                        _state.value = VaultGateState.Error("Not enough disk space", isMigration = true)
                    }
                    return@withContext
                }

                engine.run()

                val failures = engine.getFailures()
                withContext(Dispatchers.Main) {
                    _state.value = VaultGateState.MigrationComplete(
                        migrated = resultMigrated,
                        skipped = resultSkipped,
                        failures = failures
                    )
                }
            }
        }
    }

    fun finishMigration(onComplete: () -> Unit) {
        Log.i(TAG, "Migration acknowledged, continuing")
        onComplete()
    }

    fun retryMigration(onComplete: () -> Unit) {
        runMigration(onComplete)
    }

    fun resetToInput() {
        _state.value = computeInitialState()
    }
}
