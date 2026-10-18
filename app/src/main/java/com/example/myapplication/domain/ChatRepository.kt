package com.example.myapplication.domain

import com.example.myapplication.domain.model.ChatMessage
import com.example.myapplication.domain.model.Conversation
import com.example.myapplication.domain.model.ProviderId
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun observeConversations(): Flow<List<Conversation>>

    fun observeConversation(id: Long): Flow<Conversation?>

    fun observeMessages(conversationId: Long): Flow<List<ChatMessage>>

    fun isGenerating(conversationId: Long): Flow<Boolean>

    suspend fun createConversation(provider: ProviderId, model: String): Long

    suspend fun updateModel(conversationId: Long, model: String): String?

    suspend fun deleteConversation(id: Long)

    /** 返回 null 表示已开始生成，否则是需要展示给用户的原因。 */
    suspend fun send(conversationId: Long, text: String): String?

    suspend fun retry(conversationId: Long): String?

    fun stop(conversationId: Long)
}
