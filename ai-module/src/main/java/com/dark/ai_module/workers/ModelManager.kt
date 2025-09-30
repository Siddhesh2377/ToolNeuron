package com.dark.ai_module.workers

import android.content.Context
import android.os.Process
import android.util.Log
import com.dark.ai_module.db.DatabaseProvider
import com.dark.ai_module.db.ModelDAO
import com.dark.ai_module.model.GenerationParams
import com.dark.ai_module.model.LoadState
import com.dark.ai_module.model.ManagerDefaults
import com.dark.ai_module.model.ModelInitParams
import com.dark.ai_module.model.ModelParams
import com.dark.ai_module.model.ModelsData
import com.dark.ai_module.model.ParamsBundle
import com.dark.ai_module.model.getDefaultModelData
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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds

/**
 * ModelManager — with hardened, normalized toolJson handling.
 */
object ModelManager {

    private const val TAG = "ModelManager"

    private val _currentModel = MutableStateFlow(getDefaultModelData())
    val currentModel: StateFlow<ModelsData> = _currentModel.asStateFlow()

    private val _params = MutableStateFlow(ParamsBundle())
    val params: StateFlow<ParamsBundle> = _params.asStateFlow()

    val isGenerating = MutableStateFlow(false)
    val replies = MutableSharedFlow<String>(extraBufferCapacity = 16)

    /* ------------------------------------------------------------- */
    /*  Public lifecycle API (DB + init)                             */
    /* ------------------------------------------------------------- */

    private val initGuard = AtomicBoolean(false)
    private lateinit var dao: ModelDAO
    private val generationLib = NativeLib.getGenerationInstance()

    fun init(context: Context) {
        if (initGuard.compareAndSet(false, true)) {
            dao = DatabaseProvider.getDatabase(context).ModelDAO()
        }
    }

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
            // Otherwise, proceed with loading the model
            val init = ModelInitParams(
                ctxSize = defaults.contextLength,
                systemPrompt = defaults.systemPrompt,
                chatTemplate = chatTemplate,
                useMLOCK = keepInMemory,
            )
            Log.d(TAG, "Loading model ${f.name} with forceReload=$forceReload")
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

    fun unLoadModel() = unloadActiveModel()

    /** Streaming with hardened toolJson. */
    suspend fun generateStreaming(
        prompt: String,
        gen: GenerationParams = GenerationParams(),
        toolJson: String?,
        onToolCalled: (String, String) -> Unit = { _, _ -> },
        onToken: (String) -> Unit = {},
    ): String {
        val def = CompletableDeferred<String>()
        val normalized = ToolJsonUtils.normalizeSpec(toolJson)
        val maybeDeduped = ToolJsonUtils.maybeDedup(normalized)
        queue.send(
            Request.Streaming(
                prompt = prompt,
                gen = gen,
                onToken = onToken,
                toolJson = maybeDeduped, // send null if same as last to save bandwidth
                onToolCalled = { name, args ->
                    if (ToolJsonUtils.isSchemaEcho(args)) {
                        Log.w(
                            TAG,
                            "Model echoed tool schema inside arguments; upstream should repair."
                        )
                    }
                    onToolCalled(name, args)
                },
                completer = def
            )
        )
        return def.await()
    }

    fun stopGeneration() {
        currentGenJob?.cancel()
        activeModelVariant()?.lib?.nativeStopGeneration()
        isGenerating.value = false
    }
    fun setSystemPrompt(systemPrompt: String) {
        activeModelVariant()?.lib?.setSystemPrompt(systemPrompt)
    }

    fun shutdown() {
        runCatching { queue.close() }
        processorJob?.cancel()
        stopGeneration()
        unloadAllModels()
        genDispatcher.cancel()
        genExecutor.shutdown()
    }

    /* ------------------------------------------------------------- */
    /*  Public params API                                            */
    /* ------------------------------------------------------------- */

    fun updateModelParams(
        professional: ModelParams.Professional = ModelParams.Professional(),
        emotional: ModelParams.Emotional = ModelParams.Emotional(),
    ) {
        _params.value = ParamsBundle(professional, emotional)
    }

