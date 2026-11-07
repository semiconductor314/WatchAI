package com.semi.watchai

import com.semi.watchai.util.MathTextConverter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MathTextConverter 单元测试：覆盖分隔符识别、符号替换、结构命令、降级，
 * 以及流式（尾部未闭合片段）与代码块保护等边界。
 */
class MathTextConverterTest {

    private fun display(raw: String) = MathTextConverter.toDisplay(raw)
    private fun streaming(raw: String) = MathTextConverter.toDisplay(raw, streaming = true)

    // ---------- 分隔符：四类写法 ----------

    @Test
    fun inline_dollar_superscript() {
        assertEquals("x² + y² = r²", display("\$x^2 + y^2 = r^2\$"))
    }

    @Test
    fun block_double_dollar_fraction() {
        assertEquals("a/b", display("\$\$\\frac{a}{b}\$\$"))
    }

    @Test
    fun inline_paren_backslash_delimiters() {
        assertEquals("α ≤ β", display("\\(\\alpha \\le \\beta\\)"))
    }

    @Test
    fun block_bracket_backslash_delimiters() {
        assertEquals("∫₀¹ x dx", display("\\[ \\int_0^1 x dx \\]"))
    }

    // ---------- 结构命令 ----------

    @Test
    fun sqrt_and_nth_root() {
        assertEquals("√(x)", display("\$\\sqrt{x}\$"))
        assertEquals("∛(x)", display("\$\\sqrt[3]{x}\$"))
    }

    @Test
    fun fraction_with_operator_operand_gets_parentheses() {
        assertEquals("(a+b)/c", display("\$\\frac{a+b}{c}\$"))
        assertEquals("1/2", display("\$\\frac{1}{2}\$"))
    }

    @Test
    fun text_command_keeps_inner_text() {
        assertEquals("速度 = d/t", display("\$\\text{速度} = \\frac{d}{t}\$"))
    }

    @Test
    fun greek_superscript_and_subscript_together() {
        assertEquals("∑ᵢ₌₁ⁿ", display("\$\\sum_{i=1}^{n}\$"))
    }

    // ---------- 混排与多处公式 ----------

    @Test
    fun mixed_chinese_text_and_formula() {
        assertEquals("答案是 E=mc² 哦", display("答案是 \$E=mc^2\$ 哦"))
    }

    @Test
    fun two_formulas_in_one_message() {
        assertEquals("设 a=1，则 b=2", display("设 \$a=1\$，则 \$b=2\$"))
    }

    @Test
    fun block_formula_on_its_own_line_keeps_neighbour_lines() {
        assertEquals("前\na=b\n后", display("前\n\\[a=b\\]\n后"))
    }

    @Test
    fun block_formula_does_not_leave_dangling_spaces_on_previous_line() {
        assertEquals("公式：\nx = 1", display("公式： \$\$x = 1\$\$"))
    }

    @Test
    fun aligned_environment_rows_lose_trailing_whitespace() {
        assertEquals(
            "a  b\n c  d",
            display("\$\$\\begin{pmatrix} a & b \\\\ c & d \\end{pmatrix}\$\$")
        )
    }

    // ---------- 流式：尾部未闭合片段 ----------

    @Test
    fun unclosed_math_is_dropped_while_streaming() {
        assertEquals("", streaming("\$x^{2"))
        assertEquals("前文 ", streaming("前文 \$x^{2"))
    }

    @Test
    fun unclosed_math_is_kept_raw_when_not_streaming() {
        assertEquals("\$x^{2", display("\$x^{2"))
        assertEquals("前文 \$x^{2", display("前文 \$x^{2"))
    }

    @Test
    fun closed_formula_is_rendered_even_while_streaming() {
        assertEquals("a/b 后面还有", streaming("\$\\frac{a}{b}\$ 后面还有"))
    }

    // ---------- 保护区：代码块 / 行内代码 ----------

    @Test
    fun fenced_code_block_is_not_converted() {
        val raw = "看代码：\n```\n\$x^2 + \\frac{1}{2}\$\n```\n完"
        assertEquals(raw, display(raw))
    }

    @Test
    fun inline_code_is_not_converted() {
        assertEquals("用 `\$x^2\$` 表示", display("用 `\$x^2\$` 表示"))
    }

    // ---------- 误判防护 ----------

    @Test
    fun currency_dollar_amounts_are_not_treated_as_math() {
        val raw = "价格 \$5 和 \$10"
        assertEquals(raw, display(raw))
    }

    // ---------- 降级 ----------

    @Test
    fun unknown_command_loses_backslash_but_keeps_name() {
        assertEquals("foox", display("\$\\foo{x}\$"))
    }

    @Test
    fun empty_and_plain_text_pass_through() {
        assertEquals("", display(""))
        assertEquals("你好，世界", display("你好，世界"))
        assertEquals("你好，世界", streaming("你好，世界"))
    }

    @Test
    fun plain_text_without_any_delimiter_is_returned_unchanged() {
        val raw = "C:\\Users\\test 和第 2 个问题"
        assertEquals(raw, display(raw))
        assertFalse(display(raw).contains("\$"))
    }

    @Test
    fun escaped_dollar_is_not_a_delimiter() {
        assertTrue(display("\\$5 一个").contains("5"))
    }
}
