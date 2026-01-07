package com.dark.tool_neuron.di

import android.content.Context
import com.dark.tool_neuron.database.AppDatabase
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
    private var vaultInitialized = false

    fun init(context: Context) {
        database = AppDatabase.getDatabase(context)

        modelRepository = ModelRepository(
            modelDao = database.modelDao(), configDao = database.modelConfigDao()
        )

        chatRepository = ChatRepository()

        llmModelViewModelFactory = LLMModelViewModelFactory(modelRepository)
        chatListViewModelFactory = ChatListViewModelFactory(chatManager)
        chatViewModelFactory = ChatViewModelFactory(chatManager, generationManager)

        initVault(context)
    }

    private fun initVault(context: Context) {
        appScope.launch {
            try {
                VaultHelper.initialize(context)
                vaultInitialized = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun shutdown() {
        if (vaultInitialized) {
            appScope.launch {
                try {
                    VaultHelper.close()
                    vaultInitialized = false
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun getDatabase(): AppDatabase = database

    fun getModelRepository(): ModelRepository = modelRepository

    fun getChatRepository(): ChatRepository = chatRepository

    fun getLLMModelViewModelFactory(): LLMModelViewModelFactory = llmModelViewModelFactory

    fun getChatListViewModelFactory(): ChatListViewModelFactory = chatListViewModelFactory

    fun getChatViewModelFactory(): ChatViewModelFactory = chatViewModelFactory


    fun isVaultReady(): Boolean = vaultInitialized
}