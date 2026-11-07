package com.semi.watchai.util

/**
 * 全局功能开关（单点控制，便于整体开关某个功能而不删代码）。
 */
object FeatureFlags {

    /**
     * 流式输出总开关。
     *
     * `false` = 全局关闭：
     * - `ChatActivity` 的 AI 请求走一次性非流式 `chatCompletion()`（仍保留"正在思考…"占位，
     *   回复到达后一次性插入消息列表）；
     * - 设置页里的"流式输出"开关行隐藏（`AiSettingsFragment`），避免出现无效开关。
     *
     * 数据库里的 `chats.streamEnabled` 列与 `stream_enabled` 全局设置**保持原值不动**，
     * 因此把本常量改回 `true` 即可完整恢复流式功能，无需迁移或数据修补。
     *
     * 关闭原因：流式输出当前存在逐字节奏、思考链/正文切换、长文本滚动错位等问题，
     * 尚不具备使用价值；且与"公式等富文本渲染"（需要整段文本才能确定排版）冲突。
     */
    const val STREAMING_ENABLED = false
}
