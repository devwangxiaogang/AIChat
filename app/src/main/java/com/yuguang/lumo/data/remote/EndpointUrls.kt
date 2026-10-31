package com.yuguang.lumo.data.remote

import com.yuguang.lumo.domain.model.ProviderId

/** 设置里可以只填服务根地址，也可以填完整的 chat/completions 地址。 */
object EndpointUrls {
    fun root(baseUrl: String, provider: ProviderId): String {
        val trimmed = baseUrl.trim().ifBlank { provider.defaultBaseUrl }.trimEnd('/')
        return when {
            trimmed.endsWith("/chat/completions") -> trimmed.removeSuffix("/chat/completions")
            trimmed.endsWith("/images/generations") -> trimmed.removeSuffix("/images/generations")
            trimmed.endsWith("/models") -> trimmed.removeSuffix("/models")
            else -> trimmed
        }
    }

    fun chatCompletions(baseUrl: String, provider: ProviderId): String =
        "${root(baseUrl, provider)}/chat/completions"

    fun models(baseUrl: String, provider: ProviderId): String =
        "${root(baseUrl, provider)}/models"

    fun imageGenerations(baseUrl: String, provider: ProviderId = ProviderId.DOUBAO): String =
        "${root(baseUrl, provider)}/images/generations"
}
