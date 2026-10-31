package com.yuguang.lumo.domain.model

/**
 * 把内存里的流式文本叠加到数据库快照上。
 *
 * 生成中的正文只放在内存里，界面直接读它；数据库按秒级落盘做兜底。
 * 只有仍在生成（STREAMING）的那条消息会被覆盖，一旦落库为 DONE/FAILED，
 * 就以数据库内容为准，避免旧快照盖住最终结果。
 */
fun List<ChatMessage>.overlayStreaming(
    messageId: Long,
    content: String,
    reasoning: String,
): List<ChatMessage> = map { message ->
    if (message.id == messageId && message.status == MessageStatus.STREAMING) {
        message.copy(content = content, reasoning = reasoning)
    } else {
        message
    }
}
