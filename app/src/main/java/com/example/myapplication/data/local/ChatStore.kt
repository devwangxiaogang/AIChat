package com.example.myapplication.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** provider、model 存在会话上。换模型只改这一行，不改已经发出的消息。 */
@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val provider: String,
    val model: String,
    val systemPrompt: String,
    val updatedAt: Long,
    /** 置顶会话排在列表最前。 */
    val pinned: Boolean = false,
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("conversationId")],
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: Long,
    val role: String,
    val content: String,
    val reasoning: String = "",
    val status: String,
    val error: String = "",
    val createdAt: Long,
)

@Entity(
    tableName = "message_attachments",
    foreignKeys = [
        ForeignKey(
            entity = MessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["messageId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("messageId"), Index("conversationId")],
)
data class AttachmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val messageId: Long,
    val conversationId: Long,
    val localPath: String,
    val mime: String,
    val source: String,
    val remoteUrl: String = "",
    val sortOrder: Int = 0,
)

/** 列表页用的会话摘要：带最后一条消息预览。 */
data class ConversationSummaryRow(
    val id: Long,
    val title: String,
    val provider: String,
    val model: String,
    val systemPrompt: String,
    val updatedAt: Long,
    val pinned: Boolean,
    val preview: String?,
)

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY pinned DESC, updatedAt DESC")
    fun observeAll(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    fun observe(id: Long): Flow<ConversationEntity?>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun get(id: Long): ConversationEntity?

    /** 列表页：会话 + 最后一条消息预览，置顶优先。 */
    @Query(
        "SELECT c.id AS id, c.title AS title, c.provider AS provider, c.model AS model, " +
            "c.systemPrompt AS systemPrompt, c.updatedAt AS updatedAt, c.pinned AS pinned, " +
            "(SELECT m.content FROM messages m WHERE m.conversationId = c.id " +
            "ORDER BY m.createdAt DESC, m.id DESC LIMIT 1) AS preview " +
            "FROM conversations c ORDER BY c.pinned DESC, c.updatedAt DESC",
    )
    fun observeSummaries(): Flow<List<ConversationSummaryRow>>

    @Query("UPDATE conversations SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)

    @Query("UPDATE conversations SET title = :title WHERE id = :id")
    suspend fun rename(id: Long, title: String)

    /** 导出备份时按时间顺序取全部会话。 */
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    suspend fun all(): List<ConversationEntity>

    @Insert
    suspend fun insert(entity: ConversationEntity): Long

    @Update
    suspend fun update(entity: ConversationEntity)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC, id ASC")
    fun observe(conversationId: Long): Flow<List<MessageEntity>>

    /** 导出备份时按时间顺序取整个会话的消息。 */
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC, id ASC")
    suspend fun list(conversationId: Long): List<MessageEntity>

    @Query(
        "SELECT * FROM messages WHERE conversationId = :conversationId AND status = 'DONE' " +
            "ORDER BY createdAt DESC, id DESC LIMIT :limit",
    )
    suspend fun recentDone(conversationId: Long, limit: Int): List<MessageEntity>

    @Query(
        "SELECT * FROM messages WHERE conversationId = :conversationId AND status = 'DONE' " +
            "AND role = 'USER' ORDER BY createdAt DESC, id DESC LIMIT 1",
    )
    suspend fun lastDoneUser(conversationId: Long): MessageEntity?

    @Query(
        "SELECT * FROM messages WHERE conversationId = :conversationId " +
            "ORDER BY createdAt DESC, id DESC LIMIT 1",
    )
    suspend fun latest(conversationId: Long): MessageEntity?

    @Insert
    suspend fun insert(entity: MessageEntity): Long

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun delete(id: Long)

    @Query(
        "UPDATE messages SET content = :content, reasoning = :reasoning, status = :status, error = :error " +
            "WHERE id = :id",
    )
    suspend fun updateBody(id: Long, content: String, reasoning: String, status: String, error: String)

    /**
     * 进程在流式生成中被杀掉时，回复会停在 STREAMING。
     * 启动时把这些行改成 FAILED，界面才不会一直显示「生成中」，重试也能用。
     */
    @Query("UPDATE messages SET status = 'FAILED', error = :error WHERE status = 'STREAMING'")
    suspend fun failStreaming(error: String): Int
}

@Dao
interface AttachmentDao {
    @Query("SELECT * FROM message_attachments WHERE conversationId = :conversationId ORDER BY sortOrder ASC, id ASC")
    fun observeForConversation(conversationId: Long): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM message_attachments WHERE messageId = :messageId ORDER BY sortOrder ASC, id ASC")
    suspend fun forMessage(messageId: Long): List<AttachmentEntity>

    /** 一次取回多条消息的用户附图，避免按消息逐条查询。 */
    @Query(
        "SELECT * FROM message_attachments WHERE messageId IN (:messageIds) " +
            "AND source = 'USER' ORDER BY sortOrder ASC, id ASC",
    )
    suspend fun forUserMessages(messageIds: List<Long>): List<AttachmentEntity>

    @Query("SELECT * FROM message_attachments WHERE conversationId = :conversationId")
    suspend fun forConversation(conversationId: Long): List<AttachmentEntity>

    @Insert
    suspend fun insert(entity: AttachmentEntity): Long

    @Insert
    suspend fun insertAll(entities: List<AttachmentEntity>)
}
