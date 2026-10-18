package com.example.myapplication.data.remote

import com.example.myapplication.domain.model.ProviderId

/** 设置里可以只填服务根地址，也可以填完整的 chat/completions 地址。 */
object EndpointUrls {
    fun chatCompletions(baseUrl: String, provider: ProviderId): String {
        val trimmed = baseUrl.trim().ifBlank { provider.defaultBaseUrl }.trimEnd('/')
        return if (trimmed.endsWith("/chat/completions")) trimmed else "$trimmed/chat/completions"
    }
}
