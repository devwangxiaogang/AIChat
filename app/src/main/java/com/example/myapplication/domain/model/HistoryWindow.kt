package com.example.myapplication.domain.model

/**
 * 从按时间正序排列的历史里挑出要发给模型的那一段。
 *
 * 规则：从最新往回取，同时受条数和字符数限制；**最新的一条无论多长都保留**，
 * 否则用户刚输入的长文会被裁掉，模型会答非所问。
 *
 * @return 需要保留的起始下标（含），`textLengths.size` 表示一条都不留。
 */
fun historyStartIndex(textLengths: List<Int>, maxMessages: Int, maxChars: Int): Int {
    if (textLengths.isEmpty()) return 0
    var used = 0
    var kept = 0
    for (index in textLengths.indices.reversed()) {
        val length = textLengths[index].coerceAtLeast(0)
        if (kept > 0 && (kept >= maxMessages || used + length > maxChars)) return index + 1
        used += length
        kept++
    }
    return 0
}
