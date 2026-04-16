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
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
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
    private val modelRepository: ModelRepository
) {

    private var serverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private val json = Json {
        prettyPrint = true
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
                    routing {
                        get("/") {
                            Log.d("RemoteServer", "GET /")
                            call.respondText("ToolNeuron API is running")
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

                        post("/v1/chat/completions") {
                            val request = call.receive<ChatCompletionRequest>()
                            Log.d("RemoteServer", "POST /v1/chat/completions: $request")
                            val engine = ggufEngine ?: return@post call.respondText("Engine not initialized", status = io.ktor.http.HttpStatusCode.InternalServerError)
                            
                            val modelName = engine.getModelInfo() ?: "GGUF"
                            AppStateManager.setApiCallStatus(true, "Chat Completion", modelName)

                            try {
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
                                    
                                    engine.generateMultiTurnFlow(messagesJson, maxTokens).collect { event ->
                                        when (event) {
                                            is GenerationEvent.Token -> {
                                                fullResponse.append(event.text)
                                            }
                                            is GenerationEvent.Metrics -> {
                                                promptTokens = event.metrics.tokensEvaluated
                                                completionTokens = event.metrics.tokensPredicted
                                            }
                                            else -> {}
                                        }
                                    }

                                    call.respond(ChatCompletionResponse(
                                        id = "chatcmpl-${UUID.randomUUID()}",
                                        created = System.currentTimeMillis() / 1000,
                                        model = "toolneuron-local",
                                        choices = listOf(ChatChoice(index = 0, message = ChatCompletionMessage("assistant", fullResponse.toString()), finishReason = "stop")),
                                        usage = Usage(promptTokens, completionTokens, promptTokens + completionTokens)
                                    ))
                                }
                            } finally {
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

                                engine.generateImage(
                                    prompt = request.prompt,
                                    width = width,
                                    height = height
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