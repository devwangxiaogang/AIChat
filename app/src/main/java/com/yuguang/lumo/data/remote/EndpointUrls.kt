package com.yuguang.lumo.data.remote

import com.yuguang.lumo.domain.model.ProviderId

/** 设置里可以只填服务根地址，也可以填完整的 chat/completions 地址。 */
object EndpointUrls {
    /**
     * 非本机的 `http://` 自动升为 `https://`，降低密钥打到明文 HTTP 端点的风险。
     * `localhost` / `127.0.0.1` 仍允许 HTTP，方便本地调试。
     */
    fun normalizeBaseUrl(baseUrl: String): String {
        val trimmed = baseUrl.trim()
        if (trimmed.isEmpty()) return trimmed
        val lower = trimmed.lowercase()
        if (lower.startsWith("https://")) return trimmed
        if (lower.startsWith("http://localhost") ||
            lower.startsWith("http://127.0.0.1") ||
            lower.startsWith("http://[::1]")
        ) {
            return trimmed
        }
        if (lower.startsWith("http://")) {
            return "https://" + trimmed.substring(7)
        }
        return trimmed
    }

    fun root(baseUrl: String, provider: ProviderId): String {
        val trimmed = normalizeBaseUrl(baseUrl).ifBlank { provider.defaultBaseUrl }.trimEnd('/')
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
