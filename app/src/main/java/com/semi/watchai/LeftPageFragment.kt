package com.semi.watchai

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

class LeftPageFragment : Fragment() {

    private lateinit var adapter: ChatListAdapter
    private lateinit var chatRepository: ChatRepository
    private lateinit var dataList: MutableList<Chat>

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_left_page, container, false)

        // 初始化 Repository
        val db = AppDatabase.getInstance(requireContext())
        chatRepository = ChatRepository(db.chatDao())

        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerView)
        dataList = mutableListOf()

        adapter = ChatListAdapter(
            dataList = dataList,
            onItemClick = { chat ->
                val intent = Intent(requireContext(), ChatActivity::class.java)
                intent.putExtra("chat_id", chat.id)
                startActivity(intent)
            },
            onDeleteItem = { position ->
                deleteChat(position)
            },
            onRenameItem = { position, newName ->
                renameChat(position, newName)
            }
        )

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        recyclerView.isFocusable = true
        recyclerView.isFocusableInTouchMode = true
        recyclerView.requestFocus()

        view.post {
            recyclerView.requestFocus()
        }

        // ✅ 加载数据
        loadChats()

        // 新建聊天按钮
        val createButton = view.findViewById<Button>(R.id.createProjectButton)
        createButton.setOnClickListener {
            createNewChat()
        }

        return view
    }

    // ✅ 从 Room 加载聊天列表
    private fun loadChats() {
        lifecycleScope.launch {
            // 一次性回填：把升级前已存在的旧对话（contextMode 仍为 NULL）冻结为当前全局值，
            // 此后首页"上下文选择"改动不再影响它们。首页设置页只能从首页进入，
            // 因此在任何全局改动之前回填必已完成。DataStore 标志保证只执行一次。
            val providerRepo = ProviderRepository(requireContext())
            if (!providerRepo.isContextModeBackfilled()) {
                chatRepository.backfillNullContextMode(providerRepo.contextMode())
                providerRepo.markContextModeBackfilled()
            }

            val chats = chatRepository.getAllChatsFlow().collect { chatList ->
                dataList.clear()
                dataList.addAll(chatList)
                adapter.notifyDataSetChanged()

                // 如果没有聊天，自动创建一个
                if (dataList.isEmpty()) {
                    createNewChat()
                }
            }
        }
    }

    // ✅ 创建新聊天
    private fun createNewChat() {
        lifecycleScope.launch {
            val providerRepo = ProviderRepository(requireContext())
            val provider = providerRepo.selectedProvider()
            // 没有可用供应商（无供应商或未填 api_key）时，先弹窗要求填写供应商
            if (provider == null || !provider.hasConfiguredApiKey()) {
                showProviderSetupDialog(providerRepo)
                return@launch
            }
            doCreateChat(providerRepo, provider.id)
        }
    }

    private fun doCreateChat(providerRepo: ProviderRepository, providerId: String) {
        lifecycleScope.launch {
            // 首页设置只作为新建对话的默认值：创建时把当前全局设置快照进新对话
            val promptRepo = PromptRepository(requireContext())
            val modelOnline = providerRepo.modelOnline()
            val promptId = promptRepo.selectedPromptId()
            val contextMode = providerRepo.contextMode()
            val streamEnabled = providerRepo.streamEnabled()

            val newChat = chatRepository.createChat(
                "新聊天", providerId, modelOnline, promptId, contextMode, streamEnabled
            )
            dataList.add(0, newChat)
            adapter.notifyItemInserted(0)

            // 自动跳转到新聊天
            val intent = Intent(requireContext(), ChatActivity::class.java)
            intent.putExtra("chat_id", newChat.id)
            startActivity(intent)
        }
    }

    /** 未配置供应商时弹出的填写表单：保存后全局选中该供应商并继续创建对话。 */
    private fun showProviderSetupDialog(providerRepo: ProviderRepository) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_provider, null)
        val etName = dialogView.findViewById<EditText>(R.id.etProviderName)
        val etUrl = dialogView.findViewById<EditText>(R.id.etProviderUrl)
        val etKey = dialogView.findViewById<EditText>(R.id.etProviderKey)

        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))

        dialogView.findViewById<Button>(R.id.btnCancel).setOnClickListener {
            dialog.dismiss()
        }

        dialogView.findViewById<Button>(R.id.btnConfirm).setOnClickListener {
            val name = etName.text.toString().trim()
            val url = etUrl.text.toString().trim()
            val key = etKey.text.toString().trim()
            if (name.isEmpty() || url.isEmpty() || key.isEmpty()) {
                Toast.makeText(requireContext(), "名称、链接、Key 都不能为空", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            dialog.dismiss()
            lifecycleScope.launch {
                val newProvider = AiProvider(name = name, baseUrl = url, apiKey = key)
                providerRepo.addProvider(newProvider)
                providerRepo.selectProvider(newProvider.id)
                Toast.makeText(requireContext(), "已添加供应商：${newProvider.name}", Toast.LENGTH_SHORT).show()
                doCreateChat(providerRepo, newProvider.id)
            }
        }

        dialog.show()
        clampDialogHeight(dialog, dialogView)
    }

    /** 小屏/圆表盘上 AlertDialog 内容超高时，限制窗口高度保证底部按钮可达。 */
    private fun clampDialogHeight(dialog: androidx.appcompat.app.AlertDialog, contentView: View) {
        val win = dialog.window ?: return
        val dm = android.content.res.Resources.getSystem().displayMetrics
        val maxHeight = (dm.heightPixels * 0.85f).toInt()
        contentView.measure(
            View.MeasureSpec.makeMeasureSpec(dm.widthPixels, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST)
        )
        if (contentView.measuredHeight > maxHeight) {
            win.setLayout(android.view.WindowManager.LayoutParams.MATCH_PARENT, maxHeight)
        }
    }

    // ✅ 删除聊天
    private fun deleteChat(position: Int) {
        if (position < 0 || position >= dataList.size) return

        val deletedChat = dataList[position]

        lifecycleScope.launch {
            chatRepository.deleteChat(deletedChat.id)
            dataList.removeAt(position)
            adapter.notifyItemRemoved(position)
            adapter.notifyItemRangeChanged(position, dataList.size - position)
            Toast.makeText(requireContext(), "已删除: ${deletedChat.title}", Toast.LENGTH_SHORT).show()
        }
    }

    // ✅ 重命名聊天
    private fun renameChat(position: Int, newName: String) {
        if (position < 0 || position >= dataList.size) return

        val oldName = dataList[position].title
        val chatId = dataList[position].id

        lifecycleScope.launch {
            chatRepository.updateChatTitle(chatId, newName)
            dataList[position].title = newName
            adapter.notifyItemChanged(position)
            Toast.makeText(requireContext(), "已重命名: $oldName → $newName", Toast.LENGTH_SHORT).show()
        }
    }
}