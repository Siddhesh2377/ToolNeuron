package com.dark.ai_module.workers

import android.content.Context
import com.dark.ai_module.ai.Neuron
import com.dark.ai_module.db.DatabaseProvider
import com.dark.ai_module.db.ModelDAO
import com.dark.ai_module.model.ModelsData
import com.dark.ai_module.model.getDefaultModelData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ModelManager — single source of truth for installed models and the active selection.
 *
 * Improvements over the original:
 *  • Thread‑safe DAO init with lazy guard
 *  • Never reassign StateFlows (avoid losing collectors) — we update values
 *  • Clear load state (Idle/Loading/Ready/Error) to coordinate UI
 *  • Explicit defaults via [ManagerDefaults] and structured params via [ParamsBundle]
 *  • IO confinement for DB ops via withContext(Dispatchers.IO)
 *  • Force‑reload flag and existence checks when loading a model
 */
object ModelManager {

    // -------------------------------------------------------------
    //  Types & defaults
    // -------------------------------------------------------------

    data class ManagerDefaults(
        val systemPrompt: String = "You are a helpful assistant.",
        val contextLength: Int = 8_024, // kept from your original call
    )

    /** Structured holder instead of Pair for clarity + future growth. */
    data class ParamsBundle(
        val professional: ModelParams.Professional = ModelParams.Professional(),
        val emotional: ModelParams.Emotional = ModelParams.Emotional(),
    )

    sealed class LoadState {
        object Idle : LoadState()
        data class Loading(val path: String) : LoadState()
        data class Ready(val model: ModelsData) : LoadState()
        data class Error(val message: String) : LoadState()
    }

    // -------------------------------------------------------------
    //  State
    // -------------------------------------------------------------

    private const val TAG = "ModelManager"
    private val io = Dispatchers.IO
    private val initGuard = AtomicBoolean(false)

    private lateinit var dao: ModelDAO

    private val _currentModel = MutableStateFlow(getDefaultModelData())
    val currentModel: StateFlow<ModelsData> = _currentModel.asStateFlow()

    private val _params = MutableStateFlow(ParamsBundle())
    val params: StateFlow<ParamsBundle> = _params.asStateFlow()

    private val _loadState = MutableStateFlow<LoadState>(LoadState.Idle)
    val loadState: StateFlow<LoadState> = _loadState.asStateFlow()

    // -------------------------------------------------------------
    //  Lifecycle
    // -------------------------------------------------------------

    fun init(context: Context) {
        if (initGuard.compareAndSet(false, true)) {
            dao = DatabaseProvider.getDatabase(context).ModelDAO()
        }
    }

    private fun ensureDaoInitialized() {
        check(initGuard.get()) { "ModelManager.init(context) must be called before use." }
    }

    // -------------------------------------------------------------
    //  Model loading
    // -------------------------------------------------------------

    /** Fire-and-forget load that updates [loadState] and [currentModel]. */
    fun loadModel(
        modelData: ModelsData,
        defaults: ManagerDefaults = ManagerDefaults(),
        forceReload: Boolean = false,
        keepInMemory: Boolean = false,
        onLoaded: (() -> Unit)? = null,
    ) {
        val modelFile = File(modelData.modelPath)
        if (!modelFile.exists()) {
            _loadState.value = LoadState.Error("Model file not found: ${modelFile.path}")
            return
        }

        _loadState.value = LoadState.Loading(modelFile.path)

        Neuron.loadModel(
            path = modelFile,
            init = Neuron.ModelInitParams(
                ctxSize = defaults.contextLength,
                systemPrompt = defaults.systemPrompt,
                chatTemplate = modelData.chatTemplate,
                useMLOCK = keepInMemory
            ),
            forceReload = forceReload,
        ) {
            _currentModel.value = modelData
            _loadState.value = LoadState.Ready(modelData)
            onLoaded?.let { it() }
        }
    }

    /** Suspending variant that awaits the onLoaded callback. */
    suspend fun loadModelAwait(
        modelData: ModelsData,
        defaults: ManagerDefaults = ManagerDefaults(),
        forceReload: Boolean = false,
        keepInMemory: Boolean = false,
    ): Result<Unit> = withContext(io) {
        try {
            val f = File(modelData.modelPath)
            if (!f.exists()) return@withContext Result.failure(IllegalArgumentException("Missing model: ${f.path}"))
            val latch = kotlinx.coroutines.CompletableDeferred<Unit>()
            loadModel(modelData, defaults, forceReload, keepInMemory) { latch.complete(Unit) }
            latch.await()
            Result.success(Unit)
        } catch (t: Throwable) {
            _loadState.value = LoadState.Error(t.message ?: "Load failed")
            Result.failure(t)
        }
    }

    // -------------------------------------------------------------
    //  Params
    // -------------------------------------------------------------

    fun updateModelParams(
        professional: ModelParams.Professional = ModelParams.Professional(),
        emotional: ModelParams.Emotional = ModelParams.Emotional(),
    ) {
        _params.value = ParamsBundle(professional, emotional)
    }

    // Back-compat helpers for existing callsites
    fun getModel(): StateFlow<ModelsData> = currentModel
    fun getModelParams(): StateFlow<ParamsBundle> = params
    fun setCurrentModel(model: ModelsData) { _currentModel.value = model }

    // -------------------------------------------------------------
    //  DAO section — all on IO dispatcher
    // -------------------------------------------------------------

    fun observeModels(): Flow<List<ModelsData>> {
        ensureDaoInitialized()
        return dao.getAllModels()
    }

    suspend fun addModel(model: ModelsData) = withContext(io) {
        ensureDaoInitialized()
        dao.insertModel(model)
    }

    suspend fun removeModel(modelName: String) = withContext(io) {
        ensureDaoInitialized()
        dao.getModelByName(modelName)?.let { dao.deleteModel(it) }
    }

    suspend fun checkIfInstalled(modelName: String): Boolean = withContext(io) {
        ensureDaoInitialized()
        dao.getModelByName(modelName) != null
    }

    suspend fun getFirstModel(): ModelsData? = withContext(io) {
        ensureDaoInitialized()
        dao.getAllModels().firstOrNull()?.firstOrNull()
    }

    suspend fun getModel(modelName: String): ModelsData? = withContext(io) {
        ensureDaoInitialized()
        dao.getModelByName(modelName)
    }

    suspend fun isAnyModelInstalled(): Boolean = withContext(io) {
        ensureDaoInitialized()
        dao.getAllModels().firstOrNull()?.isNotEmpty() == true
    }
}

object ModelParams {
    data class Professional(val value: Float = 3.5f)
    data class Emotional(val value: Float = 7.6f)
}
