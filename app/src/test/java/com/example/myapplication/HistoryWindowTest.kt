package com.example.myapplication

import com.example.myapplication.domain.model.historyStartIndex
import org.junit.Assert.assertEquals
import org.junit.Test

/** 上下文窗口裁剪：条数、字符预算，以及「最新一条必须保留」的例外。 */
class HistoryWindowTest {
    @Test
    fun emptyHistoryStartsAtZero() {
        assertEquals(0, historyStartIndex(emptyList(), maxMessages = 24, maxChars = 12_000))
    }

    @Test
    fun keepsEverythingWhenItFits() {
        assertEquals(0, historyStartIndex(listOf(10, 20, 30), maxMessages = 24, maxChars = 12_000))
    }

    @Test
    fun trimsByMessageCount() {
        // 10 条、上限 3 条：只保留最后 3 条，起点是 7。
        assertEquals(7, historyStartIndex(List(10) { 5 }, maxMessages = 3, maxChars = 12_000))
    }

    @Test
    fun trimsByCharacterBudget() {
        // 倒数第二条和最后一条合计 120 字符，超过 100 的预算，只留最后一条。
        assertEquals(2, historyStartIndex(listOf(10, 60, 60), maxMessages = 24, maxChars = 100))
    }

    @Test
    fun alwaysKeepsNewestMessageEvenWhenTooLong() {
        // 最新一条 500 字符已超预算，但仍然保留它，否则刚输入的内容会被丢掉。
        assertEquals(2, historyStartIndex(listOf(10, 20, 500), maxMessages = 24, maxChars = 100))
    }

    @Test
    fun singleMessageIsAlwaysKept() {
        assertEquals(0, historyStartIndex(listOf(9_999), maxMessages = 24, maxChars = 100))
    }
}
