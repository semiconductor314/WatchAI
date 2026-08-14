package com.semi.watchai

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * 首页全局设置页：供应商 + 提示词 + 模型联网开关（全局默认值）。
 * 共享 [AiSettingsFragment]（allowManagement=true，可新建/编辑/删除提示词），持久化到全局仓库。
 */
class SettingsActivity : AppCompatActivity(), AiSettingsFragment.Listener {

    private lateinit var providerRepository: ProviderRepository
    private lateinit var promptRepository: PromptRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        providerRepository = ProviderRepository(this)
        promptRepository = PromptRepository(this)

        findViewById<Button>(R.id.setting_back).setOnClickListener {
            finish()
        }

        if (savedInstanceState == null) {
            lifecycleScope.launch {
                val selectedId = providerRepository.selectedProvider()?.id
                val modelOnline = providerRepository.modelOnline()
                val promptId = promptRepository.selectedPromptId()
                supportFragmentManager.beginTransaction()
                    .replace(
                        R.id.settingsFragmentContainer,
                        AiSettingsFragment.newInstance(selectedId, modelOnline, promptId, true)
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

    override fun onPromptSelected(promptId: String) {
        lifecycleScope.launch {
            promptRepository.selectPrompt(promptId)
        }
    }

    override fun onModelOnlineChanged(enabled: Boolean) {
        lifecycleScope.launch {
            providerRepository.setModelOnline(enabled)
        }
    }
}
