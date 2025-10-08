package com.dark.ai_module.workers

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Process
import android.util.Log
import com.dark.ai_module.db.DatabaseProvider
import com.dark.ai_module.db.ModelDAO
import com.dark.ai_module.model.GenerationParams
import com.dark.ai_module.model.LoadState
import com.dark.ai_module.model.ModelData
import com.dark.ai_module.model.ModelProvider
import com.mp.ai_core.services.GenerationService
import com.mp.ai_core.services.IGenerationCallback
import com.mp.ai_core.services.IGenerationService
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ModelManager – unified interface for:
 *   • Local GGUF models (via AIDL GenerationService)
 *   • OpenRouter cloud models (via OpenRouterExecutor)
 *   • DB persistence (Room)
 */
@SuppressLint("StaticFieldLeak")
object ModelManager {

    private const val TAG = "ModelManager"

    /* ═══════════════════════════════════════════════════════════════════ */
    /*  AIDL Service (for local GGUF models)                               */
    /* ═══════════════════════════════════════════════════════════════════ */
    private var service: IGenerationService? = null
    private var serviceBoundContext: Context? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder?) {
            binder?.let {
                service = IGenerationService.Stub.asInterface(it)
                Log.i(TAG, "GenerationService connected")
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
            Log.w(TAG, "GenerationService disconnected")
        }
    }

    /* ═══════════════════════════════════════════════════════════════════ */
    /*  OpenRouter Executor (for cloud models)                             */
    /* ═══════════════════════════════════════════════════════════════════ */
    private var openRouterExecutor: OpenRouterExecutor? = null
    private var openRouterApiKey: String = ""
    private var openRouterBaseUrl: String = "https://openrouter.ai/api/v1"

    /**
     * Configure OpenRouter credentials
     * Call this when API key changes
     */
    fun configureOpenRouter(apiKey: String, baseUrl: String = "https://openrouter.ai/api/v1") {
        openRouterApiKey = apiKey
        openRouterBaseUrl = baseUrl
        openRouterExecutor = if (apiKey.isNotBlank()) {
            OpenRouterExecutor(apiKey, baseUrl)
        } else {
            null
        }
        Log.i(TAG, "OpenRouter configured: ${if (apiKey.isBlank()) "disabled" else "enabled"}")
    }

    /* ═══════════════════════════════════════════════════════════════════ */
    /*  DB / DAO State                                                      */
    /* ═══════════════════════════════════════════════════════════════════ */
    private val initGuard = AtomicBoolean(false)
    private lateinit var dao: ModelDAO

    /** Call once (e.g. in Application.onCreate()) */
    fun init(context: Context) {
        if (initGuard.compareAndSet(false, true)) {
            dao = DatabaseProvider.getDatabase(context).ModelDAO()
            startService(context)
        }
    }

    private fun ensureDaoInitialized() {
        check(initGuard.get()) { "ModelManager.init(context) must be called before use." }
    }

    /* ═══════════════════════════════════════════════════════════════════ */
    /*  Public State                                                        */
    /* ═══════════════════════════════════════════════════════════════════ */
    private val _currentModel = MutableStateFlow(ModelData())
    val currentModel: StateFlow<ModelData> = _currentModel.asStateFlow()

    val isGenerating = MutableStateFlow(false)

    /* ═══════════════════════════════════════════════════════════════════ */
    /*  Load/Unload Model (GGUF only - OpenRouter has no loading step)     */
    /* ═══════════════════════════════════════════════════════════════════ */
    suspend fun loadModelAwait(
        modelData: ModelData,
        onLoaded: (LoadState) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {

        // Check if this is an OpenRouter model
        if (modelData.providerName == ModelProvider.OpenRouter.toString()) {
            // No loading needed for cloud models
            _currentModel.value = modelData
            onLoaded(LoadState.OnLoaded(modelData))
            return@withContext Result.success(Unit)
        }else{
            // GGUF model loading
            onLoaded(LoadState.Idle)
            onLoaded(LoadState.Loading(0f))

            try {
                val f = File(modelData.modelPath)
                if (!f.exists()) {
                    val err = IllegalArgumentException("Missing model: ${f.absolutePath}")
                    onLoaded(LoadState.Error(err.message ?: "Load failed"))
                    Log.e("Model", err.message ?: "Load failed")
                    return@withContext Result.failure(err)
                }

                val svc = service ?: run {
                    onLoaded(LoadState.Error("Service not bound"))
                    Log.e("Model", "Service not bound")
                    return@withContext Result.failure(RuntimeException("Service not bound"))
                }

                val ok = svc.loadModel(
                    modelData.modelPath,
                    modelData.threads,
                    modelData.gpuLayers,
                    modelData.useMMAP,
                    modelData.ctxSize,
                    modelData.temp,
                    modelData.topK,
                    modelData.topP,
                    modelData.minP
                )

                if (!ok) {
                    onLoaded(LoadState.Error("Model load failed in service"))
                    Log.e("Model", "Model load failed")
                    return@withContext Result.failure(RuntimeException("Model load failed"))
                }

                svc.setSystemPrompt(modelData.systemPrompt)
                modelData.chatTemplate?.let { svc.setChatTemplate(it) }

                _currentModel.value = modelData
                onLoaded(LoadState.OnLoaded(modelData))
                Result.success(Unit)
            } catch (t: Throwable) {
                onLoaded(LoadState.Error(t.message ?: "Load failed"))
                Result.failure(t)
            }
        }
    }

    fun unloadModel() {
        val svc = service ?: return
        svc.unloadModel()
        _currentModel.value = ModelData()
    }

    fun setChatTemplate(template: String) {
        val svc = service ?: return
        svc.setChatTemplate(template)
    }

    /* ═══════════════════════════════════════════════════════════════════ */
    /*  Unified Streaming API - Routes to GGUF or OpenRouter               */
    /* ═══════════════════════════════════════════════════════════════════ */
    suspend fun generateStreaming(
        prompt: String,
        gen: GenerationParams = GenerationParams(),
        toolJson: String? = null,
        onToolCalled: (String, String) -> Unit = { _, _ -> },
        onToken: (String) -> Unit = {}
    ): String {
        val model = _currentModel.value

        return when (model.providerName) {
            ModelProvider.OpenRouter.toString() -> {
                generateOpenRouter(
                    modelId = model.modelUrl?: "",
                    prompt = prompt,
                    systemPrompt = model.systemPrompt,
                    gen = gen,
                    toolJson = toolJson,
                    onToolCalled = onToolCalled,
                    onToken = onToken
                )
            }

            ModelProvider.LocalGGUF.toString() -> {
                generateGGUF(
                    prompt = prompt,
                    gen = gen,
                    toolJson = toolJson,
                    onToolCalled = onToolCalled,
                    onToken = onToken
                )
            }

            else -> {
                throw IllegalStateException("Unknown provider: ${model.providerName}")
            }
        }
    }

    /**
     * Generate using OpenRouter API
     */
    private suspend fun generateOpenRouter(
        modelId: String,
        prompt: String,
        systemPrompt: String,
        gen: GenerationParams,
        toolJson: String?,
        onToolCalled: (String, String) -> Unit,
        onToken: (String) -> Unit
    ): String {
        val executor = openRouterExecutor
            ?: throw IllegalStateException("OpenRouter not configured. Call configureOpenRouter() first.")

        isGenerating.value = true

        try {
            val normalized = ToolJsonUtils.normalizeSpec(toolJson)

            val result = executor.generateStreaming(
                modelId = modelId,
                prompt = prompt,
                systemPrompt = systemPrompt,
                gen = gen,
                toolsJson = normalized,
                onToken = onToken,
                onToolCall = onToolCalled
            )

            return result.getOrThrow()
        } finally {
            isGenerating.value = false
        }
    }

    /**
     * Generate using local GGUF model via queue system
     */
    private suspend fun generateGGUF(
        prompt: String,
        gen: GenerationParams,
        toolJson: String?,
        onToolCalled: (String, String) -> Unit,
        onToken: (String) -> Unit
    ): String {
        val def = CompletableDeferred<String>()

        val normalized = ToolJsonUtils.normalizeSpec(toolJson)
        val deduped = ToolJsonUtils.maybeDedup(normalized)

        queue.send(
            Request.Streaming(
                prompt = prompt,
                gen = gen,
                onToken = onToken,
                toolJson = deduped,
                onToolCalled = onToolCalled,
                completer = def
            )
        )

        return def.await()
    }

    fun stopGeneration() {
        val model = _currentModel.value

        when (model.providerName) {
            ModelProvider.OpenRouter.toString() -> {
                openRouterExecutor?.stopGeneration()
            }
            ModelProvider.LocalGGUF.toString() -> {
                service?.stopGeneration()
            }
        }

        isGenerating.value = false
    }

    fun setSystemPrompt(prompt: String) {
        val model = _currentModel.value

        when (model.providerName) {
            ModelProvider.LocalGGUF.toString() -> {
                service?.setSystemPrompt(prompt)
            }
            // OpenRouter system prompt is set per-request, no action needed
        }

        // Update current model
        _currentModel.value = model.copy(systemPrompt = prompt)
    }

    /* ═══════════════════════════════════════════════════════════════════ */
    /*  Graceful Shutdown                                                   */
    /* ═══════════════════════════════════════════════════════════════════ */
    fun shutdown() {
        // Graceful shutdown of the internal queue + background worker
        queue.close()
        processorJob?.cancel()
        stopGeneration()
        unloadModel()

        // Cancel dispatchers & executor
        genDispatcher.cancel()
        genExecutor.shutdown()

        // Stop OpenRouter (if it was configured)
        openRouterExecutor = null

        // Now unbind the AIDL service
        shutdownService()

        Log.i(TAG, "ModelManager shut down")
    }

    /**
     * Explicitly bind the `GenerationService`.
     * This is equivalent to the `init(context)` logic that was previously
     * executed only once.
     *
     * @param context the context that will be kept until `shutdownService()` is called
     */
    fun startService(context: Context) {
        context.bindService(
            Intent(context, GenerationService::class.java),
            connection,
            Context.BIND_AUTO_CREATE
        )
        serviceBoundContext = context.applicationContext   // keep a weak reference
    }

    /**
     * Unbind a connected `GenerationService`.
     * If the service was never bound this is a no‑op.
     */
    fun shutdownService() {
        val ctx = serviceBoundContext ?: return
        try {
            ctx.unbindService(connection)
        } catch (e: IllegalArgumentException) {
            // Service was already unbound – ignore
        }
        service = null
        serviceBoundContext = null
    }

    fun isModelLoaded(): Boolean {
        return _currentModel.value.modelName != ""
    }

    /* ═══════════════════════════════════════════════════════════════════ */
    /*  Request Queue (GGUF only)                                           */
    /* ═══════════════════════════════════════════════════════════════════ */
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

    private val queue = Channel<Request>(capacity = 64)
    private var processorJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        startProcessor()
    }

    private fun startProcessor() {
        processorJob?.cancel()
        processorJob = scope.launch {
            Log.d(TAG, "Processor started on ${Thread.currentThread().name}")
            queue.consumeEach { req ->
                try {
                    isGenerating.value = true
                    when (req) {
                        is Request.Blocking -> handleBlocking(req)
                        is Request.Streaming -> handleStreaming(req)
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "Generation error", t)
                } finally {
                    isGenerating.value = false
                }
            }
        }
    }

    private suspend fun handleBlocking(r: Request.Blocking) {
        val svc = service ?: run {
            r.completer.completeExceptionally(RuntimeException("Service not bound"))
            return
        }

        svc.generate(
            r.prompt, r.gen.maxTokens, object : IGenerationCallback.Stub() {
                var acc = StringBuilder()
                override fun onToken(token: String) {
                    acc.append(token)
                }

                override fun onToolCall(name: String, args: String) {}

                override fun onDone() {
                    r.completer.complete(acc.toString())
                }

                override fun onError(error: String) {
                    r.completer.completeExceptionally(RuntimeException(error))
                }
            })
    }

    private fun handleStreaming(r: Request.Streaming) {
        val svc = service ?: run {
            r.completer.completeExceptionally(RuntimeException("Service not bound"))
            return
        }

        svc.generate(
            r.prompt, r.gen.maxTokens, object : IGenerationCallback.Stub() {
                var acc = StringBuilder()
                override fun onToken(token: String) {
                    r.onToken(token)
                    acc.append(token)
                }

                override fun onToolCall(name: String, args: String) {
                    r.onToolCalled(name, args)
                }

                override fun onDone() {
                    r.completer.complete(acc.toString())
                }

                override fun onError(error: String) {
                    r.completer.completeExceptionally(RuntimeException(error))
                }
            })
    }

    /* ═══════════════════════════════════════════════════════════════════ */
    /*  Tool JSON Utilities                                                 */
    /* ═══════════════════════════════════════════════════════════════════ */
    private object ToolJsonUtils {
        private const val MAX_DESC = 512
        private const val MAX_SPEC_BYTES = 64 * 1024
        private var lastHash: Int? = null

        fun normalizeSpec(spec: String?): String? {
            if (spec.isNullOrBlank()) return null
            val root: JSONArray = try {
                when (spec.trimStart().firstOrNull()) {
                    '[' -> JSONArray(spec)
                    '{' -> JSONArray().put(JSONObject(spec))
                    else -> return null
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Invalid tool spec JSON", t)
                return null
            }

            for (i in 0 until root.length()) {
                val item = root.optJSONObject(i) ?: continue
                val func = item.optJSONObject("function") ?: continue

                func.optString("description").takeIf { it.length > MAX_DESC }?.let {
                    func.put("description", it.take(MAX_DESC))
                }

                val params = func.optJSONObject("parameters") ?: continue
                val required = params.opt("required")
                if (required != null && required !is JSONArray) {
                    val arr = JSONArray()
                    when (required) {
                        is String -> arr.put(required)
                        is Iterable<*> -> required.forEach { k ->
                            if (k is String) arr.put(k)
                        }
                    }
                    params.put("required", arr)
                }

                val props = params.optJSONObject("properties") ?: continue
                val ordered = JSONObject()
                props.keys().asSequence().sorted().forEach { key ->
                    ordered.put(key, props.get(key))
                }
                params.put("properties", ordered)
            }

            val txt = root.toString()
            if (txt.toByteArray().size > MAX_SPEC_BYTES) {
                Log.w(TAG, "Tool spec too big – dropping descriptions")
                for (i in 0 until root.length()) {
                    root.optJSONObject(i)?.optJSONObject("function")?.remove("description")
                }
                return root.toString()
            }
            return txt
        }

        fun maybeDedup(spec: String?): String? {
            if (spec == null) return spec
            val h = spec.hashCode()
            return if (lastHash == h) null else {
                lastHash = h
                spec
            }
        }
    }

    /* ═══════════════════════════════════════════════════════════════════ */
    /*  Background Thread for GGUF Queue                                    */
    /* ═══════════════════════════════════════════════════════════════════ */
    private val genExecutor = Executors.newSingleThreadExecutor { r ->
        Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            r.run()
        }, "LLM-Gen")
    }

    private val genDispatcher = genExecutor.asCoroutineDispatcher()

    /* ═══════════════════════════════════════════════════════════════════ */
    /*  DB API - Shared for both GGUF and OpenRouter                        */
    /* ═══════════════════════════════════════════════════════════════════ */
    fun observeModels(): Flow<List<ModelData>> {
        ensureDaoInitialized()
        return dao.getAllModels()
    }

    suspend fun addModel(model: ModelData) = withContext(Dispatchers.IO) {
        ensureDaoInitialized()
        dao.insertModel(model)
    }

    suspend fun removeModel(modelName: String) = withContext(Dispatchers.IO) {
        ensureDaoInitialized()
        dao.getModelByName(modelName)?.let { dao.deleteModel(it) }
    }

    suspend fun checkIfInstalled(modelName: String): Boolean = withContext(Dispatchers.IO) {
        ensureDaoInitialized()
        dao.getModelByName(modelName) != null
    }

    suspend fun getFirstModel(): ModelData? = withContext(Dispatchers.IO) {
        ensureDaoInitialized()
        dao.getAllModels().firstOrNull()?.firstOrNull()
    }

    suspend fun getModel(modelName: String): ModelData? = withContext(Dispatchers.IO) {
        ensureDaoInitialized()
        dao.getModelByName(modelName)
    }

    suspend fun isAnyModelInstalled(): Boolean = withContext(Dispatchers.IO) {
        ensureDaoInitialized()
        dao.getAllModels().firstOrNull()?.isNotEmpty() == true
    }

    suspend fun getAllModels(): List<ModelData> = withContext(Dispatchers.IO) {
        ensureDaoInitialized()
        dao.getAllModels().firstOrNull() ?: emptyList()
    }
}
