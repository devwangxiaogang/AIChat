package com.yuguang.lumo.domain

import android.net.Uri
import com.yuguang.lumo.domain.model.ChatMessage
import com.yuguang.lumo.domain.model.Conversation
import com.yuguang.lumo.domain.model.ProviderId
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun observeConversations(): Flow<List<Conversation>>

    fun observeConversation(id: Long): Flow<Conversation?>

    fun observeMessages(conversationId: Long): Flow<List<ChatMessage>>

    fun isGenerating(conversationId: Long): Flow<Boolean>

    suspend fun createConversation(provider: ProviderId, model: String): Long

    suspend fun updateModel(conversationId: Long, model: String): String?

    suspend fun updateSystemPrompt(conversationId: Long, prompt: String): String?

    suspend fun deleteConversation(id: Long)

    suspend fun setPinned(id: Long, pinned: Boolean)

    suspend fun renameConversation(id: Long, title: String)

    /** 返回 null 表示已开始生成，否则是需要展示给用户的原因。 */
    suspend fun send(
        conversationId: Long,
        text: String,
        imageUris: List<Uri> = emptyList(),
        drawMode: Boolean = false,
    ): String?

    suspend fun retry(conversationId: Long): String?

    fun stop(conversationId: Long)
}
