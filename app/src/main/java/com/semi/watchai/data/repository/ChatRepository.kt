package com.semi.watchai.data.repository

import com.semi.watchai.data.local.dao.ChatDao
import com.semi.watchai.data.local.entity.ChatEntity
import com.semi.watchai.data.local.entity.MessageEntity
import com.semi.watchai.data.model.Chat
import com.semi.watchai.data.model.Message
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ChatRepository(private val dao: ChatDao) {

    // ========== 获取数据 ==========

    // 获取所有聊天（Flow，自动更新）
    fun getAllChatsFlow(): Flow<List<Chat>> {
        return dao.getAllChats().map { entities ->
            entities.map { entity ->
                Chat(
                    id = entity.id,
                    title = entity.title,
                    createdAt = entity.createdAt,
                    providerId = entity.providerId,
                    modelOnline = entity.modelOnline,
                    promptId = entity.promptId,
                    contextMode = entity.contextMode,
                    streamEnabled = entity.streamEnabled
                )
            }
        }
    }

    // 获取单个聊天
    suspend fun getChat(chatId: String): Chat? {
        val entity = dao.getChatById(chatId) ?: return null
        val messages = dao.getMessagesByChatIdSync(chatId).map { msgEntity ->
            Message(
                id = msgEntity.id,
                content = msgEntity.content,
                isUser = msgEntity.isUser,
                timestamp = msgEntity.timestamp
            )
        }
        return Chat(
            id = entity.id,
            title = entity.title,
            messages = messages.toMutableList(),
            createdAt = entity.createdAt,
            providerId = entity.providerId,
            modelOnline = entity.modelOnline,
            promptId = entity.promptId,
            contextMode = entity.contextMode,
            streamEnabled = entity.streamEnabled
        )
    }

    // 获取聊天消息（Flow，自动更新）
    fun getMessagesFlow(chatId: String): Flow<List<Message>> {
        return dao.getMessagesByChatId(chatId).map { entities ->
            entities.map { entity ->
                Message(
                    id = entity.id,
                    content = entity.content,
                    isUser = entity.isUser,
                    timestamp = entity.timestamp
                )
            }
        }
    }

    // ========== 写入数据 ==========

    // 创建新聊天（providerId/modelOnline/promptId = 首页设置的快照，
    // 作为该对话的默认设置；此后首页改动不再影响它）
    suspend fun createChat(
        title: String = "新聊天",
        providerId: String? = null,
        modelOnline: Boolean? = null,
        promptId: String? = null,
        contextMode: String? = null,
        streamEnabled: Boolean? = null
    ): Chat {
        val chat = Chat(
            id = java.util.UUID.randomUUID().toString(),
            title = title,
            providerId = providerId,
            modelOnline = modelOnline,
            promptId = promptId,
            contextMode = contextMode,
            streamEnabled = streamEnabled
        )
        dao.insertChat(
            ChatEntity(
                id = chat.id,
                title = chat.title,
                createdAt = chat.createdAt,
                providerId = chat.providerId,
                modelOnline = chat.modelOnline,
                promptId = chat.promptId,
                contextMode = chat.contextMode,
                streamEnabled = chat.streamEnabled
            )
        )
        return chat
    }

    // 更新聊天标题
    suspend fun updateChatTitle(chatId: String, newTitle: String) {
        val entity = dao.getChatById(chatId) ?: return
        entity.title = newTitle
        dao.updateChat(entity)
    }

    // 一次性回填：把 contextMode 为 NULL 的已有对话冻结为当前全局值，
    // 此后首页全局改动不再影响这些旧对话（由一次性标志保证只执行一次）
    suspend fun backfillNullContextMode(mode: String) {
        dao.backfillNullContextMode(mode)
    }

    // 更新每对话的设置（只更新非 null 字段）
    suspend fun updateChatSettings(
        chatId: String,
        providerId: String? = null,
        modelOnline: Boolean? = null,
        promptId: String? = null,
        contextMode: String? = null,
        streamEnabled: Boolean? = null
    ) {
        val entity = dao.getChatById(chatId) ?: return
        if (providerId != null) entity.providerId = providerId
        if (modelOnline != null) entity.modelOnline = modelOnline
        if (promptId != null) entity.promptId = promptId
        if (contextMode != null) entity.contextMode = contextMode
        if (streamEnabled != null) entity.streamEnabled = streamEnabled
        dao.updateChat(entity)
    }

    // 删除聊天
    suspend fun deleteChat(chatId: String) {
        dao.deleteMessagesByChatId(chatId)
        dao.deleteChatById(chatId)
    }

    // 添加消息
    suspend fun addMessage(chatId: String, message: Message) {
        val msgEntity = MessageEntity(
            id = message.id,
            chatId = chatId,
            content = message.content,
            isUser = message.isUser,
            timestamp = message.timestamp
        )
        dao.insertMessage(msgEntity)
    }

    // 「重新加载」：删除被截断的消息（空列表直接返回，避免空 IN 查询）
    suspend fun deleteMessages(ids: List<String>) {
        if (ids.isEmpty()) return
        dao.deleteMessagesByIds(ids)
    }

    // ========== 批量操作 ==========

    // 删除所有聊天
    suspend fun deleteAllChats() {
        val chats = dao.getAllChatsSync()
        chats.forEach { chat ->
            dao.deleteMessagesByChatId(chat.id)
            dao.deleteChat(chat)
        }
    }
}
