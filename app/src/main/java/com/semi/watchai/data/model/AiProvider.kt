package com.semi.watchai.data.model

import java.util.UUID

/**
 * 一个 AI 供应商配置（OpenAI 兼容接口）。
 * [model] 为"选择模型"（[AiModels] 注册表 id）；null=旧数据，视为自定义。
 */
data class AiProvider(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val baseUrl: String,
    val apiKey: String,
    val model: String? = null
)

/** 该供应商选用的模型信息（旧数据/未知 id 兜底为自定义）。 */
fun AiProvider.modelInfo(): AiModels.AiModelInfo = AiModels.byId(model)

/** 该供应商的模型是否具备联网搜索能力（请求是否真的联网还需结合"模型联网"开关）。 */
fun AiProvider.supportsWebSearch(): Boolean = modelInfo().supportsWebSearch

/**
 * 旧版硬编码的默认 DeepSeek key。
 * 仅用于把存量数据中的默认 key 置空（见 [scrubDefaultApiKey]），不得再作为任何新默认值使用。
 */
const val LEGACY_DEFAULT_API_KEY = "sk-c6d9f96ab3844ffdaf174aba3d7b049b"

/** 供应商是否已配置可用：apiKey 非空且不是旧版默认 key。 */
fun AiProvider.hasConfiguredApiKey(): Boolean =
    apiKey.isNotBlank() && apiKey != LEGACY_DEFAULT_API_KEY

/** 若 apiKey 仍是旧版默认 key，置空后返回副本；否则原样返回。 */
fun AiProvider.scrubDefaultApiKey(): AiProvider =
    if (apiKey == LEGACY_DEFAULT_API_KEY) copy(apiKey = "") else this
