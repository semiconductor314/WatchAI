package com.semi.watchai

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.first

/**
 * 管理系统提示词列表与全局选中项，基于 DataStore 持久化（与供应商共用同一个 DataStore）。
 * 内置"默认"提示词：不落库、不可编辑，作为用户未新建提示词时的兜底。
 */
class PromptRepository(private val context: Context) {

    private val gson = Gson()

    companion object {
        const val DEFAULT_PROMPT_ID = "default"

        private val KEY_PROMPTS = stringPreferencesKey("prompts_json")
        private val KEY_SELECTED = stringPreferencesKey("selected_prompt_id")

        // 内置"默认"提示词（不可编辑），内容为原有系统提示词
        fun defaultPrompt() = Prompt(
            id = DEFAULT_PROMPT_ID,
            name = "默认",
            content = "你是一个友好的AI助手，请用简洁自然的回答用户的问题。"
        )
    }

    /** 用户新建的提示词列表（不含内置"默认"）。 */
    suspend fun storedPrompts(): List<Prompt> {
        val prefs = context.providerDataStore.data.first()
        val json = prefs[KEY_PROMPTS]
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val type = object : TypeToken<List<Prompt>>() {}.type
            gson.fromJson<List<Prompt>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 下拉列表用：内置"默认" + 用户新建的。 */
    suspend fun allPrompts(): List<Prompt> =
        listOf(defaultPrompt()) + storedPrompts()

    /** 当前全局选中的提示词 id；未选时回退"default"。 */
    suspend fun selectedPromptId(): String {
        val prefs = context.providerDataStore.data.first()
        return prefs[KEY_SELECTED] ?: DEFAULT_PROMPT_ID
    }

    /** 按 id 解析提示词；"default" 或未知 id 都回退到内置默认。 */
    suspend fun promptById(id: String): Prompt {
        if (id == DEFAULT_PROMPT_ID) return defaultPrompt()
        return storedPrompts().firstOrNull { it.id == id } ?: defaultPrompt()
    }

    suspend fun selectPrompt(id: String) {
        context.providerDataStore.edit { it[KEY_SELECTED] = id }
    }

    /** 新增提示词（不自动选中，由宿主决定）。 */
    suspend fun addPrompt(prompt: Prompt) {
        val list = storedPrompts().toMutableList()
        list.add(prompt)
        context.providerDataStore.edit { it[KEY_PROMPTS] = gson.toJson(list) }
    }

    /** 编辑提示词（按 id 更新名字与内容）。 */
    suspend fun updatePrompt(prompt: Prompt) {
        val list = storedPrompts().toMutableList()
        val index = list.indexOfFirst { it.id == prompt.id }
        if (index == -1) return
        list[index] = prompt
        context.providerDataStore.edit { it[KEY_PROMPTS] = gson.toJson(list) }
    }

    /** 删除提示词（按 id 从存储列表移除）。 */
    suspend fun deletePrompt(id: String) {
        val list = storedPrompts().toMutableList()
        val index = list.indexOfFirst { it.id == id }
        if (index == -1) return
        list.removeAt(index)
        context.providerDataStore.edit { it[KEY_PROMPTS] = gson.toJson(list) }
    }
}
