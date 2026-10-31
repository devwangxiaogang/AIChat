package com.example.myapplication

import com.example.myapplication.data.remote.ChunkPiece
import com.example.myapplication.data.remote.ProtoReader
import com.example.myapplication.data.remote.ProtoWriter
import com.example.myapplication.data.remote.SseDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 手写的流式协议与 protobuf 编解码，是网络层最容易回归的部分。 */
class WireProtocolTest {
    @Test
    fun decodesToolCallDelta() {
        val pieces = SseDecoder.decode(
            """data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1","function":{"name":"generate_image","arguments":"{\"prompt\":\"cat\"}"}}]}}]}""",
        )
        val call = pieces.single() as ChunkPiece.ToolCallDelta
        assertEquals(0, call.index)
        assertEquals("call_1", call.id)
        assertEquals("generate_image", call.name)
        assertEquals("""{"prompt":"cat"}""", call.arguments)
    }

    @Test
    fun protoWriterSkipsDefaultValues() {
        val bytes = ProtoWriter().apply {
            int32(1, 0)
            string(2, "")
            message(3) { }
        }.toByteArray()
        assertTrue(bytes.isEmpty())
    }

    @Test
    fun protoRoundTripsNestedMessages() {
        val bytes = ProtoWriter().apply {
            int32(2, 100)
            message(4) {
                string(1, "aidemo")
            }
            bytes(14, byteArrayOf(1, 2, 3))
        }.toByteArray()

        val fields = ProtoReader(bytes).fields()
        assertEquals(100L, fields.first { it.number == 2 }.varint)
        val nested = ProtoReader(fields.first { it.number == 4 }.bytes!!).fields()
        assertEquals("aidemo", nested.first().text())
        assertTrue(fields.first { it.number == 14 }.bytes!!.contentEquals(byteArrayOf(1, 2, 3)))
    }
}
