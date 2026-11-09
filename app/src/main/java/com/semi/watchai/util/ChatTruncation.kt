package com.semi.watchai.util

import com.semi.watchai.data.model.Message

/**
 * 「重新加载」的截断逻辑（纯函数，便于单元测试）。
 *
 * 语义：重发第 index 条消息时，该消息**本身保留**，其后所有消息（旧 AI 回复与后续问答）
 * 都要删除 —— 删除按 [Message.id] 进行，不按 timestamp（用户消息与 AI 回复可能同毫秒）。
 */
object ChatTruncation {
    /** 返回 index 之后（不含 index 本身）需要删除的消息；index 越界时返回空列表。 */
    fun afterIndex(messages: List<Message>, index: Int): List<Message> {
        if (index < 0 || index >= messages.size) return emptyList()
        return messages.subList(index + 1, messages.size).toList()
    }
}
