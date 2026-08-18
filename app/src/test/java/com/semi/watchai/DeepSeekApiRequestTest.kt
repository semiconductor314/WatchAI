package com.semi.watchai

import com.google.gson.Gson
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DeepSeekApi 请求体序列化测试：联网搜索字段的开关行为。
 */
class DeepSeekApiRequestTest {

    private val gson = Gson()

    @Test
    fun chat_request_omits_web_search_when_disabled() {
        val json = gson.toJson(
            DeepSeekApi.ChatRequest(
                model = "m",
                messages = listOf(DeepSeekApi.ReqMessage("user", "hi"))
            )
        )
        assertFalse(json.contains("web_search"))
    }

    @Test
    fun chat_request_includes_web_search_when_enabled() {
        val json = gson.toJson(
            DeepSeekApi.ChatRequest(
                model = "m",
                messages = listOf(DeepSeekApi.ReqMessage("user", "hi")),
                webSearch = true
            )
        )
        assertTrue(json.contains("\"web_search\":true"))
    }
}
