# AI助手

Android 上的助手应用。可以和 DeepSeek、火山方舟（豆包）上的模型对话，也可以做文字翻译和同声翻译。语音输入、朗读和同传使用豆包语音。对话支持识图与（经豆包 Seedream）生图。

## 功能

侧边栏有三项：AI 对话、AI 翻译、设置。打开侧边栏时主界面会被推出，关闭时不能右滑打开，避免列表误触。

### AI 对话

- 多段对话保存在本机，用 Room 存储。
- 流式回复。助手消息可以展开查看思考内容，并支持轻量 Markdown。
- 顶栏可编辑**助手人设**（系统提示）：内置预设或自定义，保存后从下一条消息生效。
- 模型按服务商分组选择。打开设置或对话时可从服务商 **拉取最新模型列表**（本地缓存约 24 小时；方舟结果会按常用前缀筛选并取同系列最新版）。新建对话默认使用最近一次成功发送的模型；没有记录时使用设置里的默认模型。
- 同一段对话不能中途更换服务商。更换模型只影响下一条消息。纯生图（Seedream）模型不会出现在对话模型列表里。
- 目录之外的模型 ID 可以手动填写，界面会显示为「自定义」。未开通或已下线的方舟模型仍可能调用失败，可刷新目录后换一个。
- 点麦克风切换到语音模式，**按住说话、松开结束**；识别结果填入输入框后再发送。点键盘图标回到文字输入。

#### 图片：识图与生图

- **发图识图**：输入框旁选图（最多 4 张），随文字发给支持视觉的模型（如 DeepSeek Flash、豆包 Seed 等）。气泡内可点开展示。
- **AI 回图**：对话接口本身不直接吐图。生图统一走豆包 **Seedream**（`/images/generations`），需要已配置**豆包方舟 API Key**（即使用 DeepSeek 会话画图也一样）。
  - 打开画笔进入**画图模式**，描述画面后发送（可附参考图做图生图）。
  - 或输入明确画图意图（如「画一张…」「生成图片…」）。
  - 豆包对话还会挂 `generate_image` 工具，模型决定调用时由客户端执行 Seedream 并显示在气泡中。
- 历史里较新的用户附图会随上下文重放；更早的附图只保留文字，避免请求体过大。

### AI 翻译

同声翻译和文字翻译用顶部切换分开，记录也各自保存。右上角进入翻译记录。

#### 文字翻译

- 使用方舟上的 Seed Translation。输入或录音后自动翻译；也可点「翻译」立刻译并写入记录。
- **按住说话、松开结束**录音输入。键盘输入或录音识别后，会按文字脚本自动匹配源语言；若与目标语相同，则自动切到中↔英另一侧。
- 译文可以复制和朗读。记录里的「填入输入框」会把原文和译文放回编辑区。

#### 同声翻译

- 对着手机说，原文和译文边写边出，并由同传服务朗读译文。
- 音色可选女声、男声或复刻原声。播放译文时麦克风不再把这段声音送回去，避免同一句反复翻译。
- 进行中不能切换页面或改语种；系统返回会先结束同传。
- 要求源语言或目标语言是中文或英文。指定女声或男声时，目标语言只能是中文或英文。复刻原声支持中文、英文、德语、法语、西班牙语、印尼语、日语、葡萄牙语。粤语和上海话只能作为源语言，并译成中文或英文。

#### 翻译记录

- 入口在翻译页右上角；空闲时也可点「上次」预览打开最近一条。
- 列表按今天、昨天和日期分组。记录超过 6 条时可以搜索原文或译文。
- 点开一条可以看全文、复制、朗读；删除前会再确认一次。离开详情会停止朗读。

### 朗读

对话、文字翻译和翻译记录里的朗读，可以在设置中选「豆包语音」或「本机朗读」。豆包语音可选女声或男声。同声翻译的音色单独选择，不走这项设置。豆包朗读失败时，可以改为只对这一次使用本机朗读。

### 密钥

API Key 经 Android Keystore 加密后保存在本机，不会写入源码。

## 服务商

| 服务商 | 默认地址 | 说明 |
| --- | --- | --- |
| DeepSeek | `https://api.deepseek.com` | 官方对话密钥；部分模型支持识图，官方 Chat **不提供**文生图 |
| 豆包 | `https://ark.cn-beijing.volces.com/api/v3` | 方舟对话密钥，同时用于文字翻译、模型列表与 **Seedream 生图**。方舟上的 DeepSeek、GLM 也用这把密钥 |
| 豆包语音 | 豆包语音控制台 | 单独的语音密钥，用于识别、同声翻译和豆包朗读，不能用方舟对话密钥代替 |

语音识别使用已开通的「流式语音识别 2.0」。同声翻译使用同声传译 2.0。

## 环境

- Android 7.0（API 24）及以上
- JDK 17
- Android SDK，`compileSdk` / `targetSdk` 为 37

## 构建

在项目根目录执行：

```bash
./gradlew :app:assembleDebug
```

Windows 使用 `gradlew.bat`。调试包位于 `app/build/outputs/apk/debug/`。

首次打开后，在设置里分别保存 DeepSeek 和豆包的密钥。语音输入、同声翻译和豆包朗读还需要填写豆包语音密钥。识图与生图依赖对应模型能力及豆包方舟密钥（生图）。语音输入和同声翻译需要麦克风权限；选图使用系统照片选择器。

## 许可

