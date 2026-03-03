package com.dark.tool_neuron.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.dark.tool_neuron.repo.ums.UmsChatRepository
import com.dark.tool_neuron.repo.ums.UmsConfigRepository
import com.dark.tool_neuron.repo.ums.UmsKnowledgeRepository
import com.dark.tool_neuron.repo.ums.UmsMemoryRepository
import com.dark.tool_neuron.repo.ums.UmsModelRepository
import com.dark.tool_neuron.repo.ums.UmsPersonaRepository
import com.dark.ums.UnifiedMemorySystem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

object VaultManager {

    private var _ums: UnifiedMemorySystem? = null
    val ums: UnifiedMemorySystem get() = _ums ?: error("VaultManager not initialized")

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady

    var modelRepo: UmsModelRepository? = null; private set
    var configRepo: UmsConfigRepository? = null; private set
    var personaRepo: UmsPersonaRepository? = null; private set
    var memoryRepo: UmsMemoryRepository? = null; private set
    var knowledgeRepo: UmsKnowledgeRepository? = null; private set
    var chatRepo: UmsChatRepository? = null; private set

    fun basePath(context: Context): String =
        context.filesDir.resolve("ums").absolutePath

    fun exists(context: Context): Boolean {
        val u = UnifiedMemorySystem()
        return u.exists(basePath(context))
    }

    fun initPlaintext(context: Context): Boolean {
        val u = UnifiedMemorySystem()
        val path = basePath(context)
        val ok = if (u.exists(path)) u.openPlaintext(path) else u.createPlaintext(path)
        if (!ok) return false
        initRepos(u)
        return true
    }

    fun initEncrypted(context: Context, passphrase: String): Boolean {
        val u = UnifiedMemorySystem()
        val path = basePath(context)
        val appKey = deriveAppKey(context)
        val ok = if (u.exists(path)) {
            u.openWithPassphrase(path, appKey, passphrase)
        } else {
            u.createWithPassphrase(path, appKey, passphrase)
        }
        if (!ok) return false
        initRepos(u)
        return true
    }

    private fun initRepos(u: UnifiedMemorySystem) {
        _ums = u
        modelRepo = UmsModelRepository(u).also { it.init() }
        configRepo = UmsConfigRepository(u).also { it.init() }
        personaRepo = UmsPersonaRepository(u).also { it.init() }
        memoryRepo = UmsMemoryRepository(u).also { it.init() }
        knowledgeRepo = UmsKnowledgeRepository(u).also { it.init() }
        chatRepo = UmsChatRepository(u).also { it.init() }
        _isReady.value = true
    }

    fun close() {
        _ums?.close()
        _ums = null
        modelRepo = null
        configRepo = null
        personaRepo = null
        memoryRepo = null
        knowledgeRepo = null
        chatRepo = null
        _isReady.value = false
    }

    private const val KEYSTORE_ALIAS = "ums_app_key"

    fun deriveAppKey(context: Context): ByteArray {
        val ks = KeyStore.getInstance("AndroidKeyStore")
        ks.load(null)
        val key: SecretKey = if (ks.containsAlias(KEYSTORE_ALIAS)) {
            (ks.getEntry(KEYSTORE_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
        } else {
            val gen = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
            )
            gen.init(
                KeyGenParameterSpec.Builder(
                    KEYSTORE_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setKeySize(256)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
            gen.generateKey()
        }
        return key.encoded ?: ByteArray(32)
    }
}
