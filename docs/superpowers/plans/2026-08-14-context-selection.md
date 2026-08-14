# 上下文选择功能实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在两个设置页添加"上下文选择"下拉框（包含全部上下文 / 只包含上条上下文 / 不包含上下文），控制发送给 AI 的消息范围。

**Architecture:** 沿用现有"全局默认值(DataStore) + 每对话覆盖(chats 表列)"双层模式。新增 Room 迁移 v3→v4 给 chats 表加 `contextMode` 列；`ProviderRepository` 新增全局 `context_mode` key。共享 `AiSettingsFragment` 加一个 Spinner + Listener 回调，两个宿主 Activity 各自持久化。核心裁剪逻辑提取为纯函数对象 `MessageContextFilter`，便于 JUnit 单元测试（此为对设计文档"局部私有函数"的细化，便于 TDD）。

**Tech Stack:** Kotlin, Room (KSP), DataStore Preferences, Gson, JUnit（本地单元测试）。

## Global Constraints

- **构建 JDK**：命令行构建必须设 `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"`（本机 PATH 上的 java 是 JDK 8，会导致 toolchain 下载失败）。见 memory `jdk25-toolchain-build`。
- **存储值**（精确字符串）：`"all"` / `"last"` / `"none"`，以 `ProviderRepository.CONTEXT_MODE_ALL/LAST/NONE` 常量引用，禁止散落字符串字面量。
- **UI 文案**（精确）：`包含全部上下文` / `只包含上条上下文` / `不包含上下文`。
- **Room 版本**：3 → 4，新增 `MIGRATION_3_4`：`ALTER TABLE chats ADD COLUMN contextMode TEXT DEFAULT NULL`。
- **语义**：系统提示词三种模式都发送；`"last"` 找不到上一轮 user 消息时退化为仅当前消息。
- **不改 DeepSeekApi 签名**；裁剪发生在 `ChatActivity` 调用前。
- **代码风格**：沿用中文注释、KDoc；新增类写文档注释。

---

### Task 1: 数据层贯通 contextMode（实体/映射/仓库/迁移）

**Files:**
- Modify: `app/src/main/java/com/semi/watchai/Chat.kt:14`（promptId 后加字段）
- Modify: `app/src/main/java/com/semi/watchai/ChatEntity.kt:16`（promptId 后加字段）
- Modify: `app/src/main/java/com/semi/watchai/ChatMapper.kt`
- Modify: `app/src/main/java/com/semi/watchai/ChatRepository.kt`
- Modify: `app/src/main/java/com/semi/watchai/AppDatabase.kt`
- Test: `app/src/test/java/com/semi/watchai/ChatMapperTest.kt`（新建）

**Interfaces:**
- Consumes: 现有 `Chat`、`ChatEntity`、`ChatMapper`、`ChatRepository`、`AppDatabase`。
- Produces: `Chat.contextMode: String?`、`ChatEntity.contextMode: String?`、`ChatRepository.createChat(..., contextMode: String? = null)`、`ChatRepository.updateChatSettings(..., contextMode: String? = null)`、`AppDatabase` version 4。

- [ ] **Step 1: 写失败测试 ChatMapperTest.kt**

```kotlin
package com.semi.watchai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatMapperTest {

    @Test
    fun toChat_preservesContextMode() {
        val entity = ChatEntity(
            id = "c1", title = "t",
            providerId = "p1", modelOnline = true, promptId = "prompt1",
            contextMode = "last"
        )
        val chat = ChatMapper.toChat(entity, emptyList())
        assertEquals("last", chat.contextMode)
        assertEquals("p1", chat.providerId)
        assertEquals(true, chat.modelOnline)
    }

    @Test
    fun toEntity_preservesContextMode() {
        val chat = Chat(
            id = "c1", title = "t",
            providerId = "p1", modelOnline = true, promptId = "prompt1",
            contextMode = "none"
        )
        val entity = ChatMapper.toEntity(chat)
        assertEquals("none", entity.contextMode)
    }

    @Test
    fun contextMode_defaultsToNull() {
        val entity = ChatEntity(id = "c1", title = "t")
        assertNull(entity.contextMode)
        val chat = Chat(id = "c1", title = "t")
        assertNull(chat.contextMode)
    }
}
```

