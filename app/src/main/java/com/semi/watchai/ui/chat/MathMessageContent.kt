package com.semi.watchai.ui.chat

import android.content.Context
import android.graphics.Color
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.semi.watchai.util.MathContentPlanner
import com.semi.watchai.util.MathContentPlanner.Row
import com.semi.watchai.util.MathContentPlanner.Run
import com.semi.watchai.util.MathTextConverter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 把一条 AI 消息构建成 View 树：文本段落 + 行内公式（B）+ 放不下的公式独占一行并横向滑动（A）。
 *
 * 布局形态：
 * ```
 * LinearLayout(vertical)
 * ├── TextView            ← 段落：文本段 + 行内公式 RaTeXSpan（基线对齐）
 * ├── HorizontalScrollView ← 溢出公式：固定宽度窗口，内部 MathFormulaView 按自然宽度绘制
 * └── TextView            ← 后续段落
 * ```
 *
 * 任何公式解析失败都会回退成 [MathTextConverter] 的 Unicode 文本，绝不外泄裸 LaTeX。
 */
object MathMessageContent {

    /** AI 气泡的正文样式（与 item_message_ai.xml 中的 15sp / #FFFFFF 保持一致）。 */
    private const val AI_TEXT_SIZE_SP = 15f
    private const val AI_TEXT_COLOR = Color.WHITE

    /**
     * @param availableWidthPx 气泡内可用宽度（px），决定行内公式是否会溢出
     */
    suspend fun build(
        context: Context,
        raw: String,
        availableWidthPx: Int,
    ): View {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        if (raw.isBlank()) return container

        val textSizePx = spToPx(context, AI_TEXT_SIZE_SP)
        val formulaSizePx = textSizePx

        // 1) 后台测量：决定每个公式是行内（B）还是独立可横滑窗口（A）
        val rows = withContext(Dispatchers.Default) {
            MathContentPlanner.plan(raw, availableWidthPx) { latex, displayMode ->
                MathFormulaCache.widthPxBlocking(
                    context.applicationContext,
                    latex,
                    displayMode,
                    AI_TEXT_COLOR,
                    formulaSizePx,
                )
            }
        }

        // 2) 主线程组装视图
        for (row in rows) {
            when (row) {
                is Row.Paragraph -> container.addView(
                    buildParagraph(context, row.runs, textSizePx)
                )

                is Row.BlockFormula -> container.addView(
                    buildFormulaRow(context, row, availableWidthPx, formulaSizePx)
                )
            }
        }
        return container
    }

    /** 段落：文本 + 行内公式 span，共用一个 TextView 以保持折行与基线对齐。 */
    private suspend fun buildParagraph(
        context: Context,
        runs: List<Run>,
        textSizePx: Float,
    ): TextView {
        val builder = SpannableStringBuilder()
        for (run in runs) {
            when (run) {
                is Run.Text -> builder.append(run.text)
                is Run.Formula -> {
                    val span = MathFormulaCache.span(
                        context = context,
                        latex = run.latex,
                        displayMode = false,
                        color = AI_TEXT_COLOR,
                        fontSizeSp = pxToSp(context, textSizePx),
                    )
                    if (span != null) {
                        val start = builder.length
                        builder.append('\u200B')
                        builder.setSpan(span, start, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    } else {
                        // span 创建失败（极端情况）→ Unicode 回退
                        builder.append(MathTextConverter.toPlainMath(run.latex))
                    }
                }
            }
        }
        return TextView(context).apply {
            setTextColor(AI_TEXT_COLOR)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx)
            text = trimEdgeNewlines(builder)
        }
    }

    /**
     * 公式行：放得下就直接画；放不下就套一层固定宽度的横向滑动窗口
     * （不在窗口上加任何"可滑动"提示，按需求约定）。
     */
    private fun buildFormulaRow(
        context: Context,
        row: Row.BlockFormula,
        availableWidthPx: Int,
        formulaSizePx: Float,
    ): View {
        val formula = MathFormulaView(context).apply {
            bind(
                latex = row.latex,
                displayMode = true,
                colorArgb = AI_TEXT_COLOR,
                fontSizePx = formulaSizePx,
            )
        }
        if (row.fits) return formula

        return HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            isFillViewport = false
            layoutParams = LinearLayout.LayoutParams(
                availableWidthPx,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            addView(
                formula,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
    }

    /** 去掉段落首尾多余的换行（块级公式前后的 `\n` 已经由行间距体现）。 */
    private fun trimEdgeNewlines(src: SpannableStringBuilder): CharSequence {
        var start = 0
        var end = src.length
        while (start < end && src[start] == '\n') start++
        while (end > start && src[end - 1] == '\n') end--
        if (start == 0 && end == src.length) return src
        if (start >= end) return ""
        return src.subSequence(start, end)
    }

    private fun spToPx(context: Context, sp: Float): Float =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            sp,
            context.resources.displayMetrics,
        )

    private fun pxToSp(context: Context, px: Float): Float =
        px / context.resources.displayMetrics.scaledDensity
}
