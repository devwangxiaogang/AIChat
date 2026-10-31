package com.example.myapplication.data

import android.content.SharedPreferences
import com.example.myapplication.domain.SettingsRepository
import com.example.myapplication.domain.model.ProviderConfig
import com.example.myapplication.domain.model.ProviderId
import com.example.myapplication.domain.model.SelectedModel
import com.example.myapplication.domain.model.SpeechPreferences
import com.example.myapplication.domain.model.TtsEngine
import com.example.myapplication.domain.model.VoiceChoice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val prefs: SharedPreferences,
) : SettingsRepository {
    private val state = MutableStateFlow(readAll())
    private val speechState = MutableStateFlow(readSpeech())
    private val speechEdit = Mutex()

    override fun observe(provider: ProviderId): Flow<ProviderConfig> =
        state.map { configs -> configs.getValue(provider) }

    override suspend fun get(provider: ProviderId): ProviderConfig = state.value.getValue(provider)

    override suspend fun save(config: ProviderConfig) {
        prefs.edit()
            .putString(key(config.provider, "apiKey"), config.apiKey.trim())
            .putString(key(config.provider, "baseUrl"), config.baseUrl.trim())
            .putString(key(config.provider, "model"), config.model.trim())
            .putString(key(config.provider, "speechApiKey"), config.speechApiKey.trim())
            .putString(key(config.provider, "speechAppId"), config.speechAppId.trim())
            .putString(key(config.provider, "speechAccessToken"), config.speechAccessToken.trim())
            .apply()
        state.value = readAll()
    }

    override suspend fun startingModel(): SelectedModel {
        val provider = prefs.getString(RECENT_PROVIDER, null)
            ?.let { name -> runCatching { ProviderId.valueOf(name) }.getOrNull() }
        val model = prefs.getString(RECENT_MODEL, null)?.takeIf { it.isNotBlank() }
        if (provider != null && model != null) return SelectedModel(provider, model)
        val configured = ProviderId.entries.firstOrNull { get(it).apiKey.isNotBlank() } ?: ProviderId.DOUBAO
        val config = get(configured)
        return SelectedModel(configured, config.model.ifBlank { configured.defaultModel })
    }

    override suspend fun rememberRecent(provider: ProviderId, model: String) {
        val chosen = model.trim()
        if (chosen.isEmpty()) return
        prefs.edit()
            .putString(RECENT_PROVIDER, provider.name)
            .putString(RECENT_MODEL, chosen)
            .apply()
    }

    override fun observeSpeech(): Flow<SpeechPreferences> = speechState

    override suspend fun speech(): SpeechPreferences = speechState.value

    override suspend fun saveSpeech(preferences: SpeechPreferences) = speechEdit.withLock {
        writeSpeech(preferences)
    }

    override suspend fun editSpeech(transform: (SpeechPreferences) -> SpeechPreferences) = speechEdit.withLock {
        writeSpeech(transform(speechState.value))
    }

    private fun writeSpeech(preferences: SpeechPreferences) {
        val saved = preferences.copy(translationModel = preferences.translationModel.trim())
        speechState.value = saved
        prefs.edit()
            .putString(TTS_ENGINE, saved.engine.name)
            .putString(TTS_SPEAKER, saved.speaker.name)
            .putString(TRANSLATION_MODEL, saved.translationModel)
            .putString(TEXT_SOURCE, saved.textSource)
            .putString(TEXT_TARGET, saved.textTarget)
            .putString(LIVE_SOURCE, saved.liveSource)
            .putString(LIVE_TARGET, saved.liveTarget)
            .putString(LIVE_VOICE, saved.liveVoice.name)
            .apply()
    }

    private fun readAll(): Map<ProviderId, ProviderConfig> = runCatching {
        ProviderId.entries.associateWith { provider ->
            ProviderConfig(
                provider = provider,
                apiKey = prefs.getString(key(provider, "apiKey"), "").orEmpty(),
                baseUrl = prefs.getString(key(provider, "baseUrl"), null)
                    ?.takeIf { it.isNotBlank() }
                    ?: provider.defaultBaseUrl,
                model = prefs.getString(key(provider, "model"), null)
                    ?.takeIf { it.isNotBlank() }
                    ?: provider.defaultModel,
                speechApiKey = prefs.getString(key(provider, "speechApiKey"), "").orEmpty(),
                speechAppId = prefs.getString(key(provider, "speechAppId"), "").orEmpty(),
                speechAccessToken = prefs.getString(key(provider, "speechAccessToken"), "").orEmpty(),
            )
        }
    }.getOrElse {
        // 备份恢复到新设备后 Keystore 主密钥已经不同，加密偏好可能解不开。
        // 与其让设置页打不开，不如清空回默认值，让用户重新填写密钥。
        resetPreferences()
        defaultConfigs()
    }

    private fun defaultConfigs(): Map<ProviderId, ProviderConfig> =
        ProviderId.entries.associateWith { provider ->
            ProviderConfig(
                provider = provider,
                apiKey = "",
                baseUrl = provider.defaultBaseUrl,
                model = provider.defaultModel,
                speechApiKey = "",
            )
        }

    private fun readSpeech(): SpeechPreferences {
        val defaults = SpeechPreferences()
        return runCatching {
            SpeechPreferences(
                engine = enumOr(prefs.getString(TTS_ENGINE, null), defaults.engine),
                speaker = enumOr(prefs.getString(TTS_SPEAKER, null), defaults.speaker).readingVoice(),
                translationModel = prefs.getString(TRANSLATION_MODEL, null)?.takeIf { it.isNotBlank() }
                    ?: defaults.translationModel,
                textSource = prefs.getString(TEXT_SOURCE, null) ?: defaults.textSource,
                textTarget = prefs.getString(TEXT_TARGET, null)?.takeIf { it.isNotBlank() } ?: defaults.textTarget,
                liveSource = prefs.getString(LIVE_SOURCE, null)?.takeIf { it.isNotBlank() } ?: defaults.liveSource,
                liveTarget = prefs.getString(LIVE_TARGET, null)?.takeIf { it.isNotBlank() } ?: defaults.liveTarget,
                liveVoice = enumOr(prefs.getString(LIVE_VOICE, null), defaults.liveVoice),
            )
        }.getOrElse {
            resetPreferences()
            defaults
        }
    }

    private fun resetPreferences() {
        runCatching { prefs.edit().clear().apply() }
    }

    private fun VoiceChoice.readingVoice(): VoiceChoice =
        if (this == VoiceChoice.CLONE) VoiceChoice.FEMALE else this

    private inline fun <reified T : Enum<T>> enumOr(name: String?, fallback: T): T =
        name?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: fallback

    private fun key(provider: ProviderId, field: String) = "${provider.name}_$field"

    private companion object {
        const val RECENT_PROVIDER = "recentProvider"
        const val RECENT_MODEL = "recentModel"
        const val TTS_ENGINE = "ttsEngine"
        const val TTS_SPEAKER = "ttsSpeaker"
        const val TRANSLATION_MODEL = "translationModel"
        const val TEXT_SOURCE = "textSourceLang"
        const val TEXT_TARGET = "textTargetLang"
        const val LIVE_SOURCE = "liveSourceLang"
        const val LIVE_TARGET = "liveTargetLang"
        const val LIVE_VOICE = "liveVoice"
    }
}
