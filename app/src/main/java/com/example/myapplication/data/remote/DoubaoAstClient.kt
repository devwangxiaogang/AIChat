package com.example.myapplication.data.remote

import android.content.Context
import com.example.myapplication.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString.Companion.toByteString
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

sealed interface AstEvent {
    data object Started : AstEvent
    data class SourcePartial(val text: String) : AstEvent
    data class SourceFinal(val text: String) : AstEvent
    data class TargetPartial(val text: String) : AstEvent
    data class TargetFinal(val text: String) : AstEvent
    data class Audio(val pcm: ByteArray) : AstEvent
    data object Finished : AstEvent
    data object Quiet : AstEvent
}

/**
 * 豆包同声传译 2.0。资源 ID 固定为 volc.service_type.10053，可切换的是音色和语言。
 * 麦克风按 16 kHz 单声道送入，译文语音按 16 kHz PCM 返回，方便直接播放。
 */
@Singleton
class DoubaoAstClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val http: OkHttpClient,
) {
    suspend fun interpret(
        apiKey: String,
        sourceLanguage: String,
        targetLanguage: String,
        speakerId: String?,
        audio: ReceiveChannel<ByteArray>,
        appId: String = "",
        accessToken: String = "",
        onEvent: (AstEvent) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val messages = Channel<ByteArray>(Channel.UNLIMITED)
        val opened = CompletableDeferred<Unit>()
        val sessionId = UUID.randomUUID().toString()
        val requestBuilder = Request.Builder()
            .url(ENDPOINT)
            .header("X-Api-Resource-Id", RESOURCE_ID)
            .header("X-Api-Connect-Id", sessionId)
        if (appId.isNotBlank() && accessToken.isNotBlank()) {
            requestBuilder
                .header("X-Api-App-Key", appId.trim())
                .header("X-Api-Access-Key", accessToken.trim())
        } else {
            requestBuilder.header("X-Api-Key", apiKey.trim())
        }
        val request = requestBuilder.build()
        val socket = http.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    opened.complete(Unit)
                }

                override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
                    messages.trySend(bytes.toByteArray())
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    val logId = response?.header("X-Tt-Logid").orEmpty()
                    val detail = t.message.orEmpty().ifBlank { context.getString(R.string.asr_connect_failed) }
                    val suffix = if (logId.isBlank()) detail else "$detail（$logId）"
                    val error = IllegalStateException(context.getString(R.string.live_connect_detail, suffix), t)
                    if (!opened.isCompleted) opened.completeExceptionally(error)
                    messages.close(error)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    messages.close()
                }
            },
        )
        try {
            withTimeout(OPEN_TIMEOUT_MS) { opened.await() }
            if (!socket.send(startSession(sessionId, sourceLanguage, targetLanguage, speakerId).toByteString())) {
                error(context.getString(R.string.live_connect_failed))
            }
            while (true) {
                val event = withTimeout(OPEN_TIMEOUT_MS) { decode(messages.receive()) }
                if (event is AstServer.Failed) error(context.getString(R.string.live_failed_detail, event.message))
                if (event is AstServer.Started) {
                    onEvent(AstEvent.Started)
                    break
                }
            }
            coroutineScope {
                val sending = launch {
                    for (chunk in audio) {
                        if (!socket.send(taskAudio(chunk).toByteString())) break
                    }
                    socket.send(finishSession().toByteString())
                }
                while (true) {
                    val event = withTimeout(RESULT_TIMEOUT_MS) { decode(messages.receive()) }
                    when (event) {
                        is AstServer.Failed -> error(context.getString(R.string.live_failed_detail, event.message))
                        AstServer.Finished -> {
                            onEvent(AstEvent.Finished)
                            sending.cancel()
                            break
                        }
                        is AstServer.Text -> onEvent(event.toPublic())
                        is AstServer.Audio -> if (event.pcm.isNotEmpty()) onEvent(AstEvent.Audio(event.pcm))
                        AstServer.Quiet -> onEvent(AstEvent.Quiet)
                        AstServer.Started, AstServer.Ignore -> Unit
                    }
                }
            }
        } finally {
            socket.close(1000, "done")
            messages.close()
        }
    }

    private fun startSession(
        sessionId: String,
        sourceLanguage: String,
        targetLanguage: String,
        speakerId: String?,
    ): ByteArray = ProtoWriter().apply {
        message(1) {
            string(4, RESOURCE_ID)
            string(5, sessionId)
            string(6, sessionId)
        }
        int32(2, EVENT_START)
        message(3) {
            string(1, "aidemo")
            string(3, "Android")
        }
        message(4) {
            string(4, "wav")
            string(5, "raw")
            int32(7, 16_000)
            int32(8, 16)
            int32(9, 1)
        }
        message(5) {
            string(4, "pcm")
            int32(7, 16_000)
            int32(8, 16)
            int32(9, 1)
        }
        message(6) {
            string(1, "s2s")
            string(2, sourceLanguage)
            string(3, targetLanguage)
            if (speakerId != null) string(4, speakerId)
        }
    }.toByteArray()

    private fun taskAudio(pcm: ByteArray): ByteArray = ProtoWriter().apply {
        int32(2, EVENT_AUDIO)
        message(4) { bytes(14, pcm) }
    }.toByteArray()

    private fun finishSession(): ByteArray = ProtoWriter().apply {
        int32(2, EVENT_FINISH)
    }.toByteArray()

    private fun decode(frame: ByteArray): AstServer {
        val fields = ProtoReader(frame).fields()
        val event = fields.firstOrNull { it.number == 2 }?.varint?.toInt() ?: 0
        val text = fields.firstOrNull { it.number == 4 }?.text().orEmpty()
        val audio = fields.firstOrNull { it.number == 3 }?.bytes ?: ByteArray(0)
        val meta = fields.firstOrNull { it.number == 1 }?.bytes
        val failure = meta?.let { metaMessage(it) }.orEmpty()
        return when (event) {
            EVENT_STARTED -> AstServer.Started
            EVENT_FINISHED -> AstServer.Finished
            EVENT_FAILED -> AstServer.Failed(failure.ifBlank { context.getString(R.string.session_failed) })
            EVENT_SOURCE -> AstServer.Text(source = true, final = false, text = text)
            EVENT_SOURCE_END -> AstServer.Text(source = true, final = true, text = text)
            EVENT_TARGET -> AstServer.Text(source = false, final = false, text = text)
            EVENT_TARGET_END -> AstServer.Text(source = false, final = true, text = text)
            EVENT_TTS -> AstServer.Audio(audio)
            EVENT_QUIET -> AstServer.Quiet
            else -> if (failure.isNotBlank()) AstServer.Failed(failure) else AstServer.Ignore
        }
    }

    private fun metaMessage(bytes: ByteArray): String {
        val fields = ProtoReader(bytes).fields()
        val code = fields.filter { it.bytes == null }.map { it.varint.toInt() }.firstOrNull { it >= 1000 } ?: 0
        val message = fields.map { it.text() }.firstOrNull { it.isNotBlank() }.orEmpty()
        if (code == 0 || code == 20_000_000) return ""
        return message.ifBlank { code.toString() }.let { "$it（$code）" }
    }

    private sealed interface AstServer {
        data object Ignore : AstServer
        data object Started : AstServer
        data object Finished : AstServer
        data class Failed(val message: String) : AstServer
        data class Text(val source: Boolean, val final: Boolean, val text: String) : AstServer
        data class Audio(val pcm: ByteArray) : AstServer
        data object Quiet : AstServer
    }

    private fun AstServer.Text.toPublic(): AstEvent = when {
        source && final -> AstEvent.SourceFinal(text)
        source -> AstEvent.SourcePartial(text)
        final -> AstEvent.TargetFinal(text)
        else -> AstEvent.TargetPartial(text)
    }

    private companion object {
        const val ENDPOINT = "wss://openspeech.bytedance.com/api/v4/ast/v2/translate"
        const val RESOURCE_ID = "volc.service_type.10053"
        const val EVENT_START = 100
        const val EVENT_FINISH = 102
        const val EVENT_AUDIO = 200
        const val EVENT_STARTED = 150
        const val EVENT_FINISHED = 152
        const val EVENT_FAILED = 153
        const val EVENT_QUIET = 250
        const val EVENT_TTS = 352
        const val EVENT_SOURCE = 651
        const val EVENT_SOURCE_END = 652
        const val EVENT_TARGET = 654
        const val EVENT_TARGET_END = 655
        const val OPEN_TIMEOUT_MS = 15_000L
        const val RESULT_TIMEOUT_MS = 60_000L
    }
}
