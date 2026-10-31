package com.yuguang.lumo.data.remote

import com.yuguang.lumo.domain.model.ModelCapabilities
import com.yuguang.lumo.domain.model.ModelChoice
import com.yuguang.lumo.domain.model.ModelGroup
import com.yuguang.lumo.domain.model.ProviderId
import com.yuguang.lumo.domain.model.modelChoices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelCatalogClient @Inject constructor(
    private val http: OkHttpClient,
    private val json: Json,
) {
    suspend fun fetch(provider: ProviderId, apiKey: String, baseUrl: String): List<ModelChoice> =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank()) return@withContext provider.modelChoices()
            val request = Request.Builder()
                .url(EndpointUrls.models(baseUrl, provider))
                .header("Authorization", "Bearer $apiKey")
                .get()
                .build()
            val raw = http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext provider.modelChoices()
                response.body?.string().orEmpty()
            }
            val parsed = runCatching {
                json.decodeFromString<ModelsListResponse>(raw)
            }.getOrNull() ?: return@withContext provider.modelChoices()
            when (provider) {
                ProviderId.DEEPSEEK -> mapDeepSeek(parsed.data)
                ProviderId.DOUBAO -> mapArk(parsed.data)
            }.ifEmpty { provider.modelChoices() }
        }

    private fun mapDeepSeek(items: List<RemoteModelDto>): List<ModelChoice> =
        items.map { item ->
            val vision = item.inputModalities.orEmpty().any { it.equals("image", ignoreCase = true) } ||
                ModelCapabilities.inferVision(ProviderId.DEEPSEEK, item.id)
            ModelChoice(
                provider = ProviderId.DEEPSEEK,
                id = item.id,
                label = item.name?.takeIf { it.isNotBlank() } ?: item.id,
                vision = vision,
                imageGen = false,
                groupRes = ModelGroup.OFFICIAL.labelRes,
            )
        }.sortedBy { it.id }

    private fun mapArk(items: List<RemoteModelDto>): List<ModelChoice> {
        val kept = items
            .map { it.id }
            .filter { ModelCapabilities.arkKeep(it) }
            .distinct()
        val latestByFamily = kept
            .groupBy { ModelCapabilities.familyKey(it) }
            .mapValues { (_, ids) ->
                ids.maxWithOrNull(
                    compareBy<String> { ModelCapabilities.versionKey(it) }
                        .thenBy { it },
                )!!
            }
            .values
            .toSet()
        return latestByFamily.map { id ->
            val imageGen = ModelCapabilities.inferImageGen(id)
            val vision = !imageGen && ModelCapabilities.inferVision(ProviderId.DOUBAO, id)
            ModelChoice(
                provider = ProviderId.DOUBAO,
                id = id,
                label = prettyLabel(id),
                vision = vision,
                imageGen = imageGen,
                groupRes = when {
                    imageGen -> ModelGroup.SEEDREAM.labelRes
                    id.startsWith("deepseek", ignoreCase = true) -> ModelGroup.ARK_DEEPSEEK.labelRes
                    id.startsWith("glm", ignoreCase = true) -> ModelGroup.ARK_GLM.labelRes
                    else -> ModelGroup.SEED.labelRes
                },
            )
        }.sortedWith(
            compareBy<ModelChoice> { it.groupRes }
                .thenByDescending { it.imageGen }
                .thenByDescending { it.vision }
                .thenBy { it.label },
        )
    }

    private fun prettyLabel(id: String): String {
        val withoutDate = id.replace(Regex("""-\d{6}$"""), "")
        return withoutDate
            .removePrefix("doubao-")
            .replace('-', ' ')
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}

@Serializable
private data class ModelsListResponse(
    val data: List<RemoteModelDto> = emptyList(),
)

@Serializable
private data class RemoteModelDto(
    val id: String,
    val name: String? = null,
    @SerialName("input_modalities") val inputModalities: List<String>? = null,
    @SerialName("output_modalities") val outputModalities: List<String>? = null,
)
