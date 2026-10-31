package com.yuguang.lumo.data

import android.content.Context
import android.net.Uri
import com.yuguang.lumo.data.local.AttachmentDao
import com.yuguang.lumo.data.local.AttachmentEntity
import com.yuguang.lumo.data.local.ChatMediaStore
import com.yuguang.lumo.data.local.ConversationDao
import com.yuguang.lumo.data.local.ConversationEntity
import com.yuguang.lumo.data.local.MessageDao
import com.yuguang.lumo.data.local.MessageEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 会话备份：导出一个 ZIP（backup.json + media/ 图片），导入时写回数据库和 chat_media。
 * 导入总是创建新的会话，避免和现有记录冲突。
 */
@Singleton
class ChatBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val attachmentDao: AttachmentDao,
    private val mediaStore: ChatMediaStore,
    private val json: Json,
) {
    /** @return 导出的会话数量。 */
    suspend fun exportTo(uri: Uri): Int = withContext(Dispatchers.IO) {
        val conversations = conversationDao.all()
        val mediaEntries = linkedMapOf<String, String>()
        val backup = BackupFile(
            exportedAt = System.currentTimeMillis(),
            conversations = conversations.map { conversation ->
                BackupConversation(
                    title = conversation.title,
                    provider = conversation.provider,
                    model = conversation.model,
                    systemPrompt = conversation.systemPrompt,
                    updatedAt = conversation.updatedAt,
                    messages = messageDao.list(conversation.id).map { message ->
                        BackupMessage(
                            role = message.role,
                            content = message.content,
                            reasoning = message.reasoning,
                            status = message.status,
                            error = message.error,
                            createdAt = message.createdAt,
                            attachments = attachmentDao.forMessage(message.id).map { attachment ->
                                val entry = mediaEntries.getOrPut(attachment.localPath) {
                                    "media/${mediaEntries.size}_${File(attachment.localPath).name}"
                                }
                                BackupAttachment(
                                    entry = entry,
                                    mime = attachment.mime,
                                    source = attachment.source,
                                    remoteUrl = attachment.remoteUrl,
                                    sortOrder = attachment.sortOrder,
                                )
                            },
                        )
                    },
                )
            },
        )
        val output = context.contentResolver.openOutputStream(uri)
            ?: error("cannot open output")
        ZipOutputStream(BufferedOutputStream(output)).use { zip ->
            zip.putNextEntry(ZipEntry(BACKUP_ENTRY))
            zip.write(json.encodeToString(backup).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            mediaEntries.forEach { (path, entry) ->
                val file = File(path)
                if (!file.isFile) return@forEach
                zip.putNextEntry(ZipEntry(entry))
                file.inputStream().use { input -> input.copyTo(zip) }
                zip.closeEntry()
            }
        }
        conversations.size
    }

    /** @return 导入的会话数量。 */
    suspend fun importFrom(uri: Uri): Int = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(uri)
            ?: error("cannot open input")
        var backup: BackupFile? = null
        val entryToFile = mutableMapOf<String, String>()
        ZipInputStream(BufferedInputStream(input)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                when {
                    name == BACKUP_ENTRY -> {
                        backup = json.decodeFromString<BackupFile>(
                            zip.readBytes().toString(Charsets.UTF_8),
                        )
                    }
                    name.startsWith("media/") && !entry.isDirectory -> {
                        val bytes = zip.readBytes()
                        val mime = when {
                            name.endsWith(".png", ignoreCase = true) -> "image/png"
                            name.endsWith(".webp", ignoreCase = true) -> "image/webp"
                            else -> "image/jpeg"
                        }
                        entryToFile[name] = mediaStore.saveImported(bytes, mime)
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        val parsed = backup ?: error("backup.json missing")
        parsed.conversations.forEach { conversation ->
            val conversationId = conversationDao.insert(
                ConversationEntity(
                    title = conversation.title,
                    provider = conversation.provider,
                    model = conversation.model,
                    systemPrompt = conversation.systemPrompt,
                    updatedAt = conversation.updatedAt,
                ),
            )
            conversation.messages.forEach { message ->
                val messageId = messageDao.insert(
                    MessageEntity(
                        conversationId = conversationId,
                        role = message.role,
                        content = message.content,
                        reasoning = message.reasoning,
                        status = message.status,
                        error = message.error,
                        createdAt = message.createdAt,
                    ),
                )
                val attachments = message.attachments.mapNotNull { attachment ->
                    val path = entryToFile[attachment.entry] ?: return@mapNotNull null
                    AttachmentEntity(
                        messageId = messageId,
                        conversationId = conversationId,
                        localPath = path,
                        mime = attachment.mime,
                        source = attachment.source,
                        remoteUrl = attachment.remoteUrl,
                        sortOrder = attachment.sortOrder,
                    )
                }
                if (attachments.isNotEmpty()) attachmentDao.insertAll(attachments)
            }
        }
        parsed.conversations.size
    }

    @Serializable
    private data class BackupFile(
        val version: Int = 1,
        val exportedAt: Long,
        val conversations: List<BackupConversation> = emptyList(),
    )

    @Serializable
    private data class BackupConversation(
        val title: String,
        val provider: String,
        val model: String,
        val systemPrompt: String,
        val updatedAt: Long,
        val messages: List<BackupMessage> = emptyList(),
    )

    @Serializable
    private data class BackupMessage(
        val role: String,
        val content: String,
        val reasoning: String = "",
        val status: String,
        val error: String = "",
        val createdAt: Long,
        val attachments: List<BackupAttachment> = emptyList(),
    )

    @Serializable
    private data class BackupAttachment(
        val entry: String,
        val mime: String,
        val source: String,
        val remoteUrl: String = "",
        val sortOrder: Int = 0,
    )

    private companion object {
        const val BACKUP_ENTRY = "backup.json"
    }
}
