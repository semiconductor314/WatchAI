package com.semi.watchai.ui.chat

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.util.AttributeSet
import android.view.View
import io.ratex.DisplayList
import io.ratex.RaTeXFontLoader
import io.ratex.RaTeXRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.ceil

/**
 * 直接用 Canvas 绘制一条 LaTeX 公式的 View（RaTeX 的 display list → 原生绘制，无 WebView、无位图）。
 *
 * 与 `io.ratex.RaTeXView` 的区别：解析结果走 [MathFormulaCache]，避免 RecyclerView 重复绑定时反复
 * native parse；并且**必须**按自然宽度测量 —— 这是"放不下时套 HorizontalScrollView 横滑"的前提。
 *
 * 测量语义：宽度 = 公式自然宽度（不会压缩到父布局宽度），因此
 * - 放在 wrap_content 容器里：正常显示；
 * - 放在 HorizontalScrollView 里：HorizontalScrollView 以 UNSPECIFIED 下发宽度，
 *   `resolveSize` 返回自然宽度 → 超出部分可横向拖动查看。
 */
class MathFormulaView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private var renderer: RaTeXRenderer? = null
    private var latex: String = ""
    private var displayMode: Boolean = true
    private var formulaColor: Int = Color.WHITE
    private var fontSizePx: Float = 0f
    private var loadJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    /**
     * 绑定一条公式。命中缓存时同步完成测量与绘制（滚动场景的主路径），
     * 未命中则先显示空白、后台解析完成后自动重绘。
     */
    fun bind(latex: String, displayMode: Boolean, colorArgb: Int, fontSizePx: Float) {
        this.latex = latex
        this.displayMode = displayMode
        this.formulaColor = colorArgb
        this.fontSizePx = fontSizePx
        loadJob?.cancel()
        renderer = null

        val cached = MathFormulaCache.cachedDisplayList(latex, displayMode, colorArgb)
        if (cached != null) {
            applyDisplayList(cached)
            return
        }
        requestLayout()
        invalidate()
        loadJob = scope.launch {
            val parsed = MathFormulaCache.displayList(context, latex, displayMode, colorArgb)
            if (parsed != null && this@MathFormulaView.latex == latex) applyDisplayList(parsed)
        }
    }

    private fun applyDisplayList(dl: DisplayList) {
        renderer = RaTeXRenderer(dl, fontSizePx) { RaTeXFontLoader.getTypeface(it) }
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val r = renderer
        if (r == null) {
            setMeasuredDimension(paddingLeft + paddingRight, paddingTop + paddingBottom)
            return
        }
        val desiredWidth = ceil(r.widthPx).toInt() + paddingLeft + paddingRight
        val desiredHeight = ceil(r.totalHeightPx).toInt() + paddingTop + paddingBottom
        setMeasuredDimension(
            resolveSize(desiredWidth, widthMeasureSpec),
            resolveSize(desiredHeight, heightMeasureSpec),
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val r = renderer ?: return
        canvas.save()
        canvas.translate(paddingLeft.toFloat(), paddingTop.toFloat())
        r.draw(canvas)
        canvas.restore()
    }

    override fun onDetachedFromWindow() {
        loadJob?.cancel()
        loadJob = null
        super.onDetachedFromWindow()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // 曾经 detach 过（被回收）且公式还没解析出来时补偿一次
        if (renderer == null && latex.isNotEmpty() && loadJob?.isActive != true) {
            bind(latex, displayMode, formulaColor, fontSizePx)
        }
    }
}
