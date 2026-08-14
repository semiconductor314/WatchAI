package com.semi.watchai

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import android.content.res.Resources

class ChatListAdapter(
    private val dataList: MutableList<Chat>,
    private val onItemClick: (Chat) -> Unit,
    private val onDeleteItem: (Int) -> Unit,
    private val onRenameItem: (Int, String) -> Unit
) : RecyclerView.Adapter<ChatListAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvItemText: TextView = itemView.findViewById(R.id.tvItemText)
        val btnAction: Button = itemView.findViewById(R.id.btnAction)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val chat = dataList[position]
        val context = holder.itemView.context

        // 显示聊天标题
        holder.tvItemText.text = chat.title

        // 点击整个条目跳转到聊天界面
        holder.itemView.setOnClickListener {
            onItemClick(chat)
        }

        // 操作按钮：弹出 PopupWindow（重命名/删除）
        holder.btnAction.setOnClickListener { view ->
            showPopupWindow(context, view, chat, position)
        }
    }

    private fun showPopupWindow(context: Context, anchorView: View, chat: Chat, position: Int) {
        val screenWidth = Resources.getSystem().displayMetrics.widthPixels
        val screenHeight = Resources.getSystem().displayMetrics.heightPixels

        val popupWidth = (screenWidth * 0.30f).toInt()
        val popupHeight = (screenHeight * 0.25f).toInt().coerceAtLeast(120)

        val popupView = LayoutInflater.from(context).inflate(R.layout.popup_actions, null)
        popupView.layoutParams = LinearLayout.LayoutParams(popupWidth, popupHeight)

        val btnOption1: Button = popupView.findViewById(R.id.btnOption1)
        val btnOption2: Button = popupView.findViewById(R.id.btnOption2)

        btnOption1.text = "重命名"
        btnOption2.text = "删除"

        val btnHeight = (popupHeight * 0.42f).toInt()
        val btnWidth = (popupWidth * 0.85f).toInt()
        val btnMargin = (popupHeight * 0.04f).toInt()

        btnOption1.layoutParams = LinearLayout.LayoutParams(btnWidth, btnHeight).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setMargins(0, 0, 0, btnMargin)
        }
        btnOption1.textSize = (popupWidth * 0.08f).coerceIn(10f, 20f)

        btnOption2.layoutParams = LinearLayout.LayoutParams(btnWidth, btnHeight).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setMargins(0, 0, 0, 0)
        }
        btnOption2.textSize = (popupWidth * 0.08f).coerceIn(10f, 20f)

        val popupWindow = PopupWindow(popupView, popupWidth, popupHeight, true)
        popupWindow.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        popupWindow.animationStyle = android.R.style.Animation_Dialog

        val location = IntArray(2)
        anchorView.getLocationOnScreen(location)

        var x = location[0] - popupWidth
        var y = location[1] - popupHeight / 4

        if (y + popupHeight > screenHeight) {
            y = location[1] - popupHeight
        }
        if (x < 0) {
            x = 10
        }
        if (x + popupWidth > screenWidth) {
            x = screenWidth - popupWidth - 10
        }

        popupWindow.showAtLocation(anchorView, Gravity.NO_GRAVITY, x, y)

        // 重命名
        btnOption1.setOnClickListener {
            popupWindow.dismiss()
            showRenameDialog(context, position)
        }

        // 删除
        btnOption2.setOnClickListener {
            onDeleteItem(position)
            popupWindow.dismiss()
        }

        popupView.setOnTouchListener { _, _ ->
            popupWindow.dismiss()
            false
        }
    }

    private fun showRenameDialog(context: Context, position: Int) {
        val currentName = dataList[position].title

        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_rename, null)
        val etNewName: android.widget.EditText = dialogView.findViewById(R.id.etNewName)
        val btnCancel: Button = dialogView.findViewById(R.id.btnCancel)
        val btnConfirm: Button = dialogView.findViewById(R.id.btnConfirm)

        etNewName.setText(currentName)
        etNewName.selectAll()

        val dialog = androidx.appcompat.app.AlertDialog.Builder(context)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnConfirm.setOnClickListener {
            val newName = etNewName.text.toString().trim()
            if (newName.isNotEmpty()) {
                onRenameItem(position, newName)
                dialog.dismiss()
            } else {
                android.widget.Toast.makeText(
                    context,
                    "名称不能为空！",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }

        dialog.show()
    }

    override fun getItemCount(): Int = dataList.size
}