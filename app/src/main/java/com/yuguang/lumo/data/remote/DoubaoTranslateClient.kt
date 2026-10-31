package com.yuguang.lumo.data.remote

import android.content.Context
import com.yuguang.lumo.R
import com.yuguang.lumo.domain.model.ProviderId
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/** 方舟豆包翻译模型。走 responses 接口，不用对话的 chat/completions。 */
@Singleton
class DoubaoTranslateClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val http: OkHttpClient,
    private val json: Json,
) {
    suspend fun translate(
        apiKey: String,
        baseUrl: String,
        model: String,
        text: String,
        sourceLanguage: String,
        targetLanguage: String,
    ): String = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("model", model)
            put(
                "input",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("role", "user")
                            put(
                                "content",
                                buildJsonArray {
                                    add(
                                        buildJsonObject {
                                            put("type", "input_text")
                                            put("text", text)
                                            put(
                                                "translation_options",
                                                buildJsonObject {
                                                    if (sourceLanguage.isNotBlank()) {
                                                        put("source_language", sourceLanguage)
                                                    }
                                                    put("target_language", targetLanguage)
                                                },
                                            )
                                        },
                                    )
                                },
                            )
                        },
                    )
                },
            )
        }
        val request = Request.Builder()
            .url(responsesUrl(baseUrl))
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON))
            .build()
        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            val root = runCatching { json.parseToJsonElement(raw) }.getOrNull()
            if (!response.isSuccessful) {
                val detail = root?.errorMessage()?.ifBlank { null } ?: "HTTP ${response.code}"
                val lower = detail.lowercase()
                val mapped = when {
                    lower.contains("overdue") || lower.contains("insufficient balance") ||
                        lower.contains("arrear") || lower.contains("欠费") ->
                        context.getString(R.string.error_account_overdue)
                    response.code == 401 -> context.getString(R.string.error_api_key_invalid)
                    response.code == 403 -> context.getString(R.string.error_forbidden)
                    else -> context.getString(R.string.translate_failed_detail, detail)
                }
                error(mapped)
            }
            root?.outputText()?.trim().orEmpty().ifBlank { error(context.getString(R.string.translate_empty)) }
        }
    }

    private fun responsesUrl(baseUrl: String): String {
        val root = baseUrl.trim().ifBlank { ProviderId.DOUBAO.defaultBaseUrl }
            .trimEnd('/')
            .removeSuffix("/chat/completions")
        return if (root.endsWith("/responses")) root else "$root/responses"
    }

    private fun JsonElement.outputText(): String {
        var found = ""
        fun walk(element: JsonElement) {
            when (element) {
                is JsonObject -> {
                    val type = element["type"]?.jsonPrimitive?.contentOrNull
                    val text = element["text"]?.jsonPrimitive?.contentOrNull
                    if (type == "output_text" && !text.isNullOrBlank()) found = text
                    element.values.forEach(::walk)
                }
                is JsonArray -> element.forEach(::walk)
                else -> Unit
            }
        }
        walk(this)
        return found
    }

    private fun JsonElement.errorMessage(): String {
        if (this !is JsonObject) return ""
        val error = this["error"]
        if (error is JsonObject) {
            return error["message"]?.jsonPrimitive?.contentOrNull.orEmpty()
        }
        if (error is JsonPrimitive) return error.contentOrNull.orEmpty()
        return this["message"]?.jsonPrimitive?.contentOrNull.orEmpty()
    }

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}
