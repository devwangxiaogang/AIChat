package com.yuguang.lumo.data.remote

import android.content.Context
import android.util.Base64
import com.yuguang.lumo.R
import com.yuguang.lumo.domain.model.ProviderId
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

data class GeneratedImage(
    val bytes: ByteArray?,
    val url: String?,
    val mime: String,
)

@Singleton
class SeedreamClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val http: OkHttpClient,
    private val json: Json,
) {
    suspend fun generate(
        apiKey: String,
        baseUrl: String,
        model: String,
        prompt: String,
        referenceDataUrls: List<String> = emptyList(),
    ): GeneratedImage = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("model", model)
            put("prompt", prompt)
            put("response_format", "url")
            put("watermark", false)
            if (referenceDataUrls.size == 1) {
                put("image", referenceDataUrls.first())
            } else if (referenceDataUrls.size > 1) {
                putJsonArray("image") {
                    referenceDataUrls.forEach { add(JsonPrimitive(it)) }
                }
            }
        }
        val request = Request.Builder()
            .url(EndpointUrls.imageGenerations(baseUrl, ProviderId.DOUBAO))
            .header("Authorization", "Bearer $apiKey")
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()
        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val parsed = runCatching {
                    json.decodeFromString<ApiErrorBody>(raw).error?.message
                }.getOrNull()
                error(parsed?.takeIf { it.isNotBlank() } ?: context.getString(R.string.request_failed_code, response.code))
            }
            val parsed = json.decodeFromString<ImageGenerationResponse>(raw)
            val item = parsed.data.firstOrNull()
                ?: error(context.getString(R.string.image_gen_empty))
            when {
                !item.b64Json.isNullOrBlank() -> GeneratedImage(
                    bytes = Base64.decode(item.b64Json, Base64.DEFAULT),
                    url = null,
                    mime = "image/jpeg",
                )
                !item.url.isNullOrBlank() -> GeneratedImage(
                    bytes = download(item.url),
                    url = item.url,
                    mime = "image/jpeg",
                )
                else -> error(context.getString(R.string.image_gen_empty))
            }
        }
    }

    private fun download(url: String): ByteArray {
        val request = Request.Builder().url(url).get().build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error(context.getString(R.string.request_failed_code, response.code))
            }
            return response.body?.bytes() ?: error(context.getString(R.string.image_gen_empty))
        }
    }

    private companion object {
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}

@Serializable
private data class ImageGenerationResponse(
    val data: List<ImageDataDto> = emptyList(),
)

@Serializable
private data class ImageDataDto(
    val url: String? = null,
    @SerialName("b64_json") val b64Json: String? = null,
)
