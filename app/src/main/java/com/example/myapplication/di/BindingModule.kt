package com.example.myapplication.di

import com.example.myapplication.data.ChatRepositoryImpl
import com.example.myapplication.data.ModelCatalogRepositoryImpl
import com.example.myapplication.data.SettingsRepositoryImpl
import com.example.myapplication.data.remote.DeepSeekProvider
import com.example.myapplication.data.remote.DoubaoProvider
import com.example.myapplication.domain.ChatRepository
import com.example.myapplication.domain.LlmProvider
import com.example.myapplication.domain.ModelCatalogRepository
import com.example.myapplication.domain.SettingsRepository
import com.example.myapplication.domain.model.ProviderId
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
/** 按服务商把对话实现放进同一个 Map，发送时用会话里的 ProviderId 取出。 */
abstract class BindingModule {
    @Binds
    @Singleton
    abstract fun bindChatRepository(impl: ChatRepositoryImpl): ChatRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindModelCatalogRepository(impl: ModelCatalogRepositoryImpl): ModelCatalogRepository

    @Binds
    @IntoMap
    @ProviderKey(ProviderId.DEEPSEEK)
    @Singleton
    abstract fun bindDeepSeek(impl: DeepSeekProvider): LlmProvider

    @Binds
    @IntoMap
    @ProviderKey(ProviderId.DOUBAO)
    @Singleton
    abstract fun bindDoubao(impl: DoubaoProvider): LlmProvider
}
