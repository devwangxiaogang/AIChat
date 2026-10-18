package com.example.myapplication.data

import android.content.Context
import android.os.SystemClock
import com.example.myapplication.R
import com.example.myapplication.ui.isDefaultConversationTitle
import dagger.hilt.android.qualifiers.ApplicationContext
import com.example.myapplication.data.local.ConversationDao
import com.example.myapplication.data.local.ConversationEntity
import com.example.myapplication.data.local.MessageDao
import com.example.myapplication.data.local.MessageEntity
import com.example.myapplication.di.AppScope
import com.example.myapplication.domain.ChatRepository
import com.example.myapplication.domain.LlmProvider
import com.example.myapplication.domain.SettingsRepository
import com.example.myapplication.domain.model.ChatMessage
import com.example.myapplication.domain.model.ChatRequest
import com.example.myapplication.domain.model.Conversation
import com.example.myapplication.domain.model.MessageStatus
import com.example.myapplication.domain.model.ProviderConfig
import com.example.myapplication.domain.model.ProviderId
import com.example.myapplication.domain.model.Role
import com.example.myapplication.domain.model.StreamEvent
import com.example.myapplication.domain.model.WireMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val providers: Map<ProviderId, @JvmSuppressWildcards LlmProvider>,
    private val settingsRepository: SettingsRepository,
    @AppScope private val appScope: CoroutineScope,
) : ChatRepository {
    private val jobs = ConcurrentHashMap<Long, Job>()
    private val activeIds = MutableStateFlow<Set<Long>>(emptySet())

    override fun observeConversations(): Flow<List<Conversation>> =
        conversationDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun observeConversation(id: Long): Flow<Conversation?> =
        conversationDao.observe(id).map { row -> row?.toDomain() }

    override fun observeMessages(conversationId: Long): Flow<List<ChatMessage>> =
        messageDao.observe(conversationId).map { rows -> rows.map { it.toDomain() } }

    override fun isGenerating(conversationId: Long): Flow<Boolean> =
        activeIds.map { conversationId in it }.distinctUntilChanged()

    /** 模型在创建时写入会话，之后发送不会用设置里的默认模型覆盖它。 */
    override suspend fun createConversation(provider: ProviderId, model: String): Long {
        val config = settingsRepository.get(provider)
        val chosen = model.trim().ifBlank { config.model.ifBlank { provider.defaultModel } }
        return conversationDao.insert(
            ConversationEntity(
                title = context.getString(R.string.new_chat),
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
        // 生成过程中换模型会导致同一次回复的上下文和计费模型不一致。
        if (jobs[conversationId]?.isActive == true) return context.getString(R.string.generating)
        val conversation = conversationDao.get(conversationId) ?: return context.getString(R.string.conversation_missing)
        conversationDao.update(
            conversation.copy(model = chosen, updatedAt = System.currentTimeMillis()),
        )
        return null
    }

    override suspend fun deleteConversation(id: Long) {
        jobs[id]?.cancel()
        conversationDao.delete(id)
    }

    override suspend fun send(conversationId: Long, text: String): String? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return context.getString(R.string.enter_message)
        val prepared = prepare(conversationId) ?: return context.getString(R.string.conversation_missing)
        val (conversation, provider, config) = prepared
        val rejected = rejectIfBusyOrMissingKey(conversationId, provider, config)
        if (rejected != null) return rejected
        if (!start(conversation.id) {
                generate(conversation, provider, config, newUserText = trimmed)
            }
        ) {
            return context.getString(R.string.generating)
        }
        return null
    }

    override suspend fun retry(conversationId: Long): String? {
        val prepared = prepare(conversationId) ?: return context.getString(R.string.conversation_missing)
        val (conversation, provider, config) = prepared
        val rejected = rejectIfBusyOrMissingKey(conversationId, provider, config)
        if (rejected != null) return rejected
        val last = messageDao.latest(conversationId) ?: return context.getString(R.string.nothing_to_retry)
        if (last.role == Role.ASSISTANT.name && last.status == MessageStatus.FAILED.name) {
            messageDao.delete(last.id)
        }
        val hasUser = messageDao.doneMessages(conversationId).any { it.role == Role.USER.name }
        if (!hasUser) return context.getString(R.string.nothing_to_retry)
        if (!start(conversation.id) {
                generate(conversation, provider, config, newUserText = null)
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
        val job = appScope.launch { block() }
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
        return true
    }

    private suspend fun generate(
        conversation: ConversationEntity,
        provider: ProviderId,
        config: ProviderConfig,
        newUserText: String?,
    ) {
        var assistantId = 0L
        var content = ""
        var reasoning = ""
        var terminal = false
        try {
            val now = System.currentTimeMillis()
            if (newUserText != null) {
                messageDao.insert(
                    MessageEntity(
                        conversationId = conversation.id,
                        role = Role.USER.name,
                        content = newUserText,
                        status = MessageStatus.DONE.name,
                        createdAt = now,
                    ),
                )
            }
            // 以会话自己的模型为准，设置页只影响还没有成功发送记录时的新对话。
            val model = conversation.model.ifBlank {
                config.model.ifBlank { provider.defaultModel }
            }
            conversationDao.update(
                conversation.copy(
                    title = if (newUserText != null && isDefaultConversationTitle(conversation.title)) {
                        newUserText.take(24)
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
            val history = messageDao.doneMessages(conversation.id).takeLast(CONTEXT_LIMIT)
            val messages = buildList {
                if (conversation.systemPrompt.isNotBlank()) {
                    add(WireMessage(Role.SYSTEM, conversation.systemPrompt))
                }
                history.forEach { row ->
                    add(WireMessage(Role.valueOf(row.role), row.content))
                }
            }
            val llm = providers[provider] ?: error(context.getString(R.string.provider_missing, context.getString(provider.nameRes)))
            var lastFlush = 0L
            llm.stream(
                apiKey = config.apiKey,
                baseUrl = config.baseUrl,
                request = ChatRequest(model = model, messages = messages),
            ).collect { event ->
                when (event) {
                    is StreamEvent.Delta -> {
                        content += event.text
                        lastFlush = flush(assistantId, content, reasoning, lastFlush, force = false)
                    }
                    is StreamEvent.Reasoning -> {
                        reasoning += event.text
                        lastFlush = flush(assistantId, content, reasoning, lastFlush, force = false)
                    }
                    is StreamEvent.Completed -> {
                        messageDao.updateBody(assistantId, content, reasoning, MessageStatus.DONE.name, "")
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
                messageDao.updateBody(assistantId, content, reasoning, MessageStatus.DONE.name, "")
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
        }
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

    private fun ConversationEntity.toDomain(): Conversation = Conversation(
        id = id,
        title = title,
        provider = ProviderId.valueOf(provider),
        model = model,
        updatedAt = updatedAt,
    )

    private fun MessageEntity.toDomain(): ChatMessage = ChatMessage(
        id = id,
        conversationId = conversationId,
        role = Role.valueOf(role),
        content = content,
        reasoning = reasoning,
        status = MessageStatus.valueOf(status),
        error = error,
        createdAt = createdAt,
    )

    private companion object {
        const val CONTEXT_LIMIT = 20
        const val FLUSH_INTERVAL_MS = 100L
    }
}
