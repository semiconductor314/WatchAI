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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 消息列表适配器。
 *
 * - 用户消息（[UserHolder]）：纯文本，经 [MathTextConverter] 把 LaTeX 降级为 Unicode。
 * - AI 消息（[AiHolder]）：内容由 [MathMessageContent] 动态构建 ——
 *   行内公式（放得下）走 span，放不下的公式独占一行并支持横向拖动查看。
 */
class MessageAdapter(
    private val messages: MutableList<Message>,
    private val scope: CoroutineScope,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    /** 用户消息：布局不变，单 TextView。 */
    private class UserHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvMessage: TextView = itemView.findViewById(R.id.tvMessage)
        val tvTime: TextView = itemView.findViewById(R.id.tvTime)
    }

    /** AI 消息：内容容器 + 时间；[token] 用于丢弃过期的异步构建结果。 */
    private class AiHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val llContent: LinearLayout = itemView.findViewById(R.id.llContent)
        val tvTime: TextView = itemView.findViewById(R.id.tvTime)
        var job: Job? = null
        var token: Any? = null
    }

    override fun getItemCount(): Int = messages.size

    override fun getItemViewType(position: Int): Int = if (messages[position].isUser) TYPE_USER else TYPE_AI

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_USER) {
            UserHolder(inflater.inflate(R.layout.item_message_user, parent, false))
        } else {
            AiHolder(inflater.inflate(R.layout.item_message_ai, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        when (holder) {
            is UserHolder -> {
                holder.tvMessage.text = MathTextConverter.toDisplay(message.content)
                holder.tvTime.text = timeFormat.format(Date(message.timestamp))
            }

            is AiHolder -> {
                holder.tvTime.text = timeFormat.format(Date(message.timestamp))
                bindAiContent(holder, message)
            }
        }
    }

    /**
     * AI 内容需要异步测量（RaTeX 解析在后台），因此先清空、再在结果回来时填充；
     * 用 [AiHolder.token] 防止快速滚动/复用导致的错位。
     */
    private fun bindAiContent(holder: AiHolder, message: Message) {
        holder.job?.cancel()
        holder.llContent.removeAllViews()

        val token = Any()
        holder.token = token
        val context = holder.itemView.context
        val availableWidthPx = availableContentWidthPx(context)
        val raw = message.content

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
    }
}
