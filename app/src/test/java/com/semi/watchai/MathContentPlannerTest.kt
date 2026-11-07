package com.semi.watchai

import com.semi.watchai.util.MathContentPlanner
import com.semi.watchai.util.MathContentPlanner.Row
import com.semi.watchai.util.MathContentPlanner.Run
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MathContentPlanner 单元测试：决定每个公式是"行内 span（B）"还是"独立可横滑窗口（A）"。
 *
 * measure 用假函数注入（公式宽度 = 我指定的值），因此本测试完全不依赖 Android / RaTeX。
 */
class MathContentPlannerTest {

    /** 所有公式都返回固定宽度。 */
    private fun fixed(width: Float?) = { _: String, _: Boolean -> width }

    /** 按模式返回不同宽度：行内 / 块级。 */
    private fun byMode(inline: Float?, block: Float?) =
        { _: String, displayMode: Boolean -> if (displayMode) block else inline }

    @Test
    fun inline_formula_fits_stays_in_paragraph() {
        val rows = MathContentPlanner.plan("答案是 \$E=mc^2\$ 哦", 100, fixed(50f))
        assertEquals(1, rows.size)
        val p = rows[0] as Row.Paragraph
        assertEquals(
            listOf(Run.Text("答案是 "), Run.Formula("E=mc^2"), Run.Text(" 哦")),
            p.runs
        )
    }

    @Test
    fun inline_formula_too_wide_becomes_scroll_row() {
        val rows = MathContentPlanner.plan("答案是 \$E=mc^2\$ 哦", 100, fixed(150f))
        assertEquals(3, rows.size)
        assertEquals(listOf<Run>(Run.Text("答案是 ")), (rows[0] as Row.Paragraph).runs)
        assertEquals(Row.BlockFormula("E=mc^2", fits = false), rows[1])
        assertEquals(listOf<Run>(Run.Text(" 哦")), (rows[2] as Row.Paragraph).runs)
    }

    @Test
    fun block_formula_always_gets_its_own_row() {
        val rows = MathContentPlanner.plan("前\$\$\\frac{a}{b}\$\$后", 200, fixed(80f))
        assertEquals(3, rows.size)
        assertTrue(rows[0] is Row.Paragraph)
        assertEquals(Row.BlockFormula("\\frac{a}{b}", fits = true), rows[1])
        assertTrue(rows[2] is Row.Paragraph)
    }

    @Test
    fun block_formula_too_wide_is_scrollable() {
        val rows = MathContentPlanner.plan("\$\$\\begin{pmatrix} a & b \\\\ c & d \\end{pmatrix}\$\$", 200, fixed(600f))
        assertEquals(1, rows.size)
        assertEquals(Row.BlockFormula("\\begin{pmatrix} a & b \\\\ c & d \\end{pmatrix}", fits = false), rows[0])
    }

    @Test
    fun parse_failure_falls_back_to_unicode_text() {
        val rows = MathContentPlanner.plan("速度 \$\\frac{d}{t}\$ 不变", 200, fixed(null))
        assertEquals(1, rows.size)
        val p = rows[0] as Row.Paragraph
        assertEquals(listOf<Run>(Run.Text("速度 "), Run.Text("d/t"), Run.Text(" 不变")), p.runs)
    }

    @Test
    fun block_parse_failure_also_falls_back_to_text() {
        val rows = MathContentPlanner.plan("\$\$\\frac{a}{b}\$\$", 200, fixed(null))
        assertEquals(1, rows.size)
        val p = rows[0] as Row.Paragraph
        assertEquals(listOf<Run>(Run.Text("a/b")), p.runs)
    }

    @Test
    fun inline_and_block_use_different_display_mode_when_measuring() {
        val modes = mutableListOf<Boolean>()
        MathContentPlanner.plan("\$a=1\$ \$\$b=2\$\$", 100) { _, displayMode ->
            modes.add(displayMode)
            10f
        }
        assertEquals(listOf(false, true), modes)
    }

    @Test
    fun plain_text_is_one_paragraph() {
        val rows = MathContentPlanner.plan("没有任何公式的一段话", 100, fixed(0f))
        assertEquals(1, rows.size)
        assertEquals(listOf<Run>(Run.Text("没有任何公式的一段话")), (rows[0] as Row.Paragraph).runs)
    }

    @Test
    fun empty_input_produces_no_rows() {
        assertEquals(0, MathContentPlanner.plan("", 100, fixed(0f)).size)
    }

    @Test
    fun blank_paragraph_around_block_is_dropped() {
        val rows = MathContentPlanner.plan("\n\n\$\$a=b\$\$\n\n", 200, fixed(50f))
        assertEquals(1, rows.size)
        assertEquals(Row.BlockFormula("a=b", fits = true), rows[0])
    }

    @Test
    fun consecutive_inline_formulas_share_one_paragraph() {
        val rows = MathContentPlanner.plan("\$a=1\$ 和 \$b=2\$", 100, fixed(20f))
        assertEquals(1, rows.size)
        val runs = (rows[0] as Row.Paragraph).runs
        assertEquals(3, runs.size)
        assertEquals(Run.Formula("a=1"), runs[0])
        assertEquals(Run.Formula("b=2"), runs[2])
    }

    @Test
    fun latex_fence_is_planned_as_block_without_delimiters() {
        val rows = MathContentPlanner.plan("```latex\nx = \\frac{a}{b}\n```", 100, byMode(10f, 400f))
        assertEquals(1, rows.size)
        assertEquals(Row.BlockFormula("x = \\frac{a}{b}", fits = false), rows[0])
    }
}
