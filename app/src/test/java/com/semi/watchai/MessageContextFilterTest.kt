package com.semi.watchai

import com.semi.watchai.data.model.Message
import com.semi.watchai.data.repository.ProviderRepository
import com.semi.watchai.util.MessageContextFilter
import org.junit.Assert.assertEquals
import org.junit.Test

class MessageContextFilterTest {

    private fun msg(content: String, isUser: Boolean) =
        Message(content = content, isUser = isUser)

    @Test
    fun all_mode_returns_all_messages() {
        val messages = listOf(msg("u1", true), msg("a1", false), msg("u2", true))
        assertEquals(
            messages,
            MessageContextFilter.filter(messages, ProviderRepository.CONTEXT_MODE_ALL)
        )
    }

    @Test
    fun none_mode_returns_only_current_message() {
        val messages = listOf(msg("u1", true), msg("a1", false), msg("u2", true))
        assertEquals(
            listOf(messages.last()),
            MessageContextFilter.filter(messages, ProviderRepository.CONTEXT_MODE_NONE)
        )
    }

    @Test
    fun last_mode_returns_previous_round_plus_current() {
        val messages = listOf(
            msg("u1", true), msg("a1", false),
            msg("u2", true), msg("a2", false),
            msg("u3", true)
        )
        assertEquals(
            listOf(messages[2], messages[3], messages[4]),
            MessageContextFilter.filter(messages, ProviderRepository.CONTEXT_MODE_LAST)
        )
    }

    @Test
    fun last_mode_with_no_assistant_reply_returns_from_last_user() {
        val messages = listOf(msg("u1", true), msg("u2", true))
        assertEquals(
            listOf(messages[0], messages[1]),
            MessageContextFilter.filter(messages, ProviderRepository.CONTEXT_MODE_LAST)
        )
    }

    @Test
    fun last_mode_with_single_message_returns_just_it() {
        val messages = listOf(msg("u1", true))
        assertEquals(
            listOf(messages[0]),
            MessageContextFilter.filter(messages, ProviderRepository.CONTEXT_MODE_LAST)
        )
    }

    @Test
    fun filter_on_empty_list_returns_empty() {
        assertEquals(
            emptyList<Message>(),
            MessageContextFilter.filter(emptyList(), ProviderRepository.CONTEXT_MODE_NONE)
        )
        assertEquals(
            emptyList<Message>(),
            MessageContextFilter.filter(emptyList(), ProviderRepository.CONTEXT_MODE_LAST)
        )
    }
}
