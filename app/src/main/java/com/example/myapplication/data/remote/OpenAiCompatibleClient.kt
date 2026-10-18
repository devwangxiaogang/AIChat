package com.example.myapplication.data.remote

import com.example.myapplication.domain.model.StreamEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/** DeepSeek 与豆包方舟都使用 OpenAI 兼容的流式 chat/completions。 */
@Singleton
class OpenAiCompatibleClient @Inject constructor(
    private val http: OkHttpClient,
    private val json: Json,
) {
    fun stream(url: String, apiKey: String, body: JsonObject): Flow<StreamEvent> = flow {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .header("Accept", "text/event-stream")
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()
        val call = http.newCall(request)
        // 用户停止生成时取消正在进行的 HTTP 调用，避免继续占用连接。
        currentCoroutineContext().job.invokeOnCompletion { cause ->
            if (cause != null) call.cancel()
        }
        try {
            call.execute().use { response ->
                val payload = response.body
                if (payload == null) {
                    emit(StreamEvent.Failed("服务端没有返回内容"))
                    return@flow
                }
                if (!response.isSuccessful) {
                    emit(StreamEvent.Failed(errorMessage(response.code, payload.string())))
                    return@flow
                }
                val contentType = response.header("Content-Type").orEmpty()
                if (!contentType.contains("event-stream")) {
                    emitNonStream(payload.string())
                    return@flow
                }
                val source = payload.source()
                var promptTokens: Int? = null
                var completionTokens: Int? = null
                while (currentCoroutineContext().isActive && !source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    for (piece in SseDecoder.decode(line)) {
                        when (piece) {
                            is ChunkPiece.Delta -> emit(StreamEvent.Delta(piece.text))
                            is ChunkPiece.Reasoning -> emit(StreamEvent.Reasoning(piece.text))
                            is ChunkPiece.Usage -> {
                                promptTokens = piece.promptTokens ?: promptTokens
                                completionTokens = piece.completionTokens ?: completionTokens
                            }
                            ChunkPiece.Done -> {
                                emit(StreamEvent.Completed(promptTokens, completionTokens))
                                return@flow
                            }
                            is ChunkPiece.Invalid -> {
                                emit(StreamEvent.Failed(piece.message))
                                return@flow
                            }
                        }
                    }
                }
                emit(StreamEvent.Completed(promptTokens, completionTokens))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (!currentCoroutineContext().isActive) {
                throw CancellationException("cancelled", error)
            }
            emit(StreamEvent.Failed(error.message?.takeIf { it.isNotBlank() } ?: "网络请求失败"))
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun kotlinx.coroutines.flow.FlowCollector<StreamEvent>.emitNonStream(raw: String) {
        val text = runCatching {
            json.decodeFromString<ChatChunk>(raw).choices.firstOrNull()?.message?.content
        }.getOrNull()
        if (!text.isNullOrBlank()) {
            emit(StreamEvent.Delta(text))
            emit(StreamEvent.Completed(null, null))
        } else {
            emit(StreamEvent.Failed(errorMessage(200, raw)))
        }
    }

    private fun errorMessage(code: Int, raw: String): String {
        val parsed = runCatching {
            json.decodeFromString<ApiErrorBody>(raw).error?.message
        }.getOrNull()
        return parsed?.takeIf { it.isNotBlank() } ?: "请求失败（$code）"
    }

    private companion object {
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
