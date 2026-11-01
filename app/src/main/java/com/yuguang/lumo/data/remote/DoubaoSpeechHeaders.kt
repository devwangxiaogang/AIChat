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

    /**
     * 实时语音（Seeduplex）的鉴权与识别/合成不同：新版控制台只认 X-Api-Key，
     * 旧版控制台是 App-ID + Access-Key + 资源 ID + 固定 App-Key 四件套。
     * 参见 https://www.volcengine.com/docs/6561/1594356 的 Request Headers。
     */
    fun applyRealtimeAuth(
        builder: Request.Builder,
        apiKey: String,
        appId: String,
        accessToken: String,
    ): Boolean {
        val key = apiKey.trim()
        val app = appId.trim()
        val token = accessToken.trim()
        return when {
            key.isNotEmpty() -> {
                builder.header("X-Api-Key", key)
                true
            }
            app.isNotEmpty() && token.isNotEmpty() -> {
                builder.header("X-Api-App-ID", app)
                builder.header("X-Api-Access-Key", token)
                builder.header("X-Api-Resource-Id", REALTIME_RESOURCE_ID)
                builder.header("X-Api-App-Key", REALTIME_APP_KEY)
                true
            }
            else -> false
        }
    }

    /** 实时语音的资源 ID，和识别/合成的 resource id 不是同一套。 */
    private const val REALTIME_RESOURCE_ID = "volc.speech.dialog"

    /** 协议里的固定值，不是用户自己的 AppID。 */
    private const val REALTIME_APP_KEY = "PlgvMymc7f3tQnJ6"
}
