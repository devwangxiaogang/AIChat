package com.example.myapplication.data.remote

import android.content.Context
import com.example.myapplication.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
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
import okio.ByteString.Companion.toByteString
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.UUID
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton

sealed interface VoiceChatEvent {
    data object Connected : VoiceChatEvent
    data object SessionStarted : VoiceChatEvent

    /** 用户说话的实时文本；[final] 为 true 表示这一句已定稿。 */
    data class UserText(val text: String, val final: Boolean) : VoiceChatEvent

    /** 模型回复的一句话（显示用）。 */
    data class AssistantText(val text: String) : VoiceChatEvent

    /** 模型回复的 PCM 音频（24kHz / 单声道 / 16bit 小端）。 */
    data class Audio(val pcm: ByteArray) : VoiceChatEvent

    /** 用户开始说话，客户端应停止本地播报。 */
    data object UserSpeaking : VoiceChatEvent
    data object Ended : VoiceChatEvent
    data class Failed(val message: String) : VoiceChatEvent
}

/**
 * 豆包端到端实时语音（Seeduplex）客户端。
 *
 * 协议：4 字节头（版本/头长、消息类型/标志、序列化/压缩、保留位），
 * 之后依次是事件号、会话 ID、负载；文本事件是 JSON，音频是裸 PCM。
 */
