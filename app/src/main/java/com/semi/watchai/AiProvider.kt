package com.semi.watchai

import java.util.UUID

/**
 * 一个 AI 供应商配置（OpenAI 兼容接口）。
 */
data class AiProvider(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val baseUrl: String,
    val apiKey: String
)
