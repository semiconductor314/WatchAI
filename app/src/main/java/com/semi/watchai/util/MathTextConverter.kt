package com.semi.watchai.util

/**
 * LaTeX 数学表达式 → 可读 Unicode 纯文本的转换器（纯函数，便于单元测试）。
 *
 * 设计要点：
 * - **不追求二维排版**：`\frac{a}{b}` 降级为 `a/b`、`\sqrt{x}` 降级为 `√(x)`，
 *   够读即可；真正的二维排版见后续可选的 RaTeX 阶段。
 * - **保护区**：``` 围栏代码块与 ` 行内代码内的内容原样保留，不做任何转换。
 * - **抗误判**：`$…$` 只有内容含 `\`、`^`、`_`、`=` 之一才当作数学，
 *   因此"价格 $5 和 $10"不会被吃。
 * - **流式友好**：`streaming = true` 时，尾部**未闭合**的数学片段整段不输出
 *   （而不是原样输出），否则用户会看到闪出的半截 `\frac{`。
 * - **无损降级**：非流式模式下遇到未闭合片段，整段原样返回，绝不丢内容。
 */
object MathTextConverter {

    /** 一段消息切分后的片段类型（供 UI 决定用行内 span 还是独立可横滑窗口）。 */
    sealed interface Segment {
        /** 该片段在原文中的原始文本（含定界符 / 围栏标记）。 */
        val raw: String

        /** 普通文本（含受保护的代码块）。 */
        data class Text(override val raw: String) : Segment

        /** 行内公式（`$…$` / `\(…\)`），[latex] 为去掉定界符后的源码。 */
        data class Inline(override val raw: String, val latex: String) : Segment

        /** 块级公式（`$$…$$` / `\[…\]` / ```latex 围栏），[latex] 为去掉定界符与围栏后的源码。 */
        data class Block(override val raw: String, val latex: String) : Segment
    }

