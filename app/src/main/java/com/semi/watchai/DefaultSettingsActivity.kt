package com.semi.watchai

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * 默认设置页：模型联网 + 流式输出 + 上下文选择（全局默认值，作用于新建对话）。
 */
class DefaultSettingsActivity : AppCompatActivity(), AiSettingsFragment.Listener {

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
                val promptId = PromptRepository(this@DefaultSettingsActivity).selectedPromptId()
                val contextMode = providerRepository.contextMode()
                val streamEnabled = providerRepository.streamEnabled()
                supportFragmentManager.beginTransaction()
                    .replace(
                        R.id.settingsFragmentContainer,
                        AiSettingsFragment.newInstance(
                            providerId = selectedId,
                            modelOnline = modelOnline,
                            promptId = promptId,
                            allowManagement = true,
                            contextMode = contextMode,
                            streamEnabled = streamEnabled,
                            title = "默认设置",
                            showPrompt = false,
                            showProvider = false
                        )
                    )
                    .commit()
            }
        }
    }

    override fun onProviderSelected(providerId: String) {}

    override fun onPromptSelected(promptId: String) {}

    override fun onModelOnlineChanged(enabled: Boolean) {
        lifecycleScope.launch {
            providerRepository.setModelOnline(enabled)
        }
    }

    override fun onStreamEnabledChanged(enabled: Boolean) {
        lifecycleScope.launch {
            providerRepository.setStreamEnabled(enabled)
        }
    }

    override fun onContextModeSelected(mode: String) {
        lifecycleScope.launch {
            providerRepository.setContextMode(mode)
        }
    }
}
