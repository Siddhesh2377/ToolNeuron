package com.dark.ai_module.ai

import android.content.Context
import android.util.Log
import com.mp.ai_core.NativeLib
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Neuron — a small, scalable manager around [NativeLib].
 *
 * Key goals:
 *  - Single-queue, serialized generations (no interleaving)
 *  - Clean model lifecycle (load/switch/unload)
 *  - Safe cancellation via nativeStopGeneration + coroutine Job cancel
 *  - Streaming + blocking APIs
 *  - Sensible defaults via data classes
 */
object Neuron {

    /* ------------------------------------------------------------- */
    /*  Types & params                                               */
    /* ------------------------------------------------------------- */

    private const val TAG = "Neuron"

    /** Defaults for model init. */
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

    /** Per-request decoding params. */
    data class GenerationParams(
        val maxTokens: Int = 512,
    )

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
            val completer: CompletableDeferred<String>
        ) : Request
    }

    private data class Variant(
        val path: File,
        val lib: NativeLib,
        val init: ModelInitParams,
        val loadJob: Job?,
    )

    /* ------------------------------------------------------------- */
    /*  State                                                        */
    /* ------------------------------------------------------------- */

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val models = ConcurrentHashMap<String, Variant>() // key = absolutePath
    private var activeModelId: String? = null

    /** Emits true while *any* generation is running. */
    val isGenerating = MutableStateFlow(false)

    /** Broadcasts every completed assistant reply (fire-and-forget API). */
    val replies = MutableSharedFlow<String>(extraBufferCapacity = 16)

    /** Back-pressure aware queue; keeps RAM stable under load. */
    private val queue = Channel<Request>(capacity = 64)

    /** the current native generation job, for cancellation */
    @Volatile private var currentGenJob: Job? = null

    /** Dispatcher for delivering tokens to UI; override if needed. */
    var tokenDispatcher: CoroutineDispatcher = Dispatchers.Main

    init { startProcessor() }

    /* ------------------------------------------------------------- */
    /*  Model lifecycle                                              */
    /* ------------------------------------------------------------- */

    fun loadModel(
        path: File,
        init: ModelInitParams = ModelInitParams(),
        forceReload: Boolean = false,
        onLoaded: (() -> Unit)? = null,
    ) {
        require(path.exists()) { "Model file missing at: ${path.path}" }
        val id = path.absolutePath

        if (!forceReload && models.containsKey(id)) {
            activeModelId = id
            onLoaded?.invoke()
            return
        }

        // One-active-model policy keeps native memory simple & predictable.
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
                withContext(Dispatchers.Main) { onLoaded?.invoke() }
            }.onFailure {
                Log.e(TAG, "Model load failed", it)
                lib.nativeRelease()
            }
        }

        models[id] = Variant(path, lib, init, job)
        activeModelId = id
        Log.d(TAG, "Loaded model ${path.name} (${path.length()} bytes)")
    }

    fun unloadActiveModel() {
        activeModelId?.let { id ->
            models.remove(id)?.lib?.nativeRelease()
        }
        activeModelId = null
        isGenerating.value = false
    }

    fun unloadAllModels() {
        models.values.forEach { it.lib.nativeRelease() }
        models.clear()
        activeModelId = null
        isGenerating.value = false
    }

    fun stopGeneration() {
        currentGenJob?.cancel()
        activeModel()?.lib?.nativeStopGeneration()
        isGenerating.value = false
    }

    fun listLoadedModels(): List<String> = models.keys.toList()

    /* ------------------------------------------------------------- */
    /*  Public API — enqueue generation                              */
    /* ------------------------------------------------------------- */

    /** Fire‑and‑forget; observe results on [replies]. */
    suspend fun enqueuePrompt(prompt: String, gen: GenerationParams = GenerationParams()) {
        val def = CompletableDeferred<String>()
        queue.send(Request.Blocking(prompt, gen, def))
        // ignore result on purpose
    }

    /** Queue prompt and await full reply. */
    suspend fun generateAndWait(prompt: String, gen: GenerationParams = GenerationParams()): String {
        val def = CompletableDeferred<String>()
        queue.send(Request.Blocking(prompt, gen, def))
        return def.await()
    }
    /** Queue a streaming request; returns the final reply. */
    suspend fun generateStreaming(
        prompt: String,
        gen: GenerationParams = GenerationParams(),
        onToken: (String) -> Unit,
    ): String {
        val def = CompletableDeferred<String>()
        queue.send(Request.Streaming(prompt, gen, onToken, def))
        return def.await()
    }

    /* ------------------------------------------------------------- */
    /*  Processor — single consumer; serializes native calls         */
    /* ------------------------------------------------------------- */

    private fun startProcessor() {
        scope.launch {
            queue.consumeEach { req ->
                try {
                    isGenerating.value = true
                    when (req) {
                        is Request.Blocking  -> handleBlocking(req)
                        is Request.Streaming -> handleStreaming(req)
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "Generation error", t)
                    when (req) {
                        is Request.Blocking  -> req.completer.completeExceptionally(t)
                        is Request.Streaming -> req.completer.completeExceptionally(t)
                    }
                } finally {
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
            uiScope = scope,
            onStart = {},
            onGenerate = { tok ->
                acc.append(tok)
            },
            onError = { msg ->
                done.completeExceptionally(IllegalStateException(msg))
            },
            onDone = {
                done.complete(Unit)
            }
        )
        // Wait for native side to signal completion
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
            uiScope = scope,
            onStart = {},
            onGenerate = { tok ->
                acc.append(tok)
                // Deliver tokens on the configured dispatcher (Main by default)
                scope.launch(tokenDispatcher) { r.onToken(tok) }
            },
            onError = { msg ->
                done.completeExceptionally(IllegalStateException(msg))
            },
            onDone = {
                done.complete(Unit)
            }
        )
        done.await()
        val reply = acc.toString().trim()
        r.completer.complete(reply)
        replies.tryEmit(reply)
    }

    /* ------------------------------------------------------------- */
    /*  Helpers                                                      */
    /* ------------------------------------------------------------- */

    private fun activeModel(): Variant? = activeModelId?.let { models[it] }

    private fun activeModelOrThrow(): Variant =
        activeModel() ?: error("No active model selected. Call loadModel() first.")
}
