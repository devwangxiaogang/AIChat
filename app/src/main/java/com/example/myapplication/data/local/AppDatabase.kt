package com.example.myapplication.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/** 会话、消息和翻译记录存在本机。删除会话时消息级联删除。 */
@Database(
    entities = [ConversationEntity::class, MessageEntity::class, TranslationEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao

    abstract fun messageDao(): MessageDao

    abstract fun translationDao(): TranslationDao
}
