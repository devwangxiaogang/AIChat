package com.yuguang.lumo.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/** 会话、消息、附件和翻译记录存在本机。删除会话时消息与附件级联删除。 */
@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        AttachmentEntity::class,
        TranslationEntity::class,
    ],
    version = 4,
    // 导出 schema 到 app/schemas，迁移才能被 room-testing 验证。
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao

    abstract fun messageDao(): MessageDao

    abstract fun attachmentDao(): AttachmentDao

    abstract fun translationDao(): TranslationDao
}
