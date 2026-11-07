package com.semi.watchai.util

/**
 * 全局功能开关（单点控制，便于整体开关某个功能）。
 */
object FeatureFlags {

    /**
     * 流式输出总开关。
     *
     * `false` = 全局关闭：
     * - `ChatActivity` 的 AI 请求走一次性非流式 `chatCompletion()`：发送后列表尾部立即出现
     *   一个与 AI 气泡同款的"正在思考…"气泡，回复到达后**原地替换**为真实回复
     *   （见 `MessageAdapter.showThinking / replaceThinkingWith`）；
     * - 设置页里的"流式输出"开关行隐藏（`AiSettingsFragment`），避免出现无效开关。
     *
     * 数据库里的 `chats.streamEnabled` 列与 `stream_enabled` 全局设置**保持原值不动**。
     * 注意：`ChatActivity` 里的流式渲染路径已随本开关一起删除（需要时按 git 历史恢复），
     * 因此把本常量改回 `true` 只会恢复设置页开关，并不会恢复流式渲染。
     *
     * 关闭原因：流式输出当前存在逐字节奏、思考链/正文切换、长文本滚动错位等问题，
     * 尚不具备使用价值；且与"公式等富文本渲染"（需要整段文本才能确定排版）冲突。
     */
    const val STREAMING_ENABLED = false
}