@Singleton
class DoubaoVoiceChatClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val http: OkHttpClient,
    private val json: Json,
) {
    suspend fun runSession(
        apiKey: String,
        appId: String,
        accessToken: String,
        speaker: String,
        botName: String,
        audio: ReceiveChannel<ByteArray>,
        onEvent: (VoiceChatEvent) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val sessionId = UUID.randomUUID().toString()
        val connectId = UUID.randomUUID().toString()
        val connected = CompletableDeferred<Unit>()
        val sessionStarted = CompletableDeferred<Unit>()
        val finished = CompletableDeferred<Unit>()
        val opened = CompletableDeferred<Unit>()

        val requestBuilder = Request.Builder()
            .url(ENDPOINT)
            .header("X-Api-Resource-Id", RESOURCE_ID)
            .header("X-Api-Connect-Id", connectId)
            .header("X-Api-Request-Id", UUID.randomUUID().toString())
        if (appId.isNotBlank() && accessToken.isNotBlank()) {
            requestBuilder
                .header("X-Api-App-Key", appId.trim())
                .header("X-Api-Access-Key", accessToken.trim())
        } else {
            requestBuilder.header("X-Api-Key", apiKey.trim())
        }

        val socket = http.newWebSocket(
            requestBuilder.build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    opened.complete(Unit)
                }

                override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
                    val frame = runCatching { parse(bytes.toByteArray()) }.getOrNull() ?: return
                    if (frame.type == ERROR) {
                        val message = friendlyError(errorText(frame))
                        onEvent(VoiceChatEvent.Failed(message))
                        finished.complete(Unit)
                        return
                    }
                    when (frame.event) {
                        EVENT_CONNECTION_STARTED -> {
                            connected.complete(Unit)
                            onEvent(VoiceChatEvent.Connected)
                        }
                        EVENT_CONNECTION_FAILED -> {
                            val message = friendlyError(errorText(frame))
                            if (!opened.isCompleted) opened.completeExceptionally(IllegalStateException(message))
                            connected.completeExceptionally(IllegalStateException(message))
                            onEvent(VoiceChatEvent.Failed(message))
                            finished.complete(Unit)
                        }
                        EVENT_SESSION_STARTED -> {
                            sessionStarted.complete(Unit)
                            onEvent(VoiceChatEvent.SessionStarted)
                        }
                        EVENT_SESSION_FINISHED -> {
                            onEvent(VoiceChatEvent.Ended)
                            finished.complete(Unit)
                        }
                        EVENT_SESSION_FAILED -> {
                            val message = friendlyError(errorText(frame))
                            onEvent(VoiceChatEvent.Failed(message))
                            finished.complete(Unit)
                        }
                        EVENT_ASR_INFO -> onEvent(VoiceChatEvent.UserSpeaking)
                        EVENT_ASR_RESPONSE -> asrText(frame)?.let { onEvent(it) }
                        EVENT_TTS_SENTENCE_START -> ttsText(frame)?.takeIf { it.isNotBlank() }
                            ?.let { onEvent(VoiceChatEvent.AssistantText(it)) }
                        EVENT_TTS_RESPONSE -> if (frame.payload.isNotEmpty()) {
                            onEvent(VoiceChatEvent.Audio(frame.payload))
                        }
                        else -> Unit
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    val logId = response?.header("X-Tt-Logid").orEmpty()
                    val detail = t.message.orEmpty().ifBlank { context.getString(R.string.voice_connect_failed) }
                    val suffix = if (logId.isBlank()) detail else "$detail（$logId）"
                    val message = context.getString(R.string.voice_failed_detail, suffix)
                    if (!opened.isCompleted) opened.completeExceptionally(IllegalStateException(suffix, t))
                    if (!connected.isCompleted) connected.completeExceptionally(IllegalStateException(suffix, t))
                    onEvent(VoiceChatEvent.Failed(message))
                    finished.complete(Unit)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    finished.complete(Unit)
                }
            },
        )

        try {
            withTimeout(OPEN_TIMEOUT_MS) { opened.await() }
            require(
                socket.send(
                    clientFrame(
                        FULL_CLIENT,
                        WITH_EVENT,
                        EVENT_START_CONNECTION,
                        null,
                        EMPTY,
                        JSON_SERIAL,
                    ).toByteString(),
                ),
            ) { "send StartConnection failed" }
            withTimeout(HANDSHAKE_TIMEOUT_MS) { connected.await() }
            require(
                socket.send(
                    clientFrame(
                        FULL_CLIENT,
                        WITH_EVENT,
                        EVENT_START_SESSION,
                        sessionId,
                        startSession(botName, speaker).encodeToByteArray(),
                        JSON_SERIAL,
                    ).toByteString(),
                ),
            ) { "send StartSession failed" }
            withTimeout(SESSION_TIMEOUT_MS) { sessionStarted.await() }

            coroutineScope {
                val sender = launch {
                    for (chunk in audio) {
                        if (chunk.isEmpty()) continue
                        val sent = socket.send(
                            clientFrame(
                                AUDIO_ONLY,
                                WITH_EVENT,
                                EVENT_TASK_REQUEST,
                                sessionId,
                                chunk,
                                RAW_SERIAL,
                            ).toByteString(),
                        )
                        if (!sent) break
                        // 16k/16bit 单声道 20ms = 640 字节，按实时节奏推送。
                        delay(20)
                    }
                }
                try {
                    finished.await()
                } finally {
                    sender.cancel()
                }
            }
        } finally {
            runCatching {
                socket.send(
                    clientFrame(
                        FULL_CLIENT,
                        WITH_EVENT,
                        EVENT_FINISH_SESSION,
                        sessionId,
                        EMPTY,
                        JSON_SERIAL,
                    ).toByteString(),
                )
            }
            runCatching {
                socket.send(
                    clientFrame(
                        FULL_CLIENT,
                        WITH_EVENT,
                        EVENT_FINISH_CONNECTION,
                        null,
                        EMPTY,
                        JSON_SERIAL,
                    ).toByteString(),
                )
            }
            socket.close(1000, "done")
        }
    }

    private fun startSession(botName: String, speaker: String): String = buildJsonObject {
        putJsonObject("dialog") {
            put("bot_name", botName.ifBlank { "豆包" })
            put("dialog_id", "")
            putJsonObject("extra") {
                // 麦克风静音时靠 keep_alive 避免音频流超时。
                put("input_mod", "keep_alive")
            }
        }
        putJsonObject("asr") {
            putJsonObject("audio_info") {
                put("format", "pcm")
                put("sample_rate", 16_000)
                put("channel", 1)
            }
            putJsonObject("extra") {}
        }
        putJsonObject("tts") {
            put("speaker", speaker)
            putJsonObject("audio_config") {
                put("channel", 1)
                put("format", "pcm_s16le")
                put("sample_rate", 24_000)
            }
            putJsonObject("extra") {}
        }
    }.toString()

    private fun clientFrame(
        messageType: Int,
        flags: Int,
        event: Int,
        sessionId: String?,
        payload: ByteArray,
        serialization: Int,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x11, ((messageType shl 4) or flags).toByte(), (serialization shl 4).toByte(), 0))
        out.write(intBytes(event))
        if (sessionId != null) {
            val id = sessionId.toByteArray(Charsets.UTF_8)
            out.write(intBytes(id.size))
            out.write(id)
        }
        out.write(intBytes(payload.size))
        out.write(payload)
        return out.toByteArray()
    }

    private fun parse(frame: ByteArray): ServerFrame {
        require(frame.size >= 4) { "frame too short" }
        val headerSize = ((frame[0].toInt() and 0x0f) * 4).coerceAtMost(frame.size)
        val type = (frame[1].toInt() ushr 4) and 0x0f
        val flags = frame[1].toInt() and 0x0f
        val compression = frame[2].toInt() and 0x0f
        var offset = headerSize
        var event = 0
        var errorCode = 0

        if (type == ERROR) {
            if (offset + 4 <= frame.size) {
                errorCode = intAt(frame, offset)
                offset += 4
            }
        } else if (flags and WITH_EVENT != 0 && offset + 4 <= frame.size) {
            event = intAt(frame, offset)
            offset += 4
            // 会话级事件后面跟 session id；连接级事件没有。
            if (event !in CONNECTION_EVENTS && offset + 4 <= frame.size) {
                val length = intAt(frame, offset)
                offset += 4
                offset = (offset + length).coerceAtMost(frame.size)
            }
            // 有序号的数据包：flags 为 0b0001 或 0b0011 时带 4 字节序号。
            if ((flags == 0x1 || flags == 0x3) && offset + 4 <= frame.size) {
                offset += 4
            }
        }

        val payload = if (offset + 4 <= frame.size) {
            val length = intAt(frame, offset)
            offset += 4
            val end = (offset + length).coerceAtMost(frame.size)
            frame.copyOfRange(offset, end)
        } else {
            ByteArray(0)
        }
        val body = if (compression == 1 && payload.isNotEmpty()) gunzip(payload) else payload
        return ServerFrame(type, event, errorCode, body)
    }

    private fun friendlyError(raw: String): String {
        val text = raw.ifBlank { return context.getString(R.string.voice_failed) }
        val lower = text.lowercase()
        return when {
            lower.contains("x-api-app-key") || lower.contains("appid mismatch") ||
                lower.contains("invalid x-api") || lower.contains("resourceid") ->
                context.getString(R.string.voice_not_enabled)
            else -> text
        }
    }

    private fun errorText(frame: ServerFrame): String = runCatching {
        val obj = json.parseToJsonElement(frame.payload.toString(Charsets.UTF_8)).jsonObject
        obj["error"]?.jsonPrimitive?.contentOrNull.orEmpty()
    }.getOrDefault("")

    private fun ttsText(frame: ServerFrame): String? = runCatching {
        val obj = json.parseToJsonElement(frame.payload.toString(Charsets.UTF_8)).jsonObject
        obj["text"]?.jsonPrimitive?.contentOrNull
    }.getOrNull()

    private fun asrText(frame: ServerFrame): VoiceChatEvent.UserText? = runCatching {
        val obj = json.parseToJsonElement(frame.payload.toString(Charsets.UTF_8)).jsonObject
        val results = obj["results"] as? JsonArray ?: return@runCatching null
        val item = results.firstOrNull() as? JsonObject ?: return@runCatching null
        val text = item["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val interim = item["is_interim"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false
        if (text.isBlank()) null else VoiceChatEvent.UserText(text, !interim)
    }.getOrNull()

    private fun intBytes(value: Int): ByteArray = ByteBuffer.allocate(4).putInt(value).array()

    private fun intAt(frame: ByteArray, offset: Int): Int = ByteBuffer.wrap(frame, offset, 4).int

    private fun gunzip(bytes: ByteArray): ByteArray =
        GZIPInputStream(bytes.inputStream()).use { it.readBytes() }

    private data class ServerFrame(
        val type: Int,
        val event: Int,
        val errorCode: Int,
        val payload: ByteArray,
    )

    private companion object {
        const val ENDPOINT = "wss://openspeech.bytedance.com/api/v3/realtime/dialogue"
        const val RESOURCE_ID = "volc.speech.dialog"
        const val FULL_CLIENT = 0x1
        const val AUDIO_ONLY = 0x2
        const val ERROR = 0xF
        const val JSON_SERIAL = 0x1
        const val RAW_SERIAL = 0x0
        const val WITH_EVENT = 0x4
        const val OPEN_TIMEOUT_MS = 8_000L
        const val HANDSHAKE_TIMEOUT_MS = 4_000L
        const val SESSION_TIMEOUT_MS = 8_000L
        const val EVENT_START_CONNECTION = 1
        const val EVENT_FINISH_CONNECTION = 2
        const val EVENT_START_SESSION = 100
        const val EVENT_FINISH_SESSION = 102
        const val EVENT_TASK_REQUEST = 200
        const val EVENT_CONNECTION_STARTED = 50
        const val EVENT_CONNECTION_FAILED = 51
        const val EVENT_SESSION_STARTED = 150
        const val EVENT_SESSION_FINISHED = 152
        const val EVENT_SESSION_FAILED = 153
        const val EVENT_TTS_SENTENCE_START = 350
        const val EVENT_TTS_RESPONSE = 352
        const val EVENT_ASR_INFO = 450
        const val EVENT_ASR_RESPONSE = 451
        val CONNECTION_EVENTS = setOf(
            EVENT_START_CONNECTION,
            EVENT_FINISH_CONNECTION,
            EVENT_CONNECTION_STARTED,
            EVENT_CONNECTION_FAILED,
        )
        val EMPTY = ByteArray(0)
    }
}
