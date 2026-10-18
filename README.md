# AI 对话

Android 上的多模型对话应用。支持 DeepSeek 官方接口和火山方舟（豆包）上的模型，可以语音输入，也可以朗读回复。

## 功能

- 多段对话保存在本机，用 Room 存储。
- 流式回复。助手消息可以展开查看思考内容。
- 模型按服务商分组选择。新建对话默认使用最近一次成功发送的模型。
- 同一段对话不能中途更换服务商。更换模型只影响下一条消息。
- 目录之外的模型 ID 可以手动填写，界面会显示为「自定义」。
- 按住说话后，使用豆包流式语音识别把录音转成文字。
- 助手消息可以用系统语音朗读。
- API Key 经 Android Keystore 加密后保存在本机，不会写入源码。

## 服务商

| 服务商 | 默认地址 | 说明 |
| --- | --- | --- |
| DeepSeek | `https://api.deepseek.com` | 官方对话密钥 |
| 豆包 | `https://ark.cn-beijing.volces.com/api/v3` | 方舟对话密钥。方舟上的 DeepSeek、GLM 也用这把密钥 |
| 豆包语音 | 豆包语音控制台 | 单独的语音识别密钥，不能用方舟对话密钥代替 |

语音识别使用已开通的「流式语音识别 2.0」。

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

首次打开应用后，在设置里分别保存 DeepSeek 和豆包的密钥。豆包语音输入还需要填写语音识别密钥，并授予麦克风权限。

## 许可

[MIT License](LICENSE)

---

# AI Chat

An Android chat app for multiple models. It talks to the official DeepSeek API and to models on Volcengine Ark (Doubao), and it can take voice input and read replies aloud.

## Features

- Conversations are stored on the device with Room.
- Replies stream in. Assistant messages can expand their reasoning.
- Models are grouped by provider. A new chat starts with the model that last sent successfully.
- The provider stays fixed for an existing conversation. A model change applies to the next message.
- A model ID outside the catalog can be entered manually and is shown as “Custom”.
- Press-to-talk uses Doubao streaming speech recognition.
- Assistant messages can be read with the system text-to-speech engine.
- API keys are encrypted with the Android Keystore and are never stored in source.

## Providers

| Provider | Default endpoint | Notes |
| --- | --- | --- |
| DeepSeek | `https://api.deepseek.com` | Official chat key |
| Doubao | `https://ark.cn-beijing.volces.com/api/v3` | Ark chat key. Ark-hosted DeepSeek and GLM models use this same key |
| Doubao Speech | Doubao Speech console | A separate speech-recognition key. The Ark chat key does not work here |

Speech recognition uses Streaming ASR 2.0 after that service is enabled.

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

After the first launch, save the DeepSeek and Doubao keys in Settings. Voice input also needs the speech-recognition key and the microphone permission.

## License

[MIT License](LICENSE)
