package com.example.myapplication.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS message_attachments (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                messageId INTEGER NOT NULL,
                conversationId INTEGER NOT NULL,
                localPath TEXT NOT NULL,
                mime TEXT NOT NULL,
                source TEXT NOT NULL,
                remoteUrl TEXT NOT NULL DEFAULT '',
                sortOrder INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY(messageId) REFERENCES messages(id) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_message_attachments_messageId ON message_attachments(messageId)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_message_attachments_conversationId ON message_attachments(conversationId)",
        )
    }
}

/** 列表页支持置顶会话。 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE conversations ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0")
    }
}