    fun getModel(): StateFlow<ModelsData> = currentModel

    /* ------------------------------------------------------------- */
    /*  Public DAO API (IO)                                          */
    /* ------------------------------------------------------------- */

    fun observeModels(): Flow<List<ModelsData>> {
        ensureDaoInitialized(); return dao.getAllModels()
    }

    suspend fun addModel(model: ModelsData) =
        withContext(ioDispatcher) { ensureDaoInitialized(); dao.insertModel(model) }

    suspend fun removeModel(modelName: String) = withContext(ioDispatcher) {
        ensureDaoInitialized(); dao.getModelByName(modelName)?.let { dao.deleteModel(it) }
    }

    suspend fun checkIfInstalled(modelName: String): Boolean =
        withContext(ioDispatcher) { ensureDaoInitialized(); dao.getModelByName(modelName) != null }

    suspend fun getFirstModel(): ModelsData? = withContext(ioDispatcher) {
        ensureDaoInitialized(); dao.getAllModels().firstOrNull()?.firstOrNull()
    }

    suspend fun getModel(modelName: String): ModelsData? =
        withContext(ioDispatcher) { ensureDaoInitialized(); dao.getModelByName(modelName) }

    suspend fun isAnyModelInstalled(): Boolean = withContext(ioDispatcher) {
        ensureDaoInitialized(); dao.getAllModels().firstOrNull()?.isNotEmpty() == true
    }

    suspend fun getAllModels(): List<ModelsData> = withContext(ioDispatcher) {
        ensureDaoInitialized(); dao.getAllModels().firstOrNull() ?: emptyList()
    }

    /* ------------------------------------------------------------- */
    /*  PRIVATE internals                                            */
    /* ------------------------------------------------------------- */

    private val ioDispatcher = Dispatchers.IO

