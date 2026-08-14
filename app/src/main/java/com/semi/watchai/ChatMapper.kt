package com.semi.watchai

object ChatMapper {

    // ChatEntity -> Chat
    fun toChat(entity: ChatEntity, messages: List<Message> = emptyList()): Chat {
        return Chat(
            id = entity.id,
            title = entity.title,
            messages = messages.toMutableList(),
            createdAt = entity.createdAt,
            providerId = entity.providerId,
            modelOnline = entity.modelOnline,
            promptId = entity.promptId,
            contextMode = entity.contextMode
        )
    }

    // Chat -> ChatEntity
    fun toEntity(chat: Chat): ChatEntity {
        return ChatEntity(
            id = chat.id,
            title = chat.title,
            createdAt = chat.createdAt,
            providerId = chat.providerId,
            modelOnline = chat.modelOnline,
            promptId = chat.promptId,
            contextMode = chat.contextMode
        )
    }

    // MessageEntity -> Message
    fun toMessage(entity: MessageEntity): Message {
        return Message(
            id = entity.id,
            content = entity.content,
            isUser = entity.isUser,
            timestamp = entity.timestamp
        )
    }

    // Message -> MessageEntity
    fun toEntity(chatId: String, message: Message): MessageEntity {
        return MessageEntity(
            id = message.id,
            chatId = chatId,
            content = message.content,
            isUser = message.isUser,
            timestamp = message.timestamp
        )
    }
}