    /**
     * 把原文切分为「文本 / 行内公式 / 块级公式」片段，保证不丢字符：
     * 所有片段的 [Segment.raw] 拼起来等于原文（流式模式下被丢弃的未闭合尾巴除外）。
     *
     * @param streaming true 时尾部未闭合的数学片段整段丢弃。
     */
    fun segments(raw: String, streaming: Boolean = false): List<Segment> {
        if (raw.isEmpty()) return emptyList()
        if (!raw.contains('$') && !raw.contains("\\(") && !raw.contains("\\[") && !containsMathFence(raw)) {
            return listOf(Segment.Text(raw))
        }

        val out = ArrayList<Segment>()
        var textStart = 0
        var i = 0
        var aborted = false

        while (i < raw.length) {
            // 1) 围栏代码块：数学标签围栏算块级公式，其余原样归入文本
            if (raw.startsWith("```", i)) {
                val end = raw.indexOf("```", i + 3)
                if (end < 0) {
                    if (streaming) {
                        aborted = true
                        break
                    }
                    i = raw.length
                    break
                }
                val infoEnd = raw.indexOf('\n', i + 3)
                if (infoEnd in (i + 3) until end) {
                    val tag = raw.substring(i + 3, infoEnd).trim().lowercase()
                    if (tag in MATH_FENCE_TAGS) {
                        if (i > textStart) out.add(Segment.Text(raw.substring(textStart, i)))
                        out.add(
                            Segment.Block(
                                raw = raw.substring(i, end + 3),
                                latex = stripMathDelimiters(raw.substring(infoEnd + 1, end))
                            )
                        )
                        i = end + 3
                        textStart = i
                        continue
                    }
                }
                i = end + 3
                continue
            }
            // 2) 行内代码：归入文本，不扫描其内部
            if (raw[i] == '`') {
                val end = raw.indexOf('`', i + 1)
                if (end < 0) {
                    if (streaming) {
                        aborted = true
                        break
                    }
                    i = raw.length
                    break
                }
                i = end + 1
                continue
            }
            // 3) 转义的美元符：跳过，不当定界符
            if (raw[i] == '\\' && i + 1 < raw.length && raw[i + 1] == '$') {
                i += 2
                continue
            }
            // 4) 块级：$$…$$
            if (raw.startsWith("$$", i)) {
                val end = raw.indexOf("$$", i + 2)
                if (end < 0) {
                    if (streaming) {
                        aborted = true
                        break
                    }
                    i = raw.length
                    break
                }
                if (i > textStart) out.add(Segment.Text(raw.substring(textStart, i)))
                out.add(Segment.Block(raw.substring(i, end + 2), raw.substring(i + 2, end)))
                i = end + 2
                textStart = i
                continue
            }
            // 5) 块级：\[…\]
            if (raw.startsWith("\\[", i)) {
                val end = raw.indexOf("\\]", i + 2)
                if (end < 0) {
                    if (streaming) {
                        aborted = true
                        break
                    }
                    i = raw.length
                    break
                }
                if (i > textStart) out.add(Segment.Text(raw.substring(textStart, i)))
                out.add(Segment.Block(raw.substring(i, end + 2), raw.substring(i + 2, end)))
                i = end + 2
                textStart = i
                continue
            }
            // 6) 行内：\(…\)
            if (raw.startsWith("\\(", i)) {
                val end = raw.indexOf("\\)", i + 2)
                if (end < 0) {
                    if (streaming) {
                        aborted = true
                        break
                    }
                    i = raw.length
                    break
                }
                if (i > textStart) out.add(Segment.Text(raw.substring(textStart, i)))
                out.add(Segment.Inline(raw.substring(i, end + 2), raw.substring(i + 2, end)))
                i = end + 2
                textStart = i
                continue
            }
            // 7) 行内：$…$（需通过"像数学"判定，避免吃掉金额）
            if (raw[i] == '$') {
                val end = findInlineDollarEnd(raw, i + 1)
                if (end < 0) {
                    if (streaming) {
                        aborted = true
                        break
                    }
                    i = raw.length
                    break
                }
                val inner = raw.substring(i + 1, end)
                if (looksLikeMath(inner)) {
                    if (i > textStart) out.add(Segment.Text(raw.substring(textStart, i)))
                    out.add(Segment.Inline(raw.substring(i, end + 1), inner))
                    i = end + 1
                    textStart = i
                    continue
                }
                i = end + 1
                continue
            }
            i++
        }

        if (aborted) {
            if (i > textStart) out.add(Segment.Text(raw.substring(textStart, i)))
            return out
        }
        if (raw.length > textStart) out.add(Segment.Text(raw.substring(textStart)))
        return out
    }

    /**
     * 把模型返回的原始文本转换为可直接放进 TextView 的展示文本。
     *
     * @param raw       模型返回的原始文本（可能包含 LaTeX）。
     * @param streaming true 表示流式揭示中：尾部未闭合的数学片段整段暂不输出。
     */
    fun toDisplay(raw: String, streaming: Boolean = false): String {
        if (raw.isEmpty()) return raw
        val list = segments(raw, streaming)
        if (list.isEmpty()) return ""

        val out = StringBuilder(raw.length)
        for ((index, seg) in list.withIndex()) {
            when (seg) {
                is Segment.Text -> out.append(seg.raw)
                is Segment.Inline -> out.append(toPlainMath(seg.latex))
                is Segment.Block -> {
                    val text = toPlainMath(seg.latex).lines()
                        .joinToString("\n") { it.trimEnd() }
                        .trim()
                    if (text.isEmpty()) continue
                    // 块级公式单独成行：只在必要时补前导/尾随换行，并抹掉悬空空格
                    if (out.isNotEmpty() && out.last() != '\n') {
                        while (out.isNotEmpty() && out.last() == ' ') out.deleteCharAt(out.length - 1)
                        out.append('\n')
                    }
                    out.append(text)
                    val next = list.getOrNull(index + 1)?.raw?.firstOrNull()
                    if (next != null && next != '\n') out.append('\n')
                }
            }
        }
        return out.toString()
    }

    /** 单个 LaTeX 公式 → Unicode 纯文本（供 UI 层在 RaTeX 解析失败时做回退）。 */
    fun toPlainMath(latex: String): String = convertMath(latex).trim()

    // ------------------------------------------------------------------
    // 外层切分辅助
    // ------------------------------------------------------------------


