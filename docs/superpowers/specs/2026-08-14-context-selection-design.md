# 上下文选择功能设计

日期：2026-08-14
状态：已获用户批准

## 背景

当前 AI 对话每次请求都会把**整个对话历史** + 系统提示词发送给供应商。用户希望在两个设置页（首页全局设置页 `SettingsActivity`、对话设置页 `ChatSettingsActivity`）添加一个下拉框，控制发送给 AI 的上下文范围，以节省 token 并控制对话"记忆"程度。

## 功能语义

三个选项（内部存储为枚举字符串）：

| 选项 | 存储值 | 发送给 AI 的内容 |
|------|--------|------------------|
| 包含全部上下文 | `"all"` | 系统提示词 + 全部历史 + 当前消息（默认，保持现有行为） |
| 只包含上条上下文 | `"last"` | 系统提示词 + 当前消息 + 最近一轮完整问答（user + assistant 配对） |
| 不包含上下文 | `"none"` | 系统提示词 + 仅当前消息 |

说明：
- 系统提示词在三种模式下都发送。
- 当前消息本身总是发送。
- `"last"` 若历史中没有完整的 user+assistant 轮（例如全新对话首条消息），回退为仅当前消息。

## 存储方式：双层模式（与现有 provider/prompt/modelOnline 一致）

- **全局默认值**：DataStore `settings`（`ProviderRepository`），新增 key `context_mode`，默认 `"all"`。
- **每对话覆盖**：Room `chats` 表新增列 `contextMode TEXT DEFAULT NULL`（null = 未单独设置，回退全局）。
- **生效值** = `chat.contextMode ?? 全局默认值`。

## 数据层改动

### AppDatabase.kt

新增迁移 `MIGRATION_3_4`，版本升到 4：

```sql
ALTER TABLE chats ADD COLUMN contextMode TEXT DEFAULT NULL
```

`@Database(version = 4)`，迁移列表加入 `MIGRATION_3_4`。

### ChatEntity.kt

`ChatEntity` 新增字段：

```kotlin
var contextMode: String? = null
```

### Chat.kt

`Chat` 数据类新增字段：

```kotlin
var contextMode: String? = null
```

### ChatMapper.kt

`toChat` / `toEntity` 贯通 `contextMode` 字段。

### ChatDao.kt

无改动（`@Update` 已覆盖整行）。

### ChatRepository.kt

- `getAllChatsFlow` / `getChat` 映射时带上 `contextMode`。
- `createChat` 新增 `contextMode: String? = null` 参数并透传。
- `updateChatSettings` 新增 `contextMode: String? = null` 参数（非 null 才更新）。

### ProviderRepository.kt

新增：

```kotlin
companion object {
    private val KEY_CONTEXT_MODE = stringPreferencesKey("context_mode")
    const val CONTEXT_MODE_ALL = "all"
    const val CONTEXT_MODE_LAST = "last"
    const val CONTEXT_MODE_NONE = "none"
}

suspend fun contextMode(): String =
    context.providerDataStore.data.first()[KEY_CONTEXT_MODE] ?: CONTEXT_MODE_ALL

suspend fun setContextMode(mode: String) {
    context.providerDataStore.edit { it[KEY_CONTEXT_MODE] = mode }
}
```

## UI 层改动

### fragment_ai_settings.xml

在"系统提示词"区块之后、"供应商"区块之前，新增一行"上下文选择"标签 + Spinner：

```xml
<TextView android:text="上下文选择" ... />
<Spinner android:id="@+id/spinnerContextMode" ... />
```

Spinner 复用现有 `spinner_item` 布局与 `spinnerProvider` 的样式。

### AiSettingsFragment.kt

- 新增 `initialContextMode: String` 参数（`newInstance` 与 `ARG_CONTEXT_MODE`）。
- 新增 `Listener.onContextModeSelected(mode: String)` 回调。
- 新增 `spinnerContextMode` 绑定，适配器用三个选项名（"包含全部上下文" / "只包含上条上下文" / "不包含上下文"），按 `initialContextMode` 恢复选中，`restoringSelection` 抑制初始化误触发。
- 选中变化时回调 `listener?.onContextModeSelected(mode)`。

### SettingsActivity.kt

实现新回调：

```kotlin
override fun onContextModeSelected(mode: String) {
    lifecycleScope.launch {
        providerRepository.setContextMode(mode)
    }
}
```

创建 Fragment 时传入 `providerRepository.contextMode()`。

### ChatSettingsActivity.kt

- 解析默认值：`val contextMode = chat.contextMode ?: providerRepository.contextMode()`。
- 创建 Fragment 时传入 `contextMode`，`allowManagement = false`。
- 实现新回调，写回该对话：

```kotlin
override fun onContextModeSelected(mode: String) {
    lifecycleScope.launch {
        chatRepository.updateChatSettings(chatId, contextMode = mode)
    }
}
```

## 发送时应用

### ChatActivity.kt `requestAIResponse()`

1. 解析生效值：`val contextMode = fresh?.contextMode ?: providerRepository.contextMode()`。
2. 纳入现有"快照写回"逻辑：若本次解析用到了全局回退值（`fresh?.contextMode != contextMode`），则把生效值写回该对话。
3. 按生效值裁剪后再调用 `DeepSeekApi.chatCompletion`：

```kotlin
val requestMessages = when (contextMode) {
    ProviderRepository.CONTEXT_MODE_NONE -> listOf(chat.messages.last())
    ProviderRepository.CONTEXT_MODE_LAST -> lastRoundMessages(chat.messages)
    else -> chat.messages
}
```

裁剪辅助函数（局部私有函数）：

- `lastRoundMessages(messages)`：当前消息为 `messages.last()`（用户消息已入列）。从 `len-2` 起向前扫描，找最近一条 **当前消息之前** 的 user 消息（即上一轮用户提问）：
  - 找到（索引 i）：返回 `messages[i..]`——上一轮 user 提问 + 其后的 assistant 回复 + 当前消息。
  - 未找到（当前是首条消息）：返回仅 `[messages.last()]`（当前消息）。
  - 即使上一轮 user 消息后没有 assistant 回复（极端情况），`messages[i..]` 仍包含当前消息，请求不失败。

注意：调用时机在用户消息已加入 `chat.messages` 之后，`chat.messages.last()` 即当前消息。

## 测试/验证

- Room 迁移：从 v3 数据库升级到 v4 不丢数据（`contextMode` 默认 NULL → 回退全局）。
- 三种模式各发一条消息验证发送内容。
- 对话设置页改动只影响该对话；首页改动作为新对话默认值。
- 全新对话（无 assistant 回复）选择 `"last"` 时退化为仅当前消息，请求不失败。

## 边界情况

- 老用户升级：已有 chats 行 `contextMode` 为 NULL，自动回退全局 `"all"`，行为不变。
- `"last"` 模式在对话仅一条消息（首条）时退化为仅当前消息。
- 快照写回只发生在"解析用到全局回退值"时，与现有逻辑一致。
