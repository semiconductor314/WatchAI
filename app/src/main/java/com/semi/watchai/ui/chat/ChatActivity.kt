package com.semi.watchai.ui.chat

import android.content.Intent
import android.os.Bundle
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ScrollView
import android.widget.TextView
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
import kotlinx.coroutines.delay
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
    private lateinit var streamingContainer: ScrollView
    private lateinit var tvStreaming: TextView

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
        streamingContainer = findViewById(R.id.streamingContainer)
        tvStreaming = findViewById(R.id.tvStreaming)
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

        // 同步进入"请求中"：立即禁用发送 + 防重入，避免重复发送产生重复回复
        aiRequestInFlight = true
        btnSend.isEnabled = false
        showStreaming("正在思考…")

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
                // 流式输出：该对话已存 ?? 全局默认（默认开）
                val streamEnabled = fresh?.streamEnabled ?: providerRepository.streamEnabled()
                val systemPrompt = promptRepository.promptById(promptId).content

                // 首页设置只作为"新建对话"的默认值：
                // 若本次解析用到了全局回退值，就把生效值快照写回该对话，
                // 此后首页改动不再影响这个已有对话
                if (fresh != null &&
                    (fresh.providerId != provider.id ||
                        fresh.modelOnline != modelOnline ||
                        fresh.promptId != promptId ||
                        fresh.contextMode != contextMode ||
                        fresh.streamEnabled != streamEnabled)
                ) {
                    chatRepository.updateChatSettings(
                        chatId,
                        providerId = provider.id,
                        modelOnline = modelOnline,
                        promptId = promptId,
                        contextMode = contextMode,
                        streamEnabled = streamEnabled
                    )
                }

                // 按上下文选择模式裁剪历史后再发送
                val requestMessages = MessageContextFilter.filter(chat.messages, contextMode)
                var reply: String? = null
                var interrupted = false
                if (streamEnabled) {
                    // 流式：思考链与正文分路累积，在列表外的 tvStreaming 中按固定节奏逐字揭示。
                    // 流式期间完全不碰 RecyclerView —— 列表随时可自由滚动，
                    // 也没有列表内长文本不断重排的布局风暴
                    val thinkingSb = StringBuilder()
                    val contentSb = StringBuilder()
                    val displaySb = StringBuilder()   // 已揭示到屏幕的文本
                    var contentStarted = false
                    var streamDone = false
                    var streamError: Exception? = null

                    // 生产者：只累积文本，不做 UI 操作
                    launch {
                        try {
                            DeepSeekApi.chatCompletionStream(
                                provider.baseUrl,
                                provider.apiKey,
                                requestMessages,
                                webSearch,
                                systemPrompt
                            ).collect { event ->
                                when (event) {
                                    is DeepSeekApi.StreamEvent.Reasoning ->
                                        thinkingSb.append(event.text)
                                    is DeepSeekApi.StreamEvent.Content ->
                                        contentSb.append(event.text)
                                }
                            }
                        } catch (e: Exception) {
                            streamError = e
                        } finally {
                            streamDone = true
                        }
                    }

                    // 渲染器：每 60ms 揭示至多 4 个字符；正文开始后丢弃思考文本改显正文
                    while (true) {
                        if (contentSb.isNotEmpty() && !contentStarted) {
                            contentStarted = true
                            displaySb.setLength(0)
                        }
                        val source = if (contentStarted) contentSb else thinkingSb
                        val revealed = displaySb.length
                        val revealCount = minOf(4, source.length - revealed)
                        if (revealCount > 0) {
                            displaySb.append(source, revealed, revealed + revealCount)
                        }
                        showStreaming(displaySb.toString().ifEmpty { "正在思考…" })
                        // 失败且无正文：尽快退出；否则等流完成且已全部揭示完再退出
                        if (streamError != null && contentSb.isEmpty()) break
                        if (streamDone && displaySb.length >= source.length) break
                        delay(60)
                    }

                    if (streamError != null) {
                        // 已累积部分正文：保留部分回复并提示中断；否则视为失败走外层 catch
                        if (contentSb.isNotEmpty()) {
                            interrupted = true
                            reply = contentSb.toString()
                        } else {
                            throw streamError!!
                        }
                    } else {
                        reply = contentSb.toString()
                    }
                } else {
                    reply = DeepSeekApi.chatCompletion(
                        provider.baseUrl,
                        provider.apiKey,
                        requestMessages,
                        webSearch,
                        systemPrompt
                    )
                }
                val finalReply = reply ?: throw IOException("响应缺少内容")
                if (finalReply.isEmpty()) throw IOException("响应缺少内容")
                if (interrupted) {
                    Toast.makeText(this@ChatActivity, "回复中断，已保留部分内容", Toast.LENGTH_LONG).show()
                }
                // 流式/非流式结束：一次性把完整回复作为新消息加入列表并入库
                val aiMessage = Message(content = finalReply, isUser = false)
                adapter.addMessage(aiMessage)
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

    // 显示/更新流式区域文本，并钉在底部（长回复在区域内滚动时始终看到最新内容）。
    // 同时把焦点还给消息列表，保证滚轮滚动列表而不是流式区
    private fun showStreaming(text: String) {
        streamingContainer.visibility = View.VISIBLE
        tvStreaming.text = text
        recyclerView.requestFocus()
        streamingContainer.post { streamingContainer.fullScroll(View.FOCUS_DOWN) }
    }

    // 请求结束（成功/失败/中断）恢复发送状态，隐藏流式区域，焦点还给消息列表
    private fun resetSendState() {
        aiRequestInFlight = false
        btnSend.isEnabled = true
        streamingContainer.visibility = View.GONE
        tvStreaming.text = ""
        recyclerView.requestFocus()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }
}