    /** 找 `$…$` 的闭合位置；遇到 `$$` 视为块级开头，放弃行内判定。 */
    private fun findInlineDollarEnd(raw: String, from: Int): Int {
        var j = from
        while (j < raw.length) {
            val c = raw[j]
            if (c == '\\') {
                j += 2
                continue
            }
            if (c == '$') {
                if (j + 1 < raw.length && raw[j + 1] == '$') return -1
                return j
            }
            j++
        }
        return -1
    }

    /** 抗误判：内容里带 LaTeX 特征才当数学。 */
    private fun looksLikeMath(inner: String): Boolean =
        inner.any { it == '\\' || it == '^' || it == '_' || it == '=' }

    /** 数学围栏的语言标签（模型常用 ```latex / ```tex / ```math 包公式）。 */
    private val MATH_FENCE_TAGS = setOf("latex", "tex", "math", "katex", "mathjax", "asciimath")

    /**
     * 快速路径判定辅助：文本里是否存在数学围栏。
     * 纯 ```latex 围栏里没有 `$` / `\(` / `\[`，所以不能只看这三个字符。
     */
    private fun containsMathFence(raw: String): Boolean {
        if (!raw.contains("```")) return false
        return MATH_FENCE_TAGS.any { raw.contains("```" + it, ignoreCase = true) }
    }

    /** 去掉围栏内容自带的数学定界符（```latex 里常写成 $$…$$ 或 $…$）。 */
    private fun stripMathDelimiters(s: String): String {
        var t = s.trim()
        for ((open, close) in listOf(
            "\$\$" to "\$\$",
            "\\[" to "\\]",
            "\$" to "\$",
            "\\(" to "\\)"
        )) {
            if (t.startsWith(open) && t.endsWith(close) && t.length > open.length + close.length) {
                t = t.substring(open.length, t.length - close.length).trim()
                break
            }
        }
        return t
    }

    // ------------------------------------------------------------------
    // 数学片段内的转换
    // ------------------------------------------------------------------

    private fun convertMath(src: String): String {
        val sb = StringBuilder(src.length)
        var i = 0
        while (i < src.length) {
            when (val c = src[i]) {
                '\\' -> {
                    if (i + 1 >= src.length) {
                        i++
                    } else if (isAsciiLetter(src[i + 1])) {
                        var j = i + 1
                        while (j < src.length && isAsciiLetter(src[j])) j++
                        i = handleCommand(src.substring(i + 1, j), src, j, sb)
                    } else {
                        i = handleEscapedChar(src, i, sb)
                    }
                }

                '^', '_' -> {
                    val arg = readArg(src, i + 1)
                    sb.append(toScript(convertMath(arg.text), superscript = c == '^'))
                    i = arg.next
                }

                '{' -> {
                    val arg = readGroup(src, i)
                    sb.append(convertMath(arg.text))
                    i = arg.next
                }

                '}' -> i++
                '&' -> i++          // 对齐符，直接丢弃
                '~' -> {
                    sb.append(' ')
                    i++
                }

                else -> {
                    sb.append(c)
                    i++
                }
            }
        }
        return sb.toString()
    }

    /** 处理 `\` + 非字母（间距、换行、转义字符）。 */
    private fun handleEscapedChar(src: String, i: Int, sb: StringBuilder): Int {
        val n = src[i + 1]
        when (n) {
            '\\' -> sb.append('\n')              // 换行
            ',', ';', ':', ' ' -> sb.append(' ') // 各类间距
            '!' -> Unit                          // 负间距
            else -> sb.append(n)                 // \$ \% \& \# \_ \{ \} 等
        }
        return i + 2
    }

