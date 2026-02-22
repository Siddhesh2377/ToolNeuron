package com.dark.tool_neuron.di

import android.app.Application
import android.content.Context
import com.dark.tool_neuron.database.AppDatabase
import com.dark.tool_neuron.database.dao.AiMemoryDao
import com.dark.tool_neuron.database.dao.PersonaDao
import com.dark.tool_neuron.repo.ChatRepository
import com.dark.tool_neuron.repo.ModelRepository
import com.dark.tool_neuron.vault.VaultHelper
import com.dark.tool_neuron.viewmodel.factory.ChatListViewModelFactory
import com.dark.tool_neuron.viewmodel.factory.ChatViewModelFactory
import com.dark.tool_neuron.viewmodel.factory.LLMModelViewModelFactory
import com.dark.tool_neuron.worker.ChatManager
import com.dark.tool_neuron.worker.GenerationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object AppContainer {

    private lateinit var database: AppDatabase
    private lateinit var modelRepository: ModelRepository
    private lateinit var chatRepository: ChatRepository
    private lateinit var llmModelViewModelFactory: LLMModelViewModelFactory
    private lateinit var chatListViewModelFactory: ChatListViewModelFactory
    private lateinit var chatViewModelFactory: ChatViewModelFactory

    private val appScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val chatManager = ChatManager()
    private var generationManager = GenerationManager()

    // Keep track of context for re-initialization if needed
    private lateinit var appContext: Context

    fun init(context: Context, application: Application) {
        appContext = context.applicationContext
        database = AppDatabase.getDatabase(context)

        modelRepository = ModelRepository(
            modelDao = database.modelDao(), configDao = database.modelConfigDao()
        )

        chatRepository = ChatRepository()

        llmModelViewModelFactory = LLMModelViewModelFactory(application, modelRepository)
        chatListViewModelFactory = ChatListViewModelFactory(chatManager)
        chatViewModelFactory = ChatViewModelFactory(context, chatManager, generationManager)

        initVault(context)
    }

    private fun initVault(context: Context) {
        appScope.launch {
            val maxRetries = 3
            for (attempt in 1..maxRetries) {
                try {
                    VaultHelper.initialize(context)
                    break
                } catch (e: Exception) {
                    e.printStackTrace()
                    if (attempt < maxRetries) {
                        kotlinx.coroutines.delay(500L * attempt)
                    }
                }
            }
        }
    }

    /**
     * Re-initialize vault if needed (e.g., after configuration change or process death)
     * This can be called from Activities/Fragments to ensure vault is ready
     */
    fun ensureVaultInitialized() {
        if (!VaultHelper.isInitialized() && ::appContext.isInitialized) {
            initVault(appContext)
        }
    }

    fun shutdown() {
        appScope.launch {
            try {
                VaultHelper.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Close the Room database for backup/restore operations.
     */
    fun closeDatabase() {
        AppDatabase.closeDatabase()
    }

    /**
     * Re-initialize the entire container after a restore operation.
     * Closes everything and re-creates database + vault connections.
     */
    fun reinitialize(context: Context) {
        val ctx = context.applicationContext
        appContext = ctx
        database = AppDatabase.getDatabase(ctx)

        modelRepository = ModelRepository(
            modelDao = database.modelDao(), configDao = database.modelConfigDao()
        )

        chatRepository = ChatRepository()

        appScope.launch {
            try {
                VaultHelper.initialize(ctx)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun getDatabase(): AppDatabase = database

    fun getModelRepository(): ModelRepository = modelRepository

    fun getChatRepository(): ChatRepository = chatRepository

    fun getLLMModelViewModelFactory(): LLMModelViewModelFactory = llmModelViewModelFactory

    fun getChatListViewModelFactory(): ChatListViewModelFactory = chatListViewModelFactory

    fun getChatViewModelFactory(): ChatViewModelFactory = chatViewModelFactory


    fun isVaultReady(): Boolean = VaultHelper.isInitialized()

    /**
     * Exposes the vault readiness StateFlow for UI observation
     */
    val vaultReadyState = VaultHelper.isReady

    fun getPersonaDao(): PersonaDao = database.personaDao()

    fun getAiMemoryDao(): AiMemoryDao = database.aiMemoryDao()

    fun getGenerationManager(): GenerationManager = generationManager
}