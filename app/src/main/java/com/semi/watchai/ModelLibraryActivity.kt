package com.semi.watchai

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * 模型库页：选择用于 AI 对话的接口（供应商管理，可新建/编辑/删除）。
 */
class ModelLibraryActivity : AppCompatActivity(), AiSettingsFragment.Listener {

    private lateinit var providerRepository: ProviderRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        providerRepository = ProviderRepository(this)

        findViewById<Button>(R.id.setting_back).setOnClickListener {
            finish()
        }

        if (savedInstanceState == null) {
            lifecycleScope.launch {
                val selectedId = providerRepository.selectedProvider()?.id
                val modelOnline = providerRepository.modelOnline()
                val promptId = PromptRepository(this@ModelLibraryActivity).selectedPromptId()
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
                            title = "模型库",
                            showModelOnline = false,
                            showContextMode = false,
                            showPrompt = false,
                            showProvider = true
                        )
                    )
                    .commit()
            }
        }
    }

    // 首页 = 全局设置：改动写入全局仓库
    override fun onProviderSelected(providerId: String) {
        lifecycleScope.launch {
            providerRepository.selectProvider(providerId)
        }
    }

    override fun onPromptSelected(promptId: String) {}

    override fun onModelOnlineChanged(enabled: Boolean) {}

    override fun onContextModeSelected(mode: String) {}
}
