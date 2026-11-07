package com.semi.watchai.util

/**
 * 把一条消息规划成「段落 / 公式行」序列，决定每个公式的呈现方式（纯函数，便于单元测试）。
 *
 * 判定规则（B + A 方案）：
 * - **B 行内**：公式自然宽度 ≤ 可用宽度 → 留在段落里用行内 span 渲染（基线对齐，不打断排版）。
 * - **A 独立可横滑窗口**：宽度超出 → 提升为独立一行，外层套 HorizontalScrollView 横向拖动查看。
 * - **回退**：RaTeX 解析失败（measure 返回 null）→ 用 [MathTextConverter.toPlainMath] 的 Unicode 文本顶上，
 *   保证任何情况下都不会吐出裸 LaTeX。
 *
 * 本对象不依赖 Android：宽度由调用方以 [measure] 回调注入（生产环境用 RaTeX 测量，测试里用假函数）。
 */
object MathContentPlanner {

    /** 段落内的一个片段。 */
    sealed interface Run {
        /** 普通文本。 */
        data class Text(val text: String) : Run

        /** 行内公式（已确认放得下）。 */
        data class Formula(val latex: String) : Run
    }

    /** 渲染行。 */
    sealed interface Row {
        /** 一段文本 + 行内公式的混排（一个 TextView）。 */
        data class Paragraph(val runs: List<Run>) : Row

        /** 独占一行的块级公式；[fits] 为 false 时需要横向滑动窗口。 */
        data class BlockFormula(val latex: String, val fits: Boolean) : Row
    }

    /**
     * @param raw              模型返回的原始文本
     * @param availableWidthPx 气泡内可用宽度（px）
     * @param measure          测量公式自然宽度（px）；解析失败返回 null
     */
    fun plan(
        raw: String,
        availableWidthPx: Int,
        measure: (latex: String, displayMode: Boolean) -> Float?
    ): List<Row> {
        if (raw.isEmpty()) return emptyList()

        val rows = ArrayList<Row>()
        var runs = ArrayList<Run>()

        fun flush() {
            if (runs.isEmpty()) return
            // 纯空白段落（块级公式前后的换行）不占位
            val hasContent = runs.any { it is Run.Formula || (it is Run.Text && it.text.isNotBlank()) }
            if (hasContent) rows.add(Row.Paragraph(runs))
            runs = ArrayList()
        }

        for (seg in MathTextConverter.segments(raw)) {
            when (seg) {
                is MathTextConverter.Segment.Text ->
                    if (seg.raw.isNotEmpty()) runs.add(Run.Text(seg.raw))

                is MathTextConverter.Segment.Inline -> {
                    val width = measure(seg.latex, false)
                    when {
                        width == null -> runs.add(Run.Text(MathTextConverter.toPlainMath(seg.latex)))
                        width <= availableWidthPx -> runs.add(Run.Formula(seg.latex))
                        else -> {
                            flush()
                            rows.add(Row.BlockFormula(seg.latex, fits = false))
                        }
                    }
                }

                is MathTextConverter.Segment.Block -> {
                    val width = measure(seg.latex, true)
                    if (width == null) {
                        runs.add(Run.Text(MathTextConverter.toPlainMath(seg.latex)))
                    } else {
                        flush()
                        rows.add(Row.BlockFormula(seg.latex, fits = width <= availableWidthPx))
                    }
                }
            }
        }
        flush()
        return rows
    }
}
