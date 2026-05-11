package com.dark.tool_neuron.plugin_host

import android.content.Context
import com.dark.hxs.HexStorage
import com.dark.hxs_encryptor.HxsEncryptor
import com.dark.plugin_exc.PluginExecutor
import com.dark.plugin_exc.catalog.PluginCatalogClient
import com.dark.plugin_exc.ui.PluginContainerActivity
import com.dark.tool_neuron.data.AppKeyStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PluginStorage

@Module
@InstallIn(SingletonComponent::class)
object PluginHostModule {

    private const val USER_KEY_INFO = "tn.plugins.user_key.v2"

    @Provides
    @Singleton
    @PluginStorage
    fun providePluginStorage(
        @ApplicationContext context: Context,
        keyStore: AppKeyStore,
        encryptor: HxsEncryptor,
    ): HexStorage {
        val storage = HexStorage()
        val dir = File(context.filesDir, "plugin_store_v2").apply { mkdirs() }
        val basePath = dir.absolutePath

        val dek = keyStore.unwrapOrCreateDek()
        val signerHash = keyStore.installSignerHash()
        val userKey = encryptor.deriveKey(ikm = dek, salt = signerHash, info = USER_KEY_INFO)

        val opened = openOrRebuild(storage, basePath, dek, userKey, encryptor)
        if (!opened) throw SecurityException("Failed to open encrypted plugin_store_v2 vault")

        return storage
    }

    @Provides
    @Singleton
    fun providePluginExecutor(
        @ApplicationContext context: Context,
        @PluginStorage storage: HexStorage,
        prefs: com.dark.tool_neuron.data.AppPreferences,
    ): PluginExecutor = PluginExecutor(context, storage).apply {
        onnxExecutionProvider = prefs.pluginOnnxEp
    }

    @Provides
    @Singleton
    fun providePluginContainerHost(
        executor: PluginExecutor,
    ): PluginContainerHost = PluginContainerHost(executor).also {
        PluginContainerActivity.bind(it)
    }

    @Provides
    @Singleton
    fun providePluginCatalogClient(): PluginCatalogClient = PluginCatalogClient()

    private fun openOrRebuild(
        storage: HexStorage,
        basePath: String,
        dek: ByteArray,
        userKey: ByteArray,
        encryptor: HxsEncryptor,
    ): Boolean {
        if (storage.exists(basePath)) {
            if (storage.openEncrypted(basePath, dek, userKey, encryptor)) return true
            File(basePath).deleteRecursively()
            File(basePath).mkdirs()
        }
        return storage.createEncrypted(basePath, dek, userKey, encryptor)
    }
}

class PluginContainerHost internal constructor(
    override val executor: PluginExecutor,
) : PluginContainerActivity.Host
