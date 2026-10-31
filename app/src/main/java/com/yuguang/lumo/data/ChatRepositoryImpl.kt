package com.yuguang.lumo.data

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import com.yuguang.lumo.R
import com.yuguang.lumo.data.local.AttachmentDao
import com.yuguang.lumo.data.local.AttachmentEntity
import com.yuguang.lumo.data.local.ChatMediaStore
import com.yuguang.lumo.data.local.ConversationDao
import com.yuguang.lumo.data.local.ConversationEntity
import com.yuguang.lumo.data.local.ConversationSummaryRow
import com.yuguang.lumo.data.local.MessageDao
import com.yuguang.lumo.data.local.MessageEntity
import com.yuguang.lumo.data.remote.DoubaoProvider
import com.yuguang.lumo.data.remote.SeedreamClient
import com.yuguang.lumo.di.AppScope
import com.yuguang.lumo.domain.ChatRepository
import com.yuguang.lumo.domain.LlmProvider
import com.yuguang.lumo.domain.ModelCatalogRepository
import com.yuguang.lumo.domain.SettingsRepository
import com.yuguang.lumo.domain.model.AttachmentSource
import com.yuguang.lumo.domain.model.ChatMessage
import com.yuguang.lumo.domain.model.ChatRequest
import com.yuguang.lumo.domain.model.Conversation
import com.yuguang.lumo.domain.model.MessageAttachment
import com.yuguang.lumo.domain.model.MessageStatus
import com.yuguang.lumo.domain.model.ProviderConfig
import com.yuguang.lumo.domain.model.ProviderId
import com.yuguang.lumo.domain.model.Role
import com.yuguang.lumo.domain.model.StreamEvent
import com.yuguang.lumo.domain.model.ToolCallRequest
import com.yuguang.lumo.domain.model.WireMessage
import com.yuguang.lumo.domain.model.historyStartIndex
import com.yuguang.lumo.domain.model.overlayStreaming
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val attachmentDao: AttachmentDao,
    private val mediaStore: ChatMediaStore,
    private val seedreamClient: SeedreamClient,
    private val modelCatalog: ModelCatalogRepository,
    private val providers: Map<ProviderId, @JvmSuppressWildcards LlmProvider>,
    private val settingsRepository: SettingsRepository,
    private val json: Json,
    @AppScope private val appScope: CoroutineScope,
) : ChatRepository {
    private val jobs = ConcurrentHashMap<Long, Job>()
    private val activeIds = MutableStateFlow<Set<Long>>(emptySet())

    /** 正在生成中的正文只留在内存里，避免每个 token 都写一次数据库。 */
    private val streaming = MutableStateFlow<Map<Long, StreamingText>>(emptyMap())

    override fun observeConversations(): Flow<List<Conversation>> =
        conversationDao.observeSummaries().map { rows -> rows.map { it.toDomain() } }

    override fun observeConversation(id: Long): Flow<Conversation?> =
        conversationDao.observe(id).map { row -> row?.toDomain() }

    override fun observeMessages(conversationId: Long): Flow<List<ChatMessage>> =
        combine(
            messageDao.observe(conversationId),
            attachmentDao.observeForConversation(conversationId),
            streaming.map { it[conversationId] }.distinctUntilChanged(),
        ) { messages, attachments, live ->
            val byMessage = attachments.groupBy { it.messageId }
            val snapshot = messages.map { row ->
                row.toDomain(byMessage[row.id].orEmpty().map { it.toDomain() })
            }
            if (live == null) {
                snapshot
            } else {
                snapshot.overlayStreaming(live.messageId, live.content, live.reasoning)
            }
        }

    override fun isGenerating(conversationId: Long): Flow<Boolean> =
        activeIds.map { conversationId in it }.distinctUntilChanged()

    override suspend fun createConversation(provider: ProviderId, model: String): Long {
        val config = settingsRepository.get(provider)
        val chosen = model.trim().ifBlank { config.model.ifBlank { provider.defaultModel } }
        return conversationDao.insert(
            ConversationEntity(
                // 空标题表示「还没自动命名」，界面再显示当前语言的「新对话」。
                title = "",
                provider = provider.name,
                model = chosen,
                systemPrompt = context.getString(R.string.assistant_prompt),
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun updateModel(conversationId: Long, model: String): String? {
        val chosen = model.trim()
        if (chosen.isEmpty()) return context.getString(R.string.choose_model_error)
        if (jobs[conversationId]?.isActive == true) return context.getString(R.string.generating)
        val conversation = conversationDao.get(conversationId) ?: return context.getString(R.string.conversation_missing)
        conversationDao.update(
            conversation.copy(model = chosen, updatedAt = System.currentTimeMillis()),
        )
        return null
    }

    override suspend fun updateSystemPrompt(conversationId: Long, prompt: String): String? {
        if (jobs[conversationId]?.isActive == true) return context.getString(R.string.generating)
        val conversation = conversationDao.get(conversationId) ?: return context.getString(R.string.conversation_missing)
        conversationDao.update(
            conversation.copy(
                systemPrompt = prompt.trim().ifBlank { context.getString(R.string.assistant_prompt) },
                updatedAt = System.currentTimeMillis(),
            ),
        )
        return null
    }

    override suspend fun setPinned(id: Long, pinned: Boolean) {
        conversationDao.setPinned(id, pinned)
    }

    override suspend fun renameConversation(id: Long, title: String) {
        val clean = title.trim()
        if (clean.isNotEmpty()) conversationDao.rename(id, clean)
    }

    override suspend fun deleteConversation(id: Long) {
        jobs[id]?.cancel()
        val paths = attachmentDao.forConversation(id).map { it.localPath }
        conversationDao.delete(id)
        mediaStore.deletePaths(paths)
    }

    override suspend fun send(
        conversationId: Long,
        text: String,
        imageUris: List<Uri>,
        drawMode: Boolean,
    ): String? {
        val trimmed = text.trim()
        if (trimmed.isEmpty() && imageUris.isEmpty()) return context.getString(R.string.enter_message)
        if (jobs[conversationId]?.isActive == true) return context.getString(R.string.generating)
        if (imageUris.size > ChatMediaStore.MAX_ATTACHMENTS) {
            return context.getString(R.string.too_many_images, ChatMediaStore.MAX_ATTACHMENTS)
        }
        val prepared = prepare(conversationId) ?: return context.getString(R.string.conversation_missing)
        val (conversation, provider, config) = prepared
        val shouldDraw = drawMode || looksLikeDrawIntent(trimmed)
        if (shouldDraw) {
            val doubao = settingsRepository.get(ProviderId.DOUBAO)
            if (doubao.apiKey.isBlank()) return context.getString(R.string.need_doubao_for_draw)
        } else {
            val rejected = rejectIfBusyOrMissingKey(conversationId, provider, config)
            if (rejected != null) return rejected
            if (imageUris.isNotEmpty() &&
                !modelCatalog.supportsVision(provider, conversation.model.ifBlank { config.model })
            ) {
                return context.getString(R.string.model_no_vision)
            }
        }
        val imported = imageUris.mapNotNull { mediaStore.importUserImage(it) }
        if (imageUris.isNotEmpty() && imported.isEmpty()) {
            return context.getString(R.string.image_import_failed)
        }
        if (!start(conversation.id) {
                if (shouldDraw) {
                    generateImage(
                        conversation = conversation,
                        prompt = trimmed.ifBlank { context.getString(R.string.draw_default_prompt) },
                        imported = imported,
                        insertUser = true,
                    )
                } else {
                    generate(conversation, provider, config, trimmed, imported)
                }
            }
        ) {
            mediaStore.deletePaths(imported.map { it.path })
            return context.getString(R.string.generating)
        }
        return null
    }

    override suspend fun retry(conversationId: Long): String? {
        if (jobs[conversationId]?.isActive == true) return context.getString(R.string.generating)
        val prepared = prepare(conversationId) ?: return context.getString(R.string.conversation_missing)
        val (conversation, provider, config) = prepared
        val last = messageDao.latest(conversationId) ?: return context.getString(R.string.nothing_to_retry)
        if (last.role != Role.ASSISTANT.name || last.status != MessageStatus.FAILED.name) {
            return context.getString(R.string.nothing_to_retry)
        }
        val drawRetry = last.error.startsWith(DRAW_ERROR_PREFIX)
        if (drawRetry) {
            val doubao = settingsRepository.get(ProviderId.DOUBAO)
            if (doubao.apiKey.isBlank()) return context.getString(R.string.need_doubao_for_draw)
        } else {
            val rejected = rejectIfBusyOrMissingKey(conversationId, provider, config)
            if (rejected != null) return rejected
        }
        val paths = attachmentDao.forMessage(last.id).map { it.localPath }
        messageDao.delete(last.id)
        mediaStore.deletePaths(paths)
        val user = messageDao.lastDoneUser(conversationId)
            ?: return context.getString(R.string.nothing_to_retry)
        if (!start(conversation.id) {
                if (drawRetry) {
                    val refs = attachmentDao.forMessage(user.id)
                        .filter { it.source == AttachmentSource.USER.name }
                        .map { ChatMediaStore.ImportedImage(it.localPath, it.mime) }
                    generateImage(
                        conversation = conversation,
                        prompt = user.content.ifBlank { context.getString(R.string.draw_default_prompt) },
                        imported = refs,
                        insertUser = false,
                    )
                } else {
                    generate(conversation, provider, config, newUserText = null, imported = emptyList())
                }
            }
        ) {
            return context.getString(R.string.generating)
        }
        return null
    }

    override fun stop(conversationId: Long) {
        jobs[conversationId]?.cancel()
    }

    private suspend fun prepare(conversationId: Long): Triple<ConversationEntity, ProviderId, ProviderConfig>? {
        val conversation = conversationDao.get(conversationId) ?: return null
        val provider = runCatching { ProviderId.valueOf(conversation.provider) }.getOrNull() ?: return null
        return Triple(conversation, provider, settingsRepository.get(provider))
    }

    private fun rejectIfBusyOrMissingKey(
        conversationId: Long,
        provider: ProviderId,
        config: ProviderConfig,
    ): String? {
        if (jobs[conversationId]?.isActive == true) return context.getString(R.string.generating)
        if (config.apiKey.isBlank()) {
            return context.getString(R.string.need_provider_key, context.getString(provider.nameRes))
        }
        return null
    }

    private fun start(conversationId: Long, block: suspend () -> Unit): Boolean {
        // 先建一个惰性任务，等占位成功后再真正启动，避免两次快速发送都插入了用户消息。
        val job = appScope.launch(start = CoroutineStart.LAZY) { block() }
        var claimed = false
        jobs.compute(conversationId) { _, existing ->
            if (existing?.isActive == true) {
                claimed = false
                existing
            } else {
                claimed = true
                job
            }
        }
        if (!claimed) {
            job.cancel()
            return false
        }
        activeIds.update { it + conversationId }
        job.invokeOnCompletion {
            jobs.remove(conversationId, job)
            activeIds.update { ids -> ids - conversationId }
        }
        job.start()
        return true
    }

    private suspend fun generate(
        conversation: ConversationEntity,
        provider: ProviderId,
        config: ProviderConfig,
        newUserText: String?,
        imported: List<ChatMediaStore.ImportedImage>,
    ) {
        var assistantId = 0L
        var content = ""
        var reasoning = ""
        var terminal = false
        try {
            val now = System.currentTimeMillis()
            val userText = newUserText.orEmpty()
            if (newUserText != null || imported.isNotEmpty()) {
                val userId = messageDao.insert(
                    MessageEntity(
                        conversationId = conversation.id,
                        role = Role.USER.name,
                        content = userText,
                        status = MessageStatus.DONE.name,
                        createdAt = now,
                    ),
                )
                if (imported.isNotEmpty()) {
                    attachmentDao.insertAll(
                        imported.mapIndexed { index, image ->
                            AttachmentEntity(
                                messageId = userId,
                                conversationId = conversation.id,
                                localPath = image.path,
                                mime = image.mime,
                                source = AttachmentSource.USER.name,
                                sortOrder = index,
                            )
                        },
                    )
                }
            }
            val model = conversation.model.ifBlank {
                config.model.ifBlank { provider.defaultModel }
            }
            conversationDao.update(
                conversation.copy(
                    title = if ((newUserText != null || imported.isNotEmpty()) &&
                        isUntitled(conversation.title)
                    ) {
                        userText.take(24).ifBlank { context.getString(R.string.image_message_title) }
                    } else {
                        conversation.title
                    },
                    model = model,
                    updatedAt = now,
                ),
            )
            assistantId = messageDao.insert(
                MessageEntity(
                    conversationId = conversation.id,
                    role = Role.ASSISTANT.name,
                    content = "",
                    status = MessageStatus.STREAMING.name,
                    createdAt = System.currentTimeMillis(),
                ),
            )
            val messages = buildWireHistory(conversation)
            val doubaoKeyReady = settingsRepository.get(ProviderId.DOUBAO).apiKey.isNotBlank()
            val enableTool = provider == ProviderId.DOUBAO && doubaoKeyReady
            val llm = providers[provider]
                ?: error(context.getString(R.string.provider_missing, context.getString(provider.nameRes)))
            var lastFlush = 0L
            var lastStream = 0L
            var pendingTools: List<ToolCallRequest> = emptyList()
            llm.stream(
                apiKey = config.apiKey,
                baseUrl = config.baseUrl,
                request = ChatRequest(model = model, messages = messages, enableImageTool = enableTool),
            ).collect { event ->
                when (event) {
                    is StreamEvent.Delta -> {
                        content += event.text
                        lastStream = publishStreaming(
                            conversationId = conversation.id,
                            messageId = assistantId,
                            content = content,
                            reasoning = reasoning,
                            lastPublish = lastStream,
                        )
                        lastFlush = flush(assistantId, content, reasoning, lastFlush, force = false)
                    }
                    is StreamEvent.Reasoning -> {
                        reasoning += event.text
                        lastStream = publishStreaming(
                            conversationId = conversation.id,
                            messageId = assistantId,
                            content = content,
                            reasoning = reasoning,
                            lastPublish = lastStream,
                        )
                        lastFlush = flush(assistantId, content, reasoning, lastFlush, force = false)
                    }
                    is StreamEvent.ToolCalls -> pendingTools = event.calls
                    is StreamEvent.Completed -> {
                        if (pendingTools.isNotEmpty()) {
                            handleImageTools(assistantId, conversation, pendingTools, content)
                        } else {
                            messageDao.updateBody(assistantId, content, reasoning, MessageStatus.DONE.name, "")
                        }
                        settingsRepository.rememberRecent(provider, model)
                        terminal = true
                    }
                    is StreamEvent.Failed -> {
                        messageDao.updateBody(
                            assistantId,
                            content,
                            reasoning,
                            MessageStatus.FAILED.name,
                            event.message,
                        )
                        terminal = true
                    }
                }
            }
            if (!terminal) {
                if (pendingTools.isNotEmpty()) {
                    handleImageTools(assistantId, conversation, pendingTools, content)
                } else {
                    messageDao.updateBody(assistantId, content, reasoning, MessageStatus.DONE.name, "")
                }
                settingsRepository.rememberRecent(provider, model)
                terminal = true
            }
        } catch (cancelled: CancellationException) {
            finishCancelled(assistantId, content, reasoning, terminal)
            throw cancelled
        } catch (error: Exception) {
            if (assistantId != 0L && !terminal) {
                messageDao.updateBody(
                    assistantId,
                    content,
                    reasoning,
                    MessageStatus.FAILED.name,
                    error.message ?: context.getString(R.string.request_failed),
                )
            }
        } finally {
            // 收尾后以数据库为准；这里只清理内存里的流式副本。
            streaming.update { it - conversation.id }
        }
    }

    private suspend fun handleImageTools(
        assistantId: Long,
        conversation: ConversationEntity,
        calls: List<ToolCallRequest>,
        priorContent: String,
    ) {
        val drawCall = calls.firstOrNull {
            it.name.equals(DoubaoProvider.GENERATE_IMAGE_TOOL, ignoreCase = true)
        }
        if (drawCall == null) {
            messageDao.updateBody(assistantId, priorContent, "", MessageStatus.DONE.name, "")
            return
        }
        val prompt = parseToolPrompt(drawCall.argumentsJson)
            ?: priorContent.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.draw_default_prompt)
        messageDao.updateBody(
            assistantId,
            context.getString(R.string.drawing_in_progress),
            "",
            MessageStatus.STREAMING.name,
            "",
        )
        try {
            attachGeneratedImage(assistantId, conversation, prompt, emptyList())
            val caption = priorContent.ifBlank { context.getString(R.string.draw_done_seedream) }
            messageDao.updateBody(assistantId, caption, "", MessageStatus.DONE.name, "")
        } catch (error: Exception) {
            messageDao.updateBody(
                assistantId,
                priorContent,
                "",
                MessageStatus.FAILED.name,
                DRAW_ERROR_PREFIX + (error.message ?: context.getString(R.string.image_gen_failed)),
            )
        }
    }

    private suspend fun generateImage(
        conversation: ConversationEntity,
        prompt: String,
        imported: List<ChatMediaStore.ImportedImage>,
        insertUser: Boolean,
    ) {
        var assistantId = 0L
        var terminal = false
        try {
            val now = System.currentTimeMillis()
            if (insertUser) {
                val userId = messageDao.insert(
                    MessageEntity(
                        conversationId = conversation.id,
                        role = Role.USER.name,
                        content = prompt,
                        status = MessageStatus.DONE.name,
                        createdAt = now,
                    ),
                )
                if (imported.isNotEmpty()) {
                    attachmentDao.insertAll(
                        imported.mapIndexed { index, image ->
                            AttachmentEntity(
                                messageId = userId,
                                conversationId = conversation.id,
                                localPath = image.path,
                                mime = image.mime,
                                source = AttachmentSource.USER.name,
                                sortOrder = index,
                            )
                        },
                    )
                }
                conversationDao.update(
                    conversation.copy(
                        title = if (isUntitled(conversation.title)) {
                            prompt.take(24).ifBlank { context.getString(R.string.draw_message_title) }
                        } else {
                            conversation.title
                        },
                        updatedAt = now,
                    ),
                )
            }
            assistantId = messageDao.insert(
                MessageEntity(
                    conversationId = conversation.id,
                    role = Role.ASSISTANT.name,
                    content = context.getString(R.string.drawing_in_progress),
                    status = MessageStatus.STREAMING.name,
                    createdAt = System.currentTimeMillis(),
                ),
            )
            attachGeneratedImage(assistantId, conversation, prompt, imported)
            messageDao.updateBody(
                assistantId,
                context.getString(R.string.draw_done_seedream),
                "",
                MessageStatus.DONE.name,
                "",
            )
            terminal = true
        } catch (cancelled: CancellationException) {
            if (assistantId != 0L && !terminal) {
                messageDao.delete(assistantId)
            }
            throw cancelled
        } catch (error: Exception) {
            if (assistantId != 0L && !terminal) {
                messageDao.updateBody(
                    assistantId,
                    context.getString(R.string.drawing_in_progress),
                    "",
                    MessageStatus.FAILED.name,
                    DRAW_ERROR_PREFIX + (error.message ?: context.getString(R.string.image_gen_failed)),
                )
            }
        }
    }

    private suspend fun attachGeneratedImage(
        assistantId: Long,
        conversation: ConversationEntity,
        prompt: String,
        imported: List<ChatMediaStore.ImportedImage>,
    ) {
        val doubao = settingsRepository.get(ProviderId.DOUBAO)
        val refs = imported.mapNotNull { mediaStore.toDataUrl(it.path, it.mime) }
        val result = seedreamClient.generate(
            apiKey = doubao.apiKey,
            baseUrl = doubao.baseUrl,
            model = modelCatalog.preferredImageGenModel(),
            prompt = prompt,
            referenceDataUrls = refs,
        )
        val bytes = result.bytes ?: error(context.getString(R.string.image_gen_empty))
        val path = mediaStore.saveGenerated(bytes, result.mime)
        attachmentDao.insert(
            AttachmentEntity(
                messageId = assistantId,
                conversationId = conversation.id,
                localPath = path,
                mime = result.mime,
                source = AttachmentSource.GENERATED.name,
                remoteUrl = result.url.orEmpty(),
                sortOrder = 0,
            ),
        )
    }

    /**
     * 组装发给模型的历史。
     *
     * 只按需取最近一段（不再整表加载），条数和字符数都设上限；
     * 图片单独受数量和编码后体积限制，避免几个大图把请求体撑到几十 MB。
     */
    private suspend fun buildWireHistory(conversation: ConversationEntity): List<WireMessage> {
        val recent = messageDao.recentDone(conversation.id, HISTORY_FETCH_LIMIT).asReversed()
        val window = if (recent.isEmpty()) {
            emptyList()
        } else {
            val start = historyStartIndex(
                textLengths = recent.map { it.content.length },
                maxMessages = HISTORY_MAX_MESSAGES,
                maxChars = HISTORY_MAX_CHARS,
            )
            recent.subList(start, recent.size)
        }
        val userIds = window.filter { it.role == Role.USER.name }.map { it.id }
        val images = if (userIds.isEmpty()) {
            emptyMap()
        } else {
            attachmentDao.forUserMessages(userIds).groupBy { it.messageId }
        }
        var imagesLeft = IMAGE_HISTORY_LIMIT
        var imageCharsLeft = IMAGE_HISTORY_CHARS
        return buildList {
            if (conversation.systemPrompt.isNotBlank()) {
                add(WireMessage(Role.SYSTEM, conversation.systemPrompt))
            }
            window.forEach { row ->
                val role = Role.valueOf(row.role)
                val picked = mutableListOf<AttachmentEntity>()
                if (role == Role.USER) {
                    for (attachment in images[row.id].orEmpty()) {
                        if (picked.size >= imagesLeft) break
                        val encoded = mediaStore.encodedLength(attachment.localPath)
                        if (encoded <= 0 || encoded > imageCharsLeft) continue
                        imageCharsLeft -= encoded
                        picked += attachment
                    }
                }
                imagesLeft -= picked.size
                val dataUrls = picked.mapNotNull { mediaStore.toDataUrl(it.localPath, it.mime) }
                add(WireMessage(role, row.content, dataUrls))
            }
        }
    }

    private fun parseToolPrompt(argumentsJson: String): String? {
        if (argumentsJson.isBlank()) return null
        return runCatching {
            json.parseToJsonElement(argumentsJson).jsonObject["prompt"]?.jsonPrimitive?.contentOrNull
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun looksLikeDrawIntent(text: String): Boolean {
        if (text.isBlank()) return false
        val lower = text.lowercase()
        return DRAW_PATTERNS.any { it.containsMatchIn(lower) || it.containsMatchIn(text) }
    }

    private suspend fun finishCancelled(
        assistantId: Long,
        content: String,
        reasoning: String,
        terminal: Boolean,
    ) {
        if (assistantId == 0L || terminal) return
        if (content.isBlank() && reasoning.isBlank()) {
            messageDao.delete(assistantId)
        } else {
            messageDao.updateBody(assistantId, content, reasoning, MessageStatus.DONE.name, "")
        }
    }

    private suspend fun flush(
        id: Long,
        content: String,
        reasoning: String,
        lastFlush: Long,
        force: Boolean,
    ): Long {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastFlush < FLUSH_INTERVAL_MS) return lastFlush
        messageDao.updateBody(id, content, reasoning, MessageStatus.STREAMING.name, "")
        return now
    }

    /** 把最新正文推到内存流；节流到几十毫秒，避免每个 token 都触发一次重组。 */
    private fun publishStreaming(
        conversationId: Long,
        messageId: Long,
        content: String,
        reasoning: String,
        lastPublish: Long,
    ): Long {
        val now = SystemClock.elapsedRealtime()
        if (now - lastPublish < STREAM_INTERVAL_MS) return lastPublish
        streaming.update { it + (conversationId to StreamingText(messageId, content, reasoning)) }
        return now
    }

    private data class StreamingText(
        val messageId: Long,
        val content: String,
        val reasoning: String,
    )

    /**
     * 空标题表示还没自动命名。旧版本把本地化的「新对话」写进了库，
     * 读出来时统一归一成空串，避免换系统语言后标题还是旧语言。
     */
    private fun isUntitled(title: String): Boolean =
        title.isBlank() || title == LEGACY_NEW_CHAT_ZH || title == LEGACY_NEW_CHAT_EN

    private fun ConversationEntity.toDomain(): Conversation = Conversation(
        id = id,
        title = if (isUntitled(title)) "" else title,
        provider = ProviderId.valueOf(provider),
        model = model,
        systemPrompt = systemPrompt,
        updatedAt = updatedAt,
    )

    private fun ConversationSummaryRow.toDomain(): Conversation = Conversation(
        id = id,
        title = if (isUntitled(title)) "" else title,
        provider = ProviderId.valueOf(provider),
        model = model,
        systemPrompt = systemPrompt,
        updatedAt = updatedAt,
        pinned = pinned,
        preview = preview?.trim()?.replace('\n', ' ')?.take(120),
    )

    private fun MessageEntity.toDomain(attachments: List<MessageAttachment> = emptyList()): ChatMessage =
        ChatMessage(
            id = id,
            conversationId = conversationId,
            role = Role.valueOf(role),
            content = content,
            reasoning = reasoning,
            status = MessageStatus.valueOf(status),
            error = error.removePrefix(DRAW_ERROR_PREFIX),
            createdAt = createdAt,
            attachments = attachments,
        )

    private fun AttachmentEntity.toDomain(): MessageAttachment = MessageAttachment(
        id = id,
        messageId = messageId,
        localPath = localPath,
        mime = mime,
        source = runCatching { AttachmentSource.valueOf(source) }.getOrDefault(AttachmentSource.USER),
        remoteUrl = remoteUrl,
        sortOrder = sortOrder,
    )

    companion object {
        const val DRAW_ERROR_PREFIX = "__draw__:"
        /** 先按条数粗取，再交给 [historyStartIndex] 按字符预算裁剪。 */
        private const val HISTORY_FETCH_LIMIT = 40
        private const val HISTORY_MAX_MESSAGES = 24
        private const val HISTORY_MAX_CHARS = 12_000
        private const val IMAGE_HISTORY_LIMIT = 4
        /** 历史附图 base64 后的总字符预算（约 4.5 MB 原始数据）。 */
        private const val IMAGE_HISTORY_CHARS = 6_000_000L
        /** 数据库兜底落盘间隔：正文平时只在内存里，进程被杀最多回退这么多内容。 */
        private const val FLUSH_INTERVAL_MS = 1_000L
        /** 内存流式文本的界面刷新间隔。 */
        private const val STREAM_INTERVAL_MS = 60L
        private const val LEGACY_NEW_CHAT_ZH = "新对话"
        private const val LEGACY_NEW_CHAT_EN = "New chat"
        private val DRAW_PATTERNS = listOf(
            Regex("""画(一)?(张|个|幅)"""),
            Regex("""帮我画"""),
            Regex("""请画"""),
            Regex("""生成(一)?(张|幅)?(图|图片|插画|海报|图像)"""),
            Regex("""\b(draw|paint)\b.+\b(image|picture|illustration)\b""", RegexOption.IGNORE_CASE),
            Regex("""\b(generate|create)\b.+\b(image|picture)\b""", RegexOption.IGNORE_CASE),
        )
    }
}
