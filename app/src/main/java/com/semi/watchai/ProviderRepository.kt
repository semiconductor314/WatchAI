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
        private val KEY_STREAM_ENABLED = booleanPreferencesKey("stream_enabled")

        const val CONTEXT_MODE_ALL = "all"
        const val CONTEXT_MODE_LAST = "last"
        const val CONTEXT_MODE_NONE = "none"
    }

    /** 供应商列表；为空时返回空列表（不再播种任何默认供应商）。 */
    suspend fun providers(): List<AiProvider> {
        val prefs = context.providerDataStore.data.first()
        val json = prefs[KEY_PROVIDERS]
        if (json.isNullOrBlank()) return emptyList()
        val list = try {
            val type = object : TypeToken<List<AiProvider>>() {}.type
            gson.fromJson<List<AiProvider>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
        // 存量清理：旧版硬编码默认 key 置空（幂等），有变化才写回
        val scrubbed = list.map { it.scrubDefaultApiKey() }
        if (scrubbed != list) {
            context.providerDataStore.edit { it[KEY_PROVIDERS] = gson.toJson(scrubbed) }
        }
        return scrubbed
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

    /** 更新供应商（按 id 覆盖；不存在则忽略）。 */
    suspend fun updateProvider(provider: AiProvider) {
        val list = providers().toMutableList()
        val index = list.indexOfFirst { it.id == provider.id }
        if (index < 0) return
        list[index] = provider
        context.providerDataStore.edit { it[KEY_PROVIDERS] = gson.toJson(list) }
    }

    /** 删除供应商（按 id；不存在则忽略）。 */
    suspend fun deleteProvider(id: String) {
        val list = providers().toMutableList()
        if (list.removeAll { it.id == id }) {
            context.providerDataStore.edit { it[KEY_PROVIDERS] = gson.toJson(list) }
        }
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

    /** 全局"流式输出"默认值（每对话可单独覆盖），默认开启。 */
    suspend fun streamEnabled(): Boolean =
        context.providerDataStore.data.first()[KEY_STREAM_ENABLED] ?: true

    /** 设置全局"流式输出"默认值。 */
    suspend fun setStreamEnabled(enabled: Boolean) {
        context.providerDataStore.edit { it[KEY_STREAM_ENABLED] = enabled }
    }
}
