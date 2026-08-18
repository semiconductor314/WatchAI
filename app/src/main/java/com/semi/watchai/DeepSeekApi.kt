package com.semi.watchai

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
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
        val stream: Boolean = false,
        // 联网搜索：null/省略 = 关闭；仅 DeepSeek 模型支持
        @SerializedName("web_search")
        val webSearch: Boolean? = null
    )

    data class RespMessage(val content: String)

    data class Choice(val message: RespMessage)

    data class ChatResponse(val choices: List<Choice>?)

    // 流式响应数据结构（与上面非流式共用 model/messages 请求体）
    // reasoningContent 对应 SSE 中的 reasoning_content：思考链文本（deepseek 系模型先输出思考再输出正文）
    data class StreamDelta(
        val content: String?,
        @SerializedName("reasoning_content")
        val reasoningContent: String? = null
    )

    data class StreamChoice(val delta: StreamDelta?)

    data class StreamChunk(val choices: List<StreamChoice>?)

    /** 流式事件：思考链与正文分开标记，调用方可分别处理（正文才需入库，思考仅作反馈）。 */
    sealed class StreamEvent {
        data class Reasoning(val text: String) : StreamEvent()
        data class Content(val text: String) : StreamEvent()
    }

    data class ErrorBody(val message: String?)

    // ========== SSE 解析 ==========

    /**
     * 解析流式响应的一行 SSE 数据，返回该行的增量 delta；无有效载荷
     * （空行、keep-alive 注释、`data: [DONE]`、坏 JSON）返回 null。
     */
    fun parseStreamDelta(line: String): StreamDelta? {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith(":")) return null
        val payload = trimmed.removePrefix("data:").trim()
        if (payload.isEmpty() || payload == "[DONE]") return null
        return try {
            gson.fromJson(payload, StreamChunk::class.java)?.choices
                ?.firstOrNull()?.delta
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 解析流式响应的一行 SSE 数据，返回该行的正文增量；无内容（空行、
     * keep-alive 注释、`data: [DONE]` 结束标记、仅带 role/思考的首行等）返回 null。
     * 增量按序拼接即完整回复。
     */
    fun parseStreamChunk(line: String): String? = parseStreamDelta(line)?.content

    /** 解析流式响应的一行 SSE 数据，返回该行的思考链增量（无则 null）。 */
    fun parseStreamReasoning(line: String): String? = parseStreamDelta(line)?.reasoningContent

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
     * [webSearch] 为是否在请求中开启联网搜索（仅 DeepSeek 模型支持，由调用方结合模型与开关计算）。
     * [systemPrompt] 为本次使用的系统提示词（来自提示词体系，可自定义）。
     * 网络/HTTP 错误会抛出 [IOException]，由调用方负责提示。
     */
    suspend fun chatCompletion(
        baseUrl: String,
        apiKey: String,
        messages: List<Message>,
        webSearch: Boolean,
        systemPrompt: String
    ): String = withContext(Dispatchers.IO) {
        val reqMessages = buildList {
            add(ReqMessage("system", systemPrompt))
            messages.forEach { msg ->
                add(ReqMessage(if (msg.isUser) "user" else "assistant", msg.content))
            }
        }
        val requestBody = gson.toJson(
            ChatRequest(
                model = MODEL,
                messages = reqMessages,
                webSearch = webSearch.takeIf { it }
            )
        ).toRequestBody("application/json; charset=utf-8".toMediaType())

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

    // ========== 流式对话 ==========

    /**
     * 流式对话：请求 `stream = true`，把 SSE 增量以 [Flow] 发射 [StreamEvent]
     * （思考链 [StreamEvent.Reasoning] 与正文 [StreamEvent.Content] 分开），调用方拼接展示。
     * [webSearch] 为是否在请求中开启联网搜索（仅 DeepSeek 模型支持）。
     * 收集被取消（如 Activity 销毁）时通过 awaitClose 取消底层 OkHttp 请求，网络随之中断。
     * 网络/HTTP 错误抛出 [IOException]，由调用方负责提示。
     */
    fun chatCompletionStream(
        baseUrl: String,
        apiKey: String,
        messages: List<Message>,
        webSearch: Boolean,
        systemPrompt: String
    ): Flow<StreamEvent> = callbackFlow {
        val reqMessages = buildList {
            add(ReqMessage("system", systemPrompt))
            messages.forEach { msg ->
                add(ReqMessage(if (msg.isUser) "user" else "assistant", msg.content))
            }
        }
        val requestBody = gson.toJson(
            ChatRequest(
                model = MODEL,
                messages = reqMessages,
                stream = true,
                webSearch = webSearch.takeIf { it }
            )
        ).toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url(normalizeChatUrl(baseUrl))
            .addHeader("Authorization", "Bearer $apiKey")
            .post(requestBody)
            .build()

        val call = client.newCall(request)
        val job = launch(Dispatchers.IO) {
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        val errorMsg = try {
                            response.body?.string()?.let {
                                gson.fromJson(it, ErrorBody::class.java)?.message
                            }
                        } catch (e: Exception) {
                            null
                        }
                        throw IOException(errorMsg ?: "HTTP ${response.code}")
                    }
                    val source = response.body?.source() ?: throw IOException("空响应")
                    while (!source.exhausted()) {
                        val line = source.readUtf8Line() ?: break
                        // 部分供应商发完 [DONE] 后不主动断开连接，显式识别结束标记立即收尾
                        val trimmed = line.trim()
                        if (trimmed == "data: [DONE]" || trimmed == "data:[DONE]") break
                        parseStreamDelta(line)?.let { delta ->
                            delta.content?.let { trySend(StreamEvent.Content(it)) }
                            delta.reasoningContent?.let { trySend(StreamEvent.Reasoning(it)) }
                        }
                    }
                    close()
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                close(e)
            }
        }
        awaitClose {
            call.cancel()
            job.cancel()
        }
    }
}
