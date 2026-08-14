package com.semi.watchai

/**
 * 按"上下文选择"模式裁剪发送给 AI 的消息列表（纯函数，便于单元测试）。
 *
 * 语义（与设计文档一致）：
 * - CONTEXT_MODE_ALL  ("all") ：返回全部消息（默认行为）。
 * - CONTEXT_MODE_LAST ("last")：当前消息 + 其前最近一轮完整问答（上一轮 user + 之后 assistant 回复）。
 * - CONTEXT_MODE_NONE ("none")：仅当前消息（最后一条）。
 */
object MessageContextFilter {

    /**
     * 按 [mode] 裁剪消息列表。调用方保证 [messages] 至少含当前消息；
     * 为空时安全回退为空列表。
     */
    fun filter(messages: List<Message>, mode: String): List<Message> {
        if (messages.isEmpty()) return emptyList()
        return when (mode) {
            ProviderRepository.CONTEXT_MODE_NONE -> listOf(messages.last())
            ProviderRepository.CONTEXT_MODE_LAST -> lastRound(messages)
            else -> messages
        }
    }

    /**
     * 当前消息（最后一条）之前最近一轮完整问答 + 当前消息。
     * 规则：从倒数第二起向前扫描最近的 user 消息（上一轮提问），从它开始截取；
     * 找不到（当前是首条消息）时仅返回当前消息。
     */
    private fun lastRound(messages: List<Message>): List<Message> {
        val currentIndex = messages.lastIndex
        for (i in currentIndex - 1 downTo 0) {
            if (messages[i].isUser) {
                return messages.subList(i, messages.size)
            }
        }
        return listOf(messages.last())
    }
}