    /**
     * 处理 `\命令`。返回新的扫描下标。
     *
     * @param name 命令名（不含反斜杠）
     * @param i    命令名之后的下标
     */
    private fun handleCommand(name: String, src: String, i: Int, sb: StringBuilder): Int {
        when (name) {
            // 分式 / 二项式
            "frac", "dfrac", "tfrac", "cfrac" -> {
                val a = readArg(src, i)
                val b = readArg(src, a.next)
                sb.append(wrapOperand(convertMath(a.text)))
                    .append('/')
                    .append(wrapOperand(convertMath(b.text)))
                return b.next
            }

            "binom", "dbinom", "tbinom" -> {
                val a = readArg(src, i)
                val b = readArg(src, a.next)
                sb.append("C(").append(convertMath(a.text)).append(',').append(convertMath(b.text)).append(')')
                return b.next
            }

            // 根式：支持 \sqrt[n]{x}
            "sqrt" -> {
                var idx = i
                var index: String? = null
                if (idx < src.length && src[idx] == '[') {
                    val close = src.indexOf(']', idx + 1)
                    if (close > 0) {
                        index = src.substring(idx + 1, close)
                        idx = close + 1
                    }
                }
                val arg = readArg(src, idx)
                sb.append(rootSymbol(index)).append('(').append(convertMath(arg.text)).append(')')
                return arg.next
            }

            // 字体/文本类命令：内容原样保留（中文与 \text 内的单位不做数学转换）
            "text", "textrm", "textnormal", "mbox", "mathrm", "mathbf", "mathit",
            "mathsf", "mathtt", "operatorname", "mathcal", "mathbb", "boldsymbol" -> {
                val arg = readArg(src, i)
                sb.append(arg.text)
                return arg.next
            }

            // 装饰（组合字符）
            "overline" -> return applyDecoration(src, i, sb, '\u0305')
            "bar" -> return applyDecoration(src, i, sb, '\u0304')
            "hat", "widehat" -> return applyDecoration(src, i, sb, '\u0302')
            "tilde", "widetilde" -> return applyDecoration(src, i, sb, '\u0303')
            "vec" -> return applyDecoration(src, i, sb, '\u20D7')
            "dot" -> return applyDecoration(src, i, sb, '\u0307')

            // 定界符命令：只去命令本身，定界符字符留给主循环
            "left", "right", "middle", "big", "Big", "bigg", "Bigg",
            "bigl", "bigr", "Bigl", "Bigr", "biggl", "biggr" -> {
                var idx = i
                // \left. / \right. 的不可见定界符
                if (idx < src.length && src[idx] == '.') idx++
                return idx
            }

            // 环境：去掉 \begin{…} / \end{…}
            "begin", "end" -> {
                val arg = readArg(src, i)
                return arg.next
            }

            // 纯排版命令：丢弃
            "displaystyle", "textstyle", "scriptstyle", "scriptscriptstyle",
            "limits", "nolimits", "nonumber", "notag", "nobreak", "hline",
            "phantom", "hphantom", "vphantom", "mathstrut", "strut", "centering" -> return i

            "quad", "qquad", "enspace", "thinspace" -> {
                sb.append(' ')
                return i
            }
        }

        val symbol = SYMBOLS[name]
        if (symbol != null) {
            sb.append(symbol)
            return i
        }
        // 兜底：未知命令去掉反斜杠只留名字（\sin → sin），避免裸反斜杠
        sb.append(name)
        return i
    }

    private fun applyDecoration(src: String, i: Int, sb: StringBuilder, mark: Char): Int {
        val arg = readArg(src, i)
        val inner = convertMath(arg.text)
        for (ch in inner) {
            sb.append(ch)
            if (ch != ' ') sb.append(mark)
        }
        return arg.next
    }

    /** `\frac` 的操作数含运算符时加括号，避免 `a+b/c` 这类歧义。 */
    private fun wrapOperand(s: String): String {
        val needParen = s.any { it == '+' || it == '-' || it == ' ' || it == '/' }
        return if (needParen && s.isNotEmpty()) "($s)" else s
    }

    /** `\sqrt[3]` → ∛、`\sqrt[4]` → ∜，其余用上标数字 + √。 */
    private fun rootSymbol(index: String?): String {
        if (index == null) return "√"
        return when (val t = convertMath(index).trim()) {
            "3" -> "∛"
            "4" -> "∜"
            else -> toScript(t, superscript = true) + "√"
        }
    }

    // ------------------------------------------------------------------
    // 上下标映射
    // ------------------------------------------------------------------

