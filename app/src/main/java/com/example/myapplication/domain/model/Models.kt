package com.example.myapplication.domain.model

/** 目录中的一个模型。id 发给接口，label 给用户看，group 用于选择器分组。 */
data class ModelOption(
    val id: String,
    val label: String,
    val group: String,
)

/** 对话服务商。豆包目录里的 DeepSeek、GLM 走方舟地址和方舟密钥，与官方 DeepSeek 分开。 */
enum class ProviderId(
    val displayName: String,
    val defaultBaseUrl: String,
    val defaultModel: String,
    val models: List<ModelOption>,
) {
    DEEPSEEK(
        displayName = "DeepSeek",
        defaultBaseUrl = "https://api.deepseek.com",
        defaultModel = "deepseek-flash",
        models = listOf(
            ModelOption("deepseek-flash", "Flash", "官方接口"),
            ModelOption("deepseek-v4-pro", "V4 Pro", "官方接口"),
        ),
    ),
    DOUBAO(
        displayName = "豆包",
        defaultBaseUrl = "https://ark.cn-beijing.volces.com/api/v3",
        defaultModel = "doubao-seed-2-1-lite-260915",
        models = listOf(
            ModelOption("doubao-seed-2-1-lite-260915", "Seed 2.1 Lite", "Seed"),
            ModelOption("doubao-seed-evolving", "Seed Evolving", "Seed"),
            ModelOption("doubao-seed-2-1-turbo-260628", "Seed 2.1 Turbo", "Seed"),
            ModelOption("doubao-seed-2-0-mini-260428", "Seed 2.0 Mini", "Seed"),
            ModelOption("doubao-seed-2-0-lite-260428", "Seed 2.0 Lite", "Seed"),
            ModelOption("deepseek-v4-1-flash-260910", "DeepSeek V4.1 Flash", "方舟上的 DeepSeek"),
            ModelOption("deepseek-v4-pro-ga-260813", "DeepSeek V4 Pro", "方舟上的 DeepSeek"),
            ModelOption("deepseek-v4-flash-ga-260731", "DeepSeek V4 Flash", "方舟上的 DeepSeek"),
            ModelOption("glm-5-3-flash-260828", "GLM 5.3 Flash", "方舟上的 GLM"),
            ModelOption("glm-5-2-260617", "GLM 5.2", "方舟上的 GLM"),
        ),
    ),
}

data class ModelChoice(
    val provider: ProviderId,
    val id: String,
    val label: String,
)

fun ProviderId.modelChoices(): List<ModelChoice> =
    models.map { option -> ModelChoice(this, option.id, option.label) }

/** 目录里没有的旧 ID 显示为「自定义」，ID 本身仍保留。 */
fun ProviderId.modelLabel(id: String): String =
    models.firstOrNull { it.id == id }?.label ?: "自定义"

fun ProviderId.modelSummary(id: String): String = "$displayName · ${modelLabel(id)}"

fun ProviderId.groupedModels(): List<Pair<String, List<ModelOption>>> =
    models.groupBy { it.group }.entries.map { it.key to it.value }

/** 新建对话时使用的服务商和模型：优先上次成功发送，否则用已配置密钥的默认模型。 */
data class SelectedModel(
    val provider: ProviderId,
    val model: String,
) {
    val summary: String get() = provider.modelSummary(model)
}

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

data class Conversation(
    val id: Long,
    val title: String,
    val provider: ProviderId,
    val model: String,
    val updatedAt: Long,
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
)

data class WireMessage(
    val role: Role,
    val content: String,
)

data class ChatRequest(
    val model: String,
    val messages: List<WireMessage>,
)

data class ProviderConfig(
    val provider: ProviderId,
    val apiKey: String,
    val baseUrl: String,
    val model: String,
    val speechApiKey: String = "",
)

sealed interface StreamEvent {
    data class Delta(val text: String) : StreamEvent
    data class Reasoning(val text: String) : StreamEvent
    data class Completed(val promptTokens: Int?, val completionTokens: Int?) : StreamEvent
    data class Failed(val message: String) : StreamEvent
}
