package com.example.myapplication.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

sealed interface ChunkPiece {
    data class Delta(val text: String) : ChunkPiece
    data class Reasoning(val text: String) : ChunkPiece
    data class Usage(val promptTokens: Int?, val completionTokens: Int?) : ChunkPiece
    data object Done : ChunkPiece
    data class Invalid(val message: String) : ChunkPiece
}

/** 解析 text/event-stream 的 data 行，兼容正文增量、思考内容和用量。 */
object SseDecoder {
    private val json = Json { ignoreUnknownKeys = true }

    fun decode(line: String): List<ChunkPiece> {
        val trimmed = line.trim()
        if (!trimmed.startsWith("data:")) return emptyList()
        val data = trimmed.removePrefix("data:").trim()
        if (data == "[DONE]") return listOf(ChunkPiece.Done)
        if (data.isEmpty()) return emptyList()
        val chunk = try {
            json.decodeFromString<ChatChunk>(data)
        } catch (error: Exception) {
            return listOf(ChunkPiece.Invalid(error.message ?: "无法解析模型响应"))
        }
        val apiError = chunk.error?.message
        if (!apiError.isNullOrBlank()) return listOf(ChunkPiece.Invalid(apiError))
        val pieces = mutableListOf<ChunkPiece>()
        val delta = chunk.choices.firstOrNull()?.delta
        val content = delta?.content ?: chunk.choices.firstOrNull()?.message?.content
        if (!content.isNullOrEmpty()) pieces += ChunkPiece.Delta(content)
        val reasoning = delta?.reasoningContent
        if (!reasoning.isNullOrEmpty()) pieces += ChunkPiece.Reasoning(reasoning)
        chunk.usage?.let { usage ->
            pieces += ChunkPiece.Usage(usage.promptTokens, usage.completionTokens)
        }
        return pieces
    }
}

@Serializable
data class ChatChunk(
    val choices: List<ChoiceDto> = emptyList(),
    val usage: UsageDto? = null,
    val error: ApiErrorDetail? = null,
)

@Serializable
data class ChoiceDto(
    val delta: DeltaDto? = null,
    val message: MessageDto? = null,
)

@Serializable
data class DeltaDto(
    val content: String? = null,
    @SerialName("reasoning_content") val reasoningContent: String? = null,
)

@Serializable
data class MessageDto(
    val content: String? = null,
)

@Serializable
data class UsageDto(
    @SerialName("prompt_tokens") val promptTokens: Int? = null,
    @SerialName("completion_tokens") val completionTokens: Int? = null,
)

@Serializable
data class ApiErrorBody(
    val error: ApiErrorDetail? = null,
)

@Serializable
data class ApiErrorDetail(
    val message: String? = null,
)
