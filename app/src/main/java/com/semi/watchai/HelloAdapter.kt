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

class HelloAdapter(
    private val dataList: MutableList<String>,
    private val onButtonClick: (String, Int) -> Unit,
    private val onDeleteItem: (Int) -> Unit,
    private val onRenameItem: (Int, String) -> Unit      // ✅ 新增重命名回调
) : RecyclerView.Adapter<HelloAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvItemText: TextView = itemView.findViewById(R.id.tvItemText)
        val btnAction: Button = itemView.findViewById(R.id.btnAction)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_hello, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = dataList[position]
        val context = holder.itemView.context
        holder.tvItemText.text = item

        //  按钮点击事件：弹出 PopupWindow
        holder.btnAction.setOnClickListener { view ->
            showPopupWindow(context, view, item, position)
        }
    }

    //  显示弹出窗口（根据屏幕大小动态调整尺寸）
    private fun showPopupWindow(context: Context, anchorView: View, item: String, position: Int) {
        // 1. 获取屏幕尺寸
        val screenWidth = Resources.getSystem().displayMetrics.widthPixels
        val screenHeight = Resources.getSystem().displayMetrics.heightPixels

        // 2. 计算弹窗尺寸（屏幕宽度的 30%，高度的 30%）
        val popupWidth = (screenWidth * 0.30f).toInt()
        val popupHeight = (screenHeight * 0.30f).toInt()

        // 3. 加载弹出布局
        val popupView = LayoutInflater.from(context).inflate(R.layout.popup_actions, null)

        // 4. 动态设置弹窗尺寸
        popupView.layoutParams = LinearLayout.LayoutParams(popupWidth, popupHeight)

        // 5. 动态调整按钮大小
        val btnOption1: Button = popupView.findViewById(R.id.btnOption1)
        val btnOption2: Button = popupView.findViewById(R.id.btnOption2)

        // 修改按钮文字
        btnOption1.text = "重命名"
        btnOption2.text = "删除"

        // 按钮高度 = 弹窗高度的 40%，宽度 = 弹窗宽度的 90%
        val btnHeight = (popupHeight * 0.45f).toInt()
        val btnWidth = (popupWidth * 0.90f).toInt()
        val btnMargin = (popupHeight * 0.04f).toInt()

        // 设置按钮1
        btnOption1.layoutParams = LinearLayout.LayoutParams(btnWidth, btnHeight).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setMargins(0, 0, 0, btnMargin)
        }
        btnOption1.textSize = (popupWidth * 0.08f).coerceIn(10f, 22f)

        // 设置按钮2
        btnOption2.layoutParams = LinearLayout.LayoutParams(btnWidth, btnHeight).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setMargins(0, 0, 0, 0)
        }
        btnOption2.textSize = (popupWidth * 0.08f).coerceIn(10f, 22f)

        // 6. 创建 PopupWindow
        val popupWindow = PopupWindow(
            popupView,
            popupWidth,
            popupHeight,
            true
        )

        // 7. 设置背景
        popupWindow.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        // 8. 设置动画
        popupWindow.animationStyle = android.R.style.Animation_Dialog

        // 9. 获取按钮位置
        val location = IntArray(2)
        anchorView.getLocationOnScreen(location)

        // 10. 计算弹窗显示位置（在按钮左方）
        var x = location[0] - popupWidth
        var y = location[1] - popupHeight / 4

        // 11. 如果下方空间不够，显示在按钮上方
        if (y + popupHeight > screenHeight) {
            y = location[1] - popupHeight
        }

        // 12. 如果左边空间不够，向左调整
        if (x < 0) {
            x = 10
        }
        // 如果右边超出屏幕，向右调整
        if (x + popupWidth > screenWidth) {
            x = screenWidth - popupWidth - 10
        }

        // 13. 显示 PopupWindow
        popupWindow.showAtLocation(anchorView, Gravity.NO_GRAVITY, x, y)

        // 14. 设置按钮点击事件
        // ✅ 按钮1：重命名
        btnOption1.setOnClickListener {
            popupWindow.dismiss()
            // 显示重命名对话框
            showRenameDialog(context, position)
        }

        // ✅ 按钮2：删除项目（调用删除回调）
        btnOption2.setOnClickListener {
            onDeleteItem(position)
            popupWindow.dismiss()
        }

        // 15. 点击外部关闭
        popupView.setOnTouchListener { _, _ ->
            popupWindow.dismiss()
            false
        }
    }

    // 显示重命名对话框
// 使用自定义布局的对话框
    private fun showRenameDialog(context: Context, position: Int) {
        val currentName = dataList[position]

        // 1. 加载自定义布局
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_rename, null)
        val etNewName: android.widget.EditText = dialogView.findViewById(R.id.etNewName)
        val btnCancel: Button = dialogView.findViewById(R.id.btnCancel)
        val btnConfirm: Button = dialogView.findViewById(R.id.btnConfirm)

        // 2. 设置当前名称
        etNewName.setText(currentName)
        etNewName.selectAll()

        // 3. 创建对话框
        val dialog = androidx.appcompat.app.AlertDialog.Builder(context)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        // 4. 设置对话框背景透明（让自定义背景生效）
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        // 5. 按钮点击事件
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

        // 6. 显示对话框
        dialog.show()
    }

    override fun getItemCount(): Int = dataList.size

    // ✅ 删除指定位置的数据
    fun removeItem(position: Int) {
        if (position >= 0 && position < dataList.size) {
            dataList.removeAt(position)
            notifyItemRemoved(position)
            notifyItemRangeChanged(position, dataList.size - position)
        }
    }

    // ✅ 重命名指定位置的数据
    fun renameItem(position: Int, newName: String) {
        if (position >= 0 && position < dataList.size) {
            dataList[position] = newName
            notifyItemChanged(position)  // 刷新该条目
        }
    }
}