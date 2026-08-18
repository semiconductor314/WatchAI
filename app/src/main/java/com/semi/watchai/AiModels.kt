package com.semi.watchai

/**
 * AI 模型注册表："选择模型"下拉的预设来源。
 *
 * 扩展新模型只需在 [ALL] 里追加一项即可（下拉、预设链接、联网能力自动生效），
 * 未来可按模型扩展更多属性（apiModelName 下发、联网参数格式、模型参数等）。
 */
object AiModels {

    const val CUSTOM = "custom"
    const val DEEPSEEK = "deepseek"

    data class AiModelInfo(
        val id: String,
        val displayName: String,
        /** 预设接口链接；null = 自定义，需用户手填 */
        val presetBaseUrl: String? = null,
        /** 请求体 model 名；null = 沿用客户端常量（自定义） */
        val apiModelName: String? = null,
        /** 该模型是否提供联网搜索能力（是否显示/生效"模型联网"开关由宿主决定） */
        val supportsWebSearch: Boolean = false
    )

    val ALL = listOf(
        AiModelInfo(id = CUSTOM, displayName = "自定义"),
        AiModelInfo(
            id = DEEPSEEK,
            displayName = "DeepSeek",
            presetBaseUrl = "https://api.deepseek.com",
            apiModelName = "deepseek-v4-flash",
            supportsWebSearch = true
        )
    )

    /** 按 id 查模型；未知 id / 旧数据（null）兜底为"自定义"。 */
    fun byId(id: String?): AiModelInfo = ALL.firstOrNull { it.id == id } ?: ALL.first()
}
