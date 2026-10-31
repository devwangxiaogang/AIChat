package com.yuguang.lumo.data.remote

import android.content.Context
import com.yuguang.lumo.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
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
 * 豆包流式语音识别。资源 ID 按账号开通情况自动选择（2.0/1.0、小时版/并发版），
 * 成功后记住可用的那个，避免每次按住说话都重新试探。
 * 音频走 WebSocket 二进制帧：4 字节头、大端长度、Gzip 负载。
 */
@Singleton
class DoubaoAsrClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val http: OkHttpClient,
    private val json: Json,
) {
    /** 上次握手成功的资源 ID，优先复用。 */
    @Volatile
    private var workingResourceId: String? = null

    /**
     * @param language BCP-47 语种，如 zh-CN / en-US；空字符串表示交给服务端自动检测。
     *
     * 不同账号开通的语音识别包不一样（2.0/1.0、小时版/并发版），资源 ID 不匹配时
     * 服务端会回 403。这里按顺序尝试，成功后记住可用的那个。
     */
    suspend fun transcribe(
        apiKey: String,
        wav: ByteArray,
        language: String = "zh-CN",
        appId: String = "",
        accessToken: String = "",
    ): String = withContext(Dispatchers.IO) {
        val asrLanguage = language.trim()
        val legacyAppId = appId.trim()
        val legacyToken = accessToken.trim()
        var lastFailure: SpeechHttpException? = null
        for (resourceId in candidateResources()) {
            try {
                val text = transcribeOnce(
                    apiKey = apiKey.trim(),
                    appId = legacyAppId,
                    accessToken = legacyToken,
                    wav = wav,
                    language = asrLanguage,
                    resourceId = resourceId,
                )
                workingResourceId = resourceId
                return@withContext text
            } catch (failure: SpeechHttpException) {
                lastFailure = failure
                android.util.Log.w(
                    "DoubaoAsr",
                    "resource=$resourceId http=${failure.code} " +
                        "detail=${failure.detail.ifBlank { "-" }} logid=${failure.logId.ifBlank { "-" }}",
                )
                // 401 是 Key 本身无效，换资源没有意义。
                if (failure.code == 401) break
            }
        }
        val failure = lastFailure
        when {
            failure == null -> error(context.getString(R.string.asr_connect_failed))
            failure.code == 401 -> error(context.getString(R.string.asr_key_invalid))
            failure.code == 403 -> error(
                context.getString(R.string.asr_forbidden, failure.logId.ifBlank { "-" }),
            )
            else -> error(
                context.getString(
                    R.string.asr_failed_detail,
                    failure.detail.ifBlank { context.getString(R.string.asr_connect_failed) },
                ),
            )
        }
    }

    private suspend fun transcribeOnce(
        apiKey: String,
        appId: String,
        accessToken: String,
        wav: ByteArray,
        language: String,
        resourceId: String,
    ): String {
        val messages = Channel<ByteArray>(Channel.UNLIMITED)
        val failure = CompletableDeferred<Throwable>()
        val requestBuilder = Request.Builder()
            .url(ENDPOINT)
            .header("X-Api-Resource-Id", resourceId)
            .header("X-Api-Request-Id", UUID.randomUUID().toString())
            .header("X-Api-Sequence", "-1")
        if (appId.isNotEmpty() && accessToken.isNotEmpty()) {
            // 旧版控制台：AppID + Access Token，账号下开通的服务通常只有这种鉴权可用。
            requestBuilder
                .header("X-Api-App-Key", appId)
                .header("X-Api-Access-Key", accessToken)
        } else {
            requestBuilder.header("X-Api-Key", apiKey)
        }
        val request = requestBuilder.build()
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
                    val httpCode = response?.code ?: 0
                    android.util.Log.w(
                        "DoubaoAsr",
                        "handshake resource=$resourceId http=$httpCode status=$status message=$message logid=$logId",
                    )
                    val detail = message.ifBlank { status.ifBlank { t.message.orEmpty() } }
                    val error = SpeechHttpException(httpCode, detail, logId, t)
                    failure.complete(error)
                    messages.close(error)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    messages.close()
                }
            },
        )
        try {
            if (!socket.send(clientFrame(FULL_CLIENT, 0, JSON_SERIAL, gzip(requestPayload(language))).toByteString())) {
                throw handshakeFailure(failure)
            }
            var offset = 0
            while (offset < wav.size) {
                val end = minOf(offset + CHUNK_BYTES, wav.size)
                val last = end == wav.size
                val flags = if (last) LAST_PACKET else 0
                val sent = socket.send(
                    clientFrame(AUDIO_ONLY, flags, RAW_SERIAL, gzip(wav.copyOfRange(offset, end))).toByteString(),
                )
                if (!sent) throw handshakeFailure(failure)
                offset = end
            }
            var spoken = ""
            while (true) {
                val packet = withTimeout(RESULT_TIMEOUT_MS) { messages.receive() }
                val parsed = parseServer(packet)
                if (parsed.failure != null) {
                    android.util.Log.w("DoubaoAsr", "server resource=$resourceId ${parsed.failure}")
                    error(context.getString(R.string.asr_failed_detail, parsed.failure))
                }
                if (parsed.text.isNotBlank()) spoken = parsed.text
                if (parsed.finished) break
            }
            return spoken.trim().ifEmpty { error(context.getString(R.string.asr_empty)) }
        } finally {
            socket.close(1000, "done")
            messages.close()
            if (!failure.isCompleted) failure.cancel()
        }
    }

    /** 握手失败时 OkHttp 只能通过 onFailure 拿到状态码，这里把它取回来。 */
    private suspend fun handshakeFailure(failure: CompletableDeferred<Throwable>): Throwable =
        withTimeoutOrNull(HANDSHAKE_WAIT_MS) { failure.await() }
            ?: IllegalStateException(context.getString(R.string.asr_connect_failed))

    /** 依次尝试 2.0/1.0、小时版/并发版；上次成功的排在最前面。 */
    private fun candidateResources(): List<String> {
        val all = listOf(
            RESOURCE_ID_SEEDASR_DURATION,
            RESOURCE_ID_SEEDASR_CONCURRENT,
            RESOURCE_ID_BIGASR_DURATION,
            RESOURCE_ID_BIGASR_CONCURRENT,
        )
        val preferred = workingResourceId ?: return all
        return listOf(preferred) + all.filter { it != preferred }
    }

    private class SpeechHttpException(
        val code: Int,
        val detail: String,
        val logId: String,
        cause: Throwable?,
    ) : Exception("http $code $detail", cause)

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
        const val RESOURCE_ID_SEEDASR_DURATION = "volc.seedasr.sauc.duration"
        const val RESOURCE_ID_SEEDASR_CONCURRENT = "volc.seedasr.sauc.concurrent"
        const val RESOURCE_ID_BIGASR_DURATION = "volc.bigasr.sauc.duration"
        const val RESOURCE_ID_BIGASR_CONCURRENT = "volc.bigasr.sauc.concurrent"
        const val HANDSHAKE_WAIT_MS = 1_500L
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
