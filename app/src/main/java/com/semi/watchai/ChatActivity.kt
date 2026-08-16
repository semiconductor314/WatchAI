package com.semi.watchai

import android.content.Intent
import android.os.Bundle
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

class ChatActivity : AppCompatActivity() {

    private lateinit var chatId: String
    private lateinit var chat: Chat
    private lateinit var chatRepository: ChatRepository
    private lateinit var providerRepository: ProviderRepository
    private lateinit var promptRepository: PromptRepository
    private lateinit var adapter: MessageAdapter

    private lateinit var recyclerView: RecyclerView
    private lateinit var etInput: EditText
    private lateinit var btnSend: ImageButton
    private lateinit var btnBack: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        chatId = intent.getStringExtra("chat_id") ?: run {
            finish()
            return
        }

        val db = AppDatabase.getInstance(this)
        chatRepository = ChatRepository(db.chatDao())
        providerRepository = ProviderRepository(this)
        promptRepository = PromptRepository(this)

        // 兜底回填：进程被杀后直接恢复到对话页（未经过首页）时，
        // 同样把旧对话（contextMode 为 NULL）冻结为当前全局值。
        // DataStore 标志保证全 app 只执行一次。
        lifecycleScope.launch {
            if (!providerRepository.isContextModeBackfilled()) {
                chatRepository.backfillNullContextMode(providerRepository.contextMode())
                providerRepository.markContextModeBackfilled()
            }
        }

