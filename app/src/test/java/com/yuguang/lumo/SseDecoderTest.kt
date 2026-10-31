package com.yuguang.lumo

import com.yuguang.lumo.data.remote.ChunkPiece
import com.yuguang.lumo.data.remote.EndpointUrls
import com.yuguang.lumo.data.remote.SseDecoder
import com.yuguang.lumo.domain.model.ProviderId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SseDecoderTest {
    @Test
    fun decodesContentDelta() {
        val pieces = SseDecoder.decode("""data: {"choices":[{"delta":{"content":"你好"}}]}""")
        assertEquals(listOf(ChunkPiece.Delta("你好")), pieces)
    }

    @Test
    fun decodesReasoningAndUsage() {
        val pieces = SseDecoder.decode(
            """data: {"choices":[{"delta":{"reasoning_content":"先想"}}],"usage":{"prompt_tokens":3,"completion_tokens":5}}""",
        )
        assertEquals(
            listOf(
                ChunkPiece.Reasoning("先想"),
                ChunkPiece.Usage(3, 5),
            ),
            pieces,
        )
    }

    @Test
    fun decodesDoneAndIgnoresOtherLines() {
        assertEquals(listOf(ChunkPiece.Done), SseDecoder.decode("data: [DONE]"))
        assertTrue(SseDecoder.decode("").isEmpty())
        assertTrue(SseDecoder.decode(": keep-alive").isEmpty())
    }

    @Test
    fun reportsInvalidJson() {
        val pieces = SseDecoder.decode("data: {not-json}")
        assertTrue(pieces.single() is ChunkPiece.Invalid)
    }

    @Test
    fun joinsChatCompletionsPath() {
        assertEquals(
            "https://api.deepseek.com/chat/completions",
            EndpointUrls.chatCompletions("https://api.deepseek.com/", ProviderId.DEEPSEEK),
        )
        assertEquals(
            "https://ark.cn-beijing.volces.com/api/v3/chat/completions",
            EndpointUrls.chatCompletions("", ProviderId.DOUBAO),
        )
        assertEquals(
            "https://api.deepseek.com/chat/completions",
            EndpointUrls.chatCompletions("https://api.deepseek.com/chat/completions", ProviderId.DEEPSEEK),
        )
    }
}
