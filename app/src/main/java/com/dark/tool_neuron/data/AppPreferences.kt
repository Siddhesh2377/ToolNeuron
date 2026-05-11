package com.dark.tool_neuron.data

import android.content.Context
import com.dark.hxs.HexStorage
import com.dark.hxs.HxsRecord
import com.dark.hxs_encryptor.HxsEncryptor
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppPreferences @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val keyStore: AppKeyStore,
    private val encryptor: HxsEncryptor,
) {

    private val storage = HexStorage()
    private val basePath: String

    init {
        val dir = context.filesDir.resolve("app_prefs")
        dir.mkdirs()
        basePath = dir.absolutePath

        val dek = keyStore.unwrapOrCreateDek()
        val signerHash = keyStore.installSignerHash()
        val userKey = encryptor.deriveKey(ikm = dek, salt = signerHash, info = USER_KEY_INFO)

        val opened = openOrRebuild(dek, userKey)
        if (!opened) throw SecurityException("Failed to open encrypted app_prefs vault")

        // userKey is NOT wiped: hxs.cpp holds a GlobalRef to this ByteArray for every encrypt/decrypt callback. Zeroing it would make every AEAD op use a zero key.

        storage.ensureCollection(COLLECTION)
        storage.addIndex(COLLECTION, TAG_KEY, HexStorage.WIRE_BYTES)
    }

    fun getBoolean(key: String, default: Boolean = false): Boolean =
        findRecord(key)?.getBool(TAG_VALUE_BOOL, default) ?: default

    fun putBoolean(key: String, value: Boolean) {
        val existing = findRecord(key)
        if (existing != null) {
            existing.putBool(TAG_VALUE_BOOL, value)
            storage.update(COLLECTION, existing)
        } else {
            val record = HxsRecord.build {
                putString(TAG_KEY, key)
                putBool(TAG_VALUE_BOOL, value)
            }
            storage.put(COLLECTION, record)
        }
        storage.flushAll()
    }

    fun getString(key: String, default: String = ""): String =
        findRecord(key)?.getString(TAG_VALUE_STRING, default) ?: default

    fun putString(key: String, value: String) {
        val existing = findRecord(key)
        if (existing != null) {
            existing.putString(TAG_VALUE_STRING, value)
            storage.update(COLLECTION, existing)
        } else {
            val record = HxsRecord.build {
                putString(TAG_KEY, key)
                putString(TAG_VALUE_STRING, value)
            }
            storage.put(COLLECTION, record)
        }
        storage.flushAll()
    }

    fun getBytes(key: String): ByteArray? =
        findRecord(key)?.getBytes(TAG_VALUE_BYTES)

    fun putBytes(key: String, value: ByteArray) {
        val existing = findRecord(key)
        if (existing != null) {
            existing.putBytes(TAG_VALUE_BYTES, value)
            storage.update(COLLECTION, existing)
        } else {
            val record = HxsRecord.build {
                putString(TAG_KEY, key)
                putBytes(TAG_VALUE_BYTES, value)
            }
            storage.put(COLLECTION, record)
        }
        storage.flushAll()
    }

    fun deleteKey(key: String) {
        val existing = findRecord(key) ?: return
        storage.delete(COLLECTION, existing.id)
        storage.flushAll()
    }

    private fun findRecord(key: String): HxsRecord? =
        storage.queryString(COLLECTION, TAG_KEY, key).firstOrNull()

    var onboardingComplete: Boolean
        get() = getBoolean(KEY_ONBOARDING_COMPLETE)
        set(value) = putBoolean(KEY_ONBOARDING_COMPLETE, value)

    var tcAccepted: Boolean
        get() = getBoolean(KEY_TC_ACCEPTED)
        set(value) = putBoolean(KEY_TC_ACCEPTED, value)

    var setupDone: Boolean
        get() = getBoolean(KEY_SETUP_DONE)
        set(value) = putBoolean(KEY_SETUP_DONE, value)

    var securitySetupDone: Boolean
        get() = getBoolean(KEY_SECURITY_SETUP_DONE)
        set(value) = putBoolean(KEY_SECURITY_SETUP_DONE, value)

    var modelSetupDone: Boolean
        get() = getBoolean(KEY_MODEL_SETUP_DONE)
        set(value) = putBoolean(KEY_MODEL_SETUP_DONE, value)

    var guideShown: Boolean
        get() = getBoolean(KEY_GUIDE_SHOWN)
        set(value) = putBoolean(KEY_GUIDE_SHOWN, value)

    var rootWarningShown: Boolean
        get() = getBoolean(KEY_ROOT_WARNING_SHOWN)
        set(value) = putBoolean(KEY_ROOT_WARNING_SHOWN, value)

    var serverToken: String
        get() = getString(KEY_SERVER_TOKEN)
        set(value) = putString(KEY_SERVER_TOKEN, value)

    var serverPort: Int
        get() {
            val raw = getString(KEY_SERVER_PORT, DEFAULT_SERVER_PORT.toString()).toIntOrNull()
                ?: return DEFAULT_SERVER_PORT
            return if (raw in 1024..65535) raw else DEFAULT_SERVER_PORT
        }
        set(value) {
            val clamped = value.coerceIn(1024, 65535)
            putString(KEY_SERVER_PORT, clamped.toString())
        }

    var serverBindMode: String
        get() = getString(KEY_SERVER_BIND_MODE, DEFAULT_BIND_MODE)
        set(value) = putString(KEY_SERVER_BIND_MODE, value)

    var serverAutoStart: Boolean
        get() = getBoolean(KEY_SERVER_AUTO_START)
        set(value) = putBoolean(KEY_SERVER_AUTO_START, value)

    var serverConfigured: Boolean
        get() = getBoolean(KEY_SERVER_CONFIGURED)
        set(value) = putBoolean(KEY_SERVER_CONFIGURED, value)

    var serverSelectedModelId: String
        get() = getString(KEY_SERVER_SELECTED_MODEL)
        set(value) = putString(KEY_SERVER_SELECTED_MODEL, value)

    var hfSearchHistory: String
        get() = getString(KEY_HF_SEARCH_HISTORY)
        set(value) = putString(KEY_HF_SEARCH_HISTORY, value)

    var hfTagsCatalogJson: String
        get() = getString(KEY_HF_TAGS_CATALOG)
        set(value) = putString(KEY_HF_TAGS_CATALOG, value)

    var hfTagsCatalogSavedAt: Long
        get() = getString(KEY_HF_TAGS_CATALOG_AT).toLongOrNull() ?: 0L
        set(value) = putString(KEY_HF_TAGS_CATALOG_AT, value.toString())

    var activeTtsModelId: String
        get() = getString(KEY_ACTIVE_TTS_MODEL)
        set(value) = putString(KEY_ACTIVE_TTS_MODEL, value)

    var activeSttModelId: String
        get() = getString(KEY_ACTIVE_STT_MODEL)
        set(value) = putString(KEY_ACTIVE_STT_MODEL, value)

    var ragSmartRerank: Boolean
        get() = getBoolean(KEY_RAG_SMART_RERANK)
        set(value) = putBoolean(KEY_RAG_SMART_RERANK, value)

    var ragMultiQuery: Boolean
        get() = getBoolean(KEY_RAG_MULTI_QUERY)
        set(value) = putBoolean(KEY_RAG_MULTI_QUERY, value)

    var ragDeepResearch: Boolean
        get() = getBoolean(KEY_RAG_DEEP_RESEARCH)
        set(value) = putBoolean(KEY_RAG_DEEP_RESEARCH, value)

    var researchMaxIterations: Int
        get() = getString(KEY_RESEARCH_MAX_ITERATIONS).toIntOrNull()?.coerceIn(1, 10)
            ?: DEFAULT_RESEARCH_MAX_ITERATIONS
        set(value) = putString(KEY_RESEARCH_MAX_ITERATIONS, value.coerceIn(1, 10).toString())

    var researchMaxQuestions: Int
        get() = getString(KEY_RESEARCH_MAX_QUESTIONS).toIntOrNull()?.coerceIn(1, 6)
            ?: DEFAULT_RESEARCH_MAX_QUESTIONS
        set(value) = putString(KEY_RESEARCH_MAX_QUESTIONS, value.coerceIn(1, 6).toString())

    var researchResultsPerSearch: Int
        get() = getString(KEY_RESEARCH_RESULTS_PER_SEARCH).toIntOrNull()?.coerceIn(3, 10)
            ?: DEFAULT_RESEARCH_RESULTS_PER_SEARCH
        set(value) = putString(KEY_RESEARCH_RESULTS_PER_SEARCH, value.coerceIn(3, 10).toString())

    var researchDdgLocale: String
        get() = getString(KEY_RESEARCH_DDG_LOCALE)
        set(value) = putString(KEY_RESEARCH_DDG_LOCALE, value)

    var researchCancelOnBackground: Boolean
        get() = getBoolean(KEY_RESEARCH_CANCEL_ON_BG, default = true)
        set(value) = putBoolean(KEY_RESEARCH_CANCEL_ON_BG, value)

    var activeResearchModelId: String
        get() = getString(KEY_ACTIVE_RESEARCH_MODEL)
        set(value) = putString(KEY_ACTIVE_RESEARCH_MODEL, value)

    var vlmImageQuality: String
        get() = getString(KEY_VLM_IMAGE_QUALITY, DEFAULT_VLM_IMAGE_QUALITY)
        set(value) = putString(KEY_VLM_IMAGE_QUALITY, value)

    var threadMode: Int
        get() = getString(KEY_THREAD_MODE, DEFAULT_THREAD_MODE.toString()).toIntOrNull()?.coerceIn(0, 2)
            ?: DEFAULT_THREAD_MODE
        set(value) = putString(KEY_THREAD_MODE, value.coerceIn(0, 2).toString())

    var pluginOnnxEp: String
        get() = getString(KEY_PLUGIN_ONNX_EP, DEFAULT_PLUGIN_ONNX_EP)
        set(value) = putString(KEY_PLUGIN_ONNX_EP, value)

    fun readAuthState(): AuthState {
        val sealed = getBytes(KEY_AUTH_STATE) ?: return AuthState.DEFAULT
        val plaintext = try {
            encryptor.decrypt(sealed, deriveAuthKey(), AUTH_AAD)
        } catch (_: SecurityException) {
            return AuthState.DEFAULT
        }
        try {
            return AuthState.decode(plaintext)
        } finally {
            encryptor.secureWipe(plaintext)
        }
    }

    fun writeAuthState(state: AuthState) {
        val plaintext = state.encode()
        val sealed = try {
            encryptor.encrypt(plaintext, deriveAuthKey(), AUTH_AAD)
        } finally {
            encryptor.secureWipe(plaintext)
        }
        putBytes(KEY_AUTH_STATE, sealed)
    }

    fun clearAuthState() {
        deleteKey(KEY_AUTH_STATE)
    }

    private fun deriveAuthKey(): ByteArray {
        val dek = keyStore.unwrapOrCreateDek()
        val signerHash = keyStore.installSignerHash()
        return encryptor.deriveKey(ikm = dek, salt = signerHash, info = AUTH_KEY_INFO)
    }

    private fun openOrRebuild(dek: ByteArray, userKey: ByteArray): Boolean {
        if (storage.exists(basePath)) {
            if (storage.openEncrypted(basePath, dek, userKey, encryptor)) return true
            File(basePath).deleteRecursively()
            File(basePath).mkdirs()
        }
        return storage.createEncrypted(basePath, dek, userKey, encryptor)
    }

    companion object {
        private const val COLLECTION = "app_prefs"
        private const val TAG_KEY = 1
        private const val TAG_VALUE_BOOL = 2
        private const val TAG_VALUE_STRING = 3
        private const val TAG_VALUE_BYTES = 4

        const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
        const val KEY_TC_ACCEPTED = "tc_accepted"
        const val KEY_SETUP_DONE = "setup_done"
        const val KEY_SECURITY_SETUP_DONE = "security_setup_done"
        const val KEY_MODEL_SETUP_DONE = "model_setup_done"
        const val KEY_GUIDE_SHOWN = "guide_shown"
        const val KEY_ROOT_WARNING_SHOWN = "root_warning_shown"
        const val KEY_SERVER_TOKEN = "server_token"
        const val KEY_SERVER_PORT = "server_port"
        const val KEY_SERVER_BIND_MODE = "server_bind_mode"
        const val KEY_SERVER_AUTO_START = "server_auto_start"
        const val KEY_SERVER_CONFIGURED = "server_configured"
        const val KEY_SERVER_SELECTED_MODEL = "server_selected_model"
        const val KEY_HF_SEARCH_HISTORY = "hf_search_history"
        const val KEY_HF_TAGS_CATALOG = "hf_tags_catalog_v1"
        const val KEY_HF_TAGS_CATALOG_AT = "hf_tags_catalog_v1_at"
        const val KEY_ACTIVE_TTS_MODEL = "active_tts_model"
        const val KEY_ACTIVE_STT_MODEL = "active_stt_model"
        const val KEY_RAG_SMART_RERANK = "rag_smart_rerank"
        const val KEY_RAG_MULTI_QUERY = "rag_multi_query"
        const val KEY_RAG_DEEP_RESEARCH = "rag_deep_research"
        const val KEY_RESEARCH_MAX_ITERATIONS = "research_max_iterations"
        const val KEY_RESEARCH_MAX_QUESTIONS = "research_max_questions"
        const val KEY_RESEARCH_RESULTS_PER_SEARCH = "research_results_per_search"
        const val KEY_RESEARCH_DDG_LOCALE = "research_ddg_locale"
        const val KEY_RESEARCH_CANCEL_ON_BG = "research_cancel_on_bg"
        const val KEY_ACTIVE_RESEARCH_MODEL = "active_research_model"
        const val KEY_VLM_IMAGE_QUALITY = "vlm_image_quality"
        const val DEFAULT_VLM_IMAGE_QUALITY = "MEDIUM"
        const val KEY_THREAD_MODE = "thread_mode"
        const val DEFAULT_THREAD_MODE = 1
        const val THREAD_MODE_POWER_SAVING = 0
        const val THREAD_MODE_BALANCED = 1
        const val THREAD_MODE_PERFORMANCE = 2

        const val KEY_PLUGIN_ONNX_EP = "plugin_onnx_ep"
        const val PLUGIN_ONNX_EP_CPU = "cpu"
        const val PLUGIN_ONNX_EP_NNAPI = "nnapi"
        const val PLUGIN_ONNX_EP_XNNPACK = "xnnpack"
        const val DEFAULT_PLUGIN_ONNX_EP = PLUGIN_ONNX_EP_CPU
        const val DEFAULT_RESEARCH_MAX_ITERATIONS = 5
        const val DEFAULT_RESEARCH_MAX_QUESTIONS = 4
        const val DEFAULT_RESEARCH_RESULTS_PER_SEARCH = 5
        const val DEFAULT_SERVER_PORT = 11434
        const val DEFAULT_BIND_MODE = "ALL_INTERFACES"
        private const val KEY_AUTH_STATE = "auth_state_v1"

        const val SECURITY_NONE = "none"
        const val SECURITY_APP_PASSWORD = "app_password"

        private const val USER_KEY_INFO = "tn.app_prefs.user_key.v2"
        private const val AUTH_KEY_INFO = "tn.app_prefs.auth_key.v2"
        private val AUTH_AAD = "tn.auth_state.v1".toByteArray(Charsets.UTF_8)
    }
}