        initViews()
        setupRecyclerView()
        loadChat()
        setupListeners()
    }

    private fun initViews() {
        recyclerView = findViewById(R.id.recyclerViewMessages)
        etInput = findViewById(R.id.etInput)
        btnSend = findViewById(R.id.btnSend)
        btnBack = findViewById(R.id.dialSetting)
    }

    private fun loadChat() {
        lifecycleScope.launch {
            try {
                val loadedChat = chatRepository.getChat(chatId)
                if (loadedChat != null) {
                    chat = loadedChat
                    adapter.updateMessages(chat.messages)
                    scrollToBottom()
                } else {
                    Toast.makeText(this@ChatActivity, "聊天不存在", Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: Exception) {
                android.util.Log.e("ChatActivity", "加载聊天失败: ${e.message}", e)
                Toast.makeText(this@ChatActivity, "加载失败: ${e.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = MessageAdapter(mutableListOf())
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // 手表滚轮（rotary encoder）滚动消息列表
        recyclerView.setOnGenericMotionListener { _, event ->
            if (event.action == MotionEvent.ACTION_SCROLL &&
                event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)
            ) {
                val delta = -event.getAxisValue(MotionEvent.AXIS_SCROLL) * 50f
                recyclerView.scrollBy(0, delta.toInt())
                true
            } else {
                false
            }
        }
        // 与首页历史记录一致：滚动容器必须持有焦点，滚轮事件才会派发到它
        recyclerView.isFocusable = true
        recyclerView.isFocusableInTouchMode = true
        recyclerView.requestFocus()
        recyclerView.post { recyclerView.requestFocus() }
    }

    private fun setupListeners() {
        btnBack.setOnClickListener {
            // "设置 >" 按钮：进入该对话的设置页（每对话独立设置）
            val intent = Intent(this, ChatSettingsActivity::class.java)
            intent.putExtra("chat_id", chatId)
            startActivity(intent)
        }

        btnSend.setOnClickListener {
            sendMessage()
        }

        etInput.setOnKeyListener { _, keyCode, event ->
            if (keyCode == android.view.KeyEvent.KEYCODE_ENTER && event.action == android.view.KeyEvent.ACTION_DOWN) {
                sendMessage()
                true
            } else {
                false
            }
        }

        // 输入框失焦时把焦点还给消息列表，保证表冠滚轮持续可用
        etInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                recyclerView.requestFocus()
            }
        }
    }

    private fun sendMessage() {
        val text = etInput.text.toString().trim()
        if (text.isEmpty()) return

        val userMessage = Message(content = text, isUser = true)

        lifecycleScope.launch {
            try {
                chatRepository.addMessage(chatId, userMessage)
                chat.messages.add(userMessage)
                adapter.addMessage(userMessage)
                scrollToBottom()

                etInput.text.clear()

                if (chat.title == "新聊天") {
                    val newTitle = if (text.length > 20) text.substring(0, 20) + "..." else text
                    chatRepository.updateChatTitle(chatId, newTitle)
                }
            } catch (e: Exception) {
                android.util.Log.e("ChatActivity", "发送消息失败: ${e.message}", e)
                Toast.makeText(this@ChatActivity, "发送失败: ${e.message}", Toast.LENGTH_SHORT).show()
                return@launch
            }

            // 必须等用户消息入列表后再请求 AI，
            // 否则占位气泡会插到用户消息之前，且 AI 拿不到刚发的这条消息
            requestAIResponse()
        }
    }

    private fun requestAIResponse() {
        btnSend.isEnabled = false

        // 内存占位消息（不写库），等待期间展示"正在思考"
        val placeholder = Message(content = "正在思考…", isUser = false)
        adapter.addMessage(placeholder)
        scrollToBottom()

        lifecycleScope.launch {
            try {
                // 用每对话生效设置：对话已存值 ?? 全局值（重新读库，保证对话设置页的改动生效）
                val fresh = chatRepository.getChat(chatId)
                val provider = (fresh?.providerId?.let { providerRepository.providerById(it) })
                    ?: providerRepository.selectedProvider()
                if (provider == null) {
                    adapter.removeMessage(placeholder.id)
                    Toast.makeText(this@ChatActivity, "请先在设置中添加 AI 供应商", Toast.LENGTH_LONG).show()
                    return@launch
                }
                if (!provider.hasConfiguredApiKey()) {
                    adapter.removeMessage(placeholder.id)
                    Toast.makeText(this@ChatActivity, "请先填写 API Key", Toast.LENGTH_LONG).show()
                    return@launch
                }
                val modelOnline = fresh?.modelOnline ?: providerRepository.modelOnline()
                // 提示词：该对话已存 ?? 全局选中；"默认"恒可解析，无 null 分支
                val promptId = fresh?.promptId ?: promptRepository.selectedPromptId()
                // 上下文选择：该对话已存 ?? 全局默认（CONTEXT_MODE_ALL）
                val contextMode = fresh?.contextMode ?: providerRepository.contextMode()
                val systemPrompt = promptRepository.promptById(promptId).content

                // 首页设置只作为"新建对话"的默认值：
                // 若本次解析用到了全局回退值，就把生效值快照写回该对话，
                // 此后首页改动不再影响这个已有对话
                if (fresh != null &&
                    (fresh.providerId != provider.id ||
                        fresh.modelOnline != modelOnline ||
                        fresh.promptId != promptId ||
                        fresh.contextMode != contextMode)
                ) {
                    chatRepository.updateChatSettings(
                        chatId,
                        providerId = provider.id,
                        modelOnline = modelOnline,
                        promptId = promptId,
                        contextMode = contextMode
                    )
                }

                // 按上下文选择模式裁剪历史后再发送
                val requestMessages = MessageContextFilter.filter(chat.messages, contextMode)
                val reply = DeepSeekApi.chatCompletion(
                    provider.baseUrl,
                    provider.apiKey,
                    requestMessages,
                    modelOnline,
                    systemPrompt
                )
                adapter.updateMessage(placeholder.id, reply)

                val aiMessage = Message(content = reply, isUser = false)
                chatRepository.addMessage(chatId, aiMessage)
                chat.messages.add(aiMessage)
            } catch (e: Exception) {
                adapter.removeMessage(placeholder.id)
                android.util.Log.e("ChatActivity", "AI回复失败: ${e.message}", e)
                Toast.makeText(this@ChatActivity, "获取回复失败: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                btnSend.isEnabled = true
                scrollToBottom()
            }
        }
    }

    // 滚动到消息列表底部
    private fun scrollToBottom() {
        recyclerView.post {
            if (adapter.itemCount > 0) {
                recyclerView.smoothScrollToPosition(adapter.itemCount - 1)
            }
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }
}