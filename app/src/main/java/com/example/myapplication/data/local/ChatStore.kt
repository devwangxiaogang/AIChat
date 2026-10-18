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

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    fun observe(id: Long): Flow<ConversationEntity?>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun get(id: Long): ConversationEntity?

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

    @Query(
        "SELECT * FROM messages WHERE conversationId = :conversationId AND status = 'DONE' " +
            "ORDER BY createdAt ASC, id ASC",
    )
    suspend fun doneMessages(conversationId: Long): List<MessageEntity>

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
}
