package com.semi.watchai

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 选择模型注册表测试：预设信息、兜底逻辑、供应商 model 字段兼容。
 */
class AiModelsTest {

    @Test
    fun deepseek_has_preset_url_and_web_search() {
        val info = AiModels.byId(AiModels.DEEPSEEK)
        assertEquals("DeepSeek", info.displayName)
        assertEquals("https://api.deepseek.com", info.presetBaseUrl)
        assertTrue(info.supportsWebSearch)
    }

    @Test
    fun custom_has_no_preset_and_no_web_search() {
        val info = AiModels.byId(AiModels.CUSTOM)
        assertNull(info.presetBaseUrl)
        assertFalse(info.supportsWebSearch)
    }

    @Test
    fun null_and_unknown_id_fall_back_to_custom() {
        assertEquals(AiModels.CUSTOM, AiModels.byId(null).id)
        assertEquals(AiModels.CUSTOM, AiModels.byId("unknown-model").id)
    }

    @Test
    fun provider_model_info_resolves() {
        val deepseek = AiProvider(
            name = "D", baseUrl = "u", apiKey = "k", model = AiModels.DEEPSEEK
        )
        assertEquals(AiModels.DEEPSEEK, deepseek.modelInfo().id)
        assertTrue(deepseek.supportsWebSearch())

        val custom = AiProvider(name = "C", baseUrl = "u", apiKey = "k")
        assertFalse(custom.supportsWebSearch())
        assertEquals(AiModels.CUSTOM, custom.modelInfo().id)
    }

    @Test
    fun gson_old_json_without_model_resolves_to_custom() {
        val gson = Gson()
        val provider = gson.fromJson(
            """{"id":"p1","name":"旧数据","baseUrl":"https://x","apiKey":"k"}""",
            AiProvider::class.java
        )
        assertNull(provider.model)
        assertEquals(AiModels.CUSTOM, provider.modelInfo().id)
    }

    @Test
    fun gson_round_trip_preserves_model() {
        val gson = Gson()
        val provider = gson.fromJson(
            """{"id":"p1","name":"D","baseUrl":"https://api.deepseek.com","apiKey":"k","model":"deepseek"}""",
            AiProvider::class.java
        )
        assertEquals(AiModels.DEEPSEEK, provider.model)
    }
}
