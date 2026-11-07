package com.semi.watchai.ui.chat

import android.content.Context
import android.util.LruCache
import io.ratex.DisplayList
import io.ratex.RaTeXEngine
import io.ratex.RaTeXFontLoader
import io.ratex.RaTeXRenderer
import io.ratex.RaTeXSpan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 公式解析结果缓存。
 *
 * 为什么需要：`RaTeXEngine.parse` 走 JNI + Rust（每次几十微秒到毫秒级），而 RecyclerView
 * 滚动时 `onBindViewHolder` 会反复执行。若不缓存，同一公式会在每次绑定/测量时重复解析。
 *
 * 三层缓存：
 * - [DisplayList]：解析结果（纯数据），可复用于任意字号；
 * - 宽度测量：每次用 `RaTeXRenderer` 现算（cheap，`width * fontSize` 只是乘法）；
 * - [RaTeXSpan]：行内公式用的 span（内含位图），按字号 + 颜色键复用。
 */
object MathFormulaCache {

    private const val MAX_DISPLAY_LISTS = 64
    private const val MAX_SPANS = 32

    private val displayLists = object : LruCache<String, DisplayList>(MAX_DISPLAY_LISTS) {}
    private val spans = object : LruCache<String, RaTeXSpan>(MAX_SPANS) {}

    private fun key(latex: String, displayMode: Boolean, color: Int) = "$displayMode|$color|$latex"

    /** 只查缓存（主线程安全）。 */
    fun cachedDisplayList(latex: String, displayMode: Boolean, color: Int): DisplayList? =
        displayLists.get(key(latex, displayMode, color))

    /**
     * 解析并缓存，失败（语法错误 / 原生库缺失）返回 null —— 调用方据此回退到 Unicode 文本。
     * **必须在后台线程调用**（内部走 `parseBlocking`）。
     */
    fun displayListBlocking(
        context: Context,
        latex: String,
        displayMode: Boolean,
        color: Int,
    ): DisplayList? {
        val k = key(latex, displayMode, color)
        displayLists.get(k)?.let { return it }
        return try {
            RaTeXFontLoader.ensureLoaded(context.applicationContext)
            RaTeXEngine.parseBlocking(latex, displayMode, color).also { displayLists.put(k, it) }
        } catch (t: Throwable) {
            // RaTeXException（语法错误）或 UnsatisfiedLinkError（ABI 不支持）
            null
        }
    }

    /** 异步版 [displayListBlocking]，供 View 在冷缓存时补渲染。 */
    suspend fun displayList(
        context: Context,
        latex: String,
        displayMode: Boolean,
        color: Int,
    ): DisplayList? = withContext(Dispatchers.Default) {
        displayListBlocking(context, latex, displayMode, color)
    }

    /**
     * 公式自然宽度（px）；解析失败返回 null。
     * **必须在后台线程调用**（由 [com.semi.watchai.util.MathContentPlanner] 的测量回调使用）。
     */
    fun widthPxBlocking(
        context: Context,
        latex: String,
        displayMode: Boolean,
        color: Int,
        fontSizePx: Float,
    ): Float? {
        val dl = displayListBlocking(context, latex, displayMode, color) ?: return null
        return RaTeXRenderer(dl, fontSizePx) { RaTeXFontLoader.getTypeface(it) }.widthPx
    }

    /**
     * 行内公式 span（基线对齐、自动撑高行高）；创建失败返回 null。
     *
     * @param fontSizeSp 字号（RaTeXSpan 内部会乘 density，语义与 sp 一致）
     */
    suspend fun span(
        context: Context,
        latex: String,
        displayMode: Boolean,
        color: Int,
        fontSizeSp: Float,
    ): RaTeXSpan? {
        val k = "$displayMode|$color|$fontSizeSp|$latex"
        spans.get(k)?.let { return it }
        return try {
            RaTeXSpan.create(context.applicationContext, latex, fontSizeSp, color, displayMode)
                .also { spans.put(k, it) }
        } catch (t: Throwable) {
            null
        }
    }
}
