package com.semi.watchai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProviderTest {

    private fun provider(apiKey: String) = AiProvider(name = "T", baseUrl = "https://x", apiKey = apiKey)

    // ========== scrubDefaultApiKey ==========

    @Test
    fun scrub_legacy_default_key_blanks_api_key() {
        val p = provider(apiKey = "sk-c6d9f96ab3844ffdaf174aba3d7b049b")
        assertEquals("", p.scrubDefaultApiKey().apiKey)
    }

    @Test
    fun scrub_custom_key_unchanged() {
        val p = provider(apiKey = "sk-my-custom-key")
        assertEquals(p, p.scrubDefaultApiKey())
    }

    // ========== hasConfiguredApiKey ==========

    @Test
    fun has_configured_api_key_false_when_blank() {
        assertFalse(provider(apiKey = "").hasConfiguredApiKey())
        assertFalse(provider(apiKey = "   ").hasConfiguredApiKey())
    }

    @Test
    fun has_configured_api_key_false_for_legacy_default() {
        assertFalse(provider(apiKey = "sk-c6d9f96ab3844ffdaf174aba3d7b049b").hasConfiguredApiKey())
    }

    @Test
    fun has_configured_api_key_true_for_custom_key() {
        assertTrue(provider(apiKey = "sk-my-custom-key").hasConfiguredApiKey())
    }
}
