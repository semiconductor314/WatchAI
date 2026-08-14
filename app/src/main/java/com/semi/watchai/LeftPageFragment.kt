package com.semi.watchai

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
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
            // 首页设置只作为新建对话的默认值：创建时把当前全局设置快照进新对话
            val providerRepo = ProviderRepository(requireContext())
            val promptRepo = PromptRepository(requireContext())
            val providerId = providerRepo.selectedProvider()?.id
            val modelOnline = providerRepo.modelOnline()
            val promptId = promptRepo.selectedPromptId()

            val newChat = chatRepository.createChat("新聊天", providerId, modelOnline, promptId)
            dataList.add(0, newChat)
            adapter.notifyItemInserted(0)

            // 自动跳转到新聊天
            val intent = Intent(requireContext(), ChatActivity::class.java)
            intent.putExtra("chat_id", newChat.id)
            startActivity(intent)
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