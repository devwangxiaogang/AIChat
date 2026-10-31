package com.yuguang.lumo

import com.yuguang.lumo.domain.model.ChatMessage
import com.yuguang.lumo.domain.model.MessageStatus
import com.yuguang.lumo.domain.model.Role
import com.yuguang.lumo.domain.model.overlayStreaming
import org.junit.Assert.assertEquals
import org.junit.Test

/** 流式正文只在内存里覆盖「生成中」的那条消息，落库后以数据库为准。 */
class StreamingOverlayTest {
    private fun message(id: Long, status: MessageStatus, content: String) = ChatMessage(
        id = id,
        conversationId = 1,
        role = Role.ASSISTANT,
        content = content,
        reasoning = "",
        status = status,
        error = "",
        createdAt = 0,
    )

    @Test
    fun overlaysOnlyTheStreamingMessage() {
        val messages = listOf(
            message(1, MessageStatus.DONE, "done"),
            message(2, MessageStatus.STREAMING, "partial"),
        )

        val merged = messages.overlayStreaming(messageId = 2, content = "partial more", reasoning = "thinking")

        assertEquals("done", merged[0].content)
        assertEquals("partial more", merged[1].content)
        assertEquals("thinking", merged[1].reasoning)
    }

    @Test
    fun ignoresMessagesThatAlreadyFinished() {
        val messages = listOf(message(3, MessageStatus.DONE, "final answer"))

        val merged = messages.overlayStreaming(messageId = 3, content = "stale text", reasoning = "")

        assertEquals("final answer", merged[0].content)
    }

    @Test
    fun ignoresOtherMessages() {
        val messages = listOf(message(4, MessageStatus.STREAMING, "keep me"))

        val merged = messages.overlayStreaming(messageId = 99, content = "not mine", reasoning = "")

        assertEquals("keep me", merged[0].content)
    }
}
