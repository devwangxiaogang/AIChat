package com.example.myapplication.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/** 会话和消息存在本机，不上传。删除会话时消息级联删除。 */
@Database(
    entities = [ConversationEntity::class, MessageEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao

    abstract fun messageDao(): MessageDao
}