[MIT License](LICENSE)

---

# AI Assistant

An Android assistant. It chats with DeepSeek and with models on Volcengine Ark (Doubao), and it translates typed text and live speech. Voice input, read-aloud, and simultaneous interpretation use Doubao Speech. Chat supports vision (image understanding) and image generation via Doubao Seedream.

## Features

The sidebar has three sections: AI Chat, AI Translation, and Settings. Opening the drawer pushes the main content aside; swipe-to-open is disabled while the drawer is closed so list scrolling is not interrupted.

### AI Chat

- Conversations are stored on the device with Room.
- Replies stream in. Assistant messages can expand their reasoning and render light Markdown.
- Edit the **assistant persona** (system prompt) from the chat app bar: presets or custom text; changes apply from the next message.
- Models are grouped by provider. Settings and chat can **refresh the model list** from each provider (cached about 24 hours; Ark results are filtered by common prefixes and the newest version in a family is kept). A new chat starts with the model that last sent successfully, or with the default from Settings when there is no history.
- The provider stays fixed for an existing conversation. A model change applies to the next message. Pure image-generation (Seedream) IDs are hidden from the chat model picker.
- A model ID outside the catalog can be entered manually and is shown as “Custom”. Unactivated or retired Ark models may still fail; refresh the list and pick another.
- Tap the microphone to enter voice mode, then **hold to talk and release to stop**. Recognized text fills the input field; send it when ready. Tap the keyboard icon to return to typing.

#### Images: vision and generation

- **Send images for vision**: Attach up to four photos next to the composer and send them with text to a vision-capable model (e.g. DeepSeek Flash, Doubao Seed). Tap a bubble image to preview.
- **AI-generated images**: Chat Completions do not return image bytes. Generation always uses Doubao **Seedream** (`/images/generations`) and needs a **Doubao Ark API key** (including when the chat provider is DeepSeek).
  - Turn on **draw mode** (brush), describe the scene, and send (optional reference images for image-to-image).
  - Or use a clear draw intent (e.g. “draw a…”, “generate an image…”).
  - Doubao chats also register a `generate_image` tool; when the model calls it, the app runs Seedream and shows the result in the bubble.
- Only recent user attachments are re-sent in context as base64; older ones keep text only so request bodies stay within limits.

### AI Translation

Simultaneous interpretation and text translation are separate tabs, and each keeps its own history. Open history from the top-right control.

#### Text translation

- Uses Seed Translation on Ark. Translation runs automatically after you pause typing, or immediately when you tap Translate (and then saves a record).
- **Hold to talk, release to stop** for voice input. After typing or recognition, the source language is inferred from the script; if it matches the target, the pair flips to the other side of Chinese ↔ English.
- Copy or read the result aloud. “Fill the input” on a history item restores that source and translation to the editor.

#### Simultaneous interpretation

- Speak to the phone; source and translation appear as you talk, and the interpretation service reads the translation aloud.
- Voice can be female, male, or a clone of the speaker. While the translation is playing, the microphone does not send that audio back, so the same sentence is not translated again.
- During a session you cannot switch tabs or change languages; system Back ends the session first.
- One side of a live pair must be Chinese or English. With a named voice, the target must be Chinese or English. Clone voice supports Chinese, English, German, French, Spanish, Indonesian, Japanese, and Portuguese. Cantonese and Shanghainese can only be the source, and only into Chinese or English.

#### Translation history

- Opened from the top-right control; when idle, a “Last” one-line preview can open the newest record.
- The list groups items by today, yesterday, and date. Search appears after more than six records.
- Opening a record shows the full text, with copy and read-aloud; delete asks for confirmation. Leaving the detail screen stops read-aloud.

### Read-aloud

Chat, text translation, and history can use Doubao Speech or the device’s own speech engine. Doubao Speech offers a female or male voice. The simultaneous-interpretation voice is chosen on that page and does not follow this setting. If Doubao read-aloud fails, that one utterance can fall back to the device engine.

### Keys

API keys are encrypted with the Android Keystore and are never stored in source.

## Providers

| Provider | Default endpoint | Notes |
| --- | --- | --- |
| DeepSeek | `https://api.deepseek.com` | Official chat key; some models support vision. Official Chat has **no** text-to-image endpoint |
| Doubao | `https://ark.cn-beijing.volces.com/api/v3` | Ark chat key for text translation, model listing, and **Seedream** image generation. Ark-hosted DeepSeek and GLM use the same key |
| Doubao Speech | Doubao Speech console | A separate speech key for recognition, simultaneous interpretation, and Doubao read-aloud. The Ark chat key does not work here |

Speech recognition uses Streaming ASR 2.0 after that service is enabled. Simultaneous interpretation uses Simultaneous Interpretation 2.0.

## Requirements

- Android 7.0 (API 24) or newer
- JDK 17
- Android SDK, with `compileSdk` and `targetSdk` 37

## Build

From the project root:

```bash
./gradlew :app:assembleDebug
```

On Windows, use `gradlew.bat`. The debug APK is written to `app/build/outputs/apk/debug/`.

After the first launch, save the DeepSeek and Doubao keys in Settings. Voice input, simultaneous interpretation, and Doubao read-aloud also need the Doubao Speech key. Vision and image generation need capable models plus the Doubao Ark key (for Seedream). Voice input and simultaneous interpretation need the microphone; image picking uses the system photo picker.

## License

[MIT License](LICENSE)
