    package com.dark.tool_neuron.di

    import android.content.Context
    import com.dark.tool_neuron.database.AppDatabase
    import com.dark.tool_neuron.engine.EmbeddingEngine
    import com.dark.tool_neuron.repo.ChatRepository
    import com.dark.tool_neuron.repo.RagRepository
    import com.dark.tool_neuron.worker.ChatManager
    import com.dark.tool_neuron.worker.RagVaultIntegration
    import dagger.Module
    import dagger.Provides
    import dagger.hilt.InstallIn
    import dagger.hilt.android.qualifiers.ApplicationContext
    import dagger.hilt.components.SingletonComponent
    import kotlinx.coroutines.CoroutineScope
    import kotlinx.coroutines.Dispatchers
    import kotlinx.coroutines.SupervisorJob
    import javax.inject.Qualifier
    import javax.inject.Singleton

    @Qualifier
    @Retention(AnnotationRetention.BINARY)
    annotation class ApplicationScope

    @Module
    @InstallIn(SingletonComponent::class)
    object DatabaseModule {

        @Provides
        @Singleton
        fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
            return AppDatabase.getDatabase(context)
        }
    }

    @Module
    @InstallIn(SingletonComponent::class)
    object RepositoryModule {

        @Provides
        @Singleton
        fun provideChatRepository(): ChatRepository {
            return ChatRepository()
        }

        @Provides
        @Singleton
        fun provideRagRepository(
            database: AppDatabase,
            @ApplicationContext context: Context
        ): RagRepository {
            return RagRepository(
                ragDao = database.ragDao(),
                context = context
            )
        }
    }

    @Module
    @InstallIn(SingletonComponent::class)
    object EmbeddingModule {

        @Provides
        @Singleton
        fun provideEmbeddingEngine(): EmbeddingEngine {
            return EmbeddingEngine()
        }
    }

    @Module
    @InstallIn(SingletonComponent::class)
    object WorkerModule {

        @Provides
        @Singleton
        fun provideChatManager(): ChatManager {
            return ChatManager()
        }

        @Provides
        @Singleton
        @ApplicationScope
        fun provideApplicationScope(): CoroutineScope {
            return CoroutineScope(Dispatchers.IO + SupervisorJob())
        }

        @Provides
        @Singleton
        fun provideRagVaultIntegration(
            @ApplicationContext context: Context,
            ragRepository: RagRepository,
            embeddingEngine: EmbeddingEngine
        ): RagVaultIntegration {
            return RagVaultIntegration(
                context = context,
                ragRepository = ragRepository,
                embeddingEngine = embeddingEngine
            )
        }
    }