package com.semi.watchai.ui.settings

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.semi.watchai.R
import com.semi.watchai.data.repository.PromptRepository
import com.semi.watchai.data.repository.ProviderRepository
import kotlinx.coroutines.launch

/**
 * 系统提示词页：提示词选择与管理（可新建/编辑/删除）。
 */
class PromptSettingsActivity : AppCompatActivity(), AiSettingsFragment.Listener {

    private lateinit var promptRepository: PromptRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        promptRepository = PromptRepository(this)

        findViewById<Button>(R.id.setting_back).setOnClickListener {
            finish()
        }

        if (savedInstanceState == null) {
            lifecycleScope.launch {
                val providerRepository = ProviderRepository(this@PromptSettingsActivity)
                val selectedId = providerRepository.selectedProvider()?.id
                val modelOnline = providerRepository.modelOnline()
                val promptId = promptRepository.selectedPromptId()
                val contextMode = providerRepository.contextMode()
                supportFragmentManager.beginTransaction()
                    .replace(
                        R.id.settingsFragmentContainer,
                        AiSettingsFragment.newInstance(
                            providerId = selectedId,
                            modelOnline = modelOnline,
                            promptId = promptId,
                            allowManagement = true,
                            contextMode = contextMode,
                            title = "系统提示词",
                            showModelOnline = false,
                            showStreamEnabled = false,
                            showContextMode = false,
                            showPrompt = true,
                            showProvider = false
                        )
                    )
                    .commit()
            }
        }
    }

    override fun onProviderSelected(providerId: String) {}

    // 首页 = 全局设置：改动写入全局仓库
    override fun onPromptSelected(promptId: String) {
        lifecycleScope.launch {
            promptRepository.selectPrompt(promptId)
        }
    }

    override fun onModelOnlineChanged(enabled: Boolean) {}

    override fun onContextModeSelected(mode: String) {}
}
