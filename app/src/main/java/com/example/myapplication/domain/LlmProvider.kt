package com.example.myapplication.domain

import com.example.myapplication.domain.model.ChatRequest
import com.example.myapplication.domain.model.ProviderId
import com.example.myapplication.domain.model.StreamEvent
import kotlinx.coroutines.flow.Flow

/** 一个服务商的流式对话。密钥和地址由设置传入，不写死在实现里。 */
interface LlmProvider {
    val id: ProviderId

    fun stream(apiKey: String, baseUrl: String, request: ChatRequest): Flow<StreamEvent>
}
