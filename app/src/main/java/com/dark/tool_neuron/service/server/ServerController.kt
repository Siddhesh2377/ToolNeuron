package com.dark.tool_neuron.service.server

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Base64
import android.util.Log
import com.dark.native_server.BindMode
import com.dark.tool_neuron.data.AppPreferences
import com.dark.tool_neuron.model.ModelInfo
import com.dark.tool_neuron.model.enums.PathType
import com.dark.tool_neuron.model.enums.ProviderType
import com.dark.tool_neuron.repo.ModelRepository
import com.dark.tool_neuron.util.VlmPaths
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServerController @Inject constructor(
    @ApplicationContext private val app: Context,
    private val prefs: AppPreferences,
    private val modelRepo: ModelRepository,
) {

    private val _state = MutableStateFlow<ServerState>(ServerState.Stopped)
    val state: StateFlow<ServerState> = _state.asStateFlow()

    private val _requestEvents = MutableStateFlow<List<ServerRequestEvent>>(emptyList())
    val requestEvents: StateFlow<List<ServerRequestEvent>> = _requestEvents.asStateFlow()

    val isRunning: Boolean get() = _state.value is ServerState.Running

    val isBusy: Boolean
        get() = _state.value is ServerState.Running ||
                _state.value is ServerState.Starting ||
                _state.value is ServerState.LoadingModel

    @Volatile private var stub: IRemoteServerService? = null
    @Volatile private var bound = false

    private val callback = object : IRemoteServerCallback.Stub() {
        override fun onStateChanged(snapshotJson: String) {
            applySnapshot(snapshotJson)
        }
        override fun onRequestEvent(eventJson: String) {
            val o = runCatching { JSONObject(eventJson) }.getOrNull() ?: return
            val evt = ServerRequestEvent(
                timestampMs = o.optLong("ts_ms"),
                method      = o.optString("method"),
                path        = o.optString("path"),
                status      = o.optInt("status"),
                durationMs  = o.optLong("duration_ms"),
                client      = o.optString("client"),
            )
            _requestEvents.update { existing -> (listOf(evt) + existing).take(MAX_EVENT_HISTORY) }
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val s = IRemoteServerService.Stub.asInterface(service)
            stub = s
            bound = true
            try {
                s.registerCallback(callback)
                applySnapshot(s.currentSnapshotJson())
            } catch (e: Exception) {
                Log.w(TAG, "register failed", e)
            }
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            stub = null
            bound = false
        }
        override fun onBindingDied(name: ComponentName?) {
            stub = null
            bound = false
            tryBind()
        }
        override fun onNullBinding(name: ComponentName?) {
            stub = null
            bound = false
        }
    }

    init {
        tryBind()
    }

    private fun tryBind() {
        if (bound) return
        val intent = Intent(app, RemoteServerService::class.java)
        try {
            app.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            Log.e(TAG, "bind failed", e)
        }
    }

    fun start() {
        if (isBusy) return
        val installed = modelRepo.models.value
        val engines = buildEnginesCatalog(installed)
        if (engines.length() == 0) {
            _state.value = ServerState.Failed("install at least one chat / VLM / embedding / voice / image model before starting the server")
            return
        }

        _state.value = ServerState.Starting
        ensureBoundThen { s ->
            val cfg = JSONObject().apply {
                put("engines", engines)
                put("token", ensureToken())
                put("port", prefs.serverPort)
                put("bindMode", prefs.serverBindMode)
                put("webUiHtml", loadAsset("server_webui.html"))
                put("docsHtml", loadAsset("server_docs.html"))
            }
            try { s.start(cfg.toString()) }
            catch (e: Exception) {
                Log.e(TAG, "start failed", e)
                _state.value = ServerState.Failed(e.message ?: "start failed")
            }
        }
    }

    fun stop() {
        ensureBoundThen { s ->
            try { s.stop() } catch (e: Exception) { Log.w(TAG, "stop failed", e) }
        }
    }

    fun ensureToken(): String {
        val existing = prefs.serverToken
        if (existing.isNotBlank()) return existing
        val fresh = generateToken()
        prefs.serverToken = fresh
        return fresh
    }

    fun rotateToken(): String {
        val fresh = generateToken()
        prefs.serverToken = fresh
        stub?.let {
            try { it.rotateToken(fresh) } catch (_: Exception) {}
        }
        return fresh
    }

    fun currentToken(): String = prefs.serverToken

    fun selectedModelId(): String = prefs.serverSelectedModelId
    fun setSelectedModelId(modelId: String) { prefs.serverSelectedModelId = modelId }
    fun setChatDefaultModelId(modelId: String) {
        prefs.serverSelectedModelId = modelId
        val obj = runCatching { JSONObject(prefs.serverRoleDefaultsJson) }.getOrDefault(JSONObject())
        if (modelId.isBlank()) obj.remove(ServerEngineKind.CHAT_GGUF.token)
        else obj.put(ServerEngineKind.CHAT_GGUF.token, modelId)
        prefs.serverRoleDefaultsJson = obj.toString()
    }

    fun port(): Int = prefs.serverPort
    fun bindMode(): BindMode =
        runCatching { BindMode.valueOf(prefs.serverBindMode) }.getOrDefault(BindMode.ALL_INTERFACES)

    fun setPort(port: Int) { prefs.serverPort = port.coerceIn(1024, 65535) }
    fun setBindMode(mode: BindMode) { prefs.serverBindMode = mode.name }

    fun markConfigured() { prefs.serverConfigured = true }
    val isConfigured: Boolean get() = prefs.serverConfigured

    private fun buildEnginesCatalog(installed: List<ModelInfo>): JSONArray {
        val out = JSONArray()
        val modelsRoot = modelRepo.getModelsDir()
        val now = System.currentTimeMillis() / 1000
        val roleOverrides = readServerModelRoles()
        val roleDefaults = readServerRoleDefaults()

        installed.forEach { m ->
            if (m.pathType != PathType.FILE) return@forEach
            if (m.path.isBlank()) return@forEach
            val role = roleOverrides[m.id] ?: ServerModelRole.AUTO
            if (role == ServerModelRole.DISABLED) return@forEach

            val kind = when (role) {
                ServerModelRole.CHAT -> ServerEngineKind.CHAT_GGUF
                ServerModelRole.VLM -> ServerEngineKind.VLM
                ServerModelRole.EMBEDDING -> ServerEngineKind.EMBEDDING
                ServerModelRole.TTS -> ServerEngineKind.TTS
                ServerModelRole.STT -> ServerEngineKind.STT
                ServerModelRole.IMAGE_GEN -> ServerEngineKind.IMAGE_GEN
                ServerModelRole.IMAGE_UPSCALER -> ServerEngineKind.IMAGE_UPSCALER
                ServerModelRole.AUTO -> defaultServerKind(m, modelsRoot)
                ServerModelRole.DISABLED -> null
            } ?: return@forEach

            val mmproj = if (kind == ServerEngineKind.VLM) {
                VlmPaths.colocatedMmproj(File(m.path))?.absolutePath
            } else {
                null
            }
            if (kind == ServerEngineKind.VLM && mmproj.isNullOrBlank()) return@forEach
            val isDefault = roleDefaults[kind.token] == m.id ||
                (kind == ServerEngineKind.CHAT_GGUF && roleDefaults[kind.token].isNullOrBlank() &&
                    prefs.serverSelectedModelId == m.id)
            out.put(makeEntry(m, kind.token, now, mmprojPath = mmproj, isDefault = isDefault))
        }
        return out
    }

    private fun defaultServerKind(model: ModelInfo, modelsRoot: File): ServerEngineKind? =
        when (model.providerType) {
            ProviderType.GGUF -> {
                inferServerKindFromName(model)?.let { return it }
                if (VlmPaths.isInsideVlmFolder(model.path, modelsRoot) &&
                    VlmPaths.colocatedMmproj(File(model.path)) != null
                ) {
                    ServerEngineKind.VLM
                } else {
                    ServerEngineKind.CHAT_GGUF
                }
            }
            ProviderType.VISION_CHAT -> ServerEngineKind.VLM
            ProviderType.TOOL_SEARCH -> ServerEngineKind.CHAT_GGUF
            ProviderType.EMBEDDING -> ServerEngineKind.EMBEDDING
            ProviderType.TTS -> ServerEngineKind.TTS
            ProviderType.STT -> ServerEngineKind.STT
            ProviderType.IMAGE_GEN -> ServerEngineKind.IMAGE_GEN
            ProviderType.IMAGE_UPSCALER -> ServerEngineKind.IMAGE_UPSCALER
        }

    private fun inferServerKindFromName(model: ModelInfo): ServerEngineKind? {
        val haystack = "${model.name} ${model.id} ${model.path}".lowercase()
        val ext = model.path.substringAfterLast('.', "").lowercase()
        return when {
            ext == "mnn" && listOf("upscale", "upscaler", "esrgan", "realesrgan", "upgif", "x2", "x3", "x4")
                .any { it in haystack } -> ServerEngineKind.IMAGE_UPSCALER
            listOf("embed", "embedding", "bge-", "e5-", "nomic-embed", "gte-", "snowflake-arctic-embed")
                .any { it in haystack } -> ServerEngineKind.EMBEDDING
            listOf("whisper", "speech-to-text", "stt", "transcrib")
                .any { it in haystack } -> ServerEngineKind.STT
            listOf("piper", "kokoro", "text-to-speech", "tts")
                .any { it in haystack } -> ServerEngineKind.TTS
            listOf("stable-diffusion", "sd-", "sd_", "diffusion", "unet", "inpaint")
                .any { it in haystack } -> ServerEngineKind.IMAGE_GEN
            listOf("-vl-", "_vl_", "vision", "vlm", "mmproj", "llava", "moondream", "minicpm-v")
                .any { it in haystack } -> ServerEngineKind.VLM
            else -> null
        }
    }

    private fun readServerModelRoles(): Map<String, ServerModelRole> {
        val raw = prefs.serverModelRolesJson
        val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyMap()
        val out = HashMap<String, ServerModelRole>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val id = keys.next()
            val role = ServerModelRole.fromToken(obj.optString(id))
            if (id.isNotBlank() && role != ServerModelRole.AUTO) {
                out[id] = role
            }
        }
        return out
    }

    private fun readServerRoleDefaults(): Map<String, String> {
        val raw = prefs.serverRoleDefaultsJson
        val obj = runCatching { JSONObject(raw) }.getOrNull() ?: JSONObject()
        val out = HashMap<String, String>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val kind = keys.next()
            val modelId = obj.optString(kind)
            if (kind.isNotBlank() && modelId.isNotBlank()) {
                out[kind] = modelId
            }
        }
        if (out[ServerEngineKind.CHAT_GGUF.token].isNullOrBlank() && prefs.serverSelectedModelId.isNotBlank()) {
            out[ServerEngineKind.CHAT_GGUF.token] = prefs.serverSelectedModelId
        }
        if (out[ServerEngineKind.TTS.token].isNullOrBlank() && prefs.activeTtsModelId.isNotBlank()) {
            out[ServerEngineKind.TTS.token] = prefs.activeTtsModelId
        }
        if (out[ServerEngineKind.STT.token].isNullOrBlank() && prefs.activeSttModelId.isNotBlank()) {
            out[ServerEngineKind.STT.token] = prefs.activeSttModelId
        }
        return out
    }

    private fun makeEntry(
        model: ModelInfo,
        type: String,
        createdUnix: Long,
        mmprojPath: String? = null,
        isDefault: Boolean = false,
    ): JSONObject =
        JSONObject().apply {
            put("id", model.id)
            put("name", model.name)
            put("path", model.path)
            if (!mmprojPath.isNullOrBlank()) put("mmproj_path", mmprojPath)
            put("config_json", buildModelConfigJson(model.id))
            put("type", type)
            put("created", createdUnix)
            if (isDefault) put("default", true)
        }

    private fun ensureBoundThen(block: (IRemoteServerService) -> Unit) {
        val s = stub
        if (s != null) { block(s); return }
        tryBind()
        val deadline = System.currentTimeMillis() + 1500L
        while (stub == null && System.currentTimeMillis() < deadline) {
            try { Thread.sleep(40) } catch (_: InterruptedException) { return }
        }
        stub?.let(block) ?: run {
            _state.value = ServerState.Failed("server service did not bind")
        }
    }

    private fun applySnapshot(json: String?) {
        if (json.isNullOrBlank()) return
        val o = runCatching { JSONObject(json) }.getOrNull() ?: return
        val phase = o.optString("phase", "stopped")
        when (phase) {
            "stopped" -> {
                _state.value = ServerState.Stopped
                _requestEvents.value = emptyList()
            }
            "loading_model" -> {
                _state.value = ServerState.LoadingModel(
                    modelId = o.optString("modelId"),
                    modelName = o.optString("modelName"),
                )
            }
            "starting" -> _state.value = ServerState.Starting
            "running" -> {
                val info = ServerInfo(
                    host = o.optString("host"),
                    displayHost = o.optString("displayHost"),
                    lanHost = o.optString("lanHost").takeIf { it.isNotBlank() },
                    port = o.optInt("port"),
                    bindMode = runCatching { BindMode.valueOf(o.optString("bindMode")) }
                        .getOrDefault(BindMode.ALL_INTERFACES),
                    wifiActive = o.optBoolean("wifiActive"),
                )
                _state.value = ServerState.Running(
                    info = info,
                    modelId = o.optString("modelId"),
                    modelName = o.optString("modelName"),
                )
                drainAuditLog()
            }
            "failed" -> {
                _state.value = ServerState.Failed(o.optString("reason", "failed"))
            }
        }
    }

    private fun drainAuditLog() {
        val s = stub ?: return
        val raw = try { s.recentRequestEventsJson(MAX_EVENT_HISTORY) } catch (_: Exception) { return }
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return
        val seen = (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            ServerRequestEvent(
                timestampMs = o.optLong("ts_ms"),
                method      = o.optString("method"),
                path        = o.optString("path"),
                status      = o.optInt("status"),
                durationMs  = o.optLong("duration_ms"),
                client      = o.optString("client"),
            )
        }
        if (seen.isNotEmpty()) _requestEvents.value = seen
    }

    private fun buildModelConfigJson(modelId: String): String {
        val cfg = modelRepo.getConfig(modelId) ?: return "{}"
        val sb = StringBuilder(256).append('{')
        val loading = cfg.loadingParamsJson
        val inference = cfg.inferenceParamsJson
        if (loading != "{}" && loading.isNotBlank()) {
            val inner = loading.trim().removePrefix("{").removeSuffix("}")
            if (inner.isNotBlank()) sb.append(inner).append(',')
        }
        if (inference != "{}" && inference.isNotBlank()) {
            val inner = inference.trim().removePrefix("{").removeSuffix("}")
            if (inner.isNotBlank()) sb.append(inner)
        }
        if (sb.last() == ',') sb.deleteCharAt(sb.length - 1)
        sb.append('}')
        return sb.toString()
    }

    private fun loadAsset(name: String): String =
        try { app.assets.open(name).bufferedReader().use { it.readText() } }
        catch (_: Exception) { "" }

    private fun generateToken(): String {
        val raw = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val b64 = Base64.encodeToString(raw, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        return "tn_sk_$b64"
    }

    companion object {
        private const val TAG = "ServerController"
        private const val MAX_EVENT_HISTORY = 100
    }
}
