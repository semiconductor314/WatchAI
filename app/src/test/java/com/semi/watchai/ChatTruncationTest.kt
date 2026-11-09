package com.semi.watchai

import com.semi.watchai.data.model.Message
import com.semi.watchai.util.ChatTruncation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatTruncationTest {

    private fun msg(id: String, isUser: Boolean, timestamp: Long = 0L) =
        Message(id = id, content = id, isUser = isUser, timestamp = timestamp)

    @Test
    fun `中间位置的用户消息_只返回其后的消息`() {
        val list = listOf(msg("u1", true), msg("a1", false), msg("u2", true), msg("a2", false))
        assertEquals(listOf("a2"), ChatTruncation.afterIndex(list, 2).map { it.id })
    }

    @Test
    fun `最后一条_返回空列表`() {
        val list = listOf(msg("u1", true), msg("a1", false))
        assertTrue(ChatTruncation.afterIndex(list, 1).isEmpty())
    }

    @Test
    fun `index 越界_返回空列表`() {
        val list = listOf(msg("u1", true))
        assertTrue(ChatTruncation.afterIndex(list, -1).isEmpty())
        assertTrue(ChatTruncation.afterIndex(list, 1).isEmpty())
        assertTrue(ChatTruncation.afterIndex(emptyList(), 0).isEmpty())
    }

    @Test
    fun `同 timestamp 的后续消息_全部返回（按 id 删除而非时间）`() {
        val list = listOf(
            msg("u1", true, timestamp = 1000L),
            msg("a1", false, timestamp = 1000L),
            msg("u2", true, timestamp = 1000L),
            msg("a2", false, timestamp = 1000L),
        )
        assertEquals(listOf("u2", "a2"), ChatTruncation.afterIndex(list, 1).map { it.id })
    }

    @Test
    fun `返回的是原列表元素的副本列表_不改动入参`() {
        val list = mutableListOf(msg("u1", true), msg("a1", false))
        ChatTruncation.afterIndex(list, 0)
        assertEquals(2, list.size)
    }
}
