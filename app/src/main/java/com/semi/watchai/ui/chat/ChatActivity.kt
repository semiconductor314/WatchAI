package com.semi.watchai.ui.chat

import android.content.Intent
import android.os.Bundle
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.semi.watchai.R
import com.semi.watchai.data.local.AppDatabase
import com.semi.watchai.data.model.Chat
import com.semi.watchai.data.model.Message
import com.semi.watchai.data.model.hasConfiguredApiKey
import com.semi.watchai.data.model.supportsWebSearch
import com.semi.watchai.data.repository.ChatRepository
import com.semi.watchai.data.repository.PromptRepository
import com.semi.watchai.data.repository.ProviderRepository
import com.semi.watchai.network.DeepSeekApi
import com.semi.watchai.util.MessageContextFilter
import java.io.IOException
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

    // 同步防重入：进入 sendMessage 立即置 true，请求结束（finally）才复位，
    // 关闭双击/表冠确认在异步 DB 写入窗口内触发第二次发送的竞态
    private var aiRequestInFlight = false

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
        adapter = MessageAdapter(mutableListOf(), lifecycleScope)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // 滚轮（rotary encoder）滚动消息列表统一在 Activity 层的 onGenericMotionEvent 处理：
        // 不依赖焦点在哪个子视图上（输入框/流式区/列表都行），事件冒泡到 Activity 即滚动列表。
        // 消息列表保持可聚焦，作为滚轮事件的首要接收者。
        recyclerView.isFocusable = true
        recyclerView.isFocusableInTouchMode = true
        recyclerView.requestFocus()
        recyclerView.post { recyclerView.requestFocus() }
    }

    // 手表滚轮（rotary encoder）滚动消息列表：在 Activity 层统一截获，
    // 无论焦点在输入框还是列表，滚轮都滚动消息列表
    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_SCROLL &&
            event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)
        ) {
            val delta = -event.getAxisValue(MotionEvent.AXIS_SCROLL) * 50f
            recyclerView.scrollBy(0, delta.toInt())
            return true
        }
        return super.onGenericMotionEvent(event)
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
        if (text.isEmpty() || aiRequestInFlight) return

        // 同步进入“请求中”：立即禁用发送 + 防重入，避免重复发送产生重复回复
        aiRequestInFlight = true
        btnSend.isEnabled = false
        // 收起输入法并让焦点回到列表（否则会一直停留在输入法界面）
        hideKeyboard()

        val userMessage = Message(content = text, isUser = true)

        lifecycleScope.launch {
            try {
                chatRepository.addMessage(chatId, userMessage)
                chat.messages.add(userMessage)
                adapter.addMessage(userMessage)
                // 用户气泡出现后，再在尾部追加与 AI 气泡同款的“正在思考…”占位（回复到达即原地替换）
                adapter.showThinking()
                scrollToBottom()

                etInput.text.clear()

                if (chat.title == "新聊天") {
                    val newTitle = if (text.length > 20) text.substring(0, 20) + "..." else text
                    chatRepository.updateChatTitle(chatId, newTitle)
                }
            } catch (e: Exception) {
                android.util.Log.e("ChatActivity", "发送消息失败: ${e.message}", e)
                Toast.makeText(this@ChatActivity, "发送失败: ${e.message}", Toast.LENGTH_SHORT).show()
                resetSendState()
                return@launch
            }

            // 必须等用户消息入列表后再请求 AI，
            // 否则 AI 拿不到刚发的这条消息
            requestAIResponse()
        }
    }

    private fun requestAIResponse() {
        lifecycleScope.launch {
            try {
                // 用每对话生效设置：对话已存值 ?? 全局值（重新读库，保证对话设置页的改动生效）
                val fresh = chatRepository.getChat(chatId)
                val provider = (fresh?.providerId?.let { providerRepository.providerById(it) })
                    ?: providerRepository.selectedProvider()
                if (provider == null) {
                    Toast.makeText(this@ChatActivity, "请先在设置中添加 AI 供应商", Toast.LENGTH_LONG).show()
                    return@launch
                }
                if (!provider.hasConfiguredApiKey()) {
                    Toast.makeText(this@ChatActivity, "请先填写 API Key", Toast.LENGTH_LONG).show()
                    return@launch
                }
                val modelOnline = fresh?.modelOnline ?: providerRepository.modelOnline()
                // 联网搜索：仅当供应商模型支持（目前仅 DeepSeek）且"模型联网"开关开启
                val webSearch = provider.supportsWebSearch() && modelOnline
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

                // 按上下文选择模式裁剪历史后再发送（流式输出已全局关闭，见 util/FeatureFlags）
                val requestMessages = MessageContextFilter.filter(chat.messages, contextMode)
                val reply = DeepSeekApi.chatCompletion(
                    provider.baseUrl,
                    provider.apiKey,
                    requestMessages,
                    webSearch,
                    systemPrompt
                )
                val finalReply = reply ?: throw IOException("响应缺少内容")
                if (finalReply.isEmpty()) throw IOException("响应缺少内容")
                // 回复结束：把“正在思考…”占位气泡原地替换为真实回复并入库
                val aiMessage = Message(content = finalReply, isUser = false)
                adapter.replaceThinkingWith(aiMessage)
                chatRepository.addMessage(chatId, aiMessage)
                chat.messages.add(aiMessage)
            } catch (e: Exception) {
                android.util.Log.e("ChatActivity", "AI回复失败: ${e.message}", e)
                Toast.makeText(this@ChatActivity, "获取回复失败: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                resetSendState()
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

    /** 收起输入法并把焦点还给消息列表（表冠滚轮持续可用）。 */
    private fun hideKeyboard() {
        val imm = getSystemService(InputMethodManager::class.java)
        imm?.hideSoftInputFromWindow(etInput.windowToken, 0)
        etInput.clearFocus()
        recyclerView.requestFocus()
    }

    // 请求结束（成功/失败/中断）恢复发送状态，移除遗留的“正在思考…”占位气泡
    private fun resetSendState() {
        aiRequestInFlight = false
        btnSend.isEnabled = true
        adapter.hideThinking()
        recyclerView.requestFocus()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }
}
