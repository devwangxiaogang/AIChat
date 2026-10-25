package com.example.myapplication.data

import android.content.Context
import com.example.myapplication.R
import com.example.myapplication.data.remote.ModelCatalogClient
import com.example.myapplication.domain.ModelCatalogRepository
import com.example.myapplication.domain.SettingsRepository
import com.example.myapplication.domain.model.ModelCapabilities
import com.example.myapplication.domain.model.ModelChoice
import com.example.myapplication.domain.model.ModelGroup
import com.example.myapplication.domain.model.ProviderId
import com.example.myapplication.domain.model.defaultImageGenModel
import com.example.myapplication.domain.model.modelChoices
import com.example.myapplication.domain.model.staticSupportsVision
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelCatalogRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: ModelCatalogClient,
    private val settingsRepository: SettingsRepository,
    private val json: Json,
) : ModelCatalogRepository {
    private val mutex = Mutex()
    private val prefs = context.getSharedPreferences("model_catalog", Context.MODE_PRIVATE)
    private val cache = MutableStateFlow(loadCache())

    override fun observe(provider: ProviderId): Flow<List<ModelChoice>> =
        cache.map { map ->
            map[provider]?.takeIf { it.isNotEmpty() } ?: provider.modelChoices()
        }

    override fun supportsVision(provider: ProviderId, modelId: String): Boolean {
        val remote = cache.value[provider]?.firstOrNull { it.id == modelId }
        if (remote != null) return remote.vision
        return provider.staticSupportsVision(modelId)
    }

    override fun preferredImageGenModel(): String {
        val fromRemote = cache.value[ProviderId.DOUBAO]
            ?.filter { it.imageGen }
            ?.maxWithOrNull(compareBy { ModelCapabilities.versionKey(it.id) })
            ?.id
        return fromRemote ?: ProviderId.DOUBAO.defaultImageGenModel()
    }

    override suspend fun refresh(provider: ProviderId): String? = mutex.withLock {
        refreshLocked(provider)
    }

    override suspend fun refreshAll(): String? {
        var lastError: String? = null
        ProviderId.entries.forEach { provider ->
            val config = settingsRepository.get(provider)
            if (config.apiKey.isBlank()) return@forEach
            val error = refresh(provider)
            if (error != null) lastError = error
        }
        return lastError
    }

    /** 若缓存仍有效则跳过网络；force 时强制拉取。 */
    override suspend fun refreshIfStale(provider: ProviderId, force: Boolean): String? = mutex.withLock {
        if (!force && cache.value[provider].orEmpty().isNotEmpty() && !isExpired()) {
            return null
        }
        refreshLocked(provider)
    }

    private suspend fun refreshLocked(provider: ProviderId): String? {
        val config = settingsRepository.get(provider)
        if (config.apiKey.isBlank()) {
            return context.getString(R.string.need_provider_key, context.getString(provider.nameRes))
        }
        return try {
            val models = client.fetch(provider, config.apiKey, config.baseUrl)
            cache.update { it + (provider to models) }
            persist(cache.value)
            null
        } catch (error: Exception) {
            error.message?.takeIf { it.isNotBlank() } ?: context.getString(R.string.model_refresh_failed)
        }
    }

    private fun isExpired(): Boolean {
        val raw = prefs.getString(CACHE_KEY, null) ?: return true
        val parsed = runCatching { json.decodeFromString<CatalogCacheDto>(raw) }.getOrNull() ?: return true
        return System.currentTimeMillis() - parsed.savedAt > TTL_MS
    }

    private fun loadCache(): Map<ProviderId, List<ModelChoice>> {
        val raw = prefs.getString(CACHE_KEY, null) ?: return emptyMap()
        val parsed = runCatching {
            json.decodeFromString<CatalogCacheDto>(raw)
        }.getOrNull() ?: return emptyMap()
        if (System.currentTimeMillis() - parsed.savedAt > TTL_MS) return emptyMap()
        return parsed.entries.mapNotNull { entry ->
            val provider = runCatching { ProviderId.valueOf(entry.provider) }.getOrNull() ?: return@mapNotNull null
            provider to entry.models.map { dto ->
                ModelChoice(
                    provider = provider,
                    id = dto.id,
                    label = dto.label,
                    vision = dto.vision,
                    imageGen = dto.imageGen,
                    groupRes = ModelGroup.fromKey(dto.groupKey).labelRes,
                )
            }
        }.toMap()
    }

    private fun persist(map: Map<ProviderId, List<ModelChoice>>) {
        val dto = CatalogCacheDto(
            savedAt = System.currentTimeMillis(),
            entries = map.map { (provider, models) ->
                CatalogProviderDto(
                    provider = provider.name,
                    models = models.map {
                        CatalogModelDto(
                            id = it.id,
                            label = it.label,
                            vision = it.vision,
                            imageGen = it.imageGen,
                            groupKey = ModelGroup.fromLabelRes(it.groupRes).key,
                        )
                    },
                )
            },
        )
        prefs.edit().putString(CACHE_KEY, json.encodeToString(dto)).apply()
    }

    private companion object {
        const val CACHE_KEY = "model_catalog_v2"
        const val TTL_MS = 24L * 60L * 60L * 1000L
    }
}

@Serializable
private data class CatalogCacheDto(
    val savedAt: Long,
    val entries: List<CatalogProviderDto> = emptyList(),
)

@Serializable
private data class CatalogProviderDto(
    val provider: String,
    val models: List<CatalogModelDto> = emptyList(),
)

@Serializable
private data class CatalogModelDto(
    val id: String,
    val label: String,
    val vision: Boolean = false,
    val imageGen: Boolean = false,
    val groupKey: String = ModelGroup.OFFICIAL.key,
)
