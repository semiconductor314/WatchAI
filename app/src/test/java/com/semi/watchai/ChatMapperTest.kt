package com.semi.watchai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatMapperTest {

    @Test
    fun toChat_preservesContextMode() {
        val entity = ChatEntity(
            id = "c1", title = "t",
            providerId = "p1", modelOnline = true, promptId = "prompt1",
            contextMode = "last"
        )
        val chat = ChatMapper.toChat(entity, emptyList())
        assertEquals("last", chat.contextMode)
        assertEquals("p1", chat.providerId)
        assertEquals(true, chat.modelOnline)
    }

    @Test
    fun toEntity_preservesContextMode() {
        val chat = Chat(
            id = "c1", title = "t",
            providerId = "p1", modelOnline = true, promptId = "prompt1",
            contextMode = "none"
        )
        val entity = ChatMapper.toEntity(chat)
        assertEquals("none", entity.contextMode)
    }

    @Test
    fun contextMode_defaultsToNull() {
        val entity = ChatEntity(id = "c1", title = "t")
        assertNull(entity.contextMode)
        val chat = Chat(id = "c1", title = "t")
        assertNull(chat.contextMode)
    }
}
