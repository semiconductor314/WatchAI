package com.semi.watchai

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class MessageAdapter(
    private val messages: MutableList<Message>
) : RecyclerView.Adapter<MessageAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvMessage: TextView = itemView.findViewById(R.id.tvMessage)
        val tvTime: TextView = itemView.findViewById(R.id.tvTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layout = if (viewType == 0) {
            R.layout.item_message_user
        } else {
            R.layout.item_message_ai
        }
        val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val message = messages[position]
        holder.tvMessage.text = message.content
        holder.tvTime.text = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(message.timestamp))
    }

    override fun getItemCount(): Int = messages.size

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].isUser) 0 else 1
    }

    fun addMessage(message: Message) {
        messages.add(message)
        val position = messages.size - 1
        notifyItemInserted(position)
    }

    fun updateMessages(newMessages: List<Message>) {
        messages.clear()
        messages.addAll(newMessages)
        notifyDataSetChanged()
    }

    // 原位更新某条消息内容（用于把"正在思考…"占位替换为真实回复）
    fun updateMessage(id: String, newContent: String) {
        val index = messages.indexOfFirst { it.id == id }
        if (index == -1) return
        messages[index] = messages[index].copy(content = newContent)
        notifyItemChanged(index)
    }

    // 移除某条消息（用于请求失败时移除占位）
    fun removeMessage(id: String) {
        val index = messages.indexOfFirst { it.id == id }
        if (index == -1) return
        messages.removeAt(index)
        notifyItemRemoved(index)
    }
}