- [ ] **Step 2: 运行测试确认失败（编译失败即失败）**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest --tests "com.semi.watchai.ChatMapperTest"`
Expected: 编译报错 `Unresolved reference: contextMode`。

- [ ] **Step 3: 实现数据层**

`Chat.kt` 的 `promptId` 之后加：

```kotlin
    // 每对话上下文选择（null = 未单独设置，回退到全局设置）
    var contextMode: String? = null
```

`ChatEntity.kt` 的 `promptId` 之后加：

```kotlin
    // 每对话上下文选择（null = 未单独设置，回退到全局设置）
    var contextMode: String? = null
```

`ChatMapper.kt` `toChat` 的构造加 `contextMode = entity.contextMode`，`toEntity` 加 `contextMode = chat.contextMode`。

`ChatRepository.kt` 三处：
- `getAllChatsFlow` 的 `Chat(...)` 构造加 `contextMode = entity.contextMode`。
- `getChat` 的 `Chat(...)` 构造加 `contextMode = entity.contextMode`。
- `createChat` 签名加 `contextMode: String? = null`，`Chat(...)` 与 `ChatEntity(...)` 都透传。
- `updateChatSettings` 签名加 `contextMode: String? = null`，函数体内加 `if (contextMode != null) entity.contextMode = contextMode`。

`AppDatabase.kt`：
- `@Database(version = 3)` → `version = 4`。
- 新增迁移并在 `addMigrations` 中加入：

```kotlin
// v3 -> v4：chats 表新增每对话上下文选择列
private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE chats ADD COLUMN contextMode TEXT DEFAULT NULL")
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest --tests "com.semi.watchai.ChatMapperTest"`
Expected: 3 个测试全部 PASS。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/semi/watchai/Chat.kt \
        app/src/main/java/com/semi/watchai/ChatEntity.kt \
        app/src/main/java/com/semi/watchai/ChatMapper.kt \
        app/src/main/java/com/semi/watchai/ChatRepository.kt \
        app/src/main/java/com/semi/watchai/AppDatabase.kt \
        app/src/test/java/com/semi/watchai/ChatMapperTest.kt
git commit -m "feat: chats 表新增 contextMode 列（Room v3->v4 迁移）"
```

---

### Task 2: ProviderRepository 全局 contextMode

**Files:**
- Modify: `app/src/main/java/com/semi/watchai/ProviderRepository.kt`

**Interfaces:**
- Consumes: 现有 `providerDataStore`。
- Produces: `ProviderRepository.CONTEXT_MODE_ALL/LAST/NONE: String`（const）、`suspend fun contextMode(): String`、`suspend fun setContextMode(mode: String)`。

- [ ] **Step 1: 加常量与读写方法**

`companion object` 内加：

```kotlin
private val KEY_CONTEXT_MODE = stringPreferencesKey("context_mode")

const val CONTEXT_MODE_ALL = "all"
const val CONTEXT_MODE_LAST = "last"
const val CONTEXT_MODE_NONE = "none"
```

类内 `setModelOnline` 之后加：

```kotlin
/** 全局"上下文选择"默认值（每对话可单独覆盖）。 */
suspend fun contextMode(): String =
    context.providerDataStore.data.first()[KEY_CONTEXT_MODE] ?: CONTEXT_MODE_ALL

/** 设置全局"上下文选择"默认值。 */
suspend fun setContextMode(mode: String) {
    context.providerDataStore.edit { it[KEY_CONTEXT_MODE] = mode }
}
```

