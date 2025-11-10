package com.dark.ai_module.workers

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import com.dark.ai_module.db.DatabaseProvider
import com.dark.ai_module.db.ModelDAO
import com.dark.ai_module.model.ModelData
import com.dark.ai_module.model.ModelType
import com.mp.ai_core.services.AudioService
import com.mp.ai_core.services.IAudioCallback
import com.mp.ai_core.services.IAudioService
import com.mp.ai_core.services.ISttCallback
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

@SuppressLint("StaticFieldLeak")
object AudioManager {

    private const val TAG = "AudioManager"

    // Service
    private var service: IAudioService? = null
    private var serviceBoundContext: Context? = null
    private val serviceConnection = AudioServiceConnection()

    // Database
    private val initGuard = AtomicBoolean(false)
    private lateinit var dao: ModelDAO

    // State
    private val _ttsModel = MutableStateFlow<ModelData?>(null)
    val ttsModel: StateFlow<ModelData?> = _ttsModel.asStateFlow()

    private val _sttModel = MutableStateFlow<ModelData?>(null)
    val sttModel: StateFlow<ModelData?> = _sttModel.asStateFlow()

    val isTtsGenerating = MutableStateFlow(false)
    val isSttProcessing = MutableStateFlow(false)

    // Queue
    private val ttsQueue = Channel<TtsRequest>(capacity = 32)
    private val sttQueue = Channel<SttRequest>(capacity = 32)
    private var ttsProcessorJob: Job? = null
    private var sttProcessorJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        startProcessors()
    }

    //region Initialization

    fun init(context: Context) {
        if (initGuard.compareAndSet(false, true)) {
            dao = DatabaseProvider.getDatabase(context).ModelDAO()
            bindService(context)
        }
    }

    //endregion

    //region Service Management

    private fun bindService(context: Context) {
        context.bindService(
            Intent(context, AudioService::class.java), serviceConnection, Context.BIND_AUTO_CREATE
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

    private class AudioServiceConnection : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder?) {
            service = IAudioService.Stub.asInterface(binder)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
            Log.w(TAG, "AudioService disconnected")
        }
    }

    //endregion

    //region TTS Management

    suspend fun loadTtsModel(modelData: ModelData): Result<Unit> = withContext(Dispatchers.IO) {
        val svc = service ?: return@withContext Result.failure(
            RuntimeException("Service not bound")
        )

        if (modelData.modelType != ModelType.TTS) {
            return@withContext Result.failure(
                IllegalArgumentException("Model is not TTS type")
            )
        }

        val modelFile = File(modelData.modelPath)
        if (!modelFile.exists()) {
            return@withContext Result.failure(
                IllegalArgumentException("Model not found: ${modelFile.absolutePath}")
            )
        }

        try {
            val modelDir = modelData.modelPath
            val modelName = "model.onnx"
            val voices = "voices.bin"
            val dataDir = "${modelData.modelPath}/espeak-ng-data"

            val ok = svc.initializeTts(modelDir, modelName, voices, dataDir)
            if (!ok) {
                return@withContext Result.failure(RuntimeException("TTS initialization failed"))
            }

            _ttsModel.value = modelData
            Result.success(Unit)
        } catch (t: Throwable) {
            Log.e(TAG, "TTS load failed", t)
            Result.failure(t)
        }
    }

    fun unloadTtsModel() {
        service?.releaseTts()
        _ttsModel.value = null
    }

    suspend fun generateTts(
        text: String,
        speakerId: Int = 0,
        onProgress: (Float) -> Unit = {},
        onAudioChunk: (FloatArray) -> Unit = {}
    ): Result<Unit> {
        val deferred = CompletableDeferred<Result<Unit>>()

        ttsQueue.send(
            TtsRequest(
                text = text,
                speakerId = speakerId,
                onProgress = onProgress,
                onAudioChunk = onAudioChunk,
                completer = deferred
            )
        )

        return deferred.await()
    }

    fun stopTts() {
        service?.stopTts()
        isTtsGenerating.value = false
    }

    fun getTtsSampleRate(): Int = service?.ttsSampleRate ?: 0

    fun getTtsNumSpeakers(): Int = service?.ttsNumSpeakers ?: 0

    fun isTtsReady(): Boolean = service?.isTtsReady ?: false

    //endregion

    //region STT Management

    suspend fun loadSttModel(modelData: ModelData): Result<Unit> = withContext(Dispatchers.IO) {
        val svc = service ?: return@withContext Result.failure(
            RuntimeException("Service not bound")
        )

        if (modelData.modelType != ModelType.STT) {
            return@withContext Result.failure(
                IllegalArgumentException("Model is not STT type")
            )
        }

        val modelFile = File(modelData.modelPath)
        if (!modelFile.exists()) {
            return@withContext Result.failure(
                IllegalArgumentException("Model not found: ${modelFile.absolutePath} | ${modelData.modelPath}")
            )
        }

        try {
            val ok = svc.initializeStt(modelData.modelPath, 2, 4)
            if (!ok) {
                return@withContext Result.failure(RuntimeException("STT initialization failed"))
            }

            _sttModel.value = modelData
            Result.success(Unit)
        } catch (t: Throwable) {
            Log.e(TAG, "STT load failed", t)
            Result.failure(t)
        }
    }

    fun unloadSttModel() {
        service?.releaseStt()
        _sttModel.value = null
    }

    suspend fun transcribeFile(
        filePath: String, sampleRate: Int = 16000
    ): Result<String> {
        val deferred = CompletableDeferred<Result<String>>()

        sttQueue.send(
            SttRequest.File(
                filePath = filePath, sampleRate = sampleRate, completer = deferred
            )
        )

        return deferred.await()
    }

    suspend fun transcribeSamples(
        samples: FloatArray, sampleRate: Int = 16000
    ): Result<String> {
        val deferred = CompletableDeferred<Result<String>>()

        sttQueue.send(
            SttRequest.Samples(
                samples = samples, sampleRate = sampleRate, completer = deferred
            )
        )

        return deferred.await()
    }

    fun isSttReady(): Boolean = service?.isSttReady ?: false

    fun getActiveStreamCount(): Int = service?.activeStreamCount ?: 0

    fun getCurrentModelType(): Int = service?.currentModelType ?: 0

    //endregion

    //region Queue Processors

    private fun startProcessors() {
        // TTS Processor
        ttsProcessorJob?.cancel()
        ttsProcessorJob = scope.launch {
            ttsQueue.consumeAsFlow().collect { req ->
                try {
                    isTtsGenerating.value = true
                    handleTtsRequest(req)
                } catch (t: Throwable) {
                    Log.e(TAG, "TTS generation error", t)
                    req.completer.complete(Result.failure(t))
                } finally {
                    isTtsGenerating.value = false
                }
            }
        }

        // STT Processor
        sttProcessorJob?.cancel()
        sttProcessorJob = scope.launch {
            sttQueue.consumeAsFlow().collect { req ->
                try {
                    isSttProcessing.value = true
                    when (req) {
                        is SttRequest.File -> handleSttFileRequest(req)
                        is SttRequest.Samples -> handleSttSamplesRequest(req)
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "STT processing error", t)
                    req.completer.complete(Result.failure(t))
                } finally {
                    isSttProcessing.value = false
                }
            }
        }
    }

    private suspend fun handleTtsRequest(req: TtsRequest) = withContext(Dispatchers.IO) {
        val svc = service ?: run {
            req.completer.complete(Result.failure(RuntimeException("Service not bound")))
            return@withContext
        }

        svc.generateTts(
            req.text, req.speakerId, object : IAudioCallback.Stub() {
                override fun onAudioChunk(samples: FloatArray) {
                    req.onProgress(0f)
                    req.onAudioChunk(samples)
                }

                override fun onComplete() {
                    req.completer.complete(Result.success(Unit))
                }

                override fun onError(error: String) {
                    req.completer.complete(Result.failure(RuntimeException(error)))
                }
            })
    }

    private suspend fun handleSttFileRequest(req: SttRequest.File) = withContext(Dispatchers.IO) {
        val svc = service ?: run {
            req.completer.complete(Result.failure(RuntimeException("Service not bound")))
            return@withContext
        }

        svc.transcribeFile(
            req.filePath, req.sampleRate, object : ISttCallback.Stub() {
                override fun onResult(text: String) {
                    req.completer.complete(Result.success(text))
                }

                override fun onError(error: String) {
                    req.completer.complete(Result.failure(RuntimeException(error)))
                }
            })
    }

    private suspend fun handleSttSamplesRequest(req: SttRequest.Samples) =
        withContext(Dispatchers.IO) {
            val svc = service ?: run {
                req.completer.complete(Result.failure(RuntimeException("Service not bound")))
                return@withContext
            }

            svc.transcribeSamples(
                req.samples, req.sampleRate, object : ISttCallback.Stub() {
                    override fun onResult(text: String) {
                        req.completer.complete(Result.success(text))
                    }

                    override fun onError(error: String) {
                        req.completer.complete(Result.failure(RuntimeException(error)))
                    }
                })
        }

    //endregion

    //region Database Operations

    suspend fun getTtsModels(): List<ModelData> = withContext(Dispatchers.IO) {
        ensureDaoInitialized()
        dao.getAllModels().firstOrNull()?.filter { it.modelType == ModelType.TTS } ?: emptyList()
    }

    suspend fun getSttModels(): List<ModelData> = withContext(Dispatchers.IO) {
        ensureDaoInitialized()
        dao.getAllModels().firstOrNull()?.filter { it.modelType == ModelType.STT } ?: emptyList()
    }

    suspend fun addAudioModel(model: ModelData) = withContext(Dispatchers.IO) {
        ensureDaoInitialized()
        dao.insertModel(model)
    }

    suspend fun updateAudioModel(model: ModelData) = withContext(Dispatchers.IO) {
        ensureDaoInitialized()
        dao.updateModel(model)
    }

    suspend fun removeAudioModel(modelName: String) = withContext(Dispatchers.IO) {
        ensureDaoInitialized()
        dao.getModelByName(modelName)?.let { dao.deleteModel(it) }
    }

    private fun ensureDaoInitialized() {
        check(initGuard.get()) { "AudioManager.init(context) must be called first" }
    }

    //endregion

    //region Info

    fun getAudioInfo(): String = service?.audioInfo ?: "Service not connected"

    //endregion

    //region Lifecycle

    fun shutdown() {
        ttsQueue.close()
        sttQueue.close()
        ttsProcessorJob?.cancel()
        sttProcessorJob?.cancel()

        stopTts()
        unloadTtsModel()
        unloadSttModel()

        unbindService()
        scope.cancel()

        Log.i(TAG, "AudioManager shutdown complete")
    }

    //endregion

    //region Internal Types

    private data class TtsRequest(
        val text: String,
        val speakerId: Int,
        val onProgress: (Float) -> Unit,
        val onAudioChunk: (FloatArray) -> Unit,
        val completer: CompletableDeferred<Result<Unit>>
    )

    private sealed interface SttRequest {
        val completer: CompletableDeferred<Result<String>>

        data class File(
            val filePath: String,
            val sampleRate: Int,
            override val completer: CompletableDeferred<Result<String>>
        ) : SttRequest

        data class Samples(
            val samples: FloatArray,
            val sampleRate: Int,
            override val completer: CompletableDeferred<Result<String>>
        ) : SttRequest {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as Samples

                if (!samples.contentEquals(other.samples)) return false
                if (sampleRate != other.sampleRate) return false
                if (completer != other.completer) return false

                return true
            }

            override fun hashCode(): Int {
                var result = samples.contentHashCode()
                result = 31 * result + sampleRate
                result = 31 * result + completer.hashCode()
                return result
            }
        }
    }

    //endregion
}