# WatchAI ⌚

> 跑在 Wear OS 手表上的 AI 对话客户端 —— **独立运行，不依赖手机**。

![Platform](https://img.shields.io/badge/platform-Wear%20OS%203%2B-black)
![minSdk](https://img.shields.io/badge/minSdk-29-green)
![version](https://img.shields.io/badge/version-2.1.0-blue)
![License](https://img.shields.io/badge/license-MIT-green)
![Kotlin](https://img.shields.io/badge/Kotlin-2.3.20-7F52FF)
![Tests](https://img.shields.io/badge/unit%20tests-96%20passed-brightgreen)

WatchAI 是一个原生 Android 手表应用：抬手就能问 AI，支持任意 OpenAI 兼容接口的模型供应商。
所有数据（对话记录、供应商配置、提示词）都留在手表本地，请求直接从手表发往你选择的供应商。

<!--
界面预览：把截图放到仓库根目录的 screenshots/ 后取消下面注释（docs/ 目前被 .gitignore 排除，不要放那里）
| 对话                                         | 数学公式                                     | 设置                                             |
| -------------------------------------------- | -------------------------------------------- | ------------------------------------------------ |
| <img src="screenshots/chat.png" width="200"> | <img src="screenshots/math.png" width="200"> | <img src="screenshots/settings.png" width="200"> |
-->

## 功能特性

### 对话

- **手表独立运行**：声明 `com.google.android.wearable.standalone`，无需配对手机或手机端 App。
- **多轮对话，本地持久化**：对话与消息存于 Room 数据库；首页可新建、重命名、删除对话。
- **「重新加载」按钮**（v2.1.0 新增）：每个用户气泡下方都有一枚 ↻ 图标。点它会**删除该消息之后的全部消息并重新发送这一条**，用来重新生成不满意的回复（相当于「从这一步重来」）。
- **表冠滚轮**：列表滚动由 Activity 层统一接管，无论焦点在输入框还是消息列表，旋转表冠都能滚动对话。
- **「正在思考…」占位**：发送后列表尾部立即出现一个与 AI 气泡同款的占位气泡，回复到达时**原地替换**（无插入/移除动画闪烁）。

### AI 与设置

- **任意 OpenAI 兼容供应商**：填 Base URL + API Key 即可。内置 DeepSeek 预设（自带 Base URL 与模型名），也可选「自定义」手填。
- **两层设置模型**：首页「默认设置」只影响**新建**对话；进入某个对话后可单独调整该对话的供应商 / 提示词  / 上下文模式，改动只作用于这一个对话（新建时把默认值快照进该对话，此后首页改动不再影响它）。
- **系统提示词**：内置「默认」提示词（不可编辑、恒可用），也可自建多条并选用。
- **上下文模式**（省 token / 控制记忆）：

  | 模式                   | 发送给 AI 的内容                     |
  | ---------------------- | ------------------------------------ |
  | 包含全部上下文（默认） | 系统提示词 + 全部历史 + 当前消息     |
  | 只包含上条上下文       | 系统提示词 + 最近一轮问答 + 当前消息 |
  | 不包含上下文           | 系统提示词 + 仅当前消息              |


### 富文本

- **LaTeX 数学公式**：AI 回复里的 `$...$` / `$$...$$` / `\(...\)` / `\[...\]` 不会以裸源码出现。
  - 先由 `MathTextConverter` 把公式降级成可读的 Unicode 纯文本（`\frac{a}{b}` → `a/b`、`\sqrt{x}` → `√(x)`）；
  - 再由 [RaTeX](https://github.com/erweixin/RaTeX) 对**已定稿消息**做真正的二维排版：行内放得下的公式走 span，放不下的公式独占一行并支持横向拖动查看；
  - 代码块与行内代码受保护，原样保留（「价格 $5 和 $10」不会被误判成公式）。

## 技术栈

| 层次        | 选型                                                                                      |
| ----------- | ----------------------------------------------------------------------------------------- |
| 语言 / 构建 | Kotlin 2.3.20、AGP 9.3.1、Gradle 9.5.0、Java 11                                           |
| 平台        | minSdk 29（Wear OS 3+）、compileSdk / targetSdk 37、ABI：arm64-v8a / armeabi-v7a / x86_64 |
| UI          | AppCompat + RecyclerView（无 Compose）                                                    |
| 数据        | Room（对话与消息）、DataStore Preferences（全局设置、供应商、提示词）                     |
| 网络        | OkHttp + Gson，直连供应商的 OpenAI 兼容 `/chat/completions`                               |
| 公式渲染    | 自研 `MathTextConverter`（Unicode 降级）+ `io.github.erweixin:ratex-android`              |


## 快速开始

### 环境要求

- Android Studio（自带 JDK，用于编译）
- 一块 Wear OS 3+ 手表，或 Android Studio 里的 Wear OS 模拟器

### 构建与安装

```bash
git clone https://github.com/semiconductor314/WatchAI.git
cd WatchAI
```

用 Android Studio 打开工程直接 Run 即可。命令行构建（Windows）需指定 Android Studio 的 JDK：

```bash
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

> 调试包的 `applicationId` 带 `.debug` 后缀（`com.semi.watchai.debug`），可与正式版共存安装；两版数据目录互相独立。

### 首次配置

1. 打开 App → 首页「其他 >」→「模型库」新增供应商：填名称、Base URL（如 `https://api.deepseek.com`）、API Key，并选择模型预设（不选预设即为自定义，手填模型名）。
2. 回到首页「默认设置」，选择供应商、提示词、上下文模式与联网开关（这些是**新建对话**的默认值）。
3. 首页点「新建对话」开始聊天；对话内点「设置 >」可单独调整这一个对话的参数。

API Key 只保存在手表本地 DataStore 中，不会上传到任何第三方服务器（请求直接发给你选择的供应商）。

## 项目结构

```
app/src/main/java/com/semi/watchai/
├── MainActivity.kt              首页宿主（对话列表 Fragment）
├── data/
│   ├── local/                   Room 数据库、DAO、实体
│   ├── model/                   Chat / Message / Prompt / AiProvider / 模型注册表
│   └── repository/              ChatRepository、ProviderRepository、PromptRepository
├── network/DeepSeekApi.kt       OpenAI 兼容请求与 SSE 解析
├── ui/
│   ├── home/                    对话列表
│   ├── chat/                    对话页、对话设置页、气泡 Adapter、公式渲染
│   ├── settings/                默认设置、模型库、系统提示词、其他
│   └── about/                   关于
└── util/                        上下文裁剪、LaTeX 转换、消息截断、功能开关
```

分层约定：纯逻辑（可 JVM 单测）放 `util`，UI 放 `ui`，转换/渲染结果**绝不写回数据库**（DB 里始终是模型返回的原文）。


## 版本历史

| 版本  | 变更                                                                                           |
| ----- | ---------------------------------------------------------------------------------------------- |
| 2.1.0 | 每个用户气泡下方新增「重新加载」按钮：删除其后消息并重发该条消息                               |
| 2.0.0 | LaTeX 公式渲染（Unicode 降级 + RaTeX 二维排版）、发送后收起输入法、「正在思考…」改为列表内气泡 |
| 1.x   | 多供应商 / 多提示词 / 上下文模式 / 对话管理                                                    |

## 隐私

- 仅申请 `INTERNET` 权限。
- 无自建后端、无遥测、无统计 SDK。
- 对话记录、供应商配置、API Key 均存于本机；卸载或清除应用数据即彻底删除。

## 致谢

- [RaTeX](https://github.com/erweixin/RaTeX) —— 手表上可用的 LaTeX 二维排版库。
- [Material Icons](https://fonts.google.com/icons) —— 应用内图标（Apache License 2.0）。

## 许可证

本项目基于 [MIT License](LICENSE) 开源。

```
MIT License

Copyright (c) 2026 semiconductor314
```

你可以自由使用、修改、分发本项目（包括商用），只需在副本或实质部分中保留上述版权声明与许可声明。
