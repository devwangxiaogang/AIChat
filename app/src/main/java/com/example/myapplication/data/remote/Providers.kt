package com.example.myapplication.data.remote

import com.example.myapplication.domain.LlmProvider
import com.example.myapplication.domain.model.ChatRequest
import com.example.myapplication.domain.model.ProviderId
import com.example.myapplication.domain.model.Role
import com.example.myapplication.domain.model.StreamEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeepSeekProvider @Inject constructor(
    private val client: OpenAiCompatibleClient,
) : LlmProvider {
    override val id: ProviderId = ProviderId.DEEPSEEK

    override fun stream(apiKey: String, baseUrl: String, request: ChatRequest): Flow<StreamEvent> {
        return client.stream(
            url = EndpointUrls.chatCompletions(baseUrl, id),
            apiKey = apiKey,
            body = chatBody(request),
        )
    }
}

/** 方舟在通用流式请求上额外要求返回 token 用量。 */
@Singleton
class DoubaoProvider @Inject constructor(
    private val client: OpenAiCompatibleClient,
) : LlmProvider {
    override val id: ProviderId = ProviderId.DOUBAO

    override fun stream(apiKey: String, baseUrl: String, request: ChatRequest): Flow<StreamEvent> {
        val body = buildJsonObject {
            chatBody(request).forEach { (key, value) -> put(key, value) }
            putJsonObject("stream_options") {
                put("include_usage", true)
            }
        }
        return client.stream(
            url = EndpointUrls.chatCompletions(baseUrl, id),
            apiKey = apiKey,
            body = body,
        )
    }
}

private fun chatBody(request: ChatRequest): JsonObject = buildJsonObject {
    put("model", request.model)
    put("stream", true)
    putJsonArray("messages") {
        request.messages.forEach { message ->
            addJsonObject {
                put("role", message.role.wireName())
                put("content", message.content)
            }
        }
    }
}

private fun Role.wireName(): String = when (this) {
    Role.SYSTEM -> "system"
    Role.USER -> "user"
    Role.ASSISTANT -> "assistant"
}