    /** 上/下标：能全部映射就用 Unicode 字符，否则退回 `^(…)` / `_(…)`。 */
    private fun toScript(text: String, superscript: Boolean): String {
        if (text.isEmpty()) return ""
        val table = if (superscript) SUPERSCRIPTS else SUBSCRIPTS
        val sb = StringBuilder(text.length)
        for (ch in text) {
            val mapped = table[ch] ?: return if (superscript) "^($text)" else "_($text)"
            sb.append(mapped)
        }
        return sb.toString()
    }

    // ------------------------------------------------------------------
    // 参数读取
    // ------------------------------------------------------------------

    private data class Arg(val text: String, val next: Int)

    /** 读取 `{…}` 分组（跳过前置空白）。 */
    private fun readGroup(src: String, i: Int): Arg {
        var j = i
        while (j < src.length && src[j] == ' ') j++
        if (j >= src.length || src[j] != '{') return Arg("", j)
        var depth = 0
        var k = j
        while (k < src.length) {
            when (src[k]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return Arg(src.substring(j + 1, k), k + 1)
                }
            }
            k++
        }
        return Arg(src.substring(j + 1), src.length)
    }

    /**
     * 读取一个参数：`{…}` 分组，或单个字符（`x^2` 这种写法）。
     * 若下一个是 `\命令`，把整条命令当作参数原文。
     */
    private fun readArg(src: String, i: Int): Arg {
        var j = i
        while (j < src.length && src[j] == ' ') j++
        if (j >= src.length) return Arg("", j)
        if (src[j] == '{') return readGroup(src, j)
        if (src[j] == '\\') {
            var k = j + 1
            while (k < src.length && isAsciiLetter(src[k])) k++
            if (k == j + 1) k = minOf(j + 2, src.length)
            return Arg(src.substring(j, k), k)
        }
        return Arg(src[j].toString(), j + 1)
    }

    private fun isAsciiLetter(c: Char): Boolean = c in 'a'..'z' || c in 'A'..'Z'

    // ------------------------------------------------------------------
    // 符号表
    // ------------------------------------------------------------------

    private val SYMBOLS: Map<String, String> = buildMap {
        // 希腊字母
        put("alpha", "α"); put("beta", "β"); put("gamma", "γ"); put("delta", "δ")
        put("epsilon", "ε"); put("varepsilon", "ε"); put("zeta", "ζ"); put("eta", "η")
        put("theta", "θ"); put("vartheta", "ϑ"); put("iota", "ι"); put("kappa", "κ")
        put("lambda", "λ"); put("mu", "μ"); put("nu", "ν"); put("xi", "ξ")
        put("pi", "π"); put("varpi", "ϖ"); put("rho", "ρ"); put("varrho", "ϱ")
        put("sigma", "σ"); put("varsigma", "ς"); put("tau", "τ"); put("upsilon", "υ")
        put("phi", "φ"); put("varphi", "ϕ"); put("chi", "χ"); put("psi", "ψ")
        put("omega", "ω")
        put("Gamma", "Γ"); put("Delta", "Δ"); put("Theta", "Θ"); put("Lambda", "Λ")
        put("Xi", "Ξ"); put("Pi", "Π"); put("Sigma", "Σ"); put("Upsilon", "Υ")
        put("Phi", "Φ"); put("Psi", "Ψ"); put("Omega", "Ω")

        // 运算与关系
        put("times", "×"); put("cdot", "·"); put("div", "÷"); put("pm", "±"); put("mp", "∓")
        put("le", "≤"); put("leq", "≤"); put("ge", "≥"); put("geq", "≥")
        put("ne", "≠"); put("neq", "≠"); put("approx", "≈"); put("equiv", "≡")
        put("sim", "∼"); put("simeq", "≃"); put("propto", "∝"); put("ll", "≪"); put("gg", "≫")
        put("to", "→"); put("rightarrow", "→"); put("leftarrow", "←")
        put("Rightarrow", "⇒"); put("Leftarrow", "⇐"); put("leftrightarrow", "↔")
        put("Leftrightarrow", "⇔"); put("implies", "⇒"); put("iff", "⇔")
        put("in", "∈"); put("notin", "∉"); put("ni", "∋")
        put("subset", "⊂"); put("subseteq", "⊆"); put("supset", "⊃"); put("supseteq", "⊇")
        put("cup", "∪"); put("cap", "∩"); put("setminus", "∖")
        put("emptyset", "∅"); put("varnothing", "∅")
        put("forall", "∀"); put("exists", "∃"); put("nexists", "∄")
        put("neg", "¬"); put("land", "∧"); put("lor", "∨"); put("wedge", "∧"); put("vee", "∨")

        // 大型运算符与微积分
        put("sum", "∑"); put("prod", "∏"); put("coprod", "∐")
        put("int", "∫"); put("iint", "∬"); put("iiint", "∭"); put("oint", "∮")
        put("partial", "∂"); put("nabla", "∇"); put("infty", "∞")
        put("lim", "lim"); put("sup", "sup"); put("inf", "inf")
        put("prime", "′"); put("hbar", "ℏ"); put("ell", "ℓ")

        // 其他常用符号
        put("ldots", "…"); put("dots", "…"); put("cdots", "⋯"); put("vdots", "⋮")
        put("deg", "°"); put("angle", "∠"); put("perp", "⊥"); put("parallel", "∥")
        put("therefore", "∴"); put("because", "∵")
        put("star", "⋆"); put("ast", "∗"); put("circ", "∘"); put("bullet", "•")
        put("aleph", "ℵ"); put("Re", "ℜ"); put("Im", "ℑ")
        put("square", "□"); put("triangle", "△"); put("checkmark", "✓")
        put("lbrace", "{"); put("rbrace", "}"); put("langle", "⟨"); put("rangle", "⟩")
        put("lceil", "⌈"); put("rceil", "⌉"); put("lfloor", "⌊"); put("rfloor", "⌋")
    }

    private val SUPERSCRIPTS: Map<Char, Char> = mapOf(
        '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
        '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
        '+' to '⁺', '-' to '⁻', '=' to '⁼', '(' to '⁽', ')' to '⁾',
        'a' to 'ᵃ', 'b' to 'ᵇ', 'c' to 'ᶜ', 'd' to 'ᵈ', 'e' to 'ᵉ',
        'f' to 'ᶠ', 'g' to 'ᵍ', 'h' to 'ʰ', 'i' to 'ⁱ', 'j' to 'ʲ',
        'k' to 'ᵏ', 'l' to 'ˡ', 'm' to 'ᵐ', 'n' to 'ⁿ', 'o' to 'ᵒ',
        'p' to 'ᵖ', 'r' to 'ʳ', 's' to 'ˢ', 't' to 'ᵗ', 'u' to 'ᵘ',
        'v' to 'ᵛ', 'w' to 'ʷ', 'x' to 'ˣ', 'y' to 'ʸ', 'z' to 'ᶻ',
        'A' to 'ᴬ', 'B' to 'ᴮ', 'D' to 'ᴰ', 'E' to 'ᴱ', 'K' to 'ᴷ',
        'M' to 'ᴹ', 'N' to 'ᴺ', 'O' to 'ᴼ', 'P' to 'ᴾ', 'R' to 'ᴿ',
        'T' to 'ᵀ', 'U' to 'ᵁ', 'V' to 'ⱽ', 'W' to 'ᵂ'
    )

    private val SUBSCRIPTS: Map<Char, Char> = mapOf(
        '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄',
        '5' to '₅', '6' to '₆', '7' to '₇', '8' to '₈', '9' to '₉',
        '+' to '₊', '-' to '₋', '=' to '₌', '(' to '₍', ')' to '₎',
        'a' to 'ₐ', 'e' to 'ₑ', 'h' to 'ₕ', 'i' to 'ᵢ', 'j' to 'ⱼ',
        'k' to 'ₖ', 'l' to 'ₗ', 'm' to 'ₘ', 'n' to 'ₙ', 'o' to 'ₒ',
        'p' to 'ₚ', 'r' to 'ᵣ', 's' to 'ₛ', 't' to 'ₜ', 'u' to 'ᵤ',
        'v' to 'ᵥ', 'x' to 'ₓ'
    )
}
