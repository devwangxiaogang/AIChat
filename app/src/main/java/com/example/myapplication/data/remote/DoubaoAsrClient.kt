package com.example.myapplication.data.remote

import android.content.Context
import com.example.myapplication.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
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
import java.util.zip.GZIPOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 豆包流式语音识别 2.0。账号开通的是小时版，资源 ID 为 volc.seedasr.sauc.duration。
 * 极速版 volc.bigasr.auc_turbo 未开通，因此不用录音文件极速接口。
 * 音频走 WebSocket 二进制帧：4 字节头、大端长度、Gzip 负载。
 */
@Singleton
class DoubaoAsrClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val http: OkHttpClient,
    private val json: Json,
) {
    /**
     * @param language BCP-47 语种，如 zh-CN / en-US；空字符串表示交给服务端自动检测。
     */
    suspend fun transcribe(apiKey: String, wav: ByteArray, language: String = "zh-CN"): String = withContext(Dispatchers.IO) {
        val asrLanguage = language.trim()
        val messages = Channel<ByteArray>(Channel.UNLIMITED)
        val failure = CompletableDeferred<Throwable>()
        val request = Request.Builder()
            .url(ENDPOINT)
            .header("X-Api-Key", apiKey)
            .header("X-Api-Resource-Id", RESOURCE_ID)
            .header("X-Api-Request-Id", UUID.randomUUID().toString())
            .header("X-Api-Sequence", "-1")
            .build()
        val socket = http.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
                    messages.trySend(bytes.toByteArray())
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    val status = response?.header("X-Api-Status-Code").orEmpty()
                    val message = response?.header("X-Api-Message").orEmpty()
                    val logId = response?.header("X-Tt-Logid").orEmpty()
                    android.util.Log.w(
                        "DoubaoAsr",
                        "handshake http=${response?.code} status=$status message=$message logid=$logId",
                    )
                    val detail = message.ifBlank { status.ifBlank { t.message.orEmpty() } }
                    val error = IllegalStateException(
                        context.getString(R.string.asr_failed_detail, detail.ifBlank { context.getString(R.string.asr_connect_failed) }),
                        t,
                    )
                    failure.complete(error)
                    messages.close(error)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    messages.close()
                }
            },
        )
        try {
            if (!socket.send(clientFrame(FULL_CLIENT, 0, JSON_SERIAL, gzip(requestPayload(asrLanguage))).toByteString())) {
                error(context.getString(R.string.asr_connect_failed))
            }
            var offset = 0
            while (offset < wav.size) {
                val end = minOf(offset + CHUNK_BYTES, wav.size)
                val last = end == wav.size
                val flags = if (last) LAST_PACKET else 0
                val sent = socket.send(
                    clientFrame(AUDIO_ONLY, flags, RAW_SERIAL, gzip(wav.copyOfRange(offset, end))).toByteString(),
                )
                if (!sent) error(context.getString(R.string.asr_connect_failed))
                offset = end
            }
            var spoken = ""
            while (true) {
                val packet = withTimeout(RESULT_TIMEOUT_MS) { messages.receive() }
                val parsed = parseServer(packet)
                if (parsed.failure != null) {
                    android.util.Log.w("DoubaoAsr", "server ${parsed.failure}")
                    error(context.getString(R.string.asr_failed_detail, parsed.failure))
                }
                if (parsed.text.isNotBlank()) spoken = parsed.text
                if (parsed.finished) break
            }
            spoken.trim().ifEmpty { error(context.getString(R.string.asr_empty)) }
        } finally {
            socket.close(1000, "done")
            messages.close()
            if (!failure.isCompleted) failure.cancel()
        }
    }

    private fun requestPayload(language: String): ByteArray = buildJsonObject {
        putJsonObject("user") { put("uid", "aidemo") }
        putJsonObject("audio") {
            put("format", "wav")
            put("codec", "raw")
            put("rate", 16_000)
            put("bits", 16)
            put("channel", 1)
            if (language.isNotBlank()) put("language", language)
        }
        putJsonObject("request") {
            put("model_name", "bigmodel")
            put("enable_itn", true)
            put("enable_punc", true)
            put("show_utterances", false)
        }
    }.toString().encodeToByteArray()

    /** 协议版本 1、头长 4 字节。flags 的最低两位表示是否带序号、是否为最后一包。 */
    private fun clientFrame(messageType: Int, flags: Int, serialization: Int, payload: ByteArray): ByteArray {
        val header = byteArrayOf(
            0x11,
            ((messageType shl 4) or flags).toByte(),
            ((serialization shl 4) or GZIP).toByte(),
            0,
        )
        val size = ByteBuffer.allocate(4).putInt(payload.size).array()
        return header + size + payload
    }

    private fun parseServer(packet: ByteArray): ServerMessage {
        if (packet.size < 8) return ServerMessage(failure = context.getString(R.string.response_incomplete))
        val headerSize = (packet[0].toInt() and 0x0f) * 4
        val messageType = (packet[1].toInt() and 0xf0) ushr 4
        val flags = packet[1].toInt() and 0x0f
        val compression = packet[2].toInt() and 0x0f
        var offset = headerSize
        if (messageType == ERROR) {
            if (packet.size < offset + 8) return ServerMessage(failure = context.getString(R.string.response_incomplete))
            offset += 4
            val size = readInt(packet, offset)
            offset += 4
            return ServerMessage(failure = decodePayload(packet, offset, size, compression).ifBlank { context.getString(R.string.server_error) })
        }
        if (flags and 0x01 != 0) offset += 4
        if (packet.size < offset + 4) return ServerMessage(failure = context.getString(R.string.response_incomplete))
        val size = readInt(packet, offset)
        offset += 4
        val body = decodePayload(packet, offset, size, compression)
        val text = runCatching { textOf(body) }.getOrDefault("")
        return ServerMessage(text = text, finished = flags and 0x02 != 0)
    }

    private fun textOf(body: String): String {
        if (body.isBlank()) return ""
        val result = json.parseToJsonElement(body) as? JsonObject ?: return ""
        return when (val value = result["result"]) {
            is JsonObject -> value["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
            is JsonArray -> value.mapNotNull { item ->
                (item as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull
            }.joinToString("")
            else -> ""
        }
    }

    private fun decodePayload(packet: ByteArray, offset: Int, size: Int, compression: Int): String {
        if (size <= 0 || offset + size > packet.size) return ""
        val raw = packet.copyOfRange(offset, offset + size)
        val bytes = if (compression == GZIP) gunzip(raw) else raw
        return bytes.toString(Charsets.UTF_8).trim()
    }

    private fun readInt(packet: ByteArray, offset: Int): Int =
        ByteBuffer.wrap(packet, offset, 4).int

    private fun gzip(bytes: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { it.write(bytes) }
        return output.toByteArray()
    }

    private fun gunzip(bytes: ByteArray): ByteArray =
        GZIPInputStream(bytes.inputStream()).use { it.readBytes() }

    private data class ServerMessage(
        val text: String = "",
        val finished: Boolean = false,
        val failure: String? = null,
    )

    private companion object {
        // nostream：整段音频发完后再返回结果，适合按住说话后一次识别。
        const val ENDPOINT = "wss://openspeech.bytedance.com/api/v3/sauc/bigmodel_nostream"
        const val RESOURCE_ID = "volc.seedasr.sauc.duration"
        const val FULL_CLIENT = 0x1
        const val AUDIO_ONLY = 0x2
        const val ERROR = 0xF
        const val JSON_SERIAL = 0x1
        const val RAW_SERIAL = 0x0
        const val GZIP = 0x1
        const val LAST_PACKET = 0x2
        const val CHUNK_BYTES = 6_400
        const val RESULT_TIMEOUT_MS = 20_000L
    }
}
