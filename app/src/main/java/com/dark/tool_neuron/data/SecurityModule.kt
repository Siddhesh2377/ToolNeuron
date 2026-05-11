package com.dark.tool_neuron.data

import com.dark.hxs_encryptor.HxsEncryptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {

    @Provides
    @Singleton
    fun provideHxsEncryptor(): HxsEncryptor = HxsEncryptor()
}
