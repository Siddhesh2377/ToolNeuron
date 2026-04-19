package com.dark.tool_neuron.network

import android.util.Log
import com.dark.tool_neuron.engine.DiffusionEngine
import com.dark.tool_neuron.engine.GGUFEngine
import com.dark.tool_neuron.engine.GenerationEvent
import com.dark.tool_neuron.models.enums.ProviderType
import com.dark.tool_neuron.repo.ModelRepository
import com.dark.tool_neuron.state.AppStateManager
import io.ktor.http.CacheControl
import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.staticResources
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.get
import io.ktor.server.routing.options
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RemoteServer @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val modelRepository: ModelRepository
) {

    private var serverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private val json = Json {
        prettyPrint = false
        isLenient = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private var ggufEngine: GGUFEngine? = null
    private var diffusionEngine: DiffusionEngine? = null

    fun setEngines(gguf: GGUFEngine, diffusion: DiffusionEngine) {
        this.ggufEngine = gguf
        this.diffusionEngine = diffusion
    }

    fun start(port: Int = 11434) {
        if (serverJob?.isActive == true) return

        serverJob = scope.launch {
            try {
                embeddedServer(Netty, port = port) {
                    install(ContentNegotiation) {
                        json(json)
                    }
                    install(CORS) {
                        anyHost()
                        allowHeader(HttpHeaders.ContentType)
                        allowHeader(HttpHeaders.Authorization)
                        allowMethod(HttpMethod.Options)
                        allowMethod(HttpMethod.Get)
                        allowMethod(HttpMethod.Post)
                    }
                    routing {
                        get("/") {
                            try {
                                val content = context.assets.open("webui/index.html").bufferedReader().use { it.readText() }
                                call.respondText(content, ContentType.Text.Html)
                            } catch (e: Exception) {
                                Log.e("RemoteServer", "Error serving index.html", e)
                                call.respondText("Web UI not found", status = io.ktor.http.HttpStatusCode.InternalServerError)
                            }
                        }

                        get("/app.js") {
                            try {
                                val content = context.assets.open("webui/app.js").bufferedReader().use { it.readText() }
                                call.respondText(content, ContentType.parse("application/javascript"))
                            } catch (e: Exception) {
                                call.respondText("JS not found", status = io.ktor.http.HttpStatusCode.NotFound)
                            }
                        }

                        get("/style.css") {
                            try {
                                val content = context.assets.open("webui/style.css").bufferedReader().use { it.readText() }
                                call.respondText(content, ContentType.Text.CSS)
                            } catch (e: Exception) {
                                call.respondText("CSS not found", status = io.ktor.http.HttpStatusCode.NotFound)
                            }
                        }

                        get("/v1/chat-models") {
                            Log.d("RemoteServer", "GET /v1/chat-models")
                            val models = modelRepository.getModelsByProvider(ProviderType.GGUF).first()
                            val response = ModelsResponse(
                                data = models.map { model ->
                                    ModelData(
                                        id = model.modelName,
                                        created = System.currentTimeMillis() / 1000
                                    )
                                }
                            )
                            call.respond(response)
                        }

                        get("/api/v1") {
                            Log.d("RemoteServer", "GET /api/v1")
                            call.respond(mapOf(
                                "version" to "2.1.0",
                                "name" to "ToolNeuron",
                                "organization" to "ToolNeuron",
                                "tags" to listOf("text-generation", "image-generation"),
                                "models_endpoint" to "/v1/models"
                            ))
                        }

                        get("/v1") {
                            Log.d("RemoteServer", "GET /v1")
                            call.respond(mapOf(
                                "version" to "2.1.0",
                                "name" to "ToolNeuron",
                                "models_endpoint" to "/v1/models"
                            ))
                        }

                        get("/models") {
                            Log.d("RemoteServer", "GET /models")
                            try {
                                val models = modelRepository.getAllModels().first()
                                val response = ModelsResponse(
                                    data = models.map { model ->
                                        ModelData(
                                            id = model.modelName,
                                            root = model.modelName,
                                            parent = null,
                                            created = System.currentTimeMillis() / 1000
                                        )
                                    }
                                )
                                call.respond(response)
                            } catch (e: Exception) {
                                Log.e("RemoteServer", "/models endpoint error", e)
                                call.respondText("Error fetching models: ${e.message}", status = io.ktor.http.HttpStatusCode.InternalServerError)
                            }
                        }

                        get("/v1/models") {
                            Log.d("RemoteServer", "GET /v1/models")
                            try {
                                val models = modelRepository.getAllModels().first()
                                val response = ModelsResponse(
                                    data = models.map { model ->
                                        ModelData(
                                            id = model.modelName,
                                            root = model.modelName,
                                            parent = null,
                                            created = System.currentTimeMillis() / 1000
                                        )
                                    }
                                )
                                call.respond(response)
                            } catch (e: Exception) {
                                Log.e("RemoteServer", "/v1/models endpoint error", e)
                                call.respondText("Error fetching models: ${e.message}", status = io.ktor.http.HttpStatusCode.InternalServerError)
                            }
                        }

                        get("/v1/image-models") {
                            Log.d("RemoteServer", "GET /v1/image-models")
                            val models = modelRepository.getModelsByProvider(ProviderType.DIFFUSION).first()
                            val response = ImageModelsResponse(
                                data = models.map { model ->
                                    ImageModelData(
                                        id = model.modelName,
                                        created = System.currentTimeMillis() / 1000
                                    )
                                }
                            )
                            call.respond(response)
                        }

                        get("/api/ps") {
                            Log.d("RemoteServer", "GET /api/ps")
                            val runningModels = mutableListOf<RunningModel>()

                            // Check GGUF Engine
                            ggufEngine?.let { engine ->
                                if (engine.isLoaded) {
                                    val modelId = engine.getCurrentModelId()
                                    if (modelId != null) {
                                        val model = modelRepository.getModelById(modelId)
                                        if (model != null) {
                                            val infoJson = engine.getModelInfo()
                                            var quantization = "unknown"
                                            var family = "unknown"
                                            
                                            if (infoJson != null) {
                                                try {
                                                    val json = org.json.JSONObject(infoJson)
                                                    quantization = json.optString("quantization", "unknown")
                                                    family = json.optString("architecture", "unknown")
                                                } catch (_: Exception) {}
                                            }

                                            runningModels.add(RunningModel(
                                                name = model.modelName,
                                                model = model.modelName,
                                                size = model.fileSize ?: 0L,
                                                details = RunningModelDetails(
                                                    format = "gguf",
                                                    family = family,
                                                    quantization_level = quantization
                                                ),
                                                sizeVram = model.fileSize ?: 0L, // Approximation
                                                status = AppStateManager.getModelStatus(model.modelName),
                                                error = AppStateManager.getModelError(model.modelName)
                                            ))
                                        }
                                    }
                                }
                            }

                            // Check Diffusion Engine
                            diffusionEngine?.let { engine ->
                                val currentModel = engine.getCurrentModel()
                                if (currentModel != null) {
                                    runningModels.add(RunningModel(
                                        name = currentModel.name,
                                        model = currentModel.name,
                                        size = 2000000000L, // Placeholder approx size
                                        details = RunningModelDetails(
                                            format = "diffusers",
                                            family = "stable-diffusion",
                                            backend = if (currentModel.runOnCpu) "cpu" else "npu"
                                        ),
                                        sizeVram = 2000000000L,
                                        status = AppStateManager.getModelStatus(currentModel.name),
                                        error = AppStateManager.getModelError(currentModel.name)
                                    ))
                                }
                            }

                            val hardware = com.dark.tool_neuron.global.HardwareScanner.scan(context)
                            call.respond(ProcessResponse(models = runningModels, hardware = hardware))
                        }

                        post("/v1/chat/completions") {
                            val request = call.receive<ChatCompletionRequest>()
                            Log.d("RemoteServer", "POST /v1/chat/completions: $request")
                            val engine = ggufEngine ?: return@post call.respondText("Engine not initialized", status = io.ktor.http.HttpStatusCode.InternalServerError)
                            
                            try {
                                // Dynamic model loading for GGUF
                                if (request.model != null && request.model != "toolneuron-local") {
                                    val model = modelRepository.getModelByName(request.model)
                                    if (model != null && !engine.isModelLoaded(model.id)) {
                                        val config = modelRepository.getConfigByModelId(model.id)
                                        Log.i("RemoteServer", "Dynamically loading GGUF model: ${model.modelName}")
                                        
                                        AppStateManager.setLoadingModel(model.modelName)
                                        val loadResult = engine.load(model, config)
                                        
                                        loadResult.onFailure { error ->
                                            val errorMsg = error.message ?: "Unknown error"
                                            AppStateManager.setError("Failed to load model ${model.modelName}: $errorMsg")
                                            return@post call.respondText("Failed to load model: $errorMsg", status = io.ktor.http.HttpStatusCode.InternalServerError)
                                        }
                                        
                                        AppStateManager.setModelLoaded(model.modelName)
                                    }
                                }

                                if (!engine.isLoaded) {
                                    return@post call.respondText("No model loaded", status = io.ktor.http.HttpStatusCode.BadRequest)
                                }

                                val modelName = engine.getModelInfo() ?: "GGUF"
                                AppStateManager.setApiCallStatus(true, "Chat Completion", modelName)
                                AppStateManager.setGeneratingText()

                                val messagesJson = json.encodeToString(request.messages)
                                val maxTokens = request.maxTokens ?: 512

                                if (request.stream) {
                                    call.response.header(io.ktor.http.HttpHeaders.ContentType, ContentType.Text.EventStream.toString())
                                    call.response.header(io.ktor.http.HttpHeaders.CacheControl, CacheControl.NoCache(null).toString())
                                    
                                    val streamId = "chatcmpl-${UUID.randomUUID()}"
                                    val createdTime = System.currentTimeMillis() / 1000

                                    call.respondTextWriter(contentType = ContentType.Text.EventStream) {
                                        // 1. Send the initial role chunk
                                        val firstChunk = ChatCompletionResponse(
                                            id = streamId,
                                            obj = "chat.completion.chunk",
                                            created = createdTime,
                                            model = "toolneuron-local",
                                            choices = listOf(ChatChoice(index = 0, delta = ChatCompletionDelta(role = "assistant", content = "")))
                                        )
                                        write("data: ${json.encodeToString(firstChunk)}\n\n")
                                        flush()

                                        engine.generateMultiTurnFlow(messagesJson, maxTokens).collect { event ->
                                            when (event) {
                                                is GenerationEvent.Token -> {
                                                    val chunk = ChatCompletionResponse(
                                                        id = streamId,
                                                        obj = "chat.completion.chunk",
                                                        created = createdTime,
                                                        model = "toolneuron-local",
                                                        choices = listOf(ChatChoice(index = 0, delta = ChatCompletionDelta(content = event.text)))
                                                    )
                                                    write("data: ${json.encodeToString(chunk)}\n\n")
                                                    flush()
                                                }
                                                is GenerationEvent.Metrics -> {
                                                    val perfChunk = ChatCompletionResponse(
                                                        id = streamId,
                                                        obj = "chat.completion.chunk",
                                                        created = createdTime,
                                                        model = "toolneuron-local",
                                                        choices = emptyList(),
                                                        performance = PerformanceMetrics(
                                                            totalTimeMs = event.metrics.totalTimeMs,
                                                            tokensPerSecond = event.metrics.tokensPerSecond,
                                                            promptTokens = event.metrics.tokensEvaluated,
                                                            completionTokens = event.metrics.tokensPredicted
                                                        )
                                                    )
                                                    write("data: ${json.encodeToString(perfChunk)}\n\n")
                                                    flush()
                                                }
                                                is GenerationEvent.Done -> {
                                                    val lastChunk = ChatCompletionResponse(
                                                        id = streamId,
                                                        obj = "chat.completion.chunk",
                                                        created = createdTime,
                                                        model = "toolneuron-local",
                                                        choices = listOf(ChatChoice(index = 0, delta = ChatCompletionDelta(content = ""), finishReason = "stop"))
                                                    )
                                                    write("data: ${json.encodeToString(lastChunk)}\n\n")
                                                    write("data: [DONE]\n\n")
                                                    flush()
                                                }
                                                is GenerationEvent.Error -> {
                                                    write("data: {\"error\": \"${event.message}\"}\n\n")
                                                    flush()
                                                }
                                                else -> {}
                                            }
                                        }
                                    }
                                } else {
                                    val fullResponse = StringBuilder()
                                    var promptTokens = 0
                                    var completionTokens = 0
                                    var totalTimeMs = 0f
                                    var tokensPerSecond = 0f
                                    
                                    engine.generateMultiTurnFlow(messagesJson, maxTokens).collect { event ->
                                        when (event) {
                                            is GenerationEvent.Token -> {
                                                fullResponse.append(event.text)
                                            }
                                            is GenerationEvent.Metrics -> {
                                                promptTokens = event.metrics.tokensEvaluated
                                                completionTokens = event.metrics.tokensPredicted
                                                totalTimeMs = event.metrics.totalTimeMs
                                                tokensPerSecond = event.metrics.tokensPerSecond
                                            }
                                            else -> {}
                                        }
                                    }

                                    call.respond(ChatCompletionResponse(
                                        id = "chatcmpl-${UUID.randomUUID()}",
                                        created = System.currentTimeMillis() / 1000,
                                        model = "toolneuron-local",
                                        choices = listOf(ChatChoice(index = 0, message = ChatCompletionMessage("assistant", fullResponse.toString()), finishReason = "stop")),
                                        usage = Usage(promptTokens, completionTokens, promptTokens + completionTokens),
                                        performance = PerformanceMetrics(
                                            totalTimeMs = totalTimeMs,
                                            tokensPerSecond = tokensPerSecond,
                                            promptTokens = promptTokens,
                                            completionTokens = completionTokens
                                        )
                                    ))
                                }
                            } finally {
                                AppStateManager.setGenerationComplete()
                                AppStateManager.setApiCallStatus(false)
                            }
                        }

                        post("/v1/images/generations") {
                            val request = call.receive<ImageGenerationRequest>()
                            val engine = diffusionEngine ?: return@post call.respondText("Diffusion engine not initialized", status = io.ktor.http.HttpStatusCode.InternalServerError)
                            
                            AppStateManager.setApiCallStatus(true, "Image Generation", request.model ?: "Diffusion")

                            try {
                                // Load specified model if provided
                                if (request.model != null) {
                                    val currentModel = engine.getCurrentModel()
                                    if (currentModel?.name != request.model) {
                                        val model = modelRepository.getModelByName(request.model)
                                        if (model == null) {
                                            return@post call.respondText("Model not found: ${request.model}", status = io.ktor.http.HttpStatusCode.BadRequest)
                                        }

                                        val config = modelRepository.getConfigByModelId(model.id)
                                        val diffusionConfig = com.dark.tool_neuron.worker.DiffusionConfig.fromJson(config?.modelLoadingParams)
                                        
                                        Log.i("RemoteServer", "Dynamically loading model: ${model.modelName} with config: $diffusionConfig")
                                        AppStateManager.setLoadingModel(model.modelName)

                                        // NUCLEAR HARD RESET: Destroy engine, purge cache, and re-init (fixes QNN session exhaustion)
                                        engine.hardReset()
                                        kotlinx.coroutines.delay(2000) 
                                        engine.init(context)
                                        kotlinx.coroutines.delay(1000)
                                        
                                        val loadResult = engine.loadModel(
                                            name = model.modelName,
                                            modelDir = model.modelPath,
                                            textEmbeddingSize = diffusionConfig.textEmbeddingSize,
                                            runOnCpu = diffusionConfig.runOnCpu,
                                            useCpuClip = diffusionConfig.useCpuClip,
                                            isPony = diffusionConfig.isPony,
                                            safetyMode = diffusionConfig.safetyMode,
                                            width = diffusionConfig.width,
                                            height = diffusionConfig.height
                                        )
                                        
                                        if (loadResult.isFailure) {
                                            val error = loadResult.exceptionOrNull()?.message ?: "Unknown error"
                                            AppStateManager.setError(error)
                                            return@post call.respondText("Failed to load model: $error", status = io.ktor.http.HttpStatusCode.InternalServerError)
                                        }
                                        AppStateManager.setModelLoaded(model.modelName)
                                    } else {
                                        Log.i("RemoteServer", "Model ${request.model} already loaded")
                                    }
                                }
                                
                                val sizeParts = request.size.split("x")
                                val width = sizeParts.getOrNull(0)?.toIntOrNull() ?: 512
                                val height = sizeParts.getOrNull(1)?.toIntOrNull() ?: 512
                                val steps = request.steps ?: 20

                                AppStateManager.setApiCallStatus(true, "Image Generation", request.model ?: "Diffusion", "Steps: $steps")
                                AppStateManager.setGeneratingImage()
                                engine.generateImage(
                                    prompt = request.prompt,
                                    width = width,
                                    height = height,
                                    steps = steps
                                )

                                // Wait for completion via flow
                                var resultB64: String? = null
                                var errorMsg: String? = null
                                
                                val job = launch {
                                    engine.generationState.collect { state ->
                                        if (state is com.dark.ai_sd.DiffusionGenerationState.Complete) {
                                            resultB64 = engine.bitmapToBase64(state.bitmap)
                                            cancel()
                                        } else if (state is com.dark.ai_sd.DiffusionGenerationState.Error) {
                                            errorMsg = state.message
                                            cancel()
                                        }
                                    }
                                }
                                job.join()

                                if (resultB64 != null) {
                                    call.respond(ImageResponse(
                                        created = System.currentTimeMillis() / 1000,
                                        data = listOf(ImageData(b64Json = resultB64))
                                    ))
                                } else {
                                    call.respond(io.ktor.http.HttpStatusCode.InternalServerError, mapOf("error" to (errorMsg ?: "Unknown error")))
                                }
                            } finally {
                                AppStateManager.setGenerationComplete()
                                AppStateManager.setApiCallStatus(false)
                            }
                        }

                        get("/health") {
                            call.respond(mapOf(
                                "status" to "ok",
                                "version" to "2.1.0"
                            ))
                        }

                        get("/healthz") {
                            call.respond(mapOf(
                                "status" to "ok"
                            ))
                        }

                        get("/v1/models/{model_id}") {
                            val modelId = call.parameters["model_id"]
                            if (modelId == null) {
                                call.respondText("Model ID required", status = io.ktor.http.HttpStatusCode.BadRequest)
                                return@get
                            }
                            
                            val model = modelRepository.getModelByName(modelId)
                            if (model == null) {
                                call.respondText("Model not found: $modelId", status = io.ktor.http.HttpStatusCode.NotFound)
                                return@get
                            }

                            call.respond(mapOf(
                                "id" to model.modelName,
                                "object" to "model",
                                "created" to System.currentTimeMillis() / 1000,
                                "owned_by" to "toolneuron-local",
                                "permission" to emptyList<String>(),
                                "root" to model.modelName,
                                "parent" to null
                            ))
                        }

                        post("/v1/embeddings") {
                            call.respondText("Embeddings endpoint not yet implemented", status = io.ktor.http.HttpStatusCode.NotImplemented)
                        }

                        options("/v1/chat/completions") {
                            call.response.header("Allow", "POST, OPTIONS")
                            call.respondText("")
                        }

                        options("/v1/images/generations") {
                            call.response.header("Allow", "POST, OPTIONS")
                            call.respondText("")
                        }

                        options("/v1/models") {
                            call.response.header("Allow", "GET, OPTIONS")
                            call.respondText("")
                        }
                    }
                }.start(wait = true)
            } catch (e: Exception) {
                Log.e("RemoteServer", "Failed to start server", e)
            }
        }
        Log.i("RemoteServer", "Server started on port $port")
    }

    fun stop() {
        serverJob?.cancel()
        serverJob = null
        Log.i("RemoteServer", "Server stopped")
    }

    fun isRunning(): Boolean = serverJob?.isActive == true
}