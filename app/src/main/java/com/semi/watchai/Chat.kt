package com.semi.watchai

import java.util.UUID

// 聊天会话
data class Chat(
    val id: String = UUID.randomUUID().toString(),
    var title: String,
    var messages: MutableList<Message> = mutableListOf(),
    val createdAt: Long = System.currentTimeMillis(),
    // 每对话设置（null = 未单独设置，回退到全局设置）
    var providerId: String? = null,
    var modelOnline: Boolean? = null,
    var promptId: String? = null
)

// 单条消息
data class Message(
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val isUser: Boolean,  // true=用户, false=AI
    val timestamp: Long = System.currentTimeMillis()
)