package com.semi.watchai

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * OpenAI 兼容的 AI 对话客户端（如 DeepSeek）。
 *
 * 供应商的 baseUrl / apiKey 由 [ProviderRepository] 提供，可在设置页切换。
 */
object DeepSeekApi {

    // 模型名常量：deepseek-chat 已于 2026-07-24 弃用；改用 v4-flash。需要更强推理可切 "deepseek-v4-pro"。
    // 注意：若在设置页接入其它供应商且对方不认该模型名，请求会失败。
    private const val MODEL = "deepseek-v4-flash"

    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // ========== 请求/响应数据类 ==========

    data class ReqMessage(val role: String, val content: String)

    data class ChatRequest(
        val model: String,
        val messages: List<ReqMessage>,
        val stream: Boolean = false
    )

    data class RespMessage(val content: String)

    data class Choice(val message: RespMessage)

    data class ChatResponse(val choices: List<Choice>?)

    data class ErrorBody(val message: String?)

    // ========== 对话 ==========

    /**
     * 把用户填的链接归一化为完整的 /chat/completions 地址。
     */
    private fun normalizeChatUrl(baseUrl: String): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        return if (trimmed.endsWith("/chat/completions")) trimmed else "$trimmed/chat/completions"
    }

    /**
     * 向 [baseUrl] 指定的供应商发送完整对话历史，返回助手回复文本。
     * [modelOnline] 为"模型联网"开关设置值（当前仅透传存储，联网搜索功能待后续接入）。
     * [systemPrompt] 为本次使用的系统提示词（来自提示词体系，可自定义）。
     * 网络/HTTP 错误会抛出 [IOException]，由调用方负责提示。
     */
    suspend fun chatCompletion(
        baseUrl: String,
        apiKey: String,
        messages: List<Message>,
        modelOnline: Boolean,
        systemPrompt: String
    ): String = withContext(Dispatchers.IO) {
        val reqMessages = buildList {
            add(ReqMessage("system", systemPrompt))
            messages.forEach { msg ->
                add(ReqMessage(if (msg.isUser) "user" else "assistant", msg.content))
            }
        }
        val requestBody = gson.toJson(ChatRequest(model = MODEL, messages = reqMessages))
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url(normalizeChatUrl(baseUrl))
            .addHeader("Authorization", "Bearer $apiKey")
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: throw IOException("空响应")
            if (!response.isSuccessful) {
                val errorMsg = try {
                    gson.fromJson(body, ErrorBody::class.java)?.message
                } catch (e: Exception) {
                    null
                }
                throw IOException(errorMsg ?: "HTTP ${response.code}")
            }
            val parsed = gson.fromJson(body, ChatResponse::class.java)
            parsed.choices?.firstOrNull()?.message?.content
                ?: throw IOException("响应缺少内容")
        }
    }
}
