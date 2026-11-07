package com.semi.watchai

import com.semi.watchai.network.DeepSeekApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 流式响应 SSE 单行解析测试。
 * 覆盖 OpenAI 兼容接口的常见行格式：
 * - data: {json}（增量块）
 * - data: [DONE]（结束标记）
 * - 空行 / keep-alive 注释
 * - 无 content 的增量（如仅 role 字段的首行）
 */
class DeepSeekSseParserTest {

    @Test
    fun parses_content_chunk() {
        val line = """data: {"choices":[{"delta":{"content":"你好"}}]}"""
        assertEquals("你好", DeepSeekApi.parseStreamChunk(line))
    }

    @Test
    fun parses_multiple_chunks_accumulate() {
        val chunks = listOf(
            """data: {"choices":[{"delta":{"content":"你"}}]}""",
            """data: {"choices":[{"delta":{"content":"好"}}]}""",
            """data: {"choices":[{"delta":{"content":"！"}}]}"""
        )
        assertEquals("你好！", chunks.mapNotNull { DeepSeekApi.parseStreamChunk(it) }.joinToString(""))
    }

    @Test
    fun data_prefix_without_space_is_supported() {
        val line = """data:{"choices":[{"delta":{"content":"hi"}}]}"""
        assertEquals("hi", DeepSeekApi.parseStreamChunk(line))
    }

    @Test
    fun done_marker_returns_null() {
        assertNull(DeepSeekApi.parseStreamChunk("data: [DONE]"))
    }

    @Test
    fun empty_line_returns_null() {
        assertNull(DeepSeekApi.parseStreamChunk(""))
        assertNull(DeepSeekApi.parseStreamChunk("   "))
    }

    @Test
    fun keep_alive_comment_returns_null() {
        assertNull(DeepSeekApi.parseStreamChunk(": keep-alive"))
        assertNull(DeepSeekApi.parseStreamChunk(": ping"))
    }

    @Test
    fun delta_without_content_returns_null() {
        // 流首行常见：只带 role 不带 content
        val line = """data: {"choices":[{"delta":{"role":"assistant"}}]}"""
        assertNull(DeepSeekApi.parseStreamChunk(line))
    }

    @Test
    fun delta_with_null_content_returns_null() {
        val line = """data: {"choices":[{"delta":{"content":null}}]}"""
        assertNull(DeepSeekApi.parseStreamChunk(line))
    }

    @Test
    fun malformed_json_returns_null() {
        assertNull(DeepSeekApi.parseStreamChunk("data: {not json"))
    }

    @Test
    fun parseStreamDelta_extracts_content_and_reasoning() {
        val line = """data: {"choices":[{"delta":{"content":"答","reasoning_content":"想"}}]}"""
        val delta = DeepSeekApi.parseStreamDelta(line)
        assertEquals("答", delta?.content)
        assertEquals("想", delta?.reasoningContent)
    }

    @Test
    fun parseStreamReasoning_extracts_reasoning() {
        val line = """data: {"choices":[{"delta":{"reasoning_content":"先分析"}}]}"""
        assertEquals("先分析", DeepSeekApi.parseStreamReasoning(line))
    }

    @Test
    fun parseStreamReasoning_returns_null_when_no_reasoning() {
        val line = """data: {"choices":[{"delta":{"content":"x"}}]}"""
        assertNull(DeepSeekApi.parseStreamReasoning(line))
    }

    @Test
    fun parseStreamChunk_returns_null_for_reasoning_only_line() {
        val line = """data: {"choices":[{"delta":{"reasoning_content":"想"}}]}"""
        assertNull(DeepSeekApi.parseStreamChunk(line))
    }
}
