package com.semi.watchai.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    var title: String = "新聊天",
    val createdAt: Long = System.currentTimeMillis(),
    // 每对话设置（null = 未单独设置，回退到全局设置）
    var providerId: String? = null,
    var modelOnline: Boolean? = null,
    var promptId: String? = null,
    // 每对话上下文选择（null = 未单独设置，回退到全局设置）
    var contextMode: String? = null,
    // 每对话流式输出开关（null = 未单独设置，回退到全局设置）
    var streamEnabled: Boolean? = null
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        androidx.room.ForeignKey(
            entity = ChatEntity::class,
            parentColumns = ["id"],
            childColumns = ["chatId"],
            onDelete = androidx.room.ForeignKey.CASCADE
        )
    ],
    indices = [androidx.room.Index("chatId")]
)
data class MessageEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val chatId: String,
    val content: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
