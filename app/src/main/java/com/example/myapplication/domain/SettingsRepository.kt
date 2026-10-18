package com.example.myapplication.domain

import com.example.myapplication.domain.model.ProviderConfig
import com.example.myapplication.domain.model.ProviderId
import com.example.myapplication.domain.model.SelectedModel
import com.example.myapplication.domain.model.SpeechPreferences
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun observe(provider: ProviderId): Flow<ProviderConfig>

    suspend fun get(provider: ProviderId): ProviderConfig

    suspend fun save(config: ProviderConfig)

    /** 新建对话的默认选择。还没有成功发送记录时，用已填写密钥的服务商默认模型。 */
    suspend fun startingModel(): SelectedModel

    /** 一次回复成功结束后记住模型，供下次新建对话直接使用。 */
    suspend fun rememberRecent(provider: ProviderId, model: String)

    fun observeSpeech(): Flow<SpeechPreferences>

    suspend fun speech(): SpeechPreferences

    suspend fun saveSpeech(preferences: SpeechPreferences)

    /** 连续点选语言或音色时，在同一把锁里读改写，避免后一次覆盖前一次。 */
    suspend fun editSpeech(transform: (SpeechPreferences) -> SpeechPreferences)
}
