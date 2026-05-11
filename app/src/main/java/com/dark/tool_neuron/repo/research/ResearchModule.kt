package com.dark.tool_neuron.repo.research

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ResearchModule {

    @Binds
    @Singleton
    abstract fun bindResearchModelClient(impl: ChatLlmResearchClient): ResearchModelClient
}
