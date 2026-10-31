package com.example.myapplication.data.remote

import android.content.Context
import com.example.myapplication.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.Base64
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

sealed interface VoiceChatEvent {
    data object SessionStarted : VoiceChatEvent

    /** 用户说话的实时文本；[final] 为 true 表示这一句已定稿。 */
    data class UserText(val text: String, val final: Boolean) : VoiceChatEvent

    /** 模型回复的一句话（显示用）。 */
    data class AssistantText(val text: String) : VoiceChatEvent

    /** 模型回复的 PCM 音频（24kHz / 单声道 / 16bit 小端）。 */
    data class Audio(val pcm: ByteArray) : VoiceChatEvent

    data object AssistantAudioDone : VoiceChatEvent
    data object Ended : VoiceChatEvent
    data class Failed(val message: String) : VoiceChatEvent
}

/**
 * 豆包实时语音模型 3.0（Seeduplex）全双工端到端客户端。
 *
 * 接口：wss://openspeech.bytedance.com/api/v3/duplex/realtime/dialogue
 * 协议：JSON 文本事件（session.create / input_audio_buffer.append / response.cancel ...），
 * 上行音频 16k 单声道 16bit、20ms 一包；下行音频 24k 单声道 16bit，Base64 分片。
 */
@Singleton
class DoubaoVoiceChatClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val http: OkHttpClient,
    private val json: Json,
) {
    suspend fun connect(
        apiKey: String,
        appId: String,
        accessToken: String,
        voice: String,
        instructions: String,
        onEvent: (VoiceChatEvent) -> Unit,
    ): VoiceSession = withContext(Dispatchers.IO) {
        val ready = CompletableDeferred<Unit>()
        val connectId = UUID.randomUUID().toString()
        val requestBuilder = Request.Builder()
            .url(ENDPOINT)
            .header("X-Api-Connect-Id", connectId)
            .header("X-Api-Request-Id", UUID.randomUUID().toString())
        val legacyAppKey = appId.trim()
        val legacyToken = accessToken.trim()
        if (apiKey.isNotBlank()) {
            requestBuilder.header("X-Api-Key", apiKey.trim())
        } else if (legacyAppKey.isNotEmpty() && legacyToken.isNotEmpty()) {
            requestBuilder
                .header("X-Api-App-Key", legacyAppKey)
                .header("X-Api-Access-Key", legacyToken)
        } else {
            error(context.getString(R.string.need_speech_key))
        }

        val session = object : WebSocketListener() {
            @Volatile
            var socket: WebSocket? = null

            override fun onOpen(webSocket: WebSocket, response: Response) {
                socket = webSocket
                webSocket.send(sessionCreate(voice, instructions))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
                when (val type = obj["type"]?.jsonPrimitive?.contentOrNull.orEmpty()) {
                    "session.created" -> {
                        ready.complete(Unit)
                        onEvent(VoiceChatEvent.SessionStarted)
                    }
                    "conversation.item.input_audio_transcription.started" -> Unit
                    "conversation.item.input_audio_transcription.delta" -> {
                        val delta = obj["delta"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        if (delta.isNotBlank()) onEvent(VoiceChatEvent.UserText(delta, false))
                    }
                    "conversation.item.input_audio_transcription.completed" -> {
                        val text = obj["transcript"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        if (text.isNotBlank()) onEvent(VoiceChatEvent.UserText(text, true))
                    }
                    "conversation.item.input_audio_transcription.failed" -> {
                        val message = obj.errorMessage()
                        onEvent(VoiceChatEvent.Failed(message.ifBlank { context.getString(R.string.voice_failed) }))
                    }
                    "response.output_text.delta" -> {
                        val delta = obj["delta"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        if (delta.isNotBlank()) onEvent(VoiceChatEvent.AssistantText(delta))
                    }
                    "response.output_audio.delta" -> {
                        val delta = obj["delta"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        if (delta.isNotEmpty()) {
                            val pcm = runCatching { Base64.getDecoder().decode(delta) }.getOrNull()
                            if (pcm != null && pcm.isNotEmpty()) onEvent(VoiceChatEvent.Audio(pcm))
                        }
                    }
                    "response.output_audio.done" -> onEvent(VoiceChatEvent.AssistantAudioDone)
                    "response.done", "response.canceled" -> Unit
                    "session.closed" -> {
                        if (!ready.isCompleted) ready.completeExceptionally(IllegalStateException(context.getString(R.string.voice_failed)))
                        onEvent(VoiceChatEvent.Ended)
                    }
                    "error" -> {
                        val message = obj.errorMessage().ifBlank { context.getString(R.string.voice_failed) }
                        if (!ready.isCompleted) ready.completeExceptionally(IllegalStateException(message))
                        onEvent(VoiceChatEvent.Failed(message))
                    }
                    else -> Unit
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val logId = response?.header("X-Tt-Logid").orEmpty()
                val detail = describeFailure(response?.code ?: 0, t.message.orEmpty(), logId)
                if (!ready.isCompleted) ready.completeExceptionally(IllegalStateException(detail, t))
                onEvent(VoiceChatEvent.Failed(detail))
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!ready.isCompleted) ready.completeExceptionally(IllegalStateException(context.getString(R.string.voice_failed)))
                onEvent(VoiceChatEvent.Ended)
            }
        }

        val socket = http.newWebSocket(requestBuilder.build(), session)
        withTimeout(SESSION_TIMEOUT_MS) { ready.await() }
        VoiceSession(socket)
    }

    private fun describeFailure(code: Int, message: String, logId: String): String {
        val base = when (code) {
            401 -> context.getString(R.string.error_api_key_invalid)
            403 -> context.getString(R.string.error_forbidden)
            else -> message.ifBlank { context.getString(R.string.voice_connect_failed) }
        }
        return if (logId.isBlank()) base else "$base（$logId）"
    }

    private fun sessionCreate(voice: String, instructions: String): String = buildJsonObject {
        put("type", "session.create")
        putJsonObject("session") {
            put("model", MODEL)
            if (instructions.isNotBlank()) put("instructions", instructions)
            putJsonObject("audio") {
                putJsonObject("input") {
                    putJsonObject("format") {
                        put("type", "pcm")
                        put("rate", 16_000)
                    }
                }
                putJsonObject("output") {
                    putJsonObject("format") {
                        put("type", "pcm_s16le")
                        put("rate", 24_000)
                    }
                    put("voice", voice.ifBlank { DEFAULT_VOICE })
                    put("speed", 0)
                    put("loudness", 0)
                }
            }
        }
    }.toString()

    private fun JsonObject.errorMessage(): String {
        val error = this["error"]
        if (error is JsonObject) {
            return error["message"]?.jsonPrimitive?.contentOrNull.orEmpty()
        }
        return this["message"]?.jsonPrimitive?.contentOrNull.orEmpty()
    }

    private companion object {
        const val ENDPOINT = "wss://openspeech.bytedance.com/api/v3/duplex/realtime/dialogue"
        const val MODEL = "1.2.6.1"
        const val DEFAULT_VOICE = "zh_female_vv_jupiter_bigtts"
        const val SESSION_TIMEOUT_MS = 10_000L
    }
}

/** 建好的会话：按住说话时持续 append，松开后 commit；也可以直接发文本。 */
class VoiceSession internal constructor(private val socket: WebSocket) {
    fun appendAudio(pcm: ByteArray) {
        if (pcm.isEmpty()) return
        val encoded = Base64.getEncoder().encodeToString(pcm)
        socket.send(
            buildJsonObject {
                put("event_id", UUID.randomUUID().toString())
                put("type", "input_audio_buffer.append")
                put("audio", encoded)
            }.toString(),
        )
    }

    /** 松开后强制判停，让模型立刻开始回应。 */
    fun commitAudio() {
        socket.send(buildJsonObject { put("type", "input_audio_buffer.commit") }.toString())
    }

    fun mute() {
        socket.send(buildJsonObject { put("type", "input_audio_mute.commit") }.toString())
    }

    fun unmute() {
        socket.send(buildJsonObject { put("type", "input_audio_unmute.commit") }.toString())
    }

    fun sendText(text: String) {
        socket.send(
            buildJsonObject {
                put("event_id", UUID.randomUUID().toString())
                put("type", "speech_text_buffer.commit")
                put("text", text)
            }.toString(),
        )
    }

    fun cancelResponse() {
        socket.send(buildJsonObject { put("type", "response.cancel") }.toString())
    }

    fun close() {
        runCatching { socket.send(buildJsonObject { put("type", "session.close") }.toString()) }
        socket.close(1000, "done")
    }
}