- [ ] **Step 2: 编译验证**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/semi/watchai/ProviderRepository.kt
git commit -m "feat: ProviderRepository 新增全局 contextMode 读写"
```

---

### Task 3: 纯函数 MessageContextFilter + 单元测试（TDD 核心逻辑）

**Files:**
- Create: `app/src/main/java/com/semi/watchai/MessageContextFilter.kt`
- Test: `app/src/test/java/com/semi/watchai/MessageContextFilterTest.kt`

**Interfaces:**
- Consumes: `Message`（现有数据类）、`ProviderRepository.CONTEXT_MODE_*` 常量。
- Produces: `object MessageContextFilter` 的 `fun filter(messages: List<Message>, mode: String): List<Message>`。

- [ ] **Step 1: 写失败测试 MessageContextFilterTest.kt**

```kotlin
package com.semi.watchai

import org.junit.Assert.assertEquals
import org.junit.Test

class MessageContextFilterTest {

    private fun msg(content: String, isUser: Boolean) =
        Message(content = content, isUser = isUser)

    @Test
    fun all_mode_returns_all_messages() {
        val messages = listOf(msg("u1", true), msg("a1", false), msg("u2", true))
        assertEquals(
            messages,
            MessageContextFilter.filter(messages, ProviderRepository.CONTEXT_MODE_ALL)
        )
    }

    @Test
    fun none_mode_returns_only_current_message() {
        val messages = listOf(msg("u1", true), msg("a1", false), msg("u2", true))
        assertEquals(
            listOf(messages.last()),
            MessageContextFilter.filter(messages, ProviderRepository.CONTEXT_MODE_NONE)
        )
    }

    @Test
    fun last_mode_returns_previous_round_plus_current() {
        val messages = listOf(
            msg("u1", true), msg("a1", false),
            msg("u2", true), msg("a2", false),
            msg("u3", true)
        )
        assertEquals(
            listOf(messages[2], messages[3], messages[4]),
            MessageContextFilter.filter(messages, ProviderRepository.CONTEXT_MODE_LAST)
        )
    }

    @Test
    fun last_mode_with_no_assistant_reply_returns_from_last_user() {
        val messages = listOf(msg("u1", true), msg("u2", true))
        assertEquals(
            listOf(messages[0], messages[1]),
            MessageContextFilter.filter(messages, ProviderRepository.CONTEXT_MODE_LAST)
        )
    }

    @Test
    fun last_mode_with_single_message_returns_just_it() {
        val messages = listOf(msg("u1", true))
        assertEquals(
            listOf(messages[0]),
            MessageContextFilter.filter(messages, ProviderRepository.CONTEXT_MODE_LAST)
        )
    }

