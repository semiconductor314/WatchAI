package com.semi.watchai

import com.semi.watchai.util.MathTextConverter
import com.semi.watchai.util.MathTextConverter.Segment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MathTextConverter.segments 单元测试：切段结果供 UI 层决定
 * "行内 span（B）" 还是 "独立可横滑窗口（A）"。
 */
class MathSegmentTest {

    private fun segs(raw: String) = MathTextConverter.segments(raw)

    /** 各片段 raw 拼起来必须还原原文（不丢字符、不重复）。 */
    private fun assertLossless(raw: String) {
        assertEquals(raw, segs(raw).joinToString("") { it.raw })
    }

    @Test
    fun plain_text_is_a_single_text_segment() {
        val s = segs("你好，世界")
        assertEquals(1, s.size)
        assertTrue(s[0] is Segment.Text)
        assertEquals("你好，世界", s[0].raw)
    }

    @Test
    fun empty_input_has_no_segments() {
        assertEquals(0, segs("").size)
    }

    @Test
    fun inline_dollar_between_texts() {
        val s = segs("答案是 \$E=mc^2\$ 哦")
        assertEquals(3, s.size)
        assertEquals("答案是 ", s[0].raw)
        assertEquals(Segment.Inline("\$E=mc^2\$", "E=mc^2"), s[1])
        assertEquals(" 哦", s[2].raw)
        assertLossless("答案是 \$E=mc^2\$ 哦")
    }

    @Test
    fun paren_backslash_is_inline() {
        val s = segs("前\\(\\alpha\\)后")
        assertEquals(3, s.size)
        assertEquals(Segment.Inline("\\(\\alpha\\)", "\\alpha"), s[1])
    }

    @Test
    fun double_dollar_is_block() {
        val s = segs("前\$\$\\frac{a}{b}\$\$后")
        assertEquals(3, s.size)
        assertEquals(Segment.Block("\$\$\\frac{a}{b}\$\$", "\\frac{a}{b}"), s[1])
        assertLossless("前\$\$\\frac{a}{b}\$\$后")
    }

    @Test
    fun bracket_backslash_is_block() {
        val s = segs("\\[ \\int_0^1 x \\]")
        assertEquals(1, s.size)
        assertEquals(Segment.Block("\\[ \\int_0^1 x \\]", " \\int_0^1 x "), s[0])
    }

    @Test
    fun latex_fence_is_block_and_delimiters_are_stripped() {
        val raw = "```latex\n\$\\frac{a}{b}\$\n```"
        val s = segs(raw)
        assertEquals(1, s.size)
        assertEquals(Segment.Block(raw, "\\frac{a}{b}"), s[0])
    }

    @Test
    fun non_math_fence_stays_text() {
        val raw = "```python\nx = 1\n```"
        val s = segs(raw)
        assertEquals(1, s.size)
        assertTrue(s[0] is Segment.Text)
        assertEquals(raw, s[0].raw)
    }

    @Test
    fun inline_code_is_protected() {
        val raw = "用 `\$x^2\$` 表示"
        val s = segs(raw)
        assertEquals(1, s.size)
        assertTrue(s[0] is Segment.Text)
    }

    @Test
    fun currency_is_not_math() {
        val raw = "价格 \$5 和 \$10"
        val s = segs(raw)
        assertEquals(1, s.size)
        assertTrue(s[0] is Segment.Text)
        assertEquals(raw, s[0].raw)
    }

    @Test
    fun unclosed_math_is_text_when_not_streaming() {
        val s = segs("前文 \$x^{2")
        assertEquals(1, s.size)
        assertEquals("前文 \$x^{2", s[0].raw)
    }

    @Test
    fun unclosed_math_tail_is_dropped_when_streaming() {
        val s = MathTextConverter.segments("前文 \$x^{2", streaming = true)
        assertEquals(1, s.size)
        assertEquals("前文 ", s[0].raw)
    }

    @Test
    fun mixed_message_keeps_order() {
        val raw = "设 \$a=1\$，则\n\$\$b=2\$\$\n完"
        val s = segs(raw)
        assertEquals(5, s.size)
        assertTrue(s[0] is Segment.Text)
        assertTrue(s[1] is Segment.Inline)
        assertTrue(s[2] is Segment.Text)
        assertTrue(s[3] is Segment.Block)
        assertTrue(s[4] is Segment.Text)
        assertLossless(raw)
    }

    // ---------- toDisplay 仍须与切段结果一致（回归保护） ----------

    @Test
    fun toDisplay_matches_segment_join_for_common_case() {
        val raw = "答案是 \$E=mc^2\$ 哦"
        assertEquals("答案是 E=mc² 哦", MathTextConverter.toDisplay(raw))
        assertEquals("答案是 E=mc² 哦", toDisplayFromSegments(raw))
    }

    private fun toDisplayFromSegments(raw: String): String {
        val sb = StringBuilder()
        for (seg in segs(raw)) {
            when (seg) {
                is Segment.Text -> sb.append(seg.raw)
                is Segment.Inline -> sb.append(MathTextConverter.toPlainMath(seg.latex))
                is Segment.Block -> sb.append(MathTextConverter.toPlainMath(seg.latex))
            }
        }
        return sb.toString()
    }
}
