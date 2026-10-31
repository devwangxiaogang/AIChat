package com.yuguang.lumo.domain.model

import androidx.annotation.StringRes
import com.yuguang.lumo.R

/** 目录中的一个模型。id 发给接口，label 给用户看，groupRes 用于选择器分组。 */
data class ModelOption(
    val id: String,
    val label: String,
    @StringRes val groupRes: Int,
    val vision: Boolean = false,
    val imageGen: Boolean = false,
)

/** 对话服务商。豆包目录里的 DeepSeek、GLM 走方舟地址和方舟密钥，与官方 DeepSeek 分开。 */
enum class ProviderId(
    @StringRes val nameRes: Int,
    val defaultBaseUrl: String,
    val defaultModel: String,
    val models: List<ModelOption>,
) {
    DEEPSEEK(
        nameRes = R.string.provider_deepseek,
        defaultBaseUrl = "https://api.deepseek.com",
        defaultModel = "deepseek-flash",
        models = listOf(
            ModelOption("deepseek-flash", "Flash", R.string.group_official, vision = true),
            ModelOption("deepseek-v4-pro", "V4 Pro", R.string.group_official),
        ),
    ),
    DOUBAO(
        nameRes = R.string.provider_doubao,
        defaultBaseUrl = "https://ark.cn-beijing.volces.com/api/v3",
        defaultModel = "doubao-seed-2-1-lite-260915",
        models = listOf(
            ModelOption("doubao-seed-2-1-lite-260915", "Seed 2.1 Lite", R.string.group_seed, vision = true),
            ModelOption("doubao-seed-evolving", "Seed Evolving", R.string.group_seed, vision = true),
            ModelOption("doubao-seed-2-1-turbo-260628", "Seed 2.1 Turbo", R.string.group_seed, vision = true),
            ModelOption("doubao-seed-2-0-mini-260428", "Seed 2.0 Mini", R.string.group_seed, vision = true),
            ModelOption("doubao-seed-2-0-lite-260428", "Seed 2.0 Lite", R.string.group_seed, vision = true),
            ModelOption("doubao-seedream-5-0-flash-260915", "Seedream 5.0 Flash", R.string.group_seedream, imageGen = true),
            ModelOption("deepseek-v4-1-flash-260910", "DeepSeek V4.1 Flash", R.string.group_ark_deepseek),
            ModelOption("deepseek-v4-pro-ga-260813", "DeepSeek V4 Pro", R.string.group_ark_deepseek),
            ModelOption("deepseek-v4-flash-ga-260731", "DeepSeek V4 Flash", R.string.group_ark_deepseek),
            ModelOption("glm-5-3-flash-260828", "GLM 5.3 Flash", R.string.group_ark_glm),
            ModelOption("glm-5-2-260617", "GLM 5.2", R.string.group_ark_glm),
        ),
    ),
}

data class ModelChoice(
    val provider: ProviderId,
    val id: String,
    val label: String,
    val vision: Boolean = false,
    val imageGen: Boolean = false,
    @StringRes val groupRes: Int = R.string.group_official,
)

fun ProviderId.modelChoices(): List<ModelChoice> =
    models.map { option ->
        ModelChoice(this, option.id, option.label, option.vision, option.imageGen, option.groupRes)
    }

/** 目录里没有的旧 ID 由界面显示为「自定义」，ID 本身仍保留。 */
fun ProviderId.knownModelLabel(id: String): String? =
    models.firstOrNull { it.id == id }?.label

fun ProviderId.groupedModels(): List<Pair<Int, List<ModelOption>>> =
    models.groupBy { it.groupRes }.entries.map { it.key to it.value }

fun ProviderId.staticSupportsVision(modelId: String): Boolean =
    models.firstOrNull { it.id == modelId }?.vision == true ||
        ModelCapabilities.inferVision(this, modelId)

fun ProviderId.defaultImageGenModel(): String =
    models.firstOrNull { it.imageGen }?.id ?: "doubao-seedream-5-0-flash-260915"

