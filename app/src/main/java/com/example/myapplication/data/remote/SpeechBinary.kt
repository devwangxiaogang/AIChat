package com.example.myapplication.data.remote

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.zip.GZIPInputStream

/**
 * 豆包语音合成 WebSocket 的二进制帧。
 * 带事件号时，连接级事件没有 session id，会话级事件在负载前带 session id。
 */
internal object SpeechBinary {
    const val FULL_CLIENT = 0x1
    const val AUDIO_SERVER = 0xB
    const val ERROR = 0xF
    private const val WITH_EVENT = 0x4
    private val connectionEvents = setOf(1, 2, 50, 51, 52)

    fun request(event: Int, sessionId: String, payload: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x11, (FULL_CLIENT shl 4 or WITH_EVENT).toByte(), 0x10, 0x00))
        out.write(intBytes(event))
        if (event !in connectionEvents) {
            val id = sessionId.toByteArray(Charsets.UTF_8)
            out.write(intBytes(id.size))
            out.write(id)
        }
        out.write(intBytes(payload.size))
        out.write(payload)
        return out.toByteArray()
    }

    fun parse(frame: ByteArray): SpeechPacket {
        if (frame.size < 4) error("empty speech frame")
        val headerSize = (frame[0].toInt() and 0x0f) * 4
        val type = (frame[1].toInt() ushr 4) and 0x0f
        val flags = frame[1].toInt() and 0x0f
        val compression = frame[2].toInt() and 0x0f
        var offset = headerSize.coerceAtLeast(4).coerceAtMost(frame.size)
        var event = 0
        var sessionId = ""
        var errorCode = 0
        if (flags and WITH_EVENT != 0 && offset + 4 <= frame.size) {
            event = intAt(frame, offset)
            offset += 4
            if (event !in connectionEvents && offset + 4 <= frame.size) {
                val length = intAt(frame, offset)
                offset += 4
                val end = (offset + length).coerceAtMost(frame.size)
                sessionId = frame.copyOfRange(offset, end).toString(Charsets.UTF_8)
                offset = end
            }
        } else if (flags and 0x1 != 0 && offset + 4 <= frame.size) {
            offset += 4
        }
        if (type == ERROR && offset + 4 <= frame.size) {
            errorCode = intAt(frame, offset)
            offset += 4
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
        return SpeechPacket(type, event, sessionId, errorCode, body)
    }

    private fun gunzip(payload: ByteArray): ByteArray =
        GZIPInputStream(payload.inputStream()).use { it.readBytes() }

    private fun intBytes(value: Int): ByteArray =
        ByteBuffer.allocate(4).putInt(value).array()

    private fun intAt(frame: ByteArray, offset: Int): Int =
        ByteBuffer.wrap(frame, offset, 4).int
}

internal data class SpeechPacket(
    val type: Int,
    val event: Int,
    val sessionId: String,
    val errorCode: Int,
    val payload: ByteArray,
)