    private val genExecutor = Executors.newSingleThreadExecutor { r ->
        Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            r.run()
        }, "LLM-Gen")
    }
    private val genDispatcher: CoroutineDispatcher = genExecutor.asCoroutineDispatcher()

    private val scope = CoroutineScope(SupervisorJob() + genDispatcher)
    private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private data class Variant(
        val path: File,
        val lib: NativeLib,
        val init: ModelInitParams,
        val loadJob: Job?
    )

    private val models = ConcurrentHashMap<String, Variant>()
    private var activeModelId: String? = null

    private val queue = Channel<Request>(capacity = 64)

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

    private fun internalLoadModel(
        path: File,
        init: ModelInitParams,
        forceReload: Boolean,
        onLoaded: (() -> Unit)? = null
    ) {
        val id = path.absolutePath

        Log.d(TAG, "Loading generation model: ${path.name}")
        unloadAllModels()

        val job = scope.launch {
            runCatching {
                val ok = generationLib.initModel(
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
                if (!ok) {
                    error("Generation model init failed for ${path.name}")
                }
                generationLib.setSystemPrompt(init.systemPrompt)
                init.chatTemplate?.let { generationLib.nativeSetChatTemplate(it) }
                withContext(Dispatchers.Main.immediate) { onLoaded?.invoke() }
            }.onFailure { e ->
                Log.e(TAG, "Model load failed", e)
                throw e
            }
        }
        models[id] = Variant(path, generationLib, init, job)
        activeModelId = id
        Log.d(TAG, "Generation model loaded: ${path.name}")
    }


    private fun activeModelVariant(): Variant? = activeModelId?.let { models[it] }
    private fun activeModelOrThrow(): Variant =
        activeModelVariant() ?: error("No active model selected. Call loadModel() first.")

    private fun unloadActiveModel() {
        activeModelId?.let { id ->
            Log.d(TAG, "Releasing NativeLib instance for model $id")
            models.remove(id)?.lib?.nativeRelease()
        }
        activeModelId = null
        isGenerating.value = false
    }

    private fun unloadAllModels() {
        models.values.forEach { model ->
            Log.d(TAG, "Releasing NativeLib instance for model ${model.path.name}")
            model.lib.nativeRelease()
        }
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
            uiScope = uiScope,
            onStart = {},
            onGenerate = { tok -> acc.append(tok) },
            onError = { msg -> done.completeExceptionally(IllegalStateException(msg)) },
            onDone = { done.complete(Unit) }
        )
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
            prompt = """
                ${r.prompt}
            """.trimIndent(),
            maxTokens = r.gen.maxTokens,
            uiScope = uiScope,
            onStart = {},
            onGenerate = { tok -> acc.append(tok); r.onToken(tok) },
            toolsJson = r.toolJson, // already normalized/deduped
            onToolCall = { name, args ->
                r.onToolCalled(name, args); Log.d(
                TAG,
                "Tool called: $name args=$args"
            )
            },
            onError = { msg -> done.completeExceptionally(IllegalStateException(msg)) },
            onDone = { done.complete(Unit) }
        )
        done.await()
        val reply = acc.toString()
        r.completer.complete(reply)
        replies.tryEmit(reply)
    }

    /* ------------------------------------------------------------- */
    /*  Tool JSON utilities                                          */
    /* ------------------------------------------------------------- */

    private object ToolJsonUtils {
        private const val MAX_DESC = 512 // prune oversharing
        private const val MAX_SPEC_BYTES = 64 * 1024 // safety cap
        private var lastHash: Int? = null
        private const val DEDUP = true

        /** Normalize a tools spec to compact, valid JSON array string. */
        fun normalizeSpec(spec: String?): String? {
            if (spec.isNullOrBlank()) return null
            val root: JSONArray = try {
                when (spec.trim().firstOrNull()) {
                    '[' -> JSONArray(spec)
                    '{' -> JSONArray().put(JSONObject(spec))
                    else -> return null
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Invalid tool spec JSON", t); return null
            }

            // Walk and sanitize
            for (i in 0 until root.length()) {
                val item = root.optJSONObject(i) ?: continue
                val fn = item.optJSONObject("function") ?: continue
                // Trim description
                fn.optString("description").takeIf { it.length > MAX_DESC }?.let {
                    fn.put("description", it.take(MAX_DESC))
                }
                val params = fn.optJSONObject("parameters") ?: continue
                // Force required → JSONArray
                val required = params.opt("required")
                if (required != null && required !is JSONArray) {
                    try {
                        val arr = JSONArray()
                        when (required) {
                            is String -> arr.put(required)
                            is Iterable<*> -> required.forEach { k -> if (k is String) arr.put(k) }
                            else -> {}
                        }
                        params.put("required", arr)
                    } catch (_: Throwable) {
                    }
                }
                // Properties ordering (stable for hashing)
                val props = params.optJSONObject("properties")
                if (props != null) {
                    val keys = props.keys().asSequence().toList().sorted()
                    val ordered = JSONObject()
                    for (k in keys) ordered.put(k, props.get(k))
                    params.put("properties", ordered)
                }
            }

            val compact = root.toString()
            // Size guard
            if (compact.toByteArray().size > MAX_SPEC_BYTES) {
                Log.w(TAG, "Tool spec too large; dropping descriptions to shrink")
                for (i in 0 until root.length()) {
                    root.optJSONObject(i)?.optJSONObject("function")?.remove("description")
                }
            }
            return root.toString()
        }

        /** Return null if spec unchanged to save native hops. */
        fun maybeDedup(spec: String?): String? {
            if (!DEDUP || spec == null) return spec
            val h = spec.hashCode()
            return if (lastHash == h) null else {
                lastHash = h; spec
            }
        }

        /** Heuristic: arguments look like a schema echo instead of concrete args. */
        fun isSchemaEcho(argsJson: String?): Boolean {
            if (argsJson.isNullOrBlank()) return false
            return try {
                val obj = JSONObject(argsJson)
                val calls = obj.optJSONArray("tool_calls") ?: return false
                val first = calls.optJSONObject(0) ?: return false
                val a = first.optJSONObject("arguments") ?: return false
                a.has("type") || a.has("properties") || a.has("required")
            } catch (_: Throwable) {
                false
            }
        }
    }
}