/** 新建对话时使用的服务商和模型：优先上次成功发送，否则用已配置密钥的默认模型。 */
data class SelectedModel(
    val provider: ProviderId,
    val model: String,
)

enum class Role {
    SYSTEM,
    USER,
    ASSISTANT,
}

enum class MessageStatus {
    STREAMING,
    DONE,
    FAILED,
}

enum class AttachmentSource {
    USER,
    GENERATED,
}

data class MessageAttachment(
    val id: Long,
    val messageId: Long,
    val localPath: String,
    val mime: String,
    val source: AttachmentSource,
    val remoteUrl: String = "",
    val sortOrder: Int = 0,
)

data class Conversation(
    val id: Long,
    val title: String,
    val provider: ProviderId,
    val model: String,
    val systemPrompt: String,
    val updatedAt: Long,
    val pinned: Boolean = false,
    /** 最后一条消息预览，列表页用。 */
    val preview: String? = null,
)

data class ChatMessage(
    val id: Long,
    val conversationId: Long,
    val role: Role,
    val content: String,
    val reasoning: String,
    val status: MessageStatus,
    val error: String,
    val createdAt: Long,
    val attachments: List<MessageAttachment> = emptyList(),
)

/** 发给接口的消息。图片用 data URL，由客户端从本地文件编码。 */
data class WireMessage(
    val role: Role,
    val text: String,
    val imageDataUrls: List<String> = emptyList(),
)

data class ChatRequest(
    val model: String,
    val messages: List<WireMessage>,
    /** 豆包对话可挂 generate_image 工具，由客户端执行 Seedream。 */
    val enableImageTool: Boolean = false,
)

data class ProviderConfig(
    val provider: ProviderId,
    val apiKey: String,
    val baseUrl: String,
    val model: String,
    val speechApiKey: String = "",
    /** 旧版控制台的语音应用凭证；与 [speechAccessToken] 同时填写时优先用它们鉴权。 */
    val speechAppId: String = "",
    val speechAccessToken: String = "",
)

data class ToolCallRequest(
    val id: String,
    val name: String,
    val argumentsJson: String,
)

sealed interface StreamEvent {
    data class Delta(val text: String) : StreamEvent
    data class Reasoning(val text: String) : StreamEvent
    data class ToolCalls(val calls: List<ToolCallRequest>) : StreamEvent
    data class Completed(val promptTokens: Int?, val completionTokens: Int?) : StreamEvent
    data class Failed(val message: String) : StreamEvent
}

/** 从模型 ID / 远程字段推断识图、生图能力。 */
object ModelCapabilities {
    fun inferVision(provider: ProviderId, modelId: String): Boolean {
        val id = modelId.lowercase()
        if (id.contains("seedream")) return false
        return when (provider) {
            ProviderId.DEEPSEEK -> id.contains("flash") && !id.contains("pro")
            ProviderId.DOUBAO ->
                id.contains("seed-2") ||
                    id.contains("seed-evolving") ||
                    id.contains("vision") ||
                    id.contains("seed-1-6") ||
                    id.contains("seed-1-8")
        }
    }

    fun inferImageGen(modelId: String): Boolean =
        modelId.lowercase().contains("seedream")

    fun arkKeep(modelId: String): Boolean {
        val id = modelId.lowercase()
        return id.startsWith("doubao-seed-") ||
            id.startsWith("doubao-seedream-") ||
            id.startsWith("deepseek-v4") ||
            id.startsWith("glm-5")
    }

    /** 同系列多版本取日期后缀更大的；无日期的单独保留。 */
    fun versionKey(modelId: String): Long {
        val match = Regex("""(\d{6})$""").find(modelId) ?: return -1L
        return match.groupValues[1].toLongOrNull() ?: -1L
    }

    fun familyKey(modelId: String): String =
        modelId.replace(Regex("""-\d{6}$"""), "")
}
