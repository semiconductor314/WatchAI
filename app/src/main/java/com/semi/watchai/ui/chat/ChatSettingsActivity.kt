package com.semi.watchai.ui.chat

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.semi.watchai.R
import com.semi.watchai.data.local.AppDatabase
import com.semi.watchai.data.repository.ChatRepository
import com.semi.watchai.data.repository.PromptRepository
import com.semi.watchai.data.repository.ProviderRepository
import com.semi.watchai.ui.settings.AiSettingsFragment
import kotlinx.coroutines.launch

/**
 * 对话设置页：供应商 + 提示词 + 模型联网 + 流式输出开关，默认值 = 全局设置，可单独调整，
 * 保存为该对话自己的一份（写回 chats 表对应行的 providerId/promptId/modelOnline/streamEnabled）。
 * 不允许管理（新建/编辑提示词、新建供应商只在首页设置页提供）。
 */
class ChatSettingsActivity : AppCompatActivity(), AiSettingsFragment.Listener {

    private lateinit var chatId: String
    private lateinit var chatRepository: ChatRepository
    private lateinit var providerRepository: ProviderRepository
    private lateinit var promptRepository: PromptRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        chatId = intent.getStringExtra("chat_id") ?: run {
            finish()
            return
        }
        val db = AppDatabase.getInstance(this)
        chatRepository = ChatRepository(db.chatDao())
        providerRepository = ProviderRepository(this)
        promptRepository = PromptRepository(this)

        findViewById<Button>(R.id.setting_back).setOnClickListener {
            finish()
        }

        if (savedInstanceState == null) {
            lifecycleScope.launch {
                val chat = chatRepository.getChat(chatId)
                if (chat == null) {
                    Toast.makeText(this@ChatSettingsActivity, "对话不存在", Toast.LENGTH_SHORT).show()
                    finish()
                    return@launch
                }
                // 默认值 = 该对话已存设置 ?? 全局设置
                val providerId = chat.providerId ?: providerRepository.selectedProvider()?.id
                val modelOnline = chat.modelOnline ?: providerRepository.modelOnline()
                val promptId = chat.promptId ?: promptRepository.selectedPromptId()
                val contextMode = chat.contextMode ?: providerRepository.contextMode()
                val streamEnabled = chat.streamEnabled ?: providerRepository.streamEnabled()

                supportFragmentManager.beginTransaction()
                    .replace(
                        R.id.settingsFragmentContainer,
                        AiSettingsFragment.newInstance(
                            providerId, modelOnline, promptId, false, contextMode,
                            streamEnabled = streamEnabled
                        )
                    )
                    .commit()
            }
        }
    }

    // 用户在该对话设置页做的修改，只写进这一个对话
    override fun onProviderSelected(providerId: String) {
        lifecycleScope.launch {
            chatRepository.updateChatSettings(chatId, providerId = providerId)
        }
    }

    override fun onPromptSelected(promptId: String) {
        lifecycleScope.launch {
            chatRepository.updateChatSettings(chatId, promptId = promptId)
        }
    }

    override fun onModelOnlineChanged(enabled: Boolean) {
        lifecycleScope.launch {
            chatRepository.updateChatSettings(chatId, modelOnline = enabled)
        }
    }

    override fun onStreamEnabledChanged(enabled: Boolean) {
        lifecycleScope.launch {
            chatRepository.updateChatSettings(chatId, streamEnabled = enabled)
        }
    }

    override fun onContextModeSelected(mode: String) {
        lifecycleScope.launch {
            chatRepository.updateChatSettings(chatId, contextMode = mode)
        }
    }
}
