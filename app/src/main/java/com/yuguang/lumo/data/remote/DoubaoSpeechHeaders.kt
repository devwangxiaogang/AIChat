package com.yuguang.lumo.data.remote

import okhttp3.Request

/**
 * 豆包语音鉴权统一规则：AppId + Access Token 同时存在时优先（旧版控制台），
 * 否则使用 X-Api-Key。ASR / TTS / AST / 实时语音共用，避免各客户端优先级不一致。
 */
internal object DoubaoSpeechHeaders {
    fun applyAuth(
        builder: Request.Builder,
        apiKey: String,
        appId: String,
        accessToken: String,
    ): Boolean {
        val app = appId.trim()
        val token = accessToken.trim()
        val key = apiKey.trim()
        return when {
            app.isNotEmpty() && token.isNotEmpty() -> {
                builder.header("X-Api-App-Key", app)
                builder.header("X-Api-Access-Key", token)
                true
            }
            key.isNotEmpty() -> {
                builder.header("X-Api-Key", key)
                true
            }
            else -> false
        }
    }
}
