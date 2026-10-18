# AI助手

Android 上的助手应用。可以和 DeepSeek、火山方舟（豆包）上的模型对话，也可以做文字翻译和同声翻译。语音输入、朗读和同传使用豆包语音。

## 功能

侧边栏有三项：AI 对话、AI 翻译、设置。

### AI 对话

- 多段对话保存在本机，用 Room 存储。
- 流式回复。助手消息可以展开查看思考内容。
- 模型按服务商分组选择。新建对话默认使用最近一次成功发送的模型；没有记录时使用设置里的默认模型。
- 同一段对话不能中途更换服务商。更换模型只影响下一条消息。
- 目录之外的模型 ID 可以手动填写，界面会显示为「自定义」。
- 按住说话后，使用豆包流式语音识别把录音转成文字。

### AI 翻译

文字翻译和同声翻译分成两个页面，记录也各自显示。

- 文字翻译使用方舟上的 Seed Translation。选择语言和模型后输入原文，译文可以复制和朗读。点记录里的「填入输入框」会把原文和译文放回编辑区。
- 同声翻译对着手机说，原文和译文边写边出。译文由同传服务朗读。音色可选女声、男声或复刻原声。播放译文时麦克风不再把这段声音送回去，避免同一句反复翻译。
- 同声翻译要求源语言或目标语言是中文或英文。指定女声或男声时，目标语言只能是中文或英文。复刻原声支持中文、英文、德语、法语、西班牙语、印尼语、日语、葡萄牙语。粤语和上海话只能作为源语言，并译成中文或英文。
- 每个页面露出最近 3 条记录。点「全部」按今天、昨天和日期查看。记录超过 6 条时可以搜索原文或译文。点开一条可以看全文、复制、朗读；删除前会再确认一次。

### 朗读

对话、文字翻译和翻译记录里的朗读，可以在设置中选「豆包语音」或「本机朗读」。豆包语音可选女声或男声。同声翻译的音色单独选择，不走这项设置。豆包朗读失败时，可以改为只对这一次使用本机朗读。

### 密钥

API Key 经 Android Keystore 加密后保存在本机，不会写入源码。

## 服务商

| 服务商 | 默认地址 | 说明 |
| --- | --- | --- |
| DeepSeek | `https://api.deepseek.com` | 官方对话密钥 |
| 豆包 | `https://ark.cn-beijing.volces.com/api/v3` | 方舟对话密钥，同时用于文字翻译。方舟上的 DeepSeek、GLM 也用这把密钥 |
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

首次打开后，在设置里分别保存 DeepSeek 和豆包的密钥。语音输入、同声翻译和豆包朗读还需要填写豆包语音密钥。语音输入和同声翻译需要麦克风权限。

## 许可

[MIT License](LICENSE)

---

# AI Assistant

An Android assistant. It chats with DeepSeek and with models on Volcengine Ark (Doubao), and it translates typed text and live speech. Voice input, read-aloud, and simultaneous interpretation use Doubao Speech.

## Features

The sidebar has three sections: AI Chat, AI Translation, and Settings.

### AI Chat

- Conversations are stored on the device with Room.
- Replies stream in. Assistant messages can expand their reasoning.
- Models are grouped by provider. A new chat starts with the model that last sent successfully, or with the default model from Settings when there is no history.
- The provider stays fixed for an existing conversation. A model change applies to the next message.
- A model ID outside the catalog can be entered manually and is shown as “Custom”.
- Press-to-talk uses Doubao streaming speech recognition.

### AI Translation

Text translation and simultaneous interpretation are separate pages, and each page shows only its own history.

- Text translation uses Seed Translation on Ark. Choose the languages and model, enter the source text, then copy or read the result. “Fill the input” on a history item puts that source and translation back into the editor.
- Simultaneous interpretation writes the source and the translation while you speak, and the interpretation service reads the translation aloud. The voice can be female, male, or a clone of the speaker. While the translation is playing, the microphone does not send that audio back, so the same sentence is not translated again.
- One side of a live pair must be Chinese or English. With a named voice, the target must be Chinese or English. Clone voice supports Chinese, English, German, French, Spanish, Indonesian, Japanese, and Portuguese. Cantonese and Shanghainese can only be the source, and only into Chinese or English.
- Each page shows the latest three records. “All” groups them by today, yesterday, and date. Search appears after more than six records. Opening a record shows the full text, with copy and read-aloud; delete asks for confirmation.

### Read-aloud

Chat, text translation, and history can use Doubao Speech or the device’s own speech engine. Doubao Speech offers a female or male voice. The simultaneous-interpretation voice is chosen on that page and does not follow this setting. If Doubao read-aloud fails, that one utterance can fall back to the device engine.

### Keys

API keys are encrypted with the Android Keystore and are never stored in source.

## Providers

| Provider | Default endpoint | Notes |
| --- | --- | --- |
| DeepSeek | `https://api.deepseek.com` | Official chat key |
| Doubao | `https://ark.cn-beijing.volces.com/api/v3` | Ark chat key, also used for text translation. Ark-hosted DeepSeek and GLM models use this same key |
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

After the first launch, save the DeepSeek and Doubao keys in Settings. Voice input, simultaneous interpretation, and Doubao read-aloud also need the Doubao Speech key. Voice input and simultaneous interpretation need the microphone permission.

## License

[MIT License](LICENSE)
