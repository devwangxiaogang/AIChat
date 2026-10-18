package com.example.myapplication.data

import android.content.SharedPreferences
import com.example.myapplication.domain.SettingsRepository
import com.example.myapplication.domain.model.ProviderConfig
import com.example.myapplication.domain.model.ProviderId
import com.example.myapplication.domain.model.SelectedModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val prefs: SharedPreferences,
) : SettingsRepository {
    private val state = MutableStateFlow(readAll())

    override fun observe(provider: ProviderId): Flow<ProviderConfig> =
        state.map { configs -> configs.getValue(provider) }

    override suspend fun get(provider: ProviderId): ProviderConfig = state.value.getValue(provider)

    override suspend fun save(config: ProviderConfig) {
        prefs.edit()
            .putString(key(config.provider, "apiKey"), config.apiKey.trim())
            .putString(key(config.provider, "baseUrl"), config.baseUrl.trim())
            .putString(key(config.provider, "model"), config.model.trim())
            .putString(key(config.provider, "speechApiKey"), config.speechApiKey.trim())
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

    private fun readAll(): Map<ProviderId, ProviderConfig> =
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
            )
        }

    private fun key(provider: ProviderId, field: String) = "${provider.name}_$field"

    private companion object {
        const val RECENT_PROVIDER = "recentProvider"
        const val RECENT_MODEL = "recentModel"
    }
}
