package com.dark.ai_module.workers

import android.content.Context
import android.os.Process
import android.util.Log
import com.dark.ai_module.db.DatabaseProvider
import com.dark.ai_module.db.ModelDAO
import com.dark.ai_module.model.ModelsData
import com.dark.ai_module.model.getDefaultModelData
import com.dark.ai_module.workers.ModelManager.replies
import com.mp.ai_core.NativeLib
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds

/**
 * ModelManager — unified manager for:
 *  • Model catalog (DB via DAO)
 *  • Active model lifecycle (load/switch/unload)
 *  • Text generation queue (blocking + streaming)
 *  • UI‑friendly state flows
 *
 * It merges the original ModelManager and Neuron into a single, thread‑safe object.
 * Public API is grouped clearly; internals are kept private.
 */
object ModelManager {

    /* ------------------------------------------------------------- *//*  Public types                                                  *//* ------------------------------------------------------------- */

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
        data class Loading(val progress: Float) : LoadState()
        data class OnLoaded(val model: ModelsData) : LoadState()
        data class Error(val message: String) : LoadState()
    }

    /** Defaults for native model init. */
    data class ModelInitParams(
        val threads: Int = (Runtime.getRuntime().availableProcessors().coerceAtLeast(2)) / 2,
        val gpuLayers: Int = 0,
        val useMMAP: Boolean = true,
        val useMLOCK: Boolean = false,
        val ctxSize: Int = 2048,
        val temp: Float = 0.7f,
        val topK: Int = 20,
        val topP: Float = 0.9f,
        val minP: Float = 0.0f,
        val systemPrompt: String = "You are a helpful assistant.",
        val chatTemplate: String? = null,
    )

    /** Per‑request decoding params. */
    data class GenerationParams(
        val maxTokens: Int = 512,
    )

    /* ------------------------------------------------------------- *//*  Public state (Flows)                                          *//* ------------------------------------------------------------- */

    private const val TAG = "ModelManager"

    private val _currentModel = MutableStateFlow(getDefaultModelData())
    val currentModel: StateFlow<ModelsData> = _currentModel.asStateFlow()

    private val _params = MutableStateFlow(ParamsBundle())
    val params: StateFlow<ParamsBundle> = _params.asStateFlow()

    /** Emits true while *any* generation is running. */
    val isGenerating = MutableStateFlow(false)

    /** Broadcasts every completed assistant reply (for UI to collect). */
    val replies = MutableSharedFlow<String>(extraBufferCapacity = 16)

    /* ------------------------------------------------------------- *//*  Public lifecycle API (DB + init)                              *//* ------------------------------------------------------------- */

    private val initGuard = AtomicBoolean(false)
    private lateinit var dao: ModelDAO

    /** Call once, e.g., in Application.onCreate(). */
    fun init(context: Context) {
        if (initGuard.compareAndSet(false, true)) {
            dao = DatabaseProvider.getDatabase(context).ModelDAO()
        }
    }

    /** Suspending variant that reports progress via [onLoaded]. */
    suspend fun loadModelAwait(
        modelData: ModelsData,
        defaults: ManagerDefaults = ManagerDefaults(),
        chatTemplate: String = modelData.chatTemplate,
        forceReload: Boolean = false,
        keepInMemory: Boolean = false,
        onLoaded: ((LoadState) -> Unit),
    ): Result<Unit> = withContext(ioDispatcher) {
        onLoaded(LoadState.Idle)
        onLoaded(LoadState.Loading(0f))

        try {
            val f = File(modelData.modelPath)
            if (!f.exists()) {
                val err = IllegalArgumentException("Missing model: ${f.path}")
                onLoaded(LoadState.Error(err.message ?: "Load failed"))
                return@withContext Result.failure(err)
            }

            val parentJob: Job? = coroutineContext.job
            val done = CompletableDeferred<Unit>(parentJob)

            var progress = 0f
            val capBeforeComplete = 0.92f

            val progressJob = launch {
                while (isActive && !done.isCompleted) {
                    val step = ((capBeforeComplete - progress) * 0.12f).coerceAtLeast(0.0025f)
                    progress = (progress + step).coerceAtMost(capBeforeComplete)
                    onLoaded(LoadState.Loading(progress))
                    delay(50.milliseconds)
                }
            }

            val init = ModelInitParams(
                ctxSize = defaults.contextLength,
                systemPrompt = defaults.systemPrompt,
                chatTemplate = chatTemplate,
                useMLOCK = keepInMemory,
            )

            internalLoadModel(f, init, forceReload) {
                done.complete(Unit)
                _currentModel.value = modelData
            }

            done.await()

            var p = progress
            repeat(12) {
                p += ((1f - p) * 0.45f)
                val capped = p.coerceAtMost(0.999f)
                onLoaded(LoadState.Loading(capped))
                delay(16.milliseconds)
            }
            onLoaded(LoadState.Loading(1f))

            onLoaded(LoadState.OnLoaded(modelData))

            progressJob.cancelAndJoin()

            Result.success(Unit)
        } catch (t: Throwable) {
            onLoaded(LoadState.Error(t.message ?: "Load failed"))
            Result.failure(t)
        }
    }

    /** Unload the currently active model (native release). */
    fun unLoadModel() = unloadActiveModel()

    /** Queue prompt (fire‑and‑forget); collect [replies] for completed text. */
    suspend fun enqueuePrompt(prompt: String, gen: GenerationParams = GenerationParams()) {
        val def = CompletableDeferred<String>()
        queue.send(Request.Blocking(prompt, gen, def))
    }

    /** Queue prompt and await the full reply. */
    suspend fun generateAndWait(
        prompt: String, gen: GenerationParams = GenerationParams()
    ): String {
        val def = CompletableDeferred<String>()
        queue.send(Request.Blocking(prompt, gen, def))
        return def.await()
    }

    /** Queue a streaming generation; invoke [onToken] per token and return final reply. */
    suspend fun generateStreaming(
        prompt: String,
        gen: GenerationParams = GenerationParams(),
        toolJson: String?,
        onToolCalled: (String, String) -> Unit = { _, _ ->},
        onToken: (String) -> Unit = {},
    ): String {
        val def = CompletableDeferred<String>()
        queue.send(Request.Streaming(prompt, gen, onToken, toolJson, onToolCalled, def))
        return def.await()
    }

    /** Stop the current generation if any (native + coroutine side). */
    fun stopGeneration() {
        currentGenJob?.cancel()
        activeModelVariant()?.lib?.nativeStopGeneration()
        isGenerating.value = false
    }

    /** List absolute paths for all currently loaded models. */
    fun listLoadedModels(): List<String> = models.keys.toList()

    /** Update system prompt of the active model on the fly. */
    fun setSystemPrompt(systemPrompt: String) {
        activeModelVariant()?.lib?.setSystemPrompt(systemPrompt)
    }

    /** Release everything: queue, jobs, native libs, executor. */
    fun shutdown() {
        runCatching { queue.close() }
        processorJob?.cancel()
        stopGeneration()
        unloadAllModels()
        genDispatcher.cancel()
        genExecutor.shutdown()
    }

    /* ------------------------------------------------------------- *//*  Public params API                                             *//* ------------------------------------------------------------- */

    fun updateModelParams(
        professional: ModelParams.Professional = ModelParams.Professional(),
        emotional: ModelParams.Emotional = ModelParams.Emotional(),
    ) {
        _params.value = ParamsBundle(professional, emotional)
    }

    // Back‑compat helpers for existing callsites
    fun getModel(): StateFlow<ModelsData> = currentModel
    fun getModelParams(): StateFlow<ParamsBundle> = params
    fun setCurrentModel(model: ModelsData) {
        _currentModel.value = model
    }

    /* ------------------------------------------------------------- *//*  Public DAO API (all on IO dispatcher)                         *//* ------------------------------------------------------------- */

    fun observeModels(): Flow<List<ModelsData>> {
        ensureDaoInitialized()
        return dao.getAllModels()
    }

    suspend fun addModel(model: ModelsData) = withContext(ioDispatcher) {
        ensureDaoInitialized()
        dao.insertModel(model)
    }

    suspend fun removeModel(modelName: String) = withContext(ioDispatcher) {
        ensureDaoInitialized()
        dao.getModelByName(modelName)?.let { dao.deleteModel(it) }
    }

    suspend fun checkIfInstalled(modelName: String): Boolean = withContext(ioDispatcher) {
        ensureDaoInitialized()
        dao.getModelByName(modelName) != null
    }

    suspend fun getFirstModel(): ModelsData? = withContext(ioDispatcher) {
        ensureDaoInitialized()
        dao.getAllModels().firstOrNull()?.firstOrNull()
    }

    suspend fun getModel(modelName: String): ModelsData? = withContext(ioDispatcher) {
        ensureDaoInitialized()
        dao.getModelByName(modelName)
    }

    suspend fun isAnyModelInstalled(): Boolean = withContext(ioDispatcher) {
        ensureDaoInitialized()
        dao.getAllModels().firstOrNull()?.isNotEmpty() == true
    }

    suspend fun getAllModels(): List<ModelsData> = withContext(ioDispatcher) {
        ensureDaoInitialized()
        dao.getAllModels().firstOrNull() ?: emptyList()
    }

    /* ------------------------------------------------------------- *//*  PRIVATE: internals (native + queue + guards)                  *//* ------------------------------------------------------------- */

    // Dispatchers & scopes
    private val ioDispatcher = Dispatchers.IO

    // Dedicated gen thread so Vulkan/CPU work never competes with UI
    private val genExecutor = Executors.newSingleThreadExecutor { r ->
        Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            r.run()
        }, "LLM-Gen")
    }
    private val genDispatcher: CoroutineDispatcher = genExecutor.asCoroutineDispatcher()

    // All native calls & the queue processor live here
    private val scope = CoroutineScope(SupervisorJob() + genDispatcher)

    // Stable UI scope for things that must hop to main
    private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // Active native variants keyed by absolute path (one‑active policy enforced)
    private data class Variant(
        val path: File,
        val lib: NativeLib,
        val init: ModelInitParams,
        val loadJob: Job?,
    )

    private val models = ConcurrentHashMap<String, Variant>() // key = absolutePath
    private var activeModelId: String? = null

    /** Back‑pressure aware queue; keeps RAM stable under load. */
    private val queue = Channel<Request>(capacity = 64)

    /** Current native generation job, for cancellation. */
    @Volatile
    private var currentGenJob: Job? = null
    private var processorJob: Job? = null

    private sealed interface Request {
        data class Blocking(
            val prompt: String,
            val gen: GenerationParams,
            val completer: CompletableDeferred<String>
        ) : Request

        data class Streaming(
            val prompt: String,
            val gen: GenerationParams,
            val onToken: (String) -> Unit,
            val toolJson: String?,
            val onToolCalled: (String, String) -> Unit,
            val completer: CompletableDeferred<String>
        ) : Request
    }

    init {
        startProcessor()
    }

    private fun ensureDaoInitialized() {
        check(initGuard.get()) { "ModelManager.init(context) must be called before use." }
    }

    /** Core loader: unloads others, inits native, sets active, notifies onLoaded on main. */
    private fun internalLoadModel(
        path: File,
        init: ModelInitParams,
        forceReload: Boolean,
        onLoaded: (() -> Unit)? = null,
    ) {
        val id = path.absolutePath
        if (!forceReload && models.containsKey(id)) {
            activeModelId = id
            onLoaded?.invoke()
            return
        }

        // One‑active‑model policy: keeps native memory simple & predictable
        unloadAllModels()

        val lib = NativeLib()
        val job = scope.launch {
            runCatching {
                val ok = lib.initModel(
                    path = path.path,
                    threads = init.threads,
                    gpuLayers = init.gpuLayers,
                    useMMAP = init.useMMAP,
                    useMLOCK = init.useMLOCK,
                    ctxSize = init.ctxSize,
                    temp = init.temp,
                    topK = init.topK,
                    topP = init.topP,
                    minP = init.minP,
                )
                if (!ok) error("native init failed for ${path.name}")

                lib.setSystemPrompt(init.systemPrompt)
                init.chatTemplate?.let { lib.nativeSetChatTemplate(it) }
                withContext(Dispatchers.Main.immediate) { onLoaded?.invoke() }
            }.onFailure {
                Log.e(TAG, "Model load failed", it)
                lib.nativeRelease()
            }
        }

        models[id] = Variant(path, lib, init, job)
        activeModelId = id
        Log.d(TAG, "Loaded model ${path.name} (${path.length()} bytes)")
    }

    private fun activeModelVariant(): Variant? = activeModelId?.let { models[it] }

    private fun activeModelOrThrow(): Variant =
        activeModelVariant() ?: error("No active model selected. Call loadModel() first.")

    private fun unloadActiveModel() {
        activeModelId?.let { id ->
            models.remove(id)?.lib?.nativeRelease()
        }
        activeModelId = null
        isGenerating.value = false
    }

    private fun unloadAllModels() {
        models.values.forEach { it.lib.nativeRelease() }
        models.clear()
        activeModelId = null
        isGenerating.value = false
    }

    private fun startProcessor() {
        processorJob?.cancel()
        processorJob = scope.launch {
            Log.d(
                TAG,
                "processor on ${Thread.currentThread().name}, prio=" + Process.getThreadPriority(
                    Process.myTid()
                )
            )

            queue.consumeEach { req ->
                try {
                    isGenerating.value = true
                    when (req) {
                        is Request.Blocking -> handleBlocking(req)
                        is Request.Streaming -> handleStreaming(req)
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "Generation error", t)
                    when (req) {
                        is Request.Blocking -> req.completer.completeExceptionally(t)
                        is Request.Streaming -> req.completer.completeExceptionally(t)
                    }
                } finally {
                    currentGenJob = null
                    isGenerating.value = false
                }
            }
        }
    }

    private suspend fun handleBlocking(r: Request.Blocking) {
        val lib = activeModelOrThrow().lib
        val acc = StringBuilder()
        val done = CompletableDeferred<Unit>()

        currentGenJob = lib.generateStreaming(
            prompt = r.prompt,
            maxTokens = r.gen.maxTokens,
            uiScope = uiScope, // ensure native posts UI work to main
            onStart = {},
            onGenerate = { tok -> acc.append(tok) },
            onError = { msg -> done.completeExceptionally(IllegalStateException(msg)) },
            onDone = { done.complete(Unit) })

        done.await()

        val reply = acc.toString().trim()
        r.completer.complete(reply)
        replies.tryEmit(reply)
    }

    private suspend fun handleStreaming(r: Request.Streaming) {
        val lib = activeModelOrThrow().lib
        val acc = StringBuilder()
        val done = CompletableDeferred<Unit>()

        currentGenJob = lib.generateStreaming(
            prompt = r.prompt,
            maxTokens = r.gen.maxTokens,
            uiScope = uiScope, // ensure any UI callbacks use main
            onStart = {},
            onGenerate = { tok ->
                acc.append(tok)
                r.onToken(tok)
            },
            toolsJson = r.toolJson,
            onToolCall = r.onToolCalled,
            onError = { msg -> done.completeExceptionally(IllegalStateException(msg)) },
            onDone = { done.complete(Unit) })

        done.await()

        val reply = acc.toString()
        r.completer.complete(reply)
        replies.tryEmit(reply)
    }
}

object ModelParams {
    data class Professional(val value: Float = 3.5f)
    data class Emotional(val value: Float = 7.6f)
}
