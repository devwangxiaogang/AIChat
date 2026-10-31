package com.yuguang.lumo.di

import com.yuguang.lumo.data.ChatRepositoryImpl
import com.yuguang.lumo.data.ModelCatalogRepositoryImpl
import com.yuguang.lumo.data.SettingsRepositoryImpl
import com.yuguang.lumo.data.remote.DeepSeekProvider
import com.yuguang.lumo.data.remote.DoubaoProvider
import com.yuguang.lumo.domain.ChatRepository
import com.yuguang.lumo.domain.LlmProvider
import com.yuguang.lumo.domain.ModelCatalogRepository
import com.yuguang.lumo.domain.SettingsRepository
import com.yuguang.lumo.domain.model.ProviderId
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
