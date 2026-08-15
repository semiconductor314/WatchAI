package com.semi.watchai

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.first

// 全局 DataStore（保存供应商配置）
val Context.providerDataStore by preferencesDataStore(name = "settings")

/**
 * 管理 AI 供应商列表与当前选中项，基于 DataStore 持久化。
 */
class ProviderRepository(private val context: Context) {

    private val gson = Gson()

    companion object {
        private val KEY_PROVIDERS = stringPreferencesKey("providers_json")
        private val KEY_SELECTED = stringPreferencesKey("selected_provider_id")
        private val KEY_MODEL_ONLINE = booleanPreferencesKey("model_online")
        private val KEY_CONTEXT_MODE = stringPreferencesKey("context_mode")
        private val KEY_CONTEXT_MODE_BACKFILLED = booleanPreferencesKey("context_mode_backfilled")

        const val CONTEXT_MODE_ALL = "all"
        const val CONTEXT_MODE_LAST = "last"
        const val CONTEXT_MODE_NONE = "none"

        // 默认 DeepSeek 供应商（沿用原硬编码 key，保证首次启动即可用）
        private fun defaultProvider() = AiProvider(
            name = "DeepSeek",
            baseUrl = "https://api.deepseek.com",
            apiKey = "sk-c6d9f96ab3844ffdaf174aba3d7b049b"
        )
    }

    /** 供应商列表；为空时播种默认 DeepSeek 并写回。 */
    suspend fun providers(): List<AiProvider> {
        val prefs = context.providerDataStore.data.first()
        val json = prefs[KEY_PROVIDERS]
        if (json.isNullOrBlank()) {
            val defaults = listOf(defaultProvider())
            context.providerDataStore.edit { it[KEY_PROVIDERS] = gson.toJson(defaults) }
            return defaults
        }
        return try {
            val type = object : TypeToken<List<AiProvider>>() {}.type
            gson.fromJson<List<AiProvider>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 新增供应商（追加到全局列表）。
     * 不在此处改变全局选中项：是否选中由宿主决定——
     * 首页设置页经 listener 走 [selectProvider]，对话设置页走 [ChatRepository.updateChatSettings]。
     */
    suspend fun addProvider(provider: AiProvider) {
        val list = providers().toMutableList()
        list.add(provider)
        context.providerDataStore.edit { it[KEY_PROVIDERS] = gson.toJson(list) }
    }

    /** 切换当前选中的供应商。 */
    suspend fun selectProvider(id: String) {
        context.providerDataStore.edit { it[KEY_SELECTED] = id }
    }

    /** 当前选中的供应商；未选或已失效时回退到第一个。 */
    suspend fun selectedProvider(): AiProvider? {
        val list = providers()
        if (list.isEmpty()) return null
        val prefs = context.providerDataStore.data.first()
        val selectedId = prefs[KEY_SELECTED]
        return list.firstOrNull { it.id == selectedId } ?: list.first()
    }

    /** 按 id 查供应商。 */
    suspend fun providerById(id: String): AiProvider? =
        providers().firstOrNull { it.id == id }

    /** 全局"模型联网"默认值（每对话可单独覆盖）。 */
    suspend fun modelOnline(): Boolean =
        context.providerDataStore.data.first()[KEY_MODEL_ONLINE] ?: false

    /** 设置全局"模型联网"默认值。 */
    suspend fun setModelOnline(enabled: Boolean) {
        context.providerDataStore.edit { it[KEY_MODEL_ONLINE] = enabled }
    }

    /** 全局"上下文选择"默认值（每对话可单独覆盖）。 */
    suspend fun contextMode(): String =
        context.providerDataStore.data.first()[KEY_CONTEXT_MODE] ?: CONTEXT_MODE_ALL

    /** 设置全局"上下文选择"默认值。 */
    suspend fun setContextMode(mode: String) {
        context.providerDataStore.edit { it[KEY_CONTEXT_MODE] = mode }
    }

    /** 是否已执行过旧对话 contextMode 一次性回填。 */
    suspend fun isContextModeBackfilled(): Boolean =
        context.providerDataStore.data.first()[KEY_CONTEXT_MODE_BACKFILLED] ?: false

    /** 标记旧对话 contextMode 一次性回填已完成。 */
    suspend fun markContextModeBackfilled() {
        context.providerDataStore.edit { it[KEY_CONTEXT_MODE_BACKFILLED] = true }
    }
}