    @Test
    fun filter_on_empty_list_returns_empty() {
        assertEquals(
            emptyList<Message>(),
            MessageContextFilter.filter(emptyList(), ProviderRepository.CONTEXT_MODE_NONE)
        )
        assertEquals(
            emptyList<Message>(),
            MessageContextFilter.filter(emptyList(), ProviderRepository.CONTEXT_MODE_LAST)
        )
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest --tests "com.semi.watchai.MessageContextFilterTest"`
Expected: 编译报错 `Unresolved reference: MessageContextFilter`。

- [ ] **Step 3: 实现 MessageContextFilter.kt**

```kotlin
package com.semi.watchai

/**
 * 按"上下文选择"模式裁剪发送给 AI 的消息列表（纯函数，便于单元测试）。
 *
 * 语义（与设计文档一致）：
 * - CONTEXT_MODE_ALL  ("all") ：返回全部消息（默认行为）。
 * - CONTEXT_MODE_LAST ("last")：当前消息 + 其前最近一轮完整问答（上一轮 user + 之后 assistant 回复）。
 * - CONTEXT_MODE_NONE ("none")：仅当前消息（最后一条）。
 */
object MessageContextFilter {

    /**
     * 按 [mode] 裁剪消息列表。调用方保证 [messages] 至少含当前消息；
     * 为空时安全回退为空列表。
     */
    fun filter(messages: List<Message>, mode: String): List<Message> {
        if (messages.isEmpty()) return emptyList()
        return when (mode) {
            ProviderRepository.CONTEXT_MODE_NONE -> listOf(messages.last())
            ProviderRepository.CONTEXT_MODE_LAST -> lastRound(messages)
            else -> messages
        }
    }

    /**
     * 当前消息（最后一条）之前最近一轮完整问答 + 当前消息。
     * 规则：从倒数第二起向前扫描最近的 user 消息（上一轮提问），从它开始截取；
     * 找不到（当前是首条消息）时仅返回当前消息。
     */
    private fun lastRound(messages: List<Message>): List<Message> {
        val currentIndex = messages.lastIndex
        for (i in currentIndex - 1 downTo 0) {
            if (messages[i].isUser) {
                return messages.subList(i, messages.size)
            }
        }
        return listOf(messages.last())
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest --tests "com.semi.watchai.MessageContextFilterTest"`
Expected: 6 个测试全部 PASS。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/semi/watchai/MessageContextFilter.kt \
        app/src/test/java/com/semi/watchai/MessageContextFilterTest.kt
git commit -m "feat: MessageContextFilter 按上下文模式裁剪消息（含单元测试）"
```

---

### Task 4: 设置页布局加"上下文选择"下拉

**Files:**
- Modify: `app/src/main/res/layout/fragment_ai_settings.xml`（`btnAddPrompt` 之后、"选择用于 AI 对话的接口"标题之前）

**Interfaces:**
- Produces: `R.id.spinnerContextMode`。

- [ ] **Step 1: 插入下拉行**

在 `android:id="@+id/btnAddPrompt"` 按钮之后、"供应商区"注释块之前插入：

```xml
        <!-- 上下文选择：下拉框 -->
        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="上下文选择"
            android:textColor="#888888"
            android:textSize="12sp"
            android:layout_marginTop="24dp"
            android:layout_marginBottom="8dp" />

        <Spinner
            android:id="@+id/spinnerContextMode"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:popupBackground="#10141D" />
```

- [ ] **Step 2: 编译验证**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew processDebugResources`
Expected: BUILD SUCCESSFUL（XML 布局资源链接通过；`compileDebugKotlin` 不处理资源，故用 process 任务验证）。

- [ ] **Step 3: 提交**

```bash
git add app/src/main/res/layout/fragment_ai_settings.xml
git commit -m "feat: 设置页布局新增上下文选择下拉框"
```

---

### Task 5: AiSettingsFragment 接上下文 Spinner

**Files:**
- Modify: `app/src/main/java/com/semi/watchai/AiSettingsFragment.kt`

**Interfaces:**
- Consumes: `ProviderRepository.CONTEXT_MODE_*`、`R.id.spinnerContextMode`、`R.layout.spinner_item`。
- Produces: `Listener.onContextModeSelected(mode: String)`、`newInstance(..., contextMode: String = ProviderRepository.CONTEXT_MODE_ALL)`、私有 `contextModeOptions: List<Pair<String, String>>`。

- [ ] **Step 1: 扩展 Listener 接口与字段**

`interface Listener` 内加（**默认空实现**，避免破坏未更新的宿主 Activity——Task 6/7 再真正 override）：

```kotlin
fun onContextModeSelected(mode: String) {}
```

字段区加：

```kotlin
private lateinit var spinnerContextMode: Spinner

// 上下文选择选项（显示名 -> 存储值），顺序即下拉顺序
private val contextModeOptions = listOf(
    "包含全部上下文" to ProviderRepository.CONTEXT_MODE_ALL,
    "只包含上条上下文" to ProviderRepository.CONTEXT_MODE_LAST,
    "不包含上下文" to ProviderRepository.CONTEXT_MODE_NONE
)

private var initialContextMode = ProviderRepository.CONTEXT_MODE_ALL
```

- [ ] **Step 2: 解析参数并绑定**

`onCreateView` 的 `arguments?.let` 内加：

```kotlin
initialContextMode = it.getString(ARG_CONTEXT_MODE) ?: ProviderRepository.CONTEXT_MODE_ALL
```

绑定控件处加 `spinnerContextMode = view.findViewById(R.id.spinnerContextMode)`，并在 `loadProviders()` 前调用 `refreshContextModeSpinner()`、注册监听。

新增方法：

```kotlin
private fun refreshContextModeSpinner() {
    val adapter = ArrayAdapter(
        requireContext(),
        R.layout.spinner_item,
        contextModeOptions.map { it.first }
    )
    adapter.setDropDownViewResource(R.layout.spinner_item)

    // 恢复选中时不触发保存逻辑（onItemSelected 同步回调）
    restoringSelection = true
    spinnerContextMode.adapter = adapter
    val index = contextModeOptions.indexOfFirst { it.second == initialContextMode }
    if (index >= 0) {
        spinnerContextMode.setSelection(index)
    }
    restoringSelection = false
}
```

监听（与供应商 Spinner 同款）：

```kotlin
spinnerContextMode.onItemSelectedListener =
    object : android.widget.AdapterView.OnItemSelectedListener {
        override fun onItemSelected(
            parent: android.widget.AdapterView<*>?,
            view: android.view.View?,
            position: Int,
            id: Long
        ) {
            if (restoringSelection) return
            if (position in contextModeOptions.indices) {
                listener?.onContextModeSelected(contextModeOptions[position].second)
            }
        }

        override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
    }
```

- [ ] **Step 3: 更新 newInstance 与常量**

`companion object` 加 `private const val ARG_CONTEXT_MODE = "arg_context_mode"`；`newInstance` 签名加 `contextMode: String = ProviderRepository.CONTEXT_MODE_ALL`，`putString(ARG_CONTEXT_MODE, contextMode)`。

- [ ] **Step 4: 编译验证**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/semi/watchai/AiSettingsFragment.kt
git commit -m "feat: AiSettingsFragment 新增上下文选择下拉与回调"
```

---

### Task 6: 全局设置页接线

**Files:**
- Modify: `app/src/main/java/com/semi/watchai/SettingsActivity.kt`

**Interfaces:**
- Consumes: `AiSettingsFragment.newInstance(..., contextMode)`、`providerRepository.contextMode()` / `setContextMode()`。

- [ ] **Step 1: 传初始值 + 实现回调**

`onCreate` 的 `lifecycleScope.launch` 内加载默认值后加 `val contextMode = providerRepository.contextMode()`，并把 `newInstance(selectedId, modelOnline, promptId, true)` 改为 `newInstance(selectedId, modelOnline, promptId, true, contextMode)`。

类内加：

```kotlin
override fun onContextModeSelected(mode: String) {
    lifecycleScope.launch {
        providerRepository.setContextMode(mode)
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/semi/watchai/SettingsActivity.kt
git commit -m "feat: 全局设置页持久化上下文选择到 DataStore"
```

---

### Task 7: 对话设置页接线

**Files:**
- Modify: `app/src/main/java/com/semi/watchai/ChatSettingsActivity.kt`

**Interfaces:**
- Consumes: `chat.contextMode`、`providerRepository.contextMode()`、`chatRepository.updateChatSettings(chatId, contextMode=...)`、`AiSettingsFragment.newInstance(..., contextMode, allowManagement=false)`。

- [ ] **Step 1: 解析默认值 + 实现回调**

`onCreate` 的 `lifecycleScope.launch` 内，`val promptId = ...` 之后加 `val contextMode = chat.contextMode ?: providerRepository.contextMode()`，并把 `newInstance(providerId, modelOnline, promptId, false)` 改为 `newInstance(providerId, modelOnline, promptId, false, contextMode)`。

类内加：

```kotlin
override fun onContextModeSelected(mode: String) {
    lifecycleScope.launch {
        chatRepository.updateChatSettings(chatId, contextMode = mode)
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/semi/watchai/ChatSettingsActivity.kt
git commit -m "feat: 对话设置页持久化上下文选择到该对话"
```

---

### Task 8: ChatActivity 发送时按模式裁剪

**Files:**
- Modify: `app/src/main/java/com/semi/watchai/ChatActivity.kt`（`requestAIResponse`）

**Interfaces:**
- Consumes: `fresh?.contextMode`、`providerRepository.contextMode()`、`chatRepository.updateChatSettings(..., contextMode=...)`、`MessageContextFilter.filter(messages, mode)`。

- [ ] **Step 1: 解析生效值并纳入快照写回**

在 `requestAIResponse` 中 `val systemPrompt = ...` 之前加：

```kotlin
// 上下文选择：该对话已存 ?? 全局默认（"all"）
val contextMode = fresh?.contextMode ?: providerRepository.contextMode()
```

把现有快照写回条件改为包含 `fresh.contextMode != contextMode`，并把 `chatRepository.updateChatSettings(...)` 调用加 `contextMode = contextMode`：

```kotlin
if (fresh != null &&
    (fresh.providerId != provider.id ||
        fresh.modelOnline != modelOnline ||
        fresh.promptId != promptId ||
        fresh.contextMode != contextMode)
) {
    chatRepository.updateChatSettings(
        chatId,
        providerId = provider.id,
        modelOnline = modelOnline,
        promptId = promptId,
        contextMode = contextMode
    )
}
```

- [ ] **Step 2: 裁剪后传入 DeepSeekApi**

把 `DeepSeekApi.chatCompletion(provider.baseUrl, provider.apiKey, chat.messages, modelOnline, systemPrompt)` 改为：

```kotlin
// 按上下文选择模式裁剪历史后再发送
val requestMessages = MessageContextFilter.filter(chat.messages, contextMode)
val reply = DeepSeekApi.chatCompletion(
    provider.baseUrl,
    provider.apiKey,
    requestMessages,
    modelOnline,
    systemPrompt
)
```

- [ ] **Step 3: 编译验证**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/semi/watchai/ChatActivity.kt
git commit -m "feat: ChatActivity 按上下文模式裁剪发送给 AI 的历史"
```

---

### Task 9: 全量验证

- [ ] **Step 1: 跑全部单元测试**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest`
Expected: 全部测试 PASS（ExampleUnitTest + ChatMapperTest + MessageContextFilterTest）。

- [ ] **Step 2: 完整编译**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL，产出 APK。

- [ ] **Step 3: 人工核对设计语义（无需代码改动，确认即可）**

- 老用户升级：已有 chats 行 `contextMode = NULL` → 回退全局 `"all"`，行为不变。
- 全新对话选 `"last"`：`MessageContextFilter.lastRound` 找不到上一轮 user，退化为仅当前消息。
- 三个设置入口都显示"上下文选择"下拉：首页设置页、对话设置页（隐藏管理按钮后仍可见）。

---

## 自审记录

- **Spec 覆盖**：语义三选项 ✅（Task 3、8）；Room v3→v4 迁移 ✅（Task 1）；DataStore 全局 key ✅（Task 2）；共享 Fragment UI ✅（Task 4、5）；全局/对话页接线 ✅（Task 6、7）；发送时应用 + 快照写回 ✅（Task 8）。边界情况在 Task 9 核对。
- **占位符检查**：所有步骤含实际代码或精确命令，无 TBD。
- **类型一致**：`contextMode` 字段名与 `ProviderRepository.CONTEXT_MODE_ALL/LAST/NONE` 常量在所有任务中一致；`newInstance` 参数顺序（providerId, modelOnline, promptId, allowManagement, contextMode）在 Task 5/6/7 中一致；`updateChatSettings(chatId, contextMode=...)` 命名参数一致。
- **对设计文档的一处细化**：裁剪逻辑从"ChatActivity 局部私有函数"改为独立纯函数对象 `MessageContextFilter`（Task 3），以获得可单元测试的独立单元；语义完全一致。
