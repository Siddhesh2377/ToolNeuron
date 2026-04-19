package com.dark.tool_neuron.plugin

import com.dark.tool_neuron.plugin.web.WebPlugin
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PluginModule {

    @Provides
    @Singleton
    fun provideRegistry(
        web: WebPlugin,
    ): PluginRegistry = PluginRegistry(
        plugins = listOf(web),
    )
}
