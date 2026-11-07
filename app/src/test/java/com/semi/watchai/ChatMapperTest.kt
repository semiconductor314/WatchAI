package com.semi.watchai

import com.semi.watchai.data.local.entity.ChatEntity
import com.semi.watchai.data.model.Chat
import com.semi.watchai.data.model.ChatMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatMapperTest {

    @Test
    fun toChat_preservesContextMode() {
        val entity = ChatEntity(
            id = "c1", title = "t",
            providerId = "p1", modelOnline = true, promptId = "prompt1",
            contextMode = "last", streamEnabled = false
        )
        val chat = ChatMapper.toChat(entity, emptyList())
        assertEquals("last", chat.contextMode)
        assertEquals("p1", chat.providerId)
        assertEquals(true, chat.modelOnline)
        assertEquals(false, chat.streamEnabled)
    }

    @Test
    fun toEntity_preservesContextMode() {
        val chat = Chat(
            id = "c1", title = "t",
            providerId = "p1", modelOnline = true, promptId = "prompt1",
            contextMode = "none", streamEnabled = true
        )
        val entity = ChatMapper.toEntity(chat)
        assertEquals("none", entity.contextMode)
        assertEquals(true, entity.streamEnabled)
    }

    @Test
    fun contextMode_defaultsToNull() {
        val entity = ChatEntity(id = "c1", title = "t")
        assertNull(entity.contextMode)
        assertNull(entity.streamEnabled)
        val chat = Chat(id = "c1", title = "t")
        assertNull(chat.contextMode)
        assertNull(chat.streamEnabled)
    }
}
