package com.semi.watchai

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Delete
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {

    // ========== Chat 操作 ==========

    @Query("SELECT * FROM chats ORDER BY createdAt DESC")
    fun getAllChats(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats ORDER BY createdAt DESC")
    suspend fun getAllChatsSync(): List<ChatEntity>

    @Query("SELECT * FROM chats WHERE id = :chatId")
    suspend fun getChatById(chatId: String): ChatEntity?

    @Insert
    suspend fun insertChat(chat: ChatEntity)

    @Update
    suspend fun updateChat(chat: ChatEntity)

    @Delete
    suspend fun deleteChat(chat: ChatEntity)

    @Query("DELETE FROM chats WHERE id = :chatId")
    suspend fun deleteChatById(chatId: String)

    // 一次性回填：把 contextMode 为 NULL 的旧对话冻结为当前全局值（幂等）
    @Query("UPDATE chats SET contextMode = :mode WHERE contextMode IS NULL")
    suspend fun backfillNullContextMode(mode: String)

    // ========== Message 操作 ==========

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    fun getMessagesByChatId(chatId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    suspend fun getMessagesByChatIdSync(chatId: String): List<MessageEntity>

    @Insert
    suspend fun insertMessage(message: MessageEntity)

    @Delete
    suspend fun deleteMessage(message: MessageEntity)

    @Query("DELETE FROM messages WHERE chatId = :chatId")
    suspend fun deleteMessagesByChatId(chatId: String)

    // ========== 联合查询 ==========

    @Query("""
        SELECT chats.id, chats.title, chats.createdAt, COUNT(messages.id) as messageCount
        FROM chats
        LEFT JOIN messages ON chats.id = messages.chatId
        GROUP BY chats.id
        ORDER BY chats.createdAt DESC
    """)
    fun getChatsWithMessageCount(): Flow<List<ChatWithMessageCount>>
}

// 辅助数据类
data class ChatWithMessageCount(
    val id: String,
    val title: String,
    val createdAt: Long,
    val messageCount: Int
)