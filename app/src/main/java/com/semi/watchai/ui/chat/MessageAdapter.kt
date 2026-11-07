package com.semi.watchai.ui.chat

import android.content.Context
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.semi.watchai.R
import com.semi.watchai.data.model.Message
import com.semi.watchai.util.MathTextConverter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 消息列表适配器。
 *
 * - 用户消息（[UserHolder]）：纯文本，经 [MathTextConverter] 把 LaTeX 降级为 Unicode。
 * - AI 消息（[AiHolder]）：内容由 [MathMessageContent] 动态构建 ——
 *   行内公式（放得下）走 span，放不下的公式独占一行并支持横向拖动查看。
 * - "正在思考…" 占位：由 [showThinking] 在列表尾部追加一个**与 AI 气泡同款**的项，
 *   不写入数据库；AI 回复到达后由 [replaceThinkingWith] 原地替换，失败时 [hideThinking] 移除。
 */
class MessageAdapter(
    private val messages: MutableList<Message>,
    private val scope: CoroutineScope,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    /** 列表尾部是否正在显示"正在思考…"占位气泡。 */
    private var thinking = false

    /** 用户消息：主布局不变，单 TextView。 */
    private class UserHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvMessage: TextView = itemView.findViewById(R.id.tvMessage)
    }

    /** AI 消息：内容容器；[token] 用于丢弃过期的异步构建结果。 */
    private class AiHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val llContent: LinearLayout = itemView.findViewById(R.id.llContent)
        var job: Job? = null
        var token: Any? = null
    }

    override fun getItemCount(): Int = messages.size + if (thinking) 1 else 0

    /** 占位项（索引 = messages.size）按 AI 气泡类型渲染。 */
    override fun getItemViewType(position: Int): Int =
        if (position < messages.size && messages[position].isUser) TYPE_USER else TYPE_AI

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_USER) {
            UserHolder(inflater.inflate(R.layout.item_message_user, parent, false))
        } else {
            AiHolder(inflater.inflate(R.layout.item_message_ai, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val raw = if (position < messages.size) messages[position].content else THINKING_TEXT
        when (holder) {
            is UserHolder -> holder.tvMessage.text = MathTextConverter.toDisplay(raw)
            is AiHolder -> bindAiContent(holder, raw)
        }
    }

    /**
     * AI 内容需要异步测量（RaTeX 解析在后台），因此先清空、再在结果回来时填充；
     * 用 [AiHolder.token] 防止快速滚动/复用导致的错位。
     */
    private fun bindAiContent(holder: AiHolder, raw: String) {
        holder.job?.cancel()
        holder.llContent.removeAllViews()

        val token = Any()
        holder.token = token
        val context = holder.itemView.context
        val availableWidthPx = availableContentWidthPx(context)

        holder.job = scope.launch {
            val content = MathMessageContent.build(context, raw, availableWidthPx)
            if (holder.token !== token) return@launch
            holder.llContent.removeAllViews()
            holder.llContent.addView(content)
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        if (holder is AiHolder) {
            holder.job?.cancel()
            holder.job = null
            holder.token = null
            holder.llContent.removeAllViews()
        }
        super.onViewRecycled(holder)
    }

    fun addMessage(message: Message) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    fun updateMessages(newMessages: List<Message>) {
        messages.clear()
        messages.addAll(newMessages)
        notifyDataSetChanged()
    }

    /** 在列表尾部追加"正在思考…"气泡（幂等）。 */
    fun showThinking() {
        if (thinking) return
        thinking = true
        notifyItemInserted(messages.size)
    }

    /** 移除"正在思考…"气泡（请求失败或已替换时调用，幂等）。 */
    fun hideThinking() {
        if (!thinking) return
        thinking = false
        notifyItemRemoved(messages.size)
    }

    /**
     * AI 回复到达：把"正在思考…"气泡**原地替换**成真实回复，
     * 索引不变，因此不会有插入/移除动画造成的闪烁。
     */
    fun replaceThinkingWith(message: Message) {
        if (!thinking) {
            addMessage(message)
            return
        }
        thinking = false
        messages.add(message)
        notifyItemChanged(messages.size - 1)
    }

    /**
     * 气泡内可用宽度：屏幕宽 − 列表项左右 padding(12dp×2) − 气泡内 padding(12dp×2)，
     * 且不超过气泡的 maxWidth(280dp)。
     */
    private fun availableContentWidthPx(context: Context): Int {
        val dm = context.resources.displayMetrics
        val itemPadding = dp(context, 24f)
        val bubblePadding = dp(context, 24f)
        val bubbleMax = dp(context, 280f)
        val bubble = minOf(dm.widthPixels - itemPadding, bubbleMax)
        return maxOf(dp(context, 40f), bubble - bubblePadding)
    }

    private fun dp(context: Context, value: Float): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value,
        context.resources.displayMetrics,
    ).toInt()

    private companion object {
        const val TYPE_USER = 0
        const val TYPE_AI = 1
        const val THINKING_TEXT = "正在思考…"
    }
}
