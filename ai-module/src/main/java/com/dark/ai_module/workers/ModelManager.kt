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
import com.dark.ai_module.model.ModelType
import com.mp.ai_core.services.GenerationService
import com.mp.ai_core.services.IGenerationCallback
import com.mp.ai_core.services.IGenerationService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min

@SuppressLint("StaticFieldLeak")
object ModelManager {

    private const val TAG = "ModelManager"

    // Service
    private var service: IGenerationService? = null
    private var serviceBoundContext: Context? = null
    private val serviceConnection = ServiceConnectionImpl()

    // OpenRouter
    private var openRouterExecutor: OpenRouterExecutor? = null
    private var openRouterApiKey: String = ""
    private var openRouterBaseUrl: String = "https://openrouter.ai/api/v1"

    // Database
    private val initGuard = AtomicBoolean(false)
    private lateinit var dao: ModelDAO

    // State
    private val _currentModel = MutableStateFlow(ModelData())
    val currentModel: StateFlow<ModelData> = _currentModel.asStateFlow()
    val isGenerating = MutableStateFlow(false)

    // Generation Queue
    private val queue = Channel<GenerationRequest>(capacity = 64)
    private var processorJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val genExecutor = Executors.newSingleThreadExecutor { r ->
        Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            r.run()
        }, "LLM-Gen")
    }
    private val genDispatcher = genExecutor.asCoroutineDispatcher()

    init {
        startProcessor()
    }

    //region Initialization

    fun init(context: Context) {
        bindService(context)
        if (initGuard.compareAndSet(false, true)) {
            dao = DatabaseProvider.getDatabase(context).ModelDAO()
        }
    }

    fun configureOpenRouter(apiKey: String, baseUrl: String = "https://openrouter.ai/api/v1") {
        openRouterApiKey = apiKey
        openRouterBaseUrl = baseUrl
        openRouterExecutor = if (apiKey.isNotBlank()) {
            OpenRouterExecutor(apiKey, baseUrl)
        } else null
        Log.i(TAG, "OpenRouter configured: ${if (apiKey.isBlank()) "disabled" else "enabled"}")
    }

    //endregion

    //region Service Management

    private fun bindService(context: Context) {
        context.bindService(
            Intent(context, GenerationService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )
        serviceBoundContext = context.applicationContext
    }

    private fun unbindService() {
        serviceBoundContext?.let { ctx ->
            try {
                ctx.unbindService(serviceConnection)
            } catch (_: IllegalArgumentException) {
            }
        }
        service = null
        serviceBoundContext = null
    }

    private class ServiceConnectionImpl : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder?) {
            service = IGenerationService.Stub.asInterface(binder)
            Log.i(TAG, "Service connected")
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
            Log.w(TAG, "Service disconnected")
        }
    }

    //endregion

    //region Model Loading

    suspend fun loadGenerationModel(
        modelData: ModelData, onLoaded: (LoadState) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        when (modelData.providerName) {
            ModelProvider.OpenRouter.toString() -> loadOpenRouterModel(modelData, onLoaded)
            ModelProvider.LocalGGUF.toString() -> loadGGUFModel(modelData, onLoaded)
            else -> Result.failure(IllegalArgumentException("Unknown provider: ${modelData.providerName}"))
        }
    }

    private fun loadOpenRouterModel(
        modelData: ModelData, onLoaded: (LoadState) -> Unit
    ): Result<Unit> {
        _currentModel.value = modelData
        onLoaded(LoadState.OnLoaded(modelData))
        return Result.success(Unit)
    }

    private suspend fun loadGGUFModel(
        modelData: ModelData, onLoaded: (LoadState) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        onLoaded(LoadState.Idle)
        onLoaded(LoadState.Loading(0f))

        val file = File(modelData.modelPath)
        if (!file.exists()) {
            val err = "Model not found: ${file.absolutePath}"
            onLoaded(LoadState.Error(err))
            return@withContext Result.failure(IllegalArgumentException(err))
        }

        val svc = service ?: run {
            onLoaded(LoadState.Error("Service not bound"))
            return@withContext Result.failure(RuntimeException("Service not bound"))
        }

        return@withContext try {
            val ok = svc.loadTextGenerationModel(
                modelData.modelPath,
                modelData.threads,
                modelData.gpuLayers,
                modelData.useMMAP,
                modelData.ctxSize,
                modelData.temp,
                modelData.topK,
                modelData.topP,
                modelData.minP,
                modelData.mirostat,
                modelData.mirostatTau,
                modelData.mirostatEta,
                modelData.seed,
            )

            if (!ok) {
                onLoaded(LoadState.Error("Model load failed"))
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

    suspend fun loadVLModels(
        modelData: ModelData, onLoaded: (LoadState) -> Unit
    ) = withContext(Dispatchers.IO) {
        val svc = service ?: run {
            onLoaded(LoadState.Error("Service not bound"))
            return@withContext
        }

        val pathJson = JSONObject(modelData.modelPath)
        val generatorPath = pathJson.getString("genModelPath")
        val vlModelsPath = pathJson.getString("vlModelPath")

        if (!File(generatorPath).exists()) {
            onLoaded(LoadState.Error("Generator model not found"))
            return@withContext
        }
        if (!File(vlModelsPath).exists()) {
            onLoaded(LoadState.Error("VL model not found"))
            return@withContext
        }

        val threads = min(Runtime.getRuntime().availableProcessors(), 8)

        val textOk = svc.loadTextGenerationModel(
            generatorPath,
            threads,
            0,
            true,
            modelData.ctxSize,
            modelData.temp,
            modelData.topK,
            modelData.topP,
            modelData.minP,
            modelData.mirostat,
            modelData.mirostatTau,
            modelData.mirostatEta,
            modelData.seed,
        )

        if (!textOk) {
            onLoaded(LoadState.Error("Text model load failed"))
            return@withContext
        }

        val vlOk = svc.loadMultimodalProjector(vlModelsPath, threads)
        if (!vlOk) {
            onLoaded(LoadState.Error("VL model load failed"))
            return@withContext
        }

        _currentModel.value = modelData
        onLoaded(LoadState.OnLoaded(modelData))
    }

    suspend fun loadEmbeddingModel(
        modelData: ModelData, onLoaded: (LoadState) -> Unit
    ) = withContext(Dispatchers.IO) {
        val svc = service ?: run {
            onLoaded(LoadState.Error("Service not bound"))
            return@withContext
        }

        if (!File(modelData.modelPath).exists()) {
            onLoaded(LoadState.Error("Embedding model not found"))
            return@withContext
        }

        val threads = min(Runtime.getRuntime().availableProcessors(), 8)
        val ok = svc.loadEmbedModel(modelData.modelPath, threads, 512)

        if (!ok) {
            onLoaded(LoadState.Error("Embedding model load failed"))
            return@withContext
        }

        onLoaded(LoadState.OnLoaded(modelData))
    }

    //endregion

    //region Model Unloading

    fun unloadGenerationModel() {
        service?.unloadTextGenerationModel()
        _currentModel.value = ModelData()
    }

    fun unloadVLModel() {
        service?.let {
            it.unloadTextGenerationModel()
            it.unloadMultimodalProjector()
        }
        _currentModel.value = ModelData()
    }

    fun unloadEmbeddingModel() {
        service?.unLoadEmbeddingModel()
        _currentModel.value = ModelData()
    }

    //endregion

    //region Model Info
    fun getModelInfo(): String? {
        return service?.modelInfo
    }
    //endregion

    //region Text Generation

    suspend fun generateStreaming(
        prompt: String,
        gen: GenerationParams = GenerationParams(),
        toolJson: String = "",
        onToolCalled: (String, String) -> Unit = { _, _ -> },
        onToken: (String) -> Unit = {}
    ): String {
        val model = _currentModel.value

        return when (model.providerName) {
            ModelProvider.OpenRouter.toString() -> {
                generateOpenRouter(
                    modelId = model.modelUrl ?: "",
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

            else -> throw IllegalStateException("Unknown provider: ${model.providerName}")
        }
    }

    private suspend fun generateOpenRouter(
        modelId: String,
        prompt: String,
        systemPrompt: String,
        gen: GenerationParams,
        toolJson: String,
        onToolCalled: (String, String) -> Unit,
        onToken: (String) -> Unit
    ): String {
        val executor =
            openRouterExecutor ?: throw IllegalStateException("OpenRouter not configured")

        isGenerating.value = true
        return try {
            val normalized = ToolJsonUtils.normalizeSpec(toolJson)
            executor.generateStreaming(
                modelId = modelId,
                prompt = prompt,
                systemPrompt = systemPrompt,
                gen = gen,
                toolsJson = if (toolJson.isNotEmpty()) normalized else null,
                onToken = onToken,
                onToolCall = onToolCalled
            ).getOrThrow()
        } finally {
            isGenerating.value = false
        }
    }

    private suspend fun generateGGUF(
        prompt: String,
        gen: GenerationParams,
        toolJson: String,
        onToolCalled: (String, String) -> Unit,
        onToken: (String) -> Unit
    ): String {
        val deferred = CompletableDeferred<String>()
        val normalized = ToolJsonUtils.normalizeSpec(toolJson)
        val deduped = ToolJsonUtils.maybeDedup(normalized)

        Log.e("GGUF", "Tool JSON: $toolJson")

        Log.e("GGUF", "Deduped: $deduped && $normalized")
        Log.e("GGUF", "${toolJson.isNotEmpty()}")

        queue.send(
            GenerationRequest.Streaming(
                prompt = prompt,
                gen = gen,
                onToken = onToken,
                toolJson = if (toolJson.isEmpty()) "" else deduped.toString(),
                onToolCalled = onToolCalled,
                completer = deferred
            )
        )
        return deferred.await()
    }

    fun stopGeneration() {
        val model = _currentModel.value
        when (model.providerName) {
            ModelProvider.OpenRouter.toString() -> openRouterExecutor?.stopGeneration()
            ModelProvider.LocalGGUF.toString() -> {
                service?.stopTextGeneration()
            }
        }
        isGenerating.value = false
    }

    //endregion

    //region Embedding-Generation
    suspend fun generateEmbeddings(input: String): FloatArray = withContext(Dispatchers.IO) {
        service ?: {
            Log.e(TAG, "Service not bound")
            FloatArray(0)
        }
        return@withContext service!!.embed(input)
    }
    //endregion

    //region Generation Queue Processor

    private fun startProcessor() {
        processorJob?.cancel()
        processorJob = scope.launch(genDispatcher) {
            queue.consumeAsFlow().collect { req ->
                try {
                    isGenerating.value = true
                    when (req) {
                        is GenerationRequest.Streaming -> handleStreaming(req)
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "Generation error", t)
                } finally {
                    isGenerating.value = false
                }
            }
        }
    }

    private fun handleStreaming(req: GenerationRequest.Streaming) {
        val svc = service ?: run {
            req.completer.completeExceptionally(RuntimeException("Service not bound"))
            return
        }

        svc.generateText(
            req.prompt, req.gen.maxTokens, req.toolJson, object : IGenerationCallback.Stub() {
                private val acc = StringBuilder()

                override fun onToken(token: String) {
                    req.onToken(token)
                    acc.append(token)
                }

                override fun onToolCall(name: String, args: String) {
                    req.onToolCalled(name, args)
                }

                override fun onDone() {
                    req.completer.complete(acc.toString())
                }

                override fun onError(error: String) {
                    req.completer.completeExceptionally(RuntimeException(error))
                }
            })
    }

    //endregion

    //region State Management

    suspend fun getStateSize(): Long = withContext(Dispatchers.IO) {
        service?.stateSize ?: 0L
    }

    suspend fun getStateData(): ByteArray? = withContext(Dispatchers.IO) {
        service?.stateData
    }

    suspend fun loadStateData(state: ByteArray): Boolean = withContext(Dispatchers.IO) {
        service?.loadStateData(state) ?: false
    }

    suspend fun saveStateFile(filePath: String): Boolean = withContext(Dispatchers.IO) {
        service?.saveStateFile(filePath) ?: false
    }

    suspend fun loadStateFile(filePath: String): Boolean = withContext(Dispatchers.IO) {
        service?.loadStateFile(filePath) ?: false
    }

    //endregion

    //region Configuration

    fun setSystemPrompt(prompt: String) {
        val model = _currentModel.value
        when (model.providerName) {
            ModelProvider.LocalGGUF.toString() -> service?.setSystemPrompt(prompt)
        }
        _currentModel.value = model.copy(systemPrompt = prompt)
    }

    fun setChatTemplate(template: String) {
        service?.setChatTemplate(template)
    }

    //endregion

    //region Database Operations

    fun observeModels(): Flow<List<ModelData>> {
        ensureDaoInitialized()
        return dao.getAllModels()
    }

    suspend fun addModel(model: ModelData) = withContext(Dispatchers.IO) {
        ensureDaoInitialized()
        dao.insertModel(model)
    }

    suspend fun updateModel(model: ModelData) = withContext(Dispatchers.IO) {
        ensureDaoInitialized()
        dao.updateModel(model)
    }

    suspend fun removeModel(modelName: String) = withContext(Dispatchers.IO) {
        ensureDaoInitialized()
        dao.getModelByName(modelName)?.let { dao.deleteModel(it) }
    }

    suspend fun checkIfInstalled(modelName: String): Boolean = withContext(Dispatchers.IO) {
        ensureDaoInitialized()
        dao.getModelByName(modelName) != null
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

    suspend fun getTTSModel(): ModelData? = withContext(Dispatchers.IO) {
        ensureDaoInitialized()
        dao.getTTSModel()
    }

    suspend fun getSTTModel(): ModelData? = withContext(Dispatchers.IO) {
        ensureDaoInitialized()
        dao.getSTTModel()
    }

    suspend fun isEmbeddingModelInstalled(): Boolean = withContext(Dispatchers.IO) {
        ensureDaoInitialized()
        getAllModels().firstOrNull()?.modelType == ModelType.EMBEDDING
    }

    suspend fun addEmbeddingModel(model: ModelData) = withContext(Dispatchers.IO) {
        ensureDaoInitialized()
        dao.insertModel(model.copy(modelType = ModelType.EMBEDDING))
    }

    private fun ensureDaoInitialized() {
        check(initGuard.get()) { "ModelManager.init(context) must be called first" }
    }

    //endregion

    //region Lifecycle

    fun isModelLoaded(): Boolean = _currentModel.value.modelName.isNotEmpty()

    fun shutdown() {
        queue.close()
        processorJob?.cancel()
        stopGeneration()
        unloadGenerationModel()

        genDispatcher.cancel()
        genExecutor.shutdown()

        openRouterExecutor = null
        unbindService()

        Log.i(TAG, "ModelManager shutdown complete")
    }

    //endregion

    //region Internal Types

    private sealed interface GenerationRequest {
        data class Streaming(
            val prompt: String,
            val gen: GenerationParams,
            val onToken: (String) -> Unit,
            val toolJson: String,
            val onToolCalled: (String, String) -> Unit,
            val completer: CompletableDeferred<String>
        ) : GenerationRequest
    }

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
                Log.e(TAG, "Invalid tool JSON", t)
                return null
            }

            for (i in 0 until root.length()) {
                val item = root.optJSONObject(i) ?: continue
                val func = item.optJSONObject("function") ?: continue

                // Truncate description
                func.optString("description").takeIf { it.length > MAX_DESC }?.let {
                    func.put("description", it.take(MAX_DESC))
                }

                // Normalize required array
                val params = func.optJSONObject("parameters") ?: continue
                val required = params.opt("required")
                if (required != null && required !is JSONArray) {
                    val arr = JSONArray()
                    when (required) {
                        is String -> arr.put(required)
                        is Iterable<*> -> required.forEach { if (it is String) arr.put(it) }
                    }
                    params.put("required", arr)
                }

                // Sort properties
                val props = params.optJSONObject("properties") ?: continue
                val ordered = JSONObject()
                props.keys().asSequence().sorted().forEach { key ->
                    ordered.put(key, props.get(key))
                }
                params.put("properties", ordered)
            }

            val txt = root.toString()
            if (txt.toByteArray().size > MAX_SPEC_BYTES) {
                Log.w(TAG, "Tool spec too large, dropping descriptions")
                for (i in 0 until root.length()) {
                    root.optJSONObject(i)?.optJSONObject("function")?.remove("description")
                }
                return root.toString()
            }
            return txt
        }

        fun maybeDedup(spec: String?): String? {
            if (spec == null) return null
            val hash = spec.hashCode()
            return if (lastHash == hash) spec else {
                lastHash = hash
                spec
            }
        }
    }

    //endregion